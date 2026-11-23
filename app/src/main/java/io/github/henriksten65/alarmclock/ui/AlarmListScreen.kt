package io.github.henriksten65.alarmclock.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.henriksten65.alarmclock.Alarm
import io.github.henriksten65.alarmclock.AlarmHealth
import io.github.henriksten65.alarmclock.AlarmRepository
import io.github.henriksten65.alarmclock.AlarmScheduler
import io.github.henriksten65.alarmclock.AlarmService
import io.github.henriksten65.alarmclock.AlarmSound
import io.github.henriksten65.alarmclock.AppSettings
import io.github.henriksten65.alarmclock.R
import io.github.henriksten65.alarmclock.ThemeMode
import io.github.henriksten65.alarmclock.Timer
import io.github.henriksten65.alarmclock.TimeEntryMode
import io.github.henriksten65.alarmclock.dayAndTimeText
import io.github.henriksten65.alarmclock.formatTime
import io.github.henriksten65.alarmclock.timeUntilShortText
import io.github.henriksten65.alarmclock.repeatText
import io.github.henriksten65.alarmclock.timeUntilText
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val UNDO_TIMEOUT_MILLIS = 15_000L

/** The alarm list and the warning banners don't get wider than this (tablets, landscape). */
private val MAX_CONTENT_WIDTH = 640.dp

/** Below this window height (e.g. a phone in landscape) "Add alarm" moves from the floating button into the top bar. */
private val SHORT_WINDOW_HEIGHT = 480.dp

