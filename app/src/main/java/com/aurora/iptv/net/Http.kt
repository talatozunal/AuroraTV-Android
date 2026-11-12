package com.aurora.iptv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, url: String) : IOException("HTTP $code for $url")

class Http {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val userAgent = "AuroraTV/1.0 (Android TV)"

    private fun request(url: String, headers: Map<String, String>): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()

    suspend fun text(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        client.newCall(request(url, headers)).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url)
            response.body?.string().orEmpty()
        }
    }

    suspend fun json(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): JsonElement = Json.parseToJsonElement(text(url, headers))

    /**
     * Opens a stream and gives the caller access to the buffered source so parsers can
     * consume huge M3U files without loading them into memory. The HTTP response stays
     * open while the (suspend) parser inserts batches into the database.
     */
    suspend fun withSource(
        url: String,
        headers: Map<String, String> = emptyMap(),
        block: suspend (BufferedSource) -> Unit
    ) = withContext(Dispatchers.IO) {
        client.newCall(request(url, headers)).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url)
            val body = response.body ?: throw IOException("Empty body")
            body.source().use { source -> block(source) }
        }
    }

    suspend fun withStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        block: suspend (InputStream) -> Unit
    ) = withContext(Dispatchers.IO) {
        client.newCall(request(url, headers)).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, url)
            val body = response.body ?: throw IOException("Empty body")
            body.byteStream().use { stream -> block(stream) }
        }
    }
}
