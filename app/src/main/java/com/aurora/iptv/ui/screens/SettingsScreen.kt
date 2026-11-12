package com.aurora.iptv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.iptv.R
import com.aurora.iptv.data.settings.LocaleManager
import com.aurora.iptv.playback.ExternalPlayer
import com.aurora.iptv.playback.ExternalPlayers
import com.aurora.iptv.security.ContentFilter
import com.aurora.iptv.ui.AppViewModel
import com.aurora.iptv.ui.components.PinSetupDialog
import com.aurora.iptv.ui.components.PinVerifyDialog
import com.aurora.iptv.ui.components.RemoteTestDialog
import com.aurora.iptv.ui.components.ScreenHeader
import com.aurora.iptv.ui.components.SyncStatusDialog
import com.aurora.iptv.ui.components.TvBadge
import com.aurora.iptv.ui.components.TvButton
import com.aurora.iptv.ui.components.TvCard
import com.aurora.iptv.ui.components.TvDialog
import com.aurora.iptv.ui.components.TvSectionTitle
import com.aurora.iptv.ui.components.TvSettingRow
import com.aurora.iptv.ui.components.TvTextField
import com.aurora.iptv.ui.components.TvToggleRow
import com.aurora.iptv.ui.components.autoFocusOnce
import com.aurora.iptv.ui.util.findActivity

private enum class PendingPinAction { ENABLE_PARENTAL, ENABLE_KIDS }

private enum class VerifyAction { DISABLE_KIDS, DISABLE_PARENTAL, REMOVE_PIN, CHANGE_PIN }

