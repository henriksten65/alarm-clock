package io.github.henriksten65.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import io.github.henriksten65.alarmclock.ui.AlarmClockTheme
import io.github.henriksten65.alarmclock.ui.RingScreen

/** Full-screen UI shown while an alarm rings, including over the lock screen. */
class RingActivity : ComponentActivity() {

    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()

        ContextCompat.registerReceiver(
            this,
            stoppedReceiver,
            IntentFilter(AlarmService.ACTION_ALARM_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Back would hide this screen but leave the alarm ringing, so while it rings Back only
        // explains how to stop it. (Home still works; the alarm can then be stopped from the
        // notification or by opening the app.)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (AlarmService.ringingAlarmId.value != null) {
                    Toast.makeText(this@RingActivity, R.string.back_while_ringing, Toast.LENGTH_SHORT).show()
                } else {
                    finish()
                }
            }
        })

        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(stoppedReceiver)
        super.onDestroy()
    }

    private fun render(intent: Intent) {
        val openedFor = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        setContent {
            // What rings now: when a second timer or alarm starts while this screen shows, the
            // service switches to it (the full-screen intent doesn't reopen this screen), so follow it.
            val alarmId = AlarmService.ringingAlarmId.collectAsState().value ?: openedFor
            AlarmClockTheme {
                val timerId = Timer.timerIdOf(alarmId)
                if (timerId != null) {
                    val timer = Timer.get(this, timerId)
                    RingScreen(
                        label = timer?.let { getString(R.string.times_up_named, Timer.displayName(this, it)) } ?: getString(R.string.times_up),
                        icon = R.drawable.ic_timer,
                        dismissText = getString(R.string.stop),
                        snoozeText = getString(R.string.add_minute),
                        onSnooze = {
                            Timer.addMinute(this, timerId)
                            finish()
                        },
                        onDismiss = {
                            AlarmScheduler.stopRinging(this)
                            finish()
                        },
                    )
                } else {
                    val alarm = AlarmRepository.getInstance(this).get(alarmId)
                    RingScreen(
                        label = alarm?.label.orEmpty(),
                        onSnooze = {
                            AlarmScheduler.snooze(this, alarmId)
                            finish()
                        },
                        onDismiss = {
                            AlarmScheduler.stopRinging(this)
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
