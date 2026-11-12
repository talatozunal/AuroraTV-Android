package com.aurora.iptv.playback

import android.content.Intent
import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aurora.iptv.AuroraApp
import kotlinx.coroutines.runBlocking

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as AuroraApp).container
        val bufferSeconds = runCatching {
            runBlocking { container.settings.snapshot().bufferSeconds }
        }.getOrDefault(20)
        val player = container.playerFactory.build(bufferSeconds)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, com.aurora.iptv.MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val container = (application as AuroraApp).container
        val backgroundPlay = runCatching {
            runBlocking { container.settings.snapshot().backgroundPlay }
        }.getOrDefault(true)
        if (player == null || !backgroundPlay || !player.playWhenReady) {
            player?.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
