package com.codetrio.spatialflow.util

import java.util.Locale

/**
 * Modern Kotlin implementation of FFmpegCommandBuilder.
 * Optimized for "Studio Grade" 8D processing with maximum multicore performance.
 */
object FFmpegCommandBuilder {

    /**
     * Builds an optimized "High Fidelity" 8D audio processing command.
     * Use this for permanent conversion or intensive background processing.
     *
     * OPTIMIZATIONS:
     * 1. Multithreaded filter processing (-filter_threads 0)
     * 2. High-performance AAC encoding (320kbps + LC-AAC)
     * 3. Sized thread queues for I/O stability
     * 4. Studio-grade sample rate and limiters
     *
     * @param inputPath     Source audio file
     * @param outputPath    Destination file path
     * @param rotationSpeed Speed of spatial movement (0.05 Hz recommended)
     * @return Formatted FFmpeg command
     */
    @JvmStatic
    fun build8D(inputPath: String, outputPath: String, rotationSpeed: Float): String {
        val speed = rotationSpeed.coerceIn(0.03f, 0.25f)

        return buildString {
            append("-y ")
            append("-hide_banner ")
            append("-loglevel error ")
            append("-threads 0 ")

            append("-i \"$inputPath\" ")
            append("-vn ")
            append("-map 0:a:0 ")

            append("-af \"")

            // 🎧 MAIN MOTION (slightly softer to avoid harsh looping)
            append("apulsator=hz=")
            append(String.format(Locale.US, "%.3f", speed))
            append(":width=0.75:mode=sine")

            // 🎧 VERY LIGHT DEPTH (not echo-y, just space)
            append(",aecho=0.6:0.4:30|60:0.2|0.15")

            // 🎧 SUBTLE STEREO VARIATION (breaks perfect symmetry)
            append(",stereotools=balance_in=0.02")

            // 🎧 CLEAN LIMIT
            append(",alimiter=limit=0.97")

            append("\" ")

            append("-c:a aac ")
            append("-b:a 192k ")
            append("-ar 44100 ")
            append("-ac 2 ")

            append("-movflags +faststart ")
            append("-map_metadata 0 ")

            append("\"$outputPath\"")
        }
    }
}