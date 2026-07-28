package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.speak.AdvancedSpeakVd
import net.bible.sharedcore.speak.SpeakTransportVd
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.speak.AdvancedSpeakSettingsScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AdvancedSpeakGoldenTest {
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

    private val screen = @androidx.compose.runtime.Composable {
        AdvancedSpeakSettingsScreen(
            advanced = AdvancedSpeakVd(synchronize = true, replaceDivineName = false, autoBookmark = true, restoreSettingsFromBookmarks = false),
            onSynchronize = {}, onReplaceDivineName = {}, onAutoBookmark = {}, onRestoreSettingsFromBookmarks = {},
            onHelp = {}, transportBar = transportBar, onNavigateUp = {},
        )
    }

    @Test fun primary() = captureMatrix("AdvancedSpeak", "primary", content = screen)

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun primary_rtl() = captureRtl("AdvancedSpeak", "primary", content = screen)
}
