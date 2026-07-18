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

import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.LabelCategory
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.components.AbColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ManageLabelsGoldenTest {

    private fun label(
        id: String,
        name: String,
        color: Int = AbColor.palette[0],
        favourite: Boolean = false,
        isUnlabeled: Boolean = false,
        hasOverride: Boolean = false,
    ) = LabelItem(
        id = id,
        name = name,
        color = color,
        favourite = favourite,
        isUnlabeled = isUnlabeled,
        isSpecial = false,
        customIcon = null,
        hasOverride = hasOverride,
    )

    /** A representative row list spanning ACTIVE/RECENT/OTHER, with a checked+primary row, a
     *  favourite, an override dot, an auto-assign row and a highlighted (StudyPad current) row.
     *  Headers are omitted for [ManageLabelsMode.STUDYPAD] (mode.hideCategories); the Unlabeled
     *  pseudo-label is appended only for modes that show it (mode.showUnassigned), exercising the
     *  Task-3 parity fix (plain icon, no auto-assign toggle). */
    private fun rows(mode: ManageLabelsMode): List<ManageLabelsRow> {
        val out = mutableListOf<ManageLabelsRow>()
        if (!mode.hideCategories) out += ManageLabelsRow.Header(LabelCategory.ACTIVE)
        out += ManageLabelsRow.Item(
            label = label("L1", "Study", favourite = true),
            checked = true, isAutoAssign = false, isPrimary = true, highlighted = false,
        )
        out += ManageLabelsRow.Item(
            label = label("L2", "Sermon notes", color = AbColor.palette[1], hasOverride = true),
            checked = false, isAutoAssign = true, isPrimary = false, highlighted = false,
        )
        if (!mode.hideCategories) out += ManageLabelsRow.Header(LabelCategory.RECENT)
        out += ManageLabelsRow.Item(
            label = label("L3", "Devotional", color = AbColor.palette[2]),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = true,
        )
        if (!mode.hideCategories) out += ManageLabelsRow.Header(LabelCategory.OTHER)
        out += ManageLabelsRow.Item(
            label = label("L4", "Prayer requests", color = AbColor.palette[3]),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        )
        if (mode.showUnassigned) {
            out += ManageLabelsRow.Item(
                label = label("unlabeled", "Unlabeled", isUnlabeled = true),
                checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
            )
        }
        return out
    }

    private fun screen(mode: ManageLabelsMode) = @androidx.compose.runtime.Composable {
        ManageLabelsScreen(
            title = "Manage labels",
            rows = rows(mode),
            mode = mode,
            searchText = "",
            nameSearchInside = false,
            onSearch = {},
            onToggleSearchInside = {},
            onRowClick = {},
            onRowLongClick = {},
            onToggleChecked = {},
            onToggleFavourite = {},
            onSetPrimary = {},
            onToggleAutoAssign = {},
            onUp = {},
            iconSlot = { _, _ -> },
            actions = {},
        )
    }

    // heightDp=800: search row + up to 3 category headers + 5 item rows -- the default viewport
    // clips the tail of the list.
    @Test fun manageLabels_assign() =
        captureMatrix("ManageLabels", "assign", heightDp = 800, content = screen(ManageLabelsMode.ASSIGN))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun manageLabels_assign_rtl() =
        captureRtl("ManageLabels", "assign", heightDp = 800, content = screen(ManageLabelsMode.ASSIGN))

    /** WORKSPACE: auto-assign circle icons, favourite hearts, override dot, and the Unlabeled row
     *  (mode.showUnassigned) rendered with a plain (non-clickable) icon -- the Task-3 parity fix. */
    @Test fun manageLabels_workspace() =
        captureGolden("ManageLabels", "workspace", EDGE_MODE, heightDp = 800, content = screen(ManageLabelsMode.WORKSPACE))

    /** STUDYPAD: no category headers (mode.hideCategories), plain list -- no checkboxes, favourite,
     *  primary or auto-assign controls. */
    @Test fun manageLabels_studypad() =
        captureGolden("ManageLabels", "studypad", EDGE_MODE, heightDp = 800, content = screen(ManageLabelsMode.STUDYPAD))
}
