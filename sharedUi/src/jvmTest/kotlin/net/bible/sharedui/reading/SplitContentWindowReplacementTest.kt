package net.bible.sharedui.reading

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** ticket-3520: replacing a links pane must not corrupt the split's movable composition. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class SplitContentWindowReplacementTest {
    private fun window(id: String, links: Boolean = false) = WindowSnapshot(
        id = id, state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
        isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = links,
    )

    @Test fun convertingLinksWindowFromItsOpenMenu() = runComposeUiTest {
        var windows by mutableStateOf(listOf(window("reading"), window("links", links = true)))
        var menuOpen by mutableStateOf(true)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                net.bible.sharedui.strings.LocalStrings provides net.bible.sharedui.strings.iosStrings("en"),
            ) {
                SplitContent(
                    layout = WindowLayoutState(windows, "links", null, false, true),
                    onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                    pane = { id -> BasicText(id) },
                    paneOverlay = { id ->
                        WindowPaneMenu(
                            items = if (id == "links") listOf(net.bible.sharedcore.window.WindowPaneMenuItem("convert", "Change to normal window")) else emptyList(),
                            expanded = menuOpen && id == "links",
                            onItemClick = {
                                windows = listOf(window("reading"), window("normal"))
                                menuOpen = false
                            },
                            onDismiss = { menuOpen = false },
                        )
                    },
                )
            }
        }
        onNodeWithText("Change to normal window").performClick()
        waitForIdle()
        onNodeWithText("normal").assertIsDisplayed()
        onNodeWithText("links").assertDoesNotExist()
    }

    @Test fun replacingLinksWindowKeepsTheSurvivingPaneMounted() = runComposeUiTest {
        var windows by mutableStateOf(listOf(window("reading"), window("links", links = true)))
        val identities = mutableMapOf<String, Any>()
        val disposed = mutableListOf<String>()
        setContent {
            SplitContent(
                layout = WindowLayoutState(windows, "reading", null, false, true),
                onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                pane = { id ->
                    val identity = remember { Any() }
                    DisposableEffect(id) {
                        identities[id] = identity
                        onDispose { disposed += id }
                    }
                    BasicText(id)
                },
            )
        }
        waitForIdle()
        val reading = identities.getValue("reading")
        runOnUiThread { windows = listOf(window("reading"), window("normal"), window("links", links = true)) }
        waitForIdle()
        runOnUiThread { windows = listOf(window("normal"), window("reading")) }
        waitForIdle()
        onNodeWithText("normal").assertIsDisplayed()
        onNodeWithText("links").assertDoesNotExist()
        assertSame(reading, identities.getValue("reading"))
        assertEquals(listOf("links"), disposed)
    }
}
