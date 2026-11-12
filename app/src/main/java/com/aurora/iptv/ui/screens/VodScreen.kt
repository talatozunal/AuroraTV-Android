package com.aurora.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.iptv.R
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.components.EmptyState
import com.aurora.iptv.ui.components.HorizontalChipRow
import com.aurora.iptv.ui.components.ScreenHeader
import com.aurora.iptv.ui.components.TvChip
import com.aurora.iptv.ui.components.VodCard
import com.aurora.iptv.ui.components.autoFocusOnce

@Composable
fun VodScreen(
    appViewModel: AppViewModel,
    onPlay: (StreamEntity, Long) -> Unit
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val kind by appViewModel.vodKind.collectAsStateWithLifecycle()
    val categories by appViewModel.vodCategories.collectAsStateWithLifecycle()
    val streams by appViewModel.vodStreams.collectAsStateWithLifecycle()
    val selectedCategory by appViewModel.selectedVodCategory.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 22.dp, end = 28.dp, bottom = 16.dp)
    ) {
        ScreenHeader(
            title = stringResource(R.string.vod_title),
            subtitle = stringResource(
                if (kind == StreamKind.SERIES) R.string.series_count else R.string.movie_count,
                streams.size
            )
        )
        Spacer(Modifier.height(10.dp))
        HorizontalChipRow {
            TvChip(
                text = stringResource(R.string.movies),
                selected = kind == StreamKind.MOVIE,
                onClick = { appViewModel.selectVodKind(StreamKind.MOVIE) },
                leading = Icons.Filled.Movie,
                modifier = Modifier.autoFocusOnce()
            )
            TvChip(
                text = stringResource(R.string.series),
                selected = kind == StreamKind.SERIES,
                onClick = { appViewModel.selectVodKind(StreamKind.SERIES) }
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            TvChip(
                text = stringResource(R.string.all),
                selected = selectedCategory == null,
                onClick = { appViewModel.selectVodCategory(null) }
            )
            categories.forEach { row ->
                TvChip(
                    text = row.category,
                    selected = selectedCategory == row.category,
                    onClick = { appViewModel.selectVodCategory(row.category) }
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (streams.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_vod),
                message = stringResource(R.string.vod_title),
                icon = Icons.Filled.Movie,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(164.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(streams, key = { it.uid }) { stream ->
                    VodCard(
                        stream = stream,
                        onClick = { appViewModel.openVodDetail(stream) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    VodDetailHost(
        appViewModel = appViewModel,
        settings = settings,
        onPlay = onPlay
    )
}
