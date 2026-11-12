package com.aurora.iptv.playback

/**
 * Small hand-off object used between the browse screens and the player route.
 * The player copies the values into its own state as soon as it opens.
 */
object PlaybackRequest {
    @Volatile var uid: String? = null
    @Volatile var startPositionMs: Long = 0L
    @Volatile var catchupStartMs: Long? = null
    @Volatile var catchupEndMs: Long? = null
    @Volatile var catchupTitle: String? = null
    @Volatile var nonce: Long = 0L

    fun live(uid: String) {
        this.uid = uid
        startPositionMs = 0L
        catchupStartMs = null
        catchupEndMs = null
        catchupTitle = null
        nonce = System.nanoTime()
    }

    fun vod(uid: String, positionMs: Long = 0L) {
        this.uid = uid
        startPositionMs = positionMs
        catchupStartMs = null
        catchupEndMs = null
        catchupTitle = null
        nonce = System.nanoTime()
    }

    fun catchup(
        uid: String,
        startMs: Long,
        endMs: Long,
        title: String? = null
    ) {
        this.uid = uid
        startPositionMs = 0L
        catchupStartMs = startMs
        catchupEndMs = endMs
        catchupTitle = title
        nonce = System.nanoTime()
    }
}
