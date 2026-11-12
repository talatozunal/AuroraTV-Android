package com.aurora.iptv.ui.screens

import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.aurora.iptv.AppContainer
import com.aurora.iptv.R
import com.aurora.iptv.data.db.EpgEntity
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.playback.ExternalPlayers
import com.aurora.iptv.playback.TrackOption
import com.aurora.iptv.playback.audioTrackOptions
import com.aurora.iptv.playback.disableTextTrack
import com.aurora.iptv.playback.selectAudioTrack
import com.aurora.iptv.playback.selectTextTrack
import com.aurora.iptv.playback.textTrackOptions
import com.aurora.iptv.ui.AuroraViewModelFactory
import com.aurora.iptv.ui.PlayerViewModel
import com.aurora.iptv.ui.components.TvBadge
import com.aurora.iptv.ui.components.TvButton
import com.aurora.iptv.ui.components.TvCard
import com.aurora.iptv.ui.components.TvDialog
import com.aurora.iptv.ui.components.TvIconButton
import com.aurora.iptv.ui.components.TvProgress
import com.aurora.iptv.ui.components.TvTextField
import com.aurora.iptv.ui.components.autoFocusOnce
import com.aurora.iptv.ui.theme.AuroraColors
import com.aurora.iptv.ui.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun PlayerScreen(
    container: AppContainer,
    controller: MediaController?,
    inPip: Boolean,
    onBack: () -> Unit,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val factory = remember(container) { AuroraViewModelFactory(container) }
    val playerViewModel: PlayerViewModel = viewModel(factory = factory)

    LaunchedEffect(Unit) { playerViewModel.open() }

    val ui by playerViewModel.ui.collectAsStateWithLifecycle()
    val settings by playerViewModel.settings.collectAsStateWithLifecycle()
    val stream = ui.stream
    val isLive = stream?.kind == StreamKind.LIVE && !ui.catchingUp
    val canSeek = stream != null && !isLive
    val displayName = ui.catchupTitle
        ?: stream?.customName?.takeIf { it.isNotBlank() }
        ?: stream?.name
        ?: stringResource(R.string.loading)
    val backgroundPlay by rememberUpdatedState(settings.backgroundPlay)
    val subtitleScale by rememberUpdatedState(settings.subtitleScale)
    val pipActive by rememberUpdatedState(inPip)

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var showTracks by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var numberBuffer by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf<String?>(null) }
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }

    val supportsCatchup = stream != null && (
        stream.catchupType != null ||
            stream.catchupDays > 0 ||
            !stream.catchupSource.isNullOrBlank()
        )

    fun showBanner(text: String) {
        banner = text
    }

    fun revealControls(focusPlayer: Boolean = false) {
        controlsVisible = true
        interaction++
        if (focusPlayer) {
            scope.launch {
                delay(60)
                runCatching { playPauseFocus.requestFocus() }
            }
        }
    }

    fun togglePlay() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
            playerViewModel.saveProgress(player.currentPosition, player.duration)
            controlsVisible = true
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        if (!canSeek) {
            showBanner(context.getString(R.string.seek_not_supported))
            return
        }
        val duration = player.duration
        if (duration <= 0L || duration == C.TIME_UNSET) return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        positionMs = target
    }

    fun zap(delta: Int) {
        val target = playerViewModel.channelAt(delta) ?: return
        playerViewModel.selectChannel(target)
        val number = target.channelNo?.let { "$it · " }.orEmpty()
        showBanner(number + (target.customName?.takeIf { it.isNotBlank() } ?: target.name))
    }

    fun commitNumber() {
        if (numberBuffer.isBlank()) return
        val number = numberBuffer.toIntOrNull()
        numberBuffer = ""
        if (number == null) return
        val target = playerViewModel.selectChannelNumber(number)
        if (target == null) {
            showBanner(context.getString(R.string.invalid_channel))
        } else {
            playerViewModel.selectChannel(target)
            showBanner(
                context.getString(R.string.channel_number, number) + " · " +
                    (target.customName?.takeIf { it.isNotBlank() } ?: target.name)
            )
        }
    }

    LaunchedEffect(controller, ui.stream?.uid, ui.catchingUp, ui.catchupStartMs, ui.catchupEndMs) {
        if (controller != null && ui.stream != null && ui.playlist != null) {
            playerViewModel.startPlayback(controller)
        }
    }

    DisposableEffect(controller, ui.stream?.uid) {
        if (controller == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    playing = player.isPlaying
                    buffering = player.playbackState == Player.STATE_BUFFERING
                    ended = player.playbackState == Player.STATE_ENDED
                    durationMs = player.duration
                    positionMs = player.currentPosition
                    if (ended) controlsVisible = true
                }

                override fun onTracksChanged(tracks: Tracks) {
                    tracksVersion++
                }

                override fun onPlayerError(error: PlaybackException) {
                    playerViewModel.onPlaybackError(controller, error)
                }
            }
            controller.addListener(listener)
            onDispose {
                controller.removeListener(listener)
                playerViewModel.saveProgress(
                    controller.currentPosition,
                    controller.duration
                )
            }
        }
    }

    LaunchedEffect(controller) {
        while (isActive) {
            val player = controller ?: break
            positionMs = player.currentPosition
            durationMs = player.duration
            delay(400)
        }
    }

    LaunchedEffect(controller, ui.stream?.uid) {
        if (controller == null) return@LaunchedEffect
        while (isActive) {
            delay(15_000)
            playerViewModel.saveProgress(controller.currentPosition, controller.duration)
        }
    }

    LaunchedEffect(controller, ui.stream?.uid) {
        delay(550)
        runCatching { playPauseFocus.requestFocus() }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            clockNow = System.currentTimeMillis()
            delay(15_000)
        }
    }

    LaunchedEffect(controlsVisible, interaction, playing, showTracks, showArchive, showInfo) {
        if (!controlsVisible || !playing || showTracks || showArchive || showInfo) {
            return@LaunchedEffect
        }
        delay(6_000)
        controlsVisible = false
        focusManager.clearFocus()
        runCatching { rootFocus.requestFocus() }
    }

    LaunchedEffect(banner) {
        if (banner != null) {
            delay(2_600)
            banner = null
        }
    }

    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isNotEmpty()) {
            delay(1_700)
            commitNumber()
        }
    }

    LaunchedEffect(inPip) {
        if (inPip) controlsVisible = false
    }

    // Leaving the player (or sending the app to the background) must not keep
    // audio running when background playback is disabled.
    DisposableEffect(controller) {
        onDispose {
            if (!backgroundPlay) {
                controller?.let {
                    it.pause()
                    playerViewModel.saveProgress(it.currentPosition, it.duration)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !backgroundPlay && !pipActive) {
                controller?.let {
                    it.pause()
                    playerViewModel.saveProgress(it.currentPosition, it.duration)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = !showTracks && !showArchive && !showInfo) {
        if (controlsVisible) {
            controlsVisible = false
            focusManager.clearFocus()
            runCatching { rootFocus.requestFocus() }
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interaction++
                val code = event.nativeKeyEvent.keyCode
                if (code in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9) {
                    if (numberBuffer.length < 4) {
                        numberBuffer += (code - android.view.KeyEvent.KEYCODE_0).toString()
                    }
                    return@onPreviewKeyEvent true
                }
                if (numberBuffer.isNotEmpty() &&
                    (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == android.view.KeyEvent.KEYCODE_ENTER)
                ) {
                    commitNumber()
                    return@onPreviewKeyEvent true
                }
                when (code) {
                    android.view.KeyEvent.KEYCODE_DEL -> {
                        if (numberBuffer.isNotEmpty()) {
                            numberBuffer = numberBuffer.dropLast(1)
                            return@onPreviewKeyEvent true
                        }
                    }

                    android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                    android.view.KeyEvent.KEYCODE_PAGE_UP,
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        zap(1)
                        return@onPreviewKeyEvent true
                    }

                    android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
                    android.view.KeyEvent.KEYCODE_PAGE_DOWN,
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        zap(-1)
                        return@onPreviewKeyEvent true
                    }

                    android.view.KeyEvent.KEYCODE_MENU -> {
                        showTracks = true
                        revealControls()
                        return@onPreviewKeyEvent true
                    }

                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        togglePlay()
                        revealControls()
                        return@onPreviewKeyEvent true
                    }

                    android.view.KeyEvent.KEYCODE_INFO -> {
                        showInfo = true
                        revealControls()
                        return@onPreviewKeyEvent true
                    }
                }

                if (!controlsVisible) {
                    when (event.key) {
                        androidx.compose.ui.input.key.Key.DirectionCenter,
                        androidx.compose.ui.input.key.Key.DirectionUp,
                        androidx.compose.ui.input.key.Key.DirectionDown -> {
                            revealControls(focusPlayer = true)
                            return@onPreviewKeyEvent true
                        }

                        androidx.compose.ui.input.key.Key.DirectionLeft -> {
                            if (canSeek) seekBy(-10_000L)
                            revealControls(focusPlayer = true)
                            return@onPreviewKeyEvent true
                        }

                        androidx.compose.ui.input.key.Key.DirectionRight -> {
                            if (canSeek) seekBy(30_000L)
                            revealControls(focusPlayer = true)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
    ) {
        AndroidView(
            factory = { viewContext ->
                LayoutInflater.from(viewContext)
                    .inflate(R.layout.view_player, null) as PlayerView
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = controller
                view.subtitleView?.setStyle(
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_NONE,
                        android.graphics.Color.WHITE,
                        null
                    )
                )
                view.subtitleView?.setFractionalTextSize(0.0533f * subtitleScale)
            },
            onRelease = { view -> view.player = null }
        )

        if (!inPip) {
            if (buffering && ui.error == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.buffering),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            banner?.let { text ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 28.dp, top = 108.dp)
                        .background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }

            if (numberBuffer.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 136.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 26.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = numberBuffer,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.zapping_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.92f), Color.Transparent)
                            )
                        )
                        .padding(start = 22.dp, top = 18.dp, end = 22.dp, bottom = 28.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            onClick = onBack,
                            size = 48.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = when {
                                ui.catchingUp -> stringResource(R.string.catchup) + " · " +
                                    TimeFormat.range(ui.catchupStartMs, ui.catchupEndMs)

                                isLive -> {
                                    val now = ui.nowNext.now
                                    val next = ui.nowNext.next
                                    when {
                                        now != null && next != null ->
                                            "${TimeFormat.range(now.startTime, now.endTime)} · ${now.title}  ·  " +
                                                stringResource(R.string.next) + ": " + next.title

                                        now != null ->
                                            "${TimeFormat.range(now.startTime, now.endTime)} · ${now.title}"

                                        else -> stringResource(R.string.no_epg)
                                    }
                                }

                                else -> listOfNotNull(
                                    stream?.year?.takeIf { it.isNotBlank() },
                                    stream?.genre?.takeIf { it.isNotBlank() },
                                    stream?.category?.takeIf { it.isNotBlank() }
                                ).joinToString(" · ")
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.74f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvIconButton(
                                icon = Icons.Filled.Star,
                                contentDescription = stringResource(
                                    if (stream?.favorite == true) R.string.favorite_remove
                                    else R.string.favorite_add
                                ),
                                selected = stream?.favorite == true,
                                onClick = playerViewModel::toggleFavorite,
                                size = 46.dp
                            )
                            TvIconButton(
                                icon = Icons.Filled.Subtitles,
                                contentDescription = stringResource(R.string.player_tracks),
                                onClick = {
                                    showTracks = true
                                    revealControls()
                                },
                                size = 46.dp
                            )
                            if (isLive && supportsCatchup) {
                                TvIconButton(
                                    icon = Icons.Filled.History,
                                    contentDescription = stringResource(R.string.archive_title),
                                    onClick = {
                                        showArchive = true
                                        revealControls()
                                    },
                                    size = 46.dp
                                )
                            }
                            TvIconButton(
                                icon = Icons.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.external_play),
                                onClick = {
                                    revealControls()
                                    scope.launch {
                                        val url = playerViewModel.resolveUrl()
                                        if (url.isNullOrBlank()) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.stream_not_found),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else if (!ExternalPlayers.launch(
                                                context,
                                                url,
                                                settings.externalPlayerPackage
                                            )
                                        ) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.external_player_missing),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                size = 46.dp
                            )
                            TvIconButton(
                                icon = Icons.Filled.Info,
                                contentDescription = stringResource(R.string.info),
                                onClick = {
                                    showInfo = true
                                    revealControls()
                                },
                                size = 46.dp
                            )
                            TvIconButton(
                                icon = Icons.Filled.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.pip),
                                onClick = onEnterPip,
                                size = 46.dp
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f))
                            )
                        )
                        .padding(start = 28.dp, top = 46.dp, end = 28.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canSeek) {
                        val duration = durationMs.coerceAtLeast(0L)
                        val progress = if (duration > 0L) {
                            (positionMs.toFloat() / duration).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = TimeFormat.position(positionMs.coerceAtLeast(0L)),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.width(12.dp))
                            TvProgress(progress, Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (duration > 0L) TimeFormat.position(duration) else "--:--",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    } else if (isLive) {
                        val now = ui.nowNext.now
                        if (now != null) {
                            val progress = if (now.endTime > now.startTime) {
                                ((clockNow - now.startTime).toFloat() /
                                    (now.endTime - now.startTime).toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = TimeFormat.clock(now.startTime),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(12.dp))
                                TvProgress(progress, Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = TimeFormat.clock(now.endTime),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLive) {
                            TvIconButton(
                                icon = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.previous_channel),
                                onClick = { zap(-1) },
                                size = 50.dp
                            )
                        } else {
                            TvIconButton(
                                icon = Icons.Filled.Replay10,
                                contentDescription = stringResource(R.string.seek_back),
                                onClick = { seekBy(-10_000L) },
                                size = 50.dp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        TvIconButton(
                            icon = when {
                                ended -> Icons.Filled.Replay
                                playing -> Icons.Filled.Pause
                                else -> Icons.Filled.PlayArrow
                            },
                            contentDescription = stringResource(
                                if (playing) R.string.pause else R.string.play
                            ),
                            onClick = { togglePlay() },
                            focusRequester = playPauseFocus,
                            size = 64.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        if (isLive) {
                            TvIconButton(
                                icon = Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.next_channel),
                                onClick = { zap(1) },
                                size = 50.dp
                            )
                        } else {
                            TvIconButton(
                                icon = Icons.Filled.Forward30,
                                contentDescription = stringResource(R.string.seek_forward),
                                onClick = { seekBy(30_000L) },
                                size = 50.dp
                            )
                        }
                        if (canSeek) {
                            Spacer(Modifier.width(18.dp))
                            TvIconButton(
                                icon = Icons.Filled.History,
                                contentDescription = stringResource(R.string.start_over),
                                onClick = { controller?.seekTo(0) },
                                size = 46.dp
                            )
                        }
                        if (isLive && supportsCatchup) {
                            Spacer(Modifier.width(18.dp))
                            TvIconButton(
                                icon = Icons.Filled.History,
                                contentDescription = stringResource(R.string.archive_title),
                                onClick = {
                                    showArchive = true
                                    revealControls()
                                },
                                size = 46.dp
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.zapping_hint),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.56f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            ui.error?.let { error ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(560.dp)
                        .background(Color(0xF20C1422), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.playback_error),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(
                            text = stringResource(R.string.retry_play),
                            onClick = { controller?.let(playerViewModel::retry) },
                            modifier = Modifier.autoFocusOnce(),
                            enabled = controller != null
                        )
                        TvButton(
                            text = stringResource(R.string.back),
                            onClick = onBack,
                            primary = false
                        )
                    }
                }
            }
        }
    }

    if (!inPip && showTracks && controller != null) {
        TracksDialog(
            controller = controller,
            version = tracksVersion,
            onVersion = { tracksVersion++ },
            onDismiss = {
                showTracks = false
                revealControls()
            }
        )
    }

    if (!inPip && showArchive) {
        ArchiveDialog(
            archive = ui.archive,
            catchingUp = ui.catchingUp,
            onDismiss = {
                showArchive = false
                revealControls()
            },
            onLive = {
                showArchive = false
                playerViewModel.stopCatchup()
                revealControls()
            },
            onProgram = { program ->
                showArchive = false
                playerViewModel.playCatchup(program)
                revealControls()
            }
        )
    }

    if (!inPip && showInfo && stream != null) {
        InfoDialog(
            stream = stream,
            onDismiss = {
                showInfo = false
                revealControls()
            },
            onRename = { name ->
                playerViewModel.rename(stream.uid, name)
            },
            onToggleFavorite = playerViewModel::toggleFavorite
        )
    }
}

@Composable
private fun TracksDialog(
    controller: MediaController,
    version: Int,
    onVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val audio = remember(version) { audioTrackOptions(controller) }
    val text = remember(version) { textTrackOptions(controller) }
    TvDialog(
        title = stringResource(R.string.player_tracks),
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.audio_track),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            PlayerOptionRow(
                label = stringResource(R.string.automatic),
                selected = audio.none { it.selected },
                onClick = {
                    selectAudioTrack(controller, null)
                    onVersion()
                },
                autoFocus = true
            )
            audio.forEach { option ->
                PlayerOptionRow(
                    label = option.label,
                    selected = option.selected,
                    onClick = {
                        selectAudioTrack(controller, option)
                        onVersion()
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.subtitle_track),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            PlayerOptionRow(
                label = stringResource(R.string.subtitles_off),
                selected = text.none { it.selected },
                onClick = {
                    disableTextTrack(controller)
                    onVersion()
                }
            )
            text.forEach { option ->
                PlayerOptionRow(
                    label = option.label,
                    selected = option.selected,
                    onClick = {
                        selectTextTrack(controller, option)
                        onVersion()
                    }
                )
            }
            if (audio.isEmpty()) {
                Text(
                    text = stringResource(R.string.not_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TvButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                primary = false
            )
        }
    }
}

@Composable
private fun ArchiveDialog(
    archive: List<EpgEntity>,
    catchingUp: Boolean,
    onDismiss: () -> Unit,
    onLive: () -> Unit,
    onProgram: (EpgEntity) -> Unit
) {
    TvDialog(
        title = stringResource(R.string.archive_title),
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    text = stringResource(R.string.live),
                    onClick = onLive,
                    enabled = catchingUp,
                    modifier = if (catchingUp) Modifier.autoFocusOnce() else Modifier
                )
            }
            if (archive.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_archive),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(archive, key = { _, program -> program.id }) { index, program ->
                        TvCard(
                            onClick = { onProgram(program) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (index == 0 && !catchingUp) Modifier.autoFocusOnce()
                                    else Modifier
                                ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = TimeFormat.range(program.startTime, program.endTime),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(150.dp)
                                )
                                Text(
                                    text = program.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            TvButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = if (archive.isEmpty() && !catchingUp) Modifier.autoFocusOnce()
                else Modifier,
                primary = false
            )
        }
    }
}

@Composable
private fun InfoDialog(
    stream: StreamEntity,
    onDismiss: () -> Unit,
    onRename: (String?) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val displayName = stream.customName?.takeIf { it.isNotBlank() } ?: stream.name
    var name by remember(stream.uid, stream.customName) {
        mutableStateOf(stream.customName.orEmpty())
    }
    TvDialog(
        title = stringResource(R.string.info),
        onDismiss = onDismiss
    ) { requester ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvBadge(
                    text = when (stream.kind) {
                        StreamKind.LIVE -> stringResource(R.string.live)
                        StreamKind.SERIES, StreamKind.EPISODE -> stringResource(R.string.series)
                        else -> stringResource(R.string.movies)
                    }
                )
                stream.channelNo?.let {
                    TvBadge(
                        text = contextChannelNumber(it),
                        color = Color.White.copy(alpha = 0.14f),
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                stream.rating?.takeIf { it.isNotBlank() }?.let {
                    TvBadge(text = "★ $it", color = AuroraColors.Warning)
                }
            }
            if (!stream.category.isNullOrBlank()) {
                Text(
                    text = stream.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!stream.plot.isNullOrBlank()) {
                Text(
                    text = stream.plot,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!stream.cast.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.cast_label) + ": " + stream.cast,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!stream.director.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.director_label) + ": " + stream.director,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            TvTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.rename),
                placeholder = stream.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        onRename(name.trim().ifBlank { null })
                        onDismiss()
                    }
                )
                TvButton(
                    text = stringResource(
                        if (stream.favorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                    primary = false
                )
                TvButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    primary = false
                )
            }
        }
    }
}

@Composable
private fun PlayerOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    autoFocus: Boolean = false
) {
    TvCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (autoFocus) Modifier.autoFocusOnce() else Modifier),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun contextChannelNumber(number: Int): String =
    stringResource(R.string.channel_number, number)
