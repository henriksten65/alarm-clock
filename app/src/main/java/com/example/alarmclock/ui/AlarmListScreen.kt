package com.example.alarmclock.ui

import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarmclock.Alarm
import com.example.alarmclock.AlarmRepository
import com.example.alarmclock.AlarmScheduler
import com.example.alarmclock.R
import com.example.alarmclock.formatTime
import com.example.alarmclock.timeUntilText
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(repository: AlarmRepository) {
    val context = LocalContext.current
    val alarms by repository.alarms.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Alarm?>(null) }
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
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
            onDismiss = { editing = null },
            onSave = {
                enableAndSchedule(it)
                editing = null
            },
        )
    }
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
    val repeatText = stringResource(if (alarm.repeatDaily) R.string.every_day else R.string.once)
    val subtitle = listOf(alarm.label, repeatText).filter { it.isNotBlank() }.joinToString(" · ")

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
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    var hour by remember(initial.id) { mutableIntStateOf(initial.hour) }
    var minute by remember(initial.id) { mutableIntStateOf(initial.minute) }
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var repeatDaily by remember(initial.id) { mutableStateOf(initial.repeatDaily) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = repeatDaily,
                            role = Role.Checkbox,
                            onValueChange = { repeatDaily = it },
                        ),
                ) {
                    Checkbox(checked = repeatDaily, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.repeat_daily))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(hour = hour, minute = minute, label = label.trim(), repeatDaily = repeatDaily))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (pickingTime) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { pickingTime = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
                pickingTime = false
            },
        )
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
