package io.github.henriksten65.alarmclock

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * When alarms ring: [Alarm.nextOccurrenceMillis], [Alarm.nextTriggerMillis] (with "Skip next"),
 * [Alarm.nextRingMillis] (with snooze), across weekdays, month/year ends and DST changes.
 * Plain JVM tests (no Android needed). Times are in Europe/Berlin; 2026-11-21 is a Saturday.
 */
class AlarmScheduleTest {

    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TestSupport.useZoneAndLocale()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private fun days(vararg calendarDays: Int) = calendarDays.fold(0) { acc, d -> acc or Alarm.dayBit(d) }

    // --- One-time alarms ---

    @Test
    fun oneTimeAlarmLaterTodayRingsToday() {
        val alarm = Alarm(id = 1, hour = 7, minute = 30)
        assertEquals("2026-11-21 07:30 SAT", describe(alarm.nextTriggerMillis(millisOf(2026, 11, 21, 6, 0))))
    }

    @Test
    fun oneTimeAlarmEarlierTodayRingsTomorrow() {
        val alarm = Alarm(id = 1, hour = 7, minute = 30)
        assertEquals("2026-11-22 07:30 SUN", describe(alarm.nextTriggerMillis(millisOf(2026, 11, 21, 8, 0))))
    }

    @Test
    fun alarmExactlyNowIsNotPickedAgain() {
        // "Strictly after": an alarm ringing at 07:30 must schedule its next ring for tomorrow.
        val alarm = Alarm(id = 1, hour = 7, minute = 30)
        assertEquals("2026-11-22 07:30 SUN", describe(alarm.nextTriggerMillis(millisOf(2026, 11, 21, 7, 30))))
    }

    @Test
    fun secondsAndMillisAreZero() {
        val alarm = Alarm(id = 1, hour = 7, minute = 30)
        val next = alarm.nextTriggerMillis(millisOf(2026, 11, 21, 6, 0) + 12_345L)
        assertEquals(0L, next % 60_000L)
    }

    @Test
    fun midnightAlarm() {
        val alarm = Alarm(id = 1, hour = 0, minute = 0)
        assertEquals("2026-11-22 00:00 SUN", describe(alarm.nextTriggerMillis(millisOf(2026, 11, 21, 23, 59))))
    }

    @Test
    fun rollsOverMonthAndYearEnd() {
        val alarm = Alarm(id = 1, hour = 6, minute = 0)
        assertEquals("2027-01-01 06:00 FRI", describe(alarm.nextTriggerMillis(millisOf(2026, 12, 31, 7, 0))))
    }

    // --- Weekday alarms ---

