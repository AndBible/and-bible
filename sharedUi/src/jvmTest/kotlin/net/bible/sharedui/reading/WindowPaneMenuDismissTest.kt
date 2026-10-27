package net.bible.sharedui.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #3899: dismissing the ☰ window menu must fade it out in place. The host clears the menu's
 * items the moment it closes (`items = if (open) items else emptyList()`), so the popup used to
 * shrink to its bare padding during the exit animation and the position provider re-placed it,
 * which reads as the menu sliding down and to the right before it disappears.
 */
@OptIn(ExperimentalTestApi::class)
class WindowPaneMenuDismissTest {
    private val items = listOf(
        WindowPaneMenuItem(id = "close", label = "Close window"),
        WindowPaneMenuItem(id = "maximise", label = "Maximise window"),
        WindowPaneMenuItem(
            id = "move", label = "Move to",
            submenu = listOf(WindowPaneMenuItem(id = "move_first", label = "First position")),
        ),
    )

    private fun closingMenu(openSubmenuFirst: Boolean, visibleLabel: String) = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.TopEnd).size(40.dp)) {
                            WindowPaneMenu(
                                // Same shape as ReadingHost's PaneWindowButtonOverlay call site.
                                items = if (open) items else emptyList(),
                                expanded = open,
                                onItemClick = {},
                                onDismiss = { open = false },
                            )
                        }
                    }
                }
            }
        }
        if (openSubmenuFirst) onNodeWithText("Move to").performClick()
        waitForIdle()
        val before = onNodeWithText(visibleLabel).getBoundsInRoot()

        mainClock.autoAdvance = false
        runOnUiThread { open = false }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()

        // Mid exit animation: the same rows, at the same place.
        assertEquals(before, onNodeWithText(visibleLabel).getBoundsInRoot())

        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithText(visibleLabel).assertDoesNotExist()
    }

    @Test fun rootLevelStaysInPlaceWhileFadingOut() =
        closingMenu(openSubmenuFirst = false, visibleLabel = "Close window")

    @Test fun openSubmenuStaysInPlaceWhileFadingOut() =
        closingMenu(openSubmenuFirst = true, visibleLabel = "First position")
}
