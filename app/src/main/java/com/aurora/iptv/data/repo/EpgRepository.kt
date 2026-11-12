package com.aurora.iptv.data.repo

import androidx.room.withTransaction
import com.aurora.iptv.data.db.AuroraDatabase
import com.aurora.iptv.data.db.EpgEntity
import com.aurora.iptv.data.db.PlaylistEntity
import com.aurora.iptv.data.db.PlaylistType
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.source.XmltvParser
import com.aurora.iptv.net.Http
import com.aurora.iptv.search.SmartSearch
import java.util.concurrent.TimeUnit

data class NowNext(val now: EpgEntity?, val next: EpgEntity?)

class EpgRepository(
    private val db: AuroraDatabase,
    private val http: Http
) {
    private val parser = XmltvParser()

    /**
     * Downloads and stores XMLTV data for the playlist. Old rows are only removed after a
     * successful import, so a broken EPG URL never leaves the guide empty.
     */
    suspend fun refresh(
        playlist: PlaylistEntity,
        onProgress: (Int) -> Unit = {}
    ): Int {
        val url = epgUrlFor(playlist) ?: return 0
        val liveStreams = db.streamDao().getLive(playlist.id)
        if (liveStreams.isEmpty()) return 0

        val byName = HashMap<String, MutableList<StreamEntity>>()
        val byEpgId = HashMap<String, MutableList<StreamEntity>>()
        liveStreams.forEach { stream ->
            byName.getOrPut(SmartSearch.normalize(stream.name)) { mutableListOf() }.add(stream)
            stream.epgId?.takeIf { it.isNotBlank() }?.let { epgId ->
                byEpgId.getOrPut(epgId) { mutableListOf() }.add(stream)
            }
        }

        val resolvedChannels = HashSet<String>()
        val epgIdUpdates = HashSet<Pair<String, String>>()
        val tag = System.currentTimeMillis()
        var buffer = ArrayList<EpgEntity>(500)
        var total = 0
        var channelCount = 0

        suspend fun flush() {
            if (buffer.isEmpty()) return
            val copy = buffer.toList()
            buffer = ArrayList(500)
            db.withTransaction { db.epgDao().insertAll(copy) }
        }

        http.withStream(url) { input ->
            parser.parse(
                input = input,
                onChannel = { channel ->
                    val matched = mutableListOf<StreamEntity>()
                    byEpgId[channel.id]?.let { matched += it }
                    if (matched.isEmpty()) {
                        val keys = channel.names.map { SmartSearch.normalize(it) } +
                            SmartSearch.normalize(channel.id)
                        keys.filter { it.isNotBlank() }.forEach { key ->
                            byName[key]?.let { matched += it }
                        }
                    }
                    if (matched.isNotEmpty()) {
                        resolvedChannels += channel.id
                        matched.forEach { stream ->
                            if (stream.epgId != channel.id) {
                                epgIdUpdates += stream.uid to channel.id
                            }
                        }
                    }
                    channelCount++
                    if (channelCount % 100 == 0) onProgress(channelCount)
                },
                onProgramme = { programme ->
                    if (programme.channelId in resolvedChannels) {
                        buffer += EpgEntity(
                            playlistId = playlist.id,
                            epgId = programme.channelId,
                            title = programme.title,
                            description = programme.description,
                            startTime = programme.start,
                            endTime = programme.end,
                            syncTag = tag
                        )
                        total++
                        if (buffer.size >= 500) {
                            flush()
                            onProgress(total)
                        }
                    }
                },
                onProgress = { count -> if (count % 1000 == 0) onProgress(count) }
            )
        }
        flush()

        if (total > 0) {
            epgIdUpdates.forEach { (uid, epgId) ->
                db.streamDao().setEpgId(uid, epgId)
            }
            val now = System.currentTimeMillis()
            db.withTransaction {
                db.epgDao().deleteStale(playlist.id, tag)
                db.epgDao().prune(
                    now - TimeUnit.DAYS.toMillis(2),
                    now + TimeUnit.DAYS.toMillis(8)
                )
            }
        }
        onProgress(total)
        return total
    }

    fun epgUrlFor(playlist: PlaylistEntity): String? {
        playlist.epgUrl?.takeIf { it.isNotBlank() }?.let { return it }
        if (playlist.type == PlaylistType.XTREAM && playlist.url.isNotBlank()) {
            val base = playlist.url.trimEnd('/')
            return "$base/xmltv.php?username=${encode(playlist.username)}&password=${encode(playlist.password)}"
        }
        return null
    }

    suspend fun nowNext(
        playlistId: Long,
        channels: List<StreamEntity>
    ): Map<String, NowNext> {
        val epgIds = channels.mapNotNull { it.epgId?.takeIf { id -> id.isNotBlank() } }.distinct()
        if (epgIds.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        val programmes = programmesForChannels(
            playlistId = playlistId,
            epgIds = epgIds,
            from = now - TimeUnit.HOURS.toMillis(6),
            to = now + TimeUnit.HOURS.toMillis(18)
        )
        val grouped = programmes.groupBy { it.epgId }
        return grouped.mapValues { (_, list) ->
            NowNext(
                now = list.firstOrNull { it.startTime <= now && it.endTime > now },
                next = list.firstOrNull { it.startTime > now }
            )
        }
    }

    suspend fun guide(
        playlistId: Long,
        channels: List<StreamEntity>,
        from: Long,
        to: Long
    ): Map<String, List<EpgEntity>> {
        val epgIds = channels.mapNotNull { it.epgId?.takeIf { id -> id.isNotBlank() } }.distinct()
        if (epgIds.isEmpty()) return emptyMap()
        return programmesForChannels(playlistId, epgIds, from, to)
            .groupBy { it.epgId }
    }

    suspend fun archive(
        playlistId: Long,
        channel: StreamEntity,
        days: Int = 1
    ): List<EpgEntity> {
        val epgId = channel.epgId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val now = System.currentTimeMillis()
        val limit = days.coerceIn(1, 30)
        return db.epgDao().rangeForChannels(
            playlistId = playlistId,
            epgIds = listOf(epgId),
            from = now - TimeUnit.DAYS.toMillis(limit.toLong()),
            to = now
        ).filter { it.endTime <= now }.sortedByDescending { it.startTime }
    }

    /**
     * SQLite has a hard limit of 999 host parameters per statement. Large playlists can
     * easily exceed that with channel lists, so query in small batches and merge.
     */
    private suspend fun programmesForChannels(
        playlistId: Long,
        epgIds: List<String>,
        from: Long,
        to: Long
    ): List<EpgEntity> {
        if (epgIds.isEmpty()) return emptyList()
        return epgIds.chunked(EPG_QUERY_BATCH)
            .flatMap { batch ->
                db.epgDao().rangeForChannels(
                    playlistId = playlistId,
                    epgIds = batch,
                    from = from,
                    to = to
                )
            }
            .sortedBy { it.startTime }
    }

    suspend fun clearAll() {
        db.withTransaction {
            db.epgDao().deleteAll()
            db.playlistDao().getAll().forEach { playlist ->
                db.playlistDao().update(playlist.copy(epgCount = 0))
            }
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val EPG_QUERY_BATCH = 400
    }
}
