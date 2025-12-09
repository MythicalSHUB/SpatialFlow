package com.codetrio.spatialflow.util;

import android.util.Log;

public class FFmpegCommandBuilder {

    private static final String TAG = "FFmpegCommandBuilder";

    /**
     * Builds clean professional 8D audio effect.
     * Focuses on smooth circular panning without artifacts.
     *
     * @param inputPath     Input audio file path
     * @param outputPath    Output audio file path
     * @param rotationSpeed 8D rotation speed in Hz (0.08-0.15 recommended)
     * @return Complete FFmpeg command string
     */
    public static String build8D(String inputPath, String outputPath, float rotationSpeed) {

        // Optimal rotation speed for smooth 8D (0.08-0.12 Hz is best)
        if (rotationSpeed < 0.05f) rotationSpeed = 0.08f;
        if (rotationSpeed > 0.25f) rotationSpeed = 0.25f;

        StringBuilder command = new StringBuilder();

        command.append("-y");
        command.append(" -loglevel warning");
        command.append(" -i ").append("\"").append(inputPath).append("\"");

        // Audio-only processing
        command.append(" -vn");
        command.append(" -map 0:a");

        // ===== CLEAN 8D FILTER CHAIN =====
        command.append(" -af ");
        command.append("\"");

        // 1. APULSATOR - Smooth circular auto-panning
        command.append("apulsator=hz=").append(rotationSpeed);
        command.append(":width=0.8");            // 80% panning width (not too extreme)
        command.append(":mode=sine");            // Smooth sine wave
        command.append(":offset_l=0");           // Left channel at 0°
        command.append(":offset_r=0.5");         // Right channel at 180° (circular motion)

        // 2. STEREO WIDENER - Gentle stereo enhancement
        command.append(",extrastereo=m=1.8:c=false");  // Moderate widening

        // 3. SUBTLE SPATIAL DELAY - Light haas effect only
        command.append(",adelay=0|10:all=0");    // 10ms delay on right (very subtle)

        command.append("\"");

        // ===== HIGH QUALITY AUDIO ENCODING =====
        command.append(" -c:a aac");
        command.append(" -b:a 320k");
        command.append(" -ar 48000");
        command.append(" -ac 2");
        command.append(" -map_metadata 0");
        command.append(" -movflags +faststart");
        command.append(" ").append("\"").append(outputPath).append("\"");

        String finalCommand = command.toString();
        Log.d(TAG, "Generated Clean 8D Command: " + finalCommand);
        return finalCommand;
    }
}
