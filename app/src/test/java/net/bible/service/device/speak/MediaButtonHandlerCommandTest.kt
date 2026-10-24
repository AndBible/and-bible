package net.bible.service.device.speak

import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakPlaybackState
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.sharedcore.platform.speak.MediaPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaButtonHandlerCommandTest {
    private val calls = mutableListOf<String>()
    private var enabled = true
    private val commands = SpeakMediaCommands(
        pause = { calls += "pause" },
        continueLastPosition = { calls += "continue" },
        stop = { calls += "stop" },
        forward = { calls += "forward:$it" },
        rewind = { calls += "rewind:$it" },
        buttonsEnabled = { enabled },
    )

    @Test fun playContinuesOnlyWhileButtonsAreEnabled() {
        commands.play()
        enabled = false
        commands.play()
        assertEquals(listOf("continue"), calls)
    }

    @Test fun transportCommandsPreserveDirectionAndRewindAmount() {
        commands.next()
        commands.previous()
        commands.fastForward()
        commands.rewind()
        assertEquals(listOf(
            "forward:${SpeakSettings.RewindAmount.SMART}",
            "rewind:${SpeakSettings.RewindAmount.SMART}",
            "forward:${SpeakSettings.RewindAmount.ONE_VERSE}",
            "rewind:${SpeakSettings.RewindAmount.ONE_VERSE}",
        ), calls)
    }

    @Test fun disablingButtonsDoesNotSuppressPauseStopOrTransportCommands() {
        enabled = false
        commands.pause()
        commands.stop()
        commands.next()
        commands.previous()
        commands.fastForward()
        commands.rewind()
        assertEquals(listOf("pause", "stop",
            "forward:${SpeakSettings.RewindAmount.SMART}",
            "rewind:${SpeakSettings.RewindAmount.SMART}",
            "forward:${SpeakSettings.RewindAmount.ONE_VERSE}",
            "rewind:${SpeakSettings.RewindAmount.ONE_VERSE}",
        ), calls)
    }

    @Test fun speechStatesKeepPausedStoppedAndPlayingDistinct() {
        assertEquals(MediaPlaybackState.PAUSED, mediaStateFor(SpeakChange.State(SpeakPlaybackState.PAUSED)))
        assertEquals(MediaPlaybackState.STOPPED, mediaStateFor(SpeakChange.State(SpeakPlaybackState.SILENT)))
        assertEquals(MediaPlaybackState.PLAYING, mediaStateFor(SpeakChange.State(SpeakPlaybackState.SPEAKING)))
    }

    @Test fun temporaryStopDoesNotReplaceMediaState() {
        assertNull(mediaStateFor(SpeakChange.State(SpeakPlaybackState.TEMPORARY_STOP)))
    }
}
