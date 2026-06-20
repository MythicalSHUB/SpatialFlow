package com.codetrio.spatialflow.data.lyrics

import java.text.Normalizer
import java.util.ArrayList

/**
 * Generates multiple search queries from cleaned TrackMetadata.
 * Increases hit rate by trying different query formulations.
 */
class LyricsNormalizer {

    /**
     * Generate a list of search query variations from the track metadata.
     * Each query is a String[] where [0]=artist, [1]=title.
     * Providers should try these in order until one matches.
     */
    fun generateQueries(track: TrackMetadata?): List<Array<String>> {
        val queries = ArrayList<Array<String>>()
        if (track == null) return queries

        val artist = track.cleanedArtist
        val title = track.cleanedTitle

        // 1. Cleaned artist + title (primary query)
        if (!isEmpty(artist) && !isEmpty(title)) {
            queries.add(arrayOf(artist, title))
        }

        // 2. Title only (for when artist is wrong or missing)
        if (!isEmpty(title)) {
            queries.add(arrayOf("", title))
        }

        // 3. Original title if different from cleaned
        if (!isEmpty(track.rawTitle) && track.rawTitle != title) {
            val rawCleaned = stripDiacritics(track.rawTitle)
            if (!isEmpty(rawCleaned) && rawCleaned != title) {
                queries.add(arrayOf(artist, rawCleaned))
            }
        }

        // 4. If version is not original, try the original title only
        if (track.version != "original" && !isEmpty(title)) {
            // The cleaned title should already have version tags removed,
            // but let's also try just the core words
            val coreTitle = title.replace("(?i)\\s*(?:remix|rmx|slowed|reverb|live|cover|acoustic|extended|edit|radio\\s*edit)\\s*".toRegex(), "").trim()
            if (coreTitle != title && !isEmpty(coreTitle)) {
                queries.add(arrayOf(artist, coreTitle))
            }
        }

        // 5. Phonetic/diacritic-stripped version
        val stripped = stripDiacritics(title)
        if (stripped != title && !isEmpty(stripped)) {
            queries.add(arrayOf(stripDiacritics(artist), stripped))
        }

        // 6. YouTube-style combined query (for providers that take a single query)
        if (!isEmpty(artist) && !isEmpty(title)) {
            queries.add(arrayOf("", "$artist $title"))
        }

        // 7. Try with raw artist if different from cleaned
        if (!isEmpty(track.rawArtist) && track.rawArtist != artist && !isEmpty(title)) {
            queries.add(arrayOf(track.rawArtist, title))
        }

        return queries
    }

    private fun isEmpty(s: String?): Boolean {
        return s.isNullOrBlank()
    }

    companion object {

        /**
         * Normalize a string for fuzzy comparison.
         * Lowercase, strip diacritics, remove punctuation, collapse whitespace.
         */
        @JvmStatic
        fun normalizeForComparison(input: String?): String {
            if (input == null) return ""
            var normalized = input.lowercase().trim()
            // Strip diacritics
            normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
            normalized = normalized.replace("\\p{InCombiningDiacriticalMarks}".toRegex(), "")
            // Remove non-alphanumeric (keep spaces)
            normalized = normalized.replace("[^a-z0-9\\s]".toRegex(), "")
            // Collapse whitespace
            normalized = normalized.replace("\\s+".toRegex(), " ").trim()
            return normalized
        }

        /**
         * Compute simple Levenshtein-based similarity ratio between two strings.
         * Returns 0.0 (no match) to 1.0 (identical) after normalization.
         */
        @JvmStatic
        fun similarity(a: String, b: String): Float {
            val na = normalizeForComparison(a)
            val nb = normalizeForComparison(b)

            if (na.isEmpty() && nb.isEmpty()) return 1.0f
            if (na.isEmpty() || nb.isEmpty()) return 0.0f
            if (na == nb) return 1.0f

            // Check containment
            if (na.contains(nb) || nb.contains(na)) {
                val ratio = na.length.coerceAtMost(nb.length).toFloat() / na.length.coerceAtLeast(nb.length)
                return 0.7f.coerceAtLeast(ratio)
            }

            val distance = levenshteinDistance(na, nb)
            val maxLen = na.length.coerceAtLeast(nb.length)
            return 1.0f - (distance.toFloat() / maxLen)
        }

        @JvmStatic
        private fun stripDiacritics(input: String?): String {
            if (input == null) return ""
            val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            return normalized.replace("\\p{InCombiningDiacriticalMarks}".toRegex(), "").trim()
        }

        @JvmStatic
        private fun levenshteinDistance(a: String, b: String): Int {
            val lenA = a.length
            val lenB = b.length

            // Optimize: only keep two rows
            var prev = IntArray(lenB + 1) { it }
            var curr = IntArray(lenB + 1)

            for (i in 1..lenA) {
                curr[0] = i
                for (j in 1..lenB) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] =
                        (curr[j - 1] + 1).coerceAtMost(prev[j] + 1).coerceAtMost(prev[j - 1] + cost)
                }
                val temp = prev
                prev = curr
                curr = temp
            }
            return prev[lenB]
        }
    }
}
