package com.codex.ringlab;
import android.content.Intent;import org.json.JSONObject;

final class EffectSettings {
    String mode="rainbow",colors="#00D9CF,#446BFF,#F064CA";
    int brightness=96,white=40,speed=40,palette=7,bank=0,source=0,gain=50;
    int tail=8,softness=65,width=28,waves=2,density=22,floor=2,release=65,gate=55;
    boolean reverse=false;
    EffectSettings copy(){return read(new Intent(),this);}
    static int limit(int x,int a,int b){return Math.max(a,Math.min(b,x));}
    static EffectSettings read(Intent i,EffectSettings old){EffectSettings c=new EffectSettings();
        c.mode=i.hasExtra("mode")?i.getStringExtra("mode"):old.mode;c.colors=i.hasExtra("colors")?i.getStringExtra("colors"):old.colors;
        c.brightness=limit(i.getIntExtra("brightness",old.brightness),0,255);c.white=limit(i.getIntExtra("white",old.white),0,255);
        c.speed=limit(i.getIntExtra("speed",old.speed),1,100);c.palette=limit(i.getIntExtra("palette",old.palette),0,Patterns.PALETTES.length-1);c.bank=limit(i.getIntExtra("bank",old.bank),0,2);
        c.source=limit(i.getIntExtra("source",old.source),0,1);c.gain=limit(i.getIntExtra("gain",old.gain),0,100);c.reverse=i.getBooleanExtra("reverse",old.reverse);
        c.tail=limit(i.getIntExtra("tail",old.tail),1,24);c.softness=limit(i.getIntExtra("softness",old.softness),0,100);c.width=limit(i.getIntExtra("width",old.width),8,80);
        c.waves=limit(i.getIntExtra("waves",old.waves),1,6);c.density=limit(i.getIntExtra("density",old.density),5,90);c.floor=limit(i.getIntExtra("floor",old.floor),0,80);
        c.release=limit(i.getIntExtra("release",old.release),0,100);c.gate=limit(i.getIntExtra("gate",old.gate),30,85);
        Patterns.parseColors(c.colors);return c;
    }
    Intent write(Intent i){return i.putExtra("mode",mode).putExtra("colors",colors).putExtra("brightness",brightness).putExtra("white",white).putExtra("speed",speed).putExtra("palette",palette).putExtra("bank",bank).putExtra("source",source).putExtra("gain",gain).putExtra("reverse",reverse).putExtra("tail",tail).putExtra("softness",softness).putExtra("width",width).putExtra("waves",waves).putExtra("density",density).putExtra("floor",floor).putExtra("release",release).putExtra("gate",gate);}
    JSONObject json(){JSONObject j=new JSONObject();try{Intent i=write(new Intent());for(String k:i.getExtras().keySet())j.put(k,i.getExtras().get(k));}catch(Exception ignored){}return j;}
    static EffectSettings fromJson(String value,boolean music){EffectSettings c=new EffectSettings();if(music)c.mode="music_breathe";try{JSONObject j=new JSONObject(value);Intent i=new Intent();java.util.Iterator<String> it=j.keys();while(it.hasNext()){String k=it.next();Object v=j.get(k);if(v instanceof Boolean)i.putExtra(k,(Boolean)v);else if(v instanceof Number)i.putExtra(k,((Number)v).intValue());else i.putExtra(k,String.valueOf(v));}c=read(i,c);}catch(Exception ignored){}if(Patterns.music(c.mode)!=music)c.mode=music?"music_breathe":"rainbow";return c;}
}
