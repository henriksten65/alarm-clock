package io.github.henriksten65.alarmclock

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class StopwatchStatus { IDLE, RUNNING, PAUSED }

/**
 * The stopwatch. While RUNNING it started (counting any earlier runs before a pause) at
 * [startElapsed] ([SystemClock.elapsedRealtime], so clock changes don't affect it);
 * [startWall] is the same moment on the wall clock, for after a reboot, when elapsed time
 * starts again from 0 ([bootCount] tells). While PAUSED, [pausedMillis] is the time so far.
 * [laps] are the total times at each "Lap", oldest first.
 */
data class StopwatchState(
    val status: StopwatchStatus = StopwatchStatus.IDLE,
    val startElapsed: Long = 0L,
    val startWall: Long = 0L,
    val bootCount: Int = 0,
    val pausedMillis: Long = 0L,
    val laps: List<Long> = emptyList(),
) {
    fun elapsed(nowElapsed: Long = SystemClock.elapsedRealtime()): Long = when (status) {
        StopwatchStatus.RUNNING -> (nowElapsed - startElapsed).coerceAtLeast(0L)
        StopwatchStatus.PAUSED -> pausedMillis
        StopwatchStatus.IDLE -> 0L
    }

    fun toJson(): JSONObject = JSONObject()
        .put("status", status.name)
        .put("startElapsed", startElapsed)
        .put("startWall", startWall)
        .put("bootCount", bootCount)
        .put("paused", pausedMillis)
        .put("laps", JSONArray(laps))

    companion object {
        fun fromJson(o: JSONObject): StopwatchState {
            val laps = o.optJSONArray("laps")
            return StopwatchState(
                status = StopwatchStatus.entries.firstOrNull { it.name == o.optString("status") } ?: StopwatchStatus.IDLE,
                startElapsed = o.optLong("startElapsed"),
                startWall = o.optLong("startWall"),
                bootCount = o.optInt("bootCount"),
                pausedMillis = o.optLong("paused"),
                laps = if (laps == null) emptyList() else List(laps.length()) { laps.getLong(it) },
            )
        }
    }
}

/** The one stopwatch: start, pause, resume, lap, reset; keeps its status-bar notification in step. */
object Stopwatch {

    /** At most this many laps are kept (the oldest go first). */
    const val MAX_LAPS = 999

    private val _state = MutableStateFlow(StopwatchState())
    private var loaded = false

    fun state(context: Context): StateFlow<StopwatchState> {
        load(context)
        return _state.asStateFlow()
    }

    fun start(context: Context) {
        val state = state(context).value
        if (state.status != StopwatchStatus.IDLE) return
        run(context, StopwatchState(), 0L)
    }

    fun pause(context: Context) {
        val state = state(context).value
        if (state.status != StopwatchStatus.RUNNING) return
        save(context, state.copy(status = StopwatchStatus.PAUSED, pausedMillis = state.elapsed()))
    }

    fun resume(context: Context) {
        val state = state(context).value
        if (state.status != StopwatchStatus.PAUSED) return
        run(context, state, state.pausedMillis)
    }

    /** Notes the time so far; only while running. */
    fun lap(context: Context) {
        val state = state(context).value
        if (state.status != StopwatchStatus.RUNNING) return
        save(context, state.copy(laps = (state.laps + state.elapsed()).takeLast(MAX_LAPS)))
    }

    fun reset(context: Context) {
        load(context)
        save(context, StopwatchState())
    }

    /**
     * After a reboot (elapsed time restarted from 0) the time so far comes from the wall clock;
     * after a clock change (same boot) elapsed time is still right, only the notification moves.
     */
    fun restore(context: Context) {
        val state = state(context).value
        when (state.status) {
            StopwatchStatus.IDLE -> hideNotification(context)
            StopwatchStatus.PAUSED -> save(context, state)
            StopwatchStatus.RUNNING -> {
                val soFar = if (state.bootCount == bootCount(context)) {
                    state.elapsed()
                } else {
                    (System.currentTimeMillis() - state.startWall).coerceAtLeast(0L)
                }
                run(context, state, soFar)
            }
        }
    }

    /** Running, with [soFar] already counted. */
    private fun run(context: Context, state: StopwatchState, soFar: Long) {
        save(
            context,
            state.copy(
                status = StopwatchStatus.RUNNING,
                startElapsed = SystemClock.elapsedRealtime() - soFar,
                startWall = System.currentTimeMillis() - soFar,
                bootCount = bootCount(context),
                pausedMillis = 0L,
            ),
        )
    }

    @Synchronized
    private fun save(context: Context, state: StopwatchState) {
        _state.value = state
        prefs(context).edit { putString(KEY_STATE, state.toJson().toString()) }
        if (state.status == StopwatchStatus.IDLE) hideNotification(context) else showNotification(context, state)
    }

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        _state.value = prefs(context).getString(KEY_STATE, null)?.let { raw ->
            try {
                StopwatchState.fromJson(JSONObject(raw))
            } catch (e: JSONException) {
                null
            }
        } ?: StopwatchState()
        loaded = true
    }

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    // --- Notification ---

    /** "Stopwatch" counting up in the status bar while running; "Stopwatch paused · 1:23" while paused. */
    private fun showNotification(context: Context, state: StopwatchState) {
        createNotificationChannel(context)
        val running = state.status == StopwatchStatus.RUNNING
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stopwatch)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openStopwatchTab(context))
        if (state.laps.isNotEmpty()) {
            builder.setSubText(context.getString(R.string.lap_number, state.laps.size + if (running) 1 else 0))
        }
        if (running) {
            // The chronometer counts from "when" on the wall clock.
            builder.setContentTitle(context.getString(R.string.stopwatch))
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - state.elapsed())
                .setShowWhen(true)
                .addAction(0, context.getString(R.string.pause), action(context, StopwatchReceiver.ACTION_PAUSE))
                .addAction(0, context.getString(R.string.lap), action(context, StopwatchReceiver.ACTION_LAP))
        } else {
            builder.setContentTitle(context.getString(R.string.stopwatch_paused))
                .setContentText(formatStopwatch(state.pausedMillis))
                .setShowWhen(false)
                .addAction(0, context.getString(R.string.resume), action(context, StopwatchReceiver.ACTION_RESUME))
                .addAction(0, context.getString(R.string.reset), action(context, StopwatchReceiver.ACTION_RESET))
        }
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
            .setName(context.getString(R.string.stopwatch))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun openStopwatchTab(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        2,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_STOPWATCH)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, StopwatchReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun prefs(context: Context) = deviceProtectedPrefs(context, "stopwatch")

    const val CHANNEL_ID = "stopwatch"
    private const val NOTIFICATION_ID = 5000
    private const val KEY_STATE = "state"
}

/** "1:05:09.42", "5:09.42" or "0:09.42" (hundredths, rounded down). */
fun formatStopwatch(millis: Long): String {
    val ms = millis.coerceAtLeast(0L)
    val totalSeconds = ms / 1000L
    val h = totalSeconds / 3600
    val m = totalSeconds / 60 % 60
    val s = totalSeconds % 60
    val cs = ms % 1000L / 10L
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%d:%02d.%02d".format(m, s, cs)
}
