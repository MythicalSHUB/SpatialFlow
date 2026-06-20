package com.codetrio.spatialflow.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.AppWidgetTarget;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.model.SongItem;

import java.util.Objects;

/**
 * Base Widget Provider for SpatialFlow.
 * Handles common playback control intents and updates all 3 widget sizes.
 */
public class SpatialFlowWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "SpatialFlowWidget";

    // Intent Actions from AudioPlaybackService (must match exactly)
    private static final String ACTION_PLAY = "com.codetrio.spatialflow.ACTION_PLAY";
    private static final String ACTION_PAUSE = "com.codetrio.spatialflow.ACTION_PAUSE";
    private static final String ACTION_PREVIOUS = "com.codetrio.spatialflow.ACTION_PREVIOUS";
    private static final String ACTION_NEXT = "com.codetrio.spatialflow.ACTION_NEXT";
    private static final String ACTION_TOGGLE_LYRICS = "com.codetrio.spatialflow.ACTION_TOGGLE_LYRICS";
    
    private static final String PREFS_NAME = "spatialflow_widget_prefs";
    private static final String PREF_LYRICS_MODE = "lyrics_mode_";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        int layoutId = R.layout.widget_medium;
        Class<?> providerClass = WidgetMediumProvider.class;
        if (this instanceof WidgetSmallProvider) {
            layoutId = R.layout.widget_small;
            providerClass = WidgetSmallProvider.class;
        } else if (this instanceof WidgetLargeProvider) {
            layoutId = R.layout.widget_large;
            providerClass = WidgetLargeProvider.class;
        }
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null, false, 0, 0, null, layoutId, providerClass);
        }
    }

    public static void updateAllWidgets(Context context, SongItem currentSong, boolean isPlaying, int position,
            int duration, String currentLyric) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        new Handler(Looper.getMainLooper()).post(() -> {
            // Update Small Widgets
            int[] smallIds = manager.getAppWidgetIds(new ComponentName(context, WidgetSmallProvider.class));
            for (int id : smallIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, R.layout.widget_small, WidgetSmallProvider.class);
            }

            // Update Medium Widgets
            int[] mediumIds = manager.getAppWidgetIds(new ComponentName(context, WidgetMediumProvider.class));
            for (int id : mediumIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, R.layout.widget_medium, WidgetMediumProvider.class);
            }

            // Update Large Widgets
            int[] largeIds = manager.getAppWidgetIds(new ComponentName(context, WidgetLargeProvider.class));
            for (int id : largeIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, R.layout.widget_large, WidgetLargeProvider.class);
            }
        });
    }

    public static void updateAllWidgetsPartial(Context context, int position, int duration, String currentLyric) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        new Handler(Looper.getMainLooper()).post(() -> {
            // Update Medium Progress
            int[] mediumIds = manager.getAppWidgetIds(new ComponentName(context, WidgetMediumProvider.class));
            for (int id : mediumIds) {
                updateWidgetProgress(context, manager, id, position, duration, currentLyric, R.layout.widget_medium);
            }

            // Update Large Progress
            int[] largeIds = manager.getAppWidgetIds(new ComponentName(context, WidgetLargeProvider.class));
            for (int id : largeIds) {
                updateWidgetProgress(context, manager, id, position, duration, null, R.layout.widget_large);
            }
        });
    }

    private static void updateWidgetProgress(Context context, AppWidgetManager manager, int id, int position, int duration, String currentLyric, int layoutId) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
            if (duration > 0 && layoutId != R.layout.widget_small) {
                int progress = (int) (((float) position / duration) * 100);
                views.setProgressBar(R.id.widget_progress, 100, progress, false);
            }
            if (currentLyric != null && layoutId == R.layout.widget_medium) {
                views.setTextViewText(R.id.widget_lyrics_text, currentLyric);
            }
            manager.partiallyUpdateAppWidget(id, views);
        } catch (Exception e) {
            Log.e(TAG, "Error in partial update: " + e.getMessage());
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int id, SongItem song,
            boolean isPlaying, int position, int duration, String currentLyric, int layoutId, Class<?> providerClass) {
        try {
            RemoteViews views = hideSkeleton(context, layoutId);

            // Basic Text and State
            if (song != null) {
                if (layoutId == R.layout.widget_medium || layoutId == R.layout.widget_large) {
                    views.setTextViewText(R.id.widget_song_title, song.title);
                    views.setTextViewText(R.id.widget_artist_name, song.artist);
                }

                int playIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
                views.setImageViewResource(R.id.widget_play_pause, playIcon);

                // --- APPLY TINTS PROGRAMMATICALLY ---
                // User requested all controls to be White
                int controlColor = android.graphics.Color.WHITE;

                views.setInt(R.id.widget_play_pause, "setColorFilter", controlColor);

                if (layoutId == R.layout.widget_medium) {
                    views.setInt(R.id.widget_prev, "setColorFilter", controlColor);
                    views.setInt(R.id.widget_next, "setColorFilter", controlColor);
                    views.setInt(R.id.widget_toggle_lyrics, "setColorFilter", controlColor);

                    if (duration > 0) {
                        int progress = (int) (((float) position / duration) * 100);
                        views.setProgressBar(R.id.widget_progress, 100, progress, false);
                    }

                    // Set Lyric Text
                    views.setTextViewText(R.id.widget_lyrics_text, currentLyric != null ? currentLyric : "...");
                    
                    // Persistent View State: Ensure the correct slide is shown during updates
                    boolean isLyricsMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .getBoolean(PREF_LYRICS_MODE + id, false);
                    views.setDisplayedChild(R.id.widget_flipper, isLyricsMode ? 1 : 0);
                } else if (layoutId == R.layout.widget_large) {
                    views.setInt(R.id.widget_prev, "setColorFilter", controlColor);
                    views.setInt(R.id.widget_next, "setColorFilter", controlColor);
                    views.setInt(R.id.widget_favorite, "setColorFilter", controlColor);
                    views.setInt(R.id.widget_repeat, "setColorFilter", controlColor);

                    if (duration > 0) {
                        int progress = (int) (((float) position / duration) * 100);
                        views.setProgressBar(R.id.widget_progress, 100, progress, false);
                    }
                }

                // Setup Intents with the correct Context for isPlaying
                setupPendingIntents(context, views, isPlaying, layoutId, providerClass);

                // Load Art and Aura
                loadAlbumArt(context, views, song.getAlbumArtUri(), id);
                if (layoutId == R.layout.widget_small || layoutId == R.layout.widget_medium) {
                    loadBlurredAura(context, views, song.getAlbumArtUri(), id);
                }
            } else {
                if (layoutId == R.layout.widget_medium || layoutId == R.layout.widget_large) {
                    views.setTextViewText(R.id.widget_song_title, "SpatialFlow");
                    views.setTextViewText(R.id.widget_artist_name, "Select a song");
                }
                if (layoutId == R.layout.widget_medium || layoutId == R.layout.widget_large) {
                    views.setProgressBar(R.id.widget_progress, 100, 0, false);
                }
                setupPendingIntents(context, views, false, layoutId, providerClass);
            }

            manager.updateAppWidget(id, views);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update widget " + id + ": " + e.getMessage());
        }
    }

    public static String findCurrentLyricLine(java.util.List<com.codetrio.spatialflow.data.lyrics.LyricLine> lyrics,
            int position) {
        if (lyrics == null || lyrics.isEmpty())
            return null;

        String currentLine = lyrics.get(0).content;
        for (com.codetrio.spatialflow.data.lyrics.LyricLine line : lyrics) {
            if (line.startTimeMs <= position) {
                currentLine = line.content;
            } else {
                break;
            }
        }
        return currentLine;
    }

    private static RemoteViews hideSkeleton(Context context, int layoutId) {
        return new RemoteViews(context.getPackageName(), layoutId);
    }

    private static void loadAlbumArt(Context context, RemoteViews views, Uri artUri, int widgetId) {
        AppWidgetTarget target = new AppWidgetTarget(context, R.id.widget_album_art, views, widgetId);

        Glide.with(context.getApplicationContext())
                .asBitmap()
                .load(artUri)
                .placeholder(R.drawable.default_album_art)
                .error(R.drawable.default_album_art)
                .centerCrop()
                .into(target);
    }

    private static void loadBlurredAura(Context context, RemoteViews views, Uri artUri, int widgetId) {
        AppWidgetTarget auraTarget = new AppWidgetTarget(context, R.id.widget_aura, views, widgetId);

        Glide.with(context.getApplicationContext())
                .asBitmap()
                .load(artUri)
                .centerCrop()
                .override(150, 150) // Scale down for performance
                .transform(new com.bumptech.glide.load.resource.bitmap.BitmapTransformation() {
                    @Override
                    protected Bitmap transform(@NonNull com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool pool,
                            @NonNull Bitmap toTransform, int outWidth, int outHeight) {
                        // 1. Zoom in (2x)
                        int zoomWidth = toTransform.getWidth() / 2;
                        int zoomHeight = toTransform.getHeight() / 2;
                        Bitmap zoomed = Bitmap.createBitmap(toTransform,
                                zoomWidth / 2, zoomHeight / 2, zoomWidth, zoomHeight);

                        // 2. Multi-pass deep blur
                        return blur(zoomed);
                    }

                    @Override
                    public void updateDiskCacheKey(@NonNull java.security.MessageDigest messageDigest) {
                    }
                })
                .into(auraTarget);
    }

    private static Bitmap blur(Bitmap image) {
        if (null == image)
            return null;

        Bitmap outputBitmap = Bitmap.createBitmap(image);
        // Simple fast box blur for widgets (RenderScript is deprecated and sometimes
        // unstable in background)
        return fastblur(outputBitmap);
    }

    private static Bitmap fastblur(Bitmap sentBitmap) {
        int width = Math.round(sentBitmap.getWidth() * (float) 1.0);
        int height = Math.round(sentBitmap.getHeight() * (float) 1.0);
        sentBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false);

        Bitmap bitmap = sentBitmap.copy(Objects.requireNonNull(sentBitmap.getConfig()), true);

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = 25 + 25 + 1;

        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] temp = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            temp[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = 25 + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -25; i <= 25; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + 25];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = 25;

            for (x = 0; x < w; x++) {

                r[yi] = temp[rsum];
                g[yi] = temp[gsum];
                b[yi] = temp[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - 25 + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + 25 + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -25 * w;
            for (i = -25; i <= 25; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + 25];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = 25;
            for (y = 0; y < h; y++) {
                // Preserve alpha or set fully opaque? Background aura should probably be opaque
                // but alpha is handled by layout
                pix[yi] = (0xff000000 & pix[yi]) | (temp[rsum] << 16) | (temp[gsum] << 8) | temp[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - 25 + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + 25 + 1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);

        return (bitmap);
    }

    private static void setupPendingIntents(Context context, RemoteViews views, boolean isPlaying, int layoutId, Class<?> providerClass) {
        // App Launch Intent (On Art)
        Intent launchIntent = new Intent(context, com.codetrio.spatialflow.MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piLaunch = PendingIntent.getActivity(context, 0, launchIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        views.setOnClickPendingIntent(R.id.widget_album_art, piLaunch);
        
        // Lyrics Toggle (Medium Widget only)
        if (layoutId == R.layout.widget_medium) {
            Intent toggleIntent = new Intent(context, providerClass);
            toggleIntent.setAction(ACTION_TOGGLE_LYRICS);
            PendingIntent piToggle = PendingIntent.getBroadcast(context, 20, toggleIntent, 
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.widget_toggle_lyrics, piToggle);
            views.setOnClickPendingIntent(R.id.widget_lyrics_text, piToggle);
        }
        
        // Playback Controls
        PendingIntent piPlayPause = getServicePendingIntent(context, isPlaying ? ACTION_PAUSE : ACTION_PLAY, 10);
        views.setOnClickPendingIntent(R.id.widget_play_pause, piPlayPause);
        
        if (layoutId == R.layout.widget_medium || layoutId == R.layout.widget_large) {
            PendingIntent piPrev = getServicePendingIntent(context, ACTION_PREVIOUS, 11);
            views.setOnClickPendingIntent(R.id.widget_prev, piPrev);

            PendingIntent piNext = getServicePendingIntent(context, ACTION_NEXT, 12);
            views.setOnClickPendingIntent(R.id.widget_next, piNext);
        }

        if (layoutId == R.layout.widget_large) {
            // Favorite Button Intent
            PendingIntent piFav = getServicePendingIntent(context, "com.codetrio.spatialflow.ACTION_TOGGLE_FAV", 13);
            views.setOnClickPendingIntent(R.id.widget_favorite, piFav);

            // Repeat/Loop Button Intent
            PendingIntent piLoop = getServicePendingIntent(context, "com.codetrio.spatialflow.ACTION_TOGGLE_LOOP", 14);
            views.setOnClickPendingIntent(R.id.widget_repeat, piLoop);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TOGGLE_LYRICS.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, WidgetMediumProvider.class));
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            for (int id : ids) {
                // Toggle the saved state
                boolean currentMode = prefs.getBoolean(PREF_LYRICS_MODE + id, false);
                prefs.edit().putBoolean(PREF_LYRICS_MODE + id, !currentMode).apply();
                
                // Trigger a full update to reflect the state change immediately
                // We'll use the last known data or just refresh the skeleton
                RemoteViews views = hideSkeleton(context, R.layout.widget_medium);
                views.setDisplayedChild(R.id.widget_flipper, !currentMode ? 1 : 0);
                manager.partiallyUpdateAppWidget(id, views);
            }
        }
    }

    private static PendingIntent getServicePendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, com.codetrio.spatialflow.service.AudioPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, requestCode, intent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
