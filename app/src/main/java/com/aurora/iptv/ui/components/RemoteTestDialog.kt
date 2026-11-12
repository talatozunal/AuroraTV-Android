package com.aurora.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.iptv.R

/**
 * Diagnostics for Bluetooth remotes whose buttons do not map to standard D-pad
 * key codes. Shows every key the focused view receives, without consuming it.
 */
@Composable
fun RemoteTestDialog(onDismiss: () -> Unit) {
    val history = remember { mutableStateListOf<String>() }

    TvDialog(title = stringResource(R.string.remote_test), onDismiss = onDismiss) { requester ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.remote_test_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .focusRequester(requester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        recordKey(history, event)
                        false
                    }
                    .background(
                        Color.White.copy(alpha = 0.04f),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (history.isEmpty()) {
                    Text(
                        text = stringResource(R.string.remote_press_button),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = history.first(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (history.size > 1) {
                Text(
                    text = stringResource(R.string.remote_history),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    history.drop(1).take(7).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    primary = false
                )
            }
        }
    }
}

private fun recordKey(history: MutableList<String>, event: KeyEvent) {
    val action = when (event.type) {
        KeyEventType.KeyDown -> "DOWN"
        KeyEventType.KeyUp -> "UP"
        else -> "UNKNOWN"
    }
    val native = event.nativeKeyEvent
    val line = "${action}  ${event.key}  (keyCode=${native.keyCode}, scan=${native.scanCode})"
    history.removeAll { it == line }
    history.add(0, line)
    while (history.size > 8) history.removeAt(history.lastIndex)
}
