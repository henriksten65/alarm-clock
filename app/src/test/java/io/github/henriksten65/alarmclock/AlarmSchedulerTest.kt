package io.github.henriksten65.alarmclock

import android.app.NotificationManager
import android.content.Intent
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.util.Locale
import java.util.TimeZone

/** Scheduling with AlarmManager, snooze, skip, and re-registering after a reboot / time change. */
@RunWith(AndroidJUnit4::class)
class AlarmSchedulerTest {

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
    private fun dueTimes() = deviceProtectedPrefs(context, "due_times")
    private fun dueTime(id: Int): Long? = dueTimes().getLong(id.toString(), 0L).takeIf { it != 0L }
    /** The "Missed alarm" notifications (an alarm due soon also has an "Upcoming alarm" one). */
    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        .filter { it.channelId == "missed_alarms" }

    private fun add(alarm: Alarm): Alarm = alarm.also { repo.upsert(it) }

    /** An alarm that should have rung [minutesAgo] minutes ago, as if the phone was off then. */
    private fun dueAgo(alarm: Alarm, minutesAgo: Int) {
        dueTimes().edit().putLong(alarm.id.toString(), now() - minutesAgo * minute).commit()
    }

    // --- schedule / cancel ---

    @Test
    fun scheduleRegistersAnAlarmClockAtTheNextRingTime() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0))
        assertTrue(AlarmScheduler.schedule(context, alarm))

        val scheduled = TestSupport.alarmClocks.single()
        val expected = alarm.nextRingMillis()
        assertEquals(expected, scheduled.triggerAtMs)
        // setAlarmClock (not set/setExact): shows the system alarm icon and opens the app when tapped.
        assertEquals(expected, scheduled.alarmClockInfo!!.triggerTime)
        assertNotNull(scheduled.alarmClockInfo!!.showIntent)
        assertEquals(expected, dueTime(1))
    }

    @Test
    fun schedulingAgainReplacesTheEarlierTrigger() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0))
        AlarmScheduler.schedule(context, alarm)
        AlarmScheduler.schedule(context, alarm.copy(hour = 8))
        assertEquals(listOf(alarm.copy(hour = 8).nextRingMillis()), TestSupport.scheduledTimes())
    }

    @Test
    fun eachAlarmHasItsOwnTrigger() {
        AlarmScheduler.schedule(context, add(Alarm(id = 1, hour = 7, minute = 0)))
        AlarmScheduler.schedule(context, add(Alarm(id = 2, hour = 8, minute = 0)))
        assertEquals(2, TestSupport.alarmClocks.size)
    }

    @Test
    fun triggerFiresTheAlarmReceiver() {
        AlarmScheduler.schedule(context, add(Alarm(id = 5, hour = 7, minute = 0)))
        val operation = shadowOf(TestSupport.alarmClocks.single().pendingIntent)
        assertTrue(operation.isBroadcast)
        val intent = operation.savedIntent
        assertEquals(AlarmReceiver.ACTION_FIRE, intent.action)
        assertEquals(AlarmReceiver::class.java.name, intent.component?.className)
        assertEquals(5, intent.getIntExtra(EXTRA_ALARM_ID, -1))
    }

    @Test
    fun cancelRemovesTheTriggerAndTheDueTime() {
        AlarmScheduler.schedule(context, add(Alarm(id = 1, hour = 7, minute = 0)))
        AlarmScheduler.schedule(context, add(Alarm(id = 2, hour = 8, minute = 0)))
        AlarmScheduler.cancel(context, 1)
        assertEquals(listOf(repo.get(2)!!.nextRingMillis()), TestSupport.scheduledTimes())
        assertNull(dueTime(1))
        assertNotNull(dueTime(2))
    }

    @Test
    fun reportsWhetherExactAlarmsAreAllowed() {
        // (Robolectric's AlarmManager doesn't throw like the real one when they aren't, so the
        // SecurityException path of schedule() can't be tested here.)
        assertTrue(AlarmScheduler.canScheduleExactAlarms(context))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(AlarmScheduler.canScheduleExactAlarms(context))
    }

    // --- rescheduleAll without catch-up (time or time zone changed) ---

    @Test
    fun rescheduleAllRegistersOnlyEnabledAlarms() {
        add(Alarm(id = 1, hour = 7, minute = 0))
        add(Alarm(id = 2, hour = 8, minute = 0, enabled = false))
        add(Alarm(id = 3, hour = 9, minute = 0, days = Alarm.WEEKDAYS))
        AlarmScheduler.rescheduleAll(context, catchUpMissed = false)
        assertEquals(listOf(repo.get(1)!!, repo.get(3)!!).map { it.nextRingMillis() }.sorted(), TestSupport.scheduledTimes())
    }

    @Test
    fun rescheduleAllKeepsAPendingSnooze() {
        val until = now() + 7 * minute
        add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY, snoozedUntil = until))
        AlarmScheduler.rescheduleAll(context, catchUpMissed = false)
        assertEquals(listOf(until), TestSupport.scheduledTimes())
        assertEquals(until, repo.get(1)!!.snoozedUntil)
    }

    @Test
    fun rescheduleAllDropsAPassedSnoozeOfARepeatingAlarm() {
        add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY, snoozedUntil = now() - minute))
        AlarmScheduler.rescheduleAll(context, catchUpMissed = false)
        val alarm = repo.get(1)!!
        assertEquals(0L, alarm.snoozedUntil)
        assertTrue(alarm.enabled)
        assertEquals(listOf(alarm.nextTriggerMillis()), TestSupport.scheduledTimes())
    }

    @Test
    fun rescheduleAllSwitchesOffAOneTimeAlarmWhoseSnoozePassed() {
        add(Alarm(id = 1, hour = 7, minute = 0, snoozedUntil = now() - minute))
        AlarmScheduler.rescheduleAll(context, catchUpMissed = false)
        assertEquals(Alarm(id = 1, hour = 7, minute = 0, enabled = false), repo.get(1))
        assertTrue(TestSupport.scheduledTimes().isEmpty())
    }

    @Test
    fun withoutCatchUpAPassedDueTimeIsNotReportedAsMissed() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0))
        dueAgo(alarm, 60)
        AlarmScheduler.rescheduleAll(context, catchUpMissed = false)
        assertTrue(notifications().isEmpty())
        assertTrue(repo.get(1)!!.enabled)
        assertEquals(listOf(alarm.nextRingMillis()), TestSupport.scheduledTimes())
    }

    // --- rescheduleAll with catch-up (after a reboot or an app update) ---

    @Test
    fun alarmMissedByAFewMinutesRingsNow() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0))
        dueAgo(alarm, 3)
        val before = now()
        AlarmScheduler.rescheduleAll(context, catchUpMissed = true)
        val trigger = TestSupport.scheduledTimes().single()
        assertTrue("rings right away, not tomorrow", trigger in before..now())
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun ringLateWindowIsTheStopRingingAfterSetting() {
        TestSupport.settings.setAutoStopMinutes(30)
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0))
        dueAgo(alarm, 20)
        AlarmScheduler.rescheduleAll(context, catchUpMissed = true)
        assertTrue(TestSupport.scheduledTimes().single() <= now())
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun oneTimeAlarmMissedLongAgoIsReportedAndSwitchedOff() {
        val alarm = add(Alarm(id = 1, hour = 6, minute = 15, label = "Work"))
        dueAgo(alarm, 60)
        AlarmScheduler.rescheduleAll(context, catchUpMissed = true)

        assertFalse(repo.get(1)!!.enabled)
        assertTrue(TestSupport.scheduledTimes().isEmpty())
        assertNull(dueTime(1))
        val notification = shadowOf(notifications().single())
        assertEquals("Missed alarm: 06:15 · Work", notification.contentTitle)
        assertEquals("The phone was off when it was due", notification.contentText)
    }

    @Test
    fun repeatingAlarmMissedLongAgoIsReportedAndMovesOn() {
        val alarm = add(Alarm(id = 1, hour = 6, minute = 15, days = Alarm.EVERY_DAY, snoozedUntil = now() - 50 * minute))
        dueAgo(alarm, 60)
        AlarmScheduler.rescheduleAll(context, catchUpMissed = true)

        val updated = repo.get(1)!!
        assertTrue(updated.enabled)
        assertEquals(0L, updated.snoozedUntil)
        assertEquals(listOf(updated.nextTriggerMillis()), TestSupport.scheduledTimes())
        assertEquals("Missed alarm: 06:15", shadowOf(notifications().single()).contentTitle)
    }

    @Test
    fun alarmsNotYetDueAreScheduledNormallyAfterBoot() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0))
        AlarmScheduler.schedule(context, alarm)
        AlarmScheduler.rescheduleAll(context, catchUpMissed = true)
        assertEquals(listOf(alarm.nextRingMillis()), TestSupport.scheduledTimes())
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun disabledAlarmsAreNeverReportedAsMissed() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0, enabled = false))
        dueAgo(alarm, 60)
        AlarmScheduler.rescheduleAll(context, catchUpMissed = true)
        assertTrue(notifications().isEmpty())
        assertTrue(TestSupport.scheduledTimes().isEmpty())
    }

    // --- snooze ---

    @Test
    fun snoozeRingsAgainAfterTheSnoozeLength() {
        TestSupport.settings.setSnoozeMinutes(5)
        add(Alarm(id = 1, hour = 7, minute = 0, enabled = false)) // a one-time alarm switches itself off when it rings
        val before = now()
        AlarmScheduler.snooze(context, 1)

        val alarm = repo.get(1)!!
        assertTrue("stays on, so the switch can cancel the snooze", alarm.enabled)
        assertTrue(alarm.snoozedUntil in before + 5 * minute..now() + 5 * minute)
        assertEquals(listOf(alarm.snoozedUntil), TestSupport.scheduledTimes())
        assertStoppedRinging()
    }

    @Test
    fun snoozingAnUnknownAlarmStillStopsTheRinging() {
        AlarmScheduler.snooze(context, 42)
        assertTrue(TestSupport.scheduledTimes().isEmpty())
        assertStoppedRinging()
    }

    @Test
    fun cancellingTheSnoozeOfARepeatingAlarmGoesBackToItsSchedule() {
        add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY))
        AlarmScheduler.snooze(context, 1)
        AlarmScheduler.cancelSnooze(context, 1)

        val alarm = repo.get(1)!!
        assertEquals(0L, alarm.snoozedUntil)
        assertTrue(alarm.enabled)
        assertEquals(listOf(alarm.nextTriggerMillis()), TestSupport.scheduledTimes())
    }

    @Test
    fun cancellingTheSnoozeOfAOneTimeAlarmSwitchesItOff() {
        add(Alarm(id = 1, hour = 7, minute = 0, enabled = false))
        AlarmScheduler.snooze(context, 1)
        AlarmScheduler.cancelSnooze(context, 1)
        assertEquals(Alarm(id = 1, hour = 7, minute = 0, enabled = false), repo.get(1))
        assertTrue(TestSupport.scheduledTimes().isEmpty())
    }

    // --- skip next ---

    @Test
    fun skipNextLeavesOutTheNextOccurrence() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY))
        AlarmScheduler.schedule(context, alarm)
        val next = alarm.nextOccurrenceMillis()

        val skipping = AlarmScheduler.skipNext(context, 1)!!
        assertEquals(next, skipping.skippedAt)
        assertTrue(skipping.isSkipping())
        assertEquals(skipping, repo.get(1))
        assertEquals(listOf(alarm.nextOccurrenceMillis(next)), TestSupport.scheduledTimes())
    }

    @Test
    fun skipNextIsOnlyForRepeatingAlarmsThatAreOn() {
        add(Alarm(id = 1, hour = 7, minute = 0))
        add(Alarm(id = 2, hour = 7, minute = 0, days = Alarm.EVERY_DAY, enabled = false))
        assertNull(AlarmScheduler.skipNext(context, 1))
        assertNull(AlarmScheduler.skipNext(context, 2))
        assertNull(AlarmScheduler.skipNext(context, 3))
        assertTrue(TestSupport.scheduledTimes().isEmpty())
    }

    @Test
    fun skippingTwiceStillSkipsOnlyOneOccurrence() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY))
        val first = AlarmScheduler.skipNext(context, 1)!!
        val second = AlarmScheduler.skipNext(context, 1)!!
        assertEquals(first.skippedAt, second.skippedAt)
        assertEquals(listOf(alarm.nextOccurrenceMillis(first.skippedAt)), TestSupport.scheduledTimes())
    }

    @Test
    fun cancelSkipRestoresTheNextOccurrence() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY))
        AlarmScheduler.skipNext(context, 1)
        val restored = AlarmScheduler.cancelSkip(context, 1)!!
        assertEquals(0L, restored.skippedAt)
        assertEquals(listOf(alarm.nextOccurrenceMillis()), TestSupport.scheduledTimes())
    }

    @Test
    fun cancelSkipOnADisabledAlarmDoesNotScheduleIt() {
        add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY, enabled = false, skippedAt = now() + minute))
        assertEquals(0L, AlarmScheduler.cancelSkip(context, 1)!!.skippedAt)
        assertTrue(TestSupport.scheduledTimes().isEmpty())
    }

    private fun assertStoppedRinging() {
        val stopped: Intent? = shadowOf(TestSupport.context as android.app.Application).nextStoppedService
        assertEquals(AlarmService::class.java.name, stopped?.component?.className)
    }
}
