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


package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.ActivityState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedui.reading.ReadingToolbar
import net.bible.sharedui.reading.activity.ActivityMenuSection
import net.bible.sharedui.reading.activity.ActivityMenuState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the activity center's overflow button ring and badge and the menu activity section (#4007),
 * in light, dark and monochrome. Fixed item values, and the harness runs with animations disabled, so the
 * indeterminate ring is a static arc and every capture is deterministic. The menu section is rendered in a
 * plain column (like `ReadingOverflowMenuGoldenTest`) rather than a popup, which can hang golden capture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ActivityCenterGoldenTest {
    private val modes = listOf(GoldenMode.LIGHT, GoldenMode.DARK, GoldenMode.MONO)

    private val toolbarState = ToolbarState(
        pageTitle = "Genesis 1:1-3", documentTitle = "King James Version (KJV)", syncRunning = true,
        showBible = true, showCommentary = false, showStrongs = false, strongsMode = 1,
        searchable = true, speakable = false, speakStopped = true,
    )

    private fun item(
        id: String, state: ActivityState = ActivityState.RUNNING, progress: Int? = null, message: String? = null,
    ) = ActivityItem(
        id = id, kind = ActivityKind.DOWNLOAD, title = "Item $id", state = state, progress = progress, message = message,
        cancel = null, startedAt = 1_000L, finishedAt = if (state == ActivityState.RUNNING) null else 2_000L,
        read = state != ActivityState.FAILED,
    )

    private fun activity(items: List<ActivityItem>, unread: Int = 0) =
        ActivityMenuState(items, unread, onShowAll = {}, onMenuOpened = {})

    private fun toolbar(activity: ActivityMenuState): @Composable () -> Unit = {
        ReadingToolbar(state = toolbarState, icons = goldenToolbarIcons(), callbacks = goldenToolbarCallbacks(), activity = activity)
    }

    private fun capture(state: String, content: @Composable () -> Unit) =
        modes.forEach { captureGolden("ActivityCenter", state, it, content = content) }

    @Test
    fun ring_indeterminate() = capture("ring_indeterminate", toolbar(activity(listOf(item("a"), item("b")))))

    @Test
    fun ring_determinate() = capture("ring_determinate", toolbar(activity(listOf(item("a", progress = 60)))))

    @Test
    fun badge() = capture(
        "badge",
        toolbar(activity(listOf(
            item("a", ActivityState.FAILED, message = "Sync failed"), item("b", ActivityState.FAILED, message = "Download failed"),
        ), unread = 2)),
    )

    @Test
    fun ring_and_badge() = capture(
        "ring_and_badge",
        toolbar(activity(listOf(item("a", progress = 25), item("b", ActivityState.FAILED, message = "Sync failed")), unread = 1)),
    )

    @Test
    fun menu_section() = capture("menu_section") {
        Column(Modifier.width(280.dp)) {
            ActivityMenuSection(
                activity(listOf(
                    item("Download KJV", progress = 35),
                    item("Index ESV"),
                    item("Sync", ActivityState.FAILED, message = "Could not reach the server, check your connection and try again"),
                    item("Old", ActivityState.SUCCEEDED),
                    item("Older", ActivityState.SUCCEEDED),
                ), unread = 1),
            )
        }
    }

    @Test
    fun menu_section_single_running() = capture("menu_section_single_running") {
        Column(Modifier.width(280.dp)) { ActivityMenuSection(activity(listOf(item("Download KJV", progress = 70)))) }
    }
}
