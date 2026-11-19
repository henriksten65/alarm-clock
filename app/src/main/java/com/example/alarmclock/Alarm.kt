package com.example.alarmclock

import org.json.JSONObject
import java.util.Calendar

const val EXTRA_ALARM_ID = "com.example.alarmclock.EXTRA_ALARM_ID"

/** A single alarm. Time is stored as wall-clock hour/minute in the device's time zone. */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDaily: Boolean = false,
) {
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
        return cal.timeInMillis
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("label", label)
        .put("enabled", enabled)
        .put("repeatDaily", repeatDaily)

    companion object {
        fun fromJson(json: JSONObject) = Alarm(
            id = json.getInt("id"),
            hour = json.getInt("hour"),
            minute = json.getInt("minute"),
            label = json.optString("label", ""),
            enabled = json.optBoolean("enabled", true),
            repeatDaily = json.optBoolean("repeatDaily", false),
        )
    }
}
