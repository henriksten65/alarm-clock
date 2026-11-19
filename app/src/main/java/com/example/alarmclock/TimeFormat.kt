package com.example.alarmclock

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar

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

/** "Alarm set for 7 h 30 min from now" */
fun timeUntilText(context: Context, triggerAtMillis: Long): String {
    val totalMinutes = ((triggerAtMillis - System.currentTimeMillis() + 59_999L) / 60_000L).toInt()
    return context.getString(R.string.alarm_set_in, totalMinutes / 60, totalMinutes % 60)
}
