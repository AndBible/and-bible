package net.bible.sharedcore.speak

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Drives the Compose speak-transport bar. iOS-clean: sees only [SpeakTransportService] (transport +
 * visibility) and [SpeakSettingsService] (speed). [onConfig] is a host lambda (launches Screen.BibleSpeak).
 */
class SpeakTransportController(
    private val transport: SpeakTransportService,
    private val settings: SpeakSettingsService,
    scope: CoroutineScope,
    private val onConfig: () -> Unit,
) {
    private val _state = MutableStateFlow(combineState(transport.state.value, settings.playback.value.speedPercent))
    val state: StateFlow<SpeakTransportVd> = _state.asStateFlow()

    private val _dialog = MutableStateFlow<SpeakTransportDialog>(SpeakTransportDialog.None)
    val dialog: StateFlow<SpeakTransportDialog> = _dialog.asStateFlow()

    init {
        combine(transport.state, settings.playback) { t, pb -> combineState(t, pb.speedPercent) }
            .onEach { _state.value = it }
            .launchIn(scope)
    }

    private fun combineState(t: SpeakTransportStateVd, speedPercent: Int) = SpeakTransportVd(
        visible = t.visible,
        playing = t.speaking,
        paused = t.paused,
        stopped = t.stopped,
        statusText = t.statusText,
        speedPercent = speedPercent,
        bookmarkButtonVisible = t.bookmarkButtonVisible,
    )

    /** Classic SpeakTransportWidget.onButtonClick play/pause branch (:157-169). */
    fun togglePlayPause() {
        val s = transport.state.value
        when {
            s.paused -> transport.continueAfterPause()
            s.speaking -> transport.pause()
            else -> transport.speakAny()
        }
    }

    fun stop() = transport.stop()
    fun rewind() = transport.rewind()
    fun forward() = transport.forward()
    fun prevVerse() = transport.prevVerse()
    fun nextVerse() = transport.nextVerse()
    fun setSpeed(percent: Int) = settings.setSpeed(percent)
    fun onConfig() = onConfig.invoke()

    fun onBookmarkButton() {
        _dialog.value = SpeakTransportDialog.ChooseSpeakBookmark(transport.speakBookmarks())
    }

    fun onSpeakBookmarkChosen(id: String) {
        transport.speakFromBookmark(id)
        _dialog.value = SpeakTransportDialog.None
    }

    fun dismissDialog() { _dialog.value = SpeakTransportDialog.None }
}
