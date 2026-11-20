package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RoborazziComposeFontScaleOption
import com.github.takahirom.roborazzi.RoborazziComposeOption
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ui.dialog.DialogAnswer
import net.bible.sharedui.components.AbOptionsDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens for [AbOptionsDialog]'s answer cards (spec §5.3): two answers, four with one destructive, checkbox and AX5. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbOptionsDialogGoldenTest {

    @Composable private fun TwoAnswers() = AbOptionsDialog(
        title = "Delete the label “Sermon notes”?",
        message = "Some bookmarks have only this label.",
        options = listOf(
            DialogAnswer("labelOnly", "Delete label only", "The bookmarks stay, without a label."),
            DialogAnswer("both", "Delete label and bookmarks", "The bookmarks with this label are deleted too.", destructive = true),
        ),
        onSelect = {}, onDismissRequest = {}, dismissText = "Cancel",
    )

    private val cloudSync = listOf(
        DialogAnswer("cloud", "Use the cloud copy", "Replaces what is on this device with the version stored in the cloud."),
        DialogAnswer("device", "Keep this device's copy", "Overwrites the cloud version with what is on this device."),
        DialogAnswer("merge", "Merge both copies", "Combines the two where possible and asks about each conflict."),
        DialogAnswer("reset", "Start over in the cloud", "Deletes the cloud copy and uploads this device's data as a new first version.", destructive = true),
    )

    @Composable private fun FourAnswers() = AbOptionsDialog(
        title = "How should the two copies be reconciled?", message = null,
        options = cloudSync, onSelect = {}, onDismissRequest = {}, dismissText = "Cancel",
    )

    @Composable private fun Checkbox() = AbOptionsDialog(
        title = "New version available",
        message = "AndBible production-99999 is available. You have 6.0.1266.",
        options = listOf(DialogAnswer("download", "Download"), DialogAnswer("later", "Later")),
        onSelect = {}, onDismissRequest = {}, cancellable = false, checkboxLabel = "Don't notify me about updates",
    )

    /** AX5: the real activity font scale (Roborazzi option); a LocalDensity override does not reach the Dialog window. */
    private val ax5 = listOf<RoborazziComposeOption>(RoborazziComposeFontScaleOption(3f))

    @Test fun twoAnswers_matrix() = captureMatrix("AbOptionsDialog", "twoAnswers") { TwoAnswers() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun twoAnswers_rtl() = captureRtl("AbOptionsDialog", "twoAnswers") { TwoAnswers() }

    @Test fun fourAnswers_matrix() = captureMatrix("AbOptionsDialog", "fourAnswers", heightDp = 640) { FourAnswers() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun fourAnswers_rtl() = captureRtl("AbOptionsDialog", "fourAnswers", heightDp = 640) { FourAnswers() }

    @Test fun checkbox_matrix() = captureMatrix("AbOptionsDialog", "checkbox") { Checkbox() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun checkbox_rtl() = captureRtl("AbOptionsDialog", "checkbox") { Checkbox() }

    /** AX5 with the capture height raised, so the scroll position (cards cut off at the bottom) shows honestly. */
    @Test fun ax5FourAnswers_matrix() =
        captureMatrix("AbOptionsDialog", "ax5FourAnswers", heightDp = 800, captureOptions = ax5) { FourAnswers() }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun ax5FourAnswers_rtl() =
        captureRtl("AbOptionsDialog", "ax5FourAnswers", heightDp = 800, captureOptions = ax5) { FourAnswers() }
}
