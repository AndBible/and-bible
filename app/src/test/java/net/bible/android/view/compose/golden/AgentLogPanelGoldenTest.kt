package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.sharedcore.ai.reading.*
import net.bible.sharedui.ai.reading.AgentLogPanel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AgentLogPanelGoldenTest {

    private val entries = listOf(
        AgentLogEntryVd("1", LogEntryKind.INFO, LogEntryStatus.COMPLETED, "Iteration 1"),
        AgentLogEntryVd("2", LogEntryKind.ACTION, LogEntryStatus.PENDING, "Reading John 3", details = "book=John"),
        AgentLogEntryVd("3", LogEntryKind.PERMISSION_REQUEST, LogEntryStatus.APPROVED, "Allow web search?"),
        AgentLogEntryVd("4", LogEntryKind.LLM_COMMENT, LogEntryStatus.COMPLETED, "Let me look that up."),
        AgentLogEntryVd("5", LogEntryKind.ERROR, LogEntryStatus.FAILED, "Rate limited", details = "429", showRawLogLink = true, cost = "$0.03"),
    )
    private val models = listOf(
        ReadingModelVd("m1", "gpt-4o", "OpenAI", isDefault = true, supported = true),
        ReadingModelVd("m2", "claude-3", "Anthropic", isDefault = false, supported = true),
    )
    private fun panel(s: AgentLogUiState, maxHeightDp: Float = 520f) = @Composable {
        AgentLogPanel(s, animateStatus = false, statusIcon = painterResource(R.drawable.icon_robot),
            applyNavBarInset = false,
            panelHeightDp = if (s.expanded) agentPanelHeight(s, collapsedDp = 48f, maxDp = maxHeightDp) else null,
            onHeightDrag = {}, onCollapsedHeightMeasured = {},
            onToggleExpanded = {}, onStop = {}, onClose = {},
            onModelSelectorClick = {}, onModelChosen = {}, onModelPickerDismiss = {}, onRawLogClick = {})
    }
    private val runningExpanded = AgentLogUiState(visible = true, expanded = true,
        snapshot = AgentLogSnapshot(running = true, entries = entries, statusText = "Reading John 3", headerCost = "$0.03", defaultModelText = "gpt-4o"))
    private val runningCollapsed = runningExpanded.copy(expanded = false)
    private val idle = AgentLogUiState(visible = true, expanded = false,
        snapshot = AgentLogSnapshot(running = false, statusText = null, defaultModelText = "gpt-4o"))

    @Test fun expanded_matrix() = captureMatrix("AgentLogPanel", "expanded", heightDp = 520) { panel(runningExpanded)() }
    @Test @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun expanded_rtl() = captureRtl("AgentLogPanel", "expanded", heightDp = 520) { panel(runningExpanded)() }
    @Test fun collapsed_matrix() = captureMatrix("AgentLogPanel", "collapsed") { panel(runningCollapsed)() }
    @Test fun idle_light() = captureGolden("AgentLogPanel", "idle", EDGE_MODE) { panel(idle)() }
    @Test fun modelPicker_light() = captureGolden("AgentLogPanel", "modelPicker", EDGE_MODE) {
        panel(runningExpanded.copy(modelPicker = models))()
    }

    /**
     * Round 12b §4: a panel dragged taller than the pre-round fixed 308dp. No other golden can reach
     * this state, and it is the one that proves the body fills the dragged height instead of
     * stopping at the old 240dp cap.
     */
    @Test fun draggedTall_light() = captureGolden("AgentLogPanel", "draggedTall", EDGE_MODE) {
        panel(runningExpanded.copy(heightDp = 460f))()
    }
}
