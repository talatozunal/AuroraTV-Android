package com.aurora.iptv.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aurora.iptv.data.db.StreamEntity
import okhttp3.OkHttpClient

class PlayerFactory(
    private val context: Context,
    private val headers: StreamHeaderRegistry,
    private val httpClient: OkHttpClient
) {

    fun build(bufferSeconds: Int = 20, handleAudioFocus: Boolean = true): ExoPlayer {
        val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent("AuroraTV/1.0 (Android TV)")
        val baseFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val resolvingFactory = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val values = headers.headersFor(dataSpec.uri.toString())
            if (values.isEmpty()) dataSpec else dataSpec.withRequestHeaders(values)
        }

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setLoadControl(loadControl(bufferSeconds))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }

    fun mediaItem(stream: StreamEntity, url: String): MediaItem {
        headers.registerStream(stream, url)
        val title = stream.customName?.takeIf { it.isNotBlank() } ?: stream.name
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(stream.category)
            .setMediaType(
                if (stream.kind == com.aurora.iptv.data.db.StreamKind.LIVE) {
                    MediaMetadata.MEDIA_TYPE_TV_SHOW
                } else {
                    MediaMetadata.MEDIA_TYPE_MOVIE
                }
            )
            .build()
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(stream.uid)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun loadControl(bufferSeconds: Int): DefaultLoadControl {
        val max = (bufferSeconds.coerceIn(5, 120) * 1000)
        val min = (max / 2).coerceAtLeast(5_000)
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                min,
                max,
                1_500,
                3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
}