/** Keeps the alarm being edited across rotation and other configuration changes. */
internal val AlarmSaver = Saver<Alarm?, String>(
    save = { it?.toJson()?.toString() ?: "" },
    restore = { if (it.isEmpty()) null else Alarm.fromJson(JSONObject(it)) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(repository: AlarmRepository, settings: AppSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val alarms by repository.alarms.collectAsStateWithLifecycle()
    val timeEntryMode by settings.timeEntryMode.collectAsStateWithLifecycle()
    val keyboard24Hour by settings.keyboard24Hour.collectAsStateWithLifecycle()
    val defaultSoundUri by settings.alarmSoundUri.collectAsStateWithLifecycle()
    // Saveable, so an open dialog (and what was changed in it) survives rotating the phone.
    var editing by rememberSaveable(stateSaver = AlarmSaver) { mutableStateOf<Alarm?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val ringingAlarmId by AlarmService.ringingAlarmId.collectAsStateWithLifecycle()

    // Phone settings that can stop alarms from being heard, seen or dismissed.
    var canScheduleExact by remember { mutableStateOf(true) }
    var notificationsBlocked by remember { mutableStateOf(false) }
    var fullScreenBlocked by remember { mutableStateOf(false) }
    var volumeLow by remember { mutableStateOf(false) }
    var dndBlocksAlarms by remember { mutableStateOf(false) }
    fun recheck() {
        canScheduleExact = AlarmScheduler.canScheduleExactAlarms(context)
        notificationsBlocked = AlarmHealth.notificationsBlocked(context)
        fullScreenBlocked = AlarmHealth.fullScreenBlocked(context)
        volumeLow = AlarmHealth.alarmVolumeLow(context)
        dndBlocksAlarms = AlarmHealth.doNotDisturbBlocksAlarms(context)
    }

    // Re-check when returning from the system settings screens…
    LifecycleResumeEffect(Unit) {
        recheck()
        onPauseOrDispose { }
    }
    // …and when the volume or Do Not Disturb changes while the app is open.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = recheck()
        }
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // The current time, re-read every minute and whenever the alarms change (e.g. right after a
    // snooze), for the "Next alarm" summary and the snooze/skip lines on the cards.
    var minuteTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            // Wake just after the next minute boundary so the texts stay in step with the clock.
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            minuteTick++
        }
    }
    val now = remember(alarms, minuteTick) { System.currentTimeMillis() }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun stopIfRinging(alarmId: Int) {
        if (ringingAlarmId == alarmId) AlarmScheduler.stopRinging(context)
    }

    fun enableAndSchedule(alarm: Alarm) {
        val enabled = alarm.copy(enabled = true, snoozedUntil = 0L)
        repository.upsert(enabled)
        val message = if (AlarmScheduler.schedule(context, enabled)) {
            timeUntilText(context, enabled.nextRingMillis())
        } else {
            resources.getString(R.string.schedule_failed)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun addAlarm() {
        val now = Calendar.getInstance()
        editing = Alarm(
            id = repository.nextId(),
            hour = now.get(Calendar.HOUR_OF_DAY),
            minute = now.get(Calendar.MINUTE),
        )
    }

    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val shortWindow = windowHeight < SHORT_WINDOW_HEIGHT

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (shortWindow) {
                        IconButton(onClick = ::addAlarm) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_alarm))
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            // In a short window the floating button would cover much of the list, so it's in the top bar instead.
            if (!shortWindow) {
                FloatingActionButton(onClick = ::addAlarm) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_alarm))
                }
            }
        },
    ) { innerPadding ->
      // Centred and at most MAX_CONTENT_WIDTH wide, so cards don't stretch across a tablet.
      Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize()) {
            // (A ringing timer is shown on the Timer tab.)
            ringingAlarmId?.takeIf { it != Timer.RING_ID }?.let { id ->
                RingingCard(
                    alarm = alarms.firstOrNull { it.id == id },
                    onSnooze = { AlarmScheduler.snooze(context, id) },
                    onDismiss = { AlarmScheduler.stopRinging(context) },
                )
            }
            if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WarningBanner(R.string.exact_alarm_needed, R.string.allow) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData("package:${context.packageName}".toUri()),
                    )
                }
            }
            if (notificationsBlocked) {
                WarningBanner(R.string.notifications_blocked, R.string.allow) {
                    context.startActivity(AlarmHealth.notificationSettingsIntent(context))
                }
            } else if (fullScreenBlocked) {
                WarningBanner(R.string.full_screen_blocked, R.string.allow) {
                    context.startActivity(AlarmHealth.fullScreenSettingsIntent(context))
                }
            }
            if (dndBlocksAlarms) {
                WarningBanner(R.string.dnd_blocks_alarms, R.string.open_settings) {
                    context.startActivity(AlarmHealth.doNotDisturbSettingsIntent())
                }
            } else if (volumeLow) {
                WarningBanner(R.string.alarm_volume_low, R.string.turn_up) {
                    AlarmHealth.turnUpAlarmVolume(context)
                    recheck()
                }
            }

            if (alarms.isNotEmpty()) NextAlarmSummary(alarms, now)

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
                            now = now,
                            onClick = { editing = alarm },
                            onToggle = { checked ->
                                if (checked) {
                                    enableAndSchedule(alarm)
                                } else {
                                    repository.upsert(alarm.copy(enabled = false, snoozedUntil = 0L, skippedAt = 0L))
                                    AlarmScheduler.cancel(context, alarm.id)
                                    stopIfRinging(alarm.id)
                                }
                            },
                            onCancelSnooze = { AlarmScheduler.cancelSnooze(context, alarm.id) },
                            onSkipNext = {
                                AlarmScheduler.skipNext(context, alarm.id)?.let {
                                    Toast.makeText(context, timeUntilText(context, it.nextRingMillis()), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCancelSkip = {
                                AlarmScheduler.cancelSkip(context, alarm.id)?.let {
                                    Toast.makeText(context, timeUntilText(context, it.nextRingMillis()), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = {
                                AlarmScheduler.cancel(context, alarm.id)
                                repository.delete(alarm.id)
                                stopIfRinging(alarm.id)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    // Up for 15 s (a little longer than the standard "long" 10 s);
                                    // cancelling showSnackbar on timeout removes it.
                                    val result = withTimeoutOrNull(UNDO_TIMEOUT_MILLIS) {
                                        snackbarHostState.showSnackbar(
                                            message = resources.getString(R.string.alarm_deleted),
                                            actionLabel = resources.getString(R.string.undo),
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Indefinite,
                                        )
                                    }
                                    if (result == SnackbarResult.ActionPerformed) {
                                        // Restore exactly as it was, including on/off and any snooze.
                                        repository.upsert(alarm)
                                        if (alarm.enabled) AlarmScheduler.schedule(context, alarm)
                                    }
                                }
                            },
                        )
                    }
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
            defaultSoundUri = defaultSoundUri,
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

@OptIn(ExperimentalLayoutApi::class)
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
    val snoozeMinutes by settings.snoozeMinutes.collectAsStateWithLifecycle()
    val autoStopMinutes by settings.autoStopMinutes.collectAsStateWithLifecycle()
    val vibrate by settings.vibrate.collectAsStateWithLifecycle()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()

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
                // Wraps onto two lines when both buttons don't fit (narrow screens, large text).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { pickSystemSound.launch(AlarmSound.pickerIntent(context, alarmSoundUri)) }) {
                        Text(stringResource(R.string.phone_sounds))
                    }
                    OutlinedButton(onClick = { pickAudioFile.launch(arrayOf("audio/*")) }) {
                        Text(stringResource(R.string.audio_file))
                    }
                }
                Text(
                    stringResource(R.string.alarm_sound_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text(
                    stringResource(R.string.ringing),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MinutesSetting(R.string.snooze_length, snoozeMinutes, AppSettings.SNOOZE_MINUTE_OPTIONS, settings::setSnoozeMinutes)
                MinutesSetting(R.string.auto_stop, autoStopMinutes, AppSettings.AUTO_STOP_MINUTE_OPTIONS, settings::setAutoStopMinutes)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = vibrate, role = Role.Switch, onValueChange = settings::setVibrate),
                ) {
                    Text(stringResource(R.string.vibrate), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = vibrate, onCheckedChange = null)
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Column(Modifier.selectableGroup()) {
                    Text(
                        stringResource(R.string.appearance),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                    ).forEach { (mode, labelRes) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = mode == themeMode,
                                    role = Role.RadioButton,
                                    onClick = { settings.setThemeMode(mode) },
                                ),
                        ) {
                            RadioButton(selected = mode == themeMode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(labelRes))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

/** "Snooze length    10 min ▾": a label with a drop-down list of minute values. */
@Composable
private fun MinutesSetting(labelRes: Int, minutes: Int, options: List<Int>, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.minutes_short, minutes))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.minutes_short, option)) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** "Next alarm: tomorrow 07:30 · in 9 h 20 min" (or "No alarms on") as of [now]. */
@Composable
private fun NextAlarmSummary(alarms: List<Alarm>, now: Long) {
    val context = LocalContext.current
    val next = alarms.filter { it.enabled }.minOfOrNull { it.nextRingMillis(now) }
    Text(
        text = if (next == null) {
            stringResource(R.string.no_alarms_on)
        } else {
            stringResource(
                R.string.next_alarm,
                dayAndTimeText(context, next, now),
                timeUntilShortText(context, next, now),
            )
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
    )
}

@Composable
private fun WarningBanner(textRes: Int, actionRes: Int, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(textRes), Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(stringResource(actionRes)) }
        }
    }
}

