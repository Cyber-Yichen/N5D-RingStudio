package com.codex.ringlab;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import org.json.*;
public class EffectsService extends Service {
    static volatile EffectSettings settings=new EffectSettings();
    public static volatile String mode="rainbow",status="准备灯光",audioStatus="未采集";
    public static volatile int brightness=96,speed=40,palette=7,bank=0,audioSource=0,gain=50,frameFade=0,whiteFade=0,logoMode=2,logoLevel=96,logoActual=0;
    public static volatile boolean reverse=false,ready=false,running=false,audioListening=false,ringEnabled=false;
    public static volatile int[] displayed=new int[96];
    public static volatile double volume=0,db=-120,rms=0,fps=0,writeMs=0,minDb=0,maxDb=-120,phase=0;
    public static volatile long frames=0,audioSamples=0,phaseEpoch=0,audioStarts=0;
    private HandlerThread thread;private Handler worker;private RootDriver driver;private final AudioInput audio=new AudioInput();
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
        worker.post(()->{if(destroyed)return;try{
            String command=values.getStringExtra("command");String requested=values.getStringExtra("mode");
            if("quit".equals(command)){allOff();stopSelf();return;}
            if("off".equals(requested)||"off".equals(command)){allOff();return;}
            if(driver==null){status="等待本机 root 授权";driver=new RootDriver();driver.open(getApplicationInfo().sourceDir);ready=true;}
            if("logo".equals(command)){
                logoMode=EffectSettings.limit(values.getIntExtra("logo_mode",logoMode),0,3);logoLevel=EffectSettings.limit(values.getIntExtra("logo_level",logoLevel),0,255);
                getSharedPreferences("effects",0).edit().putInt("logo_mode",logoMode).putInt("logo_level",logoLevel).apply();
            }else{
                EffectSettings next=EffectSettings.read(values,settings);if(!Patterns.valid(next.mode))throw new IOException("未知灯效");
                boolean different=!ringEnabled||!next.mode.equals(settings.mode);settings=next;ringEnabled=true;mode=next.mode;
                brightness=next.brightness;speed=next.speed;palette=next.palette;bank=next.bank;audioSource=next.source;gain=next.gain;reverse=next.reverse;
                if(different){phase=0;phaseEpoch=SystemClock.elapsedRealtime();}
                getSharedPreferences("effects",0).edit().putString("mode",mode).putString(Patterns.music(mode)?"music_settings":"normal_settings",next.json().toString()).apply();
            }
            synchronizeAudio();
            running=ringEnabled||logoMode==1||logoMode==3;status=ringEnabled?"正在运行 · "+Patterns.NAMES[Patterns.index(mode)]:"仅 Logo 灯运行";
            if(!running){allOff();}else if(!ticking){ticking=true;lastTick=started=SystemClock.elapsedRealtime();tick();}
            save();
        }catch(Exception e){failure(e);}});return START_NOT_STICKY;
    }
    private void synchronizeAudio()throws Exception{boolean need=(ringEnabled&&Patterns.music(settings.mode))||logoMode==3;
        if(audioListening&&(!need||captureSource!=settings.source)){audio.stop();audioListening=false;audioStatus="未采集";}
        if(need&&!audioListening){audio.start(settings.source);captureSource=settings.source;audioStarts++;audioListening=true;minDb=0;maxDb=-120;}
    }
    private final Runnable nextTick=()->tick();
    private void tick(){if(!running||destroyed){ticking=false;return;}long before=SystemClock.elapsedRealtime();try{
        double dt=Math.max(0,Math.min(200,before-lastTick));lastTick=before;if(ringEnabled)phase+=dt/(8000-settings.speed*72.0);
        double target=0;if(audioListening){db=audio.db;rms=audio.rms;audioStatus=audio.status;audioSamples=audio.samples;if(audioSamples>0){minDb=Math.min(minDb,db);maxDb=Math.max(maxDb,db);}target=Patterns.clamp((db+(settings.gain-50)*.48+settings.gate)/45);}
        double old=smoothed;double response=target>smoothed?.55:.025+(100-settings.release)*.0035;smoothed+=(target-smoothed)*(1-Math.pow(1-response,dt/40));
        if(target-old>.13)pulse=1;else pulse*=Math.pow(.86,dt/40);volume=smoothed;
        Patterns.Frame frame=ringEnabled?Patterns.render(settings,phase,smoothed,pulse):new Patterns.Frame();
        double maximum=0;for(int n=0;n<96;n++)maximum=Math.max(maximum,frame.channels[n]/255.0*(n<72?frame.fade/Math.max(1.0,settings.brightness):frame.whiteFade/Math.max(1.0,settings.white)));
        logoActual=logoMode==0?0:logoMode==1?logoLevel:logoMode==3?(int)Math.round(logoLevel*smoothed):(int)Math.round(logoLevel*maximum);
        long writeStart=SystemClock.elapsedRealtime();driver.frame(frame.channels,frame.fade,frame.whiteFade,logoActual);long after=SystemClock.elapsedRealtime();writeMs=after-writeStart;
        displayed=frame.channels;frameFade=frame.fade;whiteFade=frame.whiteFade;frames++;fps=1000.0/Math.max(1,after-before+Math.max(1,40-(after-before)));
        if(audioListening&&audioStatus.startsWith("音频失败"))throw new IOException(audioStatus);
        if(after-lastSave>1000){lastSave=after;save();}
        worker.postDelayed(nextTick,Math.max(1,40-(after-before)));
    }catch(Exception e){failure(e);}}
    private void allOff()throws Exception{running=ringEnabled=false;worker.removeCallbacks(nextTick);ticking=false;audio.stop();audioListening=false;audioStatus="未采集";if(driver!=null)driver.off();displayed=new int[96];frameFade=whiteFade=logoActual=0;status="所有灯光已关闭";volume=0;save();}
    private void failure(Exception e){try{allOff();}catch(Exception ignored){}if(driver!=null)driver.close();driver=null;ready=false;status="未运行："+e.getMessage();save();}
    private void save(){try{JSONObject d=new JSONObject();d.put("time_ms",System.currentTimeMillis());d.put("mode",mode);d.put("status",status);d.put("running",running);d.put("root_connected",ready);d.put("computer_bridge",false);d.put("chip_configuration",driver==null?"":driver.configuration);d.put("brightness",brightness);d.put("frame_fade",frameFade);d.put("white_brightness",settings.white);d.put("white_fade",whiteFade);d.put("phase",phase);d.put("phase_epoch",phaseEpoch);d.put("audio_starts",audioStarts);d.put("ring_enabled",ringEnabled);d.put("settings",settings.json());d.put("speed",speed);d.put("palette",palette);d.put("bank",bank);d.put("reverse",reverse);d.put("audio_source",audioSource==0?"microphone":"playback");d.put("audio_status",audioStatus);d.put("audio_samples",audioSamples);d.put("rms",rms);d.put("dbfs",db);d.put("min_dbfs",minDb);d.put("max_dbfs",maxDb);d.put("audio_level",volume);d.put("frames",frames);d.put("fps",fps);d.put("write_ms",writeMs);d.put("logo_mode",logoMode);d.put("logo_level",logoLevel);d.put("logo_actual",logoActual);d.put("audio_listening",audioListening);d.put("display_keep_awake",screen!=null&&screen.isHeld());JSONArray a=new JSONArray();for(int x:displayed)a.put(x);d.put("channels",a);File dir=getExternalFilesDir(null);if(dir==null)dir=getFilesDir();File temp=new File(dir,"effects-state.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(d.toString(2).getBytes("UTF-8"));}temp.renameTo(new File(dir,"effects-state.json"));}catch(Exception ignored){}}

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){destroyed=true;running=false;if(worker!=null){worker.removeCallbacksAndMessages(null);worker.post(()->{try{allOff();}catch(Exception ignored){}if(driver!=null)driver.close();driver=null;ready=false;if(cpu!=null&&cpu.isHeld())cpu.release();if(screen!=null&&screen.isHeld())screen.release();thread.quitSafely();});}super.onDestroy();}
}
