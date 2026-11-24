package io.github.henriksten65.alarmclock.ui

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.PHONE_SCREEN
import io.github.henriksten65.alarmclock.Stopwatch
import io.github.henriksten65.alarmclock.StopwatchStatus
import io.github.henriksten65.alarmclock.TestSupport
import io.github.henriksten65.alarmclock.formatStopwatch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The Stopwatch tab, driven like a user would. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_SCREEN)
class StopwatchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = TestSupport.context

    @Before
    fun setUp() = TestSupport.resetAppState()

    /** Moves both clocks: the stopwatch's (elapsed time) and the screen's ticking. */
    private fun pass(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        pass(100)
    }

    private fun showStopwatch() = compose.setContent { AlarmClockTheme { StopwatchScreen() } }

    @Test
    fun stopwatchIsTheThirdTab() {
        compose.setContent {
            AlarmClockTheme {
                var tab by remember { mutableStateOf(Tab.ALARMS) }
                MainScreen(TestSupport.repository, TestSupport.settings, tab, onTabChange = { tab = it })
            }
        }
        tap("Stopwatch")
        compose.onNodeWithText("0:00.00").assertExists()
        compose.onNodeWithText("Start").assertExists()
        tap("Timer")
        compose.onNodeWithText("0:00.00").assertDoesNotExist()
    }

    @Test
    fun startLapPauseResumeReset() {
        showStopwatch()
        compose.onNodeWithText("0:00.00").assertExists()
        tap("Start")
        assertEquals(StopwatchStatus.RUNNING, Stopwatch.state(context).value.status)
        compose.onNodeWithText("Lap").assertExists()

        pass(3_000)
        tap("Lap")
        pass(2_000)
        // The finished lap and the one in progress, newest on top.
        val lap1 = formatStopwatch(Stopwatch.state(context).value.laps.single())
        compose.onNodeWithContentDescription("Lap 1: $lap1, total $lap1").assertExists()
        compose.onNode(hasContentDescription("Lap 2: ", substring = true)).assertExists()

        tap("Pause")
        compose.onNodeWithText("Paused").assertExists()
        val shown = Stopwatch.state(context).value.elapsed()
        compose.onNodeWithText(formatStopwatch(shown)).assertExists()

        tap("Resume")
        tap("Pause")
        tap("Reset")
        compose.onNodeWithText("0:00.00").assertExists()
        compose.onNodeWithText("Start").assertExists()
        compose.onNode(hasContentDescription("Lap 1: ", substring = true)).assertDoesNotExist()
    }

    @Test
    fun showsAStopwatchThatWasAlreadyRunning() {
        Stopwatch.start(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(65))
        Stopwatch.pause(context)
        showStopwatch()
        compose.onNodeWithText("1:05.00").assertExists()
        compose.onNodeWithText("Resume").assertExists()
    }
}
