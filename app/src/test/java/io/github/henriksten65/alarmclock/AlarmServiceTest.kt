package io.github.henriksten65.alarmclock

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.time.Duration
import java.util.Locale
import java.util.TimeZone

/** The ringing foreground service: notification, sound fallback chain, vibration, auto-stop. */
@RunWith(AndroidJUnit4::class)
class AlarmServiceTest {

    private val context get() = TestSupport.context
    private val repo get() = TestSupport.repository
    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale
    private val fallbackUri get() = Uri.parse("android.resource://${context.packageName}/${R.raw.fallback_alarm}")

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TestSupport.resetAppState()
        TestSupport.useZoneAndLocale()
        TestSupport.use24HourClock(true)
        ShadowMediaPlayer.resetStaticState()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    /** Lets the fake media player "play" [uri]; others fail as if the file were unreadable. */
    private fun playable(uri: Uri) =
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(context, uri), ShadowMediaPlayer.MediaInfo(1_000, 0))

    private fun start(alarmId: Int): ServiceController<AlarmService> =
        Robolectric.buildService(AlarmService::class.java, Intent().putExtra(EXTRA_ALARM_ID, alarmId))
            .create()
            .startCommand(0, 1)

    private fun player(service: AlarmService): MediaPlayer? =
        AlarmService::class.java.getDeclaredField("player").apply { isAccessible = true }.get(service) as MediaPlayer?

    private fun vibrator(): Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun ringingNotification() {
        repo.upsert(Alarm(id = 3, hour = 6, minute = 30, label = "Work"))
        val service = start(3).get()
        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull("runs as a foreground service", notification)

        val shadow = shadowOf(notification)
        assertEquals("Work", shadow.contentTitle)
        assertEquals("06:30", shadow.contentText)
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals(AlarmService.CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Snooze", "Dismiss"), notification.actions.map { it.title.toString() })

        val ringScreen = shadowOf(notification.fullScreenIntent).savedIntent
        assertEquals(RingActivity::class.java.name, ringScreen.component?.className)
        assertEquals(3, ringScreen.getIntExtra(EXTRA_ALARM_ID, -1))

        val snooze = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(AlarmReceiver.ACTION_SNOOZE, snooze.action)
        assertEquals(3, snooze.getIntExtra(EXTRA_ALARM_ID, -1))
        assertEquals(AlarmReceiver.ACTION_DISMISS, shadowOf(notification.actions[1].actionIntent).savedIntent.action)
    }

    @Test
    fun notificationWithoutALabelSaysAlarm() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, label = " "))
        assertEquals("Alarm", shadowOf(shadowOf(start(1).get()).lastForegroundNotification).contentTitle)
    }

    @Test
    fun ringingChannelIsSilentSoTheServicePlaysTheSound() {
        AlarmService.createNotificationChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringing = manager.getNotificationChannel(AlarmService.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, ringing.importance)
        assertNull(ringing.sound)
        assertFalse(ringing.shouldVibrate())
        assertNotNull(manager.getNotificationChannel("missed_alarms"))
    }

    @Test
    fun publishesTheRingingAlarmUntilStopped() {
        repo.upsert(Alarm(id = 4, hour = 7, minute = 0))
        val controller = start(4)
        assertEquals(4, AlarmService.ringingAlarmId.value)

        controller.destroy()
        assertNull(AlarmService.ringingAlarmId.value)
        val broadcasts = shadowOf(context as Application).broadcastIntents.map { it.action }
        assertTrue("tells the ring screen to close", AlarmService.ACTION_ALARM_STOPPED in broadcasts)
    }

    @Test
    fun playsTheAlarmsOwnSound() {
        val own = Uri.parse("content://media/external/audio/media/7")
        playable(own)
        playable(fallbackUri)
        TestSupport.settings.setAlarmSoundUri("content://media/external/audio/media/8")
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, soundUri = own.toString()))

        val player = player(start(1).get())!!
        assertEquals(DataSource.toDataSource(context, own), shadowOf(player).dataSource)
        assertTrue(player.isLooping)
        assertTrue(player.isPlaying)
    }

    @Test
    fun withoutItsOwnSoundAnAlarmPlaysTheSettingsSound() {
        val fromSettings = Uri.parse("content://media/external/audio/media/8")
        playable(fromSettings)
        TestSupport.settings.setAlarmSoundUri(fromSettings.toString())
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        assertEquals(DataSource.toDataSource(context, fromSettings), shadowOf(player(start(1).get())!!).dataSource)
    }

    @Test
    fun fallsBackToTheBundledSoundWhenNothingElsePlays() {
        // E.g. right after a reboot, before unlock: the user's file and the system sounds can't be read.
        playable(fallbackUri)
        TestSupport.settings.setAlarmSoundUri("content://media/external/audio/media/deleted")
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        assertEquals(DataSource.toDataSource(context, fallbackUri), shadowOf(player(start(1).get())!!).dataSource)
    }

    @Test
    fun stoppingReleasesThePlayer() {
        playable(fallbackUri)
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        val controller = start(1)
        val player = player(controller.get())!!
        controller.destroy()
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(player).state)
        assertNull(player(controller.get()))
    }

    @Test
    fun vibratesUnlessSwitchedOff() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        val controller = start(1)
        assertTrue(shadowOf(vibrator()).isVibrating)
        controller.destroy()
        assertFalse(shadowOf(vibrator()).isVibrating)

        TestSupport.settings.setVibrate(false)
        start(1)
        assertFalse(shadowOf(vibrator()).isVibrating)
    }

    @Test
    fun stopsByItselfAfterTheAutoStopTimeAndReportsAMissedAlarm() {
        TestSupport.settings.setAutoStopMinutes(5)
        repo.upsert(Alarm(id = 1, hour = 6, minute = 15, label = "Work"))
        val controller = start(1)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(4))
        assertFalse(shadowOf(controller.get()).isStoppedBySelf)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()

        val missed = shadowOf(notifications().single())
        assertEquals("Missed alarm: 06:15 · Work", missed.contentTitle)
        assertEquals("Rang for 5 minutes without being dismissed", missed.contentText)
    }

    @Test
    fun dismissedAlarmIsNotReportedAsMissed() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        start(1).destroy()
        assertTrue(notifications().isEmpty())
    }
}
