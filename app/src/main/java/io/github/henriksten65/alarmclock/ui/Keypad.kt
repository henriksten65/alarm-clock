package io.github.henriksten65.alarmclock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.henriksten65.alarmclock.KeypadLayout
import io.github.henriksten65.alarmclock.R

internal const val BACKSPACE_KEY = "⌫"

/** The keypad's rows, top to bottom: 1 2 3 first like a phone, or 7 8 9 first like a calculator. */
internal fun keypadRows(layout: KeypadLayout): List<List<String>> {
    val digitRows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    val ordered = if (layout == KeypadLayout.CALCULATOR) digitRows.reversed() else digitRows
    return ordered + listOf(listOf("00", "0", BACKSPACE_KEY))
}

/** Round digit keys in the chosen [layout], with "00", "0" and backspace on the bottom row. */
@Composable
internal fun Keypad(
    keySize: Dp,
    layout: KeypadLayout,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    spacing: Dp = 16.dp,
    keyColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val deleteDescription = stringResource(R.string.delete_digit)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keypadRows(layout).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { key ->
                    Surface(
                        color = keyColor,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(keySize)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { if (key == BACKSPACE_KEY) onBackspace() else onDigit(key) }
                            .then(if (key == BACKSPACE_KEY) Modifier.semantics { contentDescription = deleteDescription } else Modifier),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(key, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}
