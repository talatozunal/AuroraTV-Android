package com.aurora.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aurora.iptv.R
import com.aurora.iptv.data.repo.SyncPhase
import com.aurora.iptv.ui.SyncUiState

@Composable
fun SyncStatusDialog(state: SyncUiState, onDismiss: () -> Unit) {
    when {
        state.running -> {
            BlockingLoadingDialog(
                message = stringResource(
                    if (state.phase == SyncPhase.EPG) R.string.importing_epg
                    else R.string.importing
                ),
                detail = if (state.count > 0) state.count.toString() else null
            )
        }

        state.error != null -> {
            TvDialog(title = stringResource(R.string.import_failed), onDismiss = onDismiss) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (!state.detail.isNullOrBlank()) {
                        Text(
                            text = state.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(1.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(
                            text = stringResource(R.string.close),
                            onClick = onDismiss,
                            modifier = Modifier.autoFocusOnce(),
                            primary = true
                        )
                    }
                }
            }
        }

        state.done -> {
            TvDialog(title = null, onDismiss = onDismiss) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(state.messageRes ?: R.string.import_done),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TvButton(
                        text = stringResource(R.string.ok),
                        onClick = onDismiss,
                        modifier = Modifier.autoFocusOnce()
                    )
                }
            }
        }
    }
}
