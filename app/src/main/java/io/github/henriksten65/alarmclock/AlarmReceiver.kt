package io.github.henriksten65.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Receives the AlarmManager trigger plus the Snooze / Dismiss notification actions. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        when (intent.action) {
            ACTION_FIRE -> onAlarmFired(context, alarmId)
            ACTION_SNOOZE -> AlarmScheduler.snooze(context, alarmId)
            ACTION_DISMISS -> AlarmScheduler.stopRinging(context)
        }
    }

    private fun onAlarmFired(context: Context, alarmId: Int) {
        val repository = AlarmRepository.getInstance(context)
        val alarm = repository.get(alarmId) ?: return
        if (!alarm.enabled) return

        // Ringing now, so any snooze is used up (and a skip that has passed).
        if (alarm.repeats) {
            val now = System.currentTimeMillis()
            val updated = alarm.copy(snoozedUntil = 0L, skippedAt = alarm.skippedAt.takeIf { it > now } ?: 0L)
            repository.upsert(updated)
            // Schedule the next selected day (offset by a minute so we never re-pick this occurrence).
            AlarmScheduler.schedule(context, updated, updated.nextTriggerMillis(now + 60_000L))
        } else {
            repository.upsert(alarm.copy(enabled = false, snoozedUntil = 0L))
            AlarmScheduler.forgetDueTime(context, alarmId)
        }

        // Alarms set with setAlarmClock() are allowed to start a foreground service from the background.
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).putExtra(EXTRA_ALARM_ID, alarmId),
        )
    }

    companion object {
        const val ACTION_FIRE = "io.github.henriksten65.alarmclock.action.FIRE"
        const val ACTION_SNOOZE = "io.github.henriksten65.alarmclock.action.SNOOZE"
        const val ACTION_DISMISS = "io.github.henriksten65.alarmclock.action.DISMISS"
    }
}
