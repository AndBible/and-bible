package net.bible.android.view.compose.golden

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.dialog.AbTextPage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens of [AbTextPage]: a decision page (disclaimer-like) and a read-only page (licence-like). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbTextPageGoldenTest {
    private val longText = (1..14).joinToString("\n\n") {
        "Paragraph $it. AI can make mistakes, so always check the result against Scripture before you rely on it."
    }

    @Composable private fun Decision() = AbTextPage(
        topic = "AI disclaimer", onClose = {}, confirmText = "I accept", dismissText = "Cancel",
    ) { Text(longText) }

    @Composable private fun ReadOnly() = AbTextPage(topic = "Licence", onClose = {}) { Text(longText) }

    @Test fun decision_matrix() = captureMatrix("AbTextPage", "decision") { Decision() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun decision_rtl() = captureRtl("AbTextPage", "decision") { Decision() }

    @Test fun readOnly_matrix() = captureMatrix("AbTextPage", "readOnly") { ReadOnly() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun readOnly_rtl() = captureRtl("AbTextPage", "readOnly") { ReadOnly() }
}
