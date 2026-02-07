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
 * Optimized animated mesh gradient view with frame rate limiting.
 * Performance: 30fps target, hardware acceleration, reduced blob count.
 */
public class AnimatedMeshGradientView extends View {

    private final List<Blob> blobs = new ArrayList<>();
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int[] DARK_MODE_COLORS = new int[] {
            0xFF1a1a2e, 0xFF16213e, 0xFF0f3460,
            0xFF533483, 0xFF2d4059, 0xFF1f4068
    };

    private static final int[] LIGHT_MODE_COLORS = new int[] {
            0xFFe3f2fd, 0xFFf3e5f5, 0xFFfce4ec,
            0xFFe8f5e9, 0xFFfff3e0, 0xFFede7f6
    };

    private int[] currentPalette = DARK_MODE_COLORS;
    private long lastFrameTime = 0;
    private static final long FRAME_INTERVAL_MS = 60; // ~30fps (was 60+ fps)

    private static final float COLOR_TRANSITION_SPEED = 2.5f;
    private static final float SPEED_MULTIPLIER = 1.8f;
    private static final int BLOB_COUNT = 10; // Reduced from 6

    private boolean isDarkMode = true;
    private boolean isAnimating = true;

    // Cached background color
    private int cachedSurfaceColor = -1;

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
        // Enable hardware acceleration
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Reduced blob count for better performance
        for (int i = 0; i < BLOB_COUNT; i++) {
            blobs.add(new Blob());
        }
        paint.setDither(true);
    }

    public void setColors(int[] colors) {
        if (colors == null || colors.length == 0)
            return;

        this.currentPalette = colors;

        // 1. Analyze Palette Luminance for Vibe
        double totalLuminance = 0;
        List<Integer> darkColors = new ArrayList<>();
        List<Integer> lightColors = new ArrayList<>();

        for (int color : colors) {
            double lum = calculateLuminance(color);
            totalLuminance += lum;
            if (lum < 0.5) {
                darkColors.add(color);
            } else {
                lightColors.add(color);
            }
        }

        double avgLuminance = totalLuminance / colors.length;
        boolean isDarkVibe = avgLuminance < 0.5;

        // 2. Build Weighted Pool
        List<Integer> weightedColors = new ArrayList<>();

        // Strategy:
        // If Dark Vibe -> Dark Colors get 3x weight, Light Colors get 1x
        // If Light Vibe -> Light Colors get 3x weight, Dark Colors get 1x

        List<Integer> primarySet = isDarkVibe ? darkColors : lightColors;
        List<Integer> secondarySet = isDarkVibe ? lightColors : darkColors;

        // Fallback: If primary set is empty (e.g. generic dark art with ALL dark
        // colors), just use everything
        if (primarySet.isEmpty())
            primarySet = secondarySet;
        if (secondarySet.isEmpty())
            secondarySet = primarySet; // Should generally not happen if primary not empty

        // Add Primary (3x Weight)
        for (int i = 0; i < 3; i++) {
            weightedColors.addAll(primarySet);
        }
        // Add Secondary (1x Weight)
        weightedColors.addAll(secondarySet);

        // 3. Assign to Blobs
        for (int i = 0; i < blobs.size(); i++) {
            int colorIndex = i % weightedColors.size();
            blobs.get(i).setTargetColor(weightedColors.get(colorIndex));
        }
        invalidate();
    }

    private double calculateLuminance(int color) {
        // Rec. 709 constants for relative luminance
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0;
    }

    public void setIsDarkMode(boolean isDarkMode) {
        if (this.isDarkMode != isDarkMode) {
            this.isDarkMode = isDarkMode;
            // Only switch to default palettes if we haven't set a custom cover art palette
            // yet
            // Or if you strictly want to enforce mode-switching behavior, you can keep
            // logic.
            // But usually, player gradient follows ALBUM ART, regardless of app theme.
            // For now, we'll respect the existing logic which resets to defaults.
            currentPalette = isDarkMode ? DARK_MODE_COLORS : LIGHT_MODE_COLORS;
            cachedSurfaceColor = -1; // Reset cache

            // Re-apply defaults using the new weighted logic (though defaults are uniform,
            // so it doesn't matter much)
            setColors(currentPalette);
        }
    }

    /** Stop animation when view is not visible */
    public void stopAnimation() {
        isAnimating = false;
    }

    /** Resume animation */
    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            lastFrameTime = 0;
            invalidate();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0)
            return;

        // Frame rate limiting - skip if too soon
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastFrameTime;

        float deltaTime;
        if (lastFrameTime == 0) {
            deltaTime = 0.033f; // Assume 30fps for first frame
            lastFrameTime = currentTime;
        } else if (elapsed < FRAME_INTERVAL_MS) {
            // Too soon - draw without update, schedule next frame
            deltaTime = 0; // Don't update positions
        } else {
            deltaTime = elapsed / 1000f;
            lastFrameTime = currentTime;
        }

        // Draw background
        if (isDarkMode) {
            canvas.drawColor(0xFF0a0a0f);
        } else {
            if (cachedSurfaceColor == -1) {
                cachedSurfaceColor = MaterialColors.getColor(this,
                        com.google.android.material.R.attr.colorSurface);
            }
            canvas.drawColor(cachedSurfaceColor);
        }

        // Draw blobs
        for (Blob blob : blobs) {
            if (deltaTime > 0) {
                blob.update(width, height, deltaTime);
            }

            // Recreate shader only when color actually changes
            if (blob.shader == null || blob.lastAppliedColor != blob.currentColor) {
                createShaderForBlob(blob);
            }

            blob.matrix.setTranslate(blob.x, blob.y);
            blob.shader.setLocalMatrix(blob.matrix);

            paint.setShader(blob.shader);
            paint.setAlpha(isDarkMode ? 170 : 140);
            canvas.drawCircle(blob.x, blob.y, blob.radius, paint);
        }

        // Schedule next frame only if animating
        if (isAnimating) {
            postInvalidateDelayed(FRAME_INTERVAL_MS - (System.currentTimeMillis() - currentTime));
        }
    }

    private void createShaderForBlob(Blob blob) {
        int centerColor = blob.currentColor;

        if (isDarkMode) {
            int midColor1 = adjustAlpha(centerColor, 0.75f);
            int midColor2 = adjustAlpha(centerColor, 0.45f);
            int edgeColor = adjustAlpha(centerColor, 0.15f);

            blob.shader = new RadialGradient(
                    0, 0, blob.radius,
                    new int[] { centerColor, midColor1, midColor2, edgeColor, 0x00000000 },
                    new float[] { 0.0f, 0.25f, 0.5f, 0.75f, 1.0f },
                    Shader.TileMode.CLAMP);
        } else {
            int midColor1 = adjustAlpha(centerColor, 0.8f);
            int midColor2 = adjustAlpha(centerColor, 0.5f);
            int edgeColor = adjustAlpha(centerColor, 0.2f);

            blob.shader = new RadialGradient(
                    0, 0, blob.radius,
                    new int[] { centerColor, midColor1, midColor2, edgeColor, 0x00000000 },
                    new float[] { 0.0f, 0.3f, 0.6f, 0.85f, 1.0f },
                    Shader.TileMode.CLAMP);
        }

        blob.lastAppliedColor = blob.currentColor;
    }

    private int adjustAlpha(int color, float alphaRatio) {
        int alpha = (int) (Color.alpha(color) * alphaRatio);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int lerpColor(int from, int to, float ratio) {
        ratio = Math.min(ratio, 1f);
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

        Shader shader;
        int lastAppliedColor;
        final android.graphics.Matrix matrix = new android.graphics.Matrix();

        Blob() {
            // Slightly smaller radius for better performance
            radius = 800 + random.nextInt(800);
            targetColor = currentPalette[random.nextInt(currentPalette.length)];
            currentColor = targetColor;
            reset();
        }

        void reset() {
            x = random.nextFloat() * 2000;
            y = random.nextFloat() * 4000;
            vx = (random.nextFloat() - 0.5f) * 200 * SPEED_MULTIPLIER;
            vy = (random.nextFloat() - 0.5f) * 200 * SPEED_MULTIPLIER;
        }

        void setTargetColor(int color) {
            this.targetColor = color;
        }

        void update(int width, int height, float deltaTime) {
            if (x == 0 && y == 0) {
                x = random.nextFloat() * width;
                y = random.nextFloat() * height;
            }

            x += vx * deltaTime;
            y += vy * deltaTime;

            if (currentColor != targetColor) {
                currentColor = lerpColor(currentColor, targetColor, deltaTime * COLOR_TRANSITION_SPEED);
            }

            float margin = radius * 0.6f;

            if (x < -margin) {
                x = -margin;
                vx = Math.abs(vx) * 0.95f;
            } else if (x > width + margin) {
                x = width + margin;
                vx = -Math.abs(vx) * 0.95f;
            }

            if (y < -margin) {
                y = -margin;
                vy = Math.abs(vy) * 0.95f;
            } else if (y > height + margin) {
                y = height + margin;
                vy = -Math.abs(vy) * 0.95f;
            }

            // Reduced random drift frequency
            if (random.nextFloat() < 0.01f) {
                vx += (random.nextFloat() - 0.5f) * 20;
                vy += (random.nextFloat() - 0.5f) * 20;

                float maxSpeed = 250 * SPEED_MULTIPLIER;
                vx = Math.max(-maxSpeed, Math.min(maxSpeed, vx));
                vy = Math.max(-maxSpeed, Math.min(maxSpeed, vy));
            }
        }
    }
}