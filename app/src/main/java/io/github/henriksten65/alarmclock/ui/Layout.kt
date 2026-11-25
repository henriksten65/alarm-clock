package io.github.henriksten65.alarmclock.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The alarm list, cards and warning banners don't get wider than this (tablets, landscape). */
internal val MAX_CONTENT_WIDTH = 640.dp

/** Below this window height (e.g. a phone in landscape) "Add" moves from the floating button into the top bar, etc. */
internal val SHORT_WINDOW_HEIGHT = 480.dp

/** True when the window is shorter than [SHORT_WINDOW_HEIGHT]. */
@Composable
internal fun isShortWindow(): Boolean =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < SHORT_WINDOW_HEIGHT

/** [SystemClock.elapsedRealtime], updated every [intervalMillis] while [ticking]. */
@Composable
internal fun rememberElapsedNow(ticking: Boolean, intervalMillis: Long): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(ticking) {
        now = SystemClock.elapsedRealtime()
        while (ticking) {
            delay(intervalMillis)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}
