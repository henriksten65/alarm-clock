package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** A typical phone screen for the UI tests (Robolectric's default is a small 320 × 470 dp). */
const val PHONE_SCREEN = "w411dp-h891dp-mdpi"

/** Shared helpers for the Robolectric tests. */
object TestSupport {

    val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * The app keeps its repository, settings and ringing state in process-wide singletons. Robolectric
     * gives every test a fresh application (and fresh SharedPreferences) but keeps static state, so
     * clear the singletons before each test. They are re-created from the test's own preferences.
     */
    fun resetAppState() {
        clearStatic(AlarmRepository::class.java, "instance")
        clearStatic(AppSettings::class.java, "instance")
        setRingingAlarmId(null)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    /** Simulates the app process being restarted: the singletons re-read what was saved. */
    fun restartProcess() {
        clearStatic(AlarmRepository::class.java, "instance")
        clearStatic(AppSettings::class.java, "instance")
    }

    /** Pretends an alarm is ringing (or nothing, for null), as [AlarmService] does while it runs. */
    @Suppress("UNCHECKED_CAST")
    fun setRingingAlarmId(id: Int?) {
        val field = AlarmService::class.java.getDeclaredField("_ringingAlarmId").apply { isAccessible = true }
        (field.get(null) as MutableStateFlow<Int?>).value = id
    }

    /** Use a fixed zone and locale so day and DST calculations are the same on every machine. */
    fun useZoneAndLocale(zone: String = "Europe/Berlin", locale: Locale = Locale.UK) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        Locale.setDefault(locale)
    }

    fun use24HourClock(enabled: Boolean) {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, if (enabled) "24" else "12")
    }

    val repository: AlarmRepository get() = AlarmRepository.getInstance(context)
    val settings: AppSettings get() = AppSettings.getInstance(context)

    val shadowAlarmManager: ShadowAlarmManager
        get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    /** The alarm clocks currently registered with AlarmManager, by trigger time. */
    fun scheduledTimes(): List<Long> = shadowAlarmManager.scheduledAlarms.map { it.triggerAtMs }.sorted()

    private fun clearStatic(owner: Class<*>, name: String) {
        owner.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
    }
}

/** Epoch millis for a wall-clock time in the default time zone. */
fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

/** The wall-clock fields of [millis] in the default time zone, e.g. "2026-03-29 07:00 SUN". */
fun describe(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val day = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")[c.get(Calendar.DAY_OF_WEEK) - 1]
    return "%04d-%02d-%02d %02d:%02d %s".format(
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), day,
    )
}
