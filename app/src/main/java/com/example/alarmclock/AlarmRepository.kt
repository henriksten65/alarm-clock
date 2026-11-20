package com.example.alarmclock

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException

/**
 * Stores alarms in SharedPreferences as JSON and exposes them as a [StateFlow]
 * so the UI updates automatically (e.g. when a one-time alarm disables itself after ringing).
 */
class AlarmRepository private constructor(context: Context) {

    private val prefs = deviceProtectedPrefs(context, PREFS_NAME)
    private val _alarms = MutableStateFlow(load())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    @Synchronized
    fun nextId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun upsert(alarm: Alarm) = save(_alarms.value.filterNot { it.id == alarm.id } + alarm)

    @Synchronized
    fun delete(id: Int) = save(_alarms.value.filterNot { it.id == id })

    private fun load(): List<Alarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { Alarm.fromJson(array.getJSONObject(it)) }.sortedWith(ORDER)
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun save(list: List<Alarm>) {
        val sorted = list.sortedWith(ORDER)
        _alarms.value = sorted
        prefs.edit { putString(KEY_ALARMS, JSONArray(sorted.map { it.toJson() }).toString()) }
    }

    companion object {
        private const val PREFS_NAME = "alarms"
        private const val KEY_ALARMS = "alarms"
        private val ORDER = compareBy<Alarm>({ it.hour }, { it.minute }, { it.id })

        @Volatile
        private var instance: AlarmRepository? = null

        fun getInstance(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(context).also { instance = it }
            }
    }
}
