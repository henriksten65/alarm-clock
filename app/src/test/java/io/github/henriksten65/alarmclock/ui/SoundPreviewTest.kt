package io.github.henriksten65.alarmclock.ui

import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.henriksten65.alarmclock.TestSupport
import io.github.henriksten65.alarmclock.TestSupport.context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.time.Duration

/** The ▶/■ sound preview next to the alarm sound (Settings and the edit dialog). */
@RunWith(AndroidJUnit4::class)
class SoundPreviewTest {

    @get:Rule
    val compose = createComposeRule()

    private val song = Uri.parse("content://media/audio/5")

    @Before
    fun setUp() {
        TestSupport.resetAppState()
        ShadowMediaPlayer.resetStaticState()
    }

    /** Lets the fake media player "play" [uri] for [millis]; others fail as if unreadable. */
    private fun playable(uri: Uri, millis: Int = 60_000) =
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(context, uri), ShadowMediaPlayer.MediaInfo(millis, 0))

    private fun idle(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
        compose.waitForIdle()
    }

    @Test
    fun playsUntilStopped() {
        playable(song)
        compose.setContent { SoundPreviewButton(song.toString()) }
        compose.onNodeWithContentDescription("Play sound").performClick()
        idle(100)
        compose.onNodeWithContentDescription("Stop sound").performClick()
        idle(100)
        compose.onNodeWithContentDescription("Play sound").assertExists()
    }

    @Test
    fun stopsByItselfAfter15Seconds() {
        playable(song)
        val player = SoundPreviewPlayer(context, onStopped = {}, onError = {})
        player.play(song)
        idle(PREVIEW_MILLIS - 1_000)
        assertTrue(player.isPlaying)
        idle(2_000)
        assertFalse(player.isPlaying)
    }

    @Test
    fun aShortSoundStopsWhenItEnds() {
        playable(song, millis = 1_000)
        var stopped = 0
        val player = SoundPreviewPlayer(context, onStopped = { stopped++ }, onError = {})
        player.play(song)
        idle(2_000)
        assertFalse(player.isPlaying)
        assertEquals(1, stopped)
    }

    @Test
    fun unplayableSoundSaysSo() {
        compose.setContent { SoundPreviewButton("content://media/audio/404") }
        compose.onNodeWithContentDescription("Play sound").performClick()
        idle(100)
        compose.onNodeWithContentDescription("Play sound").assertExists()
        assertEquals("Can't play this sound", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun changingTheSoundStopsThePreview() {
        playable(song)
        var uri by mutableStateOf<String?>(song.toString())
        compose.setContent { SoundPreviewButton(uri) }
        compose.onNodeWithContentDescription("Play sound").performClick()
        idle(100)
        compose.onNodeWithContentDescription("Stop sound").assertExists()
        uri = null
        idle(100)
        compose.onNodeWithContentDescription("Play sound").assertExists()
    }
}
