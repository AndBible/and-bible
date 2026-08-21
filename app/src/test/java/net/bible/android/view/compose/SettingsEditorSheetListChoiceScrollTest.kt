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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbListChoiceContent
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings editor sheets T13 fix round 1, Finding 3. Task 5's list-choice page bounds
 * `AbListChoiceContent` for a long list with an ANCESTOR `Box(Modifier.heightIn(max = 400.dp))`,
 * not through the content's own `modifier` parameter -- see that composable's KDoc. The bug this
 * guards is real and was shipped once: passing the bound through `modifier` lands it INSIDE
 * `AbListChoiceContent`'s own `Modifier.verticalScroll(rememberScrollState()).then(modifier)`,
 * where `verticalScroll`'s child is measured with `maxHeight = Infinity`. That clamps only the
 * inner `Column`'s reported size, never the scroll viewport, so the scroll RANGE collapses to 0
 * and every row past the bound becomes permanently unreachable -- not merely scroll-capped.
 *
 * `SettingsEditorSheetGoldenTest.listChoiceLong_matrix` captures the SAME Box-wrapped composition
 * and proves the 400dp bound clamps (nothing overflows past it) -- but a static screenshot has no
 * gesture, so it cannot prove the list is actually scrollable to reach the clipped rows. That
 * question does not need a `ModalBottomSheet` open to answer (the Roborazzi prohibition on
 * capturing `SettingsEditorSheet`/`ColorSettingsEditorSheet`/`TextSettingRowEditorSheet` is about
 * screenshot capture of an open sheet, and does not apply here): this is a `createComposeRule`
 * test of the page BODY alone, the same idiom `AbSearchableOptionSheetContentTest` and
 * `BookmarksRowExpandTest` already use elsewhere in this module.
 *
 * Reuses the golden test's own 20-entry `longChoices` fixture shape (`font1`..`font20`) rather
 * than inventing a shorter one -- the point is a list long enough that its tail is unreachable
 * without a working scroll range under the 400dp bound.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SettingsEditorSheetListChoiceScrollTest {
    @get:Rule val compose = createComposeRule()

    private val longChoices = (1..20).map { SettingsItem.Choice("font$it", "Font family $it") }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    content()
                }
            }
        }
    }

    @Test fun theLastRowOfALongListIsUnreachableWithoutScrollingThenReachableAfter() {
        setContent {
            // The exact composition GenericSettingsEditorSheet renders in production, and the
            // exact one SettingsEditorSheetGoldenTest.listChoiceLong_matrix captures: an ANCESTOR
            // Box bounding a plain AbListChoiceContent call with no modifier of its own.
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                AbListChoiceContent(choices = longChoices, selectedValue = "font1", onSelect = {})
            }
        }
        // Not displayed initially -- it exists in the tree (Column composes all children eagerly,
        // unlike LazyColumn), but 400dp cannot show all 20 rows at once.
        compose.onNodeWithText("Font family 20").assertIsNotDisplayed()
        // A working scroll range reaches it.
        compose.onNodeWithText("Font family 20").performScrollTo().assertIsDisplayed()
    }
}
