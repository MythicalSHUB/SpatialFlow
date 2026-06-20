package com.codetrio.spatialflow.data.lyrics

import android.util.Log
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics

/**
 * Parses lyrics content into a list of [LyricLine]s.
 * Delegate parsing to Gramophone's high-fidelity LrcUtils parser.
 */
object LrcParser {
    private const val TAG = "LrcParser"
    
    // Minimum gap (ms) between two lyric lines to insert an interlude marker
    private const val INTERLUDE_THRESHOLD_MS = 5000L

    @JvmStatic
    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrEmpty()) {
            return emptyList()
        }

        // Use Gramophone's robust parser options: trim=true, multiLine=true
        val parserOptions = LrcUtils.LrcParserOptions(trim = true, multiLine = true, errorText = null)
        val semanticLyrics = try {
            LrcUtils.parseLyrics(lrcContent, null, parserOptions, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing lyrics via Gramophone parser", e)
            null
        } ?: return emptyList()

        val parsedLines = mutableListOf<LyricLine>()
        if (semanticLyrics is SemanticLyrics.SyncedLyrics) {
            for (line in semanticLyrics.text) {
                val words = line.words?.map { word ->
                    val wordText = if (word.charRange.first in 0 until line.text.length &&
                        word.charRange.last in 0 until line.text.length &&
                        word.charRange.first <= word.charRange.last) {
                        line.text.substring(word.charRange)
                    } else {
                        ""
                    }
                                        val duration = (word.endInclusive?.toLong()?.minus(word.begin.toLong()))?.coerceAtLeast(0L) ?: 0L
                    LyricWord(
                        text = wordText,
                        absoluteStartTimeMs = word.begin.toLong(),
                        durationMs = duration,
                        charRange = word.charRange
                    )
                } ?: emptyList()

                parsedLines.add(
                    LyricLine(
                        startTimeMs = line.start.toLong(),
                        content = line.text,
                        isInterlude = false,
                        isWordByWord = words.isNotEmpty(),
                        words = words
                    )
                )
            }
        } else if (semanticLyrics is SemanticLyrics.UnsyncedLyrics) {
            // Unsynced lyrics (plain lyrics)
            for (line in semanticLyrics.unsyncedText) {
                parsedLines.add(
                    LyricLine(
                        startTimeMs = 0L,
                        content = line.first,
                        isInterlude = false,
                        isWordByWord = false,
                        words = emptyList()
                    )
                )
            }
        }

        parsedLines.sort()

        // Insert interlude markers for instrumental gaps
        return insertInterludes(parsedLines)
    }

    private fun insertInterludes(sorted: List<LyricLine>): List<LyricLine> {
        if (sorted.size < 2) return sorted

        val result = mutableListOf<LyricLine>()

        for (i in sorted.indices) {
            val current = sorted[i]

            // Skip empty lines (they're essentially interludes already in LRC)
            if (current.content.isEmpty() && i > 0) {
                val nextTime = if (i + 1 < sorted.size) sorted[i + 1].startTimeMs else current.startTimeMs
                val prevTime = sorted[i - 1].startTimeMs
                if (nextTime - prevTime > INTERLUDE_THRESHOLD_MS) {
                    result.add(LyricLine(current.startTimeMs, "♪", true))
                    continue
                }
                continue
            }

            result.add(current)

            // Check gap to next line
            if (i + 1 < sorted.size) {
                val gap = sorted[i + 1].startTimeMs - current.startTimeMs
                if (gap > INTERLUDE_THRESHOLD_MS && sorted[i + 1].content.isNotEmpty()) {
                    val padding = (gap / 3).coerceAtMost(3500)
                    val interludeTime = current.startTimeMs + padding
                    result.add(LyricLine(interludeTime, "♪", true))
                }
            }
        }

        return result
    }
}
