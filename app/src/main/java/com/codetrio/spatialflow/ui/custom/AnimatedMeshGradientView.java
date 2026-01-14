package com.codetrio.spatialflow.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import com.google.android.material.color.MaterialColors;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A custom view that renders animated, blurred "blobs" that move randomly.
 * Generated purely in code using RadialGradient for maximum blur control.
 * Features smooth color transitions and organic mesh blending.
 */
public class AnimatedMeshGradientView extends View {

    private final List<Blob> blobs = new ArrayList<>();
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int[] targetColors = new int[] { 0xFF1a1a2e, 0xFF16213e, 0xFF0f0f23, 0xFF000000 };
    private long lastTime = 0;
    private static final float COLOR_TRANSITION_SPEED = 1.2f;
    private boolean isDarkMode = true;

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
        // Create 6 large procedural blobs - 6 is a good balance for performance and
        // density
        for (int i = 0; i < 6; i++) {
            blobs.add(new Blob());
        }
    }

    public void setColors(int[] colors) {
        if (colors == null || colors.length == 0)
            return;
        this.targetColors = colors;

        for (int i = 0; i < blobs.size(); i++) {
            blobs.get(i).targetColor = colors[i % colors.length];
        }
        invalidate();
    }

    public void setIsDarkMode(boolean isDarkMode) {
        this.isDarkMode = isDarkMode;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long currentTime = System.currentTimeMillis();
        float deltaTime = lastTime == 0 ? 0 : (currentTime - lastTime) / 1000f;
        lastTime = currentTime;

        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0)
            return;

        // Dynamic base background
        if (isDarkMode) {
            canvas.drawColor(0xFF000000); // Pure Black
        } else {
            // Material 3 Dynamic Surface
            int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface);
            canvas.drawColor(surfaceColor);
        }

        for (Blob blob : blobs) {
            blob.update(width, height, deltaTime);

            // Recreate shader only if color has changed or it doesn't exist
            if (blob.shader == null || blob.lastAppliedColor != blob.currentColor) {
                int centerColor = blob.currentColor;
                int midColor = (centerColor & 0x00FFFFFF) | 0x80000000; // 50% alpha

                blob.shader = new RadialGradient(
                        0, 0, blob.radius,
                        new int[] { centerColor, midColor, 0x00000000 },
                        new float[] { 0.0f, 0.5f, 1.0f },
                        Shader.TileMode.CLAMP);
                blob.lastAppliedColor = blob.currentColor;
            }

            // Move the shader to blob's position using matrix
            blob.matrix.setTranslate(blob.x, blob.y);
            blob.shader.setLocalMatrix(blob.matrix);

            paint.setShader(blob.shader);
            // Softer blending in light mode
            paint.setAlpha(isDarkMode ? 190 : 130);
            canvas.drawCircle(blob.x, blob.y, blob.radius, paint);
        }

        invalidate();
    }

    private int lerpColor(int from, int to, float ratio) {
        if (ratio > 1f)
            ratio = 1f;
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * ratio);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * ratio);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * ratio);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio);
        return Color.argb(a, r, g, b);
    }

    private class Blob {
        float x, y;
        float vx, vy;
        float radius;
        int currentColor;
        int targetColor;

        // Performance cache
        Shader shader;
        int lastAppliedColor;
        final android.graphics.Matrix matrix = new android.graphics.Matrix();

        Blob() {
            // Massive radius for "Totally Blurred" effect
            radius = 1000 + random.nextInt(1000);

            targetColor = targetColors[random.nextInt(targetColors.length)];
            currentColor = targetColor;

            reset();
        }

        void reset() {
            x = random.nextFloat() * 2000;
            y = random.nextFloat() * 4000;
            vx = (random.nextFloat() - 0.5f) * 160;
            vy = (random.nextFloat() - 0.5f) * 160;
        }

        void update(int width, int height, float deltaTime) {
            if (x == 0 && y == 0) {
                x = random.nextFloat() * width;
                y = random.nextFloat() * height;
            }

            x += vx * deltaTime;
            y += vy * deltaTime;

            // Color Transition
            if (currentColor != targetColor) {
                currentColor = lerpColor(currentColor, targetColor, deltaTime * COLOR_TRANSITION_SPEED);
            }

            // Wider bounce margins to keep colors bleeding from edges
            float margin = radius * 0.5f;
            if (x < -margin) {
                x = -margin;
                vx = Math.abs(vx);
            } else if (x > width + margin) {
                x = width + margin;
                vx = -Math.abs(vx);
            }

            if (y < -margin) {
                y = -margin;
                vy = Math.abs(vy);
            } else if (y > height + margin) {
                y = height + margin;
                vy = -Math.abs(vy);
            }
        }
    }
}
