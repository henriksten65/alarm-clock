package com.example.alarmclock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that plays the alarm sound and vibrates while an alarm is ringing.
 * Its notification launches [RingActivity] full screen and has Snooze / Dismiss actions.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    /** True when the alarm stopped by itself after ringing [autoStopMinutes] without Dismiss/Snooze. */
    private var timedOut = false
    private var autoStopMinutes = 0
    private val autoStop = Runnable {
        timedOut = true
        stopSelf()
    }

    private val alarmAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        val alarm = AlarmRepository.getInstance(this).get(alarmId)
        _ringingAlarmId.value = alarmId

        createNotificationChannel(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(alarmId, alarm),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )

        startRinging(alarm)

        // Don't ring forever if nobody is around.
        autoStopMinutes = AppSettings.getInstance(this).autoStopMinutes.value
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, autoStopMinutes * 60_000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        if (timedOut) _ringingAlarmId.value?.let { notifyMissed(it) }
        player?.run {
            stop()
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        _ringingAlarmId.value = null
        // Tell RingActivity (if showing) to close.
        sendBroadcast(Intent(ACTION_ALARM_STOPPED).setPackage(packageName))
        super.onDestroy()
    }

    private fun startRinging(alarm: Alarm?) {
        if (player == null) {
            // The alarm's own sound or the one from the settings first; fall back to the system
            // defaults if it can't be played (e.g. the audio file was deleted).
            player = listOfNotNull(
                (alarm?.soundUri ?: AppSettings.getInstance(this).alarmSoundUri.value)?.let(Uri::parse),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                // Built into the app, so it plays even right after a reboot before the phone is
                // unlocked, when the user's audio files and the system default can't be read.
                Uri.parse("android.resource://$packageName/${R.raw.fallback_alarm}"),
            ).firstNotNullOfOrNull { createPlayer(it) }
        }
        if (vibrator == null && AppSettings.getInstance(this).vibrate.value) {
            vibrator = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                getSystemService(Vibrator::class.java)
            })?.also {
                @Suppress("DEPRECATION")
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0), alarmAudioAttributes)
            }
        }
    }

    private fun createPlayer(uri: Uri): MediaPlayer? {
        val mediaPlayer = MediaPlayer()
        return try {
            mediaPlayer.apply {
                setAudioAttributes(alarmAudioAttributes)
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaPlayer.release()
            null
        }
    }

    private fun buildNotification(alarmId: Int, alarm: Alarm?): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            alarmId,
            Intent(this, RingActivity::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = alarm?.label?.takeIf { it.isNotBlank() } ?: getString(R.string.alarm)
        val text = alarm?.let { formatTime(this, it.hour, it.minute) }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(0, getString(R.string.snooze), actionIntent(AlarmReceiver.ACTION_SNOOZE, alarmId))
            .addAction(0, getString(R.string.dismiss), actionIntent(AlarmReceiver.ACTION_DISMISS, alarmId))
            .build()
    }

    /** "Missed alarm · 06:15 Work" – posted when an alarm rang out without being dismissed. */
    private fun notifyMissed(alarmId: Int) {
        val alarm = AlarmRepository.getInstance(this).get(alarmId)
        val time = alarm?.let { formatTime(this, it.hour, it.minute) }.orEmpty()
        val what = listOfNotNull(time.ifEmpty { null }, alarm?.label?.takeIf { it.isNotBlank() }).joinToString(" · ")
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, MISSED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(getString(R.string.missed_alarm, what))
            .setContentText(resources.getQuantityString(R.plurals.missed_alarm_text, autoStopMinutes, autoStopMinutes))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(MISSED_NOTIFICATION_ID_BASE + alarmId, notification)
        } catch (e: SecurityException) {
            // Notifications are off; the app already shows a banner about that.
        }
    }

    private fun actionIntent(action: String, alarmId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            alarmId,
            Intent(this, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_ALARM_STOPPED = "com.example.alarmclock.action.ALARM_STOPPED"
        const val CHANNEL_ID = "ringing_alarms"

        private val _ringingAlarmId = MutableStateFlow<Int?>(null)

        /** The id of the alarm that is ringing right now, or null. */
        val ringingAlarmId: StateFlow<Int?> = _ringingAlarmId.asStateFlow()

        /** Creates the notification channels (safe to call repeatedly). */
        fun createNotificationChannel(context: Context) {
            val ringing = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_name))
                .setSound(null, null) // The service plays the sound itself.
                .setVibrationEnabled(false)
                .build()
            val missed = NotificationChannelCompat.Builder(MISSED_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.missed_channel_name))
                .build()
            NotificationManagerCompat.from(context).createNotificationChannelsCompat(listOf(ringing, missed))
        }

        private const val MISSED_CHANNEL_ID = "missed_alarms"
        private const val MISSED_NOTIFICATION_ID_BASE = 1000

        private const val NOTIFICATION_ID = 1
    }
}
