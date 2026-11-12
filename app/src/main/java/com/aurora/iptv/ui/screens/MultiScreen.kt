package com.aurora.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aurora.iptv.AppContainer
import com.aurora.iptv.R
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.components.ChannelPickerRow
import com.aurora.iptv.ui.components.EmptyState
import com.aurora.iptv.ui.components.PinVerifyDialog
import com.aurora.iptv.ui.components.ScreenHeader
import com.aurora.iptv.ui.components.TvButton
import com.aurora.iptv.ui.components.TvChip
import com.aurora.iptv.ui.components.TvDialog
import com.aurora.iptv.ui.components.TvTextField
import com.aurora.iptv.ui.components.autoFocusOnce
import com.aurora.iptv.security.ContentFilter

@Composable
fun MultiScreen(
    appViewModel: AppViewModel,
    container: AppContainer
) {
    val allLive by appViewModel.allLive.collectAsStateWithLifecycle()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val streams = remember { mutableStateListOf<StreamEntity?>(null, null, null, null) }
    var layout by remember { mutableIntStateOf(2) }
    var activeCell by remember { mutableIntStateOf(0) }
    var pickerCell by remember { mutableStateOf<Int?>(null) }
    var pendingProtected by remember { mutableStateOf<Pair<Int, StreamEntity>?>(null) }
    var initialized by remember { mutableStateOf(false) }
    val adultLocked = settings.parentalLock && !settings.kidsMode

    fun assignChannel(cell: Int, stream: StreamEntity) {
        streams[cell] = stream
        activeCell = cell
        pickerCell = null
    }

    fun requestChannel(cell: Int, stream: StreamEntity) {
        if (adultLocked && ContentFilter.isAdult(stream)) {
            pendingProtected = cell to stream
        } else {
            assignChannel(cell, stream)
        }
    }

    LaunchedEffect(allLive, adultLocked) {
        if (!initialized && allLive.isNotEmpty()) {
            val available = allLive.filter { !adultLocked || !ContentFilter.isAdult(it) }
            for (index in 0 until layout) {
                if (streams[index] == null) {
                    streams[index] = available.getOrNull(index)
                }
            }
            initialized = true
        }
    }

    LaunchedEffect(settings.parentalLock, settings.kidsMode) {
        if (settings.parentalLock || settings.kidsMode) {
            streams.indices.forEach { index ->
                val current = streams[index]
                if (current != null && ContentFilter.isAdult(current)) {
                    streams[index] = null
                }
            }
            if (streams.getOrNull(activeCell) == null) activeCell = -1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 22.dp, end = 28.dp, bottom = 16.dp)
    ) {
        ScreenHeader(
            title = stringResource(R.string.multi_title),
            subtitle = stringResource(R.string.multi_hint),
            actions = {
                TvChip(
                    text = stringResource(R.string.layout_2),
                    selected = layout == 2,
                    onClick = { layout = 2; activeCell = 0 },
                    modifier = Modifier.autoFocusOnce()
                )
                TvChip(
                    text = stringResource(R.string.layout_4),
                    selected = layout == 4,
                    onClick = { layout = 4 }
                )
                TvButton(
                    text = stringResource(R.string.select_channel),
                    icon = Icons.Filled.GridView,
                    onClick = { pickerCell = activeCell },
                    primary = false
                )
            }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.multiview_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (allLive.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_channels),
                message = stringResource(R.string.multi_hint),
                modifier = Modifier.weight(1f)
            )
        } else {
            if (layout == 2) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    MultiCell(
                        index = 0,
                        stream = streams[0],
                        active = activeCell == 0,
                        factory = container,
                        bufferSeconds = settings.bufferSeconds,
                        onSelect = { pickerCell = 0 },
                        onAudio = { activeCell = if (activeCell == 0) -1 else 0 },
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                    )
                    MultiCell(
                        index = 1,
                        stream = streams[1],
                        active = activeCell == 1,
                        factory = container,
                        bufferSeconds = settings.bufferSeconds,
                        onSelect = { pickerCell = 1 },
                        onAudio = { activeCell = if (activeCell == 1) -1 else 1 },
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                    )
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        MultiCell(
                            index = 0,
                            stream = streams[0],
                            active = activeCell == 0,
                            factory = container,
                            bufferSeconds = settings.bufferSeconds,
                            onSelect = { pickerCell = 0 },
                            onAudio = { activeCell = if (activeCell == 0) -1 else 0 },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                        )
                        MultiCell(
                            index = 1,
                            stream = streams[1],
                            active = activeCell == 1,
                            factory = container,
                            bufferSeconds = settings.bufferSeconds,
                            onSelect = { pickerCell = 1 },
                            onAudio = { activeCell = if (activeCell == 1) -1 else 1 },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                        )
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        MultiCell(
                            index = 2,
                            stream = streams[2],
                            active = activeCell == 2,
                            factory = container,
                            bufferSeconds = settings.bufferSeconds,
                            onSelect = { pickerCell = 2 },
                            onAudio = { activeCell = if (activeCell == 2) -1 else 2 },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                        )
                        MultiCell(
                            index = 3,
                            stream = streams[3],
                            active = activeCell == 3,
                            factory = container,
                            bufferSeconds = settings.bufferSeconds,
                            onSelect = { pickerCell = 3 },
                            onAudio = { activeCell = if (activeCell == 3) -1 else 3 },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.audio_active) + ": " +
                    if (activeCell >= 0) "#${activeCell + 1}" else stringResource(R.string.mute_cell),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    pickerCell?.let { cell ->
        ChannelPickerDialog(
            allLive = allLive,
            selected = streams.getOrNull(cell),
            onDismiss = { pickerCell = null },
            onPick = { stream -> requestChannel(cell, stream) }
        )
    }

    pendingProtected?.let { (cell, stream) ->
        PinVerifyDialog(
            title = stringResource(R.string.pin_required),
            onDismiss = { pendingProtected = null },
            verifyPin = appViewModel::verifyPin,
            onVerified = {
                pendingProtected = null
                assignChannel(cell, stream)
            }
        )
    }
}

@Composable
private fun MultiCell(
    index: Int,
    stream: StreamEntity?,
    active: Boolean,
    factory: AppContainer,
    bufferSeconds: Int,
    onSelect: () -> Unit,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val player = remember {
        factory.playerFactory.build(bufferSeconds, handleAudioFocus = false)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                error = playbackError.localizedMessage
                    ?: playbackError.errorCodeName
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) error = null
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(stream?.uid) {
        val current = stream
        if (current == null) {
            player.pause()
            player.stop()
            player.clearMediaItems()
        } else {
            val url = current.streamUrl
            if (url.isNotBlank()) {
                player.setMediaItem(factory.playerFactory.mediaItem(current, url))
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    LaunchedEffect(active) {
        player.volume = if (active) 1f else 0f
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
    ) {
        if (stream == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_cell),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TvButton(
                    text = stringResource(R.string.assign_channel),
                    onClick = onSelect,
                    primary = false
                )
            }
        } else {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = stream.customName?.takeIf { it.isNotBlank() } ?: stream.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (error != null) {
                Text(
                    text = error!!,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = if (active) stringResource(R.string.mute_cell)
                    else stringResource(R.string.unmute_cell),
                    icon = if (active) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    onClick = onAudio,
                    primary = false
                )
                TvButton(
                    text = stringResource(R.string.select_channel),
                    onClick = onSelect,
                    primary = true
                )
            }
            if (buffering) {
                Text(
                    text = stringResource(R.string.buffering),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ChannelPickerDialog(
    allLive: List<StreamEntity>,
    selected: StreamEntity?,
    onDismiss: () -> Unit,
    onPick: (StreamEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(allLive, query) {
        if (query.isBlank()) allLive
        else allLive.filter {
            it.name.contains(query, true) ||
                (it.customName?.contains(query, true) == true) ||
                it.category.contains(query, true)
        }
    }
    TvDialog(title = stringResource(R.string.select_channel), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.search),
                placeholder = stringResource(R.string.search_hint),
                modifier = Modifier.autoFocusOnce()
            )
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_channels),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(430.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.uid }) { stream ->
                        ChannelPickerRow(
                            stream = stream,
                            nowNext = null,
                            onClick = { onPick(stream) },
                            selected = stream.uid == selected?.uid
                        )
                    }
                }
            }
            TvButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                primary = false
            )
        }
    }
}
