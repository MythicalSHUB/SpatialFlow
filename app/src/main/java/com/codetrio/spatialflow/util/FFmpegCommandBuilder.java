package com.codetrio.spatialflow.util;

import android.util.Log;

public class FFmpegCommandBuilder {

    private static final String TAG = "FFmpegCommandBuilder";

    // Professional 8D rotation speed constants
    private static final float MIN_ROTATION_SPEED = 0.05f;
    private static final float MAX_ROTATION_SPEED = 0.25f;
    private static final float DEFAULT_ROTATION_SPEED = 0.08f;

    /**
     * Builds optimized professional 8D audio effect with musical reverb.
     * Chain:
     *  1) apulsator  – auto‑panner (0.08 Hz, width 0.75)
     *  2) extrastereo – gentle stereo widening
     *  3) adelay     – Haas depth (10 ms)
     *  4) aecho      – room‑style reverb (preset‑like)
     *
     * @param inputPath     Input audio file path
     * @param outputPath    Output audio file path
     * @param rotationSpeed 8D rotation speed in Hz (0.08 Hz recommended)
     * @return Complete FFmpeg command string
     */
    public static String build8D(String inputPath, String outputPath, float rotationSpeed) {

        // Validate and clamp rotation speed
        rotationSpeed = clampRotationSpeed(rotationSpeed);

        // Use StringBuilder with estimated capacity for better performance
        StringBuilder command = new StringBuilder(320);

        // Basic flags
        command.append("-y")
                .append(" -loglevel warning")
                .append(" -i \"").append(inputPath).append("\"");

        // Audio-only processing (skip video/album art)
        command.append(" -vn")
                .append(" -map 0:a");

        // ===== OPTIMIZED 8D + SMOOTH REVERB FILTER CHAIN =====
        command.append(" -af \"");

        // 1) 8D Auto‑panner – slightly slower, less aggressive width
        command.append("apulsator=hz=")
                .append(String.format("%.2f", rotationSpeed))
                .append(":width=0.75:mode=sine:offset_l=0:offset_r=0.5");

        // 2) Gentle stereo widener
        command.append(",extrastereo=m=1.3:c=false");

        // 3) Very small Haas delay
        command.append(",adelay=delays=0|10:all=0");

        // 4) Shorter, subtler reverb
        command.append(",aecho=0.9:0.9:40|80:0.20|0.15");

        command.append("\"");

        // ===== OPTIMIZED AUDIO ENCODING =====
        command.append(" -c:a alac")
                .append(" -ar 48000")
                .append(" -ac 2")
                .append(" -movflags +faststart")
                .append(" -map_metadata 0");

        // Output file
        command.append(" \"").append(outputPath).append("\"");

        String finalCommand = command.toString();
        Log.d(TAG, "8D Command: " + finalCommand);
        return finalCommand;
    }

    /**
     * Clamps rotation speed to safe professional range.
     *
     * @param speed Desired rotation speed in Hz
     * @return Clamped speed between MIN and MAX
     */
    private static float clampRotationSpeed(float speed) {
        if (speed < MIN_ROTATION_SPEED) {
            Log.w(TAG, "Speed too low (" + speed + " Hz), using default: " + DEFAULT_ROTATION_SPEED + " Hz");
            return DEFAULT_ROTATION_SPEED;
        }
        if (speed > MAX_ROTATION_SPEED) {
            Log.w(TAG, "Speed too high (" + speed + " Hz), clamping to: " + MAX_ROTATION_SPEED + " Hz");
            return MAX_ROTATION_SPEED;
        }
        return speed;
    }

    /**
     * Gets recommended rotation speed for professional 8D effect.
     *
     * @return Default rotation speed (0.08 Hz)
     */
    public static float getDefaultRotationSpeed() {
        return DEFAULT_ROTATION_SPEED;
    }

    /**
     * Validates if a rotation speed is within acceptable range.
     *
     * @param speed Speed to validate
     * @return true if speed is valid, false otherwise
     */
    public static boolean isValidRotationSpeed(float speed) {
        return speed >= MIN_ROTATION_SPEED && speed <= MAX_ROTATION_SPEED;
    }
}