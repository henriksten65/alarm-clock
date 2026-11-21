package io.github.henriksten65.alarmclock

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The texts on the alarm cards, the "Next alarm" summary and the "Alarm set for …" message. */
@RunWith(AndroidJUnit4::class)
class TimeFormatTest {

    private val context get() = TestSupport.context
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

    private fun days(vararg calendarDays: Int) = calendarDays.fold(0) { acc, d -> acc or Alarm.dayBit(d) }

    @Test
    fun formatTimeFollowsThePhones24HourSetting() {
        assertEquals("07:05", formatTime(context, 7, 5))
        assertEquals("18:30", formatTime(context, 18, 30))
        TestSupport.use24HourClock(false)
        assertEquals("6:30 pm", formatTime(context, 18, 30).lowercase().replace(' ', ' '))
    }

    @Test
    fun weekStartsOnTheLocalesFirstDay() {
        Locale.setDefault(Locale.UK)
        assertEquals(Calendar.MONDAY, weekDaysInLocaleOrder().first())
        Locale.setDefault(Locale.US)
        assertEquals(
            listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY),
            weekDaysInLocaleOrder(),
        )
    }

    @Test
    fun dayNames() {
        assertEquals("Mon", dayName(Calendar.MONDAY, TextStyle.SHORT))
        assertEquals("Sunday", dayName(Calendar.SUNDAY, TextStyle.FULL))
        assertEquals("S", dayName(Calendar.SATURDAY, TextStyle.NARROW))
    }

    @Test
    fun repeatTexts() {
        assertEquals("Once", repeatText(context, 0))
        assertEquals("Every day", repeatText(context, Alarm.EVERY_DAY))
        assertEquals("Weekdays", repeatText(context, Alarm.WEEKDAYS))
        assertEquals("Weekends", repeatText(context, Alarm.WEEKEND))
        assertEquals("Mon, Wed, Fri", repeatText(context, days(Calendar.FRIDAY, Calendar.MONDAY, Calendar.WEDNESDAY)))
        // Sunday last in a Monday-first locale, first in a Sunday-first one.
        assertEquals("Tue, Sun", repeatText(context, days(Calendar.SUNDAY, Calendar.TUESDAY)))
        Locale.setDefault(Locale.US)
        assertEquals("Sun, Tue", repeatText(context, days(Calendar.SUNDAY, Calendar.TUESDAY)))
    }

    @Test
    fun dayAndTime() {
        val now = millisOf(2026, 11, 21, 22, 0) // Saturday
        assertEquals("today 23:30", dayAndTimeText(context, millisOf(2026, 11, 21, 23, 30), now))
        assertEquals("tomorrow 07:00", dayAndTimeText(context, millisOf(2026, 11, 22, 7, 0), now))
        assertEquals("Mon 07:00", dayAndTimeText(context, millisOf(2026, 11, 23, 7, 0), now))
    }

    @Test
    fun tomorrowAcrossADstChange() {
        // The day after 2026-03-28 is only 23 hours long; it's still "tomorrow".
        val now = millisOf(2026, 3, 28, 0, 30)
        assertEquals("tomorrow 07:00", dayAndTimeText(context, millisOf(2026, 3, 29, 7, 0), now))
    }

    @Test
    fun shortCountdownRoundsUpToWholeMinutes() {
        val now = millisOf(2026, 11, 21, 7, 0)
        val min = 60_000L
        assertEquals("in less than a minute", timeUntilShortText(context, now, now))
        assertEquals("in 1 min", timeUntilShortText(context, now + 1, now))
        assertEquals("in 2 min", timeUntilShortText(context, now + min + 40_000L, now))
        assertEquals("in 59 min", timeUntilShortText(context, now + 59 * min, now))
        assertEquals("in 1 h 0 min", timeUntilShortText(context, now + 60 * min, now))
        assertEquals("in 9 h 20 min", timeUntilShortText(context, now + (9 * 60 + 20) * min, now))
        assertEquals("in 1 d 0 h", timeUntilShortText(context, now + 24 * 60 * min, now))
        assertEquals("in 2 d 7 h", timeUntilShortText(context, now + ((2 * 24 + 7) * 60 + 30) * min, now))
    }

    @Test
    fun alarmSetMessage() {
        val now = System.currentTimeMillis()
        val min = 60_000L
        // A few seconds of slack: the function reads the clock itself.
        assertEquals("Alarm set for 7 h 30 min from now", timeUntilText(context, now + (7 * 60 + 30) * min - 5_000L))
        assertEquals("Alarm set for 1 day 2 h 0 min from now", timeUntilText(context, now + 26 * 60 * min - 5_000L))
        assertEquals("Alarm set for 3 days 0 h 5 min from now", timeUntilText(context, now + (3 * 24 * 60 + 5) * min - 5_000L))
    }
}
