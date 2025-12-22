package com.codetrio.spatialflow.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.codetrio.spatialflow.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SongPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnSongSelectedListener {
        void onSongSelected(String title, String artist, String path);
    }

    private OnSongSelectedListener listener;
    private SongsAdapter adapter;
    private List<SongItem> allSongs;

    public void setOnSongSelectedListener(OnSongSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply the Material 3 style with rounded corners
        setStyle(STYLE_NORMAL, R.style.AppModalStyle);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_song_list, container, false);

        RecyclerView rvSongs = view.findViewById(R.id.rvSongs);
        TextInputEditText etSearch = view.findViewById(R.id.etSearch);
        ChipGroup chipGroupSort = view.findViewById(R.id.chipGroupSort);
        View btnClose = view.findViewById(R.id.btnCancel);

        rvSongs.setLayoutManager(new LinearLayoutManager(getContext()));

        allSongs = loadSongs(requireContext());
        adapter = new SongsAdapter(allSongs, (title, artist, path) -> {
            if (listener != null) listener.onSongSelected(title, artist, path);
            dismiss();
        });
        rvSongs.setAdapter(adapter);

        // --- Search Logic ---
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // --- Sorting Logic ---
        chipGroupSort.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipName) adapter.sortByName();
            else if (id == R.id.chipArtist) adapter.sortByArtist();
            else if (id == R.id.chipDate) adapter.sortByDate();
        });

        btnClose.setOnClickListener(v -> dismiss());

        return view;
    }

    private List<SongItem> loadSongs(Context context) {
        List<SongItem> result = new ArrayList<>();
        Uri contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED
        };

        try (Cursor cursor = context.getContentResolver().query(contentUri, projection, null, null, MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataIdx);
                    if (path == null || !(new File(path).exists())) continue;

                    result.add(new SongItem(
                            cursor.getLong(idIdx),
                            cursor.getString(titleIdx),
                            cursor.getString(artistIdx),
                            cursor.getLong(albumIdIdx),
                            path,
                            cursor.getLong(dateIdx)
                    ));
                }
            }
        }
        return result;
    }

    // --- Static Model ---
    private static class SongItem {
        final long id, albumId, dateAdded;
        final String title, artist, path;

        SongItem(long id, String title, String artist, long albumId, String path, long dateAdded) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.albumId = albumId;
            this.path = path;
            this.dateAdded = dateAdded;
        }
    }

    // --- Optimized Adapter ---
    private static class SongsAdapter extends RecyclerView.Adapter<SongsAdapter.VH> {
        interface OnClick { void onClick(String title, String artist, String path); }

        private final List<SongItem> originalList;
        private final List<SongItem> filteredList;
        private final OnClick click;

        SongsAdapter(List<SongItem> songs, OnClick click) {
            this.originalList = songs;
            this.filteredList = new ArrayList<>(songs);
            this.click = click;
        }

        @SuppressLint("NotifyDataSetChanged")
        void filter(String query) {
            filteredList.clear();
            if (query.isEmpty()) {
                filteredList.addAll(originalList);
            } else {
                String lowerQuery = query.toLowerCase().trim();
                for (SongItem item : originalList) {
                    if ((item.title != null && item.title.toLowerCase().contains(lowerQuery)) ||
                            (item.artist != null && item.artist.toLowerCase().contains(lowerQuery))) {
                        filteredList.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @SuppressLint("NotifyDataSetChanged")
        void sortByName() {
            Collections.sort(filteredList, (a, b) -> a.title.compareToIgnoreCase(b.title));
            notifyDataSetChanged();
        }

        @SuppressLint("NotifyDataSetChanged")
        void sortByArtist() {
            Collections.sort(filteredList, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
            notifyDataSetChanged();
        }

        @SuppressLint("NotifyDataSetChanged")
        void sortByDate() {
            Collections.sort(filteredList, (a, b) -> Long.compare(b.dateAdded, a.dateAdded));
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SongItem s = filteredList.get(position);
            holder.tvTitle.setText(s.title != null ? s.title : "Unknown Title");
            holder.tvArtist.setText(s.artist != null ? s.artist : "Unknown Artist");

            // --- ASYNCHRONOUS IMAGE LOADING (Fixes Lag) ---
            Uri artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), String.valueOf(s.albumId));

            Glide.with(holder.ivAlbumArt.getContext())
                    .load(artUri)
                    .placeholder(R.drawable.default_album_art) // Your music note icon
                    .error(R.drawable.default_album_art)
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache to avoid re-decoding
                    .centerCrop()
                    .into(holder.ivAlbumArt);

            holder.itemView.setOnClickListener(v -> click.onClick(s.title, s.artist, s.path));
        }

        @Override
        public int getItemCount() { return filteredList.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivAlbumArt;
            MaterialTextView tvTitle, tvArtist;

            VH(@NonNull View itemView) {
                super(itemView);
                ivAlbumArt = itemView.findViewById(R.id.ivAlbumArt);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvArtist = itemView.findViewById(R.id.tvArtist);
            }
        }
    }
}