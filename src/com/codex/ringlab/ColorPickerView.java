package com.codex.ringlab;
import android.content.Context;import android.graphics.*;import android.view.*;
final class ColorPickerView extends View {
    interface Listener {void changed(int color);}
    private final Paint p=new Paint(3);private final RectF square=new RectF();private final float[] hsv={0,.8f,1};private Listener listener;private float cx,cy,radius,band;private int target;
    ColorPickerView(Context c,int color,Listener listener){super(c);this.listener=listener;Color.colorToHSV(color,hsv);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("触摸色轮与明暗选色区域");setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);setClickable(true);}
    void setColor(int color){Color.colorToHSV(color,hsv);invalidate();}
    int color(){return Color.HSVToColor(hsv);}
    @Override protected void onDraw(Canvas c){cx=getWidth()/2f;cy=getHeight()/2f;radius=Math.min(getWidth(),getHeight())*.42f;band=radius*.16f;square.set(cx-radius*.48f,cy-radius*.48f,cx+radius*.48f,cy+radius*.48f);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(band);p.setShader(new SweepGradient(cx,cy,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null));c.drawCircle(cx,cy,radius,p);p.setShader(null);
        int hue=Color.HSVToColor(new float[]{hsv[0],1,1});p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(square.left,0,square.right,0,Color.WHITE,hue,Shader.TileMode.CLAMP));c.drawRect(square,p);p.setShader(new LinearGradient(0,square.top,0,square.bottom,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP));c.drawRect(square,p);p.setShader(null);
        double angle=Math.toRadians(hsv[0]);cursor(c,cx+(float)Math.cos(angle)*radius,cy+(float)Math.sin(angle)*radius,band*.46f);cursor(c,square.left+hsv[1]*square.width(),square.bottom-hsv[2]*square.height(),band*.40f);
    }
    private void cursor(Canvas c,float x,float y,float radius){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(0xff24364d);c.drawCircle(x,y,radius,p);p.setStrokeWidth(2);p.setColor(Color.WHITE);c.drawCircle(x,y,radius,p);p.setStyle(Paint.Style.FILL);}
    @Override public boolean onTouchEvent(MotionEvent e){float x=e.getX(),y=e.getY();int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){double d=Math.hypot(x-cx,y-cy);target=square.contains(x,y)?2:Math.abs(d-radius)<band*1.4?1:0;if(target==0)return false;getParent().requestDisallowInterceptTouchEvent(true);}
        if(target==1){double a=Math.toDegrees(Math.atan2(y-cy,x-cx));hsv[0]=(float)((a+360)%360);}else if(target==2){hsv[1]=(float)Patterns.clamp((x-square.left)/square.width());hsv[2]=(float)Patterns.clamp((square.bottom-y)/square.height());}
        if(target!=0){invalidate();listener.changed(color());}if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){target=0;getParent().requestDisallowInterceptTouchEvent(false);performClick();}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
