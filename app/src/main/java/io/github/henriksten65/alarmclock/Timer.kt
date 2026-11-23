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
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class TimerStatus { IDLE, RUNNING, PAUSED }

/**
 * One countdown timer. [durationMillis] is what the user set (a finished or reset timer stays
 * in the list with it, ready to start again); [totalMillis] is that plus any "+1 min", for the
 * progress ring.
 *
 * While RUNNING it ends at [endElapsed] ([SystemClock.elapsedRealtime], so changing the clock
 * doesn't affect it); [endWall] is the same moment on the wall clock, for the notification and
 * for after a reboot, when elapsed time starts again from 0 ([bootCount] tells).
 * While PAUSED, [remainingMillis] is what's left.
 */
data class TimerState(
    val id: Int = 0,
    val label: String = "",
    val status: TimerStatus = TimerStatus.IDLE,
    val durationMillis: Long = 5 * 60_000L,
    val totalMillis: Long = 0L,
    val endElapsed: Long = 0L,
    val endWall: Long = 0L,
    val bootCount: Int = 0,
    val remainingMillis: Long = 0L,
) {
    /** Time left now (RUNNING / PAUSED), never negative; the set time when IDLE. */
    fun remaining(nowElapsed: Long = SystemClock.elapsedRealtime()): Long = when (status) {
        TimerStatus.RUNNING -> (endElapsed - nowElapsed).coerceAtLeast(0L)
        TimerStatus.PAUSED -> remainingMillis
        TimerStatus.IDLE -> durationMillis
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("status", status.name)
        .put("duration", durationMillis)
        .put("total", totalMillis)
        .put("endElapsed", endElapsed)
        .put("endWall", endWall)
        .put("bootCount", bootCount)
        .put("remaining", remainingMillis)

    companion object {
        fun fromJson(o: JSONObject) = TimerState(
            id = o.getInt("id"),
            label = o.optString("label"),
            status = TimerStatus.entries.firstOrNull { it.name == o.optString("status") } ?: TimerStatus.IDLE,
            durationMillis = o.optLong("duration", TimerState().durationMillis),
            totalMillis = o.optLong("total"),
            endElapsed = o.optLong("endElapsed"),
            endWall = o.optLong("endWall"),
            bootCount = o.optInt("bootCount"),
            remainingMillis = o.optLong("remaining"),
        )
    }
}

/**
 * The countdown timers: add, start, pause, end and delete them; keeps each one's AlarmManager
 * alarm and status-bar notification in step.
 */
object Timer {

    /** A ringing timer's "alarm id" in [AlarmService]: [RING_ID_BASE] − timer id (alarms have ids ≥ 1). */
    private const val RING_ID_BASE = -1000

    fun ringId(timerId: Int) = RING_ID_BASE - timerId

    /** The timer behind a ringing id, or null when an alarm (or nothing) rings. */
    fun timerIdOf(ringId: Int?): Int? = ringId?.takeIf { it <= RING_ID_BASE }?.let { RING_ID_BASE - it }

    const val MAX_MILLIS = (99 * 3600L + 59 * 60L + 59L) * 1000L

    private val _timers = MutableStateFlow<List<TimerState>>(emptyList())
    private val _lastDuration = MutableStateFlow(TimerState().durationMillis)
    private var loaded = false

    /** All timers, oldest first, read from storage the first time. */
    fun timers(context: Context): StateFlow<List<TimerState>> {
        load(context)
        return _timers.asStateFlow()
    }

    /** The time last set on the keypad, shown there for the next new timer. */
    fun lastDuration(context: Context): StateFlow<Long> {
        load(context)
        return _lastDuration.asStateFlow()
    }

    fun get(context: Context, id: Int): TimerState? = timers(context).value.firstOrNull { it.id == id }

    /** A new timer for [durationMillis], started right away. Returns its id. */
    fun add(context: Context, durationMillis: Long, label: String = ""): Int {
        load(context)
        val duration = durationMillis.coerceIn(1_000L, MAX_MILLIS)
        val p = prefs(context)
        val id = maxOf(p.getInt(KEY_LAST_ID, 0), _timers.value.maxOfOrNull { it.id } ?: 0) + 1
        _lastDuration.value = duration
        p.edit {
            putInt(KEY_LAST_ID, id)
            putLong(KEY_LAST_DURATION, duration)
        }
        run(context, TimerState(id = id, label = label.trim(), durationMillis = duration, totalMillis = duration), duration)
        return id
    }

    /** Starts a finished or reset timer again with its set time. */
    fun start(context: Context, id: Int) {
        val state = get(context, id) ?: return
        stopIfRinging(context, id)
        run(context, state.copy(totalMillis = state.durationMillis), state.durationMillis)
    }

    fun pause(context: Context, id: Int) {
        val state = get(context, id) ?: return
        if (state.status != TimerStatus.RUNNING) return
        cancelAlarm(context, id)
        save(context, state.copy(status = TimerStatus.PAUSED, remainingMillis = state.remaining()))
    }

    fun resume(context: Context, id: Int) {
        val state = get(context, id) ?: return
        if (state.status != TimerStatus.PAUSED) return
        run(context, state, state.remainingMillis)
    }

    /** Back to its set time, not running; it stays in the list. */
    fun reset(context: Context, id: Int) {
        val state = get(context, id) ?: return
        cancelAlarm(context, id)
        stopIfRinging(context, id)
        save(context, state.copy(status = TimerStatus.IDLE, totalMillis = 0L, remainingMillis = 0L))
    }

    fun delete(context: Context, id: Int) {
        load(context)
        cancelAlarm(context, id)
        stopIfRinging(context, id)
        hideNotification(context, id)
        store(context, _timers.value.filterNot { it.id == id })
    }

    fun setLabel(context: Context, id: Int, label: String) {
        val state = get(context, id) ?: return
        save(context, state.copy(label = label.trim()))
    }

    /** +1 min: on a running or paused timer, or one that is ringing (it starts again with 1 min). */
    fun addMinute(context: Context, id: Int) {
        val state = get(context, id) ?: return
        when (state.status) {
            TimerStatus.RUNNING -> run(context, state.copy(totalMillis = state.totalMillis + 60_000L), state.remaining() + 60_000L)
            TimerStatus.PAUSED -> save(
                context,
                state.copy(totalMillis = state.totalMillis + 60_000L, remainingMillis = state.remainingMillis + 60_000L),
            )
            TimerStatus.IDLE -> if (isRinging(id)) {
                stopIfRinging(context, id)
                run(context, state.copy(totalMillis = 60_000L), 60_000L)
            }
        }
    }

    fun isRinging(id: Int) = timerIdOf(AlarmService.ringingAlarmId.value) == id

    /** The timer's AlarmManager alarm went off: ring. */
    fun onDone(context: Context, id: Int) {
        val state = get(context, id) ?: return
        if (state.status != TimerStatus.RUNNING) return
        save(context, state.copy(status = TimerStatus.IDLE, totalMillis = 0L))
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).putExtra(EXTRA_ALARM_ID, ringId(id)),
        )
    }

    /**
     * After a reboot (AlarmManager forgot the alarms, elapsed time restarted) or a clock change
     * (the wall-clock end times moved): set everything up again. A timer that ended while the
     * phone was off rings now if it's less than the "stop ringing after" time late.
     */
    fun restore(context: Context) {
        for (state in timers(context).value) {
            when (state.status) {
                TimerStatus.IDLE -> hideNotification(context, state.id)
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
                            onDone(context, state.id)
                        }
                        else -> reset(context, state.id)
                    }
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
        val done = action(context, TimerReceiver.ACTION_DONE, state.id)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, updated.endElapsed, done)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, updated.endElapsed, done)
        }
        save(context, updated)
    }

    private fun stopIfRinging(context: Context, id: Int) {
        if (isRinging(id)) AlarmScheduler.stopRinging(context)
    }

    private fun cancelAlarm(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(action(context, TimerReceiver.ACTION_DONE, id))
    }

    /** Puts [state] in place of the timer with its id (or adds it), saves, updates the notification. */
    private fun save(context: Context, state: TimerState) {
        load(context)
        val list = _timers.value
        store(context, if (list.any { it.id == state.id }) list.map { if (it.id == state.id) state else it } else list + state)
        if (state.status == TimerStatus.IDLE) hideNotification(context, state.id) else showNotification(context, state)
    }

    @Synchronized
    private fun store(context: Context, list: List<TimerState>) {
        _timers.value = list
        prefs(context).edit { putString(KEY_TIMERS, JSONArray(list.map { it.toJson() }).toString()) }
    }

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        _lastDuration.value = p.getLong(KEY_LAST_DURATION, TimerState().durationMillis)
        _timers.value = p.getString(KEY_TIMERS, null)?.let { raw ->
            try {
                val array = JSONArray(raw)
                List(array.length()) { TimerState.fromJson(array.getJSONObject(it)) }
            } catch (e: JSONException) {
                emptyList()
            }
        } ?: migrateSingleTimer(context)
        loaded = true
    }

    /**
     * Version 3.0 had one timer, saved as single values. A running or paused one becomes
     * timer 1; its set time is kept for the keypad either way.
     */
    private fun migrateSingleTimer(context: Context): List<TimerState> {
        val p = prefs(context)
        val status = p.getString("status", null)?.let { s -> TimerStatus.entries.firstOrNull { it.name == s } }
            ?: return emptyList()
        val list = if (status == TimerStatus.IDLE) {
            emptyList()
        } else {
            listOf(
                TimerState(
                    id = 1,
                    status = status,
                    durationMillis = p.getLong(KEY_LAST_DURATION, TimerState().durationMillis),
                    totalMillis = p.getLong("total", 0L),
                    endElapsed = p.getLong("end_elapsed", 0L),
                    endWall = p.getLong("end_wall", 0L),
                    bootCount = p.getInt("boot_count", 0),
                    remainingMillis = p.getLong("remaining", 0L),
                ),
            )
        }
        p.edit {
            listOf("status", "total", "end_elapsed", "end_wall", "boot_count", "remaining").forEach(::remove)
            putString(KEY_TIMERS, JSONArray(list.map { it.toJson() }).toString())
            if (list.isNotEmpty()) putInt(KEY_LAST_ID, 1)
        }
        // The old timer's alarm and notification; restore() sets up timer 1's own.
        context.getSystemService(AlarmManager::class.java).cancel(
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_DONE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE)
        return list
    }

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    // --- Notifications ---

    /** "Eggs" (or "Timer") with a countdown in the status bar while running; "Eggs paused · 4:32" while paused. */
    private fun showNotification(context: Context, state: TimerState) {
        createNotificationChannel(context)
        val running = state.status == TimerStatus.RUNNING
        val name = state.label.ifBlank { context.getString(R.string.timer) }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setSortKey("%010d".format(state.id))
            .setContentIntent(openTimerTab(context))
        if (running) {
            builder.setContentTitle(name)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(state.endWall)
                .setShowWhen(true)
                .addAction(0, context.getString(R.string.pause), action(context, TimerReceiver.ACTION_PAUSE, state.id))
        } else {
            builder.setContentTitle(
                if (state.label.isBlank()) context.getString(R.string.timer_paused) else context.getString(R.string.named_timer_paused, state.label),
            )
                .setContentText(formatDuration(state.remainingMillis))
                .setShowWhen(false)
                .addAction(0, context.getString(R.string.resume), action(context, TimerReceiver.ACTION_RESUME, state.id))
        }
        builder.addAction(0, context.getString(R.string.add_minute), action(context, TimerReceiver.ACTION_ADD_MINUTE, state.id))
            .addAction(0, context.getString(R.string.reset), action(context, TimerReceiver.ACTION_RESET, state.id))
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + state.id, builder.build())
        } catch (e: SecurityException) {
            // Notifications are off; the app already shows a banner about that.
        }
    }

    private fun hideNotification(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE + id)
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

    /** A broadcast to [TimerReceiver] for one timer; the data URI keeps each timer's intents apart. */
    fun action(context: Context, action: String, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, TimerReceiver::class.java)
            .setAction(action)
            .setData("timer://$id".toUri())
            .putExtra(TimerReceiver.EXTRA_TIMER_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun prefs(context: Context) = deviceProtectedPrefs(context, "timer")

    const val CHANNEL_ID = "timer"
    private const val NOTIFICATION_ID_BASE = 3000
    private const val KEY_TIMERS = "timers"
    private const val KEY_LAST_ID = "last_id"
    /** Also the old single timer's set time, so it carries over. */
    private const val KEY_LAST_DURATION = "duration"
}

/** "1:05:09", "5:09" or "0:09"; rounds up to whole seconds, so a running timer shows 0:00 only at the end. */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
    val h = totalSeconds / 3600
    val m = totalSeconds / 60 % 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
