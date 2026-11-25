package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
                // Without exact alarms, a few minutes late is fine for a reminder.
                alarmManager.setExactOrInexact(AlarmManager.RTC_WAKEUP, showAt, reminder)
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
        val text = joinNonBlank(if (snoozed) context.getString(R.string.snoozed_until, time) else time, alarm.label)
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
            .setContentIntent(openAppIntent(context))
            // Gone by itself when the alarm rings (the ringing notification takes over).
            .setTimeoutAfter(ringAt - now)
            .addAction(0, context.getString(R.string.dismiss), dismiss)
            .build()
        notifyIfAllowed(context, NOTIFICATION_ID_BASE + alarm.id, notification)
    }

    fun createNotificationChannel(context: Context) =
        createSilentChannel(context, CHANNEL_ID, context.getString(R.string.upcoming_channel_name))

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
