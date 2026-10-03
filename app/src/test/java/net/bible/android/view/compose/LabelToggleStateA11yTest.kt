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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F98: the icon-only toggles of the Labels list and the label editor carried their state in glyph and
 * tint only, so TalkBack read the label and never "on"/"off". They now expose toggle / selection state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelToggleStateA11yTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val icon: @Composable (String?, Color) -> Unit = { _, tint ->
        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = tint)
    }

    private fun setScreen(
        autoAssign: Boolean = false,
        favourite: Boolean = false,
        primary: Boolean = false,
        mode: ManageLabelsMode = ManageLabelsMode.WORKSPACE,
    ) {
        var autoAssignState by mutableStateOf(autoAssign)
        compose.setContent {
            val row = ManageLabelsRow.Item(
                label = LabelItem(
                    id = "L1", name = "Sermon notes", color = 1, favourite = favourite, isUnlabeled = false,
                    isSpecial = false, customIcon = null, overrideStyle = null,
                ),
                checked = true, isAutoAssign = autoAssignState, isPrimary = primary, highlighted = false,
            )
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    ManageLabelsScreen(
                        title = "Manage labels",
                        rows = listOf(row),
                        mode = mode,
                        styleTagsVisible = true,
                        searchText = "",
                        searchMode = SearchMode.NAME_START,
                        onSearch = {},
                        onSetSearchMode = {},
                        filters = emptySet(),
                        onToggleFilter = {},
                        searchModeActive = false,
                        onCloseSearch = {},
                        onRowClick = {},
                        onRowLongClick = {},
                        onToggleChecked = {},
                        onToggleFavourite = {},
                        onSetPrimary = {},
                        onToggleAutoAssign = { autoAssignState = !autoAssignState },
                        onUp = {},
                        iconSlot = icon,
                        actions = {},
                        searchActions = {},
                    )
                }
            }
        }
    }

    @Test fun autoAssignStateFollowsBothWays() {
        setScreen(autoAssign = false)
        val bolt = compose.onNodeWithContentDescription(context.getString(R.string.auto_assign_labels1))
        bolt.assertIsOff()
        bolt.performClick(); bolt.assertIsOn()
        bolt.performClick(); bolt.assertIsOff()
    }

    @Test fun favouriteReportsItsState() {
        setScreen(favourite = true)
        compose.onNodeWithContentDescription(context.getString(R.string.favourite_label)).assertIsOn()
    }

    @Test fun theBookmarkIsSelectedForThePrimaryLabel() {
        setScreen(primary = true, mode = ManageLabelsMode.ASSIGN)
        compose.onNodeWithContentDescription(context.getString(R.string.primary_label)).assertIsSelected()
    }

    @Test fun theBookmarkIsNotSelectedForANonPrimaryLabel() {
        setScreen(primary = false, mode = ManageLabelsMode.ASSIGN)
        compose.onNodeWithContentDescription(context.getString(R.string.primary_label)).assertIsNotSelected()
    }

    @Test fun theTogglesReadAsSwitches() {
        setScreen()
        compose.onNodeWithContentDescription(context.getString(R.string.auto_assign_labels1))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }

    @Test fun labelEditHeartReportsItsState() {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    LabelEditScreen(
                        state = LabelEditState(
                            labelId = "L1", name = "Grace", color = 0, customIcon = null,
                            selectionStyle = BookmarkDisplayStyle.HIGHLIGHT, wholeVerseStyle = null,
                            favourite = true, isAssigning = false, thisBookmarkSelected = false,
                            thisBookmarkPrimary = false, hasWorkspaceContext = false, autoAssign = false,
                            autoAssignPrimary = false, overrideMode = OverrideMode.NONE,
                            isSpecialLabel = false, isSpeakLabel = false,
                        ),
                        onName = {}, onColor = {}, onCustomIcon = {}, onSelectionStyle = {},
                        onWholeVerseStyle = {}, onToggleFavourite = {}, onToggleSelected = {},
                        onTogglePrimary = {}, onToggleAutoAssign = {}, onToggleAutoAssignPrimary = {},
                        onOverrideMode = {}, onUp = {}, iconKeys = emptyList(), iconSlot = { _, _ -> },
                        actions = {}, discardPrompt = false, onConfirmDiscard = {}, onDismissDiscard = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.favourite_label)).assertIsOn()
    }
}
