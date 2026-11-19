package com.example.alarmclock

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

        if (alarm.repeatDaily) {
            // Schedule tomorrow's occurrence (offset by a minute so we never re-pick today).
            AlarmScheduler.schedule(context, alarm, alarm.nextTriggerMillis(System.currentTimeMillis() + 60_000L))
        } else {
            repository.upsert(alarm.copy(enabled = false))
        }

        // Alarms set with setAlarmClock() are allowed to start a foreground service from the background.
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).putExtra(EXTRA_ALARM_ID, alarmId),
        )
    }

    companion object {
        const val ACTION_FIRE = "com.example.alarmclock.action.FIRE"
        const val ACTION_SNOOZE = "com.example.alarmclock.action.SNOOZE"
        const val ACTION_DISMISS = "com.example.alarmclock.action.DISMISS"
    }
}
