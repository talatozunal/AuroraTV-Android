package com.aurora.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.iptv.R
import kotlinx.coroutines.launch

@Composable
fun PinVerifyDialog(
    title: String,
    onDismiss: () -> Unit,
    verifyPin: suspend (String) -> Boolean,
    onVerified: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wrongText = stringResource(R.string.pin_wrong)

    fun submit(pin: String) {
        checking = true
        scope.launch {
            val ok = runCatching { verifyPin(pin) }.getOrDefault(false)
            checking = false
            if (ok) {
                onVerified()
            } else {
                error = true
                input = ""
            }
        }
    }

    TvDialog(title = title, onDismiss = onDismiss) { requester ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.enter_pin),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PinDots(count = input.length, error = error)
            if (error) {
                Text(
                    text = wrongText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            PinKeypad(
                enabled = !checking,
                focusRequester = requester,
                onDigit = { digit ->
                    if (input.length < 4) {
                        input += digit
                        error = false
                        if (input.length == 4) submit(input)
                    }
                },
                onBackspace = {
                    input = input.dropLast(1)
                    error = false
                }
            )
            if (checking) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(30.dp)
                )
            }
            TvButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                primary = false
            )
        }
    }
}

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var stage by remember { mutableStateOf(0) }
    var first by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    fun complete(pin: String) {
        if (stage == 0) {
            first = pin
            input = ""
            mismatch = false
            stage = 1
        } else if (pin == first) {
            onSave(pin)
        } else {
            mismatch = true
            input = ""
        }
    }

    TvDialog(
        title = if (stage == 0) stringResource(R.string.enter_new_pin)
        else stringResource(R.string.confirm_pin),
        onDismiss = onDismiss
    ) { requester ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = if (stage == 0) stringResource(R.string.parental_pin_needed)
                else stringResource(R.string.confirm_pin),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PinDots(count = input.length, error = mismatch)
            if (mismatch) {
                Text(
                    text = stringResource(R.string.pin_mismatch),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            PinKeypad(
                focusRequester = requester,
                onDigit = { digit ->
                    if (input.length < 4) {
                        input += digit
                        mismatch = false
                        if (input.length == 4) complete(input)
                    }
                },
                onBackspace = {
                    input = input.dropLast(1)
                    mismatch = false
                }
            )
            TvButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                primary = false
            )
        }
    }
}

@Composable
private fun PinDots(count: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(4) { index ->
            Box(
                Modifier
                    .size(18.dp)
                    .background(
                        color = when {
                            error -> MaterialTheme.colorScheme.error
                            index < count -> MaterialTheme.colorScheme.primary
                            else -> Color.White.copy(alpha = 0.18f)
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    focusRequester: FocusRequester,
    enabled: Boolean = true
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    var first = true
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.width(72.dp))
                    } else {
                        val modifier = if (first) {
                            first = false
                            Modifier
                                .size(72.dp)
                                .focusRequester(focusRequester)
                        } else {
                            Modifier.size(72.dp)
                        }
                        TvButton(
                            text = key,
                            onClick = {
                                if (key == "⌫") onBackspace() else onDigit(key)
                            },
                            modifier = modifier,
                            primary = key != "⌫",
                            enabled = enabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PinSummary(dots: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) { index ->
            Box(
                Modifier
                    .size(14.dp)
                    .background(
                        if (index < dots) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
            )
        }
    }
    Spacer(Modifier.height(1.dp))
    Text(text = "", fontSize = 1.sp, fontWeight = FontWeight.Normal)
}
