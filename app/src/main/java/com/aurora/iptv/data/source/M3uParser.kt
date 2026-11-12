package com.aurora.iptv.data.source

import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.search.SmartSearch
import java.net.URLDecoder

data class ParsedStream(
    val name: String,
    val url: String,
    val category: String,
    val logoUrl: String?,
    val epgId: String?,
    val channelNo: Int?,
    val kind: StreamKind,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int,
    val headers: Map<String, String>
)

private data class Pending(
    val name: String,
    val attrs: Map<String, String>,
    val duration: String
)

/**
 * Streaming parser for M3U / M3U8 playlists. Supports EXTINF attributes,
 * EXTVLCOPT HTTP headers, pipe-separated headers (`url|User-Agent=...`),
 * catch-up (catchup / catchup-source / catchup-days) and VOD heuristics.
 */
class M3uParser(
    private val onMeta: suspend (name: String?, epgUrl: String?) -> Unit = { _, _ -> },
    private val onStream: suspend (ParsedStream) -> Unit
) {
    private var pending: Pending? = null
    private var pendingHeaders = linkedMapOf<String, String>()
    private var metaSent = false

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val seriesRegex = Regex("""\bs\d{1,2}\s*e\d{1,3}\b""", RegexOption.IGNORE_CASE)

    suspend fun feed(rawLine: String) {
        val line = rawLine.trim().removePrefix("\uFEFF").trimEnd('\r')
        if (line.isBlank()) return

        when {
            line.startsWith("#EXTM3U", ignoreCase = true) -> {
                val attrs = parseAttributes(line)
                val epg = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: attrs["tvg-url"]
                if (!metaSent) {
                    metaSent = true
                    onMeta(null, epg)
                }
            }

            line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                if (!metaSent) {
                    metaSent = true
                    onMeta(line.substringAfter(':').trim().ifBlank { null }, null)
                }
            }

            line.startsWith("#EXTINF", ignoreCase = true) -> {
                pending = parseExtInf(line)
                pendingHeaders = linkedMapOf()
            }

            line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                val group = line.substringAfter(':').trim()
                pending?.attrs?.let { attrs ->
                    if (attrs["group-title"].isNullOrBlank() && group.isNotBlank()) {
                        pending = pending?.copy(
                            attrs = attrs + ("group-title" to group)
                        )
                    }
                }
            }

            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                val option = line.substringAfter(':')
                val key = option.substringBefore('=', "").trim()
                val value = option.substringAfter('=', "").trim()
                if (value.isNotBlank()) {
                    when (key.lowercase()) {
                        "http-user-agent" -> pendingHeaders["User-Agent"] = value
                        "http-referrer", "http-referer" -> pendingHeaders["Referer"] = value
                        "http-origin" -> pendingHeaders["Origin"] = value
                        "http-cookie" -> pendingHeaders["Cookie"] = value
                    }
                }
            }

            line.startsWith("#") -> Unit // #KODIPROP and other directives are ignored.

            else -> {
                val info = pending ?: return
                pending = null
                val headers = linkedMapOf<String, String>()
                headers.putAll(pendingHeaders)
                pendingHeaders = linkedMapOf()

                var url = line
                val pipe = line.lastIndexOf('|')
                if (pipe > 0) {
                    url = line.substring(0, pipe)
                    parsePipeHeaders(line.substring(pipe + 1), headers)
                }

                val name = info.name.ifBlank {
                    info.attrs["tvg-name"]?.takeIf { it.isNotBlank() } ?: "Yayın"
                }
                val group = info.attrs["group-title"]?.trim().orEmpty()
                val category = group.ifBlank { "Genel" }
                val kind = classify(name, group, url)
                val epgId = info.attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    ?: info.attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                val catchupType = info.attrs["catchup"]?.takeIf { it.isNotBlank() }
                val catchupSource = info.attrs["catchup-source"]?.takeIf { it.isNotBlank() }
                val catchupDays = info.attrs["catchup-days"]?.toIntOrNull()
                    ?: info.attrs["catchup-duration"]?.toIntOrNull()?.div(24) ?: 0
                val channelNo = info.attrs["tvg-chno"]?.toIntOrNull()
                    ?: info.attrs["channel-number"]?.toIntOrNull()

                onStream(
                    ParsedStream(
                        name = name,
                        url = url.trim(),
                        category = category,
                        logoUrl = info.attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        epgId = epgId,
                        channelNo = channelNo,
                        kind = kind,
                        catchupType = catchupType,
                        catchupSource = catchupSource,
                        catchupDays = catchupDays.coerceIn(0, 30),
                        headers = headers
                    )
                )
            }
        }
    }

    private fun parseExtInf(line: String): Pending {
        val content = line.substringAfter(':', "").ifBlank { line }
        val comma = content.lastIndexOf(',')
        val attributesPart = if (comma >= 0) content.substring(0, comma) else content
        val displayName = if (comma >= 0) content.substring(comma + 1).trim() else ""
        val duration = attributesPart.trim().substringBefore(' ')
        return Pending(
            name = displayName,
            attrs = parseAttributes(attributesPart),
            duration = duration
        )
    }

    private fun parseAttributes(value: String): Map<String, String> =
        attrRegex.findAll(value).associate { match ->
            match.groupValues[1].lowercase() to match.groupValues[2]
        }

    private fun parsePipeHeaders(raw: String, target: MutableMap<String, String>) {
        raw.split('&').forEach { pair ->
            val key = pair.substringBefore('=', "").trim()
            if (key.isBlank()) return@forEach
            val value = pair.substringAfter('=', "").trim()
            if (value.isBlank()) return@forEach
            val header = when (key.lowercase()) {
                "user-agent", "useragent" -> "User-Agent"
                "referer", "referrer" -> "Referer"
                "origin" -> "Origin"
                "cookie" -> "Cookie"
                "authorization" -> "Authorization"
                else -> key
            }
            target[header] = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }
    }

    private fun classify(name: String, group: String, url: String): StreamKind {
        val normalizedGroup = SmartSearch.normalize(group)
        val normalizedName = SmartSearch.normalize(name)
        val haystack = "$normalizedGroup $normalizedName"

        if (seriesRegex.containsMatchIn(name) ||
            normalizedGroup.contains("dizi") ||
            normalizedGroup.contains("series") ||
            normalizedGroup.contains("serie")
        ) {
            return StreamKind.SERIES
        }
        if (normalizedGroup.contains("film") ||
            normalizedGroup.contains("movie") ||
            normalizedGroup.contains("sinema") ||
            normalizedGroup.contains("cinema") ||
            normalizedGroup.contains("vod")
        ) {
            return StreamKind.MOVIE
        }

        val path = url.substringBefore('?').lowercase()
        val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".m4v", ".mov", ".webm", ".wmv")
        if (videoExtensions.any { path.endsWith(it) } &&
            (normalizedName.contains("film") || normalizedName.contains("movie") || normalizedGroup.isNotBlank())
        ) {
            return StreamKind.MOVIE
        }

        return if (haystack.contains("dizi") || haystack.contains("series")) {
            StreamKind.SERIES
        } else {
            StreamKind.LIVE
        }
    }
}
