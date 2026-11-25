package io.github.henriksten65.alarmclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

/** Digits all the same width, so the running time doesn't jiggle. */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by Stopwatch.state(context).collectAsStateWithLifecycle()
    val running = state.status == StopwatchStatus.RUNNING

    val elapsed = state.elapsed(rememberElapsedNow(ticking = running, intervalMillis = 31))
    // Less space around the time when the window is short (a phone in landscape), so laps show.
    val shortWindow = isShortWindow()

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
                            when (state.status) {
                                StopwatchStatus.IDLE -> ControlButton(R.string.start) { Stopwatch.start(context) }
                                StopwatchStatus.RUNNING -> {
                                    ControlButton(R.string.lap, outlined = true) { Stopwatch.lap(context) }
                                    ControlButton(R.string.pause) { Stopwatch.pause(context) }
                                }
                                StopwatchStatus.PAUSED -> {
                                    ControlButton(R.string.reset, outlined = true) { Stopwatch.reset(context) }
                                    ControlButton(R.string.resume) { Stopwatch.resume(context) }
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

/** A large Start / Lap / Pause / Reset / Resume button. */
@Composable
private fun ControlButton(textRes: Int, outlined: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 128.dp)
    val content: @Composable RowScope.() -> Unit = {
        Text(stringResource(textRes), style = MaterialTheme.typography.titleMedium)
    }
    if (outlined) OutlinedButton(onClick = onClick, modifier = modifier, content = content)
    else Button(onClick = onClick, modifier = modifier, content = content)
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
