package io.github.henriksten65.alarmclock.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.henriksten65.alarmclock.AlarmService
import io.github.henriksten65.alarmclock.AlarmSound
import io.github.henriksten65.alarmclock.R

/** How long a preview plays at most, so a long song doesn't keep going. */
internal const val PREVIEW_MILLIS = 15_000L

/**
 * Plays one sound on the alarm volume (as it will sound when an alarm rings) for at most
 * [PREVIEW_MILLIS]. [onStopped] is called when it ends by itself, and [onError] (after
 * [onStopped]) when the sound can't be played.
 */
internal class SoundPreviewPlayer(
    private val context: Context,
    private val onStopped: () -> Unit,
    private val onError: () -> Unit,
) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stop(); onStopped() }

    val isPlaying: Boolean get() = player != null

    fun play(uri: Uri) {
        stop()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setAudioAttributes(AlarmSound.audioAttributes)
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.setOnPreparedListener { if (player === it) it.start() }
            mediaPlayer.setOnCompletionListener { if (player === it) { stop(); onStopped() } }
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                if (player === mp) { stop(); onStopped(); onError() }
                true
            }
            mediaPlayer.prepareAsync()
            handler.postDelayed(timeout, PREVIEW_MILLIS)
        } catch (e: Exception) {
            stop()
            onStopped()
            onError()
        }
    }

    fun stop() {
        handler.removeCallbacks(timeout)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
}

/**
 * A ▶/■ button that plays [soundUri] (null: the phone's default alarm sound). The preview
 * stops when the sound changes, the button leaves the screen (dialog closed, phone rotated) or
 * the app goes to the background (e.g. a sound picker opens), and it can't start while an
 * alarm or timer rings.
 */
@Composable
internal fun SoundPreviewButton(soundUri: String?) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    val player = remember {
        SoundPreviewPlayer(
            context,
            onStopped = { playing = false },
            onError = { Toast.makeText(context, R.string.cant_play_sound, Toast.LENGTH_SHORT).show() },
        )
    }
    val ringing by AlarmService.ringingAlarmId.collectAsStateWithLifecycle()

    DisposableEffect(soundUri) {
        onDispose { player.stop(); playing = false }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { player.stop(); playing = false }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(ringing) {
        if (ringing != null) { player.stop(); playing = false }
    }

    IconButton(
        enabled = ringing == null,
        onClick = {
            if (playing) {
                player.stop()
                playing = false
            } else {
                playing = true
                player.play(soundUri?.toUri() ?: Settings.System.DEFAULT_ALARM_ALERT_URI)
            }
        },
    ) {
        if (playing) {
            Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.stop_sound))
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play_sound))
        }
    }
}
