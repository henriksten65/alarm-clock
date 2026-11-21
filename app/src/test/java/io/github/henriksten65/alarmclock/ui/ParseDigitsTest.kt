package io.github.henriksten65.alarmclock.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Typed time entry: the last two digits are the minutes, 1–2 digits are a whole hour. */
class ParseDigitsTest {

    private fun parse24(digits: String) = parseDigits(digits, is24Hour = true, pm = false)
    private fun parse12(digits: String, pm: Boolean) = parseDigits(digits, is24Hour = false, pm = pm)

    @Test
    fun twentyFourHourTimes() {
        assertEquals(6 to 30, parse24("630"))
        assertEquals(18 to 45, parse24("1845"))
        assertEquals(0 to 5, parse24("005"))
        assertEquals(0 to 0, parse24("0000"))
        assertEquals(23 to 59, parse24("2359"))
        assertEquals(12 to 0, parse24("1200"))
    }

    @Test
    fun oneOrTwoDigitsAreAWholeHour() {
        assertEquals(7 to 0, parse24("7"))
        assertEquals(0 to 0, parse24("0"))
        assertEquals(19 to 0, parse24("19"))
        assertEquals(7 to 0, parse24("07"))
    }

    @Test
    fun invalidTimes() {
        assertNull(parse24(""))
        assertNull(parse24("24"))
        assertNull(parse24("2400"))
        assertNull(parse24("660")) // 60 minutes
        assertNull(parse24("1299"))
        assertNull(parse24("9999"))
    }

    @Test
    fun twelveHourAm() {
        assertEquals(6 to 30, parse12("630", pm = false))
        assertEquals(0 to 15, parse12("1215", pm = false)) // 12:15 AM is just after midnight
        assertEquals(11 to 0, parse12("11", pm = false))
    }

    @Test
    fun twelveHourPm() {
        assertEquals(18 to 30, parse12("630", pm = true))
        assertEquals(12 to 15, parse12("1215", pm = true)) // 12:15 PM is just after noon
        assertEquals(23 to 0, parse12("11", pm = true))
    }

    @Test
    fun hoursThatOnlyExistOnA24HourClockIgnoreAmPm() {
        assertEquals(0 to 30, parse12("030", pm = true))
        assertEquals(18 to 45, parse12("1845", pm = false))
        assertEquals(13 to 0, parse12("13", pm = true))
        assertNull(parse12("2400", pm = false))
    }
}
