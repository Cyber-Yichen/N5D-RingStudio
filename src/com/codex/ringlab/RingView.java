package com.codex.ringlab;
import android.content.Context;import android.graphics.*;import android.view.View;
final class RingView extends View {
    private Bitmap logo;private final RectF mark=new RectF();
    private final Paint p=new Paint(3);private final Matrix rotation=new Matrix();private final int[] colors=new int[25];
    RingView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);try(java.io.InputStream in=c.getAssets().open("alipay.png")){logo=BitmapFactory.decodeStream(in);}catch(Exception ignored){}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()*.43f,r=Math.min(getWidth()*.35f,getHeight()*.29f);int[] f=EffectsService.displayed;
        p.setShader(null);p.setMaskFilter(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.085f);p.setColor(0xff050b15);c.drawCircle(cx,cy+2,r,p);p.setColor(0xff2b3d56);c.drawCircle(cx,cy-1,r,p);p.setColor(0xff0a1423);c.drawCircle(cx,cy,r,p);
        double overall=0;for(int i=0;i<24;i++){int g=Patterns.RGB[i]*3;double w=(f[Patterns.WHITE[i]]+f[Patterns.WHITE[(i+23)%24]])*.5*EffectsService.whiteFade/255.0;overall=Math.max(overall,Math.max(f[g],Math.max(f[g+1],f[g+2]))*EffectsService.frameFade/255.0+w);}
        for(int i=0;i<24;i++){int g=Patterns.RGB[i]*3;double rgb=EffectsService.frameFade/255.0;
            double w=(f[Patterns.WHITE[i]]+f[Patterns.WHITE[(i+23)%24]])*.5*EffectsService.whiteFade/255.0;
            double red=f[g+2]*rgb+w,green=f[g+1]*rgb+w,blue=f[g]*rgb+w;
            double lift=overall>0?255.0/overall:1;
            colors[i]=Color.rgb((int)Math.min(255,red*lift),(int)Math.min(255,green*lift),(int)Math.min(255,blue*lift));}
        colors[24]=colors[0];SweepGradient shader=new SweepGradient(cx,cy,colors,null);rotation.setRotate(-90,cx,cy);shader.setLocalMatrix(rotation);p.setShader(shader);
        p.setColor(Color.WHITE);p.setStrokeWidth(r*.19f);p.setAlpha(85);p.setMaskFilter(new BlurMaskFilter(r*.09f,BlurMaskFilter.Blur.NORMAL));c.drawCircle(cx,cy,r,p);
        p.setStrokeWidth(r*.09f);p.setAlpha(240);p.setMaskFilter(new BlurMaskFilter(r*.022f,BlurMaskFilter.Blur.NORMAL));c.drawCircle(cx,cy,r,p);
        p.setMaskFilter(null);p.setStrokeWidth(r*.025f);p.setAlpha(110);c.drawCircle(cx,cy,r,p);
        p.setAlpha(255);p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        if(logo!=null){float height=r*.70f,width=height*.92f;mark.set(cx-width/2,cy-height/2,cx+width/2,cy+height/2);int brightness=EffectsService.logoActual;p.setFilterBitmap(true);
            p.setAlpha(210);p.setColorFilter(new PorterDuffColorFilter(0xff02060e,PorterDuff.Mode.SRC_IN));p.setMaskFilter(new BlurMaskFilter(r*.015f,BlurMaskFilter.Blur.NORMAL));c.save();c.translate(0,r*.018f);c.drawBitmap(logo,null,mark,p);c.restore();
            p.setMaskFilter(null);p.setAlpha(160);p.setColorFilter(new PorterDuffColorFilter(0xff35465d,PorterDuff.Mode.SRC_IN));c.save();c.translate(0,-r*.008f);c.drawBitmap(logo,null,mark,p);c.restore();
            p.setAlpha(255);p.setColorFilter(new PorterDuffColorFilter(0xff0a1423,PorterDuff.Mode.SRC_IN));c.drawBitmap(logo,null,mark,p);p.setColorFilter(null);
            if(brightness>0){p.setMaskFilter(new BlurMaskFilter(r*.04f,BlurMaskFilter.Blur.NORMAL));p.setAlpha((int)(brightness*.35));c.drawBitmap(logo,null,mark,p);p.setMaskFilter(null);p.setAlpha(brightness);c.drawBitmap(logo,null,mark,p);}}

        p.setAlpha(255);p.setColor(0xffeef4ff);p.setTypeface(Typeface.create("sans-serif-light",0));p.setTextSize(Math.min(getWidth(),getHeight())*.0875f);c.drawText(EffectsService.ringEnabled?(EffectsService.apiActive?("external".equals(EffectsService.mode)?"外部灯效":Patterns.NAMES[Patterns.index(EffectsService.mode)]):Patterns.NAMES[Patterns.index(EffectsService.mode)]):"灯光已关闭",cx,cy+r*1.52f,p);
    }
}
