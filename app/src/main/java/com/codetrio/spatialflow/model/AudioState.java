package com.codetrio.spatialflow.model;

import android.net.Uri;

public class AudioState {

    private Uri songUri;
    private boolean isPlaying;
    private int currentPosition;
    private int duration;
    private boolean is8DEnabled;
    private boolean isBassEnabled;
    private float speed8D;
    private int bassBoost;
    private boolean isProcessing;

    public AudioState() {
        this.isPlaying = false;
        this.currentPosition = 0;
        this.duration = 0;
        this.is8DEnabled = false;
        this.isBassEnabled = false;
        this.speed8D = 0.5f;
        this.bassBoost = 0;
        this.isProcessing = false;
    }

    // Getters and Setters
    public Uri getSongUri() { return songUri; }
    public void setSongUri(Uri songUri) { this.songUri = songUri; }

    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { isPlaying = playing; }

    public int getCurrentPosition() { return currentPosition; }
    public void setCurrentPosition(int currentPosition) { this.currentPosition = currentPosition; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public boolean is8DEnabled() { return is8DEnabled; }
    public void set8DEnabled(boolean enabled) { is8DEnabled = enabled; }

    public boolean isBassEnabled() { return isBassEnabled; }
    public void setBassEnabled(boolean enabled) { isBassEnabled = enabled; }

    public float getSpeed8D() { return speed8D; }
    public void setSpeed8D(float speed8D) { this.speed8D = speed8D; }

    public int getBassBoost() { return bassBoost; }
    public void setBassBoost(int bassBoost) { this.bassBoost = bassBoost; }

    public boolean isProcessing() { return isProcessing; }
    public void setProcessing(boolean processing) { isProcessing = processing; }
}
