package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Wraps AlarmManager. Uses setAlarmClock() so alarms are exact and fire even in Doze. */
object AlarmScheduler {

    const val SNOOZE_MINUTES = 10

    /** Schedules [alarm] at [triggerAtMillis]. Returns false if exact alarms aren't permitted. */
    fun schedule(
        context: Context,
        alarm: Alarm,
        triggerAtMillis: Long = alarm.nextTriggerMillis(),
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // Shown by the system (e.g. in the status bar / lock screen); opens the app when tapped.
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                firePendingIntent(context, alarm.id),
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, alarmId))
    }

    /** Re-registers all enabled alarms, e.g. after reboot or a time zone change. */
    fun rescheduleAll(context: Context) {
        AlarmRepository.getInstance(context).alarms.value
            .filter { it.enabled }
            .forEach { schedule(context, it) }
    }

    fun canScheduleExactAlarms(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    /** Stops the ringing alarm and rings again in [SNOOZE_MINUTES] minutes. */
    fun snooze(context: Context, alarmId: Int) {
        val repository = AlarmRepository.getInstance(context)
        repository.get(alarmId)?.let { alarm ->
            // Keep the alarm enabled while snoozed so the user can cancel the snooze with the switch.
            val updated = alarm.copy(enabled = true)
            repository.upsert(updated)
            schedule(context, updated, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
        }
        stopRinging(context)
    }

    fun stopRinging(context: Context) {
        context.stopService(Intent(context, AlarmService::class.java))
    }

    private fun firePendingIntent(context: Context, alarmId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_FIRE)
                .putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
