package com.aurora.iptv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlaylistType { M3U, XTREAM }

enum class StreamKind { LIVE, MOVIE, SERIES, EPISODE }

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    /** M3U URL or Xtream base URL. */
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0L,
    val channelCount: Int = 0,
    val vodCount: Int = 0,
    val epgCount: Int = 0
)

@Entity(
    tableName = "streams",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "uid"], unique = true),
        Index(value = ["playlistId", "kind"]),
        Index(value = ["playlistId", "kind", "category"]),
        Index(value = ["playlistId", "epgId"]),
        Index(value = ["seriesId"])
    ]
)
data class StreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable across syncs; used for favorites, renames and watch progress. */
    val uid: String,
    val playlistId: Long,
    val kind: StreamKind,
    val name: String,
    val category: String,
    val categoryId: String = "",
    val streamUrl: String,
    val fallbackUrl: String? = null,
    /** Provider stream id (Xtream) used to build catch-up URLs. */
    val providerId: String? = null,
    val logoUrl: String? = null,
    val epgId: String? = null,
    val channelNo: Int? = null,
    val favorite: Boolean = false,
    val customName: String? = null,
    val catchupType: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int = 0,
    val seriesId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val containerExt: String = "",
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val year: String? = null,
    /** JSON object of extra HTTP headers required by the stream. */
    val httpHeaders: String? = null,
    /** Normalized text used by the on-device search engine. */
    val searchText: String = "",
    /** Sync generation; rows not touched by the latest sync are removed. */
    val syncTag: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "epg",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "epgId", "startTime"]),
        Index(value = ["playlistId", "startTime", "endTime"])
    ]
)
data class EpgEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val epgId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val syncTag: Long = 0L
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val uid: String,
    val position: Long,
    val duration: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CategoryRow(
    val category: String,
    val count: Int
)

data class StreamUserState(
    val uid: String,
    val favorite: Boolean,
    val customName: String?
)
