package com.example.alarmclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.alarmclock.Alarm
import com.example.alarmclock.dayName
import com.example.alarmclock.weekDaysInLocaleOrder
import java.time.format.TextStyle

/** A row of seven round day buttons (first day of the week per locale); tap to toggle each day. */
@Composable
fun DaySelector(days: Int, onDaysChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    .weight(1f)
                    .aspectRatio(1f)
                    .semantics { contentDescription = dayName(day, TextStyle.FULL) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(dayName(day, TextStyle.NARROW), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
