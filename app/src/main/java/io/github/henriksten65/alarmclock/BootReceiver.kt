package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager forgets everything on reboot, and wall-clock alarms need re-computing when
 * the time or time zone changes, so re-register all enabled alarms on those events.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        // After a time or time zone change our alarms are still registered (AlarmManager itself
        // fires those that are now in the past); otherwise they were lost, maybe while one was due.
        val catchUpMissed = intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED
        AlarmScheduler.rescheduleAll(context, catchUpMissed)
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) Timer.restore(context)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            // Delivered right after boot, before the user unlocks (we're directBootAware);
            // BOOT_COMPLETED follows after the first unlock.
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
