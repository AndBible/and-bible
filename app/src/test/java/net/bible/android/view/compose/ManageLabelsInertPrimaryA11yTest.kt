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

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression guard for the fix-review-2 finding on item 8: `Modifier.semantics { disabled() }`
 * on the inert 🔖 slot is a NON-merging node by default, and the row is a merge scope
 * (`combinedClickable`'s `AbstractClickableNode.shouldMergeDescendantSemantics` returns `true`
 * unconditionally) — so without `mergeDescendants = true` on the slot, `Disabled` (no custom merge
 * policy; default `parentValue ?: childValue`) bubbled straight up and made the WHOLE ROW announce
 * as disabled while staying fully clickable. This test renders the real row through
 * [ManageLabelsScreen] (not a hand-built semantics tree) and asserts both halves of the fix at
 * once: the row itself must still read as enabled, and the indicator, queried on its own, must
 * still read as disabled-with-a-description.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// w600dp: since issue #4153 a narrow (<480dp) ASSIGN list draws no primary column at all.
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "w600dp")
class ManageLabelsInertPrimaryA11yTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val primaryLabel: String get() = context.getString(R.string.primary_label)

    private val icon: @Composable (String?, Color) -> Unit = { _, tint ->
        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = tint)
    }

    /** ASSIGN mode: `mode.primaryShown` is true, and a single unchecked, non-primary row puts the
     *  indicator into its inert (not the live IconButton) branch -- the exact case the finding is
     *  about. */
    private fun setScreen(onRowClick: (String) -> Unit = {}) {
        val row = ManageLabelsRow.Item(
            label = LabelItem(
                id = "L1", name = "Sermon notes", color = 1, favourite = false, isUnlabeled = false,
                isSpecial = false, customIcon = null, overrideStyle = null,
            ),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        )
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    ManageLabelsScreen(
                        title = "Manage labels",
                        rows = listOf(row),
                        mode = ManageLabelsMode.ASSIGN,
                        styleTagsVisible = true,
                        searchText = "",
                        searchMode = SearchMode.NAME_START,
                        onSearch = {},
                        onSetSearchMode = {},
                        filters = emptySet(),
                        onToggleFilter = {},
                        searchModeActive = false,
                        onCloseSearch = {},
                        onRowClick = onRowClick,
                        onRowLongClick = {},
                        onToggleChecked = {},
                        onToggleFavourite = {},
                        onSetPrimary = {},
                        onToggleAutoAssign = {},
                        onUp = {},
                        iconSlot = icon,
                        actions = {},
                        searchActions = {},
                    )
                }
            }
        }
    }

    @Test
    fun the_row_itself_still_reads_as_enabled() {
        setScreen()
        // Found by the label's own name, which the row merges into ITS node -- this is the row-level
        // merged semantics, the thing that must NOT carry the indicator's Disabled.
        compose.onNodeWithText("Sermon notes").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w320dp")
    fun narrow_assign_row_announces_its_name_and_opens_the_editor() {
        var editedLabel: String? = null
        setScreen(onRowClick = { editedLabel = it })
        // Query the merged row by its text, not the independent checkbox with the same description.
        compose.onNodeWithText("Sermon notes")
            .assertIsEnabled()
            .assertHasClickAction()
            .assertContentDescriptionContains("Sermon notes")
            .performClick()
        compose.runOnIdle { assertEquals("L1", editedLabel) }
    }

    @Test
    fun the_indicator_itself_still_reads_as_disabled_with_its_description() {
        setScreen()
        // Its own merge boundary now, so it surfaces as a node in its own right rather than being
        // absorbed into the row -- and it must still say what it is AND that it's off-limits.
        compose.onNodeWithContentDescription(primaryLabel).assertIsNotEnabled()
    }
}
