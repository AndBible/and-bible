package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.speak.SpeakBookmarkRowVd
import net.bible.sharedcore.speak.SpeakTransportVd
import net.bible.sharedui.reading.ChooseSpeakBookmarkDialog
import net.bible.sharedui.reading.SpeakTransportBar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SpeakTransportBarGoldenTest {
    private fun bar(s: SpeakTransportVd) = @Composable {
        SpeakTransportBar(s, onPlayPause = {}, onStop = {}, onRewind = {}, onForward = {},
            onPrev = {}, onNext = {}, onBookmark = {}, onConfig = {}, onSpeed = {})
    }
    private val playing = SpeakTransportVd(visible = true, playing = true, stopped = false,
        statusText = "Reading John 3:16", speedPercent = 150, bookmarkButtonVisible = true)
    private val paused = playing.copy(playing = false, paused = true, statusText = "Paused")
    private val stopped = SpeakTransportVd(visible = true, playing = false, stopped = true,
        statusText = "", speedPercent = 100, bookmarkButtonVisible = false)

    @Test fun playing_matrix() = captureMatrix("SpeakTransportBar", "playing") { bar(playing)() }
    @Test @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun playing_rtl() = captureRtl("SpeakTransportBar", "playing") { bar(playing)() }
    @Test fun paused_light() = captureGolden("SpeakTransportBar", "paused", EDGE_MODE) { bar(paused)() }
    @Test fun stopped_noBookmark_light() = captureGolden("SpeakTransportBar", "stopped", EDGE_MODE) { bar(stopped)() }
    @Test fun bookmarkDialog_light() = captureGolden("SpeakTransportBar", "bookmarkDialog", EDGE_MODE) {
        ChooseSpeakBookmarkDialog(
            rows = listOf(SpeakBookmarkRowVd("b1", "Gen 1:1 (KJV)"), SpeakBookmarkRowVd("b2", "John 3:16 (KJV)")),
            onChoose = {}, onDismiss = {},
        )
    }
}
