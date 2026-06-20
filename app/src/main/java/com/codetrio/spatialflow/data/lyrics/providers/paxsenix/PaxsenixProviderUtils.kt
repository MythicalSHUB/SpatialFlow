package com.codetrio.spatialflow.data.lyrics.providers.paxsenix

import com.google.gson.JsonElement

object PaxsenixProviderUtils {
    fun extractLyrics(jsonElement: JsonElement): String? {
        if (!jsonElement.isJsonObject) {
            if (jsonElement.isJsonArray) {
                // sometimes it might return array of lines directly
                return extractFromLinesArray(jsonElement.asJsonArray)
            }
            return null
        }
        val obj = jsonElement.asJsonObject
        
        // check for error
        if (obj.has("isError") && obj.get("isError").isJsonPrimitive && obj.get("isError").asJsonPrimitive.isBoolean && obj.get("isError").asBoolean) {
            return null
        }
        if (obj.has("error") && obj.get("error").isJsonPrimitive && obj.get("error").asJsonPrimitive.isBoolean && obj.get("error").asBoolean) {
            return null
        }

        if (obj.has("lyrics") && obj.get("lyrics").isJsonPrimitive) {
            return obj.get("lyrics").asString
        }
        if (obj.has("lrc") && obj.get("lrc").isJsonPrimitive) {
            return obj.get("lrc").asString
        }
        if (obj.has("lines") && obj.get("lines").isJsonArray) {
            return extractFromLinesArray(obj.getAsJsonArray("lines"))
        }
        return null
    }

    private fun extractFromLinesArray(lines: com.google.gson.JsonArray): String {
        val sb = StringBuilder()
        for (i in 0 until lines.size()) {
            val lineObj = lines.get(i).asJsonObject
            val timeTag = if (lineObj.has("timeTag")) lineObj.get("timeTag").asString else if (lineObj.has("startTimeMs")) {
                // spotify format
                val msStr = lineObj.get("startTimeMs").asString
                val ms = msStr.toLongOrNull() ?: 0L
                formatTimeMs(ms)
            } else ""
            val words = if (lineObj.has("words")) lineObj.get("words").asString else ""
            if (timeTag.isNotEmpty()) {
                sb.append("[$timeTag] $words\n")
            } else {
                sb.append("$words\n")
            }
        }
        return sb.toString().trim()
    }

    private fun formatTimeMs(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        val hundredths = (ms % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    fun isWordByWord(lyrics: String): Boolean {
        // Simple heuristic for enhanced LRC
        return lyrics.contains("<\\d{2}:\\d{2}\\.\\d{2,3}>".toRegex())
    }
}
