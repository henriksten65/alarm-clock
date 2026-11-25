package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Posts [notification]; does nothing when notifications are off (the app already shows a banner about that). */
internal fun notifyIfAllowed(context: Context, id: Int, notification: Notification) {
    try {
        NotificationManagerCompat.from(context).notify(id, notification)
    } catch (e: SecurityException) {
        // Notifications are off.
    }
}

/**
 * A channel with default importance, so the icon shows in the status bar (Android hides the
 * icons of low-importance "silent" notifications there), but without sound or vibration.
 */
internal fun createSilentChannel(context: Context, id: String, name: String, showBadge: Boolean = true) {
    val channel = NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        .setName(name)
        .setSound(null, null)
        .setVibrationEnabled(false)
        .setShowBadge(showBadge)
        .build()
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
}

/** The base of the timer and stopwatch notifications: ongoing, silent, public on the lock screen. */
internal fun ongoingNotificationBuilder(
    context: Context,
    channelId: String,
    icon: Int,
    contentIntent: PendingIntent,
): NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)
    .setSmallIcon(icon)
    .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setSilent(true)
    .setContentIntent(contentIntent)

/** Opens the app on whatever it shows (the alarms, unless it's open on another tab). */
internal fun openAppIntent(context: Context, requestCode: Int = 0): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

/**
 * Opens the app on [tab] ([MainActivity.TAB_TIMER] or [MainActivity.TAB_STOPWATCH]). Each tab needs
 * its own [requestCode]: extras don't tell PendingIntents apart, so they would replace each other.
 */
internal fun openTabIntent(context: Context, tab: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_TAB, tab)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

/** An exact wake-up alarm, or, when exact alarms aren't allowed, one that may be a few minutes late. */
internal fun AlarmManager.setExactOrInexact(type: Int, triggerAt: Long, operation: PendingIntent) {
    try {
        setExactAndAllowWhileIdle(type, triggerAt, operation)
    } catch (e: SecurityException) {
        setAndAllowWhileIdle(type, triggerAt, operation)
    }
}

/** Changes on every reboot, when [android.os.SystemClock.elapsedRealtime] starts again from 0. */
internal fun bootCount(context: Context): Int =
    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

/** "06:15 · Work": the parts that aren't blank, joined with [separator]. */
fun joinNonBlank(vararg parts: String, separator: String = " · "): String =
    parts.filter { it.isNotBlank() }.joinToString(separator)
