package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.speak.SpeakPlaybackVd
import net.bible.sharedui.speak.SpeakSettingsContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BibleSpeakGoldenTest {
    private fun content(pb: SpeakPlaybackVd) = @androidx.compose.runtime.Composable {
        androidx.compose.foundation.layout.Column {
            SpeakSettingsContent(
                playback = pb,
                onSpeedChange = {}, onSpeakChapterChanges = {}, onSpeakTitles = {}, onSpeakFootnotes = {},
                onOpenRepeatRange = {}, onOpenSleepTimer = {}, onOpenAdvanced = {},
                onSystemTtsSettings = {}, onHelp = {},
            )
        }
    }

    private val primary = SpeakPlaybackVd(
        speedPercent = 150, speakChapterChanges = true, speakTitles = true, speakFootnotes = false,
        sleepTimerMinutes = 0, repeatRangeName = null,
    )

    @Test fun primary() = captureMatrix("BibleSpeak", "primary", heightDp = 900, content = content(primary))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun primary_rtl() = captureRtl("BibleSpeak", "primary", heightDp = 900, content = content(primary))

    @Test fun sleep_set() = captureGolden("BibleSpeak", "sleep_set", EDGE_MODE, heightDp = 900,
        content = content(primary.copy(sleepTimerMinutes = 15)))

    @Test fun repeat_set() = captureGolden("BibleSpeak", "repeat_set", EDGE_MODE, heightDp = 900,
        content = content(primary.copy(repeatRangeName = "Ps 23:1-6")))
}
