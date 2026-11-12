package com.aurora.iptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.aurora.iptv.R
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.settings.AppSettings
import com.aurora.iptv.security.ContentFilter

/**
 * Central place where parental restrictions are enforced before handing a stream
 * to the player. Kids mode already filters lists; the PIN check is the last gate.
 */
@Composable
fun PlaybackGate(
    settings: AppSettings,
    verifyPin: suspend (String) -> Boolean,
    onPlay: (StreamEntity) -> Unit,
    content: @Composable ((StreamEntity) -> Unit) -> Unit
) {
    var pending by remember { mutableStateOf<StreamEntity?>(null) }

    content { stream ->
        val needsPin = settings.parentalLock &&
            !settings.kidsMode &&
            ContentFilter.isAdult(stream)
        if (needsPin) {
            pending = stream
        } else {
            onPlay(stream)
        }
    }

    val locked = pending
    if (locked != null) {
        val title = stringResource(R.string.pin_required)
        PinVerifyDialog(
            title = title,
            onDismiss = { pending = null },
            verifyPin = verifyPin,
            onVerified = {
                pending = null
                onPlay(locked)
            }
        )
    }
}
