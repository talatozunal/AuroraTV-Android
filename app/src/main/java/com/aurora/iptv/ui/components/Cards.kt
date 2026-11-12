package com.aurora.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.aurora.iptv.R
import com.aurora.iptv.data.db.CategoryRow
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.data.repo.NowNext
import com.aurora.iptv.ui.theme.AuroraColors
import com.aurora.iptv.ui.util.TimeFormat

@Composable
fun ChannelLogo(
    logoUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    textSize: Int = 24
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl.isNullOrBlank()) {
            Text(
                text = name.take(1).uppercase(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = textSize.sp
            )
        } else {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            ) {
                if (painter.state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = name.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = textSize.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveChannelCard(
    stream: StreamEntity,
    number: Int,
    nowNext: NowNext?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = stream.customName?.takeIf { it.isNotBlank() } ?: stream.name
    val current = nowNext?.now
    TvCard(
        onClick = onClick,
        modifier = modifier.height(172.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AuroraColors.Primary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                ChannelLogo(
                    logoUrl = stream.logoUrl,
                    name = displayName,
                    modifier = Modifier.size(64.dp),
                    textSize = 26
                )
                val badge = if (stream.channelNo != null) stream.channelNo.toString() else number.toString()
                TvBadge(
                    text = badge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
                if (stream.favorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(R.string.favorite_remove),
                        tint = AuroraColors.Warning,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (current != null) {
                        "${TimeFormat.clock(current.startTime)} · ${current.title}"
                    } else {
                        stringResource(R.string.no_epg)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VodCard(
    stream: StreamEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null
) {
    val base = if (width != null) modifier.width(width) else modifier
    TvCard(
        onClick = onClick,
        modifier = base,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                ChannelLogo(
                    logoUrl = stream.logoUrl,
                    name = stream.name,
                    modifier = Modifier.fillMaxSize(),
                    textSize = 34
                )
                if (stream.logoUrl.isNullOrBlank()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )
                }
                TvBadge(
                    text = if (stream.kind == StreamKind.SERIES) {
                        stringResource(R.string.series)
                    } else {
                        stringResource(R.string.movies)
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
                if (!stream.rating.isNullOrBlank()) {
                    TvBadge(
                        text = "★ ${stream.rating}",
                        color = AuroraColors.Warning,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = stream.customName?.takeIf { it.isNotBlank() } ?: stream.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    stream.year?.takeIf { it.isNotBlank() },
                    stream.genre?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                Text(
                    text = meta.ifBlank { stream.category },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CategoryRail(
    categories: List<CategoryRow>,
    selected: String?,
    showFavorites: Boolean,
    onSelect: (String?) -> Unit,
    onToggleFavorites: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    totalLabel: String = stringResource(R.string.all_channels)
) {
    Column(modifier.fillMaxHeight()) {
        TvSectionTitle(stringResource(R.string.choose_category))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvChip(
                text = totalLabel,
                selected = selected == null && !showFavorites,
                onClick = { onSelect(null) },
                modifier = if (autoFocus) Modifier.autoFocusOnce() else Modifier
            )
            TvChip(
                text = stringResource(R.string.favorites),
                selected = showFavorites,
                onClick = onToggleFavorites
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            itemsIndexed(categories, key = { _, row -> row.category }) { _, row ->
                val isSelected = selected == row.category && !showFavorites
                TvCard(
                    onClick = { onSelect(row.category) },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.category.ifBlank { stringResource(R.string.not_available) },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = row.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelPickerRow(
    stream: StreamEntity,
    nowNext: NowNext?,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(
                logoUrl = stream.logoUrl,
                name = stream.name,
                modifier = Modifier.size(40.dp),
                textSize = 18
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stream.customName?.takeIf { it.isNotBlank() } ?: stream.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val now = nowNext?.now
                Text(
                    text = now?.let { "${TimeFormat.clock(it.startTime)} · ${it.title}" }
                        ?: stream.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (stream.favorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = AuroraColors.Warning,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
