package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerStatus { IDLE, RUNNING, PAUSED }

/**
 * The countdown timer. [durationMillis] is what the user set (kept for next time);
 * [totalMillis] is that plus any "+1 min", for the progress ring.
 *
 * While RUNNING it ends at [endElapsed] ([SystemClock.elapsedRealtime], so changing the clock
 * doesn't affect it); [endWall] is the same moment on the wall clock, for the notification and
 * for after a reboot, when elapsed time starts again from 0 ([bootCount] tells).
 * While PAUSED, [remainingMillis] is what's left.
 */
data class TimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    val durationMillis: Long = 5 * 60_000L,
    val totalMillis: Long = 0L,
    val endElapsed: Long = 0L,
    val endWall: Long = 0L,
    val bootCount: Int = 0,
    val remainingMillis: Long = 0L,
) {
    /** Time left now (RUNNING / PAUSED), never negative. */
    fun remaining(nowElapsed: Long = SystemClock.elapsedRealtime()): Long = when (status) {
        TimerStatus.RUNNING -> (endElapsed - nowElapsed).coerceAtLeast(0L)
        TimerStatus.PAUSED -> remainingMillis
        TimerStatus.IDLE -> durationMillis
    }
}

/** Starts, pauses and ends the timer; keeps its AlarmManager alarm and notification in step. */
object Timer {

    /** Used as the "alarm id" while the timer rings in [AlarmService]. */
    const val RING_ID = -100

    const val MAX_MILLIS = (99 * 3600L + 59 * 60L + 59L) * 1000L

    private val _state = MutableStateFlow(TimerState())
    private var loaded = false

    /** The timer's state, read from storage the first time. */
    fun state(context: Context): StateFlow<TimerState> {
        if (!loaded) {
            _state.value = load(context)
            loaded = true
        }
        return _state.asStateFlow()
    }

    fun current(context: Context): TimerState = state(context).value

    fun start(context: Context, durationMillis: Long) {
        val duration = durationMillis.coerceIn(1_000L, MAX_MILLIS)
        stopIfRinging(context)
        run(context, current(context).copy(durationMillis = duration, totalMillis = duration), duration)
    }

    fun pause(context: Context) {
        val state = current(context)
        if (state.status != TimerStatus.RUNNING) return
        cancelAlarm(context)
        save(context, state.copy(status = TimerStatus.PAUSED, remainingMillis = state.remaining()))
    }

    fun resume(context: Context) {
        val state = current(context)
        if (state.status != TimerStatus.PAUSED) return
        run(context, state, state.remainingMillis)
    }

    fun reset(context: Context) {
        cancelAlarm(context)
        stopIfRinging(context)
        save(context, current(context).copy(status = TimerStatus.IDLE, totalMillis = 0L, remainingMillis = 0L))
    }

    /** +1 min: on a running or paused timer, or one that is ringing (it starts again with 1 min). */
    fun addMinute(context: Context) {
        val state = current(context)
        when (state.status) {
            TimerStatus.RUNNING -> run(context, state.copy(totalMillis = state.totalMillis + 60_000L), state.remaining() + 60_000L)
            TimerStatus.PAUSED -> save(
                context,
                state.copy(totalMillis = state.totalMillis + 60_000L, remainingMillis = state.remainingMillis + 60_000L),
            )
            TimerStatus.IDLE -> if (AlarmService.ringingAlarmId.value == RING_ID) {
                stopIfRinging(context)
                run(context, state.copy(totalMillis = 60_000L), 60_000L)
            }
        }
    }

