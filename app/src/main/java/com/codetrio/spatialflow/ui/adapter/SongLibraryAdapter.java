package com.codetrio.spatialflow.ui.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.model.SongItem;
import com.google.android.material.textview.MaterialTextView;

import java.util.HashSet;
import java.util.Set;

/**
 * RecyclerView adapter for the song library.
 * Uses ListAdapter with DiffUtil for efficient updates.
 */
public class SongLibraryAdapter extends ListAdapter<SongItem, SongLibraryAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(SongItem song, int position);
    }

    private final OnSongClickListener listener;
    private int currentlyPlayingPosition = -1;
    private Set<Long> favoriteIds = new HashSet<>();

    public SongLibraryAdapter(OnSongClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<SongItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<SongItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull SongItem oldItem, @NonNull SongItem newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull SongItem oldItem, @NonNull SongItem newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_library_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        SongItem song = getItem(position);
        boolean isFavorite = favoriteIds.contains(song.id);
        holder.bind(song, position == currentlyPlayingPosition, isFavorite);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSongClick(song, holder.getAdapterPosition());
            }
        });
    }

    /**
     * Update the currently playing song indicator.
     */
    public void setCurrentlyPlaying(int position) {
        int oldPosition = currentlyPlayingPosition;
        currentlyPlayingPosition = position;

        if (oldPosition >= 0 && oldPosition < getItemCount()) {
            notifyItemChanged(oldPosition);
        }
        if (position >= 0 && position < getItemCount()) {
            notifyItemChanged(position);
        }
    }

    /**
     * Update the set of favorite song IDs.
     */
    public void setFavorites(Set<Long> favorites) {
        this.favoriteIds = favorites != null ? favorites : new HashSet<>();
        notifyDataSetChanged();
    }

    /**
     * Get position of a song by its ID.
     */
    public int getPositionById(long songId) {
        for (int i = 0; i < getItemCount(); i++) {
            if (getItem(i).id == songId) {
                return i;
            }
        }
        return -1;
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
