package com.example.alarmclock

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

/**
 * Checks for phone settings that would keep an alarm from being seen, heard or stopped,
 * plus intents for the system screens where the user can fix them.
 */
object AlarmHealth {

    /** Notifications are off for the app (or for the ringing channel): no Snooze/Dismiss and no ring screen. */
    fun notificationsBlocked(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return true
        val channel = manager.getNotificationChannelCompat(AlarmService.CHANNEL_ID)
        return channel != null && channel.importance == NotificationManagerCompat.IMPORTANCE_NONE
    }

    /** Android 14+: the user can revoke the right to show the ring screen over the lock screen. */
    fun fullScreenBlocked(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** The alarm volume is at its lowest setting (or muted), so the alarm is barely audible. */
    fun alarmVolumeLow(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(AudioManager.STREAM_ALARM) else 0
        return audio.isStreamMute(AudioManager.STREAM_ALARM) || audio.getStreamVolume(AudioManager.STREAM_ALARM) <= min
    }

    /** Do Not Disturb is on and set to silence alarms. */
    fun doNotDisturbBlocksAlarms(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> true // "Total silence"
            // Before Android 9, priority mode always let alarms through.
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && try {
                manager.notificationPolicy.priorityCategories and
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0
            } catch (e: SecurityException) {
                false
            }
            else -> false
        }
    }

    /** Raises the alarm volume to about two thirds of the maximum. */
    fun turnUpAlarmVolume(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val target = (audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 2 + 2) / 3
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, AudioManager.FLAG_SHOW_UI)
        } catch (e: SecurityException) {
            // Some Do Not Disturb states don't allow apps to change volume; the banner stays up.
        }
    }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun fullScreenSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
        } else {
            notificationSettingsIntent(context)
        }

    fun doNotDisturbSettingsIntent(): Intent = Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
}
