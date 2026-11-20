package com.example.alarmclock

import android.content.Context
import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

/** Formats a time using the device's 12/24-hour preference. */
fun formatTime(context: Context, hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeFormat(context).format(cal.time)
}

fun formatTime(context: Context, millis: Long): String =
    DateFormat.getTimeFormat(context).format(millis)

/** "Alarm set for 7 h 30 min from now", or "… for 2 days 7 h 30 min …" when further away. */
fun timeUntilText(context: Context, triggerAtMillis: Long): String {
    val totalMinutes = ((triggerAtMillis - System.currentTimeMillis() + 59_999L) / 60_000L).toInt()
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes / 60 % 24
    val minutes = totalMinutes % 60
    return if (days == 0) {
        context.getString(R.string.alarm_set_in, hours, minutes)
    } else {
        context.resources.getQuantityString(R.plurals.alarm_set_in_days, days, days, hours, minutes)
    }
}

/** The seven [Calendar] day constants, starting with the locale's first day of the week. */
fun weekDaysInLocaleOrder(): List<Int> {
    val first = Calendar.getInstance().firstDayOfWeek
    return List(7) { (first - 1 + it) % 7 + 1 }
}

/** Day name for a [Calendar] day constant, e.g. "Mon" ([TextStyle.SHORT]) or "M" ([TextStyle.NARROW]). */
fun dayName(calendarDay: Int, style: TextStyle): String =
    // Calendar: Sunday = 1 … Saturday = 7; DayOfWeek: Monday = 1 … Sunday = 7.
    DayOfWeek.of((calendarDay + 5) % 7 + 1).getDisplayName(style, Locale.getDefault())

/** "Once", "Every day", "Weekdays", "Weekends" or e.g. "Mon, Wed, Fri". */
fun repeatText(context: Context, days: Int): String = when (days) {
    0 -> context.getString(R.string.once)
    Alarm.EVERY_DAY -> context.getString(R.string.every_day)
    Alarm.WEEKDAYS -> context.getString(R.string.weekdays)
    Alarm.WEEKEND -> context.getString(R.string.weekends)
    else -> weekDaysInLocaleOrder()
        .filter { days and Alarm.dayBit(it) != 0 }
        .joinToString(", ") { dayName(it, TextStyle.SHORT) }
}

/**
 * "today 07:30", "tomorrow 07:30" or "Mon 07:30" for a time within the coming week.
 */
fun dayAndTimeText(context: Context, millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    fun dayStart(m: Long) = Calendar.getInstance().apply {
        timeInMillis = m
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val target = dayStart(millis)
    val today = dayStart(nowMillis)
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val day = when (target.timeInMillis) {
        today.timeInMillis -> context.getString(R.string.today)
        tomorrow.timeInMillis -> context.getString(R.string.tomorrow)
        else -> dayName(target.get(Calendar.DAY_OF_WEEK), TextStyle.SHORT)
    }
    return "$day ${formatTime(context, millis)}"
}

/**
 * "in 2 d 7 h", "in 7 h 30 min" or "in 12 min". Rounds up to whole minutes, like the
 * "Alarm set for …" message, so 1 min 40 s left reads "in 2 min".
 */
fun timeUntilShortText(context: Context, triggerAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val totalMinutes = ((triggerAtMillis - nowMillis + 59_999L) / 60_000L).toInt()
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes / 60 % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> context.getString(R.string.in_days_hours, days, hours)
        hours > 0 -> context.getString(R.string.in_hours_minutes, hours, minutes)
        minutes > 0 -> context.getString(R.string.in_minutes, minutes)
        else -> context.getString(R.string.in_less_than_a_minute)
    }
}
