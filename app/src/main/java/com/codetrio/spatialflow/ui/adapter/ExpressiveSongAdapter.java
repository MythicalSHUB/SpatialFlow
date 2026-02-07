package com.codetrio.spatialflow.ui.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.model.SongItem;
import com.google.android.material.listitem.ListItemCardView;
import com.google.android.material.listitem.ListItemLayout;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Material 3 Expressive RecyclerView adapter for song library.
 * Uses ListItemLayout with segmented styling for proper M3 appearance.
 */
public class ExpressiveSongAdapter extends RecyclerView.Adapter<ExpressiveSongAdapter.SongViewHolder> {

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

    private final List<SongItem> songs = new ArrayList<>();
    private final OnSongClickListener clickListener;
    private OnSongLongClickListener longClickListener;
    private int currentlyPlayingPosition = -1;
    private Set<Long> favoriteIds = new HashSet<>();
    private SortOrder currentSortOrder = SortOrder.A_Z;
    private static final Map<Long, Integer> colorCache = new HashMap<>();

    public ExpressiveSongAdapter(OnSongClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setLongClickListener(OnSongLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_library_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        SongItem song = songs.get(position);
        boolean isFavorite = favoriteIds.contains(song.id);
        boolean isPlaying = position == currentlyPlayingPosition;

        holder.bind(song, isPlaying, isFavorite, position, getItemCount());

        // Click listener on the card
        if (holder.listItemCard != null) {
            holder.listItemCard.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onSongClick(song, holder.getAdapterPosition());
                }
            });

