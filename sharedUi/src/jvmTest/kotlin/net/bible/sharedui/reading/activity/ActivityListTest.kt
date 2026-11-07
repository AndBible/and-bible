/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui.reading.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import net.bible.sharedui.reading.host.slots.LocalAgentStatusIcon
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.ActivityState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

@OptIn(ExperimentalTestApi::class)
class ActivityListTest {
    private val strings = iosStrings("en")

    private fun item(
        id: String, state: ActivityState = ActivityState.RUNNING, cancel: (() -> Unit)? = null, message: String? = null,
    ) = ActivityItem(
        id = id, kind = ActivityKind.DOWNLOAD, title = "Item $id", state = state, progress = null, message = message,
        cancel = cancel, startedAt = 1000L, finishedAt = if (state == ActivityState.RUNNING) null else 2000L,
        read = true,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.content(
        items: List<ActivityItem>,
        onDismiss: (String) -> Unit = {}, onClear: () -> Unit = {}, onOpen: (ActivityItem) -> Unit = {},
    ) = setContent {
        AbTheme { CompositionLocalProvider(LocalStrings provides strings, LocalAgentStatusIcon provides { ColorPainter(Color.Gray) }) {
            ActivityListContent(items, onDismissItem = onDismiss, onClearFinished = onClear, onOpenItem = onOpen)
        } }
    }

    @Test fun sectionsRunningAndRecent() = runComposeUiTest {
        content(listOf(item("r"), item("ok", ActivityState.SUCCEEDED), item("bad", ActivityState.FAILED, message = "boom")))
        onNodeWithText("Running").assertExists()
        onNodeWithText("Recent").assertExists()
        onAllNodesWithTag(ACTIVITY_LIST_ROW_TAG).assertCountEquals(3)
        onNodeWithText("boom").assertExists()
    }

    @Test fun onlyRunningHasNoRecentHeader() = runComposeUiTest {
        content(listOf(item("r")))
        onNodeWithText("Running").assertExists()
        assertEquals(0, onAllNodesWithText("Recent").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("Clear finished").fetchSemanticsNodes().size)
    }

    @Test fun agentRowShowsCancelWhileRunning() = runComposeUiTest {
        var cancelled = false
        content(listOf(item("agent", cancel = { cancelled = true }).copy(kind = ActivityKind.AI_AGENT)))
        onNodeWithText("Cancel").performClick()
        assertEquals(true, cancelled)
    }

    @Test fun cancelOnlyWhenSupported() = runComposeUiTest {
        var cancelled = 0
        content(listOf(item("a", cancel = { cancelled++ }), item("b")))
        onAllNodesWithText("Cancel").assertCountEquals(1)
        onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test fun finishedRowsHaveNoCancel() = runComposeUiTest {
        content(listOf(item("a", ActivityState.SUCCEEDED, cancel = {})))
        assertEquals(0, onAllNodesWithText("Cancel").fetchSemanticsNodes().size)
    }

    @Test fun dismissCallsCenter() = runComposeUiTest {
        val dismissed = mutableListOf<String>()
        content(listOf(item("r"), item("ok", ActivityState.SUCCEEDED)), onDismiss = { dismissed += it })
        onAllNodesWithText("Dismiss").assertCountEquals(1)
        onNodeWithText("Dismiss").performClick()
        assertEquals(listOf("ok"), dismissed)
    }

    @Test fun clearFinishedKeepsRunning() = runComposeUiTest {
        var items by androidx.compose.runtime.mutableStateOf(listOf(item("r"), item("ok", ActivityState.SUCCEEDED), item("bad", ActivityState.FAILED)))
        setContent {
            AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
                ActivityListContent(
                    items, onDismissItem = {}, onClearFinished = { items = items.filter { it.state == ActivityState.RUNNING } },
                    onOpenItem = {},
                )
            } }
        }
        onNodeWithText("Clear finished").performClick()
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_ROW_TAG).assertCountEquals(1)
        onNodeWithText("Item r").assertExists()
    }

    @Test fun rowTapOpensItem() = runComposeUiTest {
        var opened: String? = null
        content(listOf(item("a")), onOpen = { opened = it.id })
        onNodeWithText("Item a").performClick()
        assertEquals("a", opened)
    }

    @Test fun emptyStateText() = runComposeUiTest {
        content(emptyList())
        onNodeWithText("Nothing running").assertExists()
        onAllNodesWithTag(ACTIVITY_LIST_ROW_TAG).assertCountEquals(0)
    }

    @Test fun cancelledItemsAreNotListed() = runComposeUiTest {
        content(listOf(item("x", ActivityState.CANCELLED)))
        onNodeWithText("Nothing running").assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.host(widthDp: Int, visible: Boolean = true) = setContent {
        AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
            Box(Modifier.width(widthDp.dp)) {
                ActivityListHost(visible, listOf(item("a")), {}, {}, {}, {})
            }
        } }
    }

    @Test fun compactUsesSheetWideUsesPopover() {
        runComposeUiTest {
            host(400)
            onNodeWithTag(ACTIVITY_LIST_SHEET_TAG).assertExists()
            assertEquals(0, onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG).fetchSemanticsNodes().size)
        }
        runComposeUiTest {
            host(800)
            onNodeWithTag(ACTIVITY_LIST_POPOVER_TAG).assertExists()
            assertEquals(0, onAllNodesWithTag(ACTIVITY_LIST_SHEET_TAG).fetchSemanticsNodes().size)
        }
    }

    @Test fun hiddenHostRendersNothing() = runComposeUiTest {
        host(400, visible = false)
        assertEquals(0, onAllNodesWithText("Background activity").fetchSemanticsNodes().size)
    }
}
