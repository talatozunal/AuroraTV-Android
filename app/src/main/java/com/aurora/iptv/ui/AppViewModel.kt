package com.aurora.iptv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.iptv.AppContainer
import com.aurora.iptv.R
import com.aurora.iptv.data.db.CategoryRow
import com.aurora.iptv.data.db.PlaylistEntity
import com.aurora.iptv.data.db.ProgressEntity
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.data.repo.PlaylistDraft
import com.aurora.iptv.data.repo.SearchOutcome
import com.aurora.iptv.data.repo.SyncPhase
import com.aurora.iptv.data.settings.AppSettings
import com.aurora.iptv.data.source.XtreamAuthException
import com.aurora.iptv.net.HttpException
import com.aurora.iptv.security.ContentFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SyncUiState(
    val running: Boolean = false,
    val phase: SyncPhase? = null,
    val count: Int = 0,
    val error: String? = null,
    val detail: String? = null,
    val done: Boolean = false,
    val messageRes: Int? = null
)

data class SearchUiState(
    val query: String = "",
    val outcome: SearchOutcome? = null,
    val loading: Boolean = false
)

data class VodDetailUiState(
    val stream: StreamEntity? = null,
    val loading: Boolean = false,
    val episodes: List<StreamEntity> = emptyList(),
    val error: String? = null
)

data class GuideUiState(
    val loading: Boolean = false,
    val channels: List<StreamEntity> = emptyList(),
    val programs: Map<String, List<com.aurora.iptv.data.db.EpgEntity>> = emptyMap(),
    val windowStart: Long = 0L,
    val windowEnd: Long = 0L,
    val error: String? = null
)

private data class LiveQuery(
    val playlistId: Long?,
    val category: String?,
    val favorites: Boolean,
    val kids: Boolean
)

