package com.aurora.iptv.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single MediaController connection used by the full-screen player and Mini preview. */
class PlaybackConnection(private val context: Context) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener(
            {
                if (future !== pending) {
                    runCatching { pending.get().release() }
                    return@addListener
                }
                runCatching { pending.get() }
                    .onSuccess { controller -> _controller.value = controller }
                    .onFailure {
                        _controller.value = null
                        future = null
                    }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        val pending = future ?: return
        future = null
        _controller.value?.let { controller -> runCatching { controller.release() } }
        _controller.value = null
        if (pending.isDone) {
            runCatching { pending.get().release() }
        } else {
            pending.addListener(
                { runCatching { pending.get().release() } },
                ContextCompat.getMainExecutor(context)
            )
        }
    }
}
