package io.github.henriksten65.alarmclock.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.henriksten65.alarmclock.R
import io.github.henriksten65.alarmclock.Stopwatch
import io.github.henriksten65.alarmclock.StopwatchStatus
import io.github.henriksten65.alarmclock.formatStopwatch
import kotlinx.coroutines.delay

/** Digits all the same width, so the running time doesn't jiggle. */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by Stopwatch.state(context).collectAsStateWithLifecycle()
    val running = state.status == StopwatchStatus.RUNNING

    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        nowElapsed = SystemClock.elapsedRealtime()
        while (running) {
            delay(31)
            nowElapsed = SystemClock.elapsedRealtime()
        }
    }
    val elapsed = state.elapsed(nowElapsed)
    // Less space around the time when the window is short (a phone in landscape), so laps show.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val shortWindow = windowHeight < SHORT_WINDOW_HEIGHT

    // Newest first, each with its own time and the total: (number, lap time, total). The lap
    // in progress is on top while there are laps.
    val laps = buildList {
        var previous = 0L
        state.laps.forEachIndexed { i, total ->
            add(Triple(i + 1, total - previous, total))
            previous = total
        }
        if (state.laps.isNotEmpty() && state.status != StopwatchStatus.IDLE) {
            add(Triple(state.laps.size + 1, elapsed - previous, elapsed))
        }
    }.asReversed()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stopwatch)) }) },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(if (shortWindow) 0.dp else 24.dp))
                        Text(
                            formatStopwatch(elapsed),
                            style = MaterialTheme.typography.displayLarge.tabular(),
                            maxLines = 1,
                        )
                        if (state.status == StopwatchStatus.PAUSED) {
                            Text(stringResource(R.string.paused), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(if (shortWindow) 12.dp else 32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val buttonModifier = Modifier.heightIn(min = 56.dp).widthIn(min = 128.dp)
                            when (state.status) {
                                StopwatchStatus.IDLE ->
                                    Button(onClick = { Stopwatch.start(context) }, modifier = buttonModifier) {
                                        Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
                                    }
                                StopwatchStatus.RUNNING -> {
                                    OutlinedButton(onClick = { Stopwatch.lap(context) }, modifier = buttonModifier) {
                                        Text(stringResource(R.string.lap), style = MaterialTheme.typography.titleMedium)
                                    }
                                    Button(onClick = { Stopwatch.pause(context) }, modifier = buttonModifier) {
                                        Text(stringResource(R.string.pause), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                StopwatchStatus.PAUSED -> {
                                    OutlinedButton(onClick = { Stopwatch.reset(context) }, modifier = buttonModifier) {
                                        Text(stringResource(R.string.reset), style = MaterialTheme.typography.titleMedium)
                                    }
                                    Button(onClick = { Stopwatch.resume(context) }, modifier = buttonModifier) {
                                        Text(stringResource(R.string.resume), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(if (shortWindow) 8.dp else 16.dp))
                    }
                }
                items(laps, key = { it.first }) { (number, lapTime, total) ->
                    LapRow(number, lapTime, total)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun LapRow(number: Int, lapTime: Long, total: Long) {
    val name = stringResource(R.string.lap_number, number)
    val description = stringResource(R.string.lap_description, name, formatStopwatch(lapTime), formatStopwatch(total))
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = MaterialTheme.typography.bodyLarge.tabular()
            Text(name, style = style, modifier = Modifier.weight(1f))
            Text(formatStopwatch(lapTime), style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text(
                formatStopwatch(total),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
