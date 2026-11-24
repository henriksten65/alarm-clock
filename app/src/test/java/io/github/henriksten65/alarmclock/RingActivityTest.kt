package io.github.henriksten65.alarmclock

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/** The full-screen ring screen: label, Dismiss, Snooze, Back while ringing, closing when the alarm stops. */
@RunWith(AndroidJUnit4::class)
class RingActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = TestSupport.context
    private val repo get() = TestSupport.repository

    @Before
    fun setUp() = TestSupport.resetAppState()

    private fun launch(alarmId: Int): ActivityScenario<RingActivity> =
        ActivityScenario.launch(Intent(context, RingActivity::class.java).putExtra(EXTRA_ALARM_ID, alarmId))

    private fun stoppedService() = shadowOf(context as Application).nextStoppedService?.component?.className

    private fun ActivityScenario<RingActivity>.isFinishing(): Boolean {
        var finishing = false
        if (state == Lifecycle.State.DESTROYED) return true
        onActivity { finishing = it.isFinishing }
        return finishing
    }

    @Test
    fun showsTheLabelAndSnoozeLength() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, label = "Work"))
        TestSupport.settings.setSnoozeMinutes(5)
        launch(1)
        compose.onNodeWithText("Work").assertExists()
        compose.onNodeWithText("Snooze 5 min").assertExists()
    }

    @Test
    fun aTimerSaysTimesUpWithItsLabel() {
        Timer.add(context, 60_000L, "Eggs")
        Timer.onDone(context, 1)
        TestSupport.setRingingAlarmId(Timer.ringId(1))
        launch(Timer.ringId(1))
        compose.onNodeWithText("Time's up: Eggs").assertExists()
    }

    @Test
    fun followsASecondTimerThatStartsRingingWhileItShows() {
        Timer.add(context, 60_000L, "Tea")
        Timer.add(context, 60_000L, "Eggs")
        Timer.onDone(context, 1)
        TestSupport.setRingingAlarmId(Timer.ringId(1))
        launch(Timer.ringId(1))
        compose.onNodeWithText("Time's up: Tea").assertExists()

        // Eggs ends too: the service rings it now, and this screen's +1 min is for Eggs.
        Timer.onDone(context, 2)
        TestSupport.setRingingAlarmId(Timer.ringId(2))
        compose.onNodeWithText("Time's up: Eggs").assertExists()
        compose.onNodeWithText("+1 min").performClick()
        assertEquals(TimerStatus.RUNNING, Timer.get(context, 2)!!.status)
        assertEquals(TimerStatus.IDLE, Timer.get(context, 1)!!.status)
    }

    @Test
    fun withoutALabelItSaysAlarm() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        launch(1)
        compose.onNodeWithText("Alarm").assertExists()
    }

    @Test
    fun dismissStopsRingingAndCloses() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        TestSupport.setRingingAlarmId(1)
        val scenario = launch(1)
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(AlarmService::class.java.name, stoppedService())
        assertTrue(scenario.isFinishing())
        assertFalse(repo.get(1)!!.isSnoozed())
    }

    @Test
    fun snoozeSnoozesAndCloses() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, enabled = false))
        TestSupport.setRingingAlarmId(1)
        val scenario = launch(1)
        compose.onNodeWithText("Snooze 10 min").performClick()
        assertTrue(repo.get(1)!!.isSnoozed())
        assertEquals(AlarmService::class.java.name, stoppedService())
        assertTrue(scenario.isFinishing())
    }

    @Test
    fun backWhileRingingKeepsTheScreenAndExplains() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        TestSupport.setRingingAlarmId(1)
        val scenario = launch(1)
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertFalse(scenario.isFinishing())
        assertEquals("Tap Dismiss or Snooze to stop the alarm", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun backAfterTheAlarmStoppedCloses() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        val scenario = launch(1)
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertTrue(scenario.isFinishing())
    }

    @Test
    fun closesWhenTheAlarmStopsElsewhere() {
        // E.g. Dismiss in the notification, or the alarm stopping by itself.
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        TestSupport.setRingingAlarmId(1)
        val scenario = launch(1)
        context.sendBroadcast(Intent(AlarmService.ACTION_ALARM_STOPPED).setPackage(context.packageName))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(scenario.isFinishing())
    }

    @Test
    fun showsOverTheLockScreen() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        launch(1).onActivity {
            assertTrue(shadowOf(it).turnScreenOn)
            assertTrue(shadowOf(it).showWhenLocked)
        }
    }
}
