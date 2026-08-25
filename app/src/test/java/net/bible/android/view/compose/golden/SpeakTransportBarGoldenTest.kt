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
    private fun bar(s: SpeakTransportVd, ownsTopEdge: Boolean = true) = @Composable {
        SpeakTransportBar(s, onPlayPause = {}, onStop = {}, onRewind = {}, onForward = {},
            onPrev = {}, onNext = {}, onBookmark = {}, onConfig = {}, ownsTopEdge = ownsTopEdge)
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

    /**
     * Round 14b §6: the bar as it renders with the agent panel stacked on top of it — square top,
     * no shadow, SAME tonal elevation. All four modes, not just light: a dropped shadow and a lost
     * corner radius are exactly the kind of difference that reads worst in the black-and-white and
     * e-ink palettes, where there is no hue left to carry the separation.
     *
     * This proves the BAR's half of the change in isolation. The pair — this bar directly beneath a
     * real `AgentLogPanel` — is `ReadingViewScreenGoldenTest.withAgentLogAndSpeakBar`, and per spec
     * §9 that one stays light-only: both surfaces are `tonalElevation = 3.dp` and the display-mode
     * mapping (`AbColorScheme.grayed`) is applied to both identically, so a second mode of the pair
     * would re-prove what this matrix already covers. How the pair reads against a LIVE navigation
     * bar is device-pass-only and is named in the round's checklist.
     */
    @Test fun underAgentPanel_matrix() =
        captureMatrix("SpeakTransportBar", "underAgentPanel") { bar(playing, ownsTopEdge = false)() }

    @Test fun bookmarkDialog_light() = captureGolden("SpeakTransportBar", "bookmarkDialog", EDGE_MODE) {
        ChooseSpeakBookmarkDialog(
            rows = listOf(SpeakBookmarkRowVd("b1", "Gen 1:1 (KJV)"), SpeakBookmarkRowVd("b2", "John 3:16 (KJV)")),
            onChoose = {}, onDismiss = {},
        )
    }
}
