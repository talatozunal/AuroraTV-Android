package com.aurora.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.iptv.R
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.components.CategoryRail
import com.aurora.iptv.ui.components.EmptyState
import com.aurora.iptv.ui.components.LiveChannelCard
import com.aurora.iptv.ui.components.PlaybackGate
import com.aurora.iptv.ui.components.ScreenHeader
import com.aurora.iptv.ui.components.TvButton

@Composable
fun LiveScreen(
    appViewModel: AppViewModel,
    onPlayStream: (StreamEntity) -> Unit
) {
    val playlist by appViewModel.activePlaylist.collectAsStateWithLifecycle()
    val categories by appViewModel.liveCategories.collectAsStateWithLifecycle()
    val streams by appViewModel.liveStreams.collectAsStateWithLifecycle()
    val selectedCategory by appViewModel.selectedLiveCategory.collectAsStateWithLifecycle()
    val showFavorites by appViewModel.showFavoriteLive.collectAsStateWithLifecycle()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val nowNext by appViewModel.nowNext.collectAsStateWithLifecycle()

    LaunchedEffect(streams) {
        appViewModel.refreshNowNext(streams.take(400))
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 22.dp, end = 28.dp, bottom = 18.dp)
    ) {
        CategoryRail(
            categories = categories,
            selected = selectedCategory,
            showFavorites = showFavorites,
            onSelect = appViewModel::selectLiveCategory,
            onToggleFavorites = appViewModel::toggleFavoriteLive,
            modifier = Modifier.width(252.dp),
            autoFocus = true,
            totalLabel = stringResource(R.string.all_channels)
        )
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ScreenHeader(
                title = stringResource(R.string.live_title),
                subtitle = playlist?.let {
                    "${it.name} · " + stringResource(R.string.channel_count, streams.size)
                },
                actions = {
                    TvButton(
                        text = stringResource(R.string.refresh),
                        icon = Icons.Filled.Refresh,
                        onClick = { appViewModel.refreshNowNext(streams.take(400)) },
                        primary = false
                    )
                }
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f)) {
                PlaybackGate(
                    settings = settings,
                    verifyPin = appViewModel::verifyPin,
                    onPlay = onPlayStream
                ) { play ->
                    if (streams.isEmpty()) {
                        EmptyState(
                            title = if (showFavorites) {
                                stringResource(R.string.favorites)
                            } else {
                                stringResource(R.string.no_channels)
                            },
                            message = if (showFavorites) {
                                stringResource(R.string.favorites_empty)
                            } else {
                                stringResource(R.string.preview_hint)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(190.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(streams, key = { _, stream -> stream.uid }) { index, stream ->
                                LiveChannelCard(
                                    stream = stream,
                                    number = index + 1,
                                    nowNext = stream.epgId?.let { nowNext[it] },
                                    onClick = { play(stream) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = stringResource(R.string.preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
