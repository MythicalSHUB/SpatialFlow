package com.codetrio.spatialflow.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class to manage persistent favorite songs.
 */
public class FavoritesManager {

    private static final String PREF_NAME = "spatialflow_favorites";
    private static final String KEY_FAVORITES = "favorite_song_ids";

    private final SharedPreferences prefs;

    public FavoritesManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(long songId) {
        Set<String> favorites = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
        return favorites.contains(String.valueOf(songId));
    }

    public void setFavorite(long songId, boolean isFavorite) {
        Set<String> favorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
        String idStr = String.valueOf(songId);

        if (isFavorite) {
            favorites.add(idStr);
        } else {
            favorites.remove(idStr);
        }

        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }

    public Set<String> getFavoriteIds() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }
}
