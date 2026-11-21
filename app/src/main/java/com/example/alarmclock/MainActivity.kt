package com.example.alarmclock

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
import com.example.alarmclock.ui.AlarmClockTheme
import com.example.alarmclock.ui.AlarmListScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the list re-checks on resume */ }

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

        val repository = AlarmRepository.getInstance(this)
        setContent {
            AlarmClockTheme {
                AlarmListScreen(repository, AppSettings.getInstance(this))
            }
        }
    }
}
