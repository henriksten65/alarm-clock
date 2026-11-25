package io.github.henriksten65.alarmclock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import androidx.compose.runtime.key
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
import io.github.henriksten65.alarmclock.AppSettings
import io.github.henriksten65.alarmclock.KeypadLayout
import io.github.henriksten65.alarmclock.R
import io.github.henriksten65.alarmclock.Timer
import io.github.henriksten65.alarmclock.TimerState
import io.github.henriksten65.alarmclock.TimerStatus
import io.github.henriksten65.alarmclock.formatDuration

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
    val timers by Timer.timers(context).collectAsStateWithLifecycle()
    val lastDuration by Timer.lastDuration(context).collectAsStateWithLifecycle()
    val keypadLayout by AppSettings.getInstance(context).keypadLayout.collectAsStateWithLifecycle()
    val ringingTimerId = Timer.timerIdOf(AlarmService.ringingAlarmId.collectAsStateWithLifecycle().value)

    // The keypad for a new timer: on its own when there are no timers, otherwise after "Add timer".
    var adding by rememberSaveable { mutableStateOf(false) }
    // The timer being edited (same screen as a new one, with its time and label), or null.
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val editedTimer = editing?.let { id -> timers.firstOrNull { it.id == id } }
    // Started (e.g. Resume in the notification), ringing or deleted meanwhile: no more editing.
    val editable = editedTimer != null && Timer.isEditable(editedTimer) && editedTimer.id != ringingTimerId
    LaunchedEffect(editing, editable) { if (editing != null && !editable) editing = null }
    val showEntry = adding || editable || timers.isEmpty()
    val canCancel = showEntry && timers.isNotEmpty()
    val closeEntry = { adding = false; editing = null }
    BackHandler(enabled = canCancel) { closeEntry() }

    var renaming by rememberSaveable { mutableStateOf<Int?>(null) }

    val shortWindow = isShortWindow()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (editable) R.string.edit_timer else if (canCancel) R.string.new_timer else R.string.timer))
                },
                navigationIcon = {
                    if (canCancel) {
                        IconButton(onClick = closeEntry) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                },
                actions = {
                    if (!showEntry && shortWindow) {
                        IconButton(onClick = { adding = true }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_timer))
                        }
                    }
                    SettingsButton(AppSettings.getInstance(context))
                },
            )
        },
        floatingActionButton = {
            if (!showEntry && !shortWindow) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_timer))
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (showEntry && editable && editedTimer != null) {
                // Keyed, so its typed digits and label are its own, not a new timer's.
                key(editedTimer.id) {
                    TimerEntry(
                        initialMillis = editedTimer.durationMillis,
                        initialLabel = editedTimer.label,
                        confirmRes = R.string.save,
                        keypadLayout = keypadLayout,
                        onConfirm = { millis, label ->
                            Timer.edit(context, editedTimer.id, millis, label)
                            editing = null
                        },
                    )
                }
            } else if (showEntry) {
                TimerEntry(
                    initialMillis = lastDuration,
                    keypadLayout = keypadLayout,
                    onConfirm = { millis, label ->
                        Timer.add(context, millis, label)
                        adding = false
                    },
                )
            } else {
                TimerList(
                    timers = timers,
                    ringingTimerId = ringingTimerId,
                    onRename = { renaming = it },
                    onEdit = { editing = it },
                )
            }
        }
    }

    renaming?.let { id ->
        val timer = timers.firstOrNull { it.id == id }
        if (timer == null) {
            renaming = null
        } else {
            LabelDialog(
                initial = timer.label,
                onDismiss = { renaming = null },
                onConfirm = {
                    Timer.setLabel(context, id, it)
                    renaming = null
                },
            )
        }
    }
}

