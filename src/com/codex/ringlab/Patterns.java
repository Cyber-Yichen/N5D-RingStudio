package com.codex.ringlab;
import android.graphics.Color;
final class Patterns {
    static final String[] IDS={"solid","breathe","chase","comet","rainbow","gradient","sweep","bounce","wave","duet","sparkle","theater","alternating","rain","music_breathe","vu","beat","orbit_breathe"};
    static final String[] NAMES={"常亮","柔和呼吸","单点转圈","彗星拖尾","色彩流转","渐变呼吸","上下扫动","左右往返","波浪流动","双星追逐","星光闪烁","间隔追逐","彩白交替","雨滴下落","音乐呼吸","音乐电平","节拍脉冲","流光呼吸"};
    static final int[] RGB={2,21,17,13,9,5,1,4,8,12,16,20,0,23,19,15,11,7,3,22,18,14,10,6};
    static final int[] WHITE={78,77,89,76,88,75,87,84,72,73,85,74,86,83,95,82,81,94,93,80,92,91,79,90};
    static final String[] PALETTES={"绯红","鲜绿","深蓝","青色","紫罗兰","琥珀","纯白","彩虹","深海","落日","糖霜","薄荷冰","桃花","自定义色卡"};
    static final int[] COLORS={0xffee3548,0xff31e585,0xff3d65ff,0xff00c9dc,0xffbf4cff,0xffffa338,0xffffffff};
    static boolean breathing(String id){return "breathe".equals(id)||"gradient".equals(id)||"orbit_breathe".equals(id);}
    static boolean music(String id){return "music_breathe".equals(id)||"vu".equals(id)||"beat".equals(id);}
    static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return 4;}
    static boolean valid(String id){return IDS[index(id)].equals(id);}
    static double clamp(double x){return Math.max(0,Math.min(1,x));}
    static double wrap(double x){return x-Math.floor(x);}
    static double distance(double a,double b){double x=Math.abs(wrap(a)-wrap(b));return Math.min(x,1-x);}
    static int[] parseColors(String value){if(value==null)throw new IllegalArgumentException("请输入色卡");String[] words=value.trim().split("[,，;；\\s]+");if(words.length<1||words.length>8)throw new IllegalArgumentException("色卡支持 1～8 个颜色");int[] a=new int[words.length];for(int i=0;i<a.length;i++){String s=words[i];if(!s.startsWith("#"))s="#"+s;if(!s.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("使用六位 HEX，例如 #00D9CF");a[i]=Color.parseColor(s);}return a;}
    static int mix(int a,int b,double t){return Color.rgb((int)Math.round(Color.red(a)*(1-t)+Color.red(b)*t),(int)Math.round(Color.green(a)*(1-t)+Color.green(b)*t),(int)Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));}
    static int palette(int p,double pos,double phase,int[] custom){
        if(p<7)return COLORS[Math.max(0,p)];
        if(p==7)return Color.HSVToColor(new float[]{(float)(360*wrap(pos+phase)),.92f,1});
        int[] stops=p==8?new int[]{0xff1946fc,0xff00ddd2}:p==9?new int[]{0xffff325c,0xffffb738,0xffa22aea}:p==10?new int[]{0xff8e62ff,0xff02d9de,0xffff71b2}:p==11?new int[]{0xff20e8ac,0xff19a1ff}:p==12?new int[]{0xffff5295,0xffff9d5a}:custom;
        double x=wrap(pos+phase)*stops.length;return mix(stops[(int)x],stops[((int)x+1)%stops.length],x-Math.floor(x));
    }
    static int defaultCount(int p){return p<7?1:p==8||p==11||p==12?2:p==9||p==10||p==13?3:8;}
    static int[] colorStops(EffectSettings s){int[] custom=parseColors(s.colors),out=new int[s.colorCount];for(int i=0;i<out.length;i++)out[i]=s.palette==13?custom[Math.min(i,custom.length-1)]:palette(s.palette,i/(double)out.length,0,custom);return out;}
    static int sample(int[] colors,double pos,double phase){double x=wrap(pos+phase)*colors.length;return mix(colors[(int)x],colors[((int)x+1)%colors.length],x-Math.floor(x));}
    static String hex(int color){return String.format(java.util.Locale.US,"#%06X",color&0xffffff);}
    static String encode(int[] colors){StringBuilder b=new StringBuilder();for(int color:colors){if(b.length()>0)b.append(',');b.append(hex(color));}return b.toString();}
    static final class Frame {final int[] channels=new int[96];int fade,whiteFade;}
    static Frame render(EffectSettings s,double phase,double spin,double audio,double pulse){
        Frame out=new Frame();double envelope=1;
        if(breathing(s.mode))envelope=s.floor/100.0+(1-s.floor/100.0)*BreathCurve.level(phase,s.breathHold);
        if(s.mode.equals("music_breathe"))envelope=audio;
        if(s.mode.equals("beat"))envelope=pulse;
        out.fade=(int)Math.round(s.brightness*envelope);out.whiteFade=(int)Math.round(s.white*envelope);
        int[] stops=colorStops(s);double move=s.reverse?-phase:phase;
        for(int kind=0;kind<2;kind++)for(int i=0;i<24;i++){
            double pos=(i+(kind==1?.5:0))/24.0,theta=pos*2*Math.PI;double intensity=1;
            if(s.mode.equals("orbit_breathe"))intensity=.08+.92*Math.exp(-Math.pow(distance(pos,s.reverse?-spin:spin)/(s.width/200.0),2));
            else if(s.mode.equals("chase"))intensity=Math.exp(-Math.pow(distance(pos,move)/(.025+s.softness*.00025),2));
            else if(s.mode.equals("comet")){
                
                double head=.008+s.softness*.00045,tail=s.tail/24.0;
                double behind=wrap(s.reverse?pos+phase:phase-pos);if(behind>1-head*4)behind-=1;
                intensity=behind<0?Math.exp(-Math.pow(behind/head,2)):behind<tail?Math.pow(.5+.5*Math.cos(Math.PI*behind/tail),1.6):0;
            }
            else if(s.mode.equals("sweep")){double y=-Math.cos(theta),scan=-1.3+2.6*wrap(move);intensity=Math.exp(-Math.pow((y-scan)/(s.width/100.0),2));}
            else if(s.mode.equals("bounce")){double x=Math.sin(theta),scan=Math.sin(move*2*Math.PI);intensity=Math.exp(-Math.pow((x-scan)/(s.width/100.0),2));}
            else if(s.mode.equals("wave"))intensity=.02+.98*Math.pow((Math.sin((pos*s.waves-move)*2*Math.PI)+1)/2,2);
            else if(s.mode.equals("duet"))intensity=Math.max(Math.exp(-Math.pow(distance(pos,move)/(.025+s.softness*.0005),2)),Math.exp(-Math.pow(distance(pos,move+.5)/(.025+s.softness*.0005),2)));
            else if(s.mode.equals("sparkle")){double seed=Math.sin((i+kind*24+1)*127.1+Math.floor(phase*8)*311.7)*43758.5453;intensity=wrap(seed)>1-s.density/100.0?wrap(seed):.01;}
            else if(s.mode.equals("theater"))intensity=(i+(int)Math.floor(wrap(move)*24))%3==0?1:0;
            else if(s.mode.equals("alternating"))intensity=((int)Math.floor(phase*2)%2)==kind?1:0;
            else if(s.mode.equals("rain")){double y=(1-Math.cos(theta))/2;double streak=wrap(y-move*2+(Math.sin(theta)>0?.35:0));intensity=Math.exp(-streak*100.0/s.width);}
            else if(s.mode.equals("vu")){double height=(1+Math.cos(theta))/2;intensity=clamp((audio-height)*12+.5);}
            
            double colorPos=s.mode.equals("vu")?(1+Math.cos(theta))/2:s.mode.equals("gradient")?0:pos;
            double colorPhase=s.mode.equals("orbit_breathe")?(s.reverse?spin:-spin):(s.mode.equals("rainbow")||s.mode.equals("gradient"))?move*.35:0;
            int c=sample(stops,colorPos,colorPhase);
            if(kind==0){if(s.bank==1)intensity=0;int g=RGB[i]*3;out.channels[g]=(int)Math.round(Color.blue(c)*intensity);out.channels[g+1]=(int)Math.round(Color.green(c)*intensity);out.channels[g+2]=(int)Math.round(Color.red(c)*intensity);}
            else{if(s.bank==0)intensity=0;out.channels[WHITE[i]]=(int)Math.round(255*intensity);}
        }return out;
    }
}
