package com.codetrio.spatialflow.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.codetrio.spatialflow.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SongPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnSongSelectedListener {
        void onSongSelected(String title, String artist, String path);
    }

    private OnSongSelectedListener listener;

    public void setOnSongSelectedListener(OnSongSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.bottom_sheet_song_list, container, false);

        RecyclerView rvSongs = view.findViewById(R.id.rvSongs);
        rvSongs.setLayoutManager(new LinearLayoutManager(getContext()));

        List<SongItem> songs = loadSongs(requireContext());
        rvSongs.setAdapter(new SongsAdapter(songs, (title, artist, path) -> {
            if (listener != null) {
                listener.onSongSelected(title, artist, path);
            }
            dismiss();
        }));

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
                MediaStore.Audio.Media.DATA
        };

        String selection = null;

        Cursor cursor = context.getContentResolver()
                .query(contentUri, projection, selection, null, MediaStore.Audio.Media.TITLE + " ASC");

        if (cursor != null) {
            int idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIdx);
                String title = cursor.getString(titleIdx);
                String artist = cursor.getString(artistIdx);
                long albumId = cursor.getLong(albumIdIdx);
                String path = cursor.getString(dataIdx);

                if (path == null || !(new File(path).exists())) continue;

                result.add(new SongItem(id, title, artist, albumId, path));
            }
            cursor.close();
        }

        return result;
    }

    private static class SongItem {
        final long id;
        final String title;
        final String artist;
        final long albumId;
        final String path;

        SongItem(long id, String title, String artist, long albumId, String path) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.albumId = albumId;
            this.path = path;
        }
    }

    private static class SongsAdapter extends RecyclerView.Adapter<SongsAdapter.VH> {

        interface OnClick {
            void onClick(String title, String artist, String path);
        }

        private final List<SongItem> songs;
        private final OnClick click;

        SongsAdapter(List<SongItem> songs, OnClick click) {
            this.songs = songs;
            this.click = click;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_song, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SongItem s = songs.get(position);
            holder.tvTitle.setText(
                    s.title != null && !s.title.isEmpty() ? s.title : "Unknown title"
            );
            holder.tvArtist.setText(
                    s.artist != null && !s.artist.isEmpty() ? s.artist : "Unknown artist"
            );

            // Load album art via MediaStore albumart content URI
            Uri albumArtBaseUri = Uri.parse("content://media/external/audio/albumart");
            Uri artUri = Uri.withAppendedPath(albumArtBaseUri, String.valueOf(s.albumId));
            try {
                holder.ivAlbumArt.setImageURI(artUri);
                if (holder.ivAlbumArt.getDrawable() == null) {
                    holder.ivAlbumArt.setImageResource(R.drawable.default_album_art);
                }
            } catch (Exception e) {
                holder.ivAlbumArt.setImageResource(R.drawable.default_album_art);
            }

            holder.itemView.setOnClickListener(v ->
                    click.onClick(s.title, s.artist, s.path));
        }

        @Override
        public int getItemCount() {
            return songs.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivAlbumArt;
            MaterialTextView tvTitle;
            MaterialTextView tvArtist;

            @SuppressLint("WrongViewCast")
            VH(@NonNull View itemView) {
                super(itemView);
                ivAlbumArt = itemView.findViewById(R.id.ivAlbumArt);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvArtist = itemView.findViewById(R.id.tvArtist);
            }
        }
    }
}
