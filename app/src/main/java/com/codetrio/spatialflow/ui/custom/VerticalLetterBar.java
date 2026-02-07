package com.codetrio.spatialflow.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;

/**
 * A vertical letter bar (A-Z) for fast scrolling through a list.
 * Features Apple-style zoom animation with smooth transitions.
 */
public class VerticalLetterBar extends View {

    private static final String LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int selectedIndex = -1;
    private float touchY = -1f;
    private boolean isPressed = false;
    private OnLetterSelectListener listener;
    private boolean isDarkMode = true;

    // Smooth animation state
    private float animatedTouchY = -1f;
    private float animatedScale = 0f; // 0 = not pressed, 1 = fully pressed
    private ValueAnimator scaleAnimator;

    public interface OnLetterSelectListener {
        void onLetterSelected(char letter);
    }

    public VerticalLetterBar(Context context) {
        super(context);
        init();
    }

    public VerticalLetterBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VerticalLetterBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        textPaint.setTextSize(14f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        // Bubble background - will be set to Material Dynamic Color in setDarkMode
        bubblePaint.setStyle(Paint.Style.FILL);

        setClickable(true);
        setFocusable(true);
    }

    public void setOnLetterSelectListener(OnLetterSelectListener listener) {
        this.listener = listener;
    }

    public void setDarkMode(boolean darkMode) {
        this.isDarkMode = darkMode;

        // Use Material Dynamic Primary Color for bubble and text
        int primaryColor;
        try {
            primaryColor = MaterialColors.getColor(getContext(), android.R.attr.colorPrimary, 0xFF6200EE);
        } catch (Exception e) {
            primaryColor = darkMode ? 0xFFBB86FC : 0xFF6200EE; // Purple fallback
        }

        // In dark mode, use primary color for text; in light mode use dark gray
        int textColor = darkMode ? primaryColor : Color.DKGRAY;
        textPaint.setColor(textColor);

        // Add transparency (0xDD = ~87% opacity) for bubble
        bubblePaint.setColor((primaryColor & 0x00FFFFFF) | 0xDD000000);

        invalidate();
    }

    private void animateScaleTo(float target) {
        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }
        scaleAnimator = ValueAnimator.ofFloat(animatedScale, target);
        scaleAnimator.setDuration(150); // Quick but smooth
        scaleAnimator.setInterpolator(new DecelerateInterpolator());
        scaleAnimator.addUpdateListener(animation -> {
            animatedScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        scaleAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int letterCount = LETTERS.length();
        float singleHeight = (float) height / letterCount;

        // Smoothly interpolate touchY for animation
        if (isPressed && touchY >= 0) {
            if (animatedTouchY < 0) {
                animatedTouchY = touchY;
            } else {
                // Smooth follow
                animatedTouchY += (touchY - animatedTouchY) * 0.4f;
            }
        }

        for (int i = 0; i < letterCount; i++) {
            float x = width / 2f;
            float letterCenterY = singleHeight * i + singleHeight / 2f;

            float scale = 1.0f;
            float alpha = 0.6f;
            float offsetX = 0f;

            // Calculate zoom based on animated state
            if (animatedScale > 0 && animatedTouchY >= 0) {
                float dist = Math.abs(animatedTouchY - letterCenterY);
                // Very tight zoom radius: Only the single selected letter zooms
                float zoomRadius = singleHeight * 0.6f;

                if (dist < zoomRadius) {
                    float proximity = 1.0f - (dist / zoomRadius);
                    // Big zoom for single letter
                    float zoomFactor = 4.5f * proximity * animatedScale;
                    scale = 1.0f + zoomFactor;
                    // More offset to 160px for better visibility
                    offsetX = 160f * proximity * animatedScale;
                    alpha = 1.0f; // Full opacity for selected letter
                }
            }

            // Apply scale
            textPaint.setTextSize(14f * scale);
            textPaint.setAlpha((int) (255 * alpha));

            float drawX = x - offsetX;
            float drawY = letterCenterY + (textPaint.getTextSize() / 3f);

            // Draw bubble behind zoomed letter
            if (scale > 1.5f) {
                float bubbleRadius = textPaint.getTextSize() * 0.7f;
                canvas.drawCircle(drawX, letterCenterY, bubbleRadius, bubblePaint);
                // Make text white on bubble
                textPaint.setColor(Color.WHITE);
                textPaint.setAlpha(255);
            }

            canvas.drawText(String.valueOf(LETTERS.charAt(i)), drawX, drawY, textPaint);

            // Reset color after bubble
            if (scale > 1.5f) {
                textPaint.setColor(isDarkMode ? Color.WHITE : Color.DKGRAY);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float y = event.getY();
        int height = getHeight();
        int letterCount = LETTERS.length();
        float letterHeight = (float) height / letterCount;

        int newIndex = (int) (y / letterHeight);
        newIndex = Math.max(0, Math.min(letterCount - 1, newIndex));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isPressed = true;
                touchY = y;
                animateScaleTo(1.0f); // Animate in
                // Fall through to MOVE logic
            case MotionEvent.ACTION_MOVE:
                isPressed = true;
                touchY = y;

                if (newIndex != selectedIndex) {
                    selectedIndex = newIndex;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }

                    if (listener != null) {
                        listener.onLetterSelected(LETTERS.charAt(selectedIndex));
                    }
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isPressed = false;
                selectedIndex = -1;
                animateScaleTo(0f); // Animate out
                // Reset animated touch after animation completes
                postDelayed(() -> {
                    if (!isPressed) {
                        animatedTouchY = -1f;
                        touchY = -1f;
                    }
                }, 200);
                return true;
        }

        return super.onTouchEvent(event);
    }
}
