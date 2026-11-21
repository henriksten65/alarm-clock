package io.github.henriksten65.alarmclock

import android.app.NotificationManager
import android.media.AudioManager
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The checks behind the warning banners on the main screen. */
@RunWith(AndroidJUnit4::class)
class AlarmHealthTest {

    private val context get() = TestSupport.context
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)
    private val audio get() = context.getSystemService(AudioManager::class.java)

    @Before
    fun setUp() = TestSupport.resetAppState()

    @Test
    fun notificationsBlocked() {
        AlarmService.createNotificationChannel(context)
        assertFalse(AlarmHealth.notificationsBlocked(context))

        shadowOf(notificationManager).setNotificationsEnabled(false)
        assertTrue(AlarmHealth.notificationsBlocked(context))
    }

    @Test
    fun ringingChannelSwitchedOffCountsAsBlocked() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(AlarmService.CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_NONE)
                .setName("Ringing alarms").build(),
        )
        assertTrue(AlarmHealth.notificationsBlocked(context))
    }

    @Test
    fun alarmVolumeAtItsLowestOrMuted() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, max / 2, 0)
        assertFalse(AlarmHealth.alarmVolumeLow(context))

        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMinVolume(AudioManager.STREAM_ALARM), 0)
        assertTrue(AlarmHealth.alarmVolumeLow(context))

        audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        audio.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
        assertTrue(AlarmHealth.alarmVolumeLow(context))
    }

    @Test
    fun turnUpSetsAboutTwoThirdsAndUnmutes() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        audio.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)

        AlarmHealth.turnUpAlarmVolume(context)

        assertEquals((max * 2 + 2) / 3, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertFalse(audio.isStreamMute(AudioManager.STREAM_ALARM))
        assertFalse(AlarmHealth.alarmVolumeLow(context))
    }

    @Test
    fun doNotDisturb() {
        val manager = notificationManager.also { shadowOf(it).setNotificationPolicyAccessGranted(true) }
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertFalse(AlarmHealth.doNotDisturbBlocksAlarms(context))

        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE) // total silence
        assertTrue(AlarmHealth.doNotDisturbBlocksAlarms(context))

        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS) // alarms only
        assertFalse(AlarmHealth.doNotDisturbBlocksAlarms(context))
    }

    @Test
    fun priorityModeBlocksAlarmsOnlyWhenTheyAreNotAllowed() {
        val manager = notificationManager.also { shadowOf(it).setNotificationPolicyAccessGranted(true) }
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        manager.setNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0))
        assertFalse(AlarmHealth.doNotDisturbBlocksAlarms(context))

        manager.setNotificationPolicy(NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_CALLS, 0, 0))
        assertTrue(AlarmHealth.doNotDisturbBlocksAlarms(context))
    }

    @Test
    fun settingsIntents() {
        val notifications = AlarmHealth.notificationSettingsIntent(context)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifications.action)
        assertEquals(context.packageName, notifications.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        val fullScreen = AlarmHealth.fullScreenSettingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, fullScreen.action)
        assertEquals("package:${context.packageName}", fullScreen.dataString)

        assertEquals(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, AlarmHealth.doNotDisturbSettingsIntent().action)
    }
}
