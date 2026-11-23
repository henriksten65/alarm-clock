package io.github.henriksten65.alarmclock.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.TestSupport
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dialog for a timer's label, driven on its own: under Robolectric, Compose never goes idle
 * with it over the Timer screen. Tapping a card's label to open it is checked on the emulator.
 */
@RunWith(AndroidJUnit4::class)
class LabelDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = TestSupport.resetAppState()

    @Test
    fun changesTheLabel() {
        var result: String? = null
        compose.setContent { LabelDialog("Tea", onDismiss = {}, onConfirm = { result = it }) }
        compose.onNodeWithText("Tea").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("Pasta")
        compose.onNodeWithText("OK").performClick()
        assertEquals("Pasta", result)
    }
}
