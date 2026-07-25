package com.codetrio.spatialflow.data.lyrics.providers.paxsenix

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.data.lyrics.PaxsenixLyrics
import com.codetrio.spatialflow.data.lyrics.TrackMetadata
import com.codetrio.spatialflow.data.lyrics.providers.LyricsProvider

/**
 * Apple Music lyrics provider via Paxsenix.
 *
 * Optimised for **word-by-word** (TTML) fetching. The pipeline:
 * 1. Calls [PaxsenixLyrics.getAppleMusicLyrics] which now returns a
 *    colon-delimited composite string:  `TTML_CONTENT|!PLAIN!|plain_text`
 *    so this provider can store **both** synced (word-level) **and** plain lyrics.
 * 2. If only LRC is returned store it as synced without the word-by-word flag.
 * 3. If only plain text is returned store it as plain-only.
 */
class PaxsenixAppleMusicProvider(private val context: Context) : LyricsProvider {
    companion object {
        private const val TAG = "PaxsenixAppleMusic"
    }

    override fun getName(): String = "Paxsenix: Apple Music"
    override fun getPriority(): Int = 1

    override fun search(track: TrackMetadata): LyricsResult? {
        return try {
            val composite = PaxsenixLyrics.getAppleMusicLyrics(
                context, track.cleanedTitle, track.cleanedArtist
            ) ?: return null

            val result = LyricsResult(
                providerName = getName(),
                matchedTitle = track.cleanedTitle,
                matchedArtist = track.cleanedArtist
            )

            // ── Parse composite format ────────────────────────────────────
            // "TTML_CONTENT|!PLAIN!|plain_text"
            // "LRC_CONTENT"
            // "plain_text"
            val plainDelim = "|!PLAIN!|"
            val ttmlEndIdx = composite.indexOf(plainDelim)

            if (ttmlEndIdx >= 0) {
                // ── Composite: TTML + plain ──
                val ttml = composite.substring(0, ttmlEndIdx)
                val plain = composite.substring(ttmlEndIdx + plainDelim.length)

                result.setSyncedLyrics(ttml)
                result.setWordByWord(true)   // TTML from Apple Music is always word-level
                if (plain.isNotBlank()) {
                    result.setPlainLyrics(plain)
                }
            } else {
                // ── Single-format fallback ──
                val trimmed = composite.trimStart()
                when {
                    trimmed.startsWith("<tt") || trimmed.startsWith("<?xml") -> {
                        result.setSyncedLyrics(composite)
                        result.setWordByWord(true)
                    }
                    trimmed.contains("[") && trimmed.contains("]:") || trimmed.contains("[00:") -> {
                        result.setSyncedLyrics(composite)
                        if (PaxsenixLyrics.isWordByWord(composite)) {
                            result.setWordByWord(true)
                        }
                    }
                    else -> {
                        result.setPlainLyrics(composite)
                    }
                }
            }

            result
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
            null
        }
    }
}
