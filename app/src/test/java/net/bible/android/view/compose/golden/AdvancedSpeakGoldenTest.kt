package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.speak.AdvancedSpeakVd
import net.bible.sharedui.speak.AdvancedSpeakSettingsContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AdvancedSpeakGoldenTest {
    private fun content(a: AdvancedSpeakVd) = @androidx.compose.runtime.Composable {
        androidx.compose.foundation.layout.Column {
            AdvancedSpeakSettingsContent(
                advanced = a,
                onSynchronize = {}, onReplaceDivineName = {},
                onAutoBookmark = {}, onRestoreSettingsFromBookmarks = {},
            )
        }
    }
    private val primary = AdvancedSpeakVd(
        synchronize = true, replaceDivineName = false,
        autoBookmark = true, restoreSettingsFromBookmarks = false,
    )

    @Test fun primary() = captureMatrix("AdvancedSpeak", "primary", heightDp = 600, content = content(primary))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun primary_rtl() = captureRtl("AdvancedSpeak", "primary", heightDp = 600, content = content(primary))
}
