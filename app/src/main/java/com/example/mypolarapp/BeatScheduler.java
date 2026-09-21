package com.example.mypolarapp;

import android.os.Handler;
import android.os.Looper;


public class BeatScheduler {
    // Implement this interface to respond to each scheduled heartbeat
    public interface OnBeatListener {
        void onBeat(float heartRate);
    }
    // Schedule callbacks on Android's main (UI) thread
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Receives heartbeat events, for example to trigger sound or animation
    private final OnBeatListener listener;
    private float heartRate = 72f; // Default heart rate in beats per minute
    private boolean running = false;
    // Perform one heartbeat callback, then schedule the next one
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            // Notify the listener using the current heart rate
            listener.onBeat(heartRate);
            // Continue scheduling only while the scheduler is running
            if (running) {
                handler.postDelayed(this, (long) (60000f / heartRate));
            }
        }
    };
    // Store the listener that will handle heartbeat events
    public BeatScheduler(OnBeatListener listener) {
        this.listener = listener;
    }

    /**
     * Update the current BPM takes effect from the next scheduled beat
     */
    public void setHeartRate(float bpm) {
        this.heartRate = bpm;
    }
    /**
     * Starts scheduling heartbeats, with the first callback queued immediately
     */
    public void start() {
        if (running) return;
        running = true;
        handler.post(tick);
    }
    /**
     * Stops scheduling heartbeats and removes pending callbacks
     */
    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }
}