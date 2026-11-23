package io.github.henriksten65.alarmclock.ui

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.PHONE_SCREEN
import io.github.henriksten65.alarmclock.TestSupport
import io.github.henriksten65.alarmclock.Timer
import io.github.henriksten65.alarmclock.TimerStatus
import org.junit.Assert.assertEquals
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
    fun startIsOffForZero() {
        showTimer()
        repeat(3) { // "500" → "50" → "5" → ""
            compose.onNodeWithContentDescription("Delete digit").performClick()
            settle()
        }
        compose.onNodeWithText("00h  00m  00s").assertExists()
        compose.onNodeWithText("Start").assertIsNotEnabled()
    }

    @Test
    fun startPauseResumeReset() {
        showTimer()
        tap("2"); tap("0")
        tap("Start")
        assertEquals(TimerStatus.RUNNING, Timer.current(context).status)
        assertEquals(20_000L, Timer.current(context).durationMillis)
        compose.onNodeWithText("0:20").assertExists()
        tap("Pause")
        compose.onNodeWithText("Paused").assertExists()
        tap("Resume")
        assertEquals(TimerStatus.RUNNING, Timer.current(context).status)
        tap("+1 min")
        assertEquals(80_000L, Timer.current(context).totalMillis)
        tap("Reset")
        assertEquals(TimerStatus.IDLE, Timer.current(context).status)
        // Back at the keypad with the time that was set.
        compose.onNodeWithText("00h  00m  20s").assertExists()
    }

    @Test
    fun ringingTimerShowsStop() {
        TestSupport.setRingingAlarmId(Timer.RING_ID)
        showTimer()
        compose.onNodeWithText("Time's up").assertExists()
        compose.onNodeWithText("Stop").assertExists()
        TestSupport.setRingingAlarmId(null)
    }
}
