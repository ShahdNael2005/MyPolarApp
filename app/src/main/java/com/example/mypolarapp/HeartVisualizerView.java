package com.example.mypolarapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HeartVisualizerView extends View {

    // Stores the position and appearance settings for one particle
    private static class Particle {
        float angle;
        float radius;
        float speed;
        float size;
        float life;
    }

    // Reuse drawing objects and keep track of all active particles
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    // Gradients for the blue background and darker outer edges
    private RadialGradient backgroundShader;
    private RadialGradient vignetteShader;

    // Initial heart rate in beats per minute
    private float heartRate = 72f;
    private float time = 0f;
    private boolean running = true;

    // Request redraws in sync with the display's frame timing
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (running) {
                time += 1f;
                invalidate();
            }
            if (isAttachedToWindow()) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };
    // Constructor used when creating this view directly in Java
    public HeartVisualizerView(Context context) {
        super(context);
        init();
    }
    // Constructor used when loading this view from an XML layout
    public HeartVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        // Create a blue-to-black gradient centred slightly above the middle
        backgroundShader = new RadialGradient(
                w * 0.5f, h * 0.48f, Math.max(w, h) * 0.75f,
                new int[]{0xFF052B67, 0xFF020617, 0xFF000000},
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP);

        // Add a transparent centre that fades towards darker outer edges
        vignetteShader = new RadialGradient(
                w * 0.5f, h * 0.5f, Math.max(w, h) * 0.75f,
                new int[]{0x00000000, 0x73000014, 0xD1000000},
                new float[]{0.45f, 0.78f, 1f},
                Shader.TileMode.CLAMP);

        // Wait until the view has a width before choosing particle orbit radii
        if (particles.isEmpty()) {
            addParticles(420);
        }
    }

    /**
     * Updates the BPM value
     */
    public void setHeartRate(float bpm) {
        this.heartRate = bpm;
    }

    /**
     *  Enables or pauses animation updates other redraws can still update particles inside onDraw()
     */
    public void setRunning(boolean running) {
        this.running = running;
    }
    // Create particles with different orbits, speeds, sizes
    private void addParticles(int amount) {
        int width = Math.max(getWidth(), 1);
        for (int i = 0; i < amount; i++) {
            Particle p = new Particle();
            p.angle = (float) (random.nextFloat() * Math.PI * 2);
            p.radius = random.nextFloat() * width * 0.36f + width * 0.08f;
            p.speed = random.nextFloat() * 1.4f + 0.5f;
            p.size = random.nextFloat() * 0.8f + 0.2f;
            p.life = random.nextFloat() * 100f;
            particles.add(p);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;
        // Draw the background
        if (backgroundShader != null) {
            paint.setShader(backgroundShader);
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(null);
        }
        // Select the visualisation colour from the heart-rate value
        int color = HeartRate.getColor(heartRate);
        // Draw the visual layers from back to front
        drawWaveField(canvas, width, height, color);
        drawHoops(canvas, width, height, color);
        drawParticles(canvas, width, height, color);
        // Edge overlay gradient
        if (vignetteShader != null) {
            paint.setShader(vignetteShader);
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(null);
        }
    }

    // draw wave paths across visualization
    private void drawWaveField(Canvas canvas, int width, int height, int color) {
        paint.setColor(color);
        paint.setStrokeWidth(2f);
        paint.setAlpha((int) (0.035f * 255));
        paint.setPathEffect(null);

        for (int row = 0; row < 28; row++) {
            path.reset();
            boolean first = true;
            // Sample each wave at 18-pixel horizontal intervals
            for (float x = 0; x < width; x += 18) {
                // Offset each row and shift its wave phase over time
                float y = (float) (height * 0.5
                        + Math.sin(x * 0.006 + time * 0.018 + row * 0.35) * height * 0.18
                        + (row - 14) * height * 0.016);
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
            canvas.drawPath(path, paint);
        }
    }

    // Draw stacked hoops with small dots moving around them
    private void drawHoops(Canvas canvas, int width, int height, int color) {
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;

        for (int index = 0; index < 13; index++) {
            // Space the hoops vertically and gradually increase their width
            float y = centerY + (index - 6) * height * 0.045f;
            float radiusX = width * (0.16f + index * 0.018f);
            float radiusY = height * 0.023f;
            // Gently expand and contract each hoop
            float pulse = (float) (Math.sin(time * 0.08 + index) * 8);

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(color);
            paint.setAlpha((int) ((0.12f + (index % 3) * 0.06f) * 255));
            paint.setStrokeWidth(index % 4 == 0 ? 2.2f : 1f);
            // Alternate between solid hoops and dashed hoops
            paint.setPathEffect(index % 2 == 1
                    ? new DashPathEffect(new float[]{8f, 15f}, 0)
                    : null);

            RectF oval = new RectF(
                    centerX - (radiusX + pulse), y - radiusY,
                    centerX + (radiusX + pulse), y + radiusY);
            canvas.drawOval(oval, paint);

            // Switch to solid filled circles for the moving dots
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha((int) (0.8f * 255));
            for (int dotIndex = 0; dotIndex < 8; dotIndex++) {
                float angle = (float) (time * 0.012 + dotIndex * 0.78 + index * 0.13);
                float x = centerX + (float) Math.cos(angle) * (radiusX + pulse);
                float dotY = y + (float) Math.sin(angle) * radiusY;
                canvas.drawCircle(x, dotY, 2.1f, paint);
            }
        }
    }

    // Update and draw particles orbiting around the centre of the view
    private void drawParticles(Canvas canvas, int width, int height, int color) {
        // Higher heart rates produce a denser particle field
        int targetCount = heartRate > 95 ? 820 : heartRate > 75 ? 600 : 380;
        // Adjust the population in small batches on each redraw
        if (particles.size() < targetCount) addParticles(8);
        if (particles.size() > targetCount) {
            int removeCount = Math.min(4, particles.size());
            for (int i = 0; i < removeCount; i++) particles.remove(0);
        }

        float centerX = width * 0.5f;
        float centerY = height * 0.5f;

        paint.setStyle(Paint.Style.FILL);
        paint.setPathEffect(null);
        paint.setColor(color);

        for (Particle p : particles) {
            // Increase rotation speed in proportion to the heart rate
            p.angle += 0.002f * p.speed * (heartRate / 70f);
            p.life += 1f;
            // Flatten the orbit vertically and add up-and-down wobble
            float wobble = (float) (Math.sin(time * 0.02 + p.life * 0.04) * 20);
            float x = centerX + (float) Math.cos(p.angle) * p.radius;
            float y = centerY + (float) Math.sin(p.angle) * (p.radius * 0.28f) + wobble;
            // Larger particles get brighter as well as bigger
            paint.setAlpha((int) ((0.25f + 0.55f * p.size) * 255));
            canvas.drawCircle(x, y, 1.1f + 2.5f * p.size, paint);
        }
    }
}