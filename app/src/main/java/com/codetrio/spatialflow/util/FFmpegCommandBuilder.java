package com.codetrio.spatialflow.util;

import android.util.Log;

public class FFmpegCommandBuilder {

    private static final String TAG = "FFmpegCommandBuilder";

    // Professional 8D rotation speed constants
    private static final float MIN_ROTATION_SPEED = 0.03f;
    private static final float MAX_ROTATION_SPEED = 0.25f;
    private static final float DEFAULT_ROTATION_SPEED = 0.05f; // Slower rotation (was 0.08)

    /**
     * Builds optimized professional 8D audio effect with musical reverb.
     *
     * PRESET SETTINGS:
     * Tool 1: Auto Panner
     * - Frequency: 0.05 Hz (slower, smoother rotation)
     * - Amount: 85%
     *
     * Tool 2: Reverb
     * - Reverberance: 50%
     * - Room scale: 100%
     * - HF Damping: 50%
     * - Pre delay: 0 ms
     * - Stereo depth: 100%
     * - Wet gain: 0 dB
     *
     * Chain:
     * 1) apulsator – auto‑panner (0.05 Hz, width 0.85)
     * 2) extrastereo – gentle stereo widening
     * 3) adelay – Haas depth (10 ms)
     * 4) aecho – room‑style reverb (preset‑like)
     *
     * @param inputPath     Input audio file path
     * @param outputPath    Output audio file path
     * @param rotationSpeed 8D rotation speed in Hz (0.05 Hz recommended)
     * @return Complete FFmpeg command string
     */
    public static String build8D(String inputPath, String outputPath, float rotationSpeed) {

        rotationSpeed = clampRotationSpeed(rotationSpeed);
        StringBuilder command = new StringBuilder(512);

        // === MAXIMUM SPEED INPUT ===
        command.append("-y")
                .append(" -threads 0") // Use ALL CPU cores
                .append(" -filter_threads 0") // Parallel filter processing
                .append(" -filter_complex_threads 0") // Parallel complex filters
                .append(" -thread_queue_size 4096") // Large buffer for smooth I/O
                .append(" -loglevel warning")
                .append(" -i \"").append(inputPath).append("\"");

        command.append(" -vn") // Skip video completely
                .append(" -map 0:a:0"); // First audio stream only

        // === CLEAN 8D FILTER CHAIN (No Bass) ===
        command.append(" -af \"");

        // 1) 8D Auto-panner with smooth sine wave rotation
        command.append("apulsator=hz=")
                .append(String.format(java.util.Locale.US, "%.2f", rotationSpeed))
                .append(":width=0.85:mode=sine:offset_l=0:offset_r=0.5");

        // 2) Stereo widening (subtle for cleaner output)
        command.append(",extrastereo=m=1.15:c=false");

        // 3) Haas delay for depth perception
        command.append(",adelay=delays=0|10:all=0");

        // 4) Clean reverb (subtle, musical)
        command.append(",aecho=0.8:0.85:40|80:0.15|0.10");

        // 5) Final limiter to prevent clipping
        command.append(",alimiter=limit=0.95:attack=5:release=50");

        command.append("\"");

        // === FASTEST + HIGHEST QUALITY ENCODING ===
        command.append(" -c:a aac") // Native AAC (fast)
                .append(" -b:a 320k") // Highest bitrate for quality
                .append(" -profile:a aac_low") // LC-AAC (fastest profile)
                .append(" -ar 48000") // Studio sample rate
                .append(" -ac 2") // Stereo
                .append(" -cutoff 20000") // Full frequency
                .append(" -movflags +faststart") // Streaming optimization
                .append(" -map_metadata 0"); // Preserve metadata

        command.append(" \"").append(outputPath).append("\"");

        String finalCommand = command.toString();
        Log.d(TAG, "8D Command (Fast+HQ): " + finalCommand);
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
     * @return Default rotation speed (0.05 Hz - slower, smoother rotation)
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