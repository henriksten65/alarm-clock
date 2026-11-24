package io.github.henriksten65.alarmclock

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** The stopwatch: start/pause/resume/lap/reset, its notification and buttons, restarts and reboots. */
@RunWith(AndroidJUnit4::class)
class StopwatchTest {

    private val context get() = TestSupport.context
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() = TestSupport.resetAppState()

    private fun state() = Stopwatch.state(context).value
    private fun pass(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    private fun notification(): Notification? =
        shadowOf(manager).allNotifications.singleOrNull { it.channelId == Stopwatch.CHANNEL_ID }
    private fun actions() = notification()!!.actions.map { it.title.toString() }
    private fun receive(action: String) = StopwatchReceiver().onReceive(context, Intent(action))
    private fun setState(state: StopwatchState) =
        Stopwatch::class.java.getDeclaredMethod("save", android.content.Context::class.java, StopwatchState::class.java)
            .apply { isAccessible = true }
            .invoke(Stopwatch, context, state)

    @Test
    fun countsWhileRunningAndNotWhilePaused() {
        assertEquals(StopwatchStatus.IDLE, state().status)
        assertEquals(0L, state().elapsed())

        Stopwatch.start(context)
        pass(5_000)
        assertEquals(5_000L, state().elapsed())

        Stopwatch.pause(context)
        pass(10_000)
        assertEquals(StopwatchStatus.PAUSED, state().status)
        assertEquals(5_000L, state().elapsed())

        Stopwatch.resume(context)
        pass(2_500)
        assertEquals(StopwatchStatus.RUNNING, state().status)
        assertEquals(7_500L, state().elapsed())
    }

    @Test
    fun startDoesNothingWhileRunningOrPaused() {
        Stopwatch.start(context)
        pass(3_000)
        Stopwatch.start(context)
        assertEquals(3_000L, state().elapsed())
        Stopwatch.pause(context)
        Stopwatch.start(context)
        assertEquals(StopwatchStatus.PAUSED, state().status)
    }

    @Test
    fun lapsKeepTheTotalsOnlyWhileRunning() {
        Stopwatch.lap(context)
        assertTrue(state().laps.isEmpty())
        Stopwatch.start(context)
        pass(4_000)
        Stopwatch.lap(context)
        pass(6_000)
        Stopwatch.lap(context)
        assertEquals(listOf(4_000L, 10_000L), state().laps)

        Stopwatch.pause(context)
        Stopwatch.lap(context)
        assertEquals(2, state().laps.size)
    }

    @Test
    fun resetClearsTimeAndLapsAndTheNotification() {
        Stopwatch.start(context)
        pass(4_000)
        Stopwatch.lap(context)
        Stopwatch.pause(context)
        Stopwatch.reset(context)
        assertEquals(StopwatchState(), state())
        assertNull(notification())
    }

    @Test
    fun runningNotificationCountsUpWithPauseAndLap() {
        Stopwatch.start(context)
        pass(5_000)
        Stopwatch.lap(context)
        val n = notification()!!
        assertEquals("Stopwatch", shadowOf(n).contentTitle)
        assertTrue(shadowOf(n).isWhenShown)
        assertEquals((System.currentTimeMillis() - 5_000L).toDouble(), n.`when`.toDouble(), 1_000.0)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(false, n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals("Lap 2", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Pause", "Lap"), actions())
    }

    @Test
    fun pausedNotificationShowsTheTimeWithResumeAndReset() {
        Stopwatch.start(context)
        pass(65_430)
        Stopwatch.pause(context)
        val n = notification()!!
        assertEquals("Stopwatch paused", shadowOf(n).contentTitle)
        assertEquals("1:05.43", shadowOf(n).contentText)
        assertEquals(listOf("Resume", "Reset"), actions())
    }

    @Test
    fun notificationButtonsWork() {
        Stopwatch.start(context)
        pass(1_000)
        receive(StopwatchReceiver.ACTION_LAP)
        assertEquals(listOf(1_000L), state().laps)
        receive(StopwatchReceiver.ACTION_PAUSE)
        assertEquals(StopwatchStatus.PAUSED, state().status)
        receive(StopwatchReceiver.ACTION_RESUME)
        assertEquals(StopwatchStatus.RUNNING, state().status)
        receive(StopwatchReceiver.ACTION_PAUSE)
        receive(StopwatchReceiver.ACTION_RESET)
        assertEquals(StopwatchStatus.IDLE, state().status)
        assertNull(notification())
    }

    @Test
    fun tappingTheNotificationOpensTheStopwatchTab() {
        Stopwatch.start(context)
        val open = shadowOf(notification()!!.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertEquals(MainActivity.TAB_STOPWATCH, open.getStringExtra(MainActivity.EXTRA_TAB))
    }

    @Test
    fun survivesTheAppBeingRestarted() {
        Stopwatch.start(context)
        pass(3_000)
        Stopwatch.lap(context)
        TestSupport.restartProcess()
        pass(2_000)
        assertEquals(StopwatchStatus.RUNNING, state().status)
        assertEquals(5_000L, state().elapsed())
        assertEquals(listOf(3_000L), state().laps)
    }

    @Test
    fun afterARebootTheTimeComesFromTheWallClock() {
        Stopwatch.start(context)
        // Started an hour ago on the wall clock, in an earlier boot.
        setState(state().copy(startWall = System.currentTimeMillis() - 3_600_000L, bootCount = 99, startElapsed = 0L))
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 100)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(StopwatchStatus.RUNNING, state().status)
        // The wall clock really ticks under Robolectric, so allow a little.
        assertEquals(3_600_000.0, state().elapsed().toDouble(), 1_000.0)
        assertEquals(100, state().bootCount)
    }

    @Test
    fun aClockChangeKeepsTheElapsedTime() {
        Stopwatch.start(context)
        pass(7_000)
        setState(state().copy(startWall = 0L)) // as if the wall clock jumped
        BootReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        assertEquals(7_000L, state().elapsed())
        assertEquals((System.currentTimeMillis() - 7_000L).toDouble(), state().startWall.toDouble(), 1_000.0)
    }

    @Test
    fun aPausedStopwatchGetsItsNotificationBackAfterABoot() {
        Stopwatch.start(context)
        pass(2_000)
        Stopwatch.pause(context)
        manager.cancelAll()
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals("Stopwatch paused", shadowOf(notification()!!).contentTitle)
    }

    @Test
    fun formatsWithHundredths() {
        assertEquals("0:00.00", formatStopwatch(0))
        assertEquals("0:09.42", formatStopwatch(9_429))
        assertEquals("5:09.42", formatStopwatch(309_420))
        assertEquals("1:05:09.42", formatStopwatch(3_909_420))
        assertEquals("0:00.00", formatStopwatch(-5))
    }
}
