package net.bible.sharedcore.speak

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SpeakTransportControllerTest {
    private class FakeTransport : SpeakTransportService {
        val _s = MutableStateFlow(SpeakTransportStateVd())
        override val state: StateFlow<SpeakTransportStateVd> = _s.asStateFlow()
        var spoke = 0; var paused = 0; var continued = 0; var stopped = 0
        var rewound = 0; var forwarded = 0; var prev = 0; var next = 0
        var spokeFromId: String? = null
        var bookmarks = listOf(SpeakBookmarkRowVd("b1", "Gen 1:1"), SpeakBookmarkRowVd("b2", "John 3:16"))
        override fun speakAny() { spoke++ }
        override fun pause() { paused++ }
        override fun continueAfterPause() { continued++ }
        override fun stop() { stopped++ }
        override fun rewind() { rewound++ }
        override fun forward() { forwarded++ }
        override fun prevVerse() { prev++ }
        override fun nextVerse() { next++ }
        override fun speakBookmarks() = bookmarks
        override fun speakFromBookmark(id: String) { spokeFromId = id }
    }
    private class FakeSettings : SpeakSettingsService {
        val _pb = MutableStateFlow(SpeakPlaybackVd(120, true, true, false, 0, null))
        override val playback: StateFlow<SpeakPlaybackVd> = _pb.asStateFlow()
        override val advanced = MutableStateFlow(AdvancedSpeakVd(true, false, false, false)).asStateFlow()
        var speed: Int? = null
        override fun setSpeed(percent: Int) { speed = percent }
        override fun setSpeakChapterChanges(on: Boolean) {}
        override fun setSpeakTitles(on: Boolean) {}
        override fun setSpeakFootnotes(on: Boolean) {}
        override fun clearRepeatRange() {}
        override fun setSynchronize(on: Boolean) {}
        override fun setReplaceDivineName(on: Boolean) {}
        override fun setAutoBookmark(on: Boolean) {}
        override fun setRestoreSettingsFromBookmarks(on: Boolean) {}
    }

    private fun controller(t: FakeTransport, s: FakeSettings) =
        SpeakTransportController(t, s, CoroutineScope(Dispatchers.Unconfined), onConfig = {})

    @Test fun play_pause_branch() {
        val t = FakeTransport(); val c = controller(t, FakeSettings())
        // stopped ⇒ speakAny
        c.togglePlayPause(); assertEquals(1, t.spoke)
        // speaking ⇒ pause
        t._s.value = t._s.value.copy(speaking = true, stopped = false)
        c.togglePlayPause(); assertEquals(1, t.paused)
        // paused ⇒ continue
        t._s.value = t._s.value.copy(speaking = false, paused = true)
        c.togglePlayPause(); assertEquals(1, t.continued)
    }

    @Test fun transport_pass_through() {
        val t = FakeTransport(); val c = controller(t, FakeSettings())
        c.stop(); c.rewind(); c.forward(); c.prevVerse(); c.nextVerse()
        assertEquals(1, t.stopped); assertEquals(1, t.rewound); assertEquals(1, t.forwarded)
        assertEquals(1, t.prev); assertEquals(1, t.next)
    }

    @Test fun set_speed_delegates_to_settings() {
        val s = FakeSettings(); val c = controller(FakeTransport(), s)
        c.setSpeed(175); assertEquals(175, s.speed)
    }

    @Test fun state_combines_seam_and_speed() = runTest {
        val t = FakeTransport(); val s = FakeSettings(); val c = controller(t, s)
        t._s.value = SpeakTransportStateVd(visible = true, speaking = true, stopped = false,
            statusText = "Reading John 3", bookmarkButtonVisible = true)
        val v = c.state.value
        assertTrue(v.visible); assertTrue(v.playing); assertFalse(v.stopped)
        assertEquals("Reading John 3", v.statusText); assertEquals(120, v.speedPercent)
        assertTrue(v.bookmarkButtonVisible)
    }

    @Test fun bookmark_dialog_open_choose_dismiss() {
        val t = FakeTransport(); val c = controller(t, FakeSettings())
        c.onBookmarkButton()
        val d = c.dialog.value
        assertTrue(d is SpeakTransportDialog.ChooseSpeakBookmark)
        assertEquals(2, (d as SpeakTransportDialog.ChooseSpeakBookmark).rows.size)
        c.onSpeakBookmarkChosen("b2")
        assertEquals("b2", t.spokeFromId)
        assertEquals(SpeakTransportDialog.None, c.dialog.value)
    }

    @Test fun config_lambda_fires() {
        var configured = 0
        val c = SpeakTransportController(FakeTransport(), FakeSettings(),
            CoroutineScope(Dispatchers.Unconfined), onConfig = { configured++ })
        c.onConfig(); assertEquals(1, configured)
    }
}