            // Long-press listener - shows action menu
            holder.listItemCard.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (longClickListener != null) {
                    longClickListener.onSongLongClick(song, holder.getAdapterPosition());
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    /**
     * Submit the song list and apply sorting.
     */
    public void submitList(List<SongItem> newSongs) {
        songs.clear();
        if (newSongs != null) {
            songs.addAll(newSongs);
        }
        sortList();
        notifyDataSetChanged();
    }

    /**
     * Change the sort order.
     */
    public void setSortOrder(SortOrder order) {
        if (currentSortOrder != order) {
            currentSortOrder = order;
            sortList();
            notifyDataSetChanged();
        }
    }

    public SortOrder getCurrentSortOrder() {
        return currentSortOrder;
    }

    private void sortList() {
        switch (currentSortOrder) {
            case A_Z:
                Collections.sort(songs, (a, b) -> a.title.compareToIgnoreCase(b.title));
                break;
            case DATE_ADDED:
                Collections.sort(songs, (a, b) -> Long.compare(b.dateAdded, a.dateAdded));
                break;
            case ARTIST:
                Collections.sort(songs, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
                break;
            case DURATION:
                Collections.sort(songs, (a, b) -> Long.compare(b.duration, a.duration));
                break;
        }
    }

    public SongItem getSongAt(int index) {
        if (index >= 0 && index < songs.size()) {
            return songs.get(index);
        }
        return null;
    }

    public List<SongItem> getAllSongs() {
        return new ArrayList<>(songs);
    }

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

    public void setFavorites(Set<Long> favorites) {
        this.favoriteIds = favorites != null ? favorites : new HashSet<>();
        notifyDataSetChanged();
    }

    public int getPositionById(long songId) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == songId) {
                return i;
            }
        }
        return -1;
    }

    // ============== View Holder ==============

    static class SongViewHolder extends RecyclerView.ViewHolder {
        final ListItemLayout listItemLayout;
        final ListItemCardView listItemCard;
        final ImageView ivAlbumArt;
        final MaterialTextView tvTitle;
        final MaterialTextView tvArtist;
        final ImageView ivNowPlaying;
        final ImageView ivFavorite;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            listItemLayout = itemView.findViewById(R.id.listItemLayout);
            listItemCard = itemView.findViewById(R.id.listItemCard);
            ivAlbumArt = itemView.findViewById(R.id.ivAlbumArt);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            ivNowPlaying = itemView.findViewById(R.id.ivNowPlaying);
            ivFavorite = itemView.findViewById(R.id.ivFavorite);
        }

        void bind(SongItem song, boolean isPlaying, boolean isFavorite, int position, int itemCount) {
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

            // Update M3 Expressive appearance based on position (segmented style)
            if (listItemLayout != null) {
                listItemLayout.updateAppearance(position, itemCount);
            }

            // Highlight playing song using Dynamic Album Colors
            if (listItemCard != null) {
                listItemCard.setChecked(isPlaying);

                if (isPlaying) {
                    Integer cachedColor = colorCache.get(song.id);
                    if (cachedColor != null) {
                        applyDynamicColors(cachedColor, isFavorite);
                    } else {
                        // Extract color if not cached
                        Glide.with(ivAlbumArt.getContext())
                                .asBitmap()
                                .load(artUri)
                                .into(new CustomTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(@NonNull Bitmap resource,
                                            @Nullable Transition<? super Bitmap> transition) {
                                        Palette.from(resource).generate(palette -> {
                                            if (palette != null) {
                                                int color = extractBestColor(palette);
                                                colorCache.put(song.id, color);
                                                applyDynamicColors(color, isFavorite);
                                            }
                                        });
                                    }

                                    @Override
                                    public void onLoadCleared(@Nullable Drawable placeholder) {
                                    }
                                });
                        // Fallback while loading
                        applyDefaultPlayingColors(isFavorite);
                    }
                } else {
                    applyDefaultNormalColors(isFavorite);
                }
            }
        }

        private int extractBestColor(Palette palette) {
            boolean isDark = (listItemCard.getContext().getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

            // Pick swatch based on the theme
            Palette.Swatch swatch = isDark ? palette.getLightVibrantSwatch() : palette.getDarkVibrantSwatch();
            if (swatch == null)
                swatch = palette.getVibrantSwatch();
            if (swatch == null)
                swatch = isDark ? palette.getVibrantSwatch() : palette.getMutedSwatch();
            if (swatch == null)
                swatch = palette.getDominantSwatch();

            int color = (swatch != null) ? swatch.getRgb()
                    : MaterialColors.getColor(listItemCard, R.attr.colorPrimary);

            // Subtle contrast adjustment
            double luminance = ColorUtils.calculateLuminance(color);
            if (isDark) {
                // If the color is visible but dark, lighten it slightly (less aggressive)
                if (luminance > 0.05 && luminance < 0.22) {
                    return ColorUtils.blendARGB(color,
                            MaterialColors.getColor(listItemCard, R.attr.colorOnSurface),
                            0.4f);
                }
            } else {
                // If the color is too light for light mode, darken it
                if (luminance > 0.65) {
                    return ColorUtils.blendARGB(color,
                            MaterialColors.getColor(listItemCard, R.attr.colorOnSurface),
                            0.5f);
                }
            }

            return color;
        }

        private void applyDynamicColors(int albumColor, boolean isFavorite) {
            boolean isDark = (listItemCard.getContext().getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

            double luminance = ColorUtils.calculateLuminance(albumColor);
            int finalTitleColor = albumColor;
            int finalBgColor = albumColor;
            int bgAlpha = isDark ? 41 : 31; // Restore pure glassy alpha for standard colors

            // Detection for 'Black Album Art' (unprocessed black)
            if (isDark && luminance < 0.08) {
                finalBgColor = MaterialColors.getColor(listItemCard,
                        R.attr.colorSurfaceContainerHigh); // Professional Deep Grey
                finalTitleColor = MaterialColors.getColor(listItemCard,
                        R.attr.colorOnSurface); // Pure White text for high visibility
                bgAlpha = 220; // More solid background as requested for black art
            } else if (!isDark && luminance > 0.9) {
                // Almost white in light mode
                finalBgColor = MaterialColors.getColor(listItemCard,
                        R.attr.colorSurfaceContainerHigh);
            }

            // Apply background tint
            int bgTint = ColorUtils.setAlphaComponent(finalBgColor, bgAlpha);
            listItemCard.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(bgTint));

            // Apply text and icon colors
            android.content.res.ColorStateList accentList = android.content.res.ColorStateList.valueOf(finalTitleColor);
            tvTitle.setTextColor(finalTitleColor);
            tvTitle.setTypeface(
                    android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

            tvArtist.setTextColor(finalTitleColor);
            tvArtist.setAlpha(0.7f); // Back to original subtle alpha for artist

            if (ivNowPlaying != null) {
                ivNowPlaying.setImageTintList(accentList);
            }
            if (ivFavorite != null && isFavorite) {
                ivFavorite.setImageTintList(accentList);
            }
        }

        private void applyDefaultPlayingColors(boolean isFavorite) {
            int primary = MaterialColors.getColor(tvTitle, R.attr.colorPrimary);
            int container = MaterialColors.getColor(listItemCard,
                    R.attr.colorPrimaryContainer);

            android.content.res.ColorStateList primaryList = android.content.res.ColorStateList.valueOf(primary);

            listItemCard.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(container));
            tvTitle.setTextColor(primary);
            tvTitle.setTypeface(
                    android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            tvArtist.setTextColor(primary);
            tvArtist.setAlpha(0.75f);

            if (ivNowPlaying != null) {
                ivNowPlaying.setImageTintList(primaryList);
            }
            if (ivFavorite != null && isFavorite) {
                ivFavorite.setImageTintList(primaryList);
            }
        }

        private void applyDefaultNormalColors(boolean isFavorite) {
            int onSurface = MaterialColors.getColor(tvTitle, R.attr.colorOnSurface);
            int onSurfaceVar = MaterialColors.getColor(tvArtist,
                    R.attr.colorOnSurfaceVariant);
            int surfaceLow = MaterialColors.getColor(listItemCard,
                    R.attr.colorSurfaceContainerLow);
            int primary = MaterialColors.getColor(tvTitle, R.attr.colorPrimary);

            listItemCard.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(surfaceLow));
            tvTitle.setTextColor(onSurface);
            tvTitle.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            tvArtist.setTextColor(onSurfaceVar);
            tvArtist.setAlpha(1.0f);

            if (ivNowPlaying != null) {
                ivNowPlaying.setVisibility(View.GONE);
            }
            if (ivFavorite != null) {
                if (isFavorite) {
                    ivFavorite.setVisibility(View.VISIBLE);
                    ivFavorite.setImageTintList(android.content.res.ColorStateList.valueOf(primary));
                } else {
                    ivFavorite.setVisibility(View.GONE);
                }
            }
        }
    }
}
