package io.github.henriksten65.alarmclock.ui

import android.app.NotificationManager
import android.media.AudioManager
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.Alarm
import io.github.henriksten65.alarmclock.AlarmScheduler
import io.github.henriksten65.alarmclock.PHONE_SCREEN
import io.github.henriksten65.alarmclock.TestSupport
import io.github.henriksten65.alarmclock.ThemeMode
import io.github.henriksten65.alarmclock.TimeEntryMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Duration
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The main screen, driven like a user would: add, edit, switch, delete/undo, skip, snooze, banners, settings. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_SCREEN)
class AlarmListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = TestSupport.context
    private val repo get() = TestSupport.repository
    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TestSupport.resetAppState()
        TestSupport.useZoneAndLocale()
        TestSupport.use24HourClock(true)
        // A healthy phone, so no warning banners unless a test asks for one.
        val audio = context.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    /**
     * Shows the screen. With [manualClock] the test clock only moves when the test says so
     * ([settle], `advanceTimeBy`), for tests that check timing (the 15 s Undo).
     */
    private fun show(manualClock: Boolean = false) {
        compose.mainClock.autoAdvance = !manualClock
        compose.setContent {
            AlarmClockTheme { AlarmListScreen(repo, TestSupport.settings) }
        }
        settle()
    }

    /** Lets clicks and state changes take effect (recomposition, dialogs, snackbars). */
    private fun settle() {
        compose.mainClock.advanceTimeBy(500)
        // Also move Robolectric's clock, which drives the frames of dialog windows.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }

    private fun add(alarm: Alarm): Alarm = alarm.also {
        repo.upsert(it)
        if (it.enabled) AlarmScheduler.schedule(context, it)
    }

    /** The big time button in the edit dialog, e.g. "07:30". */
    private val timeButton = hasClickAction() and SemanticsMatcher("shows a time") { node ->
        node.config.getOrElseNullable(SemanticsProperties.Text) { null }?.any { Regex("""\d\d:\d\d""").matches(it.text) } == true
    }

    @Test
    fun emptyList() {
        show()
        compose.onNodeWithText("No alarms yet.", substring = true).assertExists()
    }

    @Test
    fun addAnAlarm() {
        // (Typing the time is tested in KeyboardTimeDialogTest: Robolectric hangs when the typed-time
        // dialog is opened on top of this dialog.)
        show()

        compose.onNodeWithContentDescription("Add alarm").performClick()
        compose.onNodeWithText("New alarm").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Label")).performTextInput("  Gym ")
        compose.onNodeWithContentDescription("Monday").performClick()
        compose.onNodeWithContentDescription("Thursday").performClick()
        compose.onNodeWithText("Mon, Thu").assertExists()
        compose.onNodeWithText("Save").performClick()

        val alarm = repo.alarms.value.single()
        val now = Calendar.getInstance()
        assertEquals(
            Alarm(id = alarm.id, hour = now.get(Calendar.HOUR_OF_DAY), minute = now.get(Calendar.MINUTE), label = "Gym",
                days = Alarm.dayBit(Calendar.MONDAY) or Alarm.dayBit(Calendar.THURSDAY)),
            alarm,
        )
        assertEquals(listOf(alarm.nextRingMillis()), TestSupport.scheduledTimes())
        assertTrue(ShadowToast.getTextOfLatestToast().startsWith("Alarm set for"))

        compose.onNodeWithText("Gym · Mon, Thu").assertExists()
        compose.onNodeWithText("Next alarm:", substring = true).assertExists()
    }

    @Test
    fun cancellingTheNewAlarmDialogSavesNothing() {
        show()
        compose.onNodeWithContentDescription("Add alarm").performClick()
        settle()
        compose.onNodeWithText("Cancel").performClick()
        settle()
        assertTrue(repo.alarms.value.isEmpty())
        compose.onNodeWithText("New alarm").assertDoesNotExist()
    }

    @Test
    fun editAnAlarm() {
        add(Alarm(id = 1, hour = 7, minute = 0, label = "Work", days = Alarm.WEEKDAYS))
        show()
        compose.onNodeWithText("Work · Weekdays").performClick()
        settle()
        compose.onNodeWithText("Edit alarm").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Label", substring = true)).performTextReplacement("Early shift")
        settle()
        compose.onNodeWithText("Save").performClick()
        settle()

        assertEquals(Alarm(id = 1, hour = 7, minute = 0, label = "Early shift", days = Alarm.WEEKDAYS), repo.get(1))
        compose.onNodeWithText("Early shift · Weekdays").assertExists()
    }

    @Test
    fun changingTheDaysDropsASkip() {
        val base = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY)
        add(base.copy(skippedAt = base.nextOccurrenceMillis()))
        show()
        compose.onNodeWithText("Every day").performClick()
        settle()
        compose.onNodeWithContentDescription("Sunday").performClick()
        settle()
        compose.onNodeWithText("Save").performClick()
        settle()
        assertEquals(0L, repo.get(1)!!.skippedAt)
    }

    @Test
    fun switchingAnAlarmOffAndOn() {
        add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY))
        show()
        compose.onNode(isToggleable()).assertIsOn().performClick()
        settle()

        assertFalse(repo.get(1)!!.enabled)
        assertTrue(TestSupport.scheduledTimes().isEmpty())
        compose.onNodeWithText("No alarms on").assertExists()
        compose.onNodeWithText("Skip next").assertDoesNotExist()

        compose.onNode(isToggleable()).assertIsOff().performClick()
        settle()
        assertTrue(repo.get(1)!!.enabled)
        assertEquals(listOf(repo.get(1)!!.nextRingMillis()), TestSupport.scheduledTimes())
    }

    @Test
    fun switchingOffTheRingingAlarmStopsIt() {
        add(Alarm(id = 1, hour = 7, minute = 0))
        TestSupport.setRingingAlarmId(1)
        show()
        compose.onNode(isToggleable()).performClick()
        settle()
        assertEquals(
            io.github.henriksten65.alarmclock.AlarmService::class.java.name,
            shadowOf(context as android.app.Application).nextStoppedService?.component?.className,
        )
    }

    @Test
    fun deleteAndUndo() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0, label = "Work"))
        show()
        compose.onNodeWithContentDescription("Delete", substring = true).performClick()
        settle()

        assertTrue(repo.alarms.value.isEmpty())
        assertTrue(TestSupport.scheduledTimes().isEmpty())
        compose.onNodeWithText("Alarm deleted").assertExists()

        compose.onNodeWithText("Undo").performClick()
        settle()
        assertEquals(alarm, repo.get(1))
        assertEquals(listOf(alarm.nextRingMillis()), TestSupport.scheduledTimes())
        compose.onNodeWithText("Work · Once").assertExists()
    }

    @Test
    fun undoGoesAwayAfter15Seconds() {
        add(Alarm(id = 1, hour = 7, minute = 0))
        show(manualClock = true)
        compose.onNodeWithContentDescription("Delete", substring = true).performClick()
        settle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Undo").assertExists()
        compose.mainClock.advanceTimeBy(15_000)
        compose.onNodeWithText("Undo").assertDoesNotExist()
        assertTrue(repo.alarms.value.isEmpty())
    }

    @Test
    fun skipNextAndUndoTheSkip() {
        val alarm = add(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY))
        show()
        compose.onNodeWithText("Skip next").performClick()
        settle()

        assertTrue(repo.get(1)!!.isSkipping())
        compose.onNodeWithText("Skipping", substring = true).assertExists()
        assertEquals(listOf(alarm.nextOccurrenceMillis(alarm.nextOccurrenceMillis())), TestSupport.scheduledTimes())

        compose.onNodeWithText("Don't skip").performClick()
        settle()
        assertFalse(repo.get(1)!!.isSkipping())
        compose.onNodeWithText("Skip next").assertExists()
        assertEquals(listOf(alarm.nextOccurrenceMillis()), TestSupport.scheduledTimes())
    }

    @Test
    fun oneTimeAlarmsHaveNoSkip() {
        add(Alarm(id = 1, hour = 7, minute = 0))
        show()
        compose.onNodeWithText("Skip next").assertDoesNotExist()
    }

    @Test
    fun snoozedAlarmShowsItsSnoozeAndCanCancelIt() {
        val until = System.currentTimeMillis() + 10 * 60_000L
        add(Alarm(id = 1, hour = 7, minute = 0, snoozedUntil = until))
        show()
        compose.onNodeWithText("Snoozed until", substring = true).assertExists()
        compose.onNodeWithText("Cancel snooze").performClick()
        settle()

        assertEquals(Alarm(id = 1, hour = 7, minute = 0, enabled = false), repo.get(1))
        compose.onNodeWithText("Snoozed until", substring = true).assertDoesNotExist()
    }

    @Test
    fun ringingCardCanSnoozeTheAlarm() {
        add(Alarm(id = 1, hour = 7, minute = 0, label = "Work", enabled = false))
        TestSupport.setRingingAlarmId(1)
        show()
        compose.onNodeWithText("Ringing: Work · 07:00").assertExists()
        compose.onNodeWithText("Snooze").performClick()
        settle()
        assertTrue(repo.get(1)!!.isSnoozed())
    }

    @Test
    fun noRingingCardWhileNothingRings() {
        add(Alarm(id = 1, hour = 7, minute = 0))
        show()
        compose.onNodeWithText("Ringing", substring = true).assertDoesNotExist()
    }

    @Test
    fun bannerWhenNotificationsAreOff() {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        show()
        compose.onNodeWithText("Notifications are off", substring = true).assertExists()
    }

    @Test
    fun bannerWhenDoNotDisturbSilencesAlarms() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        // Takes precedence over the volume banner.
        context.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        show()
        compose.onNodeWithText("Do Not Disturb is silencing alarms.").assertExists()
        compose.onNodeWithText("Alarm volume is at its lowest", substring = true).assertDoesNotExist()
    }

    @Test
    fun lowVolumeBannerTurnsTheVolumeUp() {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        show()
        compose.onNodeWithText("Alarm volume is at its lowest", substring = true).assertExists()
        compose.onNodeWithText("Turn up").performClick()
        settle()
        assertTrue(audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0)
        compose.onNodeWithText("Alarm volume is at its lowest", substring = true).assertDoesNotExist()
    }

    @Test
    fun settingsDialogChangesTheSettings() {
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        settle()

        compose.onNodeWithText("Keyboard (type the digits)").performClick()
        settle()
        assertEquals(TimeEntryMode.KEYBOARD, TestSupport.settings.timeEntryMode.value)
        compose.onNodeWithText("Typed times use 24-hour clock").performClick()
        settle()
        assertTrue(TestSupport.settings.keyboard24Hour.value)

        compose.onNodeWithText("Vibrate").performScrollTo().performClick()
        settle()
        assertFalse(TestSupport.settings.vibrate.value)

        compose.onNodeWithText("Dark").performScrollTo().performClick()
        settle()
        assertEquals(ThemeMode.DARK, TestSupport.settings.themeMode.value)

        compose.onNodeWithText("Default alarm sound").performScrollTo().assertExists()
        settle()
        compose.onNodeWithText("Done").performClick()
        settle()
        compose.onNodeWithText("Appearance").assertDoesNotExist()
    }

    @Test
    fun twentyFourHourSwitchIsOnlyForTypedTimes() {
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        settle()
        compose.onNodeWithText("Typed times use 24-hour clock").performClick()
        settle()
        assertFalse("disabled while the clock dial is chosen", TestSupport.settings.keyboard24Hour.value)
    }
}
