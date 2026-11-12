package com.aurora.iptv

import android.content.Context
import com.aurora.iptv.data.db.AuroraDatabase
import com.aurora.iptv.data.repo.ContentRepository
import com.aurora.iptv.data.repo.EpgRepository
import com.aurora.iptv.data.repo.PlaylistRepository
import com.aurora.iptv.data.settings.SettingsStore
import com.aurora.iptv.data.source.XtreamClient
import com.aurora.iptv.net.Http
import com.aurora.iptv.playback.PlaybackConnection
import com.aurora.iptv.playback.PlayerFactory
import com.aurora.iptv.playback.StreamHeaderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; the project is small enough that DI frameworks are overkill. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val http = Http()
    val db = AuroraDatabase.build(appContext)
    val settings = SettingsStore(appContext)
    val xtream = XtreamClient(http)
    val epgRepository = EpgRepository(db, http)
    val playlistRepository = PlaylistRepository(
        appContext,
        db,
        http,
        xtream,
        epgRepository,
        settings
    )
    val contentRepository = ContentRepository(db, epgRepository)
    val headerRegistry = StreamHeaderRegistry()
    val playerFactory = PlayerFactory(appContext, headerRegistry, http.client)
    val playbackConnection = PlaybackConnection(appContext)
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
