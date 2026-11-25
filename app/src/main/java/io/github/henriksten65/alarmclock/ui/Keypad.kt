package io.github.henriksten65.alarmclock.ui

import android.widget.Toast
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.henriksten65.alarmclock.KeypadLayout
import io.github.henriksten65.alarmclock.R
import java.security.MessageDigest

internal const val BACKSPACE_KEY = "⌫"

/** The keypad's rows, top to bottom: 1 2 3 first like a phone, or 7 8 9 first like a calculator. */
internal fun keypadRows(layout: KeypadLayout): List<List<String>> {
    val digitRows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    val ordered = if (layout == KeypadLayout.CALCULATOR) digitRows.reversed() else digitRows
    return ordered + listOf(listOf("00", "0", BACKSPACE_KEY))
}

/** SHA-256 of each secret key sequence (stored only as hashes, since the source is public). */
private val SECRET_SHA256S = setOf(
    "039a69ffeaee2a305c70865c29f48ae115aa2d5a7f1b4d207f7edea51a9fc0b9",
    "b3584771a1d97fd901eff1a186a1f08d1c75bf3490fef8559710d49d140d5442",
    "41e4468bb70f1cb41858810dcf7c69705ec1fb235e90972466ba2863ace2b388",
)
private const val SECRET_LENGTH = 10
internal const val SECRET_MESSAGE = "Greetings, Master!"

/**
 * The last digits pressed on a keypad, for a small easter egg: [press] returns true when they
 * match one of the secrets. "00" counts as two zeros; backspace starts over.
 */
internal class SecretSequence {
    private var recent = ""

    fun press(key: String): Boolean {
        if (key == BACKSPACE_KEY) {
            recent = ""
            return false
        }
        recent = (recent + key).takeLast(SECRET_LENGTH)
        return recent.length == SECRET_LENGTH && sha256(recent) in SECRET_SHA256S
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
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
    val context = LocalContext.current
    val secret = remember { SecretSequence() }
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
                            .clickable(role = Role.Button) {
                                if (key == BACKSPACE_KEY) onBackspace() else onDigit(key)
                                if (secret.press(key)) Toast.makeText(context, SECRET_MESSAGE, Toast.LENGTH_LONG).show()
                            }
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
