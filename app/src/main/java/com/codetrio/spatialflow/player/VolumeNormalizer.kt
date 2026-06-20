package com.codetrio.spatialflow.player

import kotlin.math.pow

object VolumeNormalizer {

    /**
     * Calculates the gain required to reach the target LUFS.
     * @param measuredLufs The measured integrated loudness of the track.
     * @param targetLufs The target loudness (e.g., -14.0 for standard normalization).
     * @return Gain in dB to apply.
     */
    fun calculateGain(measuredLufs: Float, targetLufs: Float): Float {
        // Limit maximum boost to prevent severe clipping and distortion
        val gain = targetLufs - measuredLufs
        return gain.coerceIn(-15f, 15f)
    }

    /**
     * Converts dB gain to a linear volume scalar (0.0 to 1.0+).
     * @param db The gain in decibels.
     * @return Linear volume multiplier.
     */
    fun dbToLinear(db: Float): Float {
        return 10.0.pow(db / 20.0).toFloat()
    }
}
