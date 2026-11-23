package io.github.henriksten65.alarmclock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.henriksten65.alarmclock.ui.AlarmClockTheme
import io.github.henriksten65.alarmclock.ui.MainScreen
import io.github.henriksten65.alarmclock.ui.Tab
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the list re-checks on resume */ }

    private var tab by mutableStateOf(Tab.ALARMS)

    /** The ringing alarm we've already opened the ring screen for, so Back doesn't reopen it in a loop. */
    private var ringScreenShownFor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Created up front so the app can tell if the user has blocked it.
        AlarmService.createNotificationChannel(this)

        // Needed on Android 13+ for the ringing notification / full-screen alarm.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Opening the app while an alarm rings goes straight to the ring screen, so an alarm can
        // always be stopped – even when notifications (and with them the Dismiss button) are off.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AlarmService.ringingAlarmId.collect { id ->
                    if (id != null && id != ringScreenShownFor) {
                        startActivity(
                            Intent(this@MainActivity, RingActivity::class.java)
                                .putExtra(EXTRA_ALARM_ID, id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    ringScreenShownFor = id
                }
            }
        }

        // Only on a fresh start: during a rotation an "Undo" for a deleted alarm may still be showing.
        if (savedInstanceState == null) AlarmSound.releaseUnused(this)

        // The tab from before a rotation, or the one asked for (the timer notification opens Timer).
        tab = savedInstanceState?.getString(KEY_TAB)?.let { Tab.valueOf(it) } ?: tabFrom(intent) ?: Tab.ALARMS

        val repository = AlarmRepository.getInstance(this)
        setContent {
            AlarmClockTheme {
                MainScreen(repository, AppSettings.getInstance(this), tab, onTabChange = { tab = it })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFrom(intent)?.let { tab = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, tab.name)
    }

    private fun tabFrom(intent: Intent?): Tab? = if (intent?.getStringExtra(EXTRA_TAB) == TAB_TIMER) Tab.TIMER else null

    companion object {
        const val EXTRA_TAB = "io.github.henriksten65.alarmclock.EXTRA_TAB"
        const val TAB_TIMER = "timer"
        private const val KEY_TAB = "tab"
    }
}