    /** The AlarmManager alarm went off: ring. */
    fun onDone(context: Context) {
        val state = current(context)
        if (state.status != TimerStatus.RUNNING) return
        save(context, state.copy(status = TimerStatus.IDLE, totalMillis = 0L))
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).putExtra(EXTRA_ALARM_ID, RING_ID),
        )
    }

    /**
     * After a reboot (AlarmManager forgot the alarm, elapsed time restarted) or a clock change
     * (the wall-clock end time moved): set everything up again. A timer that ended while the
     * phone was off rings now if it's less than the "stop ringing after" time late.
     */
    fun restore(context: Context) {
        val state = current(context)
        when (state.status) {
            TimerStatus.IDLE -> hideNotification(context)
            TimerStatus.PAUSED -> showNotification(context, state)
            TimerStatus.RUNNING -> if (state.bootCount == bootCount(context)) {
                // Same boot: elapsed time is still right; only the wall-clock end may have moved.
                run(context, state, state.remaining())
            } else {
                val left = state.endWall - System.currentTimeMillis()
                val ringLateWindow = AppSettings.getInstance(context).autoStopMinutes.value * 60_000L
                when {
                    left > 0 -> run(context, state, left)
                    -left < ringLateWindow -> {
                        save(context, state.copy(bootCount = bootCount(context)))
                        onDone(context)
                    }
                    else -> reset(context)
                }
            }
        }
    }

    private fun run(context: Context, state: TimerState, remaining: Long) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val updated = state.copy(
            status = TimerStatus.RUNNING,
            endElapsed = nowElapsed + remaining,
            endWall = System.currentTimeMillis() + remaining,
            bootCount = bootCount(context),
            remainingMillis = 0L,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val done = doneIntent(context)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, updated.endElapsed, done)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, updated.endElapsed, done)
        }
        save(context, updated)
    }

    private fun stopIfRinging(context: Context) {
        if (AlarmService.ringingAlarmId.value == RING_ID) AlarmScheduler.stopRinging(context)
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(doneIntent(context))
    }

    private fun save(context: Context, state: TimerState) {
        prefs(context).edit {
            putString(KEY_STATUS, state.status.name)
            putLong(KEY_DURATION, state.durationMillis)
            putLong(KEY_TOTAL, state.totalMillis)
            putLong(KEY_END_ELAPSED, state.endElapsed)
            putLong(KEY_END_WALL, state.endWall)
            putInt(KEY_BOOT_COUNT, state.bootCount)
            putLong(KEY_REMAINING, state.remainingMillis)
        }
        _state.value = state
        if (state.status == TimerStatus.IDLE) hideNotification(context) else showNotification(context, state)
    }

    private fun load(context: Context): TimerState {
        val p = prefs(context)
        return TimerState(
            status = p.getString(KEY_STATUS, null)?.let { s -> TimerStatus.entries.firstOrNull { it.name == s } } ?: TimerStatus.IDLE,
            durationMillis = p.getLong(KEY_DURATION, TimerState().durationMillis),
            totalMillis = p.getLong(KEY_TOTAL, 0L),
            endElapsed = p.getLong(KEY_END_ELAPSED, 0L),
            endWall = p.getLong(KEY_END_WALL, 0L),
            bootCount = p.getInt(KEY_BOOT_COUNT, 0),
            remainingMillis = p.getLong(KEY_REMAINING, 0L),
        )
    }

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    // --- Notification ---

    /** A countdown in the status bar while running; "Timer paused · 4:32" while paused. */
    private fun showNotification(context: Context, state: TimerState) {
        createNotificationChannel(context)
        val running = state.status == TimerStatus.RUNNING
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openTimerTab(context))
        if (running) {
            builder.setContentTitle(context.getString(R.string.timer))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(state.endWall)
                .setShowWhen(true)
                .addAction(0, context.getString(R.string.pause), action(context, TimerReceiver.ACTION_PAUSE))
        } else {
            builder.setContentTitle(context.getString(R.string.timer_paused))
                .setContentText(formatDuration(state.remainingMillis))
                .setShowWhen(false)
                .addAction(0, context.getString(R.string.resume), action(context, TimerReceiver.ACTION_RESUME))
        }
        builder.addAction(0, context.getString(R.string.add_minute), action(context, TimerReceiver.ACTION_ADD_MINUTE))
            .addAction(0, context.getString(R.string.reset), action(context, TimerReceiver.ACTION_RESET))
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notifications are off; the app already shows a banner about that.
        }
    }

    private fun hideNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Default importance (so the icon shows in the status bar), but silent. */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.timer))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun openTimerTab(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_TIMER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun doneIntent(context: Context) = action(context, TimerReceiver.ACTION_DONE)

    private fun prefs(context: Context) = deviceProtectedPrefs(context, "timer")

    const val CHANNEL_ID = "timer"
    private const val NOTIFICATION_ID = 3000
    private const val KEY_STATUS = "status"
    private const val KEY_DURATION = "duration"
    private const val KEY_TOTAL = "total"
    private const val KEY_END_ELAPSED = "end_elapsed"
    private const val KEY_END_WALL = "end_wall"
    private const val KEY_BOOT_COUNT = "boot_count"
    private const val KEY_REMAINING = "remaining"
}

/** "1:05:09", "5:09" or "0:09"; rounds up to whole seconds, so a running timer shows 0:00 only at the end. */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
    val h = totalSeconds / 3600
    val m = totalSeconds / 60 % 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
