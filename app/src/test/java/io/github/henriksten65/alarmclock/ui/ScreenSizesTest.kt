package io.github.henriksten65.alarmclock.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.Alarm
import io.github.henriksten65.alarmclock.PHONE_SCREEN
import io.github.henriksten65.alarmclock.TestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone

/**
 * Layout on other screens (landscape, small phone, tablet, large text), touch targets, screen-reader
 * labels, and saving the edited alarm for rotation.
 * All screens are mdpi, so 1 px = 1 dp.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_SCREEN)
class ScreenSizesTest {

    @get:Rule
    val compose = createComposeRule()

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
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    private fun show() = compose.setContent {
        AlarmClockTheme { AlarmListScreen(repo, TestSupport.settings) }
    }

    private fun bounds(node: androidx.compose.ui.test.SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot

    // --- Landscape / short windows ---

    @Test
    @Config(qualifiers = "w891dp-h411dp-mdpi")
    fun landscapeEditDialogScrollsToEverything() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, label = "Work", days = Alarm.WEEKDAYS))
        show()
        compose.onNodeWithText("Work · Weekdays").performClick()
        compose.onNodeWithText("Change").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        // The day buttons keep their full size instead of being squashed.
        assertTrue(bounds(compose.onNodeWithContentDescription("Monday")).height >= 48f)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-mdpi")
    fun shortWindowHasAddInTheTopBar() {
        show()
        val add = compose.onAllNodesWithContentDescription("Add alarm").fetchSemanticsNodes()
        assertEquals("only one Add button (no floating button)", 1, add.size)
        assertTrue("in the top bar", add.single().boundsInRoot.bottom < 120f)
        compose.onNodeWithContentDescription("Add alarm").performClick()
        compose.onNodeWithText("New alarm").assertExists()
    }

    @Test
    fun tallWindowHasAFloatingAddButton() {
        show()
        assertTrue(bounds(compose.onNodeWithContentDescription("Add alarm")).top > 600f)
    }

    // --- Tablet / small phone ---

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletListIsNotStretchedAcrossTheScreen() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, label = "Work"))
        show()
        val switch = bounds(compose.onNodeWithContentDescription("07:00 Work alarm"))
        // Content is at most 640 dp wide and centred: from x = 80 to x = 720.
        assertTrue("switch at ${switch.right}", switch.right <= 720f)
        assertTrue(switch.left >= 80f)
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun smallPhoneEditDialogFits() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, label = "Work", days = Alarm.WEEKDAYS))
        show()
        compose.onNodeWithText("Work · Weekdays").performClick()
        compose.onNodeWithText("Change").performScrollTo().assertIsDisplayed()
        val days = listOf("Monday", "Sunday").map { bounds(compose.onNodeWithContentDescription(it)) }
        assertTrue("all seven days on screen", days[1].right <= 320f)
    }

    // --- Large text ---

    @Test
    @Config(fontScale = 1.5f)
    fun largeTextWrapsTheSoundButtonsInsteadOfSqueezingThem() {
        show()
        compose.onNodeWithContentDescription("Settings").performClick()
        val phone = bounds(compose.onNodeWithText("Phone sounds").performScrollTo())
        val file = bounds(compose.onNodeWithText("Audio file").performScrollTo())
        assertTrue("'Audio file' is on one line: ${file.height} vs ${phone.height}", file.height <= phone.height * 1.2f)
    }

    // --- Touch targets and screen readers ---

    @Test
    fun dayButtonsHaveFullSizeTouchTargets() {
        repo.upsert(Alarm(id = 1, hour = 7, minute = 0, label = "Work"))
        show()
        compose.onNodeWithText("Work · Once").performClick()
        listOf("Monday", "Wednesday", "Sunday").forEach {
            val b = bounds(compose.onNodeWithContentDescription(it))
            assertTrue("$it is ${b.height} dp tall", b.height >= 48f)
            assertTrue("$it is ${b.width} dp wide", b.width >= 36f)
        }
    }

    @Test
    fun switchAndDeleteSayWhichAlarmTheyBelongTo() {
        repo.upsert(Alarm(id = 1, hour = 6, minute = 30, label = "Work"))
        repo.upsert(Alarm(id = 2, hour = 9, minute = 0))
        show()
        compose.onNodeWithContentDescription("06:30 Work alarm").assertIsToggleable().assertIsOn()
        compose.onNodeWithContentDescription("09:00 alarm").assertIsToggleable()
        compose.onNodeWithContentDescription("Delete 06:30 Work alarm").assertExists()
        compose.onNodeWithContentDescription("Delete 09:00 alarm").assertExists()
    }

    // --- Rotation (saved instance state) ---
    // Rotation itself is checked on the emulator: Robolectric's StateRestorationTester sometimes
    // hangs with a dialog open. Here: the alarm being edited is saved and restored exactly.

    @Test
    fun editedAlarmIsSavedAndRestoredExactly() {
        val alarm = Alarm(
            id = 4, hour = 6, minute = 15, label = "Gym", enabled = false, days = Alarm.WEEKEND,
            snoozedUntil = 1_800_000_000_000L, soundUri = "content://media/audio/3", skippedAt = 1_800_000_600_000L,
        )
        val scope = SaverScope { true }
        val saved = with(AlarmSaver) { scope.save(alarm) }!!
        assertEquals(alarm, AlarmSaver.restore(saved))
        assertNull(AlarmSaver.restore(with(AlarmSaver) { scope.save(null) }!!))
    }
}
