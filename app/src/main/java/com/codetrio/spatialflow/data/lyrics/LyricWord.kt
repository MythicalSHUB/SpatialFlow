package com.codetrio.spatialflow.data.lyrics

/**
 * Represents a single word within a synchronized lyric line.
 * Supports background vocal flags for synchronized pairing.
 */
data class LyricWord @JvmOverloads constructor(
    @JvmField val text: String,
    @JvmField val absoluteStartTimeMs: Long,
    @JvmField val durationMs: Long,
    @JvmField val charRange: IntRange = 0..0,
    @JvmField val isBackground: Boolean = false
)
