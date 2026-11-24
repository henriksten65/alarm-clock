package io.github.henriksten65.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The buttons on the stopwatch notification. */
class StopwatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> Stopwatch.pause(context)
            ACTION_RESUME -> Stopwatch.resume(context)
            ACTION_LAP -> Stopwatch.lap(context)
            ACTION_RESET -> Stopwatch.reset(context)
        }
    }

    companion object {
        const val ACTION_PAUSE = "io.github.henriksten65.alarmclock.action.STOPWATCH_PAUSE"
        const val ACTION_RESUME = "io.github.henriksten65.alarmclock.action.STOPWATCH_RESUME"
        const val ACTION_LAP = "io.github.henriksten65.alarmclock.action.STOPWATCH_LAP"
        const val ACTION_RESET = "io.github.henriksten65.alarmclock.action.STOPWATCH_RESET"
    }
}
