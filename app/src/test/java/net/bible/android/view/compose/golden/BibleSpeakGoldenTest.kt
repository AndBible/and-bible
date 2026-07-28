package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.speak.SpeakPlaybackVd
import net.bible.sharedcore.speak.SpeakTransportVd
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.speak.BibleSpeakScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BibleSpeakGoldenTest {
    private val transportState = SpeakTransportVd(
        visible = true, playing = true, stopped = false,
        statusText = "Reading John 3:16", speedPercent = 150, bookmarkButtonVisible = true,
    )

    private val transportBar: @androidx.compose.runtime.Composable () -> Unit = {
        SpeakTransportBar(
            transportState,
            onPlayPause = {}, onStop = {}, onRewind = {}, onForward = {},
            onPrev = {}, onNext = {}, onBookmark = {}, onConfig = {},
            showConfig = false,   // classic: showConfig defaults false on the speak layouts
        )
    }

    private fun screen(pb: SpeakPlaybackVd) = @androidx.compose.runtime.Composable {
        BibleSpeakScreen(
            playback = pb,
            onSpeedChange = {}, onSpeakChapterChanges = {}, onSpeakTitles = {}, onSpeakFootnotes = {},
            onSleepTimerToggle = {}, onToggleRepeatRange = {}, onOpenAdvanced = {},
            onSystemTtsSettings = {}, onHelp = {}, transportBar = transportBar, onNavigateUp = {},
        )
    }
    private val primary = SpeakPlaybackVd(150, speakChapterChanges = true, speakTitles = true, speakFootnotes = false, sleepTimerMinutes = 0, repeatRangeName = null)

    @Test fun primary() = captureMatrix("BibleSpeak", "primary", heightDp = 900, content = screen(primary))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun primary_rtl() = captureRtl("BibleSpeak", "primary", heightDp = 900, content = screen(primary))

    @Test fun sleep_set() = captureGolden("BibleSpeak", "sleep_set", EDGE_MODE, heightDp = 900, content = screen(primary.copy(sleepTimerMinutes = 15)))
    @Test fun repeat_set() = captureGolden("BibleSpeak", "repeat_set", EDGE_MODE, heightDp = 900, content = screen(primary.copy(repeatRangeName = "Gen 1:1-5")))
}
