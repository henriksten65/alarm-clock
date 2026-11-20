package com.example.alarmclock

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the alarm time is entered in the edit dialog. */
enum class TimeEntryMode {
    /** Material clock face: tap or drag the hour, then the minute. */
    DIAL,

    /** Hour and minute text fields typed on the numeric on-screen keyboard. */
    KEYBOARD,
}

/** User preferences, stored in SharedPreferences and exposed as [StateFlow]s for the UI. */
class AppSettings private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _timeEntryMode = MutableStateFlow(
        prefs.getString(KEY_TIME_ENTRY_MODE, null)
            ?.let { name -> TimeEntryMode.entries.firstOrNull { it.name == name } }
            ?: TimeEntryMode.DIAL,
    )
    val timeEntryMode: StateFlow<TimeEntryMode> = _timeEntryMode.asStateFlow()

    fun setTimeEntryMode(mode: TimeEntryMode) {
        _timeEntryMode.value = mode
        prefs.edit { putString(KEY_TIME_ENTRY_MODE, mode.name) }
    }

    /** When true, digits typed in [TimeEntryMode.KEYBOARD] are always read as a 24-hour time (no AM/PM). */
    private val _keyboard24Hour = MutableStateFlow(prefs.getBoolean(KEY_KEYBOARD_24_HOUR, false))
    val keyboard24Hour: StateFlow<Boolean> = _keyboard24Hour.asStateFlow()

    fun setKeyboard24Hour(enabled: Boolean) {
        _keyboard24Hour.value = enabled
        prefs.edit { putBoolean(KEY_KEYBOARD_24_HOUR, enabled) }
    }

    /** Sound played when an alarm rings: a ringtone or audio-file URI, or null for the default alarm sound. */
    private val _alarmSoundUri = MutableStateFlow(prefs.getString(KEY_ALARM_SOUND, null))
    val alarmSoundUri: StateFlow<String?> = _alarmSoundUri.asStateFlow()

    fun setAlarmSoundUri(uri: String?) {
        _alarmSoundUri.value = uri
        prefs.edit { putString(KEY_ALARM_SOUND, uri) }
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_TIME_ENTRY_MODE = "time_entry_mode"
        private const val KEY_KEYBOARD_24_HOUR = "keyboard_24_hour"
        private const val KEY_ALARM_SOUND = "alarm_sound_uri"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
