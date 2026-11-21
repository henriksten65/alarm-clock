package io.github.henriksten65.alarmclock.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import io.github.henriksten65.alarmclock.Alarm
import io.github.henriksten65.alarmclock.dayName
import io.github.henriksten65.alarmclock.weekDaysInLocaleOrder
import java.time.format.TextStyle

/** Largest day circle, so the buttons don't become huge on a tablet. */
private val MAX_CIRCLE = 56.dp

/** Minimum height of each button's touch area (Material's minimum touch target). */
private val MIN_TOUCH_HEIGHT = 48.dp

/**
 * A row of seven round day buttons (first day of the week per locale); tap to toggle each day.
 *
 * Each button's touch area is its full seventh of the row and at least 48 dp tall, a little larger
 * than the circle drawn inside it. Sizes are worked out from the available width rather than with
 * weight + aspectRatio, whose height-from-width measuring kept a dialog re-measuring endlessly in
 * Robolectric tests.
 */
@Composable
fun DaySelector(days: Int, onDaysChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val slot = maxWidth / 7
        // A small gap between neighbouring circles, but never wider than MAX_CIRCLE.
        val circle = (slot - 4.dp).coerceAtMost(MAX_CIRCLE)
        val touchHeight = circle.coerceAtLeast(MIN_TOUCH_HEIGHT)
        Row {
            weekDaysInLocaleOrder().forEach { day ->
                val bit = Alarm.dayBit(day)
                val selected = days and bit != 0
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(slot)
                        .height(touchHeight)
                        .toggleable(
                            value = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = circle / 2),
                            role = Role.Checkbox,
                            onValueChange = { onDaysChange(days xor bit) },
                        )
                        .semantics { contentDescription = dayName(day, TextStyle.FULL) },
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(circle),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(dayName(day, TextStyle.NARROW), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
