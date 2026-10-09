package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import net.bible.android.TEST_SDK
import net.bible.sharedui.reading.BibleReferenceOverlay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BibleReferenceOverlayGoldenTest {
    // AnimatedVisibility renders the final (visible) frame under LocalInspectionMode (see
    // BibleReferenceOverlay's kdoc), so visible = true captures deterministically — no separate
    // hidden-state golden is needed (it would just be an empty capture).
    @Test
    fun visible_matrix() = captureMatrix("BibleReferenceOverlay", "visible", content = visibleContent())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun visible_rtl() = captureRtl("BibleReferenceOverlay", "visible") {
        Box(Modifier.fillMaxSize()) {
            BibleReferenceOverlay(visible = true, text = "KJV:Genesis 1:1")
        }
    }

    private fun visibleContent(): @androidx.compose.runtime.Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            BibleReferenceOverlay(visible = true, text = "KJV:Genesis 1:1")
        }
    }

    @Test
    fun visible_mono() {
        MONO_MODES.forEach { mode -> captureGolden("BibleReferenceOverlay", "visible", mode, content = visibleContent()) }
    }
}
