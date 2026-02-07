package com.codetrio.spatialflow.model;

import android.net.Uri;
import java.io.Serializable;

/**
 * Data class representing a song in the library.
 * Used for the song list in PlayerFragment.
 */
public class SongItem implements Serializable {
    public final long id;
    public final String title;
    public final String artist;
    public final long albumId;
    public final String path;
    public final String data; // Alias for path, used by adapter
    public final long dateAdded;
    public final long duration; // Song duration in milliseconds
    public final Uri contentUri;

    /**
     * Constructor with duration
     */
    public SongItem(long id, String title, String artist, long albumId, String path, long duration, long dateAdded) {
        this.id = id;
        this.title = title != null ? title : "Unknown Title";
        this.artist = artist != null ? artist : "Unknown Artist";
        this.albumId = albumId;
        this.path = path;
        this.data = path; // Alias
        this.duration = duration;
        this.dateAdded = dateAdded;
        this.contentUri = Uri.parse("content://media/external/audio/media/" + id);
    }

    /**
     * Legacy constructor without duration (for backwards compatibility)
     */
    public SongItem(long id, String title, String artist, long albumId, String path, long dateAdded) {
        this(id, title, artist, albumId, path, 0, dateAdded);
    }

    /**
     * Get album art URI for this song.
     */
    public Uri getAlbumArtUri() {
        if (albumId <= 0)
            return null;
        return Uri.parse("content://media/external/audio/albumart/" + albumId);
    }

    /**
     * Compare titles for A-Z sorting (case insensitive).
     */
    public int compareByTitle(SongItem other) {
        return this.title.compareToIgnoreCase(other.title);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SongItem songItem = (SongItem) o;
        return id == songItem.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
