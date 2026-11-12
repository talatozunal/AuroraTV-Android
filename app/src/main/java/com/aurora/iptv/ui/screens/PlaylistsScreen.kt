package com.aurora.iptv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.iptv.R
import com.aurora.iptv.data.db.PlaylistEntity
import com.aurora.iptv.data.db.PlaylistType
import com.aurora.iptv.data.repo.PlaylistDraft
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.components.EmptyState
import com.aurora.iptv.ui.components.ScreenHeader
import com.aurora.iptv.ui.components.SyncStatusDialog
import com.aurora.iptv.ui.components.TvBadge
import com.aurora.iptv.ui.components.TvButton
import com.aurora.iptv.ui.components.TvCard
import com.aurora.iptv.ui.components.TvChip
import com.aurora.iptv.ui.components.TvDialog
import com.aurora.iptv.ui.components.TvIconButton
import com.aurora.iptv.ui.components.TvTextField
import com.aurora.iptv.ui.components.autoFocusOnce
import com.aurora.iptv.ui.theme.AuroraColors
import com.aurora.iptv.ui.util.TimeFormat

private enum class SourceMode { M3U_URL, XTREAM, M3U_FILE }

@Composable
fun PlaylistsScreen(
    appViewModel: AppViewModel,
    setup: Boolean,
    onOpenPlayer: () -> Unit,
    onManageFinished: () -> Unit
) {
    val context = LocalContext.current
    val playlists by appViewModel.playlists.collectAsStateWithLifecycle()
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val sync by appViewModel.sync.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlaylistEntity?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 24.dp)
    ) {
        if (setup) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.welcome_title),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(R.string.welcome_sub),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TvButton(
                    text = stringResource(R.string.add_playlist),
                    icon = Icons.Filled.Add,
                    onClick = { showAdd = true },
                    modifier = Modifier.autoFocusOnce()
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.welcome_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.legal_notice_short),
                style = MaterialTheme.typography.bodySmall,
                color = AuroraColors.Warning
            )
        } else {
            ScreenHeader(
                title = stringResource(R.string.playlists),
                subtitle = stringResource(R.string.playlists_section),
                actions = {
                    TvButton(
                        text = stringResource(R.string.add_playlist),
                        icon = Icons.Filled.Add,
                        onClick = { showAdd = true }
                    )
                    TvButton(
                        text = stringResource(R.string.back),
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onManageFinished,
                        primary = false
                    )
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        if (playlists.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_playlists),
                message = stringResource(R.string.welcome_hint),
                icon = Icons.Filled.FolderOpen,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        active = playlist.id == settings.activePlaylistId,
                        onActivate = { appViewModel.setPlaylist(playlist.id) },
                        onSync = { appViewModel.refreshPlaylist(playlist.id) },
                        onEpg = { appViewModel.refreshEpg(playlist.id) },
                        onDelete = { deleteTarget = playlist }
                    )
                }
            }
        }
    }

    if (showAdd) {
        PlaylistFormDialog(
            onDismiss = { showAdd = false },
            onImport = { draft ->
                showAdd = false
                appViewModel.addPlaylist(draft)
            }
        )
    }

    SyncStatusDialog(state = sync, onDismiss = appViewModel::dismissSync)

    deleteTarget?.let { playlist ->
        TvDialog(
            title = stringResource(R.string.delete_playlist),
            onDismiss = { deleteTarget = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.name),
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = stringResource(R.string.delete),
                        onClick = {
                            appViewModel.deletePlaylist(playlist.id)
                            deleteTarget = null
                        },
                        modifier = Modifier.autoFocusOnce(),
                        primary = true
                    )
                    TvButton(
                        text = stringResource(R.string.cancel),
                        onClick = { deleteTarget = null },
                        primary = false
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistEntity,
    active: Boolean,
    onActivate: () -> Unit,
    onSync: () -> Unit,
    onEpg: () -> Unit,
    onDelete: () -> Unit
) {
    TvCard(
        onClick = onActivate,
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(10.dp))
                    if (active) {
                        TvBadge(text = stringResource(R.string.active_playlist))
                    }
                    Spacer(Modifier.width(8.dp))
                    TvBadge(
                        text = if (playlist.type == PlaylistType.XTREAM) {
                            stringResource(R.string.source_xtream)
                        } else {
                            stringResource(R.string.source_m3u_url)
                        },
                        color = AuroraColors.Secondary.copy(alpha = 0.9f),
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(
                        R.string.sync_counts,
                        playlist.channelCount,
                        playlist.vodCount,
                        playlist.epgCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.last_updated,
                        if (playlist.updatedAt > 0L) TimeFormat.dateTime(playlist.updatedAt)
                        else stringResource(R.string.never)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIconButton(
                    icon = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.set_active),
                    onClick = onActivate,
                    selected = active,
                    size = 46.dp
                )
                TvIconButton(
                    icon = Icons.Filled.Sync,
                    contentDescription = stringResource(R.string.refresh_playlist),
                    onClick = onSync,
                    size = 46.dp
                )
                TvIconButton(
                    icon = Icons.Filled.CalendarMonth,
                    contentDescription = stringResource(R.string.refresh_epg),
                    onClick = onEpg,
                    size = 46.dp
                )
                TvIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_playlist),
                    onClick = onDelete,
                    size = 46.dp
                )
            }
        }
    }
}

