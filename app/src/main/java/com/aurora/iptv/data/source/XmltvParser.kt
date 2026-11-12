package com.aurora.iptv.data.source

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class XmltvChannel(val id: String, val names: List<String>)

data class XmltvProgramme(
    val channelId: String,
    val title: String,
    val description: String?,
    val start: Long,
    val end: Long
)

/**
 * Streaming XMLTV parser. Nothing is kept in memory except the current programme,
 * so multi-megabyte EPG files from IPTV panels can be imported on TV boxes.
 */
class XmltvParser {

    suspend fun parse(
        input: InputStream,
        onChannel: suspend (XmltvChannel) -> Unit,
        onProgramme: suspend (XmltvProgramme) -> Unit,
        onProgress: suspend (Int) -> Unit = {}
    ) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var event = parser.eventType
        var count = 0
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name?.lowercase(Locale.ROOT)) {
                    "channel" -> parseChannel(parser, onChannel)
                    "programme" -> {
                        val programme = parseProgramme(parser)
                        if (programme != null) {
                            count++
                            onProgramme(programme)
                            if (count % 250 == 0) onProgress(count)
                        }
                    }
                }
            }
            event = parser.next()
        }
        onProgress(count)
    }

    private suspend fun parseChannel(
        parser: XmlPullParser,
        onChannel: suspend (XmltvChannel) -> Unit
    ) {
        val id = parser.getAttributeValue(null, "id")
        val names = mutableListOf<String>()
        if (id.isNullOrBlank()) {
            skipElement(parser, "channel")
            return
        }
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name.equals("channel", true))) {
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.START_TAG &&
                parser.name.equals("display-name", true)
            ) {
                val text = readElementText(parser)
                if (text.isNotBlank()) names += text
            }
            event = parser.next()
        }
        onChannel(XmltvChannel(id, names))
    }

    private fun parseProgramme(parser: XmlPullParser): XmltvProgramme? {
        val channelId = parser.getAttributeValue(null, "channel")
        val startRaw = parser.getAttributeValue(null, "start")
        val stopRaw = parser.getAttributeValue(null, "stop")
        if (channelId.isNullOrBlank() || startRaw.isNullOrBlank()) {
            skipElement(parser, "programme")
            return null
        }
        val start = parseXmltvDate(startRaw) ?: run {
            skipElement(parser, "programme")
            return null
        }
        val end = parseXmltvDate(stopRaw)?.takeIf { it > start }
            ?: (start + 30 * 60 * 1000L)

        var title = ""
        var subTitle = ""
        var description = ""
        var event = parser.next()
        while (true) {
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.START_TAG) {
                when (parser.name?.lowercase(Locale.ROOT)) {
                    "title" -> title = readElementText(parser)
                    "sub-title" -> subTitle = readElementText(parser)
                    "desc" -> description = readElementText(parser)
                }
            }
            if (event == XmlPullParser.END_TAG && parser.name.equals("programme", true)) break
            event = parser.next()
        }
        val fullTitle = when {
            title.isBlank() && subTitle.isBlank() -> "Program"
            title.isBlank() -> subTitle
            subTitle.isBlank() -> title
            else -> title
        }
        return XmltvProgramme(
            channelId = channelId,
            title = fullTitle.take(300),
            description = description.trim().takeIf { it.isNotBlank() }?.take(1000)
                ?: subTitle.takeIf { it.isNotBlank() }?.take(300),
            start = start,
            end = end
        )
    }

    private fun readElementText(parser: XmlPullParser): String {
        val tag = parser.name
        val sb = StringBuilder()
        var depth = 1
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth == 0 && parser.name.equals(tag, true)) break
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text)
            }
            event = parser.next()
        }
        return sb.toString().trim()
    }

    private fun skipElement(parser: XmlPullParser, tag: String) {
        var depth = 1
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth == 0 && parser.name.equals(tag, true)) break
                }
            }
            event = parser.next()
        }
    }

    private fun parseXmltvDate(raw: String): Long? {
        val value = raw.trim()
        if (value.length < 12) return null
        val patterns = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmmssZ",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm Z",
            "yyyyMMddHHmm"
        )
        for (pattern in patterns) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = true
                timeZone = TimeZone.getDefault()
            }
            val parsed = runCatching { formatter.parse(value) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }
}
