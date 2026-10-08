package com.codex.ringlab;
import android.app.Activity;import android.graphics.drawable.GradientDrawable;import android.widget.Button;
final class StudioUi {
    static Button button(Activity activity,String label,int color,int ink,Runnable action){Button b=new Button(activity);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextColor(ink);GradientDrawable background=new GradientDrawable();background.setColor(color);background.setCornerRadius(18*activity.getResources().getDisplayMetrics().widthPixels/1600f);if(color==0xffffffff)background.setStroke(1,0xffe4e9f2);b.setBackground(background);int padding=Math.round(7*activity.getResources().getDisplayMetrics().density);b.setPadding(padding,0,padding,0);b.setOnClickListener(v->action.run());return b;}
}
