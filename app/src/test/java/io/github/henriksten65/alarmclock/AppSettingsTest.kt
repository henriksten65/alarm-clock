package io.github.henriksten65.alarmclock

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsTest {

    @Before
    fun setUp() = TestSupport.resetAppState()

    @Test
    fun defaults() {
        val settings = TestSupport.settings
        assertEquals(TimeEntryMode.DIAL, settings.timeEntryMode.value)
        assertFalse(settings.keyboard24Hour.value)
        assertNull(settings.alarmSoundUri.value)
        assertEquals(10, settings.snoozeMinutes.value)
        assertEquals(10, settings.autoStopMinutes.value)
        assertTrue(settings.vibrate.value)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.value)
    }

    @Test
    fun changesShowUpRightAwayAndSurviveARestart() {
        TestSupport.settings.apply {
            setTimeEntryMode(TimeEntryMode.KEYBOARD)
            setKeyboard24Hour(true)
            setAlarmSoundUri("content://media/audio/5")
            setSnoozeMinutes(5)
            setAutoStopMinutes(20)
            setVibrate(false)
            setThemeMode(ThemeMode.DARK)
            assertEquals(TimeEntryMode.KEYBOARD, timeEntryMode.value)
            assertEquals(ThemeMode.DARK, themeMode.value)
        }

        TestSupport.restartProcess()

        TestSupport.settings.apply {
            assertEquals(TimeEntryMode.KEYBOARD, timeEntryMode.value)
            assertTrue(keyboard24Hour.value)
            assertEquals("content://media/audio/5", alarmSoundUri.value)
            assertEquals(5, snoozeMinutes.value)
            assertEquals(20, autoStopMinutes.value)
            assertFalse(vibrate.value)
            assertEquals(ThemeMode.DARK, themeMode.value)
        }
    }

    @Test
    fun soundCanBeResetToTheDefault() {
        TestSupport.settings.setAlarmSoundUri("content://media/audio/5")
        TestSupport.settings.setAlarmSoundUri(null)
        TestSupport.restartProcess()
        assertNull(TestSupport.settings.alarmSoundUri.value)
    }

    @Test
    fun unknownStoredValuesFallBackToTheDefaults() {
        deviceProtectedPrefs(TestSupport.context, "settings").edit()
            .putString("time_entry_mode", "VOICE")
            .putString("theme_mode", "SEPIA")
            .commit()
        assertEquals(TimeEntryMode.DIAL, TestSupport.settings.timeEntryMode.value)
        assertEquals(ThemeMode.SYSTEM, TestSupport.settings.themeMode.value)
    }

    @Test
    fun defaultsAreAmongTheOfferedOptions() {
        assertTrue(10 in AppSettings.SNOOZE_MINUTE_OPTIONS)
        assertTrue(10 in AppSettings.AUTO_STOP_MINUTE_OPTIONS)
    }
}