@Composable
fun SettingsScreen(
    appViewModel: AppViewModel,
    onManagePlaylists: () -> Unit
) {
    val context = LocalContext.current
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val playlists by appViewModel.playlists.collectAsStateWithLifecycle()
    val sync by appViewModel.sync.collectAsStateWithLifecycle()
    val active = playlists.firstOrNull { it.id == settings.activePlaylistId }
        ?: playlists.firstOrNull()

    var showExternal by remember { mutableStateOf(false) }
    var showBuffer by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showRemote by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var pendingAfterPin by remember { mutableStateOf<PendingPinAction?>(null) }
    var verifyAction by remember { mutableStateOf<VerifyAction?>(null) }

    val toastPinSaved = stringResource(R.string.pin_saved)
    val toastPinRemoved = stringResource(R.string.pin_removed)

    LaunchedEffect(settings.hasPin) {
        if (!settings.hasPin) return@LaunchedEffect
        when (pendingAfterPin) {
            PendingPinAction.ENABLE_PARENTAL -> appViewModel.setParentalLock(true)
            PendingPinAction.ENABLE_KIDS -> appViewModel.setKidsMode(true)
            null -> Unit
        }
        if (pendingAfterPin != null) pendingAfterPin = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 22.dp, end = 28.dp, bottom = 16.dp)
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle)
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 22.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvSectionTitle(stringResource(R.string.playlists_section))
                    TvSettingRow(
                        label = active?.name ?: stringResource(R.string.no_active_playlist),
                        description = stringResource(R.string.manage_playlists_hint),
                        value = stringResource(R.string.manage_playlists),
                        icon = Icons.Filled.PlaylistPlay,
                        onClick = onManagePlaylists,
                        modifier = Modifier.autoFocusOnce()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(
                            text = stringResource(R.string.refresh_epg),
                            onClick = { active?.id?.let(appViewModel::refreshEpg) },
                            enabled = active != null
                        )
                        TvButton(
                            text = stringResource(R.string.epg_and_sync),
                            onClick = { active?.id?.let(appViewModel::refreshPlaylist) },
                            primary = false,
                            enabled = active != null
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvSectionTitle(stringResource(R.string.playback_section))
                    TvToggleRow(
                        label = stringResource(R.string.autostart),
                        description = stringResource(R.string.autostart_desc),
                        checked = settings.autostart,
                        onChange = appViewModel::setAutostart
                    )
                    TvToggleRow(
                        label = stringResource(R.string.background_play),
                        description = stringResource(R.string.background_play_desc),
                        checked = settings.backgroundPlay,
                        onChange = appViewModel::setBackgroundPlay
                    )
                    TvSettingRow(
                        label = stringResource(R.string.external_player),
                        description = stringResource(R.string.external_player_desc),
                        value = externalPlayerLabel(context, settings.externalPlayerPackage),
                        icon = Icons.Filled.Cast,
                        onClick = { showExternal = true }
                    )
                    TvSettingRow(
                        label = stringResource(R.string.buffer_size),
                        description = stringResource(R.string.buffer_desc),
                        value = stringResource(R.string.seconds_value, settings.bufferSeconds),
                        icon = Icons.Filled.Tune,
                        onClick = { showBuffer = true }
                    )
                    TvSettingRow(
                        label = stringResource(R.string.subtitle_size),
                        value = stringResource(R.string.scale_value, settings.subtitleScale),
                        icon = Icons.Filled.Subtitles,
                        onClick = { showSubtitle = true }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvSectionTitle(stringResource(R.string.parental_section))
                    TvToggleRow(
                        label = stringResource(R.string.kids_mode),
                        description = stringResource(R.string.kids_mode_desc),
                        checked = settings.kidsMode,
                        onChange = { enabled ->
                            if (enabled) {
                                if (settings.hasPin) {
                                    appViewModel.setKidsMode(true)
                                } else {
                                    pendingAfterPin = PendingPinAction.ENABLE_KIDS
                                    showPinSetup = true
                                }
                            } else if (settings.hasPin) {
                                verifyAction = VerifyAction.DISABLE_KIDS
                            } else {
                                appViewModel.setKidsMode(false)
                            }
                        }
                    )
                    if (settings.kidsMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvBadge(text = stringResource(R.string.kids_mode_on))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.kids_exit_pin),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TvToggleRow(
                        label = stringResource(R.string.parental_lock),
                        description = stringResource(R.string.parental_lock_desc),
                        checked = settings.parentalLock,
                        onChange = { enabled ->
                            if (enabled) {
                                if (settings.hasPin) {
                                    appViewModel.setParentalLock(true)
                                } else {
                                    pendingAfterPin = PendingPinAction.ENABLE_PARENTAL
                                    showPinSetup = true
                                }
                            } else {
                                verifyAction = VerifyAction.DISABLE_PARENTAL
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(
                            text = if (settings.hasPin) {
                                stringResource(R.string.change_pin)
                            } else {
                                stringResource(R.string.set_pin)
                            },
                            icon = Icons.Filled.Lock,
                            onClick = {
                                if (settings.hasPin) {
                                    verifyAction = VerifyAction.CHANGE_PIN
                                } else {
                                    showPinSetup = true
                                }
                            }
                        )
                        if (settings.hasPin) {
                            TvButton(
                                text = stringResource(R.string.remove_pin),
                                onClick = { verifyAction = VerifyAction.REMOVE_PIN },
                                primary = false
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvSectionTitle(stringResource(R.string.general_section))
                    TvSettingRow(
                        label = stringResource(R.string.language),
                        value = when (settings.language) {
                            "tr" -> stringResource(R.string.turkish)
                            "en" -> stringResource(R.string.english)
                            "de" -> stringResource(R.string.german)
                            "fr" -> stringResource(R.string.french)
                            "it" -> stringResource(R.string.italian)
                            else -> stringResource(R.string.language_system)
                        },
                        icon = Icons.Filled.Language,
                        onClick = { showLanguage = true }
                    )
                    TvSettingRow(
                        label = stringResource(R.string.remote_test),
                        description = stringResource(R.string.remote_test_desc),
                        value = stringResource(R.string.open),
                        icon = Icons.Filled.DeveloperMode,
                        onClick = { showRemote = true }
                    )
                    TvSettingRow(
                        label = stringResource(R.string.clear_data),
                        description = stringResource(R.string.clear_data_desc),
                        value = stringResource(R.string.clear_data),
                        icon = Icons.Filled.Storage,
                        onClick = { showClear = true }
                    )
                    TvSettingRow(
                        label = stringResource(R.string.about),
                        value = appVersion(context),
                        icon = Icons.Filled.Info,
                        onClick = { showAbout = true }
                    )
                }
            }
        }
    }

    if (showExternal) {
        ExternalPlayerDialog(
            selected = settings.externalPlayerPackage,
            onDismiss = { showExternal = false },
            onPick = { packageName ->
                appViewModel.setExternalPlayer(packageName)
                showExternal = false
            }
        )
    }

    if (showBuffer) {
        ChoiceDialog(
            title = stringResource(R.string.buffer_size),
            choices = listOf(10, 20, 30, 45, 60, 90, 120),
            current = settings.bufferSeconds,
            label = { stringResource(R.string.seconds_value, it) },
            onDismiss = { showBuffer = false },
            onPick = {
                appViewModel.setBufferSeconds(it)
                showBuffer = false
            }
        )
    }

    if (showSubtitle) {
        ChoiceDialog(
            title = stringResource(R.string.subtitle_size),
            choices = listOf(80, 100, 120, 150, 200),
            current = (settings.subtitleScale * 100).toInt(),
            label = { stringResource(R.string.scale_value, it / 100f) },
            onDismiss = { showSubtitle = false },
            onPick = {
                appViewModel.setSubtitleScale(it / 100f)
                showSubtitle = false
            }
        )
    }

    if (showLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            choices = listOf("system", "tr", "en", "de", "fr", "it"),
            current = settings.language,
            label = {
                when (it) {
                    "tr" -> stringResource(R.string.turkish)
                    "en" -> stringResource(R.string.english)
                    "de" -> stringResource(R.string.german)
                    "fr" -> stringResource(R.string.french)
                    "it" -> stringResource(R.string.italian)
                    else -> stringResource(R.string.language_system)
                }
            },
            onDismiss = { showLanguage = false },
            onPick = { code ->
                showLanguage = false
                appViewModel.setLanguage(code)
                LocaleManager.set(context, code)
                context.findActivity()?.recreate()
            }
        )
    }

    if (showClear) {
        TvDialog(
            title = stringResource(R.string.clear_data),
            onDismiss = { showClear = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = stringResource(R.string.clear_data_confirm),
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            showClear = false
                            appViewModel.clearEpgCache()
                        },
                        modifier = Modifier.autoFocusOnce()
                    )
                    TvButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showClear = false },
                        primary = false
                    )
                }
            }
        }
    }

    if (showAbout) {
        val version = appVersion(context)
        TvDialog(
            title = stringResource(R.string.about),
            onDismiss = { showAbout = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.version, version),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.legal_notice),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.legal_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvButton(
                    text = stringResource(R.string.close),
                    onClick = { showAbout = false },
                    modifier = Modifier.autoFocusOnce()
                )
            }
        }
    }

    if (showRemote) {
        RemoteTestDialog(onDismiss = { showRemote = false })
    }

    if (showPinSetup) {
        PinSetupDialog(
            onDismiss = {
                showPinSetup = false
                pendingAfterPin = null
            },
            onSave = { pin ->
                showPinSetup = false
                appViewModel.setPin(pin)
                Toast.makeText(context, toastPinSaved, Toast.LENGTH_SHORT).show()
            }
        )
    }

    verifyAction?.let { action ->
        val title = when (action) {
            VerifyAction.DISABLE_KIDS -> stringResource(R.string.disable_kids_pin)
            VerifyAction.DISABLE_PARENTAL -> stringResource(R.string.pin_required)
            VerifyAction.REMOVE_PIN -> stringResource(R.string.remove_pin)
            VerifyAction.CHANGE_PIN -> stringResource(R.string.change_pin)
        }
        PinVerifyDialog(
            title = title,
            onDismiss = { verifyAction = null },
            verifyPin = appViewModel::verifyPin,
            onVerified = {
                verifyAction = null
                when (action) {
                    VerifyAction.DISABLE_KIDS -> appViewModel.setKidsMode(false)
                    VerifyAction.DISABLE_PARENTAL -> appViewModel.setParentalLock(false)
                    VerifyAction.REMOVE_PIN -> {
                        appViewModel.clearPin()
                        Toast.makeText(context, toastPinRemoved, Toast.LENGTH_SHORT).show()
                    }

                    VerifyAction.CHANGE_PIN -> showPinSetup = true
                }
            }
        )
    }

    SyncStatusDialog(state = sync, onDismiss = appViewModel::dismissSync)
}

