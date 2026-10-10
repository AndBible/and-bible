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

    @Test fun playing_matrix() = captureMatrix("SpeakTransportBar", "playing", content = playingContent())
    @Test @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun playing_rtl() = captureRtl("SpeakTransportBar", "playing") { bar(playing)() }
    @Test fun paused_light() = captureGolden("SpeakTransportBar", "paused", EDGE_MODE, content = pausedContent())
    @Test fun stopped_noBookmark_light() = captureGolden("SpeakTransportBar", "stopped", EDGE_MODE, content = stoppedContent())

    /**
     * Round 14b §6, corrected in fix round 1: this matrix proves the squared top corner and the
     * unchanged tonal elevation in all four modes. It does NOT prove the shadow half: this renderer
     * draws no Compose elevation shadow in any mode, so the capture would look the same whether
     * `shadowElevation` were 8.dp or 0.dp. All four modes still earn their place on their own —
     * measured per-mode pixel differences are light 450, dark 223, bw 439, eink 439, all confined to
     * the top band (rows 0-81), so BW and e-ink are not redundant with light. (The earlier
     * justification, "a dropped shadow reads worst in BW", does not hold once no shadow renders at
     * all — the corner and elevation differences alone are what these numbers measure.)
     *
     * This proves the BAR's half of the change in isolation. The pair — this bar directly beneath a
     * real `AgentLogPanel` — is `ReadingViewScreenGoldenTest.withAgentLogAndSpeakBar`, and per spec
     * §9 that one stays light-only: both surfaces are `tonalElevation = 3.dp` and the display-mode
     * mapping (`AbColorScheme.grayed`) is applied to both identically, so a second mode of the pair
     * would re-prove what this matrix already covers. How the pair reads against a LIVE navigation
     * bar, and whether the shadow is actually gone, is device-pass-only and is named in the round's
     * checklist.
     */
    @Test fun underAgentPanel_matrix() =
        captureMatrix("SpeakTransportBar", "underAgentPanel", content = underAgentPanelContent())

    @Test fun bookmarkDialog_light() = captureGolden("SpeakTransportBar", "bookmarkDialog", EDGE_MODE, content = bookmarkDialogContent())

    private fun playingContent(): @androidx.compose.runtime.Composable () -> Unit = { bar(playing)() }

    private fun pausedContent(): @androidx.compose.runtime.Composable () -> Unit = { bar(paused)() }

    private fun stoppedContent(): @androidx.compose.runtime.Composable () -> Unit = { bar(stopped)() }

    private fun underAgentPanelContent(): @androidx.compose.runtime.Composable () -> Unit = { bar(playing, ownsTopEdge = false)() }

    private fun bookmarkDialogContent(): @androidx.compose.runtime.Composable () -> Unit = {
        ChooseSpeakBookmarkDialog(
            rows = listOf(SpeakBookmarkRowVd("b1", "Gen 1:1 (KJV)"), SpeakBookmarkRowVd("b2", "John 3:16 (KJV)")),
            onChoose = {}, onDismiss = {},
        )
    }

    @Test
    fun playing_mono() {
        MONO_MODES.forEach { mode -> captureGolden("SpeakTransportBar", "playing", mode, content = playingContent()) }
    }

    @Test
    fun paused_mono() {
        MONO_MODES.forEach { mode -> captureGolden("SpeakTransportBar", "paused", mode, content = pausedContent()) }
    }

    @Test
    fun stopped_mono() {
        MONO_MODES.forEach { mode -> captureGolden("SpeakTransportBar", "stopped", mode, content = stoppedContent()) }
    }

    @Test
    fun underAgentPanel_mono() {
        MONO_MODES.forEach { mode -> captureGolden("SpeakTransportBar", "underAgentPanel", mode, content = underAgentPanelContent()) }
    }

    @Test
    fun bookmarkDialog_mono() {
        MONO_MODES.forEach { mode -> captureGolden("SpeakTransportBar", "bookmarkDialog", mode, content = bookmarkDialogContent()) }
    }
}
