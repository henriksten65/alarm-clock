package io.github.henriksten65.alarmclock.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.henriksten65.alarmclock.AppSettings
import io.github.henriksten65.alarmclock.R
import io.github.henriksten65.alarmclock.formatTime
import kotlinx.coroutines.delay

@Composable
fun RingScreen(
    label: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    @DrawableRes icon: Int = R.drawable.ic_alarm,
    dismissText: String = stringResource(R.string.dismiss),
    snoozeText: String? = null,
) {
    val context = LocalContext.current
    val snoozeMinutes = remember { AppSettings.getInstance(context).snoozeMinutes.value }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(formatTime(context, now), style = MaterialTheme.typography.displayLarge)
            Text(
                label.ifBlank { stringResource(R.string.alarm) },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(64.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text(dismissText, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(snoozeText ?: stringResource(R.string.snooze_minutes, snoozeMinutes))
            }
        }
    }
}