    @Test
    fun weekdayAlarmOnSaturdayWaitsForMonday() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.WEEKDAYS)
        assertEquals("2026-11-23 07:00 MON", describe(alarm.nextTriggerMillis(millisOf(2026, 11, 21, 6, 0))))
    }

    @Test
    fun weekendAlarmOnSaturdayMorningRingsToday() {
        val alarm = Alarm(id = 1, hour = 9, minute = 0, days = Alarm.WEEKEND)
        assertEquals("2026-11-21 09:00 SAT", describe(alarm.nextTriggerMillis(millisOf(2026, 11, 21, 6, 0))))
    }

    @Test
    fun singleDayAlarmAfterItsTimeWaitsAWholeWeek() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = days(Calendar.SATURDAY))
        assertEquals("2026-11-28 07:00 SAT", describe(alarm.nextTriggerMillis(millisOf(2026, 11, 21, 8, 0))))
    }

    @Test
    fun everyDayAlarmRingsTomorrowAfterItsTime() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY)
        assertEquals("2026-11-22 07:00 SUN", describe(alarm.nextTriggerMillis(millisOf(2026, 11, 21, 8, 0))))
    }

    @Test
    fun mondayWednesdayFriday() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = days(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY))
        val first = alarm.nextTriggerMillis(millisOf(2026, 11, 21, 8, 0))
        val second = alarm.nextTriggerMillis(first)
        val third = alarm.nextTriggerMillis(second)
        val fourth = alarm.nextTriggerMillis(third)
        assertEquals(
            listOf("2026-11-23 07:00 MON", "2026-11-25 07:00 WED", "2026-11-27 07:00 FRI", "2026-11-30 07:00 MON"),
            listOf(first, second, third, fourth).map(::describe),
        )
    }

    @Test
    fun dayBitsAndPresets() {
        assertEquals(1, Alarm.dayBit(Calendar.SUNDAY))
        assertEquals(64, Alarm.dayBit(Calendar.SATURDAY))
        assertEquals(0b0111110, Alarm.WEEKDAYS)
        assertEquals(0b1000001, Alarm.WEEKEND)
        assertEquals(Alarm.EVERY_DAY, Alarm.WEEKDAYS or Alarm.WEEKEND)

        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.WEEKDAYS)
        assertTrue(alarm.repeats)
        assertTrue(alarm.isOnDay(Calendar.MONDAY))
        assertFalse(alarm.isOnDay(Calendar.SUNDAY))
        assertFalse(Alarm(id = 2, hour = 7, minute = 0).repeats)
    }

    // --- Daylight saving time (Europe/Berlin: 2026-03-29 02:00 → 03:00, 2026-10-25 03:00 → 02:00) ---

    @Test
    fun keepsWallClockTimeAcrossSpringForward() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY)
        val from = millisOf(2026, 3, 28, 8, 0)
        val next = alarm.nextTriggerMillis(from)
        assertEquals("2026-03-29 07:00 SUN", describe(next))
        // The night is an hour shorter.
        assertEquals(22 * 60 * 60_000L, next - from)
    }

    @Test
    fun keepsWallClockTimeAcrossFallBack() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY)
        val from = millisOf(2026, 10, 24, 8, 0)
        val next = alarm.nextTriggerMillis(from)
        assertEquals("2026-10-25 07:00 SUN", describe(next))
        assertEquals(24 * 60 * 60_000L, next - from)
    }

    @Test
    fun alarmInTheSkippedHourRingsAnHourLater() {
        // 02:30 doesn't exist on 2026-03-29; the lenient Calendar moves it to 03:30 that day.
        val alarm = Alarm(id = 1, hour = 2, minute = 30)
        assertEquals("2026-03-29 03:30 SUN", describe(alarm.nextTriggerMillis(millisOf(2026, 3, 29, 1, 0))))
    }

    @Test
    fun alarmInTheRepeatedHourRingsOnce() {
        // 02:30 happens twice on 2026-10-25; after the first, the next ring is the following day.
        val alarm = Alarm(id = 1, hour = 2, minute = 30, days = Alarm.EVERY_DAY)
        val first = alarm.nextTriggerMillis(millisOf(2026, 10, 25, 1, 0))
        assertEquals("2026-10-25 02:30 SUN", describe(first))
        assertEquals("2026-10-26 02:30 MON", describe(alarm.nextTriggerMillis(first)))
    }

    // --- Skip next ---

    @Test
    fun skippedOccurrenceIsLeftOut() {
        val now = millisOf(2026, 11, 21, 8, 0)
        val base = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.WEEKDAYS)
        val monday = base.nextOccurrenceMillis(now)
        val skipping = base.copy(skippedAt = monday)

        assertTrue(skipping.isSkipping(now))
        assertEquals("2026-11-24 07:00 TUE", describe(skipping.nextTriggerMillis(now)))
        // nextOccurrenceMillis ignores the skip.
        assertEquals(monday, skipping.nextOccurrenceMillis(now))
    }

    @Test
    fun skipLapsesOnceItsTimeHasPassed() {
        val base = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.WEEKDAYS)
        val monday = millisOf(2026, 11, 23, 7, 0)
        val skipping = base.copy(skippedAt = monday)
        val tuesdayMorning = millisOf(2026, 11, 24, 6, 0)

        assertFalse(skipping.isSkipping(tuesdayMorning))
        assertEquals("2026-11-24 07:00 TUE", describe(skipping.nextTriggerMillis(tuesdayMorning)))
    }

    @Test
    fun staleSkipThatDoesNotMatchTheNextOccurrenceHasNoEffect() {
        // E.g. the alarm time was changed after skipping: the old skip time is no occurrence any more.
        val now = millisOf(2026, 11, 21, 8, 0)
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.WEEKDAYS, skippedAt = millisOf(2026, 11, 23, 6, 30))
        assertFalse(alarm.isSkipping(now))
        assertEquals("2026-11-23 07:00 MON", describe(alarm.nextTriggerMillis(now)))
    }

    // --- Snooze ---

    @Test
    fun snoozeTakesPrecedenceUntilItHasPassed() {
        val now = millisOf(2026, 11, 21, 7, 0)
        val snoozeEnd = now + 10 * 60_000L
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY, snoozedUntil = snoozeEnd)

        assertTrue(alarm.isSnoozed(now))
        assertEquals(snoozeEnd, alarm.nextRingMillis(now))

        val later = snoozeEnd + 1
        assertFalse(alarm.isSnoozed(later))
        assertEquals("2026-11-22 07:00 SUN", describe(alarm.nextRingMillis(later)))
    }

    @Test
    fun snoozeStillRingsWhileTheNextOccurrenceIsSkipped() {
        val now = millisOf(2026, 11, 23, 7, 0)
        val snoozeEnd = now + 5 * 60_000L
        val base = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.WEEKDAYS, snoozedUntil = snoozeEnd)
        val alarm = base.copy(skippedAt = base.nextOccurrenceMillis(now))
        assertEquals(snoozeEnd, alarm.nextRingMillis(now))
    }
}
