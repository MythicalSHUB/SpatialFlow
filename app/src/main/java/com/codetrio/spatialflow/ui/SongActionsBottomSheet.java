package com.codetrio.spatialflow.ui;

import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.codetrio.spatialflow.R;
import com.codetrio.spatialflow.model.SongItem;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;

public class SongActionsBottomSheet extends BottomSheetDialogFragment {

    private SongItem song;
    private ActionListener actionListener;

    public interface ActionListener {
        void onPlayNext(SongItem song);

        void onAddToQueue(SongItem song);

        void onDelete(SongItem song);
    }

    public static SongActionsBottomSheet newInstance(SongItem song) {
        SongActionsBottomSheet fragment = new SongActionsBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable("song", song);
        fragment.setArguments(args);
        return fragment;
    }

    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            song = (SongItem) getArguments().getSerializable("song");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_song_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (song == null) {
            dismiss();
            return;
        }

        // Bind data
        TextView tvTitle = view.findViewById(R.id.tvActionSongName);
        TextView tvArtist = view.findViewById(R.id.tvActionArtistName);
        ShapeableImageView ivArt = view.findViewById(R.id.ivActionAlbumArt);

        tvTitle.setText(song.title);
        tvArtist.setText(song.artist);
        Glide.with(this)
                .load(song.getAlbumArtUri())
                .placeholder(R.drawable.default_album_art)
                .error(R.drawable.default_album_art)
                .into(ivArt);

        // Action Buttons
        FloatingActionButton btnPlayNext = view.findViewById(R.id.fabPlayNext);
        FloatingActionButton btnQueue = view.findViewById(R.id.fabAddToQueue);
        FloatingActionButton btnDelete = view.findViewById(R.id.fabDelete);

        btnPlayNext.setOnClickListener(v -> {
            performHaptic(v);
            if (actionListener != null)
                actionListener.onPlayNext(song);
            dismiss();
        });

        btnQueue.setOnClickListener(v -> {
            performHaptic(v);
            if (actionListener != null)
                actionListener.onAddToQueue(song);
            dismiss();
        });

        btnDelete.setOnClickListener(v -> {
            performHaptic(v);
            if (actionListener != null)
                actionListener.onDelete(song);
            dismiss();
        });
    }

    private void performHaptic(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }
}
