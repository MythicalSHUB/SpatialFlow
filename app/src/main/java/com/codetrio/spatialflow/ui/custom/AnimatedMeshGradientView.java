package com.codetrio.spatialflow.ui.custom;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnimatedMeshGradientView extends View {

    private final List<Blob> blobs = new ArrayList<>();
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 🔥 Apple-style deep premium palette
    private static final int[] PREMIUM_COLORS = new int[]{
            0xFF0B0C10, // near black blue
            0xFF1F2833,
            0xFF0A192F,
            0xFF112240,
            0xFF1C1F2B,
            0xFF2C2F48
    };

    private long lastFrameTime = 0;
    // 🔥 60 FPS

    private static final float SPEED_MULTIPLIER = 1.4f;
    private static final int BLOB_COUNT = 4; // 🔥 fewer blobs = cleaner + faster

    private boolean isAnimating = true;

    private Paint noisePaint;

    public AnimatedMeshGradientView(Context context) {
        super(context);
        init();
    }

    public AnimatedMeshGradientView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnimatedMeshGradientView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        for (int i = 0; i < BLOB_COUNT; i++) {
            blobs.add(new Blob());
        }

        paint.setDither(true);
        initNoise();
    }

    // 🔥 Pre-generate noise ONCE (memory efficient)
    private void initNoise() {
        Bitmap noiseBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);

        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int gray = random.nextInt(256);
                int alpha = random.nextInt(25); // subtle
                noiseBitmap.setPixel(x, y, Color.argb(alpha, gray, gray, gray));
            }
        }

        noisePaint = new Paint();
        BitmapShader shader = new BitmapShader(noiseBitmap,
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        noisePaint.setShader(shader);
    }

    public void stopAnimation() {
        isAnimating = false;
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            lastFrameTime = 0;
            invalidate();
        }
    }

    public void setColors(int[] newColors) {
        if (newColors == null || newColors.length == 0) return;
        for (int i = 0; i < blobs.size(); i++) {
            Blob blob = blobs.get(i);
            blob.targetColor = newColors[i % newColors.length];
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        long now = System.currentTimeMillis();
        float deltaTime;

        if (lastFrameTime == 0) {
            deltaTime = 0.016f;
        } else {
            deltaTime = (now - lastFrameTime) / 1000f;
        }
        lastFrameTime = now;

        // 🔥 Always dark background (no mode switching)
        canvas.drawColor(0xFF050507);

        for (Blob blob : blobs) {
            blob.update(width, height, deltaTime);

            if (blob.shader == null) {
                createShader(blob);
            }

            blob.matrix.setTranslate(blob.x, blob.y);
            blob.shader.setLocalMatrix(blob.matrix);

            paint.setShader(blob.shader);
            paint.setAlpha(200);

            canvas.drawCircle(blob.x, blob.y, blob.radius, paint);
        }

        // subtle premium grain
        canvas.drawRect(0, 0, width, height, noisePaint);

        if (isAnimating) {
            postInvalidateOnAnimation(); // 🔥 better than delayed
        }
    }

    private void createShader(Blob blob) {
        int color = blob.color;

        blob.shader = new RadialGradient(
                0, 0, blob.radius,
                new int[]{
                        color,
                        adjustAlpha(color, 0.7f),
                        adjustAlpha(color, 0.35f),
                        0x00000000
                },
                new float[]{0f, 0.4f, 0.75f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = (int) (Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // 🔥 Blob Class (optimized)
    private class Blob {
        float x, y;
        float vx, vy;
        float radius;
        int color;
        int targetColor;

        Shader shader;
        final Matrix matrix = new Matrix();

        Blob() {
            radius = 900 + random.nextInt(600);
            color = PREMIUM_COLORS[random.nextInt(PREMIUM_COLORS.length)];
            targetColor = color;

            x = random.nextFloat() * 2000;
            y = random.nextFloat() * 2000;

            vx = (random.nextFloat() - 0.5f) * 120 * SPEED_MULTIPLIER;
            vy = (random.nextFloat() - 0.5f) * 120 * SPEED_MULTIPLIER;
        }

        void update(int width, int height, float dt) {
            x += vx * dt;
            y += vy * dt;

            // Smoothly dissolve old colors into new album art colors
            if (color != targetColor) {
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                int tr = Color.red(targetColor);
                int tg = Color.green(targetColor);
                int tb = Color.blue(targetColor);

                // Lerp speed: 1.5f per second to make it a smooth, luxury dissolve
                float lerpFactor = Math.min(1.0f, dt * 1.5f);

                r += (int) ((tr - r) * lerpFactor);
                g += (int) ((tg - g) * lerpFactor);
                b += (int) ((tb - b) * lerpFactor);

                color = Color.rgb(r, g, b);
                shader = null; // Recreate shader on the next draw call
            }

            float margin = radius * 0.5f;

            if (x < -margin || x > width + margin) {
                vx = -vx;
            }

            if (y < -margin || y > height + margin) {
                vy = -vy;
            }
        }
    }
}