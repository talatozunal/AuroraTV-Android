package com.aurora.iptv.search

import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Fully on-device search engine. It does not call any network service:
 * it normalizes Turkish characters, expands genre/actor synonyms,
 * detects intent words ("maç", "çocuk", "belgesel"...) and ranks
 * channels, movies and series with fuzzy matching.
 */
object SmartSearch {

    enum class Intent(val triggers: List<String>, val categoryWords: List<String>) {
        SPORTS(
            triggers = listOf("mac", "spor", "futbol", "basket", "lig", "sampiyonlar", "formula", "motogp"),
            categoryWords = listOf("spor", "sport", "futbol", "football", "soccer", "basket", "mac", "lig", "bein")
        ),
        KIDS(
            triggers = listOf("cocuk", "cizgi", "animasyon", "kids", "cartoon", "masal"),
            categoryWords = listOf("cocuk", "kids", "cizgi", "cartoon", "animasyon", "animation", "masal")
        ),
        NEWS(
            triggers = listOf("haber", "news", "gundem"),
            categoryWords = listOf("haber", "news", "gundem", "cnn", "ntv")
        ),
        MOVIES(
            triggers = listOf("film", "movie", "sinema", "vod"),
            categoryWords = listOf("film", "movie", "sinema", "vod", "cinema")
        ),
        SERIES(
            triggers = listOf("dizi", "series", "show"),
            categoryWords = listOf("dizi", "series", "show")
        ),
        MUSIC(
            triggers = listOf("muzik", "music", "klip", "konser"),
            categoryWords = listOf("muzik", "music", "klip", "mtv", "konser")
        ),
        DOCUMENTARY(
            triggers = listOf("belgesel", "documentary", "docu", "doga"),
            categoryWords = listOf("belgesel", "documentary", "docu", "discovery", "natgeo", "history")
        ),
        ACTION(
            triggers = listOf("aksiyon", "action"),
            categoryWords = listOf("aksiyon", "action")
        ),
        COMEDY(
            triggers = listOf("komedi", "comedy", "gulduru"),
            categoryWords = listOf("komedi", "comedy")
        ),
        HORROR(
            triggers = listOf("korku", "horror", "gerilim"),
            categoryWords = listOf("korku", "horror", "gerilim", "thriller")
        )
    }

    data class SearchQuery(
        val raw: String,
        val normalized: String,
        val tokens: List<String>,
        val intents: List<Intent>
    )

    data class Ranked(val stream: StreamEntity, val score: Double)

    private val synonymMap: Map<String, List<String>> = mapOf(
        "film" to listOf("movie", "sinema", "cinema", "vod"),
        "dizi" to listOf("series", "show", "diziler"),
        "cocuk" to listOf("kids", "kid", "cartoon", "animasyon", "animation", "cizgi", "masal"),
        "cizgi" to listOf("cartoon", "animasyon", "animation", "cocuk"),
        "mac" to listOf("spor", "sport", "sports", "futbol", "football", "soccer", "lig", "league", "match"),
        "spor" to listOf("sport", "sports", "mac", "football", "soccer"),
        "haber" to listOf("news", "gundem"),
        "muzik" to listOf("music", "mtv", "klip", "clip", "konser"),
        "belgesel" to listOf("documentary", "docu", "discovery", "natgeo"),
        "aksiyon" to listOf("action"),
        "komedi" to listOf("comedy"),
        "korku" to listOf("horror"),
        "gerilim" to listOf("thriller"),
        "bilimkurgu" to listOf("scifi", "sciencefiction"),
        "romantik" to listOf("romance", "romantic"),
        "dram" to listOf("drama"),
        "yerli" to listOf("turk", "turkish", "turkce"),
        "yabanci" to listOf("foreign", "english"),
        "animasyon" to listOf("animation", "cartoon", "cizgi"),
        "savas" to listOf("war"),
        "fantastik" to listOf("fantasy")
    )

