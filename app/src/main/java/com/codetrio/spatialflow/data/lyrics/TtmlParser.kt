package com.codetrio.spatialflow.data.lyrics

import android.util.Log
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * High-precision XML parser for TTML (Timed Text Markup Language) lyrics.
 * Features:
 * - Relative vs. Absolute word timestamp normalization
 * - Span-level background vocal detection (`role="x-bg"` / `ttm:role="x-bg"`)
 * - Time-bounded background vocal line merging
 */
object TtmlParser {

    private const val TAG = "TtmlParser"

    @JvmStatic
    fun parse(ttmlContent: String?): List<LyricLine> {
        if (ttmlContent.isNullOrBlank()) return emptyList()

        return try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = true
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(ttmlContent.toByteArray(Charsets.UTF_8)))
            doc.documentElement.normalize()

            val pNodes = doc.getElementsByTagName("p")
            val lines = mutableListOf<LyricLine>()

            for (i in 0 until pNodes.length) {
                val pElement = pNodes.item(i) as? Element ?: continue
                val beginAttr = getAttributeAny(pElement, "begin", "ttm:begin") ?: "00:00.00"
                val endAttr = getAttributeAny(pElement, "end", "ttm:end")
                val durAttr = getAttributeAny(pElement, "dur", "ttm:dur")

                val lineStartMs = parseRawTime(beginAttr)
                val lineEndMs = when {
                    endAttr != null -> parseRawTime(endAttr)
                    durAttr != null -> lineStartMs + parseRawTime(durAttr)
                    else -> lineStartMs + 5000L
                }

                val words = mutableListOf<LyricWord>()
                val lineSb = StringBuilder()

                val isLineBg = getAttributeAny(pElement, "role", "ttm:role")?.lowercase()?.let {
                    it == "x-bg" || it == "background"
                } ?: false

                val childNodes = pElement.childNodes
                var hasPendingSpace = false

                if (childNodes.length > 0) {
                    fun processSpanElement(span: Element, parentIsBg: Boolean) {
                        val roleAttr = getAttributeAny(span, "role", "ttm:role")?.lowercase()
                        val isSpanBg = parentIsBg || roleAttr == "x-bg" || roleAttr == "background"

                        val rawWordText = span.textContent ?: ""
                        val cleanWordText = rawWordText.trim()
                        if (cleanWordText.isEmpty()) return

                        val wordBegin = getAttributeAny(span, "begin", "ttm:begin")
                        val wordEnd = getAttributeAny(span, "end", "ttm:end")
                        val wordDur = getAttributeAny(span, "dur", "ttm:dur")

                        val startsWithSpace = rawWordText.startsWith(" ") || rawWordText.startsWith("\n")
                        val endsWithSpace = rawWordText.endsWith(" ") || rawWordText.endsWith("\n")

                        if (lineSb.isNotEmpty() && !lineSb.endsWith(" ")) {
                            if (hasPendingSpace || startsWithSpace) {
                                lineSb.append(" ")
                            }
                        }

                        val startChar = lineSb.length
                        lineSb.append(cleanWordText)
                        val endChar = (lineSb.length - 1).coerceAtLeast(startChar)

                        val rawWordStart = if (wordBegin != null) parseRawTime(wordBegin) else lineStartMs
                        val rawWordEnd = when {
                            wordEnd != null -> parseRawTime(wordEnd)
                            wordDur != null -> rawWordStart + parseRawTime(wordDur)
                            else -> rawWordStart + 300L
                        }

                        val wordStartMs = normalizeChildTime(rawWordStart, lineStartMs, lineEndMs, lineStartMs)
                        val wordEndMs = normalizeChildTime(rawWordEnd, lineStartMs, lineEndMs, lineEndMs).coerceAtLeast(wordStartMs)
                        val duration = (wordEndMs - wordStartMs).coerceAtLeast(50L)

                        words.add(
                            LyricWord(
                                text = cleanWordText,
                                absoluteStartTimeMs = wordStartMs,
                                durationMs = duration,
                                charRange = startChar..endChar,
                                isBackground = isSpanBg
                            )
                        )

                        hasPendingSpace = endsWithSpace
                    }

                    fun traverseNode(node: Node, isBg: Boolean) {
                        if (node.nodeType == Node.TEXT_NODE) {
                            val valText = node.nodeValue ?: ""
                            if (valText.contains(" ") || valText.contains("\n") || valText.contains("\t")) {
                                hasPendingSpace = true
                            }
                        } else if (node.nodeType == Node.ELEMENT_NODE) {
                            val elem = node as Element
                            val localName = elem.localName ?: elem.tagName
                            if (localName.endsWith("span", ignoreCase = true)) {
                                processSpanElement(elem, isBg)
                            } else {
                                val children = elem.childNodes
                                for (c in 0 until children.length) {
                                    traverseNode(children.item(c), isBg)
                                }
                            }
                        }
                    }

                    for (j in 0 until childNodes.length) {
                        traverseNode(childNodes.item(j), isLineBg)
                    }
                } else {
                    lineSb.append(pElement.textContent ?: "")
                }

                val contentText = lineSb.toString().trim()
                if (contentText.isNotEmpty()) {
                    val prevLine = lines.lastOrNull()
                    val isRecent = prevLine != null && (lineStartMs - prevLine.startTimeMs) < 4000L

                    if (isLineBg && prevLine != null && isRecent && prevLine.backgroundContent == null) {
                        // Merge background vocals into overlapping preceding line
                        lines[lines.lastIndex] = prevLine.copy(
                            backgroundContent = contentText,
                            backgroundWords = words
                        )
                    } else {
                        lines.add(
                            LyricLine(
                                startTimeMs = lineStartMs,
                                content = contentText,
                                isInterlude = false,
                                isWordByWord = words.isNotEmpty(),
                                words = words,
                                isBackground = isLineBg
                            )
                        )
                    }
                }
            }

