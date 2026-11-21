package io.github.henriksten65.alarmclock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.henriksten65.alarmclock.R

/**
 * Time entry by typing digits on the numeric keyboard: "630" → 6:30, "1845" → 18:45.
 * The last two digits are the minutes. On 12-hour devices an AM/PM switch picks the half
 * of the day for hours 1–12; hours 0 and 13–23 are taken as 24-hour times. With [force24Hour]
 * (a user setting) typed digits are always a 24-hour time and there is no AM/PM switch.
 */
@Composable
fun KeyboardTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    force24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val context = LocalContext.current
    val is24Hour = remember(force24Hour) { force24Hour || DateFormat.is24HourFormat(context) }

    val initialDigits = remember {
        val shownHour = if (is24Hour) initialHour else (initialHour % 12).let { if (it == 0) 12 else it }
        "%d%02d".format(shownHour, initialMinute)
    }
    // Start with everything selected so the first digit typed replaces the old time.
    var field by remember { mutableStateOf(TextFieldValue(initialDigits, TextRange(0, initialDigits.length))) }
    var pm by remember { mutableStateOf(initialHour >= 12) }

    val result = parseDigits(field.text, is24Hour, pm)
    val confirm = { result?.let { (h, m) -> onConfirm(h, m) } }

    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_time)) },
        text = {
            // In the dialog's own composition (its window), so the text field below is already
            // attached when focus is requested; from outside the dialog that isn't guaranteed.
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { new ->
                        val digits = new.text.filter(Char::isDigit).take(4)
                        field = new.copy(text = digits, selection = TextRange(digits.length))
                    },
                    textStyle = MaterialTheme.typography.displayMedium.copy(textAlign = TextAlign.Center),
                    singleLine = true,
                    isError = field.text.isNotEmpty() && result == null,
                    supportingText = {
                        Text(
                            stringResource(if (field.text.isNotEmpty() && result == null) R.string.invalid_time else R.string.type_time_hint),
                        )
                    },
                    visualTransformation = TimeDigitsTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (!is24Hour) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(false to R.string.am, true to R.string.pm).forEachIndexed { index, (isPm, labelRes) ->
                            SegmentedButton(
                                selected = pm == isPm,
                                onClick = { pm = isPm },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(stringResource(labelRes)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = result != null) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Turns up to 4 typed digits into (hour 0–23, minute), or null if they aren't a valid time. */
internal fun parseDigits(digits: String, is24Hour: Boolean, pm: Boolean): Pair<Int, Int>? {
    if (digits.length < 3) {
        // 1–2 digits: a whole hour, e.g. "7" → 7:00.
        val hour = digits.toIntOrNull() ?: return null
        return toDayHour(hour, is24Hour, pm)?.let { it to 0 }
    }
    val hour = digits.dropLast(2).toInt()
    val minute = digits.takeLast(2).toInt()
    if (minute > 59) return null
    return toDayHour(hour, is24Hour, pm)?.let { it to minute }
}

private fun toDayHour(hour: Int, is24Hour: Boolean, pm: Boolean): Int? = when {
    hour > 23 -> null
    is24Hour || hour == 0 || hour > 12 -> hour
    else -> (hour % 12) + if (pm) 12 else 0
}

/** Shows "630" as "6:30" and "1845" as "18:45" while typing; 1–2 digits are shown as-is. */
private object TimeDigitsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length < 3) return TransformedText(text, OffsetMapping.Identity)
        val colonAt = raw.length - 2
        val shown = raw.substring(0, colonAt) + ":" + raw.substring(colonAt)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= colonAt) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = if (offset <= colonAt) offset else offset - 1
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
