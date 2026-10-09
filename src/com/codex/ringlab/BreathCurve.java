package com.codex.ringlab;
final class BreathCurve {
    static double level(double phase,int hold){
        double cycle=phase-Math.floor(phase);boolean rising=cycle<.5;double t=rising?cycle*2:(cycle-.5)*2;
        double amount=Math.max(0,Math.min(100,hold))/100.0;
        double warped=t-amount*Math.sin(t*2*Math.PI)/(2*Math.PI);
        double value=(1-Math.cos(warped*Math.PI))/2;
        return rising?value:1-value;
    }
}
