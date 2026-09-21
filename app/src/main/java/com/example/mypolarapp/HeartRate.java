package com.example.mypolarapp;

import android.graphics.Color;

/**
 * Small shared helpers so the backdrop, the ECG waveform, and the readout panel all agree on colour and zone for a given BPM
 */
public final class HeartRate {

    private HeartRate() {
    }

    public static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Calm blue -> alert red ramp, same curve as the web version. */
    public static int getColor(float heartRate) {
        float progress = clamp((heartRate - 55f) / 60f, 0f, 1f);
        int red = (int) lerp(20, 255, progress);
        int green = (int) lerp(230, 45, progress);
        int blue = (int) lerp(255, 85, progress);
        return Color.rgb(red, green, blue);
    }

    public static String getZone(float heartRate) {
        if (heartRate < 60) return "LOW";
        if (heartRate < 85) return "NORMAL";
        if (heartRate < 100) return "ELEVATED";
        return "HIGH";
    }
}