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
package net.bible.android.view.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chooser of an own bookmark's labels (ASSIGN): an add-on collection label cannot be put on an own bookmark
 * (BookmarkControl refuses it), so its checkbox must be disabled, never a live control whose tick is silently dropped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "w600dp")
class ManageLabelsAddonAssignTest {
    @get:Rule val compose = createComposeRule()

    private fun label(id: String, name: String, module: String? = null) = ManageLabelsRow.Item(
        label = LabelItem(
            id = id, name = name, color = 1, favourite = false, isUnlabeled = false, isSpecial = false,
            customIcon = null, overrideStyle = null, addonModuleName = module,
        ),
        checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
    )

    private fun checkboxOf(name: String) = hasContentDescription(name) and isToggleable()

    private fun setScreen(mode: ManageLabelsMode, toggled: MutableList<String>) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    ManageLabelsScreen(
                        title = "Manage labels", rows = listOf(label("A1", "Comfort", "Manna"), label("L1", "Sermon notes")),
                        mode = mode, styleTagsVisible = true, searchText = "", searchMode = SearchMode.NAME_START,
                        onSearch = {}, onSetSearchMode = {}, filters = emptySet(), onToggleFilter = {},
                        searchModeActive = false, onCloseSearch = {}, onRowClick = {}, onRowLongClick = {},
                        onToggleChecked = { toggled += it }, onToggleFavourite = {}, onSetPrimary = {},
                        onToggleAutoAssign = {}, onUp = {},
                        iconSlot = { _, tint -> Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = tint) },
                        actions = {}, searchActions = {},
                    )
                }
            }
        }
    }

    @Test fun anAddonLabelCannotBeTickedWhenAssigning() {
        val toggled = mutableListOf<String>()
        setScreen(ManageLabelsMode.ASSIGN, toggled)
        // The module suffix is shown, and the checkbox (it carries the label's own name) is off-limits.
        compose.onNodeWithText("Comfort · Manna").assertExists()
        compose.onNode(checkboxOf("Comfort")).assertIsNotEnabled().performClick()
        compose.onNode(checkboxOf("Sermon notes")).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("L1"), toggled) }
    }
}
