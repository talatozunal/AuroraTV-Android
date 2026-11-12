package com.aurora.iptv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import com.aurora.iptv.AppContainer
import com.aurora.iptv.R
import com.aurora.iptv.data.db.EpgEntity
import com.aurora.iptv.data.db.PlaylistEntity
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.data.repo.NowNext
import com.aurora.iptv.data.settings.AppSettings
import com.aurora.iptv.playback.PlaybackRequest
import com.aurora.iptv.security.ContentFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val stream: StreamEntity? = null,
    val playlist: PlaylistEntity? = null,
    val nowNext: NowNext = NowNext(null, null),
    val archive: List<EpgEntity> = emptyList(),
    val catchingUp: Boolean = false,
    val catchupTitle: String? = null,
    val catchupStartMs: Long = 0L,
    val catchupEndMs: Long = 0L,
    val initialPositionMs: Long = 0L,
    val message: String? = null,
    val error: String? = null
)

class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    private val db = container.db
    private val playlistRepository = container.playlistRepository
    private val contentRepository = container.contentRepository
    private val settingsStore = container.settings

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val _zapping = MutableStateFlow<List<StreamEntity>>(emptyList())
    val zapping: StateFlow<List<StreamEntity>> = _zapping.asStateFlow()

    private var index = 0
    private var startedKey: String? = null
    private var fallbackTried = false
    private var openJob: Job? = null
    private var infoJob: Job? = null
    private var playbackJob: Job? = null

    fun open() {
        if (_ui.value.stream != null) return
        val requestUid = PlaybackRequest.uid
        val requestPosition = PlaybackRequest.startPositionMs
        val requestCatchupStart = PlaybackRequest.catchupStartMs
        val requestCatchupEnd = PlaybackRequest.catchupEndMs
        val requestCatchupTitle = PlaybackRequest.catchupTitle
        openJob?.cancel()
        openJob = viewModelScope.launch {
            if (requestUid == null) {
                _ui.value = PlayerUiState(
                    error = container.appContext.getString(R.string.stream_not_found)
                )
                return@launch
            }
            val stream = db.streamDao().getByUid(requestUid)
            if (stream == null) {
                _ui.value = PlayerUiState(
                    error = container.appContext.getString(R.string.stream_not_found)
                )
                return@launch
            }
            val playlist = db.playlistDao().get(stream.playlistId)
            val kids = settings.value.kidsMode
            val parental = settings.value.parentalLock
            val liveList = if (stream.kind == StreamKind.LIVE) {
                db.streamDao().getLive(stream.playlistId)
                    .filter { !(kids || parental) || !ContentFilter.isAdult(it) }
            } else {
                emptyList()
            }
            _zapping.value = liveList
            index = liveList.indexOfFirst { it.uid == stream.uid }.coerceAtLeast(0)
            _ui.value = PlayerUiState(
                stream = stream,
                playlist = playlist,
                catchingUp = requestCatchupStart != null,
                catchupTitle = requestCatchupTitle,
                catchupStartMs = requestCatchupStart ?: 0L,
                catchupEndMs = requestCatchupEnd ?: 0L,
                initialPositionMs = requestPosition
            )
            if (stream.kind == StreamKind.LIVE) {
                settingsStore.setLastChannel(stream.playlistId, stream.uid)
            }
            loadInfo(stream)
        }
    }

    private fun loadInfo(stream: StreamEntity) {
        infoJob?.cancel()
        infoJob = viewModelScope.launch {
            val playlistId = stream.playlistId
            val nowNext = runCatching {
                contentRepository.nowNext(playlistId, listOf(stream))[stream.epgId.orEmpty()]
            }.getOrNull() ?: NowNext(null, null)
            val archive = if (stream.kind == StreamKind.LIVE &&
                playlistRepository.supportsCatchup(stream)
            ) {
                runCatching {
                    contentRepository.archive(
                        playlistId = playlistId,
                        channel = stream,
                        days = stream.catchupDays.coerceIn(1, 7)
                    )
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _ui.update { current ->
                if (current.stream?.uid == stream.uid) {
                    current.copy(nowNext = nowNext, archive = archive)
                } else {
                    current
                }
            }
        }
    }

    fun startPlayback(controller: MediaController) {
        val state = _ui.value
        val stream = state.stream ?: return
        val playlist = state.playlist ?: return
        val key = "${stream.uid}|${state.catchingUp}|${state.catchupStartMs}|${state.catchupEndMs}"
        if (startedKey == key && controller.currentMediaItem != null) return
        playbackJob?.cancel()
        startedKey = key
        fallbackTried = false

        playbackJob = viewModelScope.launch {
            try {
                val url = if (state.catchingUp) {
                    playlistRepository.catchupUrl(
                        streamUid = stream.uid,
                        startMs = state.catchupStartMs,
                        endMs = state.catchupEndMs
                    )
                } else {
                    stream.streamUrl
                }
                if (url.isNullOrBlank()) {
                    _ui.update {
                        it.copy(
                            error = container.appContext.getString(
                                if (state.catchingUp) R.string.catchup_not_supported
                                else R.string.stream_not_found
                            )
                        )
                    }
                    return@launch
                }

                val resume = if (!state.catchingUp &&
                    (stream.kind == StreamKind.MOVIE || stream.kind == StreamKind.EPISODE)
                ) {
                    val saved = contentRepository.progress(stream.uid)
                    if (saved != null &&
                        saved.position > 10_000L &&
                        (saved.duration <= 0L || saved.duration - saved.position > 10_000L)
                    ) {
                        saved.position
                    } else {
                        state.initialPositionMs
                    }
                } else {
                    0L
                }

                val item = container.playerFactory.mediaItem(stream, url)
                withContext(Dispatchers.Main) {
                    val current = _ui.value
                    if (current.stream?.uid != stream.uid ||
                        current.catchingUp != state.catchingUp ||
                        current.catchupStartMs != state.catchupStartMs
                    ) {
                        return@withContext
                    }
                    controller.setMediaItem(item, resume)
                    controller.prepare()
                    controller.playWhenReady = true
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                _ui.update {
                    it.copy(
                        error = error.localizedMessage
                            ?: container.appContext.getString(R.string.playback_error)
                    )
                }
            }
        }
    }

    fun channelAt(delta: Int): StreamEntity? {
        val list = _zapping.value
        if (list.isEmpty()) return null
        val size = list.size
        val target = ((index + delta) % size + size) % size
        return list.getOrNull(target)
    }

    fun selectChannelNumber(number: Int): StreamEntity? {
        val list = _zapping.value
        if (list.isEmpty()) return null
        val byNumber = list.firstOrNull { it.channelNo == number }
        return byNumber ?: list.getOrNull(number - 1)
    }

    fun selectChannel(stream: StreamEntity) {
        val idx = _zapping.value.indexOfFirst { it.uid == stream.uid }
        if (idx >= 0) index = idx
        playbackJob?.cancel()
        startedKey = null
        fallbackTried = false
        _ui.update { current ->
            current.copy(
                stream = stream,
                nowNext = NowNext(null, null),
                archive = emptyList(),
                catchingUp = false,
                catchupTitle = null,
                catchupStartMs = 0L,
                catchupEndMs = 0L,
                initialPositionMs = 0L,
                message = null,
                error = null
            )
        }
        if (stream.kind == StreamKind.LIVE) {
            viewModelScope.launch {
                settingsStore.setLastChannel(stream.playlistId, stream.uid)
            }
        }
        loadInfo(stream)
    }

    fun playCatchup(program: EpgEntity) {
        playbackJob?.cancel()
        startedKey = null
        fallbackTried = false
        _ui.update {
            it.copy(
                catchingUp = true,
                catchupTitle = program.title,
                catchupStartMs = program.startTime,
                catchupEndMs = program.endTime,
                error = null,
                message = null
            )
        }
    }

    fun stopCatchup() {
        playbackJob?.cancel()
        startedKey = null
        fallbackTried = false
        _ui.update {
            it.copy(
                catchingUp = false,
                catchupTitle = null,
                catchupStartMs = 0L,
                catchupEndMs = 0L,
                error = null
            )
        }
    }

    fun toggleFavorite() {
        val stream = _ui.value.stream ?: return
        val newValue = !stream.favorite
        viewModelScope.launch {
            contentRepository.setFavorite(stream.uid, newValue)
            val updated = stream.copy(favorite = newValue)
            _ui.update { current ->
                if (current.stream?.uid == stream.uid) current.copy(stream = updated) else current
            }
            _zapping.update { list ->
                list.map { if (it.uid == stream.uid) it.copy(favorite = newValue) else it }
            }
        }
    }

    fun rename(uid: String, name: String?) {
        val cleaned = name?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            contentRepository.rename(uid, cleaned)
            _ui.update { current ->
                if (current.stream?.uid == uid) {
                    current.copy(stream = current.stream?.copy(customName = cleaned))
                } else {
                    current
                }
            }
            _zapping.update { list ->
                list.map { if (it.uid == uid) it.copy(customName = cleaned) else it }
            }
        }
    }

    fun onPlaybackError(controller: MediaController, error: PlaybackException) {
        val state = _ui.value
        val stream = state.stream ?: return
        val fallback = stream.fallbackUrl
        if (!fallbackTried &&
            !state.catchingUp &&
            !fallback.isNullOrBlank() &&
            fallback != stream.streamUrl
        ) {
            fallbackTried = true
            playbackJob?.cancel()
            startedKey = null
            playbackJob = viewModelScope.launch {
                try {
                    val item = container.playerFactory.mediaItem(stream, fallback)
                    withContext(Dispatchers.Main) {
                        val current = _ui.value
                        if (current.stream?.uid != stream.uid || current.catchingUp) {
                            return@withContext
                        }
                        controller.setMediaItem(item)
                        controller.prepare()
                        controller.playWhenReady = true
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Throwable) {
                    _ui.update { current ->
                        current.copy(
                            error = error.errorCodeName + ": " +
                                (failure.localizedMessage ?: "")
                        )
                    }
                }
            }
        } else {
            _ui.update {
                it.copy(
                    error = error.errorCodeName + ": " + (error.localizedMessage ?: "")
                )
            }
        }
    }

    fun retry(controller: MediaController) {
        startedKey = null
        fallbackTried = false
        _ui.update { it.copy(error = null) }
        startPlayback(controller)
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    suspend fun resolveUrl(): String? {
        val state = _ui.value
        val stream = state.stream ?: return null
        return if (state.catchingUp) {
            playlistRepository.catchupUrl(stream.uid, state.catchupStartMs, state.catchupEndMs)
        } else {
            stream.streamUrl.takeIf { it.isNotBlank() }
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val state = _ui.value
        if (state.catchingUp) return
        val stream = state.stream ?: return
        if (stream.kind != StreamKind.MOVIE && stream.kind != StreamKind.EPISODE) return
        viewModelScope.launch {
            contentRepository.saveProgress(stream.uid, positionMs, durationMs)
        }
    }
}
