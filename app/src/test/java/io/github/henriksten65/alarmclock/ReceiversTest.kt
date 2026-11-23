package io.github.henriksten65.alarmclock

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.Locale
import java.util.TimeZone

/** [AlarmReceiver] (alarm fired, Snooze/Dismiss actions) and [BootReceiver] (reboot, time changes). */
@RunWith(AndroidJUnit4::class)
class ReceiversTest {

    private val context get() = TestSupport.context
    private val app get() = shadowOf(context as Application)
    private val repo get() = TestSupport.repository
    private val minute = 60_000L
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

    private fun receive(action: String, alarmId: Int) =
        AlarmReceiver().onReceive(context, Intent(action).putExtra(EXTRA_ALARM_ID, alarmId))

    private fun missedNotifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        .filter { it.channelId == "missed_alarms" }

    private fun boot(action: String) = BootReceiver().onReceive(context, Intent(action))

    private fun startedServiceAlarmId(): Int? = app.nextStartedService
        ?.takeIf { it.component?.className == AlarmService::class.java.name }
        ?.getIntExtra(EXTRA_ALARM_ID, -1)

    // --- AlarmReceiver ---

    @Test
    fun firedOneTimeAlarmSwitchesOffAndStartsRinging() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, snoozedUntil = System.currentTimeMillis() - 1))
        AlarmScheduler.schedule(TestSupport.context, repo.get(1)!!)
        receive(AlarmReceiver.ACTION_FIRE, 1)

        assertEquals(Alarm(id = 1, hour = 7, minute = 0, enabled = false), repo.get(1))
        assertEquals(1, startedServiceAlarmId())
        // Nothing is due any more, so a reboot won't report it as missed.
        assertEquals(0L, deviceProtectedPrefs(context, "due_times").getLong("1", 0L))
    }

    @Test
    fun firedRepeatingAlarmSchedulesItsNextDay() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY, snoozedUntil = System.currentTimeMillis()))
        val now = System.currentTimeMillis()
        receive(AlarmReceiver.ACTION_FIRE, 1)

        val alarm = repo.get(1)!!
        assertTrue(alarm.enabled)
        assertEquals(0L, alarm.snoozedUntil)
        val next = TestSupport.scheduledTimes().single()
        assertTrue("next ring is at least a minute away", next > now + minute)
        assertEquals(alarm.nextTriggerMillis(now + minute), next)
        assertEquals(1, startedServiceAlarmId())
    }

    @Test
    fun firedRepeatingAlarmKeepsAFutureSkipAndDropsAPassedOne() {
        val now = System.currentTimeMillis()
        val base = Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY)
        // A snooze rang while tomorrow's occurrence is being skipped: the skip must survive.
        val tomorrow = base.nextOccurrenceMillis(now + minute)
        repo.upsert(base.copy(skippedAt = tomorrow))
        receive(AlarmReceiver.ACTION_FIRE, 1)
        assertEquals(tomorrow, repo.get(1)!!.skippedAt)
        assertEquals(base.nextOccurrenceMillis(tomorrow), TestSupport.scheduledTimes().single())

        repo.upsert(base.copy(skippedAt = now - minute))
        receive(AlarmReceiver.ACTION_FIRE, 1)
        assertEquals(0L, repo.get(1)!!.skippedAt)
    }

    @Test
    fun disabledOrDeletedAlarmsDoNotRing() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, enabled = false))
        receive(AlarmReceiver.ACTION_FIRE, 1)
        receive(AlarmReceiver.ACTION_FIRE, 2)
        assertNull(startedServiceAlarmId())
        assertEquals(Alarm(id = 1, hour = 7, minute = 0, enabled = false), repo.get(1))
    }

    @Test
    fun snoozeActionSnoozes() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, enabled = false))
        receive(AlarmReceiver.ACTION_SNOOZE, 1)
        assertTrue(repo.get(1)!!.isSnoozed())
        assertEquals(AlarmService::class.java.name, app.nextStoppedService?.component?.className)
    }

    @Test
    fun dismissActionStopsRingingAndChangesNothingElse() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY))
        receive(AlarmReceiver.ACTION_DISMISS, 1)
        assertEquals(AlarmService::class.java.name, app.nextStoppedService?.component?.className)
        assertEquals(Alarm(id = 1, hour = 7, minute = 0, days = Alarm.EVERY_DAY), repo.get(1))
    }

    // --- BootReceiver ---

    @Test
    fun receiversAreDirectBootAware() {
        val pm = context.packageManager
        listOf(AlarmReceiver::class.java, BootReceiver::class.java).forEach {
            val info = pm.getReceiverInfo(android.content.ComponentName(context, it), 0)
            assertTrue("${it.simpleName} must run before the first unlock", info.directBootAware)
        }
    }

    @Test
    fun bootReschedulesEnabledAlarms() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        repo.upsert(Alarm(id = 2, hour = 8, minute = 0, enabled = false))
        boot(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        assertEquals(listOf(repo.get(1)!!.nextRingMillis()), TestSupport.scheduledTimes())
    }

    @Test
    fun bootAndUpdateCatchUpOnMissedAlarms() {
        listOf(Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
            .forEachIndexed { i, action ->
                val id = i + 1
                repo.upsert(Alarm(id = id, hour = 6, minute = 0))
                deviceProtectedPrefs(context, "due_times").edit().putLong(id.toString(), System.currentTimeMillis() - 60 * minute).commit()
                boot(action)
                assertFalse("$action: missed one-time alarm is switched off", repo.get(id)!!.enabled)
            }
        assertEquals(3, missedNotifications().size)
    }

    @Test
    fun timeAndZoneChangesDoNotReportMissedAlarms() {
        listOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED).forEach { action ->
            repo.upsert(Alarm(id = 1, hour = 6, minute = 0))
            deviceProtectedPrefs(context, "due_times").edit().putLong("1", System.currentTimeMillis() - 60 * minute).commit()
            boot(action)
            assertTrue(action, repo.get(1)!!.enabled)
        }
        assertTrue(missedNotifications().isEmpty())
        assertEquals(listOf(repo.get(1)!!.nextRingMillis()), TestSupport.scheduledTimes())
    }

    @Test
    fun exactAlarmPermissionGrantReschedules() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        boot(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        assertEquals(1, TestSupport.scheduledTimes().size)
    }

    @Test
    fun otherBroadcastsAreIgnored() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0))
        boot(Intent.ACTION_SCREEN_ON)
        assertTrue(TestSupport.scheduledTimes().isEmpty())
    }
}
