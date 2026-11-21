package io.github.henriksten65.alarmclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.henriksten65.alarmclock.Alarm
import io.github.henriksten65.alarmclock.dayName
import io.github.henriksten65.alarmclock.weekDaysInLocaleOrder
import java.time.format.TextStyle

/** A row of seven round day buttons (first day of the week per locale); tap to toggle each day. */
@Composable
fun DaySelector(days: Int, onDaysChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    // The buttons share the width equally. Their size is worked out from the available width rather
    // than with weight + aspectRatio, whose height-from-width measuring could keep a dialog
    // re-measuring endlessly (seen in Robolectric tests).
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 4.dp
        val buttonSize = (maxWidth - gap * 6) / 7
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            weekDaysInLocaleOrder().forEach { day ->
                val bit = Alarm.dayBit(day)
                val selected = days and bit != 0
                Surface(
                    checked = selected,
                    onCheckedChange = { onDaysChange(days xor bit) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(buttonSize)
                        .semantics { contentDescription = dayName(day, TextStyle.FULL) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(dayName(day, TextStyle.NARROW), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
