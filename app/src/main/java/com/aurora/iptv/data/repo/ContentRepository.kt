package com.aurora.iptv.data.repo

import com.aurora.iptv.data.db.AuroraDatabase
import com.aurora.iptv.data.db.EpgEntity
import com.aurora.iptv.data.db.PlaylistEntity
import com.aurora.iptv.data.db.ProgressEntity
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.search.SmartSearch
import kotlinx.coroutines.flow.Flow

data class SearchOutcome(
    val query: SmartSearch.SearchQuery,
    val live: List<StreamEntity>,
    val vod: List<StreamEntity>
)

/**
 * Read/query facade for the UI. Network mutations stay in [PlaylistRepository].
 */
class ContentRepository(
    private val db: AuroraDatabase,
    private val epgRepository: EpgRepository
) {

    val playlists: Flow<List<PlaylistEntity>> = db.playlistDao().observeAll()

    suspend fun playlist(id: Long): PlaylistEntity? = db.playlistDao().get(id)

    suspend fun stream(uid: String): StreamEntity? = db.streamDao().getByUid(uid)

    suspend fun liveChannels(playlistId: Long): List<StreamEntity> =
        db.streamDao().getLive(playlistId)

    suspend fun liveInCategory(playlistId: Long, category: String): List<StreamEntity> =
        db.streamDao().getLiveByCategory(playlistId, category)

    suspend fun seriesEpisodes(playlistId: Long, seriesId: String): List<StreamEntity> =
        db.streamDao().episodes(playlistId, seriesId)

    /**
     * On-device search. Missing query slots are replaced by a sentinel that cannot
     * appear in normalized search text, so a two-word query never matches every row.
     */
    suspend fun search(playlistId: Long, raw: String, limit: Int = 80): SearchOutcome {
        val parsed = SmartSearch.parse(raw)
        if (parsed.tokens.isEmpty()) return SearchOutcome(parsed, emptyList(), emptyList())

        val sentinel = "\u0001"
        val a = parsed.tokens.getOrNull(0) ?: sentinel
        val b = parsed.tokens.getOrNull(1) ?: sentinel
        val c = parsed.tokens.getOrNull(2) ?: sentinel
        val candidates = db.streamDao().search(playlistId, a, b, c)
        val ranked = SmartSearch.rank(candidates, raw, limit).map { it.stream }
        return SearchOutcome(
            query = parsed,
            live = ranked.filter { it.kind == StreamKind.LIVE },
            vod = ranked.filter { it.kind == StreamKind.MOVIE || it.kind == StreamKind.SERIES }
        )
    }

    suspend fun nowNext(
        playlistId: Long,
        channels: List<StreamEntity>
    ): Map<String, NowNext> = epgRepository.nowNext(playlistId, channels)

    suspend fun guide(
        playlistId: Long,
        channels: List<StreamEntity>,
        from: Long,
        to: Long
    ): Map<String, List<EpgEntity>> = epgRepository.guide(playlistId, channels, from, to)

    suspend fun archive(
        playlistId: Long,
        channel: StreamEntity,
        days: Int
    ): List<EpgEntity> = epgRepository.archive(playlistId, channel, days)

    suspend fun setFavorite(uid: String, favorite: Boolean) =
        db.streamDao().setFavorite(uid, favorite)

    suspend fun rename(uid: String, name: String?) =
        db.streamDao().setCustomName(uid, name?.trim()?.takeIf { it.isNotBlank() })

    suspend fun progress(uid: String): ProgressEntity? = db.progressDao().get(uid)

    suspend fun saveProgress(uid: String, positionMs: Long, durationMs: Long) {
        if (positionMs < 5_000L || durationMs <= 0L) return
        db.progressDao().upsert(
            ProgressEntity(
                uid = uid,
                position = positionMs,
                duration = durationMs
            )
        )
    }
}
