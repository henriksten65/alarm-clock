package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.ui.millisToTimerDigits
import io.github.henriksten65.alarmclock.ui.timerDigitsToMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.Locale
import java.util.TimeZone

/** The countdown timer: start/pause/resume/+1 min/reset, its alarm and notification, ringing, reboots. */
@RunWith(AndroidJUnit4::class)
class TimerTest {

    private val context get() = TestSupport.context
    private val app get() = shadowOf(context as Application)
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private lateinit var savedZone: TimeZone
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        savedLocale = Locale.getDefault()
        TestSupport.resetAppState()
        TestSupport.useZoneAndLocale()
        TestSupport.use24HourClock(true)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private val minute = 60_000L
    private fun state() = Timer.current(context)
    private fun elapsed() = SystemClock.elapsedRealtime()
    private fun pass(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    private fun notification(): Notification? = shadowOf(manager).allNotifications.singleOrNull { it.channelId == Timer.CHANNEL_ID }
    private fun receive(action: String) = TimerReceiver().onReceive(context, Intent(action))
    private fun setBootCount(n: Int) = Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, n)

    @Test
    fun startSetsAnExactAlarmOnElapsedTime() {
        val before = elapsed()
        Timer.start(context, 5 * minute)
        assertEquals(TimerStatus.RUNNING, state().status)
        val alarm = TestSupport.timerAlarm!!
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type)
        assertEquals(before + 5 * minute, alarm.triggerAtMs)
        // Not an alarm clock: the system's "next alarm" stays the real alarm.
        assertNull(alarm.alarmClockInfo)
        assertTrue(TestSupport.scheduledTimes().isEmpty())
    }

    @Test
    fun runningTimerShowsACountdownInTheStatusBar() {
        Timer.start(context, 5 * minute)
        val n = notification()!!
        assertEquals("Timer", shadowOf(n).contentTitle)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(state().endWall, n.`when`)
        assertEquals(listOf("Pause", "+1 min", "Reset"), n.actions.map { it.title.toString() })
    }

    @Test
    fun channelShowsInTheStatusBarButIsSilent() {
        Timer.createNotificationChannel(context)
        val channel = manager.getNotificationChannel(Timer.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
    }

    @Test
    fun pauseKeepsWhatIsLeftAndCancelsTheAlarm() {
        Timer.start(context, 5 * minute)
        pass(minute)
        receive(TimerReceiver.ACTION_PAUSE)
        assertEquals(TimerStatus.PAUSED, state().status)
        assertEquals(4 * minute, state().remainingMillis)
        assertNull(TestSupport.timerAlarm)
        val n = notification()!!
        assertEquals("Timer paused", shadowOf(n).contentTitle)
        assertEquals("4:00", shadowOf(n).contentText)
        assertEquals(listOf("Resume", "+1 min", "Reset"), n.actions.map { it.title.toString() })
        // Paused time doesn't count.
        pass(10 * minute)
        assertEquals(4 * minute, state().remaining())
    }

    @Test
    fun resumeCarriesOnFromWhereItWas() {
        Timer.start(context, 5 * minute)
        pass(minute)
        Timer.pause(context)
        pass(10 * minute)
        receive(TimerReceiver.ACTION_RESUME)
        assertEquals(TimerStatus.RUNNING, state().status)
        assertEquals(elapsed() + 4 * minute, TestSupport.timerAlarm!!.triggerAtMs)
    }

    @Test
    fun addMinuteWhileRunningOrPaused() {
        Timer.start(context, 5 * minute)
        receive(TimerReceiver.ACTION_ADD_MINUTE)
        assertEquals(elapsed() + 6 * minute, TestSupport.timerAlarm!!.triggerAtMs)
        assertEquals(6 * minute, state().totalMillis)
        assertEquals(5 * minute, state().durationMillis)
        Timer.pause(context)
        Timer.addMinute(context)
        assertEquals(7 * minute, state().remainingMillis)
    }

    @Test
    fun resetClearsEverythingButRemembersTheDuration() {
        Timer.start(context, 7 * minute)
        receive(TimerReceiver.ACTION_RESET)
        assertEquals(TimerStatus.IDLE, state().status)
        assertEquals(7 * minute, state().durationMillis)
        assertNull(TestSupport.timerAlarm)
        assertNull(notification())
    }

    @Test
    fun whenItEndsItRingsLikeAnAlarm() {
        Timer.start(context, minute)
        pass(minute)
        receive(TimerReceiver.ACTION_DONE)
        assertEquals(TimerStatus.IDLE, state().status)
        assertNull(notification())
        val started = app.nextStartedService
        assertEquals(AlarmService::class.java.name, started.component?.className)
        assertEquals(Timer.RING_ID, started.getIntExtra(EXTRA_ALARM_ID, 0))
    }

    @Test
    fun aLateDoneForAResetTimerDoesNothing() {
        Timer.start(context, minute)
        Timer.reset(context)
        receive(TimerReceiver.ACTION_DONE)
        assertNull(app.nextStartedService)
    }

    @Test
    fun addMinuteOnARingingTimerStopsItAndRunsAMinute() {
        Timer.start(context, minute)
        Timer.onDone(context)
        TestSupport.setRingingAlarmId(Timer.RING_ID)
        Timer.addMinute(context)
        assertEquals(AlarmService::class.java.name, app.nextStoppedService?.component?.className)
        assertEquals(TimerStatus.RUNNING, state().status)
        assertEquals(elapsed() + minute, TestSupport.timerAlarm!!.triggerAtMs)
    }

    @Test
    fun ringingTimerNotificationSaysTimesUp() {
        val service = Robolectric.buildService(
            AlarmService::class.java,
            Intent(context, AlarmService::class.java).putExtra(EXTRA_ALARM_ID, Timer.RING_ID),
        ).create().startCommand(0, 1).get()
        val n = shadowOf(service).lastForegroundNotification
        assertEquals("Time's up", shadowOf(n).contentTitle)
        assertEquals(listOf("+1 min", "Stop"), n.actions.map { it.title.toString() })
        service.onDestroy()
    }

    @Test
    fun stateSurvivesTheAppBeingRestarted() {
        Timer.start(context, 5 * minute)
        Timer.pause(context)
        TestSupport.restartProcess()
        assertEquals(TimerStatus.PAUSED, state().status)
        assertEquals(5 * minute, state().remainingMillis)
    }

    @Test
    fun afterARebootARunningTimerCarriesOnByTheWallClock() {
        setBootCount(1)
        Timer.start(context, 30 * minute)
        val endWall = state().endWall
        setBootCount(2)
        TestSupport.restartProcess()
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(TimerStatus.RUNNING, state().status)
        assertTrue(Math.abs(state().endWall - endWall) < 1_000)
        assertNotNull(TestSupport.timerAlarm)
        assertNotNull(notification())
    }

    @Test
    fun afterARebootATimerThatEndedMeanwhileRingsIfOnlyALittleLate() {
        setBootCount(1)
        Timer.start(context, minute)
        Timer::class.java.getDeclaredMethod("save", android.content.Context::class.java, TimerState::class.java)
            .apply { isAccessible = true }
            .invoke(Timer, context, state().copy(endWall = System.currentTimeMillis() - 2 * minute))
        setBootCount(2)
        Timer.restore(context)
        assertEquals(Timer.RING_ID, app.nextStartedService?.getIntExtra(EXTRA_ALARM_ID, 0))
    }

    @Test
    fun afterARebootATimerThatEndedLongAgoIsJustReset() {
        setBootCount(1)
        Timer.start(context, minute)
        Timer::class.java.getDeclaredMethod("save", android.content.Context::class.java, TimerState::class.java)
            .apply { isAccessible = true }
            .invoke(Timer, context, state().copy(endWall = System.currentTimeMillis() - 60 * minute))
        setBootCount(2)
        Timer.restore(context)
        assertEquals(TimerStatus.IDLE, state().status)
        assertNull(app.nextStartedService)
        assertNull(notification())
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:01", formatDuration(1))
        assertEquals("0:59", formatDuration(59_000))
        assertEquals("1:30", formatDuration(90_000))
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("12:05:09", formatDuration((12 * 3600 + 5 * 60 + 9) * 1000L))
    }

    @Test
    fun keypadDigitsAreReadFromTheRight() {
        assertEquals(5_000L, timerDigitsToMillis("5"))
        assertEquals(90_000L, timerDigitsToMillis("130"))
        assertEquals(90_000L, timerDigitsToMillis("90"))
        assertEquals((1 * 3600 + 2 * 60 + 3) * 1000L, timerDigitsToMillis("10203"))
        assertEquals(0L, timerDigitsToMillis(""))
        assertEquals("500", millisToTimerDigits(5 * minute))
        assertEquals("10203", millisToTimerDigits((1 * 3600 + 2 * 60 + 3) * 1000L))
    }
}
