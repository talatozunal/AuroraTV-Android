package com.aurora.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.iptv.R
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.components.EmptyState
import com.aurora.iptv.ui.components.HorizontalChipRow
import com.aurora.iptv.ui.components.LiveChannelCard
import com.aurora.iptv.ui.components.PlaybackGate
import com.aurora.iptv.ui.components.ScreenHeader
import com.aurora.iptv.ui.components.TvChip
import com.aurora.iptv.ui.components.TvSectionTitle
import com.aurora.iptv.ui.components.TvTextField
import com.aurora.iptv.ui.components.VodCard
import com.aurora.iptv.ui.components.autoFocusOnce

@Composable
fun SearchScreen(
    appViewModel: AppViewModel,
    onPlayLive: (StreamEntity) -> Unit,
    onPlayVod: (StreamEntity, Long) -> Unit
) {
    val search by appViewModel.search.collectAsStateWithLifecycle()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val outcome = search.outcome

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 22.dp, end = 28.dp, bottom = 16.dp)
    ) {
        ScreenHeader(
            title = stringResource(R.string.search_title),
            subtitle = stringResource(R.string.ai_hint)
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = search.query,
            onValueChange = appViewModel::setSearchQuery,
            label = stringResource(R.string.search),
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .autoFocusOnce()
        )
        Spacer(Modifier.height(12.dp))
        if (search.query.isBlank()) {
            HorizontalChipRow {
                val suggestions = listOf(
                    R.string.suggestion_sports,
                    R.string.suggestion_kids,
                    R.string.suggestion_news,
                    R.string.suggestion_action,
                    R.string.suggestion_music,
                    R.string.suggestion_docu
                )
                suggestions.forEach { res ->
                    TvChip(
                        text = stringResource(res),
                        selected = false,
                        onClick = {
                            appViewModel.setSearchQueryImmediate(context.getString(res))
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (search.loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(24.dp)
            )
        } else if (outcome == null) {
            EmptyState(
                title = stringResource(R.string.no_results),
                message = stringResource(R.string.search_hint),
                icon = Icons.Filled.Search
            )
        } else {
            Text(
                text = stringResource(
                    R.string.search_interpretation,
                    search.query.trim()
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f)) {
                PlaybackGate(
                    settings = settings,
                    verifyPin = appViewModel::verifyPin,
                    onPlay = onPlayLive
                ) { playLive ->
                    val live = outcome.live
                    val vod = outcome.vod
                    if (live.isEmpty() && vod.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.no_results),
                            message = stringResource(R.string.ai_hint),
                            icon = Icons.Filled.Search,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(172.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (live.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    TvSectionTitle(
                                        title = stringResource(R.string.live_results) +
                                            " · ${live.size}",
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(live, key = { "live-${it.uid}" }) { stream ->
                                    LiveChannelCard(
                                        stream = stream,
                                        number = 0,
                                        nowNext = null,
                                        onClick = { playLive(stream) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (vod.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    TvSectionTitle(
                                        title = stringResource(R.string.vod_results) +
                                            " · ${vod.size}",
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(vod, key = { "vod-${it.uid}" }) { stream ->
                                    VodCard(
                                        stream = stream,
                                        onClick = { appViewModel.openVodDetail(stream) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    VodDetailHost(
        appViewModel = appViewModel,
        settings = settings,
        onPlay = onPlayVod
    )
}
