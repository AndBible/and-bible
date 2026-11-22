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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelCategory
import net.bible.sharedcore.bookmark.LabelFilter
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.bookmark.LabelAssignSheetActions
import net.bible.sharedui.bookmark.LabelAssignSheetFrame
import net.bible.sharedui.bookmark.LabelAssignSheetState
import net.bible.sharedui.components.AbColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The label-assign sheet (issue #4201), captured through [LabelAssignSheetFrame]: the sheet's header,
 * search field, filter chips, the full screen's own label list and the "Edit labels" footer, without
 * the modal window (an open `ModalBottomSheet` must never be captured).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelAssignSheetGoldenTest {
    private fun label(
        id: String,
        name: String,
        color: Int,
        favourite: Boolean = false,
        selectionStyle: BookmarkDisplayStyle = BookmarkDisplayStyle.HIGHLIGHT,
    ) = LabelItem(
        id = id, name = name, color = color, favourite = favourite, isUnlabeled = false, isSpecial = false,
        customIcon = null, selectionStyle = selectionStyle,
    )

    private fun item(label: LabelItem, checked: Boolean = false, primary: Boolean = false) =
        ManageLabelsRow.Item(label, checked = checked, isAutoAssign = false, isPrimary = primary, highlighted = false)

    private fun rows(): List<ManageLabelsRow> = listOf(
        ManageLabelsRow.Header(LabelCategory.ACTIVE),
        item(label("L1", "Study", AbColor.palette[0], favourite = true), checked = true, primary = true),
        item(label("L2", "Sermon notes", AbColor.palette[1], selectionStyle = BookmarkDisplayStyle.UNDERLINE), checked = true),
        ManageLabelsRow.Header(LabelCategory.RECENT),
        item(label("L3", "Devotional", AbColor.palette[2])),
        ManageLabelsRow.Header(LabelCategory.OTHER),
        item(label("L4", "Prayer requests", AbColor.palette[3], selectionStyle = BookmarkDisplayStyle.MARKER)),
        item(label("L5", "Promises", AbColor.palette[4], favourite = true)),
    )

    private fun sheet(
        rows: List<ManageLabelsRow> = rows(),
        searchText: String = "",
        filters: Set<LabelFilter> = emptySet(),
        searchMode: SearchMode = SearchMode.NAME_START,
    ): @Composable () -> Unit = {
        LabelAssignSheetFrame(
            state = LabelAssignSheetState(
                title = "Assign labels",
                rows = rows,
                styleTagsVisible = true,
                searchText = searchText,
                searchMode = searchMode,
                filters = filters,
            ),
            actions = LabelAssignSheetActions(
                onSearch = {}, onSetSearchMode = {}, onToggleFilter = {}, onToggleChecked = {},
                onToggleFavourite = {}, onSetPrimary = {}, onToggleAutoAssign = {}, onEditLabels = {},
                newLabelAction = { IconButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = null) } },
                iconSlot = manageLabelIcon,
            ),
            onClose = {},
        )
    }

    @Test fun labelAssignSheet_default() =
        captureMatrix("LabelAssignSheet", "default", heightDp = 800, content = sheet())

    /** A query, the favourites filter and "Name (contains)" on: the chips' selected state, and the ticked
     *  label that stays listed although the query excludes it. */
    @Test fun labelAssignSheet_filtered() =
        captureMatrix(
            "LabelAssignSheet", "filtered", heightDp = 800,
            content = sheet(
                rows = listOf(rows()[1], rows()[7]),
                searchText = "pro",
                filters = setOf(LabelFilter.FAVOURITE),
                searchMode = SearchMode.NAME_CONTAINS,
            ),
        )
}
