package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.bible.android.TEST_SDK
import net.bible.sharedui.desktop.exit.ExitSyncStatus
import net.bible.sharedui.theme.LocalDisableAnimations
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ExitSyncStatusGoldenTest {
    private fun content(noAnimations: Boolean = false, closing: Boolean = false) = @Composable {
        CompositionLocalProvider(LocalDisableAnimations provides noAnimations) {
            ExitSyncStatus(closing = closing, onQuitNow = {})
        }
    }

    @Test fun light() = captureGolden("ExitSyncStatus", "syncing", GoldenMode.LIGHT, content = content())
    @Test fun dark() = captureGolden("ExitSyncStatus", "syncing", GoldenMode.DARK, content = content())
    @Test fun mono() {
        captureGolden("ExitSyncStatus", "syncing", GoldenMode.MONO, content = content())
        auditMono("ExitSyncStatus", "syncing", 0, emptyList(), content = content())
    }
    @Test fun noAnim() = captureGolden("ExitSyncStatus", "noAnim", GoldenMode.LIGHT, content = content(noAnimations = true))
    @Test fun closing() = captureGolden("ExitSyncStatus", "closing", GoldenMode.LIGHT, content = content(closing = true))
}
