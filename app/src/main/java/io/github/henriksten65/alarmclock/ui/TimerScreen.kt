package io.github.henriksten65.alarmclock.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.henriksten65.alarmclock.AlarmScheduler
import io.github.henriksten65.alarmclock.AlarmService
import io.github.henriksten65.alarmclock.R
import io.github.henriksten65.alarmclock.Timer
import io.github.henriksten65.alarmclock.TimerState
import io.github.henriksten65.alarmclock.TimerStatus
import io.github.henriksten65.alarmclock.formatDuration
import kotlinx.coroutines.delay

/** At most six digits, read as hhmmss from the right: "130" is 1 min 30 s. */
internal fun timerDigitsToMillis(digits: String): Long {
    val d = digits.takeLast(6).padStart(6, '0')
    val h = d.substring(0, 2).toLong()
    val m = d.substring(2, 4).toLong()
    val s = d.substring(4, 6).toLong()
    return ((h * 60 + m) * 60 + s) * 1000L
}

/** The digits that show [millis] on the keypad display: 5 min → "500". */
internal fun millisToTimerDigits(millis: Long): String {
    val total = millis / 1000
    return "%02d%02d%02d".format(total / 3600, total / 60 % 60, total % 60).trimStart('0')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by Timer.state(context).collectAsStateWithLifecycle()
    val ringingId by AlarmService.ringingAlarmId.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.timer)) }) },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                ringingId == Timer.RING_ID -> TimesUp(
                    onStop = { AlarmScheduler.stopRinging(context) },
                    onAddMinute = { Timer.addMinute(context) },
                )
                state.status == TimerStatus.IDLE -> TimerEntry(
                    initialMillis = state.durationMillis,
                    onStart = { Timer.start(context, it) },
                )
                else -> Countdown(
                    state = state,
                    onPause = { Timer.pause(context) },
                    onResume = { Timer.resume(context) },
                    onReset = { Timer.reset(context) },
                    onAddMinute = { Timer.addMinute(context) },
                )
            }
        }
    }
}

@Composable
private fun TimerEntry(initialMillis: Long, onStart: (Long) -> Unit) {
    // Shows the last time used; typing replaces it (like the typed alarm time).
    var digits by rememberSaveable { mutableStateOf(millisToTimerDigits(initialMillis)) }
    var replaceOnType by rememberSaveable { mutableStateOf(true) }
    val millis = timerDigitsToMillis(digits)

    fun type(key: String) {
        val base = if (replaceOnType) "" else digits
        replaceOnType = false
        digits = (base + key).trimStart('0').take(6)
    }

    fun backspace() {
        replaceOnType = false
        digits = digits.dropLast(1)
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
        val wide = maxWidth > 560.dp
        val keySize = if (wide) 56.dp else 72.dp
        val display = @Composable {
            TimerEntryDisplay(digits, dimmed = replaceOnType)
        }
        val start = @Composable {
            Button(
                onClick = { onStart(millis) },
                enabled = millis > 0,
                modifier = Modifier.widthIn(min = 200.dp).height(56.dp),
            ) { Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium) }
        }
        val keypad = @Composable { Keypad(keySize, ::type, ::backspace) }
        if (wide) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    display()
                    Spacer(Modifier.height(24.dp))
                    start()
                }
                keypad()
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                display()
                Spacer(Modifier.height(16.dp))
                keypad()
                Spacer(Modifier.height(16.dp))
                start()
            }
        }
    }
}

/** "00h 05m 00s", digits large and units small; dimmed while the next key replaces it. */
@Composable
private fun TimerEntryDisplay(digits: String, dimmed: Boolean) {
    val d = digits.padStart(6, '0')
    val units = listOf(stringResource(R.string.hours_unit), stringResource(R.string.minutes_unit), stringResource(R.string.seconds_unit))
    val color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val text = buildAnnotatedString {
        for (i in 0..2) {
            if (i > 0) append("  ")
            append(d.substring(i * 2, i * 2 + 2))
            withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleLarge.fontSize)) { append(units[i]) }
        }
    }
    Text(text, style = MaterialTheme.typography.displayMedium, color = color, maxLines = 1)
}

@Composable
private fun Keypad(keySize: Dp, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val deleteDescription = stringResource(R.string.delete_digit)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(keySize)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { if (key == "⌫") onBackspace() else onDigit(key) }
                            .then(if (key == "⌫") Modifier.semantics { contentDescription = deleteDescription } else Modifier),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(key, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Countdown(
    state: TimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onAddMinute: () -> Unit,
) {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (state.status == TimerStatus.RUNNING) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    val remaining = state.remaining(nowElapsed)
    val running = state.status == TimerStatus.RUNNING

    BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
        // Side by side when wide (a phone in landscape), so the buttons stay on screen.
        val wide = maxWidth > 560.dp
        val ringSize = if (wide) 240.dp else minOf(maxWidth, 280.dp)
        val ring = @Composable {
            Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (state.totalMillis > 0) remaining.toFloat() / state.totalMillis else 0f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDuration(remaining), style = MaterialTheme.typography.displayMedium, maxLines = 1)
                    if (!running) {
                        Text(stringResource(R.string.paused), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        val buttons = @Composable {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reset)) }
                Button(onClick = if (running) onPause else onResume, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 120.dp)) {
                    Text(stringResource(if (running) R.string.pause else R.string.resume))
                }
                OutlinedButton(onClick = onAddMinute, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_minute)) }
            }
        }
        if (wide) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ring()
                Box(Modifier.widthIn(max = 360.dp)) { buttons() }
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ring()
                Spacer(Modifier.height(24.dp))
                buttons()
            }
        }
    }
}

@Composable
private fun TimesUp(onStop: () -> Unit, onAddMinute: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.times_up), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStop, modifier = Modifier.widthIn(min = 200.dp).height(56.dp)) {
            Text(stringResource(R.string.stop), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onAddMinute, modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.add_minute))
        }
        Spacer(Modifier.width(0.dp))
    }
}
