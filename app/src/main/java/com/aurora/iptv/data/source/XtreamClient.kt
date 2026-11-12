package com.aurora.iptv.data.source

import com.aurora.iptv.net.Http
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class XtreamAuthException : IOException("Xtream authentication failed")

data class XtreamAccount(val baseUrl: String, val status: String)

data class XtreamCategory(val id: String, val name: String)

data class XtreamLive(
    val id: String,
    val num: Int?,
    val name: String,
    val icon: String?,
    val epgId: String?,
    val categoryId: String,
    val archive: Boolean,
    val archiveDays: Int
)

data class XtreamVod(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val ext: String,
    val rating: String?,
    val year: String?,
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val director: String?
)

data class XtreamSeries(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val rating: String?,
    val year: String?
)

data class XtreamEpisode(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val ext: String,
    val plot: String?,
    val cast: String?,
    val rating: String?,
    val year: String?,
    val icon: String?
)

data class XtreamDetails(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val cover: String? = null
)

class XtreamClient(private val http: Http) {

    private fun baseUrl(raw: String): String {
        var value = raw.trim()
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "http://$value"
        }
        value = value.trimEnd('/')
        value = value.substringBefore("/player_api.php", value)
        value = value.substringBefore("/get.php", value)
        return value.trimEnd('/')
    }

    private fun api(
        base: String,
        user: String,
        pass: String,
        query: String
    ): String {
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        return "$base/player_api.php?username=$encodedUser&password=$encodedPass&$query"
    }

    suspend fun login(rawBase: String, user: String, pass: String): XtreamAccount {
        val base = baseUrl(rawBase)
        val element = http.json(
            api(base, user, pass, "")
        )
        val obj = element as? JsonObject ?: throw XtreamAuthException()
        val userInfo = obj["user_info"] as? JsonObject
        val auth = userInfo?.get("auth")?.jsonPrimitive?.intOrNull
        val status = userInfo?.get("status")?.jsonPrimitive?.contentOrNull
        val active = auth == 1 ||
            status.equals("Active", true) ||
            status.equals("Trial", true)
        if (!active) throw XtreamAuthException()
        return XtreamAccount(base, status ?: "Active")
    }

    suspend fun categories(
        base: String,
        user: String,
        pass: String,
        action: String
    ): List<XtreamCategory> {
        val array = http.json(api(base, user, pass, "action=$action")).asArrayOrNull() ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item.asObjectOrNull() ?: return@mapNotNull null
            val id = obj.string("category_id") ?: return@mapNotNull null
            XtreamCategory(id, obj.string("category_name") ?: "Genel")
        }
    }

    suspend fun liveStreams(
        base: String,
        user: String,
        pass: String
    ): List<XtreamLive> {
        val array = http.json(api(base, user, pass, "action=get_live_streams")).asArrayOrNull()
            ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item.asObjectOrNull() ?: return@mapNotNull null
            val id = obj.string("stream_id") ?: return@mapNotNull null
            val archive = obj.int("tv_archive") == 1
            XtreamLive(
                id = id,
                num = obj.int("num"),
                name = obj.string("name") ?: "Kanal $id",
                icon = obj.string("stream_icon"),
                epgId = obj.string("epg_channel_id")?.takeIf { it.isNotBlank() },
                categoryId = obj.string("category_id").orEmpty(),
                archive = archive,
                archiveDays = obj.int("tv_archive_duration")?.coerceIn(0, 30) ?: 0
            )
        }
    }

    suspend fun vodStreams(
        base: String,
        user: String,
        pass: String
    ): List<XtreamVod> {
        val array = http.json(api(base, user, pass, "action=get_vod_streams")).asArrayOrNull()
            ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item.asObjectOrNull() ?: return@mapNotNull null
            val id = obj.string("stream_id") ?: return@mapNotNull null
            XtreamVod(
                id = id,
                name = obj.string("name") ?: "Film $id",
                icon = obj.string("stream_icon"),
                categoryId = obj.string("category_id").orEmpty(),
                ext = obj.string("container_extension") ?: "mp4",
                rating = obj.string("rating"),
                year = obj.string("year"),
                plot = obj.string("plot"),
                cast = obj.string("cast"),
                genre = obj.string("genre"),
                director = obj.string("director")
            )
        }
    }

    suspend fun series(
        base: String,
        user: String,
        pass: String
    ): List<XtreamSeries> {
        val array = http.json(api(base, user, pass, "action=get_series")).asArrayOrNull()
            ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item.asObjectOrNull() ?: return@mapNotNull null
            val id = obj.string("series_id") ?: return@mapNotNull null
            XtreamSeries(
                id = id,
                name = obj.string("name") ?: "Dizi $id",
                icon = obj.string("cover"),
                categoryId = obj.string("category_id").orEmpty(),
                plot = obj.string("plot"),
                cast = obj.string("cast"),
                genre = obj.string("genre"),
                rating = obj.string("rating"),
                year = obj.string("releaseDate")?.take(4)
            )
        }
    }

    suspend fun vodDetails(
        base: String,
        user: String,
        pass: String,
        vodId: String
    ): XtreamDetails {
        val obj = http.json(api(base, user, pass, "action=get_vod_info&vod_id=$vodId")) as? JsonObject
            ?: return XtreamDetails()
        val info = obj["info"] as? JsonObject
        return XtreamDetails(
            plot = info?.string("plot") ?: info?.string("description"),
            cast = info?.string("cast") ?: info?.string("actors"),
            director = info?.string("director"),
            genre = info?.string("genre"),
            rating = info?.string("rating"),
            year = info?.string("releasedate")?.take(4) ?: info?.string("year"),
            cover = info?.string("movie_image") ?: info?.string("cover_big")
        )
    }

    suspend fun seriesDetails(
        base: String,
        user: String,
        pass: String,
        seriesId: String
    ): Pair<XtreamDetails, List<XtreamEpisode>> {
        val obj = http.json(api(base, user, pass, "action=get_series_info&series_id=$seriesId"))
            as? JsonObject ?: return XtreamDetails() to emptyList()
        val info = obj["info"] as? JsonObject
        val details = XtreamDetails(
            plot = info?.string("plot"),
            cast = info?.string("cast"),
            director = info?.string("director"),
            genre = info?.string("genre"),
            rating = info?.string("rating"),
            year = info?.string("releaseDate")?.take(4)
                ?: info?.string("release_date")?.take(4)
                ?: info?.string("year"),
            cover = info?.string("cover")
        )
        val episodes = mutableListOf<XtreamEpisode>()
        val episodesObject = obj["episodes"] as? JsonObject
        for (seasonValue in episodesObject?.values.orEmpty()) {
            val seasonArray = seasonValue.asArrayOrNull() ?: continue
            for (item in seasonArray) {
                val episodeObj = item.asObjectOrNull() ?: continue
                val episodeInfo = episodeObj["info"] as? JsonObject
                val id = episodeObj.string("id") ?: continue
                episodes += XtreamEpisode(
                    id = id,
                    title = episodeObj.string("title") ?: "Bölüm",
                    season = episodeObj.int("season") ?: episodeInfo?.int("season") ?: 1,
                    episode = episodeObj.int("episode_num") ?: episodeInfo?.int("episode") ?: 0,
                    ext = episodeObj.string("container_extension") ?: "mp4",
                    plot = episodeInfo?.string("plot"),
                    cast = episodeInfo?.string("cast"),
                    rating = episodeInfo?.string("rating"),
                    year = episodeInfo?.string("releasedate")?.take(4),
                    icon = episodeInfo?.string("movie_image")
                )
            }
        }
        episodes.sortWith(compareBy({ it.season }, { it.episode }))
        return details to episodes
    }

    fun liveUrl(base: String, user: String, pass: String, streamId: String, ext: String = "m3u8"): String =
        "$base/live/${encodePath(user)}/${encodePath(pass)}/$streamId.$ext"

    fun vodUrl(base: String, user: String, pass: String, streamId: String, ext: String): String =
        "$base/movie/${encodePath(user)}/${encodePath(pass)}/$streamId.${ext.ifBlank { "mp4" }}"

    fun episodeUrl(base: String, user: String, pass: String, episodeId: String, ext: String): String =
        "$base/series/${encodePath(user)}/${encodePath(pass)}/$episodeId.${ext.ifBlank { "mp4" }}"

    fun epgUrl(base: String, user: String, pass: String): String {
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val encodedPass = URLEncoder.encode(pass, "UTF-8")
        return "$base/xmltv.php?username=$encodedUser&password=$encodedPass"
    }

    /**
     * Xtream timeshift URL, e.g. /timeshift/user/pass/60/2026-10-25:20-15/1234.ts
     */
    fun timeshiftUrl(
        base: String,
        user: String,
        pass: String,
        streamId: String,
        startEpochSeconds: Long,
        durationMinutes: Int
    ): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val start = formatter.format(Date(startEpochSeconds * 1000L))
        val duration = durationMinutes.coerceAtLeast(1)
        return "$base/timeshift/${encodePath(user)}/${encodePath(pass)}/$duration/$start/$streamId.ts"
    }

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonObject.string(key: String): String? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JsonObject.int(key: String): Int? {
        val element = this[key] ?: return null
        return when (element) {
            is JsonPrimitive -> element.intOrNull ?: element.contentOrNull?.toDoubleOrNull()?.toInt()
            else -> null
        }
    }
}
