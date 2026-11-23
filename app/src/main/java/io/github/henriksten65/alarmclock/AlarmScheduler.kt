package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit

/** Wraps AlarmManager. Uses setAlarmClock() so alarms are exact and fire even in Doze. */
object AlarmScheduler {

    /** Schedules [alarm] at [triggerAtMillis]. Returns false if exact alarms aren't permitted. */
    fun schedule(
        context: Context,
        alarm: Alarm,
        triggerAtMillis: Long = alarm.nextRingMillis(),
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
            dueTimes(context).edit { putLong(alarm.id.toString(), triggerAtMillis) }
            UpcomingAlarm.schedule(context, alarm, triggerAtMillis)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, alarmId))
        forgetDueTime(context, alarmId)
        UpcomingAlarm.cancel(context, alarmId)
    }

    /** Called when an alarm has fired and nothing is scheduled for it any more. */
    fun forgetDueTime(context: Context, alarmId: Int) {
        dueTimes(context).edit { remove(alarmId.toString()) }
    }

    /**
     * Re-registers all enabled alarms, e.g. after reboot or a time zone change.
     *
     * With [catchUpMissed] (after a reboot or app update, when AlarmManager has forgotten our
     * alarms), alarms whose time passed in the meantime are handled first: one missed by less
     * than the "stop ringing after" time rings now, late; one missed by more gets a "Missed
     * alarm" notification and then is switched off (one-time) or moves on to its next day.
     *
     * Otherwise, a snooze that is still in the future is kept; one that already passed is dropped.
     */
    fun rescheduleAll(context: Context, catchUpMissed: Boolean) {
        val repository = AlarmRepository.getInstance(context)
        val now = System.currentTimeMillis()
        val ringLateWindow = AppSettings.getInstance(context).autoStopMinutes.value * 60_000L
        repository.alarms.value.filter { it.enabled }.forEach { alarm ->
            val due = if (catchUpMissed) dueTime(context, alarm.id) else null
            when {
                // Rings right away (AlarmManager fires a time in the past immediately).
                due != null && due <= now && now - due < ringLateWindow -> schedule(context, alarm, now)
                due != null && due <= now -> {
                    AlarmService.notifyMissed(context, alarm, context.getString(R.string.missed_phone_off))
                    if (alarm.repeats) {
                        alarm.copy(snoozedUntil = 0L).also {
                            repository.upsert(it)
                            schedule(context, it)
                        }
                    } else {
                        repository.upsert(alarm.copy(enabled = false, snoozedUntil = 0L))
                        forgetDueTime(context, alarm.id)
                    }
                }
                alarm.snoozedUntil == 0L || alarm.isSnoozed(now) -> schedule(context, alarm)
                alarm.repeats -> alarm.copy(snoozedUntil = 0L).also {
                    repository.upsert(it)
                    schedule(context, it)
                }
                // A one-time alarm whose snooze was missed: it has rung already, so switch it off.
                else -> repository.upsert(alarm.copy(enabled = false, snoozedUntil = 0L))
            }
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }

    /** Stops the ringing alarm and rings again after the snooze length from the settings. */
    fun snooze(context: Context, alarmId: Int) {
        val repository = AlarmRepository.getInstance(context)
        repository.get(alarmId)?.let { alarm ->
            // Keep the alarm enabled while snoozed so the user can cancel the snooze with the switch.
            val until = System.currentTimeMillis() + AppSettings.getInstance(context).snoozeMinutes.value * 60_000L
            val updated = alarm.copy(enabled = true, snoozedUntil = until)
            repository.upsert(updated)
            schedule(context, updated, until)
        }
        stopRinging(context)
    }

    /**
     * Cancels a pending snooze. A repeating alarm goes back to its regular schedule;
     * a one-time alarm (which has already rung) is switched off.
     */
    fun cancelSnooze(context: Context, alarmId: Int) {
        val repository = AlarmRepository.getInstance(context)
        val alarm = repository.get(alarmId) ?: return
        if (alarm.repeats) {
            val updated = alarm.copy(snoozedUntil = 0L)
            repository.upsert(updated)
            schedule(context, updated)
        } else {
            repository.upsert(alarm.copy(enabled = false, snoozedUntil = 0L))
            cancel(context, alarmId)
        }
    }

    /** Skips the next regular occurrence of a repeating alarm (a pending snooze still rings). */
    fun skipNext(context: Context, alarmId: Int): Alarm? {
        val repository = AlarmRepository.getInstance(context)
        val alarm = repository.get(alarmId)?.takeIf { it.repeats && it.enabled } ?: return null
        val updated = alarm.copy(skippedAt = alarm.copy(skippedAt = 0L).nextOccurrenceMillis())
        repository.upsert(updated)
        schedule(context, updated)
        return updated
    }

    /** Undoes [skipNext]. */
    fun cancelSkip(context: Context, alarmId: Int): Alarm? {
        val repository = AlarmRepository.getInstance(context)
        val alarm = repository.get(alarmId) ?: return null
        val updated = alarm.copy(skippedAt = 0L)
        repository.upsert(updated)
        if (updated.enabled) schedule(context, updated)
        return updated
    }

    /**
     * The Dismiss button on the "Upcoming alarm" notification: the alarm won't ring this time.
     * A snooze is cancelled, a repeating alarm skips this ring, a one-time alarm is switched off.
     */
    fun dismissUpcoming(context: Context, alarmId: Int) {
        val repository = AlarmRepository.getInstance(context)
        val alarm = repository.get(alarmId)?.takeIf { it.enabled } ?: return UpcomingAlarm.hide(context, alarmId)
        when {
            alarm.isSnoozed() -> cancelSnooze(context, alarmId)
            alarm.repeats -> skipNext(context, alarmId)
            else -> {
                repository.upsert(alarm.copy(enabled = false, snoozedUntil = 0L))
                cancel(context, alarmId)
            }
        }
    }

    fun stopRinging(context: Context) {
        context.stopService(Intent(context, AlarmService::class.java))
    }

    /**
     * When each scheduled alarm is due, by alarm id, so alarms that should have rung while the
     * phone was off can be found after a reboot. Device-protected, so it's readable before unlock.
     */
    private fun dueTimes(context: Context) = deviceProtectedPrefs(context, "due_times")

    private fun dueTime(context: Context, alarmId: Int): Long? =
        dueTimes(context).getLong(alarmId.toString(), 0L).takeIf { it != 0L }

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
