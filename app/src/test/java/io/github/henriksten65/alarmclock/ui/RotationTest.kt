package io.github.henriksten65.alarmclock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.PHONE_SCREEN
import io.github.henriksten65.alarmclock.TestSupport
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Rotating between portrait (tab bar at the bottom) and landscape (rail at the side) keeps what is
 * typed on the screen. Before the fix the two layouts were different composition trees, so saved
 * state was lost on rotating and an older one could come back on rotating back.
 * (With a dialog open this is checked on the emulator: StateRestorationTester can hang with dialogs.)
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_SCREEN)
class RotationTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = TestSupport.resetAppState()

    /** The real window, but with a height the test sets: tall = tab bar, short = rail. */
    private var windowHeight = 891

    private class FakeHeight(private val real: WindowInfo, private val height: () -> Int) : WindowInfo by real {
        override val containerSize: IntSize get() = IntSize(real.containerSize.width, height())
    }

    @Composable
    private fun Screen() {
        val real = LocalWindowInfo.current
        CompositionLocalProvider(LocalWindowInfo provides FakeHeight(real) { windowHeight }) {
            AlarmClockTheme {
                MainScreen(TestSupport.repository, TestSupport.settings, Tab.TIMER, onTabChange = {})
            }
        }
    }

    private fun typeLabelThenRotate(from: Int, to: Int) {
        windowHeight = from
        val tester = StateRestorationTester(compose)
        tester.setContent { Screen() }
        compose.onNode(hasSetTextAction()).performTextInput("Eggs")
        compose.onNodeWithText("Eggs").assertExists()

        windowHeight = to
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Eggs").assertExists()
    }

    @Test
    fun portraitToLandscapeKeepsTypedText() = typeLabelThenRotate(from = 891, to = 411)

    @Test
    fun landscapeToPortraitKeepsTypedText() = typeLabelThenRotate(from = 411, to = 891)
}
