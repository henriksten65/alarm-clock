package io.github.henriksten65.alarmclock

import org.json.JSONObject
import java.util.Calendar

const val EXTRA_ALARM_ID = "io.github.henriksten65.alarmclock.EXTRA_ALARM_ID"

/**
 * A single alarm. Time is stored as wall-clock hour/minute in the device's time zone.
 *
 * [days] is a bit set of the weekdays the alarm repeats on: bit `d - 1` for each
 * [Calendar] day constant `d` ([Calendar.SUNDAY] = 1 … [Calendar.SATURDAY] = 7).
 * 0 means a one-time alarm.
 *
 * [snoozedUntil] is the epoch millis a snoozed alarm will ring again, or 0 if not snoozed.
 *
 * [soundUri] is this alarm's own sound (a ringtone or audio-file URI), or null to use the
 * alarm sound from the settings.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val days: Int = 0,
    val snoozedUntil: Long = 0L,
    val soundUri: String? = null,
) {
    val repeats: Boolean get() = days != 0

    fun isSnoozed(nowMillis: Long = System.currentTimeMillis()): Boolean = snoozedUntil > nowMillis

    /** When this alarm will ring next: the snooze time if snoozed, otherwise the regular time. */
    fun nextRingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        if (isSnoozed(nowMillis)) snoozedUntil else nextTriggerMillis(nowMillis)

    fun isOnDay(calendarDay: Int): Boolean = days and dayBit(calendarDay) != 0

    /** The next time (epoch millis) this alarm should ring, strictly after [fromMillis]. */
    fun nextTriggerMillis(fromMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= fromMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (repeats) {
            // At most 6 more days until we reach a selected weekday.
            while (!isOnDay(cal.get(Calendar.DAY_OF_WEEK))) cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("label", label)
        .put("enabled", enabled)
        .put("days", days)
        .put("snoozedUntil", snoozedUntil)
        .apply { if (soundUri != null) put("sound", soundUri) }

    companion object {
        const val EVERY_DAY = 0b1111111
        val WEEKDAYS = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
            .fold(0) { acc, d -> acc or dayBit(d) }
        val WEEKEND = dayBit(Calendar.SATURDAY) or dayBit(Calendar.SUNDAY)

        fun dayBit(calendarDay: Int): Int = 1 shl (calendarDay - 1)

        fun fromJson(json: JSONObject) = Alarm(
            id = json.getInt("id"),
            hour = json.getInt("hour"),
            minute = json.getInt("minute"),
            label = json.optString("label", ""),
            enabled = json.optBoolean("enabled", true),
            // Version 1.x stored "repeatDaily" instead of a set of days.
            days = if (json.has("days")) {
                json.getInt("days") and EVERY_DAY
            } else if (json.optBoolean("repeatDaily", false)) {
                EVERY_DAY
            } else {
                0
            },
            snoozedUntil = json.optLong("snoozedUntil", 0L),
            soundUri = json.optString("sound").ifEmpty { null },
        )
    }
}
