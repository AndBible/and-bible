package net.bible.android.view.compose.golden

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogDetail
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.DialogButtonRole
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbDialogShellGoldenTest {

    @Composable private fun ShortConfirm() = AbDialogShell(
        onDismissRequest = {},
        buttons = {
            AbDialogButton("Keep editing", DialogButtonRole.Cancel, {})
            AbDialogButton("Discard", DialogButtonRole.Destructive, {})
        },
        lead = { Text("Discard changes?") },
        content = { AbDialogDetail("Your edits to this prompt will be lost.") },
    )

    @Composable private fun DestructiveDetail() = AbDialogShell(
        onDismissRequest = {},
        buttons = {
            AbDialogButton("Cancel", DialogButtonRole.Cancel, {})
            AbDialogButton("Delete", DialogButtonRole.Destructive, {})
        },
        lead = { Text("Delete the label \u201CSermon notes\u201D?") },
        content = { AbDialogDetail("Its Study Pad notes are deleted too. This cannot be undone.") },
    )

    @Composable private fun WrappingLead() = AbDialogShell(
        onDismissRequest = {},
        buttons = {
            AbDialogButton("Cancel", DialogButtonRole.Cancel, {})
            AbDialogButton("Delete", DialogButtonRole.Destructive, {})
        },
        lead = {
            Text(
                "Delete the label \u201CSermon notes from the 2026 autumn conference in Helsinki, " +
                    "including the study pad drafts\u201D from every workspace on this device?",
            )
        },
    )

    @Composable private fun ScrollingBody() = AbDialogShell(
        onDismissRequest = {},
        buttons = {
            AbDialogButton("Cancel", DialogButtonRole.Cancel, {})
            AbDialogButton("Continue", DialogButtonRole.Confirm, {})
        },
        lead = { Text("Review the changes") },
        content = {
            Text(
                (1..40).joinToString(" ") { "Sentence $it of the long body." },
            )
        },
    )

    @Test fun shortConfirm_matrix() = captureMatrix("AbDialogShell", "shortConfirm") { ShortConfirm() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun shortConfirm_rtl() = captureRtl("AbDialogShell", "shortConfirm") { ShortConfirm() }

    @Test fun destructiveDetail_matrix() = captureMatrix("AbDialogShell", "destructiveDetail") { DestructiveDetail() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun destructiveDetail_rtl() = captureRtl("AbDialogShell", "destructiveDetail") { DestructiveDetail() }

    @Test fun wrappingLead_matrix() = captureMatrix("AbDialogShell", "wrappingLead") { WrappingLead() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun wrappingLead_rtl() = captureRtl("AbDialogShell", "wrappingLead") { WrappingLead() }

    @Test fun scrollingBody_matrix() = captureMatrix("AbDialogShell", "scrollingBody") { ScrollingBody() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun scrollingBody_rtl() = captureRtl("AbDialogShell", "scrollingBody") { ScrollingBody() }

    /** AX5: fontScale 3 through LocalDensity, as AbReleaseNoticeCardGoldenTest's largeFont does. */
    @Composable private fun Ax5(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 3f), content = content)
    }

    @Test fun ax5Confirm_matrix() = captureMatrix("AbDialogShell", "ax5Confirm") { Ax5 { ShortConfirm() } }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun ax5Confirm_rtl() = captureRtl("AbDialogShell", "ax5Confirm") { Ax5 { ShortConfirm() } }
}
