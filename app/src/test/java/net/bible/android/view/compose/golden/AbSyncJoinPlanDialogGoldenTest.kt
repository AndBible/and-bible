package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.DialogAnswer
import net.bible.sharedui.components.AbSyncJoinPlanDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens for [AbSyncJoinPlanDialog]: the decision page (merge default, one destructive row) and both confirmations. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbSyncJoinPlanDialogGoldenTest {

    private val choices = listOf(
        DialogAnswer("merge", "Merge both", "Combines the two where possible."),
        DialogAnswer("fetch", "Copy from cloud", "Replaces what is on this device with the cloud copy.", destructive = true),
        DialogAnswer("disable", "Do not sync this category", "Leaves it out of cloud sync."),
    )

    private val request = AppDialogRequest.SyncJoinPlan(
        title = "The cloud already has data",
        lead = "Choose what happens to each kind of data the first time this device syncs.",
        rows = listOf(
            AppDialogRequest.SyncJoinPlan.Row("BOOKMARKS", "Bookmarks and notes"),
            AppDialogRequest.SyncJoinPlan.Row("WORKSPACES", "Workspaces"),
        ),
        uploadNotes = listOf("Reading plans are not in the cloud yet and will be uploaded."),
        choices = choices, defaultChoice = "merge",
        confirmTitle = "Ready to sync?",
        confirmLead = "This is what will happen:",
        outcomeLine = { key, choice ->
            val what = if (key == "BOOKMARKS") "Bookmarks and notes" else "Workspaces"
            when (choice) {
                "fetch" -> "$what: replaced by the cloud copy"
                "disable" -> "$what: not synced"
                else -> "$what: merged with the cloud copy"
            }
        },
        createOutcomes = listOf("Reading plans: uploaded to the cloud"),
        warning = "Copying from the cloud permanently replaces the data on this device.",
        nextText = "Next", backText = "Back", cancelText = "Cancel",
        startText = "Start sync", replaceText = "Replace and sync",
    )

    /** One row set to the destructive answer, the other left at the merge default. */
    private val destructiveChoice = mapOf("WORKSPACES" to "fetch")

    @Composable private fun Dialog(step: Int, chosen: Map<String, String> = emptyMap()) =
        AbSyncJoinPlanDialog(request, onResult = {}, initialStep = step, initialChoices = chosen)

    @Test fun decision_mergeDefault() = captureMatrix("AbSyncJoinPlanDialog", "decision_mergeDefault", heightDp = 640) { Dialog(0) }

    @Test fun decision_withDestructive() =
        captureMatrix("AbSyncJoinPlanDialog", "decision_withDestructive", heightDp = 640) { Dialog(0, destructiveChoice) }

    @Test fun confirm_safe() = captureMatrix("AbSyncJoinPlanDialog", "confirm_safe", heightDp = 640) { Dialog(1) }

    @Test fun confirm_destructive() =
        captureMatrix("AbSyncJoinPlanDialog", "confirm_destructive", heightDp = 640) { Dialog(1, destructiveChoice) }
}
