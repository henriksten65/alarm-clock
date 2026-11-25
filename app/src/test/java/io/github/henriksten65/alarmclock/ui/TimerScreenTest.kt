package io.github.henriksten65.alarmclock.ui

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.KeypadLayout
import io.github.henriksten65.alarmclock.PHONE_SCREEN
import io.github.henriksten65.alarmclock.TestSupport
import io.github.henriksten65.alarmclock.Timer
import io.github.henriksten65.alarmclock.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The Timer tab, driven like a user would. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_SCREEN)
class TimerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = TestSupport.context

    @Before
    fun setUp() = TestSupport.resetAppState()

    private fun settle() {
        compose.mainClock.advanceTimeBy(300)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
    }

    private fun showTabs() = compose.setContent {
        AlarmClockTheme {
            var tab by remember { mutableStateOf(Tab.ALARMS) }
            MainScreen(TestSupport.repository, TestSupport.settings, tab, onTabChange = { tab = it })
        }
    }

    private fun showTimer() = compose.setContent { AlarmClockTheme { TimerScreen() } }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        settle()
    }

    @Test
    fun settingsOpenFromTheTimerTab() {
        showTimer()
        compose.onNodeWithContentDescription("Settings").performClick()
        settle()
        compose.onNodeWithText("Appearance").assertExists()
    }

    @Test
    fun tabsSwitchBetweenAlarmsAndTimer() {
        showTabs()
        compose.onNodeWithText("No alarms yet.\nTap + to add one.").assertExists()
        tap("Timer")
        compose.onNodeWithText("Start").assertExists()
        tap("Alarms")
        compose.onNodeWithText("No alarms yet.\nTap + to add one.").assertExists()
    }

    @Test
    fun showsTheLastDurationAndTypingReplacesIt() {
        showTimer()
        compose.onNodeWithText("00h  05m  00s").assertExists()
        tap("1"); tap("3"); tap("0")
        compose.onNodeWithText("00h  01m  30s").assertExists()
        compose.onNodeWithContentDescription("Delete digit").performClick()
        settle()
        compose.onNodeWithText("00h  00m  13s").assertExists()
        tap("00")
        compose.onNodeWithText("00h  13m  00s").assertExists()
    }

    @Test
    fun calculatorLayoutPuts789OnTop() {
        val top = { key: String -> compose.onNodeWithText(key).getBoundsInRoot().top }
        showTimer() // the default layout
        assertTrue(top("7") < top("4"))
        assertTrue(top("4") < top("1"))
        assertTrue(top("1") < top("0"))
        tap("9"); tap("0")
        compose.onNodeWithText("00h  00m  90s").assertExists()
    }

    @Test
    fun phoneLayoutPuts123OnTop() {
        val top = { key: String -> compose.onNodeWithText(key).getBoundsInRoot().top }
        TestSupport.settings.setKeypadLayout(KeypadLayout.PHONE)
        showTimer()
        assertTrue(top("1") < top("4"))
        assertTrue(top("4") < top("7"))
        assertTrue(top("7") < top("0"))
    }

    @Test
    fun startIsOffForZero() {
        showTimer()
        repeat(3) { // "500" → "50" → "5" → ""
            compose.onNodeWithContentDescription("Delete digit").performClick()
            settle()
        }
        compose.onNodeWithText("00h  00m  00s").assertExists()
        compose.onNodeWithText("Start").assertIsNotEnabled()
    }

    private fun timers() = Timer.timers(context).value

    @Test
    fun startPauseResumeResetOnTheTimersCard() {
        showTimer()
        compose.onNode(hasSetTextAction() and hasText("Label")).performTextInput("Eggs")
        tap("2"); tap("0")
        tap("Start")
        val timer = timers().single()
        assertEquals(TimerStatus.RUNNING, timer.status)
        assertEquals("Eggs", timer.label)
        assertEquals(20_000L, timer.durationMillis)
        compose.onNodeWithText("Eggs").assertExists()
        compose.onNodeWithText("0:20").assertExists()
        tap("Pause")
        compose.onNodeWithText("Paused").assertExists()
        tap("Resume")
        assertEquals(TimerStatus.RUNNING, timers().single().status)
        tap("+1 min")
        assertEquals(80_000L, timers().single().totalMillis)
        tap("Reset")
        // The timer stays, with its set time, ready to start again.
        assertEquals(TimerStatus.IDLE, timers().single().status)
        compose.onNodeWithText("0:20").assertExists()
        tap("Start")
        assertEquals(TimerStatus.RUNNING, timers().single().status)
    }

    @Test
    fun addASecondTimer() {
        Timer.add(context, 60_000L, "Tea")
        showTimer()
        compose.onNodeWithText("Tea").assertExists()
        compose.onNodeWithContentDescription("Add timer").performClick()
        settle()
        compose.onNodeWithText("New timer").assertExists()
        // The keypad shows the last time used.
        compose.onNodeWithText("00h  01m  00s").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Label")).performTextInput("Eggs")
        tap("6"); tap("0"); tap("0")
        tap("Start")
        assertEquals(listOf("Tea", "Eggs"), timers().map { it.label })
        assertEquals(360_000L, timers()[1].durationMillis)
        compose.onNodeWithText("Tea").assertExists()
        compose.onNodeWithText("Eggs").assertExists()
    }

    @Test
    fun cancellingANewTimerGoesBackToTheList() {
        Timer.add(context, 60_000L, "Tea")
        showTimer()
        compose.onNodeWithContentDescription("Add timer").performClick()
        settle()
        compose.onNodeWithContentDescription("Cancel").performClick()
        settle()
        compose.onNodeWithText("Tea").assertExists()
        assertEquals(1, timers().size)
    }

    @Test
    fun deleteATimer() {
        Timer.add(context, 60_000L, "Tea")
        Timer.add(context, 300_000L)
        showTimer()
        compose.onNodeWithContentDescription("Delete Tea timer").performClick()
        settle()
        assertEquals(listOf(2), timers().map { it.id })
        // A timer without a label is named by its set time.
        compose.onNodeWithContentDescription("Delete 5:00 timer").performClick()
        settle()
        assertTrue(timers().isEmpty())
        // No timers left: straight to the keypad.
        compose.onNodeWithText("Start").assertExists()
    }

    @Test
    fun ringingTimerShowsTimesUpAndStop() {
        Timer.add(context, 60_000L, "Eggs")
        Timer.onDone(context, 1)
        TestSupport.setRingingAlarmId(Timer.ringId(1))
        showTimer()
        compose.onNodeWithText("Time's up").assertExists()
        compose.onNodeWithText("Stop").assertExists()
        TestSupport.setRingingAlarmId(null)
    }
}
