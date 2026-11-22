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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.android.activity.R
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #4201 follow-up: the "main label" switch of "This bookmark" is shown only where it has an effect. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelEditPrimarySwitchTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val switchText get() = context.getString(R.string.primary_label)

    private fun show(applies: Boolean) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                LabelEditScreen(
                    state = LabelEditState(
                        labelId = "L1", name = "Grace", color = 0, customIcon = null,
                        selectionStyle = BookmarkDisplayStyle.HIGHLIGHT, wholeVerseStyle = null,
                        favourite = false, isAssigning = true, thisBookmarkSelected = true,
                        thisBookmarkPrimary = true, hasWorkspaceContext = false, autoAssign = false,
                        autoAssignPrimary = false, overrideMode = OverrideMode.NONE,
                        isSpecialLabel = false, isSpeakLabel = false,
                        thisBookmarkPrimaryApplies = applies,
                    ),
                    onName = {}, onColor = {}, onCustomIcon = {}, onSelectionStyle = {},
                    onWholeVerseStyle = {}, onToggleFavourite = {}, onToggleSelected = {},
                    onTogglePrimary = {}, onToggleAutoAssign = {}, onToggleAutoAssignPrimary = {},
                    onOverrideMode = {}, onUp = {}, iconKeys = emptyList(), iconSlot = { _, _ -> },
                    actions = {}, initialThisBookmarkExpanded = true,
                )
            }
        }
    }

    @Test fun theSwitchIsShownWhereItApplies() {
        show(applies = true)
        assertEquals(1, compose.onAllNodesWithText(switchText).fetchSemanticsNodes().size)
    }

    @Test fun theSwitchAndItsHeaderMarkAreHiddenWhereItDoesNotApply() {
        show(applies = false)
        assertEquals(0, compose.onAllNodesWithText(switchText).fetchSemanticsNodes().size)
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription(switchText).fetchSemanticsNodes().size,
        )
    }
}
