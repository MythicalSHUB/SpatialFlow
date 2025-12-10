package com.codetrio.spatialflow.util;

import android.util.Log;

public class FFmpegCommandBuilder {

    private static final String TAG = "FFmpegCommandBuilder";

    // Professional 8D rotation speed constants
    private static final float MIN_ROTATION_SPEED = 0.05f;
    private static final float MAX_ROTATION_SPEED = 0.25f;
    private static final float DEFAULT_ROTATION_SPEED = 0.08f;

    /**
     * Builds optimized professional 8D audio effect.
     * Uses minimal filter chain for best performance and clean output.
     *
     * @param inputPath     Input audio file path
     * @param outputPath    Output audio file path
     * @param rotationSpeed 8D rotation speed in Hz (0.08 Hz recommended for smooth circular motion)
     * @return Complete FFmpeg command string
     */
    public static String build8D(String inputPath, String outputPath, float rotationSpeed) {

        // Validate and clamp rotation speed
        rotationSpeed = clampRotationSpeed(rotationSpeed);

        // Use StringBuilder with estimated capacity for better performance
        StringBuilder command = new StringBuilder(256);

        // Basic flags
        command.append("-y")                              // Overwrite output without asking
                .append(" -loglevel warning")              // Minimal logging
                .append(" -i \"").append(inputPath).append("\"");

        // Audio-only processing (skip video/album art)
        command.append(" -vn")                            // No video
                .append(" -map 0:a");                      // Map only audio stream

        // ===== OPTIMIZED 8D FILTER CHAIN =====
        command.append(" -af \"");

        // 1. APULSATOR - Core 8D circular panning effect
        command.append("apulsator=hz=").append(String.format("%.2f", rotationSpeed))
                .append(":width=0.85")                     // 85% panning (professional standard)
                .append(":mode=sine")                      // Smooth sine wave panning
                .append(":offset_l=0:offset_r=0.5");      // 180° phase = true circular motion

        // 2. STEREO WIDENER - Enhanced stereo field
        command.append(",extrastereo=m=2.0:c=false");    // 2.0x width (matches pro 8D)

        // 3. HAAS EFFECT - Spatial depth perception
        command.append(",adelay=delays=0|15:all=0");     // 15ms delay (sweet spot)

        command.append("\"");

        // ===== OPTIMIZED AUDIO ENCODING =====
        command.append(" -c:a aac")                       // AAC codec (best compatibility)
                .append(" -b:a 320k")                      // 320 kbps (high quality)
                .append(" -ar 48000")                      // 48 kHz sample rate
                .append(" -ac 2")                          // Stereo output
                .append(" -movflags +faststart")           // Enable streaming
                .append(" -map_metadata 0");               // Preserve metadata

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