@Composable
private fun TimerList(timers: List<TimerState>, ringingTimerId: Int?, onRename: (Int) -> Unit, onEdit: (Int) -> Unit) {
    val context = LocalContext.current
    // One clock for all cards, ticking while any timer runs.
    val nowElapsed = rememberElapsedNow(ticking = timers.any { it.status == TimerStatus.RUNNING }, intervalMillis = 100)
    Column(
        Modifier
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            // Room at the bottom so the floating button doesn't cover the last card's buttons.
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        timers.forEach { timer ->
            TimerCard(
                timer = timer,
                nowElapsed = nowElapsed,
                ringing = timer.id == ringingTimerId,
                onRename = { onRename(timer.id) },
                onEdit = { onEdit(timer.id) }.takeIf { Timer.isEditable(timer) && timer.id != ringingTimerId },
                onStart = { Timer.start(context, timer.id) },
                onPause = { Timer.pause(context, timer.id) },
                onResume = { Timer.resume(context, timer.id) },
                onReset = { Timer.reset(context, timer.id) },
                onAddMinute = { Timer.addMinute(context, timer.id) },
                onStop = { AlarmScheduler.stopRinging(context) },
                onDelete = { Timer.delete(context, timer.id) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerCard(
    timer: TimerState,
    nowElapsed: Long,
    ringing: Boolean,
    onRename: () -> Unit,
    onEdit: (() -> Unit)?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onAddMinute: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    val remaining = timer.remaining(nowElapsed)
    // Screen readers name the timer by its label, or by its set time: "Eggs timer", "5:00 timer".
    val timerName = stringResource(R.string.timer_name, timer.label.ifBlank { formatDuration(timer.durationMillis) })
    Card(
        colors = if (ringing) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timer.label.ifBlank { stringResource(R.string.add_label) },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (timer.label.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = stringResource(R.string.label), onClick = onRename)
                        .padding(vertical = 12.dp),
                )
                // Only while the timer isn't running or ringing; tapping the time does the same.
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_named_timer, timerName))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete_named_alarm, timerName))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = {
                            when {
                                timer.status == TimerStatus.IDLE -> if (ringing) 0f else 1f
                                timer.totalMillis > 0 -> remaining.toFloat() / timer.totalMillis
                                else -> 0f
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        gapSize = 0.dp,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    if (onEdit != null) {
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = stringResource(R.string.edit_timer), onClick = onEdit)
                            .padding(horizontal = 4.dp)
                    } else {
                        Modifier
                    },
                ) {
                    Text(
                        if (ringing) stringResource(R.string.times_up) else formatDuration(remaining),
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 1,
                    )
                    if (timer.status == TimerStatus.PAUSED) {
                        Text(stringResource(R.string.paused), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val buttonModifier = Modifier.heightIn(min = 48.dp)
                when {
                    ringing -> {
                        OutlinedButton(onClick = onAddMinute, modifier = buttonModifier) { Text(stringResource(R.string.add_minute)) }
                        Button(onClick = onStop, modifier = buttonModifier.widthIn(min = 112.dp)) { Text(stringResource(R.string.stop)) }
                    }
                    timer.status == TimerStatus.IDLE -> {
                        Button(onClick = onStart, modifier = buttonModifier.widthIn(min = 112.dp)) { Text(stringResource(R.string.start)) }
                    }
                    else -> {
                        val running = timer.status == TimerStatus.RUNNING
                        OutlinedButton(onClick = onReset, modifier = buttonModifier) { Text(stringResource(R.string.reset)) }
                        OutlinedButton(onClick = onAddMinute, modifier = buttonModifier) { Text(stringResource(R.string.add_minute)) }
                        Button(onClick = if (running) onPause else onResume, modifier = buttonModifier.widthIn(min = 112.dp)) {
                            Text(stringResource(if (running) R.string.pause else R.string.resume))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LabelDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // Cursor at the end, and the keyboard up right away (like the typed-time dialog).
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label)) },
        text = {
            // In the dialog's own composition, so the field is attached when focus is requested.
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(field.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(field.text) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TimerEntry(
    initialMillis: Long,
    keypadLayout: KeypadLayout,
    initialLabel: String = "",
    confirmRes: Int = R.string.start,
    onConfirm: (millis: Long, label: String) -> Unit,
) {
    // Shows the last time used (or the edited timer's); typing replaces it (like the typed alarm time).
    var digits by rememberSaveable { mutableStateOf(millisToTimerDigits(initialMillis)) }
    var replaceOnType by rememberSaveable { mutableStateOf(true) }
    var label by rememberSaveable { mutableStateOf(initialLabel) }
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
        val labelField = @Composable {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            )
        }
        val display = @Composable {
            TimerEntryDisplay(digits, selected = replaceOnType)
        }
        val start = @Composable {
            Button(
                onClick = { onConfirm(millis, label) },
                enabled = millis > 0,
                modifier = Modifier.widthIn(min = 200.dp).height(56.dp),
            ) { Text(stringResource(confirmRes), style = MaterialTheme.typography.titleMedium) }
        }
        val keypad = @Composable { Keypad(keySize, keypadLayout, ::type, ::backspace) }
        if (wide) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    labelField()
                    Spacer(Modifier.height(16.dp))
                    display()
                    Spacer(Modifier.height(24.dp))
                    start()
                }
                keypad()
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                labelField()
                Spacer(Modifier.height(16.dp))
                display()
                Spacer(Modifier.height(16.dp))
                keypad()
                Spacer(Modifier.height(16.dp))
                start()
            }
        }
    }
}

/** "00h 05m 00s", digits large and units small; highlighted like selected text while the next key replaces it. */
@Composable
private fun TimerEntryDisplay(digits: String, selected: Boolean) {
    val d = digits.padStart(6, '0')
    val units = listOf(stringResource(R.string.hours_unit), stringResource(R.string.minutes_unit), stringResource(R.string.seconds_unit))
    val text = buildAnnotatedString {
        for (i in 0..2) {
            if (i > 0) append("  ")
            append(d.substring(i * 2, i * 2 + 2))
            withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleLarge.fontSize)) { append(units[i]) }
        }
    }
    Text(text, style = MaterialTheme.typography.displayMedium, maxLines = 1, modifier = Modifier.selectedHighlight(selected))
}