private data class VodQuery(
    val playlistId: Long?,
    val kind: StreamKind,
    val category: String?,
    val kids: Boolean
)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val db = container.db
    private val settingsStore = container.settings
    private val contentRepository = container.contentRepository
    private val playlistRepository = container.playlistRepository

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val playlists: StateFlow<List<PlaylistEntity>> = db.playlistDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val activeId = MutableStateFlow<Long?>(null)

    val activePlaylist: StateFlow<PlaylistEntity?> = combine(playlists, activeId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ready = MutableStateFlow(false)

    val selectedLiveCategory = MutableStateFlow<String?>(null)
    val showFavoriteLive = MutableStateFlow(false)
    val selectedVodCategory = MutableStateFlow<String?>(null)
    val vodKind = MutableStateFlow(StreamKind.MOVIE)

    val liveCategories: StateFlow<List<CategoryRow>> = combine(
        activePlaylist,
        settings
    ) { playlist, current -> playlist to current.kidsMode }
        .flatMapLatest { (playlist, kids) ->
            if (playlist == null) {
                flowOf(emptyList())
            } else {
                db.streamDao().observeCategories(playlist.id, StreamKind.LIVE).map { rows ->
                    if (kids) rows.filter { !ContentFilter.isAdultText(it.category) } else rows
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val liveStreams: StateFlow<List<StreamEntity>> = combine(
        activePlaylist,
        selectedLiveCategory,
        showFavoriteLive,
        settings
    ) { playlist, category, favorites, current ->
        LiveQuery(playlist?.id, category, favorites, current.kidsMode)
    }.flatMapLatest { query ->
        val playlistId = query.playlistId
        if (playlistId == null) {
            flowOf(emptyList())
        } else {
            val source = if (query.favorites) {
                db.streamDao().observeFavoriteLive(playlistId)
            } else {
                db.streamDao().observeByCategory(playlistId, StreamKind.LIVE, query.category)
            }
            source.map { list ->
                if (query.kids) list.filter { !ContentFilter.isAdult(it) } else list
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allLive: StateFlow<List<StreamEntity>> = combine(
        activePlaylist,
        settings
    ) { playlist, current -> playlist?.id to current.kidsMode }
        .flatMapLatest { (playlistId, kids) ->
            if (playlistId == null) {
                flowOf(emptyList())
            } else {
                db.streamDao().observeAll(playlistId, StreamKind.LIVE).map { list ->
                    if (kids) list.filter { !ContentFilter.isAdult(it) } else list
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _nowNext = MutableStateFlow<Map<String, com.aurora.iptv.data.repo.NowNext>>(emptyMap())
    val nowNext: StateFlow<Map<String, com.aurora.iptv.data.repo.NowNext>> = _nowNext.asStateFlow()
    private var nowNextJob: Job? = null

    val vodCategories: StateFlow<List<CategoryRow>> = combine(
        activePlaylist,
        vodKind
    ) { playlist, kind -> playlist to kind }
        .flatMapLatest { (playlist, kind) ->
            if (playlist == null) {
                flowOf(emptyList())
            } else {
                db.streamDao().observeCategories(playlist.id, kind)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vodStreams: StateFlow<List<StreamEntity>> = combine(
        activePlaylist,
        vodKind,
        selectedVodCategory,
        settings
    ) { playlist, kind, category, current ->
        VodQuery(playlist?.id, kind, category, current.kidsMode)
    }.flatMapLatest { query ->
        val playlistId = query.playlistId
        if (playlistId == null) {
            flowOf(emptyList())
        } else {
            db.streamDao().observeByCategory(playlistId, query.kind, query.category).map { list ->
                if (query.kids) list.filter { !ContentFilter.isAdult(it) } else list
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()
    private var searchJob: Job? = null

    private val _vodDetail = MutableStateFlow<VodDetailUiState?>(null)
    val vodDetail: StateFlow<VodDetailUiState?> = _vodDetail.asStateFlow()
    private var vodDetailJob: Job? = null

    private val _guide = MutableStateFlow(GuideUiState())
    val guide: StateFlow<GuideUiState> = _guide.asStateFlow()
    private var guideJob: Job? = null

    private val _sync = MutableStateFlow(SyncUiState())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()

    init {
        viewModelScope.launch {
            combine(playlists, settingsStore.settings) { list, current -> list to current }
                .collect { (list, current) ->
                    val desired = current.activePlaylistId?.takeIf { id ->
                        list.any { it.id == id }
                    } ?: list.firstOrNull()?.id
                    if (desired != current.activePlaylistId) {
                        settingsStore.setActivePlaylist(desired)
                    }
                    activeId.value = desired
                    ready.value = true
                }
        }
        viewModelScope.launch {
            activePlaylist.map { it?.id }.distinctUntilChanged().collect {
                selectedLiveCategory.value = null
                selectedVodCategory.value = null
                showFavoriteLive.value = false
                closeVodDetail()
            }
        }
    }

    fun setPlaylist(id: Long) {
        viewModelScope.launch { settingsStore.setActivePlaylist(id) }
    }

    fun refreshNowNext(channels: List<StreamEntity>) {
        nowNextJob?.cancel()
        val playlistId = activePlaylist.value?.id
        if (playlistId == null || channels.isEmpty()) {
            _nowNext.value = emptyMap()
            return
        }
        nowNextJob = viewModelScope.launch {
            val result = runCatching {
                contentRepository.nowNext(playlistId, channels)
            }.getOrDefault(emptyMap())
            if (activePlaylist.value?.id == playlistId) _nowNext.value = result
        }
    }

    fun selectLiveCategory(category: String?) {
        selectedLiveCategory.value = category
        showFavoriteLive.value = false
    }

    fun toggleFavoriteLive() {
        showFavoriteLive.value = !showFavoriteLive.value
        if (showFavoriteLive.value) selectedLiveCategory.value = null
    }

    fun selectVodKind(kind: StreamKind) {
        if (kind == vodKind.value) return
        vodKind.value = kind
        selectedVodCategory.value = null
    }

    fun selectVodCategory(category: String?) {
        selectedVodCategory.value = category
    }

    fun setSearchQuery(text: String) {
        _search.update { it.copy(query = text, loading = text.isNotBlank()) }
        searchJob?.cancel()
        if (text.isBlank()) {
            _search.update { it.copy(outcome = null, loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            runSearch(text)
        }
    }

    /** Used for the remote's search button / ACTION_SEARCH so the query is applied immediately. */
    fun setSearchQueryImmediate(text: String) {
        _search.update { it.copy(query = text, loading = text.isNotBlank(), outcome = null) }
        searchJob?.cancel()
        if (text.isBlank()) {
            _search.update { it.copy(loading = false) }
            return
        }
        searchJob = viewModelScope.launch { runSearch(text) }
    }

    private suspend fun runSearch(text: String) {
        val playlistId = activePlaylist.value?.id
        if (playlistId == null) {
            _search.update { it.copy(loading = false, outcome = null) }
            return
        }
        val outcome = runCatching {
            contentRepository.search(playlistId, text)
        }.getOrNull()
        val filtered = if (outcome != null && settings.value.kidsMode) {
            outcome.copy(
                live = outcome.live.filter { !ContentFilter.isAdult(it) },
                vod = outcome.vod.filter { !ContentFilter.isAdult(it) }
            )
        } else {
            outcome
        }
        _search.update { it.copy(outcome = filtered, loading = false) }
    }

    fun openVodDetail(stream: StreamEntity) {
        vodDetailJob?.cancel()
        _vodDetail.value = VodDetailUiState(stream = stream, loading = true)
        vodDetailJob = viewModelScope.launch {
            try {
                when (stream.kind) {
                    StreamKind.SERIES -> {
                        val episodes = playlistRepository.loadSeriesEpisodes(stream.uid)
                        val fresh = contentRepository.stream(stream.uid) ?: stream
                        _vodDetail.value = VodDetailUiState(
                            stream = fresh,
                            loading = false,
                            episodes = episodes
                        )
                    }

                    StreamKind.MOVIE -> {
                        val detailed = playlistRepository.loadMovieDetails(stream.uid) ?: stream
                        _vodDetail.value = VodDetailUiState(stream = detailed, loading = false)
                    }

                    else -> {
                        _vodDetail.value = VodDetailUiState(stream = stream, loading = false)
                    }
                }
            } catch (error: Throwable) {
                val fallbackEpisodes = if (stream.kind == StreamKind.SERIES) {
                    stream.seriesId?.let { seriesId ->
                        contentRepository.seriesEpisodes(stream.playlistId, seriesId)
                    }.orEmpty()
                } else {
                    emptyList()
                }
                _vodDetail.value = VodDetailUiState(
                    stream = contentRepository.stream(stream.uid) ?: stream,
                    loading = false,
                    episodes = fallbackEpisodes,
                    error = humanError(error)
                )
            }
        }
    }

    fun closeVodDetail() {
        vodDetailJob?.cancel()
        _vodDetail.value = null
    }

    fun loadGuide(windowStart: Long, windowEnd: Long, category: String?) {
        guideJob?.cancel()
        val playlist = activePlaylist.value ?: return
        _guide.value = _guide.value.copy(
            loading = true,
            windowStart = windowStart,
            windowEnd = windowEnd,
            error = null
        )
        guideJob = viewModelScope.launch {
            val channels = if (category == null) {
                db.streamDao().getLive(playlist.id)
            } else {
                db.streamDao().getLiveByCategory(playlist.id, category)
            }
            val visible = channels.filter {
                !settings.value.kidsMode || !ContentFilter.isAdult(it)
            }
            val programs = runCatching {
                contentRepository.guide(
                    playlistId = playlist.id,
                    channels = visible,
                    from = windowStart - TimeUnit.HOURS.toMillis(2),
                    to = windowEnd
                )
            }.getOrDefault(emptyMap())
            _guide.value = GuideUiState(
                loading = false,
                channels = visible,
                programs = programs,
                windowStart = windowStart,
                windowEnd = windowEnd
            )
        }
    }

    fun addPlaylist(draft: PlaylistDraft) {
        if (_sync.value.running) return
        _sync.value = SyncUiState(running = true, phase = SyncPhase.CONTENT)
        viewModelScope.launch {
            try {
                playlistRepository.addAndSync(draft) { progress ->
                    _sync.update {
                        it.copy(phase = progress.phase, count = progress.count)
                    }
                }
                _sync.value = SyncUiState(done = true)
            } catch (error: Throwable) {
                _sync.value = SyncUiState(
                    error = humanError(error),
                    detail = error.localizedMessage
                )
            }
        }
    }

    fun refreshPlaylist(playlistId: Long) {
        if (_sync.value.running) return
        _sync.value = SyncUiState(running = true, phase = SyncPhase.CONTENT)
        viewModelScope.launch {
            try {
                playlistRepository.syncAll(playlistId) { progress ->
                    _sync.update {
                        it.copy(phase = progress.phase, count = progress.count)
                    }
                }
                _sync.value = SyncUiState(done = true)
            } catch (error: Throwable) {
                _sync.value = SyncUiState(error = humanError(error), detail = error.localizedMessage)
            }
        }
    }

    fun refreshEpg(playlistId: Long) {
        if (_sync.value.running) return
        _sync.value = SyncUiState(running = true, phase = SyncPhase.EPG)
        viewModelScope.launch {
            try {
                playlistRepository.refreshEpg(playlistId) { progress ->
                    _sync.update {
                        it.copy(phase = progress.phase, count = progress.count)
                    }
                }
                _sync.value = SyncUiState(done = true)
            } catch (error: Throwable) {
                _sync.value = SyncUiState(
                    error = container.appContext.getString(R.string.epg_failed),
                    detail = humanError(error)
                )
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            runCatching {
                playlistRepository.delete(playlistId)
                if (settings.value.activePlaylistId == playlistId) {
                    settingsStore.setActivePlaylist(null)
                }
            }
        }
    }

    fun clearEpgCache() {
        viewModelScope.launch {
            runCatching { playlistRepository.clearEpgCache() }
            _sync.value = SyncUiState(
                done = true,
                messageRes = R.string.clear_done
            )
        }
    }

    fun dismissSync() {
        _sync.value = SyncUiState()
    }

    suspend fun verifyPin(pin: String): Boolean = settingsStore.verifyPin(pin)

    suspend fun progressFor(uid: String): ProgressEntity? =
        contentRepository.progress(uid)

    fun setPin(pin: String) {
        viewModelScope.launch { settingsStore.setPin(pin) }
    }

    fun clearPin() {
        viewModelScope.launch { settingsStore.clearPin() }
    }

    fun setKidsMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setKidsMode(enabled) }
    }

    fun setParentalLock(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setParentalLock(enabled) }
    }

    fun setAutostart(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutostart(enabled) }
    }

    fun setBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBackgroundPlay(enabled) }
    }

    fun setExternalPlayer(packageName: String?) {
        viewModelScope.launch { settingsStore.setExternalPlayer(packageName) }
    }

    fun setBufferSeconds(seconds: Int) {
        viewModelScope.launch { settingsStore.setBufferSeconds(seconds) }
    }

    fun setSubtitleScale(scale: Float) {
        viewModelScope.launch { settingsStore.setSubtitleScale(scale) }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { settingsStore.setLanguage(code) }
    }

    fun toggleFavorite(stream: StreamEntity) {
        val newValue = !stream.favorite
        viewModelScope.launch {
            contentRepository.setFavorite(stream.uid, newValue)
        }
    }

    fun rename(uid: String, name: String?) {
        viewModelScope.launch { contentRepository.rename(uid, name) }
    }

    private fun humanError(error: Throwable): String = when (error) {
        is HttpException -> container.appContext.getString(R.string.error_http, error.code)
        is XtreamAuthException -> container.appContext.getString(R.string.error_xtream_auth)
        is IOException -> container.appContext.getString(
            R.string.error_network,
            error.localizedMessage ?: error.javaClass.simpleName
        )

        else -> container.appContext.getString(
            R.string.error_unknown
        ) + ": " + (error.localizedMessage ?: error.javaClass.simpleName)
    }
}
