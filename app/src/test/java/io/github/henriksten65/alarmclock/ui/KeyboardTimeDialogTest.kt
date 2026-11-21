package io.github.henriksten65.alarmclock.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.TestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Typing a time: digits only, "630" shown as "6:30", OK only for valid times, AM/PM on 12-hour phones. */
@RunWith(AndroidJUnit4::class)
class KeyboardTimeDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private var confirmed: Pair<Int, Int>? = null
    private var dismissed = false

    @Before
    fun setUp() = TestSupport.resetAppState()

    private fun show(hour: Int, minute: Int, force24Hour: Boolean) = compose.setContent {
        KeyboardTimeDialog(
            initialHour = hour,
            initialMinute = minute,
            force24Hour = force24Hour,
            onDismiss = { dismissed = true },
            onConfirm = { h, m -> confirmed = h to m },
        )
    }

    private val field get() = compose.onNode(hasSetTextAction())

    @Test
    fun startsWithTheCurrentTimeSelectedSoTypingReplacesIt() {
        TestSupport.use24HourClock(true)
        show(7, 30, force24Hour = false)
        compose.onNodeWithText("7:30").assertExists()

        field.performTextInput("1845") // replaces the selected "730"
        compose.onNodeWithText("18:45").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(18 to 45, confirmed)
    }

    @Test
    fun invalidTimeDisablesOk() {
        show(7, 30, force24Hour = true)
        field.performTextReplacement("675")
        compose.onNodeWithText("Not a valid time").assertExists()
        compose.onNodeWithText("OK").assertIsNotEnabled()

        field.performTextReplacement("615")
        compose.onNodeWithText("OK").assertIsEnabled().performClick()
        assertEquals(6 to 15, confirmed)
    }

    @Test
    fun onlyDigitsAndAtMostFour() {
        show(7, 30, force24Hour = true)
        field.performTextReplacement("1a2:3456")
        compose.onNodeWithText("12:34").assertExists()
    }

    @Test
    fun oneDigitIsAWholeHour() {
        show(7, 30, force24Hour = true)
        field.performTextReplacement("9")
        compose.onNodeWithText("OK").performClick()
        assertEquals(9 to 0, confirmed)
    }

    @Test
    fun twelveHourPhoneShowsAmPm() {
        TestSupport.use24HourClock(false)
        show(18, 30, force24Hour = false)
        compose.onNodeWithText("6:30").assertExists() // shown on the 12-hour clock
        compose.onNodeWithText("PM").assertIsSelected()

        field.performTextReplacement("715")
        compose.onNodeWithText("AM").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(7 to 15, confirmed)
    }

    @Test
    fun twentyFourHourSettingHidesAmPmOnATwelveHourPhone() {
        TestSupport.use24HourClock(false)
        show(18, 30, force24Hour = true)
        compose.onNodeWithText("18:30").assertExists()
        compose.onNodeWithText("PM").assertDoesNotExist()
    }

    @Test
    fun cancel() {
        show(7, 30, force24Hour = true)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, dismissed)
        assertNull(confirmed)
    }
}
