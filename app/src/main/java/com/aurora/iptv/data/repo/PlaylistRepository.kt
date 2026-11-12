package com.aurora.iptv.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.aurora.iptv.data.db.AuroraDatabase
import com.aurora.iptv.data.db.PlaylistEntity
import com.aurora.iptv.data.db.PlaylistType
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.data.settings.SettingsStore
import com.aurora.iptv.data.source.M3uParser
import com.aurora.iptv.data.source.ParsedStream
import com.aurora.iptv.data.source.XtreamClient
import com.aurora.iptv.net.Http
import com.aurora.iptv.search.SmartSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest

sealed interface PlaylistDraft {
    val name: String
    val epgUrl: String?

    data class M3uUrl(
        override val name: String,
        val url: String,
        override val epgUrl: String? = null
    ) : PlaylistDraft

    data class M3uFile(
        override val name: String,
        val uri: String,
        override val epgUrl: String? = null
    ) : PlaylistDraft

    data class Xtream(
        override val name: String,
        val server: String,
        val username: String,
        val password: String,
        override val epgUrl: String? = null
    ) : PlaylistDraft
}

enum class SyncPhase { CONTENT, EPG }

data class SyncProgress(
    val phase: SyncPhase,
    val count: Int = 0
)

class PlaylistRepository(
    private val context: Context,
    private val db: AuroraDatabase,
    private val http: Http,
    private val xtream: XtreamClient,
    private val epgRepository: EpgRepository,
    private val settings: SettingsStore
) {
    suspend fun addAndSync(
        draft: PlaylistDraft,
        onProgress: (SyncProgress) -> Unit = {}
    ): Long {
        val entity = when (draft) {
            is PlaylistDraft.M3uUrl -> PlaylistEntity(
                name = draft.name,
                type = PlaylistType.M3U,
                url = draft.url.trim(),
                epgUrl = draft.epgUrl?.trim()?.takeIf { it.isNotBlank() }
            )

            is PlaylistDraft.M3uFile -> PlaylistEntity(
                name = draft.name,
                type = PlaylistType.M3U,
                url = draft.uri,
                epgUrl = draft.epgUrl?.trim()?.takeIf { it.isNotBlank() }
            )

            is PlaylistDraft.Xtream -> PlaylistEntity(
                name = draft.name,
                type = PlaylistType.XTREAM,
                url = draft.server.trim(),
                username = draft.username.trim(),
                password = draft.password,
                epgUrl = draft.epgUrl?.trim()?.takeIf { it.isNotBlank() }
            )
        }

        val id = db.playlistDao().insert(entity)
        try {
            syncContent(id, onProgress)
            runCatching { refreshEpg(id, onProgress) }
            if (settings.snapshot().activePlaylistId == null) {
                settings.setActivePlaylist(id)
            }
        } catch (error: Throwable) {
            db.withTransaction {
                db.streamDao().deleteByPlaylist(id)
                db.epgDao().deleteByPlaylist(id)
                db.playlistDao().delete(id)
            }
            throw error
        }
        return id
    }

    suspend fun syncContent(
        playlistId: Long,
        onProgress: (SyncProgress) -> Unit = {}
    ): Int {
        val playlist = db.playlistDao().get(playlistId) ?: return 0
        return when (playlist.type) {
            PlaylistType.M3U -> syncM3u(playlist, onProgress)
            PlaylistType.XTREAM -> syncXtream(playlist, onProgress)
        }
    }

    suspend fun refreshEpg(
        playlistId: Long,
        onProgress: (SyncProgress) -> Unit = {}
    ): Int {
        val playlist = db.playlistDao().get(playlistId) ?: return 0
        val count = epgRepository.refresh(playlist) { value ->
            onProgress(SyncProgress(SyncPhase.EPG, value))
        }
        updateStats(playlistId)
        return count
    }

    suspend fun syncAll(
        playlistId: Long,
        onProgress: (SyncProgress) -> Unit = {}
    ) {
        syncContent(playlistId, onProgress)
        refreshEpg(playlistId, onProgress)
    }

    suspend fun delete(playlistId: Long) {
        db.withTransaction {
            db.streamDao().deleteByPlaylist(playlistId)
            db.epgDao().deleteByPlaylist(playlistId)
            db.playlistDao().delete(playlistId)
        }
    }

    suspend fun setActive(playlistId: Long) {
        settings.setActivePlaylist(playlistId)
    }

    suspend fun clearEpgCache() {
        epgRepository.clearAll()
    }

    fun supportsCatchup(stream: StreamEntity): Boolean =
        stream.catchupType != null ||
            stream.catchupDays > 0 ||
            !stream.catchupSource.isNullOrBlank()

    suspend fun catchupUrl(
        streamUid: String,
        startMs: Long,
        endMs: Long
    ): String? {
        val stream = db.streamDao().getByUid(streamUid) ?: return null
        val playlist = db.playlistDao().get(stream.playlistId) ?: return null
        val startSeconds = startMs / 1000L
        val endSeconds = endMs / 1000L
        val durationMinutes =
            ((endMs - startMs).coerceAtLeast(60_000L) / 60_000L).toInt()

        if (playlist.type == PlaylistType.XTREAM) {
            val providerId = stream.providerId ?: return null
            if (!supportsCatchup(stream)) return null
            return xtream.timeshiftUrl(
                playlist.url,
                playlist.username,
                playlist.password,
                providerId,
                startSeconds,
                durationMinutes
            )
        }

        val source = stream.catchupSource
        if (!source.isNullOrBlank()) {
            val now = System.currentTimeMillis() / 1000L
            val replaced = source
                .replace("{utc}", startSeconds.toString())
                .replace("{utcend}", endSeconds.toString())
                .replace("{lutc}", now.toString())
                .replace("{start}", startSeconds.toString())
                .replace("{end}", endSeconds.toString())
                .replace("{duration}", (endSeconds - startSeconds).toString())
                .replace("{timestamp}", now.toString())
            if (!replaced.contains("{")) return replaced
        }

        if (stream.catchupType?.lowercase() == "append") {
            val separator = if (stream.streamUrl.contains("?")) "&" else "?"
            return "${stream.streamUrl}${separator}start=$startSeconds&end=$endSeconds"
        }
        return null
    }

    suspend fun loadSeriesEpisodes(seriesUid: String): List<StreamEntity> {
        val series = db.streamDao().getByUid(seriesUid) ?: return emptyList()
        val playlist = db.playlistDao().get(series.playlistId) ?: return emptyList()
        if (playlist.type != PlaylistType.XTREAM) return emptyList()
        val seriesId = series.seriesId ?: return emptyList()

        val (details, episodes) = xtream.seriesDetails(
            playlist.url,
            playlist.username,
            playlist.password,
            seriesId
        )
        val updated = series.copy(
            plot = details.plot ?: series.plot,
            cast = details.cast ?: series.cast,
            director = details.director ?: series.director,
            genre = details.genre ?: series.genre,
            rating = details.rating ?: series.rating,
            year = details.year ?: series.year,
            logoUrl = details.cover ?: series.logoUrl,
            searchText = SmartSearch.buildSearchText(
                series.name,
                series.category,
                details.cast ?: series.cast,
                details.genre ?: series.genre,
                details.plot ?: series.plot
            )
        )
        db.streamDao().update(updated)

        val entities = episodes.map { episode ->
            val uid = "xt:${playlist.id}:EPISODE:${episode.id}"
            StreamEntity(
                uid = uid,
                playlistId = playlist.id,
                kind = StreamKind.EPISODE,
                name = episode.title.ifBlank { "Bölüm ${episode.episode}" },
                category = series.name,
                categoryId = series.categoryId,
                streamUrl = xtream.episodeUrl(
                    playlist.url,
                    playlist.username,
                    playlist.password,
                    episode.id,
                    episode.ext
                ),
                providerId = episode.id,
                logoUrl = episode.icon ?: series.logoUrl,
                seriesId = seriesId,
                season = episode.season,
                episode = episode.episode,
                containerExt = episode.ext,
                plot = episode.plot,
                cast = episode.cast,
                rating = episode.rating,
                year = episode.year,
                searchText = SmartSearch.buildSearchText(
                    series.name,
                    series.category,
                    episode.cast,
                    series.genre,
                    episode.plot
                )
            )
        }
        db.withTransaction {
            db.streamDao().deleteSeriesEpisodes(playlist.id, seriesId)
            db.streamDao().insertAll(entities)
        }
        return entities
    }

    suspend fun loadMovieDetails(uid: String): StreamEntity? {
        val stream = db.streamDao().getByUid(uid) ?: return null
        val playlist = db.playlistDao().get(stream.playlistId) ?: return stream
        if (playlist.type != PlaylistType.XTREAM) return stream
        val providerId = stream.providerId ?: return stream
        val details = runCatching {
            xtream.vodDetails(
                playlist.url,
                playlist.username,
                playlist.password,
                providerId
            )
        }.getOrNull() ?: return stream

        val updated = stream.copy(
            plot = details.plot ?: stream.plot,
            cast = details.cast ?: stream.cast,
            director = details.director ?: stream.director,
            genre = details.genre ?: stream.genre,
            rating = details.rating ?: stream.rating,
            year = details.year ?: stream.year,
            logoUrl = details.cover ?: stream.logoUrl,
            searchText = SmartSearch.buildSearchText(
                stream.name,
                stream.category,
                details.cast ?: stream.cast,
                details.genre ?: stream.genre,
                details.plot ?: stream.plot
            )
        )
        db.streamDao().update(updated)
        return updated
    }

    private suspend fun syncM3u(
        playlist: PlaylistEntity,
        onProgress: (SyncProgress) -> Unit
    ): Int {
        val tag = System.currentTimeMillis()
        val userState = db.streamDao().userState(playlist.id).associateBy { it.uid }
        val buffer = ArrayList<StreamEntity>(400)
        var parsedEpgUrl: String? = null
        var count = 0

        suspend fun flush() {
            if (buffer.isEmpty()) return
            val copy = buffer.toList()
            buffer.clear()
            db.withTransaction { db.streamDao().insertAll(copy) }
        }

        val parser = M3uParser(
            onMeta = { _, epgUrl ->
                if (!epgUrl.isNullOrBlank()) parsedEpgUrl = epgUrl
            },
            onStream = { parsed: ParsedStream ->
                if (parsed.url.isNotBlank()) {
                    val uid = "m3u:${playlist.id}:" + sha1(
                        "${parsed.kind}|${parsed.url}|${parsed.name}"
                    )
                    val previous = userState[uid]
                    buffer += StreamEntity(
                        uid = uid,
                        playlistId = playlist.id,
                        kind = parsed.kind,
                        name = parsed.name,
                        category = parsed.category,
                        streamUrl = parsed.url,
                        logoUrl = parsed.logoUrl,
                        epgId = parsed.epgId,
                        channelNo = parsed.channelNo,
                        favorite = previous?.favorite ?: false,
                        customName = previous?.customName,
                        catchupType = parsed.catchupType,
                        catchupSource = parsed.catchupSource,
                        catchupDays = parsed.catchupDays,
                        httpHeaders = if (parsed.headers.isEmpty()) null
                        else Json.encodeToString(parsed.headers),
                        searchText = SmartSearch.buildSearchText(
                            parsed.name,
                            parsed.category
                        ),
                        syncTag = tag
                    )
                    count++
                    if (buffer.size >= 400) {
                        flush()
                        onProgress(SyncProgress(SyncPhase.CONTENT, count))
                    }
                }
            }
        )

        if (playlist.url.startsWith("http://", true) ||
            playlist.url.startsWith("https://", true)
        ) {
            http.withSource(playlist.url) { source ->
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    parser.feed(line)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(playlist.url)
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open ${playlist.url}")
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        parser.feed(line)
                    }
                }
            }
        }
        flush()

        if (count == 0) throw IOException("Playlist is empty")
        db.withTransaction {
            db.streamDao().deleteStale(
                playlist.id,
                tag,
                listOf(StreamKind.LIVE, StreamKind.MOVIE, StreamKind.SERIES)
            )
        }
        if (playlist.epgUrl.isNullOrBlank() && !parsedEpgUrl.isNullOrBlank()) {
            db.playlistDao().update(playlist.copy(epgUrl = parsedEpgUrl))
        }
        updateStats(playlist.id)
        onProgress(SyncProgress(SyncPhase.CONTENT, count))
        return count
    }

    private suspend fun syncXtream(
        playlist: PlaylistEntity,
        onProgress: (SyncProgress) -> Unit
    ): Int {
        val account = xtream.login(playlist.url, playlist.username, playlist.password)
        val base = account.baseUrl
        val tag = System.currentTimeMillis()
        val userState = db.streamDao().userState(playlist.id).associateBy { it.uid }
        val buffer = ArrayList<StreamEntity>(400)
        var count = 0
        val successKinds = mutableSetOf<StreamKind>()

        suspend fun flush() {
            if (buffer.isEmpty()) return
            val copy = buffer.toList()
            buffer.clear()
            db.withTransaction { db.streamDao().insertAll(copy) }
        }

        // Live channels are mandatory.
        val liveCategories = runCatching {
            xtream.categories(base, playlist.username, playlist.password, "get_live_categories")
        }.getOrDefault(emptyList()).associate { it.id to it.name }
        val liveStreams = xtream.liveStreams(base, playlist.username, playlist.password)
        liveStreams.forEach { item ->
            val uid = "xt:${playlist.id}:LIVE:${item.id}"
            val previous = userState[uid]
            buffer += StreamEntity(
                uid = uid,
                playlistId = playlist.id,
                kind = StreamKind.LIVE,
                name = item.name,
                category = liveCategories[item.categoryId] ?: "Canlı TV",
                categoryId = item.categoryId,
                streamUrl = xtream.liveUrl(base, playlist.username, playlist.password, item.id, "m3u8"),
                fallbackUrl = xtream.liveUrl(base, playlist.username, playlist.password, item.id, "ts"),
                providerId = item.id,
                logoUrl = item.icon,
                epgId = item.epgId,
                channelNo = item.num,
                favorite = previous?.favorite ?: false,
                customName = previous?.customName,
                catchupType = if (item.archive) "xtream" else null,
                catchupDays = item.archiveDays,
                searchText = SmartSearch.buildSearchText(
                    item.name,
                    liveCategories[item.categoryId] ?: "Canlı TV"
                ),
                syncTag = tag
            )
            count++
            if (buffer.size >= 400) {
                flush()
                onProgress(SyncProgress(SyncPhase.CONTENT, count))
            }
        }
        flush()
        successKinds += StreamKind.LIVE

        // Movies are optional; keep old data if this section fails.
        val movieStart = count
        val movieResult = runCatching {
            val categories = xtream.categories(
                base,
                playlist.username,
                playlist.password,
                "get_vod_categories"
            ).associate { it.id to it.name }
            val movies = xtream.vodStreams(base, playlist.username, playlist.password)
            movies.forEach { item ->
                val uid = "xt:${playlist.id}:MOVIE:${item.id}"
                val previous = userState[uid]
                buffer += StreamEntity(
                    uid = uid,
                    playlistId = playlist.id,
                    kind = StreamKind.MOVIE,
                    name = item.name,
                    category = categories[item.categoryId] ?: "Filmler",
                    categoryId = item.categoryId,
                    streamUrl = xtream.vodUrl(
                        base,
                        playlist.username,
                        playlist.password,
                        item.id,
                        item.ext
                    ),
                    providerId = item.id,
                    logoUrl = item.icon,
                    favorite = previous?.favorite ?: false,
                    customName = previous?.customName,
                    containerExt = item.ext,
                    rating = item.rating,
                    year = item.year,
                    plot = item.plot,
                    cast = item.cast,
                    genre = item.genre,
                    director = item.director,
                    searchText = SmartSearch.buildSearchText(
                        item.name,
                        categories[item.categoryId] ?: "Filmler",
                        item.cast,
                        item.genre,
                        item.plot
                    ),
                    syncTag = tag
                )
                count++
                if (buffer.size >= 400) {
                    flush()
                    onProgress(SyncProgress(SyncPhase.CONTENT, count))
                }
            }
            flush()
            successKinds += StreamKind.MOVIE
        }
        movieResult.exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
            buffer.clear()
            db.withTransaction {
                db.streamDao().deleteTagged(playlist.id, tag, listOf(StreamKind.MOVIE))
            }
            count = movieStart
        }

        // Series list is optional as well; episodes are fetched on demand.
        val seriesStart = count
        val seriesResult = runCatching {
            val categories = xtream.categories(
                base,
                playlist.username,
                playlist.password,
                "get_series_categories"
            ).associate { it.id to it.name }
            val series = xtream.series(base, playlist.username, playlist.password)
            series.forEach { item ->
                val uid = "xt:${playlist.id}:SERIES:${item.id}"
                val previous = userState[uid]
                buffer += StreamEntity(
                    uid = uid,
                    playlistId = playlist.id,
                    kind = StreamKind.SERIES,
                    name = item.name,
                    category = categories[item.categoryId] ?: "Diziler",
                    categoryId = item.categoryId,
                    streamUrl = "",
                    providerId = item.id,
                    seriesId = item.id,
                    logoUrl = item.icon,
                    favorite = previous?.favorite ?: false,
                    customName = previous?.customName,
                    plot = item.plot,
                    cast = item.cast,
                    genre = item.genre,
                    rating = item.rating,
                    year = item.year,
                    searchText = SmartSearch.buildSearchText(
                        item.name,
                        categories[item.categoryId] ?: "Diziler",
                        item.cast,
                        item.genre,
                        item.plot
                    ),
                    syncTag = tag
                )
                count++
                if (buffer.size >= 400) {
                    flush()
                    onProgress(SyncProgress(SyncPhase.CONTENT, count))
                }
            }
            flush()
            successKinds += StreamKind.SERIES
        }
        seriesResult.exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
            buffer.clear()
            db.withTransaction {
                db.streamDao().deleteTagged(playlist.id, tag, listOf(StreamKind.SERIES))
            }
            count = seriesStart
        }

        if (count == 0) throw IOException("Playlist is empty")
        if (successKinds.isNotEmpty()) {
            db.withTransaction {
                db.streamDao().deleteStale(playlist.id, tag, successKinds.toList())
            }
        }

        val updated = playlist.copy(
            url = base,
            epgUrl = playlist.epgUrl?.takeIf { it.isNotBlank() }
                ?: xtream.epgUrl(base, playlist.username, playlist.password)
        )
        db.playlistDao().update(updated)
        updateStats(playlist.id, base)
        onProgress(SyncProgress(SyncPhase.CONTENT, count))
        return count
    }

    private suspend fun updateStats(playlistId: Long, baseUrl: String? = null) {
        val playlist = db.playlistDao().get(playlistId) ?: return
        val live = db.streamDao().count(playlistId, StreamKind.LIVE)
        val movies = db.streamDao().count(playlistId, StreamKind.MOVIE)
        val series = db.streamDao().count(playlistId, StreamKind.SERIES)
        val epgCount = db.epgDao().count(playlistId)
        db.playlistDao().update(
            playlist.copy(
                url = baseUrl ?: playlist.url,
                channelCount = live,
                vodCount = movies + series,
                epgCount = epgCount,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun sha1(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
