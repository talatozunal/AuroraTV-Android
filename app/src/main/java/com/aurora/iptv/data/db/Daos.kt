package com.aurora.iptv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "UPDATE playlists SET updatedAt = :updatedAt, channelCount = :channels, " +
            "vodCount = :vod, epgCount = :epg WHERE id = :id"
    )
    suspend fun updateStats(id: Long, updatedAt: Long, channels: Int, vod: Int, epg: Int)
}

@Dao
interface StreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(streams: List<StreamEntity>)

    @Query("SELECT * FROM streams WHERE uid = :uid")
    suspend fun getByUid(uid: String): StreamEntity?

    @Query("SELECT * FROM streams WHERE uid = :uid")
    fun observeByUid(uid: String): Flow<StreamEntity?>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND kind = :kind ORDER BY name COLLATE NOCASE")
    suspend fun getAll(playlistId: Long, kind: StreamKind): List<StreamEntity>

    @Query(
        "SELECT * FROM streams WHERE playlistId = :playlistId AND kind = :kind " +
            "ORDER BY name COLLATE NOCASE"
    )
    fun observeAll(playlistId: Long, kind: StreamKind): Flow<List<StreamEntity>>

    @Query(
        "SELECT * FROM streams WHERE playlistId = :playlistId AND kind = :kind " +
            "AND (:category IS NULL OR category = :category) ORDER BY name COLLATE NOCASE"
    )
    fun observeByCategory(
        playlistId: Long,
        kind: StreamKind,
        category: String?
    ): Flow<List<StreamEntity>>

    @Query(
        "SELECT category, COUNT(*) AS count FROM streams " +
            "WHERE playlistId = :playlistId AND kind = :kind GROUP BY category ORDER BY category COLLATE NOCASE"
    )
    fun observeCategories(playlistId: Long, kind: StreamKind): Flow<List<CategoryRow>>

    @Query(
        "SELECT * FROM streams WHERE playlistId = :playlistId AND kind = :kind " +
            "AND favorite = 1 ORDER BY name COLLATE NOCASE"
    )
    fun observeFavorites(playlistId: Long, kind: StreamKind): Flow<List<StreamEntity>>

    @Query(
        "SELECT * FROM streams WHERE playlistId = :playlistId AND kind = 'LIVE' " +
            "AND favorite = 1 ORDER BY name COLLATE NOCASE"
    )
    fun observeFavoriteLive(playlistId: Long): Flow<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND kind = 'LIVE' ORDER BY name COLLATE NOCASE")
    suspend fun getLive(playlistId: Long): List<StreamEntity>

    @Query(
        "SELECT * FROM streams WHERE playlistId = :playlistId AND kind = 'LIVE' AND category = :category " +
            "ORDER BY name COLLATE NOCASE"
    )
    suspend fun getLiveByCategory(playlistId: Long, category: String): List<StreamEntity>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND kind = :kind")
    suspend fun getByKind(playlistId: Long, kind: StreamKind): List<StreamEntity>

    @Query(
        "SELECT * FROM streams WHERE playlistId = :playlistId AND kind != 'EPISODE' AND (" +
            "searchText LIKE '%' || :a || '%' OR " +
            "searchText LIKE '%' || :b || '%' OR " +
            "searchText LIKE '%' || :c || '%') LIMIT 500"
    )
    suspend fun search(playlistId: Long, a: String, b: String, c: String): List<StreamEntity>

    @Query("SELECT * FROM streams WHERE seriesId = :seriesId AND kind = 'EPISODE' ORDER BY season, episode")
    suspend fun episodes(seriesId: String): List<StreamEntity>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND seriesId = :seriesId AND kind = 'EPISODE' ORDER BY season, episode")
    suspend fun episodes(playlistId: Long, seriesId: String): List<StreamEntity>

    @Query("DELETE FROM streams WHERE playlistId = :playlistId AND seriesId = :seriesId AND kind = 'EPISODE'")
    suspend fun deleteSeriesEpisodes(playlistId: Long, seriesId: String)

    @Query("SELECT COUNT(*) FROM streams WHERE playlistId = :playlistId AND kind = :kind")
    suspend fun count(playlistId: Long, kind: StreamKind): Int

    @Query("SELECT uid, favorite, customName FROM streams WHERE playlistId = :playlistId")
    suspend fun userState(playlistId: Long): List<StreamUserState>

    @Query("UPDATE streams SET favorite = :favorite WHERE uid = :uid")
    suspend fun setFavorite(uid: String, favorite: Boolean)

    @Query("UPDATE streams SET customName = :name WHERE uid = :uid")
    suspend fun setCustomName(uid: String, name: String?)

    @Query("UPDATE streams SET epgId = :epgId WHERE uid = :uid")
    suspend fun setEpgId(uid: String, epgId: String)

    @Update
    suspend fun update(stream: StreamEntity)

    @Query("DELETE FROM streams WHERE playlistId = :playlistId AND syncTag != :tag AND kind IN (:kinds)")
    suspend fun deleteStale(playlistId: Long, tag: Long, kinds: List<StreamKind>)

    @Query("DELETE FROM streams WHERE playlistId = :playlistId AND syncTag = :tag AND kind IN (:kinds)")
    suspend fun deleteTagged(playlistId: Long, tag: Long, kinds: List<StreamKind>)

    @Query("DELETE FROM streams WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface EpgDao {
    @Insert
    suspend fun insertAll(programs: List<EpgEntity>)

    @Query("SELECT * FROM epg WHERE playlistId = :playlistId AND endTime > :from AND startTime < :to ORDER BY startTime")
    fun observeRange(playlistId: Long, from: Long, to: Long): Flow<List<EpgEntity>>

    @Query(
        "SELECT * FROM epg WHERE playlistId = :playlistId AND endTime > :from AND startTime < :to " +
            "AND epgId IN (:epgIds) ORDER BY startTime"
    )
    suspend fun rangeForChannels(
        playlistId: Long,
        epgIds: List<String>,
        from: Long,
        to: Long
    ): List<EpgEntity>

    @Query("SELECT * FROM epg WHERE playlistId = :playlistId AND epgId = :epgId AND endTime > :from ORDER BY startTime LIMIT 40")
    suspend fun upcoming(playlistId: Long, epgId: String, from: Long): List<EpgEntity>

    @Query("SELECT COUNT(*) FROM epg WHERE playlistId = :playlistId")
    suspend fun count(playlistId: Long): Int

    @Query("DELETE FROM epg WHERE playlistId = :playlistId AND syncTag != :tag")
    suspend fun deleteStale(playlistId: Long, tag: Long)

    @Query("DELETE FROM epg WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("DELETE FROM epg WHERE startTime < :before OR startTime > :after")
    suspend fun prune(before: Long, after: Long)

    @Query("DELETE FROM epg")
    suspend fun deleteAll()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE uid = :uid")
    suspend fun get(uid: String): ProgressEntity?

    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    @Query("DELETE FROM progress")
    suspend fun clear()
}
