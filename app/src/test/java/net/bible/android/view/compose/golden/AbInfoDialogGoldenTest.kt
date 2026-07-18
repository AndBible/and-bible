package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbInfoDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbInfoDialogGoldenTest {

    /** A long, multi-paragraph body (well beyond the 420dp bounded height), so the golden proves
     *  the dialog stays bounded and the body scrolls rather than growing off-screen. */
    private val longBody = (1..10).joinToString("\n\n") { n ->
        "Paragraph $n. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod " +
            "tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis " +
            "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."
    }

    private val shortBody = "This is a short help message.\n\nIt has two short paragraphs."

    @Test fun longBody_matrix() =
        captureMatrix("AbInfoDialog", "long") {
            AbInfoDialog(title = "Help", body = longBody, onDismiss = {})
        }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun longBody_rtl() =
        captureRtl("AbInfoDialog", "long") {
            AbInfoDialog(title = "Help", body = longBody, onDismiss = {})
        }

    @Test fun shortBody_matrix() =
        captureMatrix("AbInfoDialog", "short") {
            AbInfoDialog(title = "About", body = shortBody, onDismiss = {}, confirmLabel = "Close")
        }
}
