package io.github.henriksten65.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The timers' AlarmManager alarms, and the buttons on their notifications. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_TIMER_ID, -1).takeIf { it > 0 } ?: return
        when (intent.action) {
            ACTION_DONE -> Timer.onDone(context, id)
            ACTION_PAUSE -> Timer.pause(context, id)
            ACTION_RESUME -> Timer.resume(context, id)
            ACTION_ADD_MINUTE -> Timer.addMinute(context, id)
            ACTION_RESET -> Timer.reset(context, id)
        }
    }

    companion object {
        const val EXTRA_TIMER_ID = "io.github.henriksten65.alarmclock.EXTRA_TIMER_ID"
        const val ACTION_DONE = "io.github.henriksten65.alarmclock.action.TIMER_DONE"
        const val ACTION_PAUSE = "io.github.henriksten65.alarmclock.action.TIMER_PAUSE"
        const val ACTION_RESUME = "io.github.henriksten65.alarmclock.action.TIMER_RESUME"
        const val ACTION_ADD_MINUTE = "io.github.henriksten65.alarmclock.action.TIMER_ADD_MINUTE"
        const val ACTION_RESET = "io.github.henriksten65.alarmclock.action.TIMER_RESET"
    }
}
