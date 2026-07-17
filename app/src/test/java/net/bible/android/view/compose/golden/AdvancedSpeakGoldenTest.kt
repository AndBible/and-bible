package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.speak.AdvancedSpeakVd
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
    private val screen = @androidx.compose.runtime.Composable {
        AdvancedSpeakSettingsScreen(
            advanced = AdvancedSpeakVd(synchronize = true, replaceDivineName = false, autoBookmark = true, restoreSettingsFromBookmarks = false),
            onSynchronize = {}, onReplaceDivineName = {}, onAutoBookmark = {}, onRestoreSettingsFromBookmarks = {},
            onHelp = {}, onNavigateUp = {},
        )
    }

    @Test fun primary() = captureMatrix("AdvancedSpeak", "primary", content = screen)

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun primary_rtl() = captureRtl("AdvancedSpeak", "primary", content = screen)
}
