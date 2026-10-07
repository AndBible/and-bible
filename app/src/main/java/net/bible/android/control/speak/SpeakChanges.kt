package net.bible.android.control.speak

import androidx.annotation.VisibleForTesting
import net.bible.service.device.speak.SpeakCommand
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

/** Playback state of the TTS engine. Not `SpeakState`: that name is the reading-plan screen's enum. */
enum class SpeakPlaybackState { SPEAKING, PAUSED, SILENT, TEMPORARY_STOP }

/** A speak notification, emitted by [SpeakChanges] on the emitter's thread. */
sealed interface SpeakChange {
    data class State(val speakState: SpeakPlaybackState) : SpeakChange {
        val isSpeaking: Boolean get() = speakState == SpeakPlaybackState.SPEAKING
        val isPaused: Boolean get() = speakState == SpeakPlaybackState.PAUSED
        val isStopped: Boolean get() = speakState == SpeakPlaybackState.SILENT
        val isTemporarilyStopped: Boolean get() = speakState == SpeakPlaybackState.TEMPORARY_STOP
    }

    data class Progress(
        val book: Book,
        val key: Key,
        val speakCommand: SpeakCommand?,
        val forceFollow: Boolean = false,
    ) : SpeakChange
}

/** Owner of the process-wide speak playback and progress change stream. */
object SpeakChanges {
    private var source = EventSource<SpeakChange>()

    val changes: Events<SpeakChange> get() = source

    fun notifyState(state: SpeakPlaybackState) = source.emit(SpeakChange.State(state))
    fun notifyProgress(progress: SpeakChange.Progress) = source.emit(progress)

    @VisibleForTesting
    fun resetSubscribersForTest() { source = EventSource() }
}
