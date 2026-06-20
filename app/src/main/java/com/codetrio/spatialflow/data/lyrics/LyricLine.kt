package com.codetrio.spatialflow.data.lyrics

/**
 * Data model for a single line of synced lyrics.
 * Supports standard line-sync and enhanced word-by-word sync.
 *
 * Uses @JvmField for direct field access from Java (LyricsAdapter, LrcParser callers).
 * Uses @JvmOverloads to generate Java-visible constructor overloads.
 */
data class LyricLine @JvmOverloads constructor(
    @JvmField val startTimeMs: Long,
    @JvmField val content: String,
    @JvmField val isInterlude: Boolean = false,
    @JvmField val isWordByWord: Boolean = false,
    @JvmField val words: List<LyricWord> = emptyList()
) : Comparable<LyricLine> {

    override fun compareTo(other: LyricLine): Int {
        return this.startTimeMs.compareTo(other.startTimeMs)
    }
}

