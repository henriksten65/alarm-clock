package io.github.henriksten65.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The timer's AlarmManager alarm, and the buttons on its notifications. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DONE -> Timer.onDone(context)
            ACTION_PAUSE -> Timer.pause(context)
            ACTION_RESUME -> Timer.resume(context)
            ACTION_ADD_MINUTE -> Timer.addMinute(context)
            ACTION_RESET -> Timer.reset(context)
        }
    }

    companion object {
        const val ACTION_DONE = "io.github.henriksten65.alarmclock.action.TIMER_DONE"
        const val ACTION_PAUSE = "io.github.henriksten65.alarmclock.action.TIMER_PAUSE"
        const val ACTION_RESUME = "io.github.henriksten65.alarmclock.action.TIMER_RESUME"
        const val ACTION_ADD_MINUTE = "io.github.henriksten65.alarmclock.action.TIMER_ADD_MINUTE"
        const val ACTION_RESET = "io.github.henriksten65.alarmclock.action.TIMER_RESET"
    }
}
