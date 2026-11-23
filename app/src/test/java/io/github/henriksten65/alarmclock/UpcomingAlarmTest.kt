package io.github.henriksten65.alarmclock

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The "Upcoming alarm" notification two hours before an alarm, and its Dismiss button. */
@RunWith(AndroidJUnit4::class)
class UpcomingAlarmTest {

    private val context get() = TestSupport.context
    private val repo get() = TestSupport.repository
    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TestSupport.resetAppState()
        TestSupport.useZoneAndLocale()
        TestSupport.use24HourClock(true)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private val minute = 60_000L
    private fun now() = System.currentTimeMillis()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun upcoming(): Notification? = shadowOf(manager).allNotifications.singleOrNull { it.channelId == UpcomingAlarm.CHANNEL_ID }

    /** An alarm (saved) at the wall-clock time [minutesFromNow] from now. */
    private fun alarmIn(minutesFromNow: Int, id: Int = 1, label: String = "", days: Int = 0): Alarm {
        val at = Calendar.getInstance().apply { add(Calendar.MINUTE, minutesFromNow) }
        return Alarm(id = id, hour = at.get(Calendar.HOUR_OF_DAY), minute = at.get(Calendar.MINUTE), label = label, days = days)
            .also { repo.upsert(it) }
    }

    private fun receive(action: String, alarmId: Int) =
        AlarmReceiver().onReceive(context, Intent(action).putExtra(EXTRA_ALARM_ID, alarmId))

    @Test
    fun alarmMoreThanTwoHoursAwaySetsAReminderTwoHoursBefore() {
        val alarm = alarmIn(5 * 60)
        AlarmScheduler.schedule(context, alarm)
        assertNull(upcoming())
        val reminder = TestSupport.reminders.single()
        assertEquals(alarm.nextRingMillis() - 2 * 60 * minute, reminder.triggerAtMs)
        val intent = shadowOf(reminder.operation).savedIntent
        assertEquals(AlarmReceiver.ACTION_UPCOMING, intent.action)
        assertEquals(1, intent.getIntExtra(EXTRA_ALARM_ID, -1))
    }

    @Test
    fun alarmWithinTwoHoursShowsTheNotificationRightAway() {
        val alarm = alarmIn(30, label = "Work")
        AlarmScheduler.schedule(context, alarm)
        assertTrue(TestSupport.reminders.isEmpty())
        val notification = upcoming()!!
        assertEquals("Upcoming alarm", shadowOf(notification).contentTitle)
        assertEquals("${formatTime(context, alarm.nextRingMillis())} · Work", shadowOf(notification).contentText)
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        assertEquals("Dismiss", notification.actions.single().title)
        // Not "ongoing": it can be swiped away.
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun reminderShowsTheNotificationWhenTheAlarmIsStillComingUp() {
        val alarm = alarmIn(90)
        receive(AlarmReceiver.ACTION_UPCOMING, alarm.id)
        assertEquals(formatTime(context, alarm.nextRingMillis()), shadowOf(upcoming()!!).contentText)
    }

    @Test
    fun reminderDoesNothingForAnAlarmThatIsOffOrFarAway() {
        repo.upsert(alarmIn(30).copy(enabled = false))
        receive(AlarmReceiver.ACTION_UPCOMING, 1)
        alarmIn(5 * 60, id = 2)
        receive(AlarmReceiver.ACTION_UPCOMING, 2)
        receive(AlarmReceiver.ACTION_UPCOMING, 99) // Deleted.
        assertNull(upcoming())
    }

    @Test
    fun cancellingTheAlarmRemovesReminderAndNotification() {
        AlarmScheduler.schedule(context, alarmIn(30, id = 1))
        AlarmScheduler.schedule(context, alarmIn(5 * 60, id = 2))
        AlarmScheduler.cancel(context, 1)
        AlarmScheduler.cancel(context, 2)
        assertNull(upcoming())
        assertTrue(TestSupport.reminders.isEmpty())
    }

    @Test
    fun movingTheAlarmLaterReplacesTheNotificationWithAReminder() {
        val alarm = alarmIn(30)
        AlarmScheduler.schedule(context, alarm)
        val later = alarmIn(5 * 60)
        AlarmScheduler.schedule(context, later)
        assertNull(upcoming())
        assertEquals(1, TestSupport.reminders.size)
    }

    @Test
    fun ringingRemovesTheNotification() {
        AlarmScheduler.schedule(context, alarmIn(30))
        receive(AlarmReceiver.ACTION_FIRE, 1)
        assertNull(upcoming())
    }

    @Test
    fun ringingLateAfterABootShowsNoNotification() {
        AlarmScheduler.schedule(context, alarmIn(30), now())
        assertNull(upcoming())
    }

    @Test
    fun snoozedAlarmSaysSnoozedUntil() {
        val until = now() + 10 * minute
        val alarm = alarmIn(5 * 60).copy(snoozedUntil = until).also { repo.upsert(it) }
        AlarmScheduler.schedule(context, alarm, until)
        assertEquals("Snoozed until ${formatTime(context, until)}", shadowOf(upcoming()!!).contentText)
    }

    @Test
    fun dismissSwitchesOffAOneTimeAlarm() {
        AlarmScheduler.schedule(context, alarmIn(30))
        receive(AlarmReceiver.ACTION_DISMISS_UPCOMING, 1)
        assertFalse(repo.get(1)!!.enabled)
        assertTrue(TestSupport.scheduledTimes().isEmpty())
        assertNull(upcoming())
    }

    @Test
    fun dismissSkipsThisRingOfARepeatingAlarm() {
        val alarm = alarmIn(30, days = Alarm.EVERY_DAY)
        val ringAt = alarm.nextRingMillis()
        AlarmScheduler.schedule(context, alarm)
        receive(AlarmReceiver.ACTION_DISMISS_UPCOMING, 1)
        val updated = repo.get(1)!!
        assertTrue(updated.enabled)
        assertEquals(ringAt, updated.skippedAt)
        assertEquals(listOf(updated.nextRingMillis()), TestSupport.scheduledTimes())
        assertTrue(updated.nextRingMillis() > ringAt + 20 * 60 * minute)
        assertNull(upcoming())
    }

    @Test
    fun dismissCancelsASnooze() {
        val until = now() + 10 * minute
        val alarm = alarmIn(5 * 60, days = Alarm.EVERY_DAY).copy(snoozedUntil = until).also { repo.upsert(it) }
        AlarmScheduler.schedule(context, alarm, until)
        receive(AlarmReceiver.ACTION_DISMISS_UPCOMING, 1)
        assertEquals(0L, repo.get(1)!!.snoozedUntil)
        assertEquals(listOf(repo.get(1)!!.nextRingMillis()), TestSupport.scheduledTimes())
        assertNull(upcoming())
    }

    @Test
    fun channelShowsInTheStatusBarButIsQuiet() {
        UpcomingAlarm.createNotificationChannel(context)
        val channel = manager.getNotificationChannel(UpcomingAlarm.CHANNEL_ID)
        // Default, not low: Android hides the status-bar icons of low-importance notifications.
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }
}
