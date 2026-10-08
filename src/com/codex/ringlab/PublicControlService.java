package com.codex.ringlab;
import android.app.Service;import android.content.Intent;import android.os.*;import java.util.concurrent.atomic.AtomicInteger;
public class PublicControlService extends Service {
    static final AtomicInteger queued=new AtomicInteger();private long window;private int requests;
    private final Messenger messenger=new Messenger(new Handler(){@Override public void handleMessage(Message received){Message m=Message.obtain(received);try{
        if(m.replyTo==null)return;Bundle b=m.getData();if(b.getInt("api_version",1)!=ControlProtocol.VERSION)throw new ControlProtocol.Error("UNSUPPORTED_VERSION","不支持此协议版本");
        boolean enabled=getSharedPreferences("effects",0).getBoolean("api_enabled",false);
        if(m.what==ControlProtocol.HELLO){ControlProtocol.reply(m,true,"OK","能力信息",ControlProtocol.capabilities(enabled));return;}
        if(m.what==ControlProtocol.STATE){ControlProtocol.reply(m,true,"OK","状态",EffectsService.publicState());return;}
        if(!enabled)throw new ControlProtocol.Error("DISABLED","请先在关于页面开启本机灯光接口");
        if(m.sendingUid<0)throw new ControlProtocol.Error("INVALID_CALLER","无法确定调用应用");
        if(m.what<ControlProtocol.ACQUIRE||m.what>ControlProtocol.OFF)throw new ControlProtocol.Error("UNKNOWN_COMMAND","未知命令");
        if(b.toString().length()>12000)throw new ControlProtocol.Error("INVALID_ARGUMENT","请求过长");
        long now=SystemClock.elapsedRealtime();if(now-window>=1000){window=now;requests=0;}if(++requests>60)throw new ControlProtocol.Error("RATE_LIMIT","请求过于频繁");
        if(queued.incrementAndGet()>32){queued.decrementAndGet();throw new ControlProtocol.Error("BUSY","等待中的请求过多");}
        try{startService(new Intent(PublicControlService.this,EffectsService.class).putExtra("_api_message",m));}catch(Exception e){queued.decrementAndGet();throw e;}
    }catch(ControlProtocol.Error e){ControlProtocol.reply(m,false,e.code,e.getMessage(),null);}catch(Exception e){ControlProtocol.reply(m,false,"INVALID_ARGUMENT","请求无效："+e.getMessage(),null);}}});
    @Override public IBinder onBind(Intent i){return ControlProtocol.ACTION.equals(i.getAction())?messenger.getBinder():null;}
}
