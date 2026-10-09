package com.codex.ringlab;
final class FrameTransition {
    static final int DURATION_MS=1200;
    private final int[] source;
    private final int fade,white,logo;
    FrameTransition(int[] channels,int fade,int white,int logo){source=channels.clone();this.fade=fade;this.white=white;this.logo=logo;}
    static final class Output {final int[] channels=new int[96];int fade,white,logo;}
    Output blend(int[] target,int targetFade,int targetWhite,int targetLogo,long elapsed){
        Output out=new Output();double t=Math.max(0,Math.min(1,elapsed/(double)DURATION_MS));double mix=t*t*(3-2*t);
        out.fade=t==0?fade:t==1?targetFade:Math.max(fade,targetFade);out.white=t==0?white:t==1?targetWhite:Math.max(white,targetWhite);
        for(int i=0;i<96;i++){int oldLevel=i<72?fade:white,newLevel=i<72?targetFade:targetWhite,level=i<72?out.fade:out.white;
            out.channels[i]=t==0?source[i]:t==1?target[i]:level==0?0:(int)Math.round((source[i]*oldLevel*(1-mix)+target[i]*newLevel*mix)/level);
        }
        out.logo=(int)Math.round(logo*(1-mix)+targetLogo*mix);return out;
    }
}
