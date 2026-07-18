package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.ai.RawLogSummaryVd
import net.bible.sharedui.ai.RawLogHistoryScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class RawLogHistoryGoldenTest {

    // One error row (hasError=true) plus normal rows with/without a resolved model/token/cost, so
    // the error indicator, blank-promptName "—" fallback, and the model/token/cost lines all render.
    private val summaries = listOf(
        RawLogSummaryVd(
            id = "log-1", promptName = "Explain passage",
            modelInfo = "Anthropic · claude-3-5-sonnet", tokenInfo = "1,204 in / 386 out", costInfo = "$0.012",
            timestamp = "2026-07-15 09:41", hasError = false,
        ),
        RawLogSummaryVd(
            id = "log-2", promptName = "", modelInfo = "OpenAI · gpt-4o", tokenInfo = "540 in / 0 out", costInfo = "",
            timestamp = "2026-07-15 10:02", hasError = true,
        ),
        RawLogSummaryVd(
            id = "log-3", promptName = "Summarize chapter",
            modelInfo = "", tokenInfo = "", costInfo = "",
            timestamp = "2026-07-14 21:17", hasError = false,
        ),
    )

    private fun screen(
        summaries: List<RawLogSummaryVd> = this.summaries,
        selection: Set<String> = emptySet(),
        selectionMode: Boolean = false,
        initiallyHelpDialogOpen: Boolean = false,
    ) = @androidx.compose.runtime.Composable {
        RawLogHistoryScreen(
            summaries = summaries,
            selection = selection,
            selectionMode = selectionMode,
            onOpenLog = {},
            onToggleSelect = {},
            onClearSelection = {},
            onDeleteSelected = {},
            onDeleteOlderThan = {},
            onDeleteAll = {},
            onNavigateUp = {},
            helpBody = "Raw connection logs record the full request/response for each AI call, for troubleshooting.",
            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html",
            initiallyHelpDialogOpen = initiallyHelpDialogOpen,
        )
    }

    // heightDp=800: 3 rows, each with an error indicator/model/token-cost/timestamp line -- gives
    // headroom so nothing clips.
    @Test fun populated_matrix() =
        captureMatrix("RawLogHistory", "populated", heightDp = 800, content = screen())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun populated_rtl() =
        captureRtl("RawLogHistory", "populated", heightDp = 800, content = screen())

    // Selection active: contextual bar (delete-selected action) + checkboxes, two of three rows selected.
    @Test fun selection_active_matrix() =
        captureMatrix(
            "RawLogHistory", "selection", heightDp = 800,
            content = screen(selectionMode = true, selection = setOf("log-1", "log-3")),
        )

    @Test fun empty() =
        captureGolden("RawLogHistory", "empty", EDGE_MODE, content = screen(summaries = emptyList()))

    // F30: the overflow "Help" item opens an AbInfoDialog (with a "Read more" docs link), replacing
    // classic's CommonUtils.showHelpDialog AlertDialog.
    @Test fun help_matrix() =
        captureMatrix("RawLogHistory", "help", heightDp = 800, content = screen(initiallyHelpDialogOpen = true))
}
