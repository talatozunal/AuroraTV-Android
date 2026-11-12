package com.aurora.iptv.playback

import okhttp3.OkHttpClient
import com.aurora.iptv.data.db.StreamEntity
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** Keeps per-URL HTTP headers (User-Agent/Referer/Cookie) for ExoPlayer requests. */
class StreamHeaderRegistry {

    private val json = Json { ignoreUnknownKeys = true }
    private val headers = ConcurrentHashMap<String, Map<String, String>>()

    fun register(url: String, values: Map<String, String>) {
        if (url.isBlank() || values.isEmpty()) return
        headers[url] = values
    }

    fun headersFor(url: String): Map<String, String> = headers[url].orEmpty()

    fun registerStream(stream: StreamEntity, url: String) {
        val parsed = stream.httpHeaders?.let { raw ->
            runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        }.orEmpty()
        register(url, parsed)
    }

    fun clear() = headers.clear()
}
