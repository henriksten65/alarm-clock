package io.github.henriksten65.alarmclock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import io.github.henriksten65.alarmclock.KeypadLayout
import io.github.henriksten65.alarmclock.R

/**
 * Time entry by typing digits on the numeric keyboard: "630" → 6:30, "1845" → 18:45.
 * The last two digits are the minutes. On 12-hour devices an AM/PM switch picks the half
 * of the day for hours 1–12; hours 0 and 13–23 are taken as 24-hour times. With [force24Hour]
 * (a user setting) typed digits are always a 24-hour time and there is no AM/PM switch.
 *
 * With the [KeypadLayout.CALCULATOR] keypad setting the dialog shows the app's own keypad
 * (7 8 9 at the top) instead of the phone's keyboard, whose layout can't be changed; a hardware
 * keyboard still works then.
 */
@Composable
fun KeyboardTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    force24Hour: Boolean,
    keypadLayout: KeypadLayout = KeypadLayout.CALCULATOR,
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
    // Saveable, so typed digits survive rotating the phone.
    val field = rememberTextFieldState(initialDigits, TextRange(0, initialDigits.length))
    var pm by rememberSaveable { mutableStateOf(initialHour >= 12) }

    val typed = field.text.toString()
    val result = parseDigits(typed, is24Hour, pm)
    val confirm = { result?.let { (h, m) -> onConfirm(h, m) } }

    // With the app's keypad the digits are kept in the same state, but shown as plain text
    // (no text field, so the phone's keyboard stays away); hardware keys still work.
    val ownKeypad = keypadLayout == KeypadLayout.CALCULATOR
    fun typeKey(key: String) = field.edit {
        if (selection.collapsed) append(key) else replace(selection.min, selection.max, key)
        val digits = toString().take(4)
        if (digits != toString()) replace(0, length, digits)
        placeCursorAtEnd()
    }
    fun backspace() = field.edit {
        if (!selection.collapsed) replace(selection.min, selection.max, "")
        else if (length > 0) replace(length - 1, length, "")
        placeCursorAtEnd()
    }

    // In a short window (landscape) the app's keypad goes beside the time and the title is left
    // out, so nothing needs scrolling.
    val keypadBeside = ownKeypad && isShortWindow()

    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Wider than the platform's dialog default, so the time fits next to the keypad.
        properties = DialogProperties(usePlatformDefaultWidth = !keypadBeside),
        modifier = if (keypadBeside) Modifier.padding(horizontal = 24.dp).widthIn(max = 520.dp) else Modifier,
        title = if (keypadBeside) null else { { Text(stringResource(R.string.enter_time)) } },
        text = {
            // In the dialog's own composition (its window), so the text field below is already
            // attached when focus is requested; from outside the dialog that isn't guaranteed.
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                if (ownKeypad) keyboard?.hide() else keyboard?.show()
            }
            val invalid = typed.isNotEmpty() && result == null
            val hint = stringResource(if (invalid) R.string.invalid_time else R.string.type_time_hint)
            val amPm = @Composable {
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
            if (ownKeypad) {
                val display = @Composable {
                    // Dimmed while the whole time is selected, i.e. the next key replaces it.
                    val replacing = !field.selection.collapsed
                    Text(
                        formatTimeDigits(typed),
                        style = if (keypadBeside) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
                        color = when {
                            invalid -> MaterialTheme.colorScheme.error
                            replacing -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event -> hardwareKey(event, ::typeKey, ::backspace) { confirm() } }
                            .focusable(),
                    )
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    amPm()
                }
                // The same key size as on the Timer tab, smaller only when the dialog is too narrow for it.
                val keypad = @Composable { keySize: Dp ->
                    BoxWithConstraints(contentAlignment = Alignment.Center) {
                        val spacing = 16.dp
                        val fitting = ((maxWidth - spacing * 2) / 3).coerceAtMost(keySize)
                        Keypad(fitting, keypadLayout, ::typeKey, ::backspace, spacing = spacing, keyColor = MaterialTheme.colorScheme.secondaryContainer)
                    }
                }
                if (keypadBeside) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) { display() }
                        keypad(48.dp) // Any bigger and the bottom row is cut off in a phone in landscape.
                    }
                } else {
                    // Scrolls when it still doesn't fit (large text).
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    ) {
                        display()
                        keypad(72.dp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        state = field,
                        inputTransformation = DigitsOnly,
                        outputTransformation = TimeDigitsTransformation,
                        textStyle = MaterialTheme.typography.displayMedium.copy(textAlign = TextAlign.Center),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        isError = invalid,
                        supportingText = { Text(hint) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        onKeyboardAction = { confirm() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                    amPm()
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

/** "630" → "6:30", "1845" → "18:45"; 1–2 digits as they are (like [TimeDigitsTransformation]). */
internal fun formatTimeDigits(digits: String): String =
    if (digits.length >= 3) digits.dropLast(2) + ":" + digits.takeLast(2) else digits

/** Digits, Backspace and Enter from a hardware keyboard, for the time display next to the app's keypad. */
private fun hardwareKey(event: KeyEvent, onDigit: (String) -> Unit, onBackspace: () -> Unit, onEnter: () -> Unit): Boolean {
    val char = event.utf16CodePoint.toChar()
    val handled = char.isDigit() || event.key == Key.Backspace || event.key == Key.Enter || event.key == Key.NumPadEnter
    if (handled && event.type == KeyEventType.KeyDown) {
        when {
            char.isDigit() -> onDigit(char.toString())
            event.key == Key.Backspace -> onBackspace()
            else -> onEnter()
        }
    }
    return handled
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

/** Keeps at most 4 digits, with the cursor after them. */
private object DigitsOnly : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val digits = toString().filter(Char::isDigit).take(4)
        if (digits != toString()) replace(0, length, digits)
        placeCursorAtEnd()
    }
}

/** Shows "630" as "6:30" and "1845" as "18:45" while typing; 1–2 digits are shown as-is. */
private object TimeDigitsTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length >= 3) replace(length - 2, length - 2, ":")
    }
}