/** Shown at the top of the list while an alarm rings, so it can always be stopped from the app. */
@Composable
private fun RingingCard(alarm: Alarm?, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            val name = alarm?.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.alarm)
            val time = alarm?.let { " · " + formatTime(context, it.hour, it.minute) }.orEmpty()
            Text(stringResource(R.string.ringing_now, name + time), style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Button(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                OutlinedButton(onClick = onSnooze) { Text(stringResource(R.string.snooze)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: Long,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCancelSnooze: () -> Unit,
    onSkipNext: () -> Unit,
    onCancelSkip: () -> Unit,
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
                if (alarm.enabled && alarm.isSnoozed(now)) {
                    // The button moves to its own line when the text is long (e.g. German, large text).
                    FlowRow {
                        Text(
                            stringResource(R.string.snoozed_until, formatTime(context, alarm.snoozedUntil)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        TextButton(onClick = onCancelSnooze) { Text(stringResource(R.string.cancel_snooze)) }
                    }
                }
                if (alarm.enabled && alarm.repeats) {
                    if (alarm.isSkipping(now)) {
                        FlowRow {
                            Text(
                                stringResource(R.string.skipping, dayAndTimeText(context, alarm.skippedAt, now)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                            TextButton(onClick = onCancelSkip) { Text(stringResource(R.string.dont_skip)) }
                        }
                    } else {
                        TextButton(onClick = onSkipNext, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(stringResource(R.string.skip_next))
                        }
                    }
                }
            }
            // Screen readers say which alarm the button and switch belong to, e.g. "06:30 Work alarm".
            val alarmName = stringResource(
                R.string.alarm_name,
                listOf(formatTime(context, alarm.hour, alarm.minute), alarm.label).filter { it.isNotBlank() }.joinToString(" "),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_named_alarm, alarmName))
            }
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = alarmName },
            )
        }
    }
}

