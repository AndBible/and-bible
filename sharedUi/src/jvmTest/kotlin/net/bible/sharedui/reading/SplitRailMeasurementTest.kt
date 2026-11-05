package net.bible.sharedui.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.theme.AbTheme

@OptIn(ExperimentalTestApi::class)
class SplitRailMeasurementTest {
    @Test fun coverageFollowsRealRailMeasurementsWithoutMovingThePanes() = runComposeUiTest {
        val width = mutableStateOf(80.dp)
        val hidden = mutableStateOf(false)
        val reverse = mutableStateOf(false)
        val dark = mutableStateOf(false)
        var covered: Set<String> = emptySet()
        val windows = listOf("left", "right").map {
            WindowSnapshot(it, WindowStateValue.VISIBLE, 1f, true, true, false, 0, false)
        }
        setContent {
            AbTheme(
                darkTheme = dark.value,
                colorMode = if (dark.value) net.bible.service.common.DisplayColorMode.MONOCHROME else net.bible.service.common.DisplayColorMode.NORMAL,
                disableAnimations = dark.value,
            ) {
                Box(Modifier.size(800.dp, 600.dp)) {
                    SplitContent(
                        layout = WindowLayoutState(windows, "left", null, reverse.value, true),
                        onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                        pane = { Box(Modifier.fillMaxSize()) },
                        // The default desktop test window is landscape, so this starts side by side.
                        railOverlay = if (hidden.value) null else { { Box(Modifier.size(width.value, 40.dp)) } },
                        onRailCoveredWindowsChanged = { covered = it },
                    )
                }
            }
        }
        waitForIdle()
        assertEquals(setOf("right"), covered)
        runOnIdle { width.value = 700.dp }
        waitForIdle()
        assertEquals(setOf("left", "right"), covered)
        runOnIdle { dark.value = true }
        waitForIdle()
        assertEquals(setOf("left", "right"), covered)
        runOnIdle { reverse.value = true }
        waitForIdle()
        assertEquals(setOf("right"), covered, "stacked panes only reserve the bottom pane")
        runOnIdle { hidden.value = true }
        waitForIdle()
        assertEquals(emptySet(), covered)
    }
}
