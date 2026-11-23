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

/** The countdown timers: add/start/pause/resume/+1 min/reset/delete, labels, their alarms and notifications, ringing, reboots. */
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
    private fun state(id: Int = 1) = Timer.get(context, id)!!
    private fun elapsed() = SystemClock.elapsedRealtime()
    private fun pass(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    private fun notifications(): List<Notification> = shadowOf(manager).allNotifications.filter { it.channelId == Timer.CHANNEL_ID }
    private fun notification(): Notification? = notifications().singleOrNull()
    private fun receive(action: String, id: Int = 1) =
        TimerReceiver().onReceive(context, Intent(action).putExtra(TimerReceiver.EXTRA_TIMER_ID, id))
    private fun setState(state: TimerState) =
        Timer::class.java.getDeclaredMethod("save", android.content.Context::class.java, TimerState::class.java)
            .apply { isAccessible = true }
            .invoke(Timer, context, state)
    private fun setBootCount(n: Int) = Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, n)

    @Test
    fun addStartsATimerWithAnExactAlarmOnElapsedTime() {
        val before = elapsed()
        val id = Timer.add(context, 5 * minute, "  Eggs ")
        assertEquals(1, id)
        assertEquals(TimerStatus.RUNNING, state().status)
        assertEquals("Eggs", state().label)
        val alarm = TestSupport.timerAlarm(id)!!
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type)
        assertEquals(before + 5 * minute, alarm.triggerAtMs)
        // Not an alarm clock: the system's "next alarm" stays the real alarm.
        assertNull(alarm.alarmClockInfo)
        assertTrue(TestSupport.scheduledTimes().isEmpty())
        assertEquals(5 * minute, Timer.lastDuration(context).value)
    }

    @Test
    fun runningTimerShowsACountdownInTheStatusBar() {
        Timer.add(context, 5 * minute)
        val n = notification()!!
        assertEquals("Timer", shadowOf(n).contentTitle)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(state().endWall, n.`when`)
        assertEquals(listOf("Pause", "+1 min", "Reset"), n.actions.map { it.title.toString() })
    }

    @Test
    fun notificationsAreNamedByTheLabel() {
        Timer.add(context, 5 * minute, "Eggs")
        assertEquals("Eggs", shadowOf(notification()!!).contentTitle)
        Timer.pause(context, 1)
        assertEquals("Eggs paused", shadowOf(notification()!!).contentTitle)
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
        Timer.add(context, 5 * minute)
        pass(minute)
        receive(TimerReceiver.ACTION_PAUSE)
        assertEquals(TimerStatus.PAUSED, state().status)
        assertEquals(4 * minute, state().remainingMillis)
        assertNull(TestSupport.timerAlarm(1))
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
        Timer.add(context, 5 * minute)
        pass(minute)
        Timer.pause(context, 1)
        pass(10 * minute)
        receive(TimerReceiver.ACTION_RESUME)
        assertEquals(TimerStatus.RUNNING, state().status)
        assertEquals(elapsed() + 4 * minute, TestSupport.timerAlarm(1)!!.triggerAtMs)
    }

    @Test
    fun addMinuteWhileRunningOrPaused() {
        Timer.add(context, 5 * minute)
        receive(TimerReceiver.ACTION_ADD_MINUTE)
        assertEquals(elapsed() + 6 * minute, TestSupport.timerAlarm(1)!!.triggerAtMs)
        assertEquals(6 * minute, state().totalMillis)
        assertEquals(5 * minute, state().durationMillis)
        Timer.pause(context, 1)
        Timer.addMinute(context, 1)
        assertEquals(7 * minute, state().remainingMillis)
    }

    @Test
    fun resetKeepsTheTimerWithItsSetTime() {
        Timer.add(context, 7 * minute, "Tea")
        receive(TimerReceiver.ACTION_RESET)
        assertEquals(TimerStatus.IDLE, state().status)
        assertEquals(7 * minute, state().durationMillis)
        assertEquals("Tea", state().label)
        assertNull(TestSupport.timerAlarm(1))
        assertNull(notification())
        // Start runs it again for its set time.
        Timer.start(context, 1)
        assertEquals(elapsed() + 7 * minute, TestSupport.timerAlarm(1)!!.triggerAtMs)
    }

    @Test
    fun timersRunIndependently() {
        val eggs = Timer.add(context, 6 * minute, "Eggs")
        val tea = Timer.add(context, 3 * minute, "Tea")
        assertEquals(listOf(1, 2), listOf(eggs, tea))
        assertEquals(2, TestSupport.timerAlarms.size)
        assertEquals(listOf("Eggs", "Tea"), notifications().map { shadowOf(it).contentTitle.toString() }.sorted())

        // A button on one notification only affects its own timer.
        receive(TimerReceiver.ACTION_PAUSE, tea)
        assertEquals(TimerStatus.RUNNING, state(eggs).status)
        assertEquals(TimerStatus.PAUSED, state(tea).status)
        assertNotNull(TestSupport.timerAlarm(eggs))
        assertNull(TestSupport.timerAlarm(tea))

        // One ending doesn't touch the other.
        pass(6 * minute)
        receive(TimerReceiver.ACTION_DONE, eggs)
        assertEquals(Timer.ringId(eggs), app.nextStartedService?.getIntExtra(EXTRA_ALARM_ID, 0))
        assertEquals(TimerStatus.PAUSED, state(tea).status)
        assertEquals(3 * minute, state(tea).remainingMillis)
    }

    @Test
    fun deleteRemovesTheTimerItsAlarmAndNotification() {
        Timer.add(context, 5 * minute)
        val second = Timer.add(context, 2 * minute)
        Timer.delete(context, 1)
        assertEquals(listOf(second), Timer.timers(context).value.map { it.id })
        assertNull(TestSupport.timerAlarm(1))
        assertEquals(1, notifications().size)
        // Ids aren't reused, so a late notification button can't reach a newer timer.
        assertEquals(3, Timer.add(context, minute))
    }

    @Test
    fun deletingARingingTimerStopsIt() {
        Timer.add(context, minute)
        Timer.onDone(context, 1)
        TestSupport.setRingingAlarmId(Timer.ringId(1))
        Timer.delete(context, 1)
        assertEquals(AlarmService::class.java.name, app.nextStoppedService?.component?.className)
    }

    @Test
    fun labelsCanBeChanged() {
        Timer.add(context, minute)
        Timer.setLabel(context, 1, " Pasta ")
        assertEquals("Pasta", state().label)
        assertEquals("Pasta", shadowOf(notification()!!).contentTitle)
    }

    @Test
    fun ringIdsDontClashWithAlarmIds() {
        assertEquals(7, Timer.timerIdOf(Timer.ringId(7)))
        assertNull(Timer.timerIdOf(7))
        assertNull(Timer.timerIdOf(-1))
        assertNull(Timer.timerIdOf(null))
    }

    @Test
    fun whenItEndsItRingsLikeAnAlarm() {
        Timer.add(context, minute)
        pass(minute)
        receive(TimerReceiver.ACTION_DONE)
        assertEquals(TimerStatus.IDLE, state().status)
        assertNull(notification())
        val started = app.nextStartedService
        assertEquals(AlarmService::class.java.name, started.component?.className)
        assertEquals(Timer.ringId(1), started.getIntExtra(EXTRA_ALARM_ID, 0))
    }

    @Test
    fun aLateDoneForAResetTimerDoesNothing() {
        Timer.add(context, minute)
        Timer.reset(context, 1)
        receive(TimerReceiver.ACTION_DONE)
        assertNull(app.nextStartedService)
    }

    @Test
    fun addMinuteOnARingingTimerStopsItAndRunsAMinute() {
        Timer.add(context, minute)
        Timer.onDone(context, 1)
        TestSupport.setRingingAlarmId(Timer.ringId(1))
        Timer.addMinute(context, 1)
        assertEquals(AlarmService::class.java.name, app.nextStoppedService?.component?.className)
        assertEquals(TimerStatus.RUNNING, state().status)
        assertEquals(elapsed() + minute, TestSupport.timerAlarm(1)!!.triggerAtMs)
    }

    @Test
    fun ringingTimerNotificationSaysTimesUpWithTheLabel() {
        Timer.add(context, minute, "Eggs")
        val service = Robolectric.buildService(
            AlarmService::class.java,
            Intent(context, AlarmService::class.java).putExtra(EXTRA_ALARM_ID, Timer.ringId(1)),
        ).create().startCommand(0, 1).get()
        val n = shadowOf(service).lastForegroundNotification
        assertEquals("Time's up", shadowOf(n).contentTitle)
        assertEquals("Eggs", shadowOf(n).contentText)
        assertEquals(listOf("+1 min", "Stop"), n.actions.map { it.title.toString() })
        service.onDestroy()
    }

    @Test
    fun stateSurvivesTheAppBeingRestarted() {
        Timer.add(context, 5 * minute, "Eggs")
        Timer.add(context, 2 * minute)
        Timer.pause(context, 1)
        TestSupport.restartProcess()
        assertEquals(2, Timer.timers(context).value.size)
        assertEquals(TimerStatus.PAUSED, state().status)
        assertEquals("Eggs", state().label)
        assertEquals(5 * minute, state().remainingMillis)
        assertEquals(TimerStatus.RUNNING, state(2).status)
        assertEquals(2 * minute, Timer.lastDuration(context).value)
    }

    @Test
    fun theSingleTimerOfVersion3_0BecomesTimer1() {
        setBootCount(1)
        val endElapsed = elapsed() + 4 * minute
        deviceProtectedPrefs(context, "timer").edit()
            .putString("status", "RUNNING").putLong("duration", 5 * minute).putLong("total", 5 * minute)
            .putLong("end_elapsed", endElapsed).putLong("end_wall", System.currentTimeMillis() + 4 * minute)
            .putInt("boot_count", 1).commit()
        TestSupport.restartProcess()
        // The app update's MY_PACKAGE_REPLACED sets up its alarm and notification again.
        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(TimerStatus.RUNNING, state().status)
        assertEquals("", state().label)
        assertEquals(5 * minute, state().durationMillis)
        assertEquals(endElapsed, TestSupport.timerAlarm(1)!!.triggerAtMs)
        assertNotNull(notification())
        assertEquals(5 * minute, Timer.lastDuration(context).value)
        assertEquals(2, Timer.add(context, minute))
    }

    @Test
    fun anIdleTimerOfVersion3_0LeavesOnlyItsTime() {
        deviceProtectedPrefs(context, "timer").edit().putString("status", "IDLE").putLong("duration", 7 * minute).commit()
        TestSupport.restartProcess()
        assertTrue(Timer.timers(context).value.isEmpty())
        assertEquals(7 * minute, Timer.lastDuration(context).value)
    }

    @Test
    fun afterARebootARunningTimerCarriesOnByTheWallClock() {
        setBootCount(1)
        Timer.add(context, 30 * minute)
        val endWall = state().endWall
        setBootCount(2)
        TestSupport.restartProcess()
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(TimerStatus.RUNNING, state().status)
        assertTrue(Math.abs(state().endWall - endWall) < 1_000)
        assertNotNull(TestSupport.timerAlarm(1))
        assertNotNull(notification())
    }

    @Test
    fun afterARebootATimerThatEndedMeanwhileRingsIfOnlyALittleLate() {
        setBootCount(1)
        Timer.add(context, minute)
        setState(state().copy(endWall = System.currentTimeMillis() - 2 * minute))
        setBootCount(2)
        Timer.restore(context)
        assertEquals(Timer.ringId(1), app.nextStartedService?.getIntExtra(EXTRA_ALARM_ID, 0))
    }

    @Test
    fun afterARebootATimerThatEndedLongAgoIsJustReset() {
        setBootCount(1)
        Timer.add(context, minute)
        setState(state().copy(endWall = System.currentTimeMillis() - 60 * minute))
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
