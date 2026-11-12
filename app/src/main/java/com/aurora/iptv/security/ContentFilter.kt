package com.aurora.iptv.security

import android.content.Context
import android.util.Base64
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.search.SmartSearch
import java.security.MessageDigest
import java.security.SecureRandom

/** Adult content + kids profile policy. */
object ContentFilter {
    private val adultTerms = setOf(
        "xxx", "porno", "porn", "erotik", "erotic", "adult", "yetiskin",
        "sex", "playboy", "brazzers", "hustler", "onlyfans", "hentai",
        "18", "dick"
    )

    fun isAdultText(text: String): Boolean {
        val haystack = SmartSearch.normalize(text)
        if (haystack.isBlank()) return false
        val tokens = haystack.split(' ').toSet()
        return adultTerms.any { it in tokens }
    }

    fun isAdult(stream: StreamEntity): Boolean = isAdultText(
        listOfNotNull(
            stream.name,
            stream.category,
            stream.genre,
            stream.plot?.take(200),
            stream.rating
        ).joinToString(" ")
    )

    fun isKidsBlocked(stream: StreamEntity): Boolean = isAdult(stream)
}

/** Iterated SHA-256 PIN hashing with a random salt. No plain PIN is persisted. */
object PinManager {
    private const val ITERATIONS = 12_000
    private const val PIN_LENGTH = 4

    fun isValidPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var value = (salt + pin).toByteArray(Charsets.UTF_8)
        repeat(ITERATIONS) {
            digest.reset()
            value = digest.digest(value)
        }
        return Base64.encodeToString(value, Base64.NO_WRAP)
    }

    fun verify(pin: String, salt: String?, hash: String?): Boolean {
        if (salt.isNullOrBlank() || hash.isNullOrBlank() || !isValidPin(pin)) return false
        val candidate = hash(pin, salt)
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            hash.toByteArray(Charsets.UTF_8)
        )
    }

    fun randomPinHint(): String = "0000"
}
