package io.github.henriksten65.alarmclock

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Saving and loading alarms: JSON format (incl. old versions), ordering, ids, device-protected storage. */
@RunWith(AndroidJUnit4::class)
class AlarmRepositoryTest {

    private val context get() = TestSupport.context

    @Before
    fun setUp() = TestSupport.resetAppState()

    private fun alarmsPrefs() = deviceProtectedPrefs(context, "alarms")

    // --- JSON ---

    @Test
    fun jsonRoundTripKeepsEveryField() {
        val alarm = Alarm(
            id = 7, hour = 6, minute = 45, label = "Work", enabled = false, days = Alarm.WEEKDAYS,
            snoozedUntil = 1_800_000_000_000L, soundUri = "content://media/audio/12", skippedAt = 1_800_000_600_000L,
        )
        assertEquals(alarm, Alarm.fromJson(JSONObject(alarm.toJson().toString())))
    }

    @Test
    fun optionalFieldsAreLeftOutWhenUnset() {
        val json = Alarm(id = 1, hour = 7, minute = 0).toJson()
        assertFalse(json.has("sound"))
        assertFalse(json.has("skippedAt"))
        assertEquals(Alarm(id = 1, hour = 7, minute = 0), Alarm.fromJson(json))
    }

    @Test
    fun readsVersion1AlarmsWithRepeatDaily() {
        val daily = Alarm.fromJson(JSONObject("""{"id":1,"hour":7,"minute":0,"label":"","enabled":true,"repeatDaily":true}"""))
        assertEquals(Alarm.EVERY_DAY, daily.days)
        val once = Alarm.fromJson(JSONObject("""{"id":2,"hour":8,"minute":0,"repeatDaily":false}"""))
        assertEquals(0, once.days)
        // Fields that version 1 didn't have get their defaults.
        assertEquals(Alarm(id = 2, hour = 8, minute = 0), once)
    }

    @Test
    fun unknownDayBitsAreDropped() {
        val alarm = Alarm.fromJson(JSONObject("""{"id":1,"hour":7,"minute":0,"days":${0xFF or Alarm.WEEKEND}}"""))
        assertEquals(Alarm.EVERY_DAY, alarm.days)
    }

    // --- Repository ---

    @Test
    fun startsEmpty() {
        assertTrue(TestSupport.repository.alarms.value.isEmpty())
    }

    @Test
    fun keepsAlarmsSortedByTime() {
        val repo = TestSupport.repository
        repo.upsert(Alarm(id = 1, hour = 9, minute = 0))
        repo.upsert(Alarm(id = 2, hour = 6, minute = 30))
        repo.upsert(Alarm(id = 3, hour = 6, minute = 15))
        repo.upsert(Alarm(id = 4, hour = 6, minute = 30))
        assertEquals(listOf(3, 2, 4, 1), repo.alarms.value.map { it.id })
    }

    @Test
    fun upsertReplacesAnAlarmWithTheSameId() {
        val repo = TestSupport.repository
        repo.upsert(Alarm(id = 1, hour = 9, minute = 0))
        repo.upsert(Alarm(id = 1, hour = 5, minute = 0, label = "Changed"))
        assertEquals(listOf(Alarm(id = 1, hour = 5, minute = 0, label = "Changed")), repo.alarms.value)
        assertEquals("Changed", repo.get(1)?.label)
        assertNull(repo.get(2))
    }

    @Test
    fun delete() {
        val repo = TestSupport.repository
        repo.upsert(Alarm(id = 1, hour = 9, minute = 0))
        repo.upsert(Alarm(id = 2, hour = 10, minute = 0))
        repo.delete(1)
        assertEquals(listOf(2), repo.alarms.value.map { it.id })
        repo.delete(99) // unknown id: no-op
        assertEquals(listOf(2), repo.alarms.value.map { it.id })
    }

    @Test
    fun alarmsSurviveAProcessRestart() {
        TestSupport.repository.upsert(Alarm(id = 1, hour = 7, minute = 15, label = "Gym", days = Alarm.WEEKEND))
        TestSupport.restartProcess()
        assertEquals(listOf(Alarm(id = 1, hour = 7, minute = 15, label = "Gym", days = Alarm.WEEKEND)), TestSupport.repository.alarms.value)
    }

    @Test
    fun idsAreNeverReusedAfterDelete() {
        val repo = TestSupport.repository
        val first = repo.nextId()
        repo.upsert(Alarm(id = first, hour = 7, minute = 0))
        val second = repo.nextId()
        repo.upsert(Alarm(id = second, hour = 8, minute = 0))
        repo.delete(second)
        val third = repo.nextId()
        assertEquals(listOf(1, 2, 3), listOf(first, second, third))

        // Not even after a restart.
        TestSupport.restartProcess()
        assertEquals(4, TestSupport.repository.nextId())
    }

    @Test
    fun nextIdIsAboveAlarmsSavedBeforeTheCounterExisted() {
        alarmsPrefs().edit().putString("alarms", """[{"id":41,"hour":7,"minute":0}]""").commit()
        assertEquals(42, TestSupport.repository.nextId())
    }

    @Test
    fun corruptDataGivesAnEmptyListInsteadOfACrash() {
        alarmsPrefs().edit().putString("alarms", "not json").commit()
        assertTrue(TestSupport.repository.alarms.value.isEmpty())
    }

    @Test
    fun storedInDeviceProtectedStorage() {
        TestSupport.repository.upsert(Alarm(id = 1, hour = 7, minute = 0))
        val raw = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("alarms", Context.MODE_PRIVATE).getString("alarms", null)
        assertTrue(raw!!.contains("\"hour\":7"))
    }

    @Test
    fun alarmsFromNormalStorageAreMovedToDeviceProtectedStorage() {
        // Versions before 1.3 kept alarms in normal (credential-protected) storage.
        context.getSharedPreferences("alarms", Context.MODE_PRIVATE).edit()
            .putString("alarms", """[{"id":3,"hour":6,"minute":0,"repeatDaily":true}]""").commit()

        assertEquals(listOf(Alarm(id = 3, hour = 6, minute = 0, days = Alarm.EVERY_DAY)), TestSupport.repository.alarms.value)
    }
}
