package com.aurora.iptv.data.settings

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.iptv.security.PinManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

private val Context.settingsDataStore by preferencesDataStore("aurora_settings")

data class AppSettings(
    val activePlaylistId: Long? = null,
    val autostart: Boolean = false,
    val backgroundPlay: Boolean = true,
    val externalPlayerPackage: String? = null,
    val bufferSeconds: Int = 20,
    val subtitleScale: Float = 1.0f,
    val language: String = "system",
    val kidsMode: Boolean = false,
    val parentalLock: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null
) {
    val hasPin: Boolean get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val activePlaylistId = longPreferencesKey("active_playlist_id")
        val autostart = booleanPreferencesKey("autostart")
        val backgroundPlay = booleanPreferencesKey("background_play")
        val externalPlayer = stringPreferencesKey("external_player")
        val bufferSeconds = intPreferencesKey("buffer_seconds")
        val subtitleScale = floatPreferencesKey("subtitle_scale")
        val language = stringPreferencesKey("language")
        val kidsMode = booleanPreferencesKey("kids_mode")
        val parentalLock = booleanPreferencesKey("parental_lock")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                activePlaylistId = prefs[Keys.activePlaylistId],
                autostart = prefs[Keys.autostart] ?: false,
                backgroundPlay = prefs[Keys.backgroundPlay] ?: true,
                externalPlayerPackage = prefs[Keys.externalPlayer],
                bufferSeconds = prefs[Keys.bufferSeconds] ?: 20,
                subtitleScale = prefs[Keys.subtitleScale] ?: 1.0f,
                language = prefs[Keys.language] ?: "system",
                kidsMode = prefs[Keys.kidsMode] ?: false,
                parentalLock = prefs[Keys.parentalLock] ?: false,
                pinHash = prefs[Keys.pinHash],
                pinSalt = prefs[Keys.pinSalt]
            )
        }

    suspend fun snapshot(): AppSettings = settings.first()

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    suspend fun setActivePlaylist(id: Long?) = update { prefs ->
        if (id == null) prefs.remove(Keys.activePlaylistId) else prefs[Keys.activePlaylistId] = id
    }

    suspend fun setAutostart(enabled: Boolean) = update { it[Keys.autostart] = enabled }

    suspend fun setBackgroundPlay(enabled: Boolean) = update { it[Keys.backgroundPlay] = enabled }

    suspend fun setExternalPlayer(packageName: String?) = update { prefs ->
        if (packageName.isNullOrBlank()) prefs.remove(Keys.externalPlayer)
        else prefs[Keys.externalPlayer] = packageName
    }

    suspend fun setBufferSeconds(seconds: Int) =
        update { it[Keys.bufferSeconds] = seconds.coerceIn(5, 120) }

    suspend fun setSubtitleScale(scale: Float) =
        update { it[Keys.subtitleScale] = scale.coerceIn(0.7f, 2.0f) }

    suspend fun setLanguage(code: String) = update { it[Keys.language] = code }

    suspend fun setKidsMode(enabled: Boolean) = update { it[Keys.kidsMode] = enabled }

    suspend fun setParentalLock(enabled: Boolean) = update { it[Keys.parentalLock] = enabled }

    suspend fun setPin(pin: String) {
        val salt = PinManager.newSalt()
        val hash = PinManager.hash(pin, salt)
        update { prefs ->
            prefs[Keys.pinSalt] = salt
            prefs[Keys.pinHash] = hash
        }
    }

    suspend fun clearPin() = update { prefs ->
        prefs.remove(Keys.pinSalt)
        prefs.remove(Keys.pinHash)
        prefs[Keys.parentalLock] = false
    }

    suspend fun verifyPin(pin: String): Boolean {
        val current = snapshot()
        return PinManager.verify(pin, current.pinSalt, current.pinHash)
    }

    fun lastChannelFlow(playlistId: Long): Flow<String?> =
        context.settingsDataStore.data.map { it[stringPreferencesKey("last_channel_$playlistId")] }

    suspend fun setLastChannel(playlistId: Long, uid: String) = update { prefs ->
        prefs[stringPreferencesKey("last_channel_$playlistId")] = uid
    }
}

/** Locale override is stored in SharedPreferences because it is read from attachBaseContext. */
object LocaleManager {
    private const val PREFS = "aurora_locale"
    private const val KEY = "locale"

    fun currentCode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "system") ?: "system"

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, code).apply()
    }

    fun wrap(base: Context): Context {
        val code = currentCode(base)
        if (code == "system" || code.isBlank()) return base
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
