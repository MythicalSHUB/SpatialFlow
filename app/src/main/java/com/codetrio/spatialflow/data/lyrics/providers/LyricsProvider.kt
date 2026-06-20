package com.codetrio.spatialflow.data.lyrics.providers

import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.TrackMetadata

/**
 * Interface for all lyrics providers.
 * Implementations MUST be synchronous — they are called from background threads
 * by ProviderRouter.
 * Return null if no lyrics found (don't throw exceptions).
 */
interface LyricsProvider {

    /**
     * Human-readable provider name for telemetry and UI.
     */
    fun getName(): String

    /**
     * Priority for search ordering. Lower = searched first.
     * Range: 1 (highest) to 100 (lowest).
     * This is the default priority — ProviderStats may override dynamically.
     */
    fun getPriority(): Int

    /**
     * Search for lyrics matching the given track metadata.
     * Called on a background thread. Must be synchronous (blocking I/O is fine).
     *
     * @param track Normalized track metadata
     * @return LyricsResult with lyrics and match metadata, or null if not found
     */
    fun search(track: TrackMetadata): LyricsResult?
}
