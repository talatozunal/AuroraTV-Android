package com.aurora.iptv.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.iptv.R
import com.aurora.iptv.data.db.EpgEntity
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.components.ChannelLogo
import com.aurora.iptv.ui.components.EmptyState
import com.aurora.iptv.ui.components.HorizontalChipRow
import com.aurora.iptv.ui.components.PinVerifyDialog
import com.aurora.iptv.ui.components.ScreenHeader
import com.aurora.iptv.ui.components.TvButton
import com.aurora.iptv.ui.components.TvCard
import com.aurora.iptv.ui.components.TvChip
import com.aurora.iptv.ui.components.TvDialog
import com.aurora.iptv.ui.components.autoFocusOnce
import com.aurora.iptv.security.ContentFilter
import com.aurora.iptv.ui.theme.AuroraColors
import com.aurora.iptv.ui.util.TimeFormat
import java.util.concurrent.TimeUnit

private sealed interface GuidePlayback {
    data class Live(val channel: StreamEntity) : GuidePlayback
    data class Catchup(
        val channel: StreamEntity,
        val startMs: Long,
        val endMs: Long,
        val title: String
    ) : GuidePlayback
}

@Composable
fun GuideScreen(
    appViewModel: AppViewModel,
    onPlayLive: (StreamEntity) -> Unit,
    onPlayCatchup: (StreamEntity, Long, Long, String) -> Unit
) {
    val categories by appViewModel.liveCategories.collectAsStateWithLifecycle()
    val guide by appViewModel.guide.collectAsStateWithLifecycle()
    val playlist by appViewModel.activePlaylist.collectAsStateWithLifecycle()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    var windowStart by remember {
        mutableStateOf(TimeFormat.floorToMinutes(System.currentTimeMillis(), 30))
    }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedProgram by remember { mutableStateOf<Pair<StreamEntity, EpgEntity>?>(null) }
    var pendingPlayback by remember { mutableStateOf<GuidePlayback?>(null) }

    fun perform(action: GuidePlayback) {
        when (action) {
            is GuidePlayback.Live -> onPlayLive(action.channel)
            is GuidePlayback.Catchup -> onPlayCatchup(
                action.channel,
                action.startMs,
                action.endMs,
                action.title
            )
        }
    }

    fun request(action: GuidePlayback) {
        val channel = when (action) {
            is GuidePlayback.Live -> action.channel
            is GuidePlayback.Catchup -> action.channel
        }
        val needsPin = settings.parentalLock &&
            !settings.kidsMode &&
            ContentFilter.isAdult(channel)
        if (needsPin) pendingPlayback = action else perform(action)
    }

    LaunchedEffect(windowStart, selectedCategory) {
        appViewModel.loadGuide(
            windowStart = windowStart,
            windowEnd = windowStart + TimeUnit.HOURS.toMillis(8),
            category = selectedCategory
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 22.dp, end = 28.dp, bottom = 16.dp)
    ) {
        ScreenHeader(
            title = stringResource(R.string.guide_title),
            subtitle = "${TimeFormat.dateLong(windowStart)} · ${TimeFormat.clock(windowStart)} – ${
                TimeFormat.clock(windowStart + TimeUnit.HOURS.toMillis(8))
            }",
            actions = {
                TvButton(
                    text = stringResource(R.string.earlier),
                    onClick = { windowStart -= TimeUnit.HOURS.toMillis(4) },
                    primary = false
                )
                TvButton(
                    text = stringResource(R.string.now_time),
                    onClick = {
                        windowStart = TimeFormat.floorToMinutes(
                            System.currentTimeMillis(),
                            30
                        ) - TimeUnit.HOURS.toMillis(1)
                    },
                    primary = false
                )
                TvButton(
                    text = stringResource(R.string.later),
                    onClick = { windowStart += TimeUnit.HOURS.toMillis(4) },
                    primary = false
                )
                TvButton(
                    text = stringResource(R.string.refresh_epg),
                    icon = Icons.Filled.Refresh,
                    onClick = { playlist?.id?.let(appViewModel::refreshEpg) }
                )
            }
        )
        Spacer(Modifier.height(10.dp))
        HorizontalChipRow {
            TvChip(
                text = stringResource(R.string.all_channels),
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                modifier = Modifier.autoFocusOnce()
            )
            categories.forEach { row ->
                TvChip(
                    text = row.category,
                    selected = selectedCategory == row.category,
                    onClick = { selectedCategory = row.category }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (guide.loading) {
            EmptyState(
                title = stringResource(R.string.loading),
                modifier = Modifier.weight(1f)
            )
        } else if (guide.channels.isEmpty() || guide.programs.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_programs),
                message = stringResource(R.string.guide_epg_missing),
                icon = Icons.Filled.CalendarMonth,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(guide.channels, key = { _, channel -> channel.uid }) { index, channel ->
                    val programs = channel.epgId?.let { guide.programs[it] }.orEmpty()
                    GuideChannelRow(
                        channel = channel,
                        programs = programs,
                        windowStart = windowStart,
                        autoFocus = index == 0,
                        onChannel = { request(GuidePlayback.Live(channel)) },
                        onProgram = { program ->
                            val now = System.currentTimeMillis()
                            when {
                                program.endTime <= now -> {
                                    if (supportsCatchup(channel)) {
                                        request(
                                            GuidePlayback.Catchup(
                                                channel = channel,
                                                startMs = program.startTime,
                                                endMs = program.endTime,
                                                title = program.title
                                            )
                                        )
                                    } else {
                                        selectedProgram = channel to program
                                    }
                                }

                                program.startTime <= now ->
                                    request(GuidePlayback.Live(channel))

                                else -> selectedProgram = channel to program
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.guide_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    selectedProgram?.let { (channel, program) ->
        val now = System.currentTimeMillis()
        val isPast = program.endTime <= now
        val isNow = program.startTime <= now && program.endTime > now
        val hasPlayAction = isNow || (isPast && supportsCatchup(channel))
        TvDialog(
            title = program.title,
            onDismiss = { selectedProgram = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "${TimeFormat.clock(program.startTime)} – ${TimeFormat.clock(program.endTime)} · ${channel.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!program.description.isNullOrBlank()) {
                    Text(
                        text = program.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isNow) {
                        TvButton(
                            text = stringResource(R.string.watch_channel),
                            onClick = {
                                selectedProgram = null
                                request(GuidePlayback.Live(channel))
                            },
                            modifier = Modifier.autoFocusOnce()
                        )
                    } else if (isPast && supportsCatchup(channel)) {
                        TvButton(
                            text = stringResource(R.string.catchup),
                            onClick = {
                                selectedProgram = null
                                request(
                                    GuidePlayback.Catchup(
                                        channel = channel,
                                        startMs = program.startTime,
                                        endMs = program.endTime,
                                        title = program.title
                                    )
                                )
                            },
                            modifier = Modifier.autoFocusOnce()
                        )
                    }
                    TvButton(
                        text = stringResource(R.string.close),
                        onClick = { selectedProgram = null },
                        modifier = if (!hasPlayAction) Modifier.autoFocusOnce() else Modifier,
                        primary = false
                    )
                }
            }
        }
    }

    pendingPlayback?.let { action ->
        PinVerifyDialog(
            title = stringResource(R.string.pin_required),
            onDismiss = { pendingPlayback = null },
            verifyPin = appViewModel::verifyPin,
            onVerified = {
                pendingPlayback = null
                perform(action)
            }
        )
    }
}

@Composable
private fun GuideChannelRow(
    channel: StreamEntity,
    programs: List<EpgEntity>,
    windowStart: Long,
    autoFocus: Boolean,
    onChannel: () -> Unit,
    onProgram: (EpgEntity) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvCard(
            onClick = onChannel,
            modifier = Modifier.width(190.dp).height(84.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(
                    logoUrl = channel.logoUrl,
                    name = channel.customName ?: channel.name,
                    modifier = Modifier.size(44.dp),
                    textSize = 17
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channel.customName?.takeIf { it.isNotBlank() } ?: channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    channel.channelNo?.let {
                        Text(
                            text = stringResource(R.string.channel_number, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.no_epg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.weight(1f).height(84.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(programs, key = { _, program -> program.id }) { pIndex, program ->
                    ProgramCard(
                        program = program,
                        onClick = { onProgram(program) },
                        autoFocus = autoFocus && pIndex == 0
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: EpgEntity,
    onClick: () -> Unit,
    autoFocus: Boolean
) {
    val now = System.currentTimeMillis()
    val current = program.startTime <= now && program.endTime > now
    val minutes = ((program.endTime - program.startTime).coerceAtLeast(TimeUnit.MINUTES.toMillis(15)) /
        TimeUnit.MINUTES.toMillis(1)).toInt()
    val width = (minutes * 3.2f).dp.coerceIn(120.dp, 360.dp)
    TvCard(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(84.dp)
            .then(if (autoFocus) Modifier.autoFocusOnce() else Modifier),
        containerColor = if (current) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TimeFormat.clock(program.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.live_now),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AuroraColors.Success
                    )
                }
            }
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun supportsCatchup(stream: StreamEntity): Boolean =
    stream.catchupType != null || stream.catchupDays > 0 || !stream.catchupSource.isNullOrBlank()