@Composable
private fun EditAlarmDialog(
    initial: Alarm,
    isNew: Boolean,
    timeEntryMode: TimeEntryMode,
    keyboard24Hour: Boolean,
    defaultSoundUri: String?,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    var hour by rememberSaveable(initial.id) { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable(initial.id) { mutableIntStateOf(initial.minute) }
    var label by rememberSaveable(initial.id) { mutableStateOf(initial.label) }
    var days by rememberSaveable(initial.id) { mutableIntStateOf(initial.days) }
    var soundUri by rememberSaveable(initial.id) { mutableStateOf(initial.soundUri) }

    // A new alarm with typed times opens straight on the typed-time dialog, so the number
    // keyboard is up without tapping the time first. Saveable, so rotating doesn't reopen it.
    var pickingTime by rememberSaveable { mutableStateOf(isNew && timeEntryMode == TimeEntryMode.KEYBOARD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.new_alarm else R.string.edit_alarm)) },
        text = {
            // Scrolls when the window is short (landscape, large text), like the settings dialog.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                AlarmSoundChooser(soundUri, defaultSoundUri, onChange = { soundUri = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // A skip belongs to one occurrence of the old time/days; drop it if those change.
                val sameSchedule = hour == initial.hour && minute == initial.minute && days == initial.days
                onSave(
                    initial.copy(
                        hour = hour, minute = minute, label = label.trim(), days = days, soundUri = soundUri,
                        skippedAt = if (sameSchedule) initial.skippedAt else 0L,
                    ),
                )
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

/**
 * The alarm's own sound, or "Default (…)" for the sound from the settings, with a menu to pick
 * one of the phone's alarm sounds or an audio file, or to go back to the default.
 */
@Composable
private fun AlarmSoundChooser(soundUri: String?, defaultSoundUri: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var menuOpen by remember { mutableStateOf(false) }
    val title = remember(soundUri, defaultSoundUri, resources) {
        if (soundUri != null) {
            AlarmSound.title(context, soundUri)
        } else {
            resources.getString(R.string.sound_default, AlarmSound.title(context, defaultSoundUri))
        }
    }
    val pickSystemSound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            ?.let { onChange(AlarmSound.accept(context, it)) }
    }
    val pickAudioFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onChange(AlarmSound.accept(context, uri))
    }

    Column {
        Text(stringResource(R.string.sound), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { menuOpen = true }) { Text(stringResource(R.string.change)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.use_default_sound)) },
                        enabled = soundUri != null,
                        onClick = {
                            menuOpen = false
                            onChange(null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.phone_sounds)) },
                        onClick = {
                            menuOpen = false
                            // No "Default" entry here: "Default" for an alarm means the settings' sound.
                            pickSystemSound.launch(AlarmSound.pickerIntent(context, soundUri ?: defaultSoundUri, showDefault = false))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.audio_file)) },
                        onClick = {
                            menuOpen = false
                            pickAudioFile.launch(arrayOf("audio/*"))
                        },
                    )
                }
            }
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
