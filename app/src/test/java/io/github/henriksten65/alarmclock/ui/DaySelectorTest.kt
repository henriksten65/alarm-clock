package io.github.henriksten65.alarmclock.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.Alarm
import io.github.henriksten65.alarmclock.PHONE_SCREEN
import io.github.henriksten65.alarmclock.TestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_SCREEN)
class DaySelectorTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var savedLocale: Locale
    private var days = 0

    @Before
    fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        TestSupport.resetAppState()
    }

    @After
    fun tearDown() = Locale.setDefault(savedLocale)

    private fun show(initial: Int) = compose.setContent {
        var state by remember { mutableIntStateOf(initial) }
        DaySelector(days = state, onDaysChange = { state = it; days = it })
    }

    @Test
    fun sevenButtonsInTheLocalesOrder() {
        show(0)
        val names = compose.onAllNodes(isToggleable()).fetchSemanticsNodes().map {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        }
        assertEquals(listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"), names)
    }

    @Test
    fun showsTheSelectedDays() {
        show(Alarm.WEEKEND)
        compose.onNodeWithContentDescription("Saturday").assertIsOn()
        compose.onNodeWithContentDescription("Sunday").assertIsOn()
        compose.onNodeWithContentDescription("Monday").assertIsOff()
    }

    @Test
    fun tappingTogglesADay() {
        show(Alarm.WEEKDAYS)
        compose.onNodeWithContentDescription("Saturday").performClick()
        val withSaturday = Alarm.WEEKDAYS or Alarm.dayBit(Calendar.SATURDAY)
        assertEquals(withSaturday, days)
        compose.onNodeWithContentDescription("Saturday").assertIsOn()

        compose.onNodeWithContentDescription("Monday").performClick()
        assertEquals(withSaturday and Alarm.dayBit(Calendar.MONDAY).inv(), days)
        compose.onNodeWithContentDescription("Monday").assertIsOff()
        assertEquals(7, compose.onAllNodesWithContentDescription("day", substring = true).fetchSemanticsNodes().size)
    }
}
