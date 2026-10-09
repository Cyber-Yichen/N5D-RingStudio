package com.codex.ringlab;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import org.json.*;
public class EffectsService extends Service {
    static volatile EffectSettings settings=new EffectSettings();
    public static volatile String mode="rainbow",status="准备灯光",audioStatus="未采集";
    public static volatile int brightness=96,speed=40,palette=7,bank=0,audioSource=0,gain=50,frameFade=0,whiteFade=0,logoMode=2,logoLevel=96,logoActual=0;
    public static volatile boolean reverse=false,ready=false,running=false,audioListening=false,ringEnabled=false;
    public static volatile int[] displayed=new int[96];
    public static volatile double volume=0,db=-120,rms=0,fps=0,writeMs=0,minDb=0,maxDb=-120,phase=0,spinPhase=0;
    public static volatile long frames=0,audioSamples=0,phaseEpoch=0,audioStarts=0;
    public static volatile boolean apiActive=false;public static volatile String apiClient="",chipState="";private static volatile int apiUid=-1;
    private String session="";private int lease=10000;private long deadline,sequence=-1;private Patterns.Frame externalFrame;private EffectSettings savedSettings;private boolean savedRing,savedRunning,restoreOnEnd=true;private double savedPhase,savedSpinPhase;private long savedEpoch,savedAt;private int savedLogoMode,savedLogoLevel;private IBinder ownerBinder;private IBinder.DeathRecipient ownerDeath;
    private HandlerThread thread;private Handler worker;private RootDriver driver;private final AudioInput audio=new AudioInput();
    private FrameTransition returnBlend;private long returnStarted;private boolean returnRunning;private static volatile boolean restoring=false;
    private PowerManager.WakeLock cpu,screen;private long lastSave,lastTick,started;private boolean ticking=false,destroyed=false;private int captureSource=-1;private double smoothed=0,pulse=0;
    @Override public void onCreate(){super.onCreate();startForeground(46,notification());thread=new HandlerThread("light-frames");thread.start();worker=new Handler(thread.getLooper());PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);cpu=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"n5d:effects");screen=pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"n5d:keep-display");cpu.acquire();screen.acquire();SharedPreferences p=getSharedPreferences("effects",0);logoMode=p.getInt("logo_mode",2);logoLevel=p.getInt("logo_level",96);}
    private Notification notification(){
        try{if(Build.VERSION.SDK_INT>=26){Class<?> c=Class.forName("android.app.NotificationChannel");Object channel=c.getConstructor(String.class,CharSequence.class,int.class).newInstance("light-effects","灯效后台运行",2);Object nm=getSystemService(NOTIFICATION_SERVICE);nm.getClass().getMethod("createNotificationChannel",c).invoke(nm,channel);}}catch(Exception ignored){}
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|0x04000000);
        Intent off=new Intent(this,EffectsService.class).putExtra("mode","off");PendingIntent stop=PendingIntent.getService(this,1,off,PendingIntent.FLAG_UPDATE_CURRENT|0x04000000);
        Notification.Builder b=new Notification.Builder(this).setContentTitle("N5D 灯环工坊").setContentText("灯效在本机运行 · 屏幕保持亮屏").setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(pi).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"关闭所有灯光",stop);
        try{if(Build.VERSION.SDK_INT>=26)b.getClass().getMethod("setChannelId",String.class).invoke(b,"light-effects");}catch(Exception ignored){}return b.build();
    }

    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null)return START_NOT_STICKY;final Intent values=new Intent(intent);
        final Message api=values.getParcelableExtra("_api_message");
        if(api!=null){worker.post(()->{try{handleApi(api);}finally{PublicControlService.queued.decrementAndGet();}});return START_NOT_STICKY;}
        worker.post(()->{if(destroyed)return;try{
            String command=values.getStringExtra("command");String requested=values.getStringExtra("mode");
            if("api_disable".equals(command)){if(apiActive)endSession(restoreOnEnd);return;}
            if("quit".equals(command)){allOff();stopSelf();return;}
            if("off".equals(requested)||"off".equals(command)){allOff();return;}
            if(driver==null){status="等待本机 root 授权";driver=new RootDriver();driver.open(getApplicationInfo().sourceDir);ready=true;}
            if("logo".equals(command)){
                logoMode=EffectSettings.limit(values.getIntExtra("logo_mode",logoMode),0,3);logoLevel=EffectSettings.limit(values.getIntExtra("logo_level",logoLevel),0,255);if(apiActive){savedLogoMode=logoMode;savedLogoLevel=logoLevel;}
                getSharedPreferences("effects",0).edit().putInt("logo_mode",logoMode).putInt("logo_level",logoLevel).apply();
            }else{
                EffectSettings next=EffectSettings.read(values,settings);if(!Patterns.valid(next.mode))throw new IOException("未知灯效");clearSession();cancelReturn();
                boolean different=!ringEnabled||!next.mode.equals(settings.mode);settings=next;ringEnabled=true;mode=next.mode;
                brightness=next.brightness;speed=next.speed;palette=next.palette;bank=next.bank;audioSource=next.source;gain=next.gain;reverse=next.reverse;
                if(different){phase=spinPhase=0;phaseEpoch=SystemClock.elapsedRealtime();}
                getSharedPreferences("effects",0).edit().putString("mode",mode).putString(Patterns.music(mode)?"music_settings":"normal_settings",next.json().toString()).apply();
            }
            synchronizeAudio();
            running=ringEnabled||logoMode==1||logoMode==3;status=ringEnabled?"正在运行 · "+Patterns.NAMES[Patterns.index(mode)]:"仅 Logo 灯运行";
            if(!running){allOff();}else if(!ticking){ticking=true;lastTick=started=SystemClock.elapsedRealtime();tick();}
            save();
        }catch(Exception e){failure(e);}});return START_NOT_STICKY;
    }
    private void synchronizeAudio()throws Exception{boolean need=(returnBlend==null||returnRunning)&&((ringEnabled&&externalFrame==null&&Patterns.music(settings.mode))||logoMode==3);
        if(audioListening&&(!need||captureSource!=settings.source)){audio.stop();audioListening=false;audioStatus="未采集";}
        if(need&&!audioListening){audio.start(settings.source);captureSource=settings.source;audioStarts++;audioListening=true;minDb=0;maxDb=-120;}
    }
    private final Runnable nextTick=()->tick();
    private void tick(){if(!running||destroyed){ticking=false;return;}long before=SystemClock.elapsedRealtime();boolean finishedReturn=false;try{
        double dt=Math.max(0,Math.min(200,before-lastTick));lastTick=before;if(ringEnabled){phase+=dt/(8000-settings.speed*72.0);spinPhase+=dt/(8000-settings.rotationSpeed*72.0);}
        double target=0;if(audioListening){db=audio.db;rms=audio.rms;audioStatus=audio.status;audioSamples=audio.samples;if(audioSamples>0){minDb=Math.min(minDb,db);maxDb=Math.max(maxDb,db);}target=Patterns.clamp((db+(settings.gain-50)*.48+settings.gate)/45);}
        double old=smoothed;double response=target>smoothed?.55:.025+(100-settings.release)*.0035;smoothed+=(target-smoothed)*(1-Math.pow(1-response,dt/40));
        if(target-old>.13)pulse=1;else pulse*=Math.pow(.86,dt/40);volume=smoothed;
        Patterns.Frame frame=ringEnabled?(externalFrame==null?Patterns.render(settings,phase,spinPhase,smoothed,pulse):externalFrame):new Patterns.Frame();
        double maximum=0;for(int n=0;n<96;n++)maximum=Math.max(maximum,frame.channels[n]/255.0*(n<72?frame.fade/Math.max(1.0,settings.brightness):frame.whiteFade/Math.max(1.0,settings.white)));
        maximum=Patterns.clamp(maximum);logoActual=logoMode==0?0:logoMode==1?logoLevel:logoMode==3?(int)Math.round(logoLevel*smoothed):(int)Math.round(logoLevel*maximum);
        if(returnBlend!=null){long elapsed=before-returnStarted;int targetLogo=returnRunning?logoActual:0;FrameTransition.Output blended=returnBlend.blend(frame.channels,frame.fade,frame.whiteFade,targetLogo,elapsed);System.arraycopy(blended.channels,0,frame.channels,0,96);frame.fade=blended.fade;frame.whiteFade=blended.white;logoActual=blended.logo;
            if(elapsed>=FrameTransition.DURATION_MS){boolean keepRunning=returnRunning;cancelReturn();finishedReturn=true;if(!keepRunning){allOff();return;}}
        }
        long writeStart=SystemClock.elapsedRealtime();driver.frame(frame.channels,frame.fade,frame.whiteFade,logoActual);chipState=driver.configuration;long after=SystemClock.elapsedRealtime();writeMs=after-writeStart;
        displayed=frame.channels;frameFade=frame.fade;whiteFade=frame.whiteFade;frames++;fps=1000.0/Math.max(1,after-before+Math.max(1,40-(after-before)));
        if(audioListening&&audioStatus.startsWith("音频失败"))throw new IOException(audioStatus);
        if(finishedReturn||after-lastSave>1000){lastSave=after;save();}
        worker.postDelayed(nextTick,Math.max(1,40-(after-before)));
    }catch(Exception e){failure(e);}}
    private void cancelReturn(){returnBlend=null;restoring=false;}
    private void allOff()throws Exception{clearSession();cancelReturn();running=ringEnabled=false;worker.removeCallbacks(nextTick);ticking=false;audio.stop();audioListening=false;audioStatus="未采集";if(driver!=null)driver.off();displayed=new int[96];frameFade=whiteFade=logoActual=0;status="所有灯光已关闭";volume=0;save();}
    private void failure(Exception e){try{allOff();}catch(Exception ignored){}if(driver!=null)driver.close();driver=null;ready=false;status="未运行："+e.getMessage();save();}
    private void save(){try{JSONObject d=new JSONObject();d.put("time_ms",System.currentTimeMillis());d.put("mode",mode);d.put("status",status);d.put("running",running);d.put("root_connected",ready);d.put("computer_bridge",false);d.put("chip_configuration",driver==null?"":driver.configuration);d.put("brightness",brightness);d.put("frame_fade",frameFade);d.put("white_brightness",settings.white);d.put("white_fade",whiteFade);d.put("phase",phase);d.put("spin_phase",spinPhase);d.put("phase_epoch",phaseEpoch);d.put("audio_starts",audioStarts);d.put("ring_enabled",ringEnabled);d.put("settings",settings.json());d.put("speed",speed);d.put("palette",palette);d.put("bank",bank);d.put("reverse",reverse);d.put("audio_source",audioSource==0?"microphone":"playback");d.put("audio_status",audioStatus);d.put("audio_samples",audioSamples);d.put("rms",rms);d.put("dbfs",db);d.put("min_dbfs",minDb);d.put("max_dbfs",maxDb);d.put("audio_level",volume);d.put("frames",frames);d.put("fps",fps);d.put("write_ms",writeMs);d.put("logo_mode",logoMode);d.put("logo_level",logoLevel);d.put("logo_actual",logoActual);d.put("audio_listening",audioListening);d.put("display_keep_awake",screen!=null&&screen.isHeld());JSONArray a=new JSONArray();for(int x:displayed)a.put(x);d.put("channels",a);d.put("api",publicState());File dir=getExternalFilesDir(null);if(dir==null)dir=getFilesDir();File temp=new File(dir,"effects-state.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(d.toString(2).getBytes("UTF-8"));}temp.renameTo(new File(dir,"effects-state.json"));}catch(Exception ignored){}}


    static JSONObject publicState(){JSONObject j=new JSONObject();try{j.put("api_version",1);j.put("app_version","1.2");j.put("ready",ready);j.put("running",running);j.put("ring_enabled",ringEnabled);j.put("mode",mode);j.put("status",status);j.put("external_control",apiActive);j.put("restoring_local",restoring);j.put("restore_transition_ms",FrameTransition.DURATION_MS);j.put("owner_uid",apiUid);j.put("owner",apiClient);j.put("settings",settings.json());j.put("brightness",frameFade);j.put("white_brightness",whiteFade);j.put("logo_mode",logoMode);j.put("logo_level",logoLevel);j.put("logo_actual",logoActual);j.put("frames",frames);j.put("spin_phase",spinPhase);j.put("chip_configuration",ready?chipState:"");JSONArray a=new JSONArray();for(int x:displayed)a.put(x);j.put("channels",a);}catch(Exception ignored){}return j;}
    private void ensureDriver()throws Exception{if(driver==null){status="等待本机 root 授权";driver=new RootDriver();driver.open(getApplicationInfo().sourceDir);ready=true;}}
    private void beginTick()throws Exception{synchronizeAudio();running=returnBlend!=null||ringEnabled||logoMode==1||logoMode==3;if(running&&!ticking){ticking=true;lastTick=started=SystemClock.elapsedRealtime();tick();}}
    private void permission(EffectSettings next,int nextLogo)throws ControlProtocol.Error{if((Patterns.music(next.mode)||nextLogo==3)&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new ControlProtocol.Error("AUDIO_PERMISSION_REQUIRED","请先在主界面授予音频权限");}
    private void renew(){deadline=SystemClock.elapsedRealtime()+lease;worker.removeCallbacks(expire);worker.postDelayed(expire,lease);}
    private final Runnable expire=()->{if(!apiActive)return;long remain=deadline-SystemClock.elapsedRealtime();if(remain>0){worker.postDelayed(this.expire,remain);return;}try{endSession(restoreOnEnd);}catch(Exception e){failure(e);}};
    private void clearSession(){apiActive=false;apiUid=-1;apiClient="";session="";externalFrame=null;sequence=-1;if(worker!=null)worker.removeCallbacks(expire);if(ownerBinder!=null&&ownerDeath!=null)try{ownerBinder.unlinkToDeath(ownerDeath,0);}catch(Exception ignored){}ownerBinder=null;ownerDeath=null;}
    private void restoreLocal()throws Exception{FrameTransition blend=new FrameTransition(displayed,frameFade,whiteFade,logoActual);EffectSettings previous=savedSettings;boolean enabled=savedRing;int lm=savedLogoMode,ll=savedLogoLevel;clearSession();settings=previous;ringEnabled=enabled;mode=previous.mode;brightness=previous.brightness;speed=previous.speed;palette=previous.palette;bank=previous.bank;audioSource=previous.source;gain=previous.gain;reverse=previous.reverse;logoMode=lm;logoLevel=ll;phase=savedPhase+(enabled?(SystemClock.elapsedRealtime()-savedAt)/(8000-previous.speed*72.0):0);spinPhase=savedSpinPhase+(enabled?(SystemClock.elapsedRealtime()-savedAt)/(8000-previous.rotationSpeed*72.0):0);phaseEpoch=savedEpoch;status=enabled?"正在运行 · "+Patterns.NAMES[Patterns.index(mode)]:"仅 Logo 灯运行";returnRunning=savedRunning;returnBlend=blend;restoring=true;synchronizeAudio();returnStarted=SystemClock.elapsedRealtime();beginTick();}
    private void endSession(boolean resume)throws Exception{if(resume)restoreLocal();else allOff();}
    private void handleApi(Message m){boolean io=false;try{
        if(destroyed)throw new ControlProtocol.Error("UNAVAILABLE","服务正在退出");
        if(!getSharedPreferences("effects",0).getBoolean("api_enabled",false))throw new ControlProtocol.Error("DISABLED","本机灯光接口已关闭");
        Bundle b=m.getData();JSONObject result=new JSONObject();
        if(m.what==ControlProtocol.ACQUIRE){
            if(apiActive)throw new ControlProtocol.Error("BUSY","灯光已由另一个会话控制");
            int requestedLease=ControlProtocol.integer(b,"lease_ms",10000,2000,60000);if(b.containsKey("restore_on_end")&&!(b.get("restore_on_end") instanceof Boolean))throw new ControlProtocol.Error("INVALID_ARGUMENT","restore_on_end 必须是 boolean");boolean restore=b.getBoolean("restore_on_end",true);IBinder binder=m.replyTo.getBinder();if(!binder.isBinderAlive())throw new ControlProtocol.Error("INVALID_CALLER","调用者已退出");
            io=true;ensureDriver();io=false;savedSettings=settings.copy();savedRing=ringEnabled;savedRunning=returnBlend!=null?returnRunning:running;savedLogoMode=logoMode;savedLogoLevel=logoLevel;savedPhase=phase;savedSpinPhase=spinPhase;savedEpoch=phaseEpoch;savedAt=SystemClock.elapsedRealtime();restoreOnEnd=restore;
            session=java.util.UUID.randomUUID().toString();lease=requestedLease;if(!savedRunning)logoMode=0;apiUid=m.sendingUid;apiActive=true;String[] packages=getPackageManager().getPackagesForUid(apiUid);apiClient=packages!=null&&packages.length>0?packages[0]:"UID "+apiUid;
            final String owned=session;ownerBinder=binder;ownerDeath=()->worker.post(()->{if(owned.equals(session))try{endSession(restoreOnEnd);}catch(Exception e){failure(e);}});try{ownerBinder.linkToDeath(ownerDeath,0);}catch(Exception e){clearSession();throw new ControlProtocol.Error("INVALID_CALLER","调用者已退出");}
            if(returnBlend!=null){logoMode=1;logoLevel=logoActual;}cancelReturn();externalFrame=new Patterns.Frame();System.arraycopy(displayed,0,externalFrame.channels,0,96);externalFrame.fade=frameFade;externalFrame.whiteFade=whiteFade;mode="external";ringEnabled=true;status="外部应用控制";renew();io=true;beginTick();io=false;
            if(!ready)throw new ControlProtocol.Error("DRIVER_ERROR",status);result.put("session_id",session);result.put("lease_ms",lease);result.put("owner",apiClient);result.put("restore_on_end",restoreOnEnd);
        }else{
            if(!apiActive||m.sendingUid!=apiUid||!session.equals(b.getString("session_id","")))throw new ControlProtocol.Error("NOT_OWNER","会话不存在、已过期或不属于调用者");
            if(m.what==ControlProtocol.EFFECT){EffectSettings next=ControlProtocol.effect(b,settings);permission(next,logoMode);boolean different=externalFrame!=null||!next.mode.equals(settings.mode);externalFrame=null;settings=next;mode=next.mode;ringEnabled=true;brightness=next.brightness;speed=next.speed;palette=next.palette;bank=next.bank;audioSource=next.source;gain=next.gain;reverse=next.reverse;if(different){phase=spinPhase=0;phaseEpoch=SystemClock.elapsedRealtime();}io=true;beginTick();io=false;}
            else if(m.what==ControlProtocol.FRAME){Patterns.Frame frame=ControlProtocol.frame(b);long next=b.getLong("sequence",sequence+1);if(next<0||next<=sequence)throw new ControlProtocol.Error("STALE_FRAME","帧序号必须递增");int nextLogo=b.containsKey("logo")?ControlProtocol.integer(b,"logo",0,0,255):-1;externalFrame=frame;sequence=next;mode="external";ringEnabled=true;if(nextLogo>=0){logoMode=1;logoLevel=nextLogo;}io=true;beginTick();io=false;result.put("sequence",sequence);}
            else if(m.what==ControlProtocol.LOGO){int lm=ControlProtocol.integer(b,"logo_mode",logoMode,0,3),ll=ControlProtocol.integer(b,"logo_level",logoLevel,0,255);if(lm==3)permission(new EffectSettings(),3);logoMode=lm;logoLevel=ll;io=true;beginTick();io=false;}
            else if(m.what==ControlProtocol.KEEPALIVE){}
            else if(m.what==ControlProtocol.RELEASE){if(b.containsKey("resume_local")&&!(b.get("resume_local") instanceof Boolean))throw new ControlProtocol.Error("INVALID_ARGUMENT","resume_local 必须是 boolean");boolean resume=b.getBoolean("resume_local",restoreOnEnd);io=true;if(resume){if(savedRunning)permission(savedSettings,savedLogoMode);restoreLocal();}else allOff();io=false;}
            else if(m.what==ControlProtocol.OFF){io=true;allOff();io=false;}
            else throw new ControlProtocol.Error("UNKNOWN_COMMAND","未知命令");
            if(apiActive)renew();if((m.what==ControlProtocol.EFFECT||m.what==ControlProtocol.FRAME||m.what==ControlProtocol.LOGO)&&!ready)throw new ControlProtocol.Error("DRIVER_ERROR",status);
        }
        save();ControlProtocol.reply(m,true,"OK","已接受",result);
    }catch(ControlProtocol.Error e){ControlProtocol.reply(m,false,e.code,e.getMessage(),null);}catch(Exception e){if(io){failure(e);ControlProtocol.reply(m,false,"DRIVER_ERROR",status,null);}else ControlProtocol.reply(m,false,"INVALID_ARGUMENT",e.getMessage(),null);}}

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){destroyed=true;running=false;if(worker!=null){worker.removeCallbacks(nextTick);worker.removeCallbacks(expire);worker.post(()->{try{allOff();}catch(Exception ignored){}if(driver!=null)driver.close();driver=null;ready=false;if(cpu!=null&&cpu.isHeld())cpu.release();if(screen!=null&&screen.isHeld())screen.release();thread.quitSafely();});}super.onDestroy();}
}
