package io.github.henriksten65.alarmclock

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

/** Order of the digit rows on the app's number keypads (Timer, typed alarm times). */
enum class KeypadLayout {
    /** Like a calculator: 7 8 9 on the top row. The default. */
    CALCULATOR,

    /** Like a phone: 1 2 3 on the top row. */
    PHONE,
}

/** Light or dark colours for the app's screens. */
enum class ThemeMode {
    /** Follow the phone's dark theme setting. */
    SYSTEM,
    LIGHT,
    DARK,
}

/** User preferences, stored in SharedPreferences and exposed as [StateFlow]s for the UI. */
class AppSettings private constructor(context: Context) {

    private val prefs = deviceProtectedPrefs(context, PREFS_NAME)

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

    /** How long Snooze waits before the alarm rings again. */
    private val _snoozeMinutes = MutableStateFlow(prefs.getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES))
    val snoozeMinutes: StateFlow<Int> = _snoozeMinutes.asStateFlow()

    fun setSnoozeMinutes(minutes: Int) {
        _snoozeMinutes.value = minutes
        prefs.edit { putInt(KEY_SNOOZE_MINUTES, minutes) }
    }

    /** How long an alarm rings before it stops by itself (and a "Missed alarm" notification is shown). */
    private val _autoStopMinutes = MutableStateFlow(prefs.getInt(KEY_AUTO_STOP_MINUTES, DEFAULT_AUTO_STOP_MINUTES))
    val autoStopMinutes: StateFlow<Int> = _autoStopMinutes.asStateFlow()

    fun setAutoStopMinutes(minutes: Int) {
        _autoStopMinutes.value = minutes
        prefs.edit { putInt(KEY_AUTO_STOP_MINUTES, minutes) }
    }

    /** Whether the phone vibrates while an alarm rings. */
    private val _vibrate = MutableStateFlow(prefs.getBoolean(KEY_VIBRATE, true))
    val vibrate: StateFlow<Boolean> = _vibrate.asStateFlow()

    fun setVibrate(enabled: Boolean) {
        _vibrate.value = enabled
        prefs.edit { putBoolean(KEY_VIBRATE, enabled) }
    }

    /** Whether repeating alarms' cards show a "Skip next" button (on by default). */
    private val _showSkipNext = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SKIP_NEXT, true))
    val showSkipNext: StateFlow<Boolean> = _showSkipNext.asStateFlow()

    fun setShowSkipNext(enabled: Boolean) {
        _showSkipNext.value = enabled
        prefs.edit { putBoolean(KEY_SHOW_SKIP_NEXT, enabled) }
    }

    /** Phone (1 2 3 at the top) or calculator (7 8 9 at the top) order for the number keypads. */
    private val _keypadLayout = MutableStateFlow(
        prefs.getString(KEY_KEYPAD_LAYOUT, null)
            ?.let { name -> KeypadLayout.entries.firstOrNull { it.name == name } }
            ?: KeypadLayout.CALCULATOR,
    )
    val keypadLayout: StateFlow<KeypadLayout> = _keypadLayout.asStateFlow()

    fun setKeypadLayout(layout: KeypadLayout) {
        _keypadLayout.value = layout
        prefs.edit { putString(KEY_KEYPAD_LAYOUT, layout.name) }
    }

    /** Whether the app uses light or dark colours, or follows the phone. */
    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    companion object {
        val SNOOZE_MINUTE_OPTIONS = listOf(1, 5, 10, 15, 20, 30)
        val AUTO_STOP_MINUTE_OPTIONS = listOf(1, 5, 10, 15, 20, 30)
        private const val DEFAULT_SNOOZE_MINUTES = 10
        private const val DEFAULT_AUTO_STOP_MINUTES = 10

        private const val PREFS_NAME = "settings"
        private const val KEY_TIME_ENTRY_MODE = "time_entry_mode"
        private const val KEY_KEYBOARD_24_HOUR = "keyboard_24_hour"
        private const val KEY_ALARM_SOUND = "alarm_sound_uri"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_AUTO_STOP_MINUTES = "auto_stop_minutes"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_KEYPAD_LAYOUT = "keypad_layout"
        private const val KEY_SHOW_SKIP_NEXT = "show_skip_next"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
