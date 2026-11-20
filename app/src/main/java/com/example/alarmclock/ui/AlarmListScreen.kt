package com.example.alarmclock.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarmclock.Alarm
import com.example.alarmclock.AlarmRepository
import com.example.alarmclock.AlarmScheduler
import com.example.alarmclock.AlarmSound
import com.example.alarmclock.AppSettings
import com.example.alarmclock.R
import com.example.alarmclock.TimeEntryMode
import com.example.alarmclock.formatTime
import com.example.alarmclock.repeatText
import com.example.alarmclock.timeUntilText
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(repository: AlarmRepository, settings: AppSettings) {
    val context = LocalContext.current
    val alarms by repository.alarms.collectAsStateWithLifecycle()
    val timeEntryMode by settings.timeEntryMode.collectAsStateWithLifecycle()
    val keyboard24Hour by settings.keyboard24Hour.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var canScheduleExact by remember { mutableStateOf(AlarmScheduler.canScheduleExactAlarms(context)) }

    // Re-check the permission when returning from the system settings screen.
    LifecycleResumeEffect(Unit) {
        canScheduleExact = AlarmScheduler.canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }

    fun enableAndSchedule(alarm: Alarm) {
        val enabled = alarm.copy(enabled = true)
        repository.upsert(enabled)
        val message = if (AlarmScheduler.schedule(context, enabled)) {
            timeUntilText(context, enabled.nextTriggerMillis())
        } else {
            context.getString(R.string.schedule_failed)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val now = Calendar.getInstance()
                editing = Alarm(
                    id = repository.nextId(),
                    hour = now.get(Calendar.HOUR_OF_DAY),
                    minute = now.get(Calendar.MINUTE),
                )
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_alarm))
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ExactAlarmBanner(onAllow = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData("package:${context.packageName}".toUri()),
                    )
                })
            }

            if (alarms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_alarms),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onClick = { editing = alarm },
                            onToggle = { checked ->
                                if (checked) {
                                    enableAndSchedule(alarm)
                                } else {
                                    repository.upsert(alarm.copy(enabled = false))
                                    AlarmScheduler.cancel(context, alarm.id)
                                }
                            },
                            onDelete = {
                                AlarmScheduler.cancel(context, alarm.id)
                                repository.delete(alarm.id)
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { alarm ->
        EditAlarmDialog(
            initial = alarm,
            isNew = repository.get(alarm.id) == null,
            timeEntryMode = timeEntryMode,
            keyboard24Hour = keyboard24Hour,
            onDismiss = { editing = null },
            onSave = {
                enableAndSchedule(it)
                editing = null
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            timeEntryMode = timeEntryMode,
            onTimeEntryModeChange = settings::setTimeEntryMode,
            keyboard24Hour = keyboard24Hour,
            onKeyboard24HourChange = settings::setKeyboard24Hour,
            settings = settings,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun SettingsDialog(
    timeEntryMode: TimeEntryMode,
    onTimeEntryModeChange: (TimeEntryMode) -> Unit,
    keyboard24Hour: Boolean,
    onKeyboard24HourChange: (Boolean) -> Unit,
    settings: AppSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val alarmSoundUri by settings.alarmSoundUri.collectAsStateWithLifecycle()
    val soundTitle = remember(alarmSoundUri) { AlarmSound.title(context, alarmSoundUri) }

    // One of the phone's alarm sounds, via the system sound picker.
    val pickSystemSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        AlarmSound.choose(context, settings, uri)
    }
    // Any audio file on the phone, via the document picker.
    val pickAudioFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) AlarmSound.choose(context, settings, uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
              Column(Modifier.selectableGroup()) {
                Text(
                    stringResource(R.string.time_entry),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                listOf(
                    TimeEntryMode.DIAL to R.string.time_entry_dial,
                    TimeEntryMode.KEYBOARD to R.string.time_entry_keyboard,
                ).forEach { (mode, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = mode == timeEntryMode,
                                role = Role.RadioButton,
                                onClick = { onTimeEntryModeChange(mode) },
                            ),
                    ) {
                        RadioButton(selected = mode == timeEntryMode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(labelRes))
                    }
                }
                // Only meaningful for typed times, so it's greyed out while the dial is chosen.
                val keyboardMode = timeEntryMode == TimeEntryMode.KEYBOARD
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = 36.dp)
                        .toggleable(
                            value = keyboard24Hour,
                            enabled = keyboardMode,
                            role = Role.Switch,
                            onValueChange = onKeyboard24HourChange,
                        ),
                ) {
                    Text(
                        stringResource(R.string.keyboard_24_hour),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (keyboardMode) 1f else 0.38f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = keyboard24Hour, onCheckedChange = null, enabled = keyboardMode)
                }
              }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text(
                    stringResource(R.string.alarm_sound),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(soundTitle, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { pickSystemSound.launch(AlarmSound.pickerIntent(context, alarmSoundUri)) }) {
                        Text(stringResource(R.string.phone_sounds))
                    }
                    OutlinedButton(onClick = { pickAudioFile.launch(arrayOf("audio/*")) }) {
                        Text(stringResource(R.string.audio_file))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
private fun ExactAlarmBanner(onAllow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.exact_alarm_needed), Modifier.weight(1f))
            TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val contentAlpha = if (alarm.enabled) 1f else 0.5f
    val subtitle = listOf(alarm.label, repeatText(context, alarm.days)).filter { it.isNotBlank() }.joinToString(" · ")

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatTime(context, alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_alarm))
            }
            Spacer(Modifier.width(4.dp))
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun EditAlarmDialog(
    initial: Alarm,
    isNew: Boolean,
    timeEntryMode: TimeEntryMode,
    keyboard24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    var hour by remember(initial.id) { mutableIntStateOf(initial.hour) }
    var minute by remember(initial.id) { mutableIntStateOf(initial.minute) }
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var days by remember(initial.id) { mutableIntStateOf(initial.days) }

    var pickingTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.new_alarm else R.string.edit_alarm)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(formatTime(context, hour, minute), style = MaterialTheme.typography.displayMedium)
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleSmall)
                    DaySelector(days = days, onDaysChange = { days = it })
                    Text(
                        repeatText(context, days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(hour = hour, minute = minute, label = label.trim(), days = days))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (pickingTime) {
        val onConfirm = { h: Int, m: Int ->
            hour = h
            minute = m
            pickingTime = false
        }
        when (timeEntryMode) {
            TimeEntryMode.DIAL -> TimePickerDialog(hour, minute, onDismiss = { pickingTime = false }, onConfirm = onConfirm)
            TimeEntryMode.KEYBOARD -> KeyboardTimeDialog(hour, minute, keyboard24Hour, onDismiss = { pickingTime = false }, onConfirm = onConfirm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
