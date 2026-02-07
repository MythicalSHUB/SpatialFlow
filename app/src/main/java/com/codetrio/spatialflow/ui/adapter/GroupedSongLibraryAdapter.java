package com.codetrio.spatialflow.ui.adapter;

import android.net.Uri;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.model.SongItem;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RecyclerView adapter for grouped song library with section headers.
 * Supports A-Z, Date Added, Artist, and Duration sorting with Material 3
 * Expressive styling.
 */
public class GroupedSongLibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SONG = 1;

    public enum SortOrder {
        A_Z,
        DATE_ADDED,
        ARTIST,
        DURATION
    }

    public interface OnSongClickListener {
        void onSongClick(SongItem song, int position);
    }

    public interface OnSongLongClickListener {
        void onSongLongClick(SongItem song, int position);
    }

    private final List<Object> items = new ArrayList<>(); // Mix of String headers and SongItem
    private final List<SongItem> allSongs = new ArrayList<>();
    private final OnSongClickListener clickListener;
    private OnSongLongClickListener longClickListener;
    private int currentlyPlayingPosition = -1;
    private Set<Long> favoriteIds = new HashSet<>();
    private SortOrder currentSortOrder = SortOrder.A_Z;

    public GroupedSongLibraryAdapter(OnSongClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setLongClickListener(OnSongLongClickListener listener) {
        this.longClickListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_SONG;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_library_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_library_song, parent, false);
            return new SongViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) items.get(position));
        } else if (holder instanceof SongViewHolder) {
            SongItem song = (SongItem) items.get(position);
            boolean isFavorite = favoriteIds.contains(song.id);
            int songIndex = getSongIndexInAllSongs(song);
            boolean isPlaying = songIndex == currentlyPlayingPosition;

            ((SongViewHolder) holder).bind(song, isPlaying, isFavorite);

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onSongClick(song, songIndex);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    longClickListener.onSongLongClick(song, songIndex);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Submit the full song list and apply grouping based on current sort order.
     */
    public void submitList(List<SongItem> songs) {
        allSongs.clear();
        if (songs != null) {
            allSongs.addAll(songs);
        }
        sortAndGroup();
    }

    /**
     * Change the sort order and regroup the list.
     */
    public void setSortOrder(SortOrder order) {
        if (currentSortOrder != order) {
            currentSortOrder = order;
            sortAndGroup();
        }
    }

    public SortOrder getCurrentSortOrder() {
        return currentSortOrder;
    }

    private void sortAndGroup() {
        items.clear();

        if (allSongs.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        List<SongItem> sortedSongs = new ArrayList<>(allSongs);
        Map<String, List<SongItem>> groups = new LinkedHashMap<>();

        switch (currentSortOrder) {
            case A_Z:
                Collections.sort(sortedSongs, (a, b) -> a.title.compareToIgnoreCase(b.title));
                for (SongItem song : sortedSongs) {
                    String header = song.title.isEmpty() ? "#" : song.title.substring(0, 1).toUpperCase();
                    if (!Character.isLetter(header.charAt(0))) {
                        header = "#";
                    }
                    groups.computeIfAbsent(header, k -> new ArrayList<>()).add(song);
                }
                break;

            case DATE_ADDED:
                Collections.sort(sortedSongs, (a, b) -> Long.compare(b.dateAdded, a.dateAdded)); // Newest first
                for (SongItem song : sortedSongs) {
                    String header = getDateHeader(song.dateAdded);
                    groups.computeIfAbsent(header, k -> new ArrayList<>()).add(song);
                }
                break;

            case ARTIST:
                Collections.sort(sortedSongs, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
                for (SongItem song : sortedSongs) {
                    String header = song.artist.isEmpty() ? "Unknown Artist" : song.artist;
                    groups.computeIfAbsent(header, k -> new ArrayList<>()).add(song);
                }
                break;

            case DURATION:
                Collections.sort(sortedSongs, (a, b) -> Long.compare(b.duration, a.duration)); // Longest first
                for (SongItem song : sortedSongs) {
                    String header = getDurationHeader(song.duration);
                    groups.computeIfAbsent(header, k -> new ArrayList<>()).add(song);
                }
                break;
        }

        // Build items list with headers and songs
        for (Map.Entry<String, List<SongItem>> entry : groups.entrySet()) {
            items.add(entry.getKey()); // Header
            items.addAll(entry.getValue()); // Songs
        }

        notifyDataSetChanged();
    }

    private String getDateHeader(long dateAddedSeconds) {
        long now = System.currentTimeMillis() / 1000;
        long diff = now - dateAddedSeconds;

        long days = diff / (60 * 60 * 24);

        if (days < 1)
            return "Today";
        if (days < 2)
            return "Yesterday";
        if (days < 7)
            return "This Week";
        if (days < 30)
            return "This Month";
        if (days < 365)
            return "This Year";
        return "Older";
    }

    private String getDurationHeader(long durationMs) {
        long minutes = durationMs / 60000;

        if (minutes < 2)
            return "Under 2 min";
        if (minutes < 4)
            return "2-4 min";
        if (minutes < 6)
            return "4-6 min";
        if (minutes < 10)
            return "6-10 min";
        return "Over 10 min";
    }

    private int getSongIndexInAllSongs(SongItem song) {
        for (int i = 0; i < allSongs.size(); i++) {
            if (allSongs.get(i).id == song.id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the original song at the given index in allSongs.
     */
    public SongItem getSongAt(int index) {
        if (index >= 0 && index < allSongs.size()) {
            return allSongs.get(index);
        }
        return null;
    }

    public List<SongItem> getAllSongs() {
        return new ArrayList<>(allSongs);
    }

    public void setCurrentlyPlaying(int position) {
        int oldPosition = currentlyPlayingPosition;
        currentlyPlayingPosition = position;

        // Refresh only affected items in the grouped list
        notifyDataSetChanged();
    }

    public void setFavorites(Set<Long> favorites) {
        this.favoriteIds = favorites != null ? favorites : new HashSet<>();
        notifyDataSetChanged();
    }

    public int getPositionById(long songId) {
        for (int i = 0; i < allSongs.size(); i++) {
            if (allSongs.get(i).id == songId) {
                return i;
            }
        }
        return -1;
    }

    // ============== View Holders ==============

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final MaterialTextView tvHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvSectionHeader);
        }

        void bind(String headerText) {
            tvHeader.setText(headerText);
        }
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivAlbumArt;
        private final MaterialTextView tvTitle;
        private final MaterialTextView tvArtist;
        private final ImageView ivNowPlaying;
        private final ImageView ivFavorite;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAlbumArt = itemView.findViewById(R.id.ivAlbumArt);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            ivNowPlaying = itemView.findViewById(R.id.ivNowPlaying);
            ivFavorite = itemView.findViewById(R.id.ivFavorite);
        }

        void bind(SongItem song, boolean isPlaying, boolean isFavorite) {
            tvTitle.setText(song.title);
            tvArtist.setText(song.artist);

            // Load album art
            Uri artUri = song.getAlbumArtUri();
            Glide.with(ivAlbumArt.getContext())
                    .load(artUri)
                    .placeholder(R.drawable.default_album_art)
                    .error(R.drawable.default_album_art)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(ivAlbumArt);

            // Show/hide now playing indicator
            if (ivNowPlaying != null) {
                ivNowPlaying.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
            }

            // Show/hide favorite indicator
            if (ivFavorite != null) {
                ivFavorite.setVisibility(isFavorite ? View.VISIBLE : View.GONE);
            }

            // Highlight currently playing item
            itemView.setActivated(isPlaying);
        }
    }
}
