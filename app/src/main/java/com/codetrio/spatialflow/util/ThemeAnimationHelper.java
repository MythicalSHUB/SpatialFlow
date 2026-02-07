package com.codetrio.spatialflow.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Telegram-style smooth theme transition animation.
 */
public class ThemeAnimationHelper {

    private static final String PREFS_NAME = "ThemeAnimation";
    private static final String KEY_PENDING = "pending";
    private static final String KEY_CX = "cx";
    private static final String KEY_CY = "cy";
    private static final String KEY_TO_DARK = "to_dark";
    private static final String SCREENSHOT = "theme_ss.jpg";
    private static final int DURATION = 450;

    private static final TimeInterpolator SMOOTH_INTERPOLATOR = input -> {
        float t = input - 1.0f;
        return t * t * t + 1.0f;
    };

    public static void prepareThemeChange(Activity activity, View trigger, boolean toDark) {
        if (activity == null || trigger == null)
            return;

        try {
            View root = activity.getWindow().getDecorView();
            if (root.getWidth() == 0)
                return;

            Bitmap bmp = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bmp));

            File file = new File(activity.getCacheDir(), SCREENSHOT);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            bmp.recycle();

            int[] loc = new int[2];
            trigger.getLocationOnScreen(loc);
            int cx = loc[0] + trigger.getWidth() - trigger.getHeight() / 2;
            int cy = loc[1] + trigger.getHeight() / 2;

            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_PENDING, true)
                    .putInt(KEY_CX, cx)
                    .putInt(KEY_CY, cy)
                    .putBoolean(KEY_TO_DARK, toDark)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static void playPendingAnimation(Activity activity) {
        if (activity == null)
            return;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_PENDING, false))
            return;
        prefs.edit().putBoolean(KEY_PENDING, false).apply();

        File file = new File(activity.getCacheDir(), SCREENSHOT);
        if (!file.exists())
            return;

        try {
            Bitmap oldThemeBmp = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            if (oldThemeBmp == null) {
                file.delete();
                return;
            }

            int cx = prefs.getInt(KEY_CX, 0);
            int cy = prefs.getInt(KEY_CY, 0);
            boolean toDark = prefs.getBoolean(KEY_TO_DARK, true);

            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                oldThemeBmp.recycle();
                file.delete();
                return;
            }

            // Get FULL screen dimensions for radius calculation
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int screenW = dm.widthPixels;
            int screenH = dm.heightPixels;

            // Calculate max radius to cover ENTIRE screen
            float d1 = (float) Math.hypot(cx, cy);
            float d2 = (float) Math.hypot(screenW - cx, cy);
            float d3 = (float) Math.hypot(cx, screenH - cy);
            float d4 = (float) Math.hypot(screenW - cx, screenH - cy);
            float maxR = Math.max(Math.max(d1, d2), Math.max(d3, d4)) * 1.1f;

            final Bitmap finalOldBmp = oldThemeBmp;
            final float finalMaxR = maxR;

            if (toDark) {
                root.postDelayed(() -> {
                    try {
                        // Capture current dark theme at full size
                        View decorView = activity.getWindow().getDecorView();
                        Bitmap newThemeBmp = Bitmap.createBitmap(
                                decorView.getWidth(), decorView.getHeight(), Bitmap.Config.ARGB_8888);
                        decorView.draw(new Canvas(newThemeBmp));

                        // Old light theme as background
                        ImageView oldOverlay = createOverlay(activity, finalOldBmp);
                        // New dark theme on top (will expand)
                        ImageView newOverlay = createOverlay(activity, newThemeBmp);

                        root.addView(oldOverlay);
                        root.addView(newOverlay);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Animator anim = ViewAnimationUtils.createCircularReveal(
                                    newOverlay, cx, cy, 0f, finalMaxR);
                            anim.setDuration(DURATION);
                            anim.setInterpolator(SMOOTH_INTERPOLATOR);
                            anim.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator a) {
                                    cleanup(root, oldOverlay, newOverlay, finalOldBmp, newThemeBmp, file);
                                }
                            });
                            anim.start();
                        } else {
                            cleanup(root, oldOverlay, newOverlay, finalOldBmp, newThemeBmp, file);
                        }
                    } catch (Exception e) {
                        finalOldBmp.recycle();
                        file.delete();
                    }
                }, 32);

            } else {
                ImageView oldOverlay = createOverlay(activity, finalOldBmp);
                root.addView(oldOverlay);

                root.postDelayed(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            Animator anim = ViewAnimationUtils.createCircularReveal(
                                    oldOverlay, cx, cy, finalMaxR, 0f);
                            anim.setDuration(DURATION);
                            anim.setInterpolator(SMOOTH_INTERPOLATOR);
                            anim.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator a) {
                                    root.removeView(oldOverlay);
                                    finalOldBmp.recycle();
                                    file.delete();
                                }
                            });
                            anim.start();
                        } catch (Exception e) {
                            root.removeView(oldOverlay);
                            finalOldBmp.recycle();
                            file.delete();
                        }
                    } else {
                        oldOverlay.animate().alpha(0f).setDuration(DURATION).withEndAction(() -> {
                            root.removeView(oldOverlay);
                            finalOldBmp.recycle();
                            file.delete();
                        }).start();
                    }
                }, 32);
            }
        } catch (Exception e) {
            file.delete();
        }
    }

    private static ImageView createOverlay(Activity activity, Bitmap bmp) {
        ImageView overlay = new ImageView(activity);
        overlay.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        overlay.setImageBitmap(bmp);
        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return overlay;
    }

    private static void cleanup(ViewGroup root, ImageView old, ImageView newV,
            Bitmap oldBmp, Bitmap newBmp, File file) {
        root.removeView(newV);
        root.removeView(old);
        oldBmp.recycle();
        newBmp.recycle();
        file.delete();
    }
}
