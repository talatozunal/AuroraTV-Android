package com.aurora.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.iptv.R
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.data.settings.AppSettings
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.components.ChannelLogo
import com.aurora.iptv.ui.components.PlaybackGate
import com.aurora.iptv.ui.components.TvBadge
import com.aurora.iptv.ui.components.TvButton
import com.aurora.iptv.ui.components.TvCard
import com.aurora.iptv.ui.components.TvChip
import com.aurora.iptv.ui.components.TvDialog
import com.aurora.iptv.ui.components.TvIconButton
import com.aurora.iptv.ui.components.TvProgress
import com.aurora.iptv.ui.components.autoFocusOnce
import com.aurora.iptv.ui.theme.AuroraColors
import com.aurora.iptv.ui.util.TimeFormat

@Composable
fun VodDetailHost(
    appViewModel: AppViewModel,
    settings: AppSettings,
    onPlay: (StreamEntity, Long) -> Unit
) {
    val detail by appViewModel.vodDetail.collectAsStateWithLifecycle()
    val state = detail ?: return
    val stream = state.stream ?: return
    val title = stream.customName?.takeIf { it.isNotBlank() } ?: stream.name
    var favorite by remember(stream.uid) { mutableStateOf(stream.favorite) }
    var savedPosition by remember(stream.uid) { mutableLongStateOf(0L) }
    var savedDuration by remember(stream.uid) { mutableLongStateOf(0L) }
    var selectedSeason by remember(stream.uid) { mutableStateOf<Int?>(null) }

    LaunchedEffect(stream.uid) {
        val progress = appViewModel.progressFor(stream.uid)
        savedPosition = progress?.position ?: 0L
        savedDuration = progress?.duration ?: 0L
    }
    LaunchedEffect(state.episodes) {
        if (selectedSeason == null) selectedSeason = state.episodes.firstOrNull()?.season ?: 1
    }

    TvDialog(title = title, onDismiss = appViewModel::closeVodDetail) {
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.loading_details),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@TvDialog
        }

        PlaybackGate(
            settings = settings,
            verifyPin = appViewModel::verifyPin,
            onPlay = { target ->
                appViewModel.closeVodDetail()
                onPlay(target, if (target.kind == StreamKind.MOVIE) savedPosition else 0L)
            }
        ) { play ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChannelLogo(
                        logoUrl = stream.logoUrl,
                        name = stream.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        textSize = 44
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (stream.kind != StreamKind.SERIES) {
                            TvButton(
                                text = if (savedPosition > 10_000L) {
                                    stringResource(R.string.continue_watching)
                                } else {
                                    stringResource(R.string.play)
                                },
                                icon = Icons.Filled.PlayArrow,
                                onClick = { play(stream) },
                                modifier = Modifier.autoFocusOnce()
                            )
                        }
                        TvIconButton(
                            icon = Icons.Filled.Star,
                            contentDescription = if (favorite) {
                                stringResource(R.string.favorite_remove)
                            } else {
                                stringResource(R.string.favorite_add)
                            },
                            selected = favorite,
                            onClick = {
                                favorite = !favorite
                                appViewModel.toggleFavorite(stream)
                            }
                        )
                    }
                    if (savedPosition > 10_000L && stream.kind != StreamKind.SERIES) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TvProgress(
                                progress = if (savedDuration > 0L) {
                                    (savedPosition.toFloat() / savedDuration).coerceIn(0f, 1f)
                                } else {
                                    0f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(
                                    R.string.continue_watching
                                ) + " · " + TimeFormat.position(savedPosition),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvBadge(
                            text = if (stream.kind == StreamKind.SERIES) {
                                stringResource(R.string.series)
                            } else {
                                stringResource(R.string.movies)
                            }
                        )
                        stream.year?.takeIf { it.isNotBlank() }?.let {
                            TvBadge(
                                text = it,
                                color = Color.White.copy(alpha = 0.14f),
                                textColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        stream.rating?.takeIf { it.isNotBlank() }?.let {
                            TvBadge(
                                text = "★ $it",
                                color = AuroraColors.Warning
                            )
                        }
                    }

                    if (!stream.genre.isNullOrBlank()) {
                        LabeledLine(
                            label = stringResource(R.string.genre_label),
                            value = stream.genre
                        )
                    }
                    if (!stream.director.isNullOrBlank()) {
                        LabeledLine(
                            label = stringResource(R.string.director_label),
                            value = stream.director
                        )
                    }
                    if (!stream.cast.isNullOrBlank()) {
                        LabeledLine(
                            label = stringResource(R.string.cast_label),
                            value = stream.cast
                        )
                    }
                    if (!stream.plot.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.synopsis),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stream.plot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!state.error.isNullOrBlank()) {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (stream.kind == StreamKind.SERIES) {
                        SeriesEpisodes(
                            episodes = state.episodes,
                            selectedSeason = selectedSeason,
                            onSeason = { selectedSeason = it },
                            onPlay = { play(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesEpisodes(
    episodes: List<StreamEntity>,
    selectedSeason: Int?,
    onSeason: (Int) -> Unit,
    onPlay: (StreamEntity) -> Unit
) {
    val seasons = remember(episodes) {
        episodes.mapNotNull { it.season }.distinct().sorted().ifEmpty { listOf(1) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.seasons),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            seasons.forEach { season ->
                TvChip(
                    text = stringResource(R.string.season_number, season),
                    selected = selectedSeason == season,
                    onClick = { onSeason(season) }
                )
            }
        }
        val visible = episodes.filter { (it.season ?: 1) == (selectedSeason ?: 1) }
        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.no_vod),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(visible, key = { it.uid }) { episode ->
                    TvCard(
                        onClick = { onPlay(episode) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 9.dp
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${episode.season ?: 1}×${episode.episode ?: 0}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(52.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = episode.customName?.takeIf { it.isNotBlank() }
                                        ?: episode.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                episode.plot?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.play_episode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
