package io.github.henriksten65.alarmclock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.henriksten65.alarmclock.AlarmRepository
import io.github.henriksten65.alarmclock.AppSettings
import io.github.henriksten65.alarmclock.R

enum class Tab(val label: Int, val icon: Int) {
    ALARMS(R.string.tab_alarms, R.drawable.ic_alarm),
    TIMER(R.string.timer, R.drawable.ic_timer),
    STOPWATCH(R.string.stopwatch, R.drawable.ic_stopwatch),
}

/**
 * The Alarms, Timer and Stopwatch tabs: a bar at the bottom, or a rail at the side when the window is
 * short (a phone in landscape), so the bar doesn't take up a fifth of the height.
 */
@Composable
fun MainScreen(
    repository: AlarmRepository,
    settings: AppSettings,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
) {
    // Back on the Timer or Stopwatch tab goes to the alarms, the app's start screen.
    BackHandler(enabled = tab != Tab.ALARMS) { onTabChange(Tab.ALARMS) }

    val content = @Composable { modifier: Modifier ->
        when (tab) {
            Tab.ALARMS -> AlarmListScreen(repository, settings, modifier)
            Tab.TIMER -> TimerScreen(modifier)
            Tab.STOPWATCH -> StopwatchScreen(modifier)
        }
    }

    // One layout tree for both: only the bar or the rail is switched. The tab's screen stays at the
    // same place in the composition, so its saved state (open dialogs, typed text) has the same keys
    // in portrait and landscape and survives rotating. (Separate Row/Scaffold trees lost it.)
    val short = isShortWindow()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!short) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == tab,
                            onClick = { onTabChange(t) },
                            icon = { Icon(painterResource(t.icon), contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
            if (short) {
                NavigationRail {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = t == tab,
                            onClick = { onTabChange(t) },
                            icon = { Icon(painterResource(t.icon), contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content(
                    if (short) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    else Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
        }
    }
}
