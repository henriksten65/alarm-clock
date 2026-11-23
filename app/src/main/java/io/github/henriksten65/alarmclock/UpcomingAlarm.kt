package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The "Upcoming alarm" notification: shown from [LEAD_MILLIS] before an alarm rings until it
 * rings, with a Dismiss button that skips that one ring. A second AlarmManager alarm per alarm
 * posts it; [AlarmScheduler] keeps that in step whenever it schedules or cancels the alarm.
 */
object UpcomingAlarm {

    const val LEAD_MILLIS = 2 * 60 * 60_000L

    /** Called by [AlarmScheduler.schedule]: the alarm will ring at [triggerAtMillis]. */
    fun schedule(context: Context, alarm: Alarm, triggerAtMillis: Long) {
        val now = System.currentTimeMillis()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val reminder = reminderIntent(context, alarm.id)
        alarmManager.cancel(reminder)
        val showAt = triggerAtMillis - LEAD_MILLIS
        when {
            triggerAtMillis <= now -> hide(context, alarm.id) // Rings right away.
            showAt <= now -> show(context, alarm, triggerAtMillis)
            else -> {
                hide(context, alarm.id)
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showAt, reminder)
                } catch (e: SecurityException) {
                    // No exact alarms: a few minutes late is fine for a reminder.
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showAt, reminder)
                }
            }
        }
    }

    /** Called by [AlarmScheduler.cancel]: nothing is scheduled for the alarm any more. */
    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(reminderIntent(context, alarmId))
        hide(context, alarmId)
    }

    /** The reminder alarm went off: show the notification if the alarm is still coming up. */
    fun onReminderDue(context: Context, alarmId: Int) {
        val alarm = AlarmRepository.getInstance(context).get(alarmId)?.takeIf { it.enabled } ?: return
        val now = System.currentTimeMillis()
        val ringAt = alarm.nextRingMillis(now)
        // A minute's grace, in case AlarmManager delivered the reminder a little early.
        if (ringAt - now <= LEAD_MILLIS + 60_000L) show(context, alarm, ringAt)
    }

    fun hide(context: Context, alarmId: Int) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE + alarmId)
    }

    private fun show(context: Context, alarm: Alarm, ringAt: Long) {
        val now = System.currentTimeMillis()
        val snoozed = alarm.isSnoozed(now)
        val time = formatTime(context, ringAt)
        val text = listOf(
            if (snoozed) context.getString(R.string.snoozed_until, time) else time,
            alarm.label,
        ).filter { it.isNotBlank() }.joinToString(" · ")
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            alarm.id,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_DISMISS_UPCOMING)
                .putExtra(EXTRA_ALARM_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.upcoming_alarm))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            // Gone by itself when the alarm rings (the ringing notification takes over).
            .setTimeoutAfter(ringAt - now)
            .addAction(0, context.getString(R.string.dismiss), dismiss)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + alarm.id, notification)
        } catch (e: SecurityException) {
            // Notifications are off; the app already shows a banner about that.
        }
    }

    /**
     * Default importance so the icon shows in the status bar (Android hides the icons of
     * low-importance "silent" notifications there), but without sound or vibration.
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.upcoming_channel_name))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun reminderIntent(context: Context, alarmId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_UPCOMING)
                .putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    const val CHANNEL_ID = "upcoming"
    private const val NOTIFICATION_ID_BASE = 2000
}
