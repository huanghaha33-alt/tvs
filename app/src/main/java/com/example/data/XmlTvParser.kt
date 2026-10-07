package com.example.data

import android.util.Xml
import com.example.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

object XmlTvParser {

    private val DATE_FORMAT_TZ = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val DATE_FORMAT_NO_TZ = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    fun parse(xmlContent: String): Map<String, List<EpgProgram>> {
        val result = mutableMapOf<String, MutableList<EpgProgram>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var currentChannelId: String? = null
            var currentStart: Long = 0
            var currentEnd: Long = 0
            var currentTitle: String = ""
            var currentDesc: String = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase(Locale.ROOT)) {
                            "programme" -> {
                                currentChannelId = parser.getAttributeValue(null, "channel")
                                val startStr = parser.getAttributeValue(null, "start")
                                val stopStr = parser.getAttributeValue(null, "stop")
                                currentStart = parseTime(startStr)
                                currentEnd = parseTime(stopStr)
                                currentTitle = ""
                                currentDesc = ""
                            }
                            "title" -> {
                                currentTitle = parser.nextText().trim()
                            }
                            "desc" -> {
                                currentDesc = parser.nextText().trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("programme", ignoreCase = true) && !currentChannelId.isNullOrBlank()) {
                            if (currentTitle.isNotBlank() && currentEnd > currentStart) {
                                val list = result.getOrPut(currentChannelId) { mutableListOf() }
                                list.add(
                                    EpgProgram(
                                        channelTvgId = currentChannelId,
                                        title = currentTitle,
                                        startTime = currentStart,
                                        endTime = currentEnd,
                                        description = currentDesc
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseTime(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        return try {
            if (dateStr.contains(" ")) {
                DATE_FORMAT_TZ.parse(dateStr)?.time ?: 0
            } else {
                DATE_FORMAT_NO_TZ.parse(dateStr)?.time ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