@Composable
private fun ExternalPlayerDialog(
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    val context = LocalContext.current
    val targets = remember { ExternalPlayers.allTargets(context) }
    TvDialog(
        title = stringResource(R.string.external_pick),
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionRow(
                label = stringResource(R.string.system_player),
                selected = selected.isNullOrBlank(),
                onClick = { onPick(null) },
                autoFocus = true
            )
            targets.forEach { player ->
                OptionRow(
                    label = player.label,
                    selected = selected == player.packageName,
                    onClick = { onPick(player.packageName) }
                )
            }
            if (targets.isEmpty()) {
                Text(
                    text = stringResource(R.string.external_not_installed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            TvButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                primary = false
            )
        }
    }
}

@Composable
private fun OptionRow(
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                TvBadge(text = stringResource(R.string.confirm))
            }
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<T>,
    current: T,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit
) {
    TvDialog(title = title, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEachIndexed { index, choice ->
                OptionRow(
                    label = label(choice),
                    selected = choice == current,
                    onClick = { onPick(choice) },
                    autoFocus = index == 0
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

private fun externalPlayerLabel(context: android.content.Context, packageName: String?): String {
    if (packageName.isNullOrBlank()) return context.getString(R.string.system_default)
    val known = ExternalPlayers.installed(context).firstOrNull { it.packageName == packageName }
    return known?.label ?: packageName
}

private fun appVersion(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}.getOrDefault("1.0")