@Composable
private fun PlaylistFormDialog(
    onDismiss: () -> Unit,
    onImport: (PlaylistDraft) -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(SourceMode.M3U_URL) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val nameRequired = stringResource(R.string.name_required)
    val urlRequired = stringResource(R.string.url_required)
    val credentialsRequired = stringResource(R.string.credentials_required)
    val fileError = stringResource(R.string.file_pick_error)

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            fileUri = uri.toString()
            if (name.isBlank()) {
                name = uri.lastPathSegment?.substringBeforeLast('.') ?: "M3U"
            }
            localError = null
        }
    }

    fun submit() {
        if (name.isBlank()) {
            localError = nameRequired
            return
        }
        when (mode) {
            SourceMode.M3U_URL -> {
                if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                    localError = urlRequired
                    return
                }
                onImport(PlaylistDraft.M3uUrl(name.trim(), url.trim(), epgUrl.trim().ifBlank { null }))
            }

            SourceMode.XTREAM -> {
                if (server.isBlank() || username.isBlank() || password.isBlank()) {
                    localError = credentialsRequired
                    return
                }
                onImport(
                    PlaylistDraft.Xtream(
                        name = name.trim(),
                        server = server.trim(),
                        username = username.trim(),
                        password = password,
                        epgUrl = epgUrl.trim().ifBlank { null }
                    )
                )
            }

            SourceMode.M3U_FILE -> {
                val selected = fileUri
                if (selected.isNullOrBlank()) {
                    localError = fileError
                    return
                }
                onImport(
                    PlaylistDraft.M3uFile(
                        name = name.trim(),
                        uri = selected,
                        epgUrl = epgUrl.trim().ifBlank { null }
                    )
                )
            }
        }
    }

    TvDialog(title = stringResource(R.string.add_playlist), onDismiss = onDismiss) { requester ->
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvChip(
                    text = stringResource(R.string.source_m3u_url),
                    selected = mode == SourceMode.M3U_URL,
                    onClick = { mode = SourceMode.M3U_URL; localError = null }
                )
                TvChip(
                    text = stringResource(R.string.source_xtream),
                    selected = mode == SourceMode.XTREAM,
                    onClick = { mode = SourceMode.XTREAM; localError = null }
                )
                TvChip(
                    text = stringResource(R.string.source_m3u_file),
                    selected = mode == SourceMode.M3U_FILE,
                    onClick = { mode = SourceMode.M3U_FILE; localError = null }
                )
            }

            FirstField(name, { name = it; localError = null }, requester)

            when (mode) {
                SourceMode.M3U_URL -> {
                    TvTextField(
                        value = url,
                        onValueChange = { url = it; localError = null },
                        label = stringResource(R.string.m3u_url),
                        placeholder = "http://example.com/playlist.m3u"
                    )
                }

                SourceMode.XTREAM -> {
                    TvTextField(
                        value = server,
                        onValueChange = { server = it; localError = null },
                        label = stringResource(R.string.xtream_server),
                        placeholder = "http://server:8080"
                    )
                    TvTextField(
                        value = username,
                        onValueChange = { username = it; localError = null },
                        label = stringResource(R.string.xtream_user)
                    )
                    TvTextField(
                        value = password,
                        onValueChange = { password = it; localError = null },
                        label = stringResource(R.string.xtream_pass),
                        password = true
                    )
                }

                SourceMode.M3U_FILE -> {
                    TvButton(
                        text = if (fileUri == null) stringResource(R.string.choose_file)
                        else stringResource(R.string.file_selected),
                        icon = Icons.Filled.FolderOpen,
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        primary = false
                    )
                    if (fileUri != null) {
                        Text(
                            text = fileUri!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            TvTextField(
                value = epgUrl,
                onValueChange = { epgUrl = it },
                label = stringResource(R.string.epg_url_optional),
                placeholder = "http://example.com/epg.xml"
            )

            localError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = stringResource(R.string.import_start),
                    onClick = { submit() }
                )
                TvButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    primary = false
                )
            }
        }
    }
}

@Composable
private fun FirstField(value: String, onChange: (String) -> Unit, requester: FocusRequester) {
    TvTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.playlist_name),
        modifier = Modifier.focusRequester(requester)
    )
}
