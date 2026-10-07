package com.example.data

import com.example.model.Channel
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.nio.charset.Charset

object M3uParser {

    private val PROTOCOL_PREFIXES = listOf(
        "http://", "https://", "rtmp://", "rtsp://",
        "rtp://", "udp://", "p2p://", "mitv://", "file://"
    )

    /**
     * Parses M3U, M3U8, and DIYP/TVBox format streams line by line.
     * Guaranteed not to crash on malformed streams or strange encodings.
     */
    fun parse(reader: Reader, maxChannels: Int = 10000): List<Channel> {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader, 16384)
        val channels = mutableListOf<Channel>()
        var line: String?

        var currentTvgId = ""
        var currentTvgName = ""
        var currentLogo = ""
        var currentGroup = "其他频道"
        var currentChannelName = ""
        var currentChannelNo: Int? = null

        var counter = 1
        var isDiyPGenreMode = false

        try {
            while (bufferedReader.readLine().also { line = it } != null) {
                if (channels.size >= maxChannels) break
                var rawLine = line?.trim() ?: continue
                if (rawLine.isEmpty()) continue

                // Strip Unicode BOM character if present
                if (rawLine.startsWith("\uFEFF")) {
                    rawLine = rawLine.substring(1).trim()
                    if (rawLine.isEmpty()) continue
                }

                // 1. Check for DIYP / TVBox genre tag line: e.g. "央视频道,#genre#"
                if (rawLine.contains("#genre#", ignoreCase = true)) {
                    isDiyPGenreMode = true
                    val commaPos = rawLine.indexOf(',')
                    currentGroup = if (commaPos != -1) {
                        rawLine.substring(0, commaPos).trim()
                    } else {
                        rawLine.replace("#genre#", "", ignoreCase = true).trim()
                    }
                    if (currentGroup.isBlank()) currentGroup = "其他频道"
                    continue
                }

                // 2. M3U #EXTINF header line
                if (rawLine.startsWith("#EXTINF:", ignoreCase = true)) {
                    isDiyPGenreMode = false
                    currentTvgId = extractAttribute(rawLine, "tvg-id")
                    currentTvgName = extractAttribute(rawLine, "tvg-name")
                    currentLogo = extractAttribute(rawLine, "tvg-logo")
                    val grp = extractAttribute(rawLine, "group-title")
                    if (grp.isNotBlank()) {
                        currentGroup = grp
                    }

                    val chnoStr = extractAttribute(rawLine, "tvg-chno")
                    currentChannelNo = chnoStr.toIntOrNull()

                    // Extract title: find the comma that separates the tag/attributes from channel title
                    currentChannelName = extractChannelTitle(rawLine, currentTvgName, counter)
                } else if (!rawLine.startsWith("#")) {
                    // 3. Could be a URL line (M3U) OR "ChannelName,URL" line (DIYP format)
                    var streamUrl = rawLine
                    var nameCandidate = currentChannelName

                    if (rawLine.contains(",")) {
                        val commaPos = rawLine.indexOf(',')
                        val possibleName = rawLine.substring(0, commaPos).trim()
                        val possibleUrlPart = rawLine.substring(commaPos + 1).trim()

                        if (hasStreamProtocol(possibleUrlPart)) {
                            streamUrl = possibleUrlPart
                            nameCandidate = possibleName
                        }
                    }

                    // Handle multi-URL separation by '#' (e.g. url1#url2#url3)
                    if (streamUrl.contains("#") && hasStreamProtocol(streamUrl)) {
                        val urls = streamUrl.split("#").map { it.trim() }.filter { hasStreamProtocol(it) }
                        if (urls.isNotEmpty()) {
                            streamUrl = urls[0] // Pick primary URL
                        }
                    }

                    if (hasStreamProtocol(streamUrl)) {
                        val finalName = when {
                            nameCandidate.isNotBlank() -> nameCandidate
                            currentTvgName.isNotBlank() -> currentTvgName
                            currentTvgId.isNotBlank() -> currentTvgId
                            else -> "频道 $counter"
                        }
                        val finalGroup = if (currentGroup.isNotBlank()) currentGroup else "其他频道"
                        val channelNumber = currentChannelNo ?: counter

                        channels.add(
                            Channel(
                                id = "ch_${System.currentTimeMillis()}_$counter",
                                number = channelNumber,
                                name = finalName,
                                url = streamUrl,
                                group = finalGroup,
                                logo = currentLogo,
                                tvgId = if (currentTvgId.isNotBlank()) currentTvgId else finalName,
                                isFavorite = false
                            )
                        )
                        counter++

                        // Reset temporary attributes for next M3U block
                        if (!isDiyPGenreMode) {
                            currentTvgId = ""
                            currentTvgName = ""
                            currentLogo = ""
                            currentChannelName = ""
                            currentChannelNo = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                bufferedReader.close()
            } catch (_: Exception) {}
        }

        return channels
    }

    fun parse(content: String): List<Channel> {
        return parse(StringReader(content))
    }

    /**
     * Parses byte stream with smart encoding detection (UTF-8, GBK/GB2312, UTF-16)
     */
    fun parse(inputStream: InputStream): List<Channel> {
        val bytes = inputStream.readBytes()
        val charset = detectCharset(bytes)
        return parse(InputStreamReader(ByteArrayInputStream(bytes), charset))
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE
        }

        // Test if bytes form valid UTF-8
        return if (isValidUtf8(bytes)) {
            Charsets.UTF_8
        } else {
            // Chinese IPTV playlists very commonly use GBK/GB18030 from Windows or legacy TV boxes
            try {
                Charset.forName("GB18030")
            } catch (_: Exception) {
                try {
                    Charset.forName("GBK")
                } catch (_: Exception) {
                    Charsets.UTF_8
                }
            }
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var expectedContinuation = 0
        for (b in bytes) {
            val byteVal = b.toInt() and 0xFF
            if (expectedContinuation > 0) {
                if ((byteVal and 0xC0) != 0x80) return false
                expectedContinuation--
            } else {
                if ((byteVal and 0x80) == 0) continue
                if ((byteVal and 0xE0) == 0xC0) expectedContinuation = 1
                else if ((byteVal and 0xF0) == 0xE0) expectedContinuation = 2
                else if ((byteVal and 0xF8) == 0xF0) expectedContinuation = 3
                else return false
            }
        }
        return expectedContinuation == 0
    }

    private fun hasStreamProtocol(url: String): Boolean {
        val lower = url.trim().lowercase()
        return PROTOCOL_PREFIXES.any { lower.startsWith(it) } ||
                lower.contains(".m3u8") ||
                lower.contains(".ts") ||
                lower.contains(".mpd")
    }

    /**
     * Extracts channel title from #EXTINF line.
     * Correctly handles quotes and commas inside attribute tags.
     */
    private fun extractChannelTitle(line: String, fallbackTvgName: String, counter: Int): String {
        var inQuotes = false
        var commaIdx = -1

        for (i in 8 until line.length) {
            val c = line[i]
            if (c == '"' || c == '\'') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                commaIdx = i
                break
            }
        }

        if (commaIdx != -1 && commaIdx + 1 < line.length) {
            val title = line.substring(commaIdx + 1).trim()
            if (title.isNotBlank()) return title
        }

        val lastComma = line.lastIndexOf(',')
        if (lastComma != -1 && lastComma + 1 < line.length) {
            val title = line.substring(lastComma + 1).trim()
            if (title.isNotBlank()) return title
        }

        return if (fallbackTvgName.isNotBlank()) fallbackTvgName else "频道 $counter"
    }

    /**
     * Fast attribute extractor supporting single/double/no quotes
     */
    private fun extractAttribute(line: String, attrName: String): String {
        val target = "$attrName="
        val idx = line.indexOf(target, ignoreCase = true)
        if (idx == -1) return ""
        val start = idx + target.length
        if (start >= line.length) return ""

        val quote = line[start]
        return if (quote == '"' || quote == '\'') {
            val end = line.indexOf(quote, start + 1)
            if (end != -1) line.substring(start + 1, end).trim() else ""
        } else {
            val spaceEnd = line.indexOf(' ', start)
            val commaEnd = line.indexOf(',', start)
            val end = when {
                spaceEnd != -1 && commaEnd != -1 -> minOf(spaceEnd, commaEnd)
                spaceEnd != -1 -> spaceEnd
                commaEnd != -1 -> commaEnd
                else -> line.length
            }
            line.substring(start, end).trim()
        }
    }
}