    private val whitespace = Regex("\\s+")
    private val combining = Regex("\\p{Mn}+")

    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        val replaced = raw
            .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
            .replace('Ş', 's').replace('ş', 's')
            .replace('Ğ', 'g').replace('ğ', 'g')
            .replace('Ü', 'u').replace('ü', 'u')
            .replace('Ö', 'o').replace('ö', 'o')
            .replace('Ç', 'c').replace('ç', 'c')
            .replace('Â', 'a').replace('â', 'a')
            .replace('Î', 'i').replace('î', 'i')
            .replace('Û', 'u').replace('û', 'u')
            .lowercase(Locale.ROOT)
        val folded = Normalizer.normalize(replaced, Normalizer.Form.NFD)
            .replace(combining, "")
        val sb = StringBuilder(folded.length)
        for (ch in folded) {
            if (ch in 'a'..'z' || ch in '0'..'9') sb.append(ch) else sb.append(' ')
        }
        return sb.toString().replace(whitespace, " ").trim()
    }

    fun buildSearchText(
        name: String,
        category: String,
        cast: String? = null,
        genre: String? = null,
        plot: String? = null
    ): String = normalize(
        listOfNotNull(name, category, cast, genre, plot?.take(400))
            .joinToString(" ")
    )

    fun parse(raw: String): SearchQuery {
        val normalized = normalize(raw)
        val tokens = normalized.split(' ').filter { it.isNotBlank() }.distinct()
        val intents = Intent.entries.filter { intent ->
            intent.triggers.any { trigger ->
                tokens.any { token ->
                    token == trigger ||
                        (token.length >= 3 && trigger.startsWith(token)) ||
                        (trigger.length >= 3 && token.startsWith(trigger))
                }
            } || intent.categoryWords.any { tokens.contains(it) }
        }
        return SearchQuery(raw, normalized, tokens, intents)
    }

    fun rank(streams: List<StreamEntity>, raw: String, limit: Int = 60): List<Ranked> {
        val query = parse(raw)
        if (query.tokens.isEmpty()) return emptyList()

        val expanded = query.tokens.flatMap { token ->
            listOf(token) + synonymMap[token].orEmpty()
        }.distinct()

        return streams.asSequence().mapNotNull { stream ->
            val name = normalize(stream.name)
            val category = normalize(stream.category)
            val cast = normalize(stream.cast.orEmpty())
            val genre = normalize(stream.genre.orEmpty())
            val plot = normalize(stream.plot.orEmpty().take(400))

            var score = 0.0
            for (token in expanded) {
                score += fieldScore(name, token, 4.2)
                score += fieldScore(category, token, 2.1)
                score += fieldScore(cast, token, 3.2)
                score += fieldScore(genre, token, 2.0)
                score += fieldScore(plot, token, 0.45)
            }

            query.intents.forEach { intent ->
                if (intent.categoryWords.any { word ->
                        category.contains(word) || name.contains(word)
                    }
                ) {
                    score += 3.8
                }
                if (intent == Intent.SPORTS && stream.kind == StreamKind.LIVE) score += 0.6
                if (intent == Intent.MOVIES && stream.kind == StreamKind.MOVIE) score += 1.4
                if (intent == Intent.SERIES && stream.kind == StreamKind.SERIES) score += 1.4
            }

            if (stream.favorite) score += 0.9
            if (query.tokens.any { it.length >= 2 && name.startsWith(it) }) score += 1.1

            if (score <= 0.4) null else Ranked(stream, score)
        }.sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    fun intentLabel(intent: Intent): String = when (intent) {
        Intent.SPORTS -> "spor"
        Intent.KIDS -> "çocuk"
        Intent.NEWS -> "haber"
        Intent.MOVIES -> "film"
        Intent.SERIES -> "dizi"
        Intent.MUSIC -> "müzik"
        Intent.DOCUMENTARY -> "belgesel"
        Intent.ACTION -> "aksiyon"
        Intent.COMEDY -> "komedi"
        Intent.HORROR -> "korku"
    }

    private fun fieldScore(field: String, token: String, weight: Double): Double {
        if (field.isBlank() || token.isBlank()) return 0.0
        if (field == token) return 12.0 * weight
        if (field.startsWith("$token ")) return 9.0 * weight
        if (field.contains(" $token ")) return 7.5 * weight
        if (field.contains(token)) return 5.0 * weight
        if (token.length >= 4 && isSubsequence(token, field)) return 1.6 * weight
        if (token.length >= 5) {
            val close = field.split(' ').any { word ->
                abs(word.length - token.length) <= 1 && levenshteinAtMostOne(word, token)
            }
            if (close) return 3.2 * weight
        }
        return 0.0
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var i = 0
        for (ch in haystack) {
            if (i < needle.length && needle[i] == ch) i++
            if (i == needle.length) return true
        }
        return needle.isEmpty()
    }

    private fun levenshteinAtMostOne(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            edits++
            if (edits > 1) return false
            when {
                a.length > b.length -> i++
                b.length > a.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
