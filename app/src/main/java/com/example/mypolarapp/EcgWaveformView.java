package com.example.mypolarapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

public class EcgWaveformView extends View {
    // Reuse objects, with anti aliasing for smoother lines
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float heartRate = 72f; // Initial BPM used to select the colour
    private float time = 0f; // Animation frame counter
    private boolean running = true;
    // Schedule animation updates in sync with the display
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (running) {
                time += 1f;
                invalidate();
            }
            // Keep receiving callbacks while the view is attached, even if paused
            if (isAttachedToWindow()) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };
    // Used when creating the view
    public EcgWaveformView(Context context) {
        super(context);
        init();
    }
    // Used when loading the view from an XML layout
    public EcgWaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Draw the waveform as an outline with a four-pixel line width
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }
    /**
     * Updates the heart rate used to choose the waveform colour
     */
    public void setHeartRate(float bpm) {
        this.heartRate = bpm;
    }
    /**
     * Starts or pauses waveform scrolling
     */
    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;
        // Select a colour based on the current heart rate
        paint.setColor(HeartRate.getColor(heartRate));
        // Clear before building the next frame
        path.reset();
        boolean first = true;
        // Calculate one waveform point for each horizontal pixel
        for (int x = 0; x < width; x++) {
            float phase = ((x + time * 3f) % 90f) / 90f;
            float spike;

            if (phase < 0.05f) {
                spike = -30f * phase / 0.05f;
            } else if (phase < 0.10f) {
                spike = 58f * (phase - 0.05f) / 0.05f;
            } else if (phase < 0.15f) {
                spike = 58f - 78f * (phase - 0.10f) / 0.05f;
            } else if (phase < 0.22f) {
                spike = -20f + 20f * (phase - 0.15f) / 0.07f;
            } else {
                spike = (float) (Math.sin(phase * 18) * 3);
            }

            float y = height * 0.55f - spike;
            if (first) {
                path.moveTo(x, y);
                first = false;
            } else {
                // Connect each point to the previous one
                path.lineTo(x, y);
            }
        }
        // Draw
        canvas.drawPath(path, paint);
    }
}