            lines.sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing TTML lyrics: ${e.message}")
            emptyList()
        }
    }

    private fun getAttributeAny(element: Element, vararg names: String): String? {
        for (name in names) {
            if (element.hasAttribute(name)) return element.getAttribute(name)
        }
        val attrs = element.attributes ?: return null
        for (i in 0 until attrs.length) {
            val node = attrs.item(i) ?: continue
            val nodeName = node.nodeName
            for (name in names) {
                if (nodeName.endsWith(name, ignoreCase = true)) return node.nodeValue
            }
        }
        return null
    }

    /**
     * Normalizes child word timestamps.
     * Handles Apple Music relative offset spans vs absolute timestamps.
     */
    private fun normalizeChildTime(
        rawMs: Long,
        lineStartMs: Long,
        lineEndMs: Long,
        fallbackMs: Long
    ): Long {
        if (rawMs < 0) return fallbackMs
        val lineDurationMs = (lineEndMs - lineStartMs).coerceAtLeast(0L)
        val isProbablyRelative = rawMs < (lineStartMs - 250L) && rawMs <= (lineDurationMs + 1000L)
        val adjustedMs = if (isProbablyRelative) lineStartMs + rawMs else rawMs
        return adjustedMs.coerceIn(lineStartMs.coerceAtLeast(0L), lineEndMs.coerceAtLeast(lineStartMs))
    }

    private fun parseRawTime(timeStr: String): Long {
        val trimmed = timeStr.trim()
        if (trimmed.endsWith("ms")) {
            return trimmed.dropLast(2).toLongOrNull() ?: 0L
        }
        if (trimmed.endsWith("s")) {
            return (trimmed.dropLast(1).toFloatOrNull()?.times(1000))?.toLong() ?: 0L
        }
        if (trimmed.endsWith("t")) {
            val ticks = trimmed.dropLast(1).toDoubleOrNull() ?: 0.0
            return (ticks / 10.0).toLong()
        }

        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toDouble()
                    ((h * 3600 + m * 60 + s) * 1000).toLong()
                }
                2 -> {
                    val m = parts[0].toLong()
                    val s = parts[1].toDouble()
                    ((m * 60 + s) * 1000).toLong()
                }
                1 -> {
                    (parts[0].toDouble() * 1000).toLong()
                }
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
