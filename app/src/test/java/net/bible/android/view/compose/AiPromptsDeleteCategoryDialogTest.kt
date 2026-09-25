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

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptGroupVd
import net.bible.sharedcore.ai.PromptVd
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AiPromptsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 22 (platform-dialog removal run 3): [AiPromptsScreen]'s delete-category chooser moved from
 * a hand-rolled M3 `AlertDialog` onto `AbOptionsDialog` (spec §6.2). Same 3-branch behaviour as
 * before: "Delete category and its prompts" cascades, "Move prompts to root and delete category"
 * keeps them, and "Cancel" (or dismissing) answers neither.
 *
 * The category row's overflow `IconButton` has no `contentDescription` (matches production, which
 * relies on the adjacent text for legibility), so it can't be found with `onNodeWithContentDescription`.
 * It's found instead via a click-action node whose ancestor has a sibling carrying the category's own
 * name -- true only for the row overflow, never the top bar's own (also-unlabelled) 3-dot menu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AiPromptsDeleteCategoryDialogTest {
    @get:Rule val compose = createComposeRule()

    private val category = PromptCategoryVd(id = "cat1", name = "Devotional", isBuiltIn = false, isHidden = false)
    // A non-empty group: an empty one renders AiPromptsScreen's "no prompts" placeholder instead of
    // PromptGroupsList, and the category header (with its overflow menu) never composes at all.
    private val prompt = PromptVd(
        id = "p1", name = "Morning prayer", description = "", categoryId = category.id,
        isBuiltIn = false, isReadOnly = false, isFavorite = false, isHidden = false,
    )
    private var deleteCalls = mutableListOf<Pair<String, Boolean>>()

    private fun show(cat: PromptCategoryVd = category) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                AiPromptsScreen(
                    configured = true,
                    groups = listOf(
                        PromptGroupVd(
                            category = cat, isFavorites = false,
                            prompts = listOf(prompt.copy(categoryId = cat.id)),
                        ),
                    ),
                    showHidden = false,
                    hasHiddenPrompts = false,
                    onUp = {},
                    onOpenPrompt = {},
                    onNewPrompt = {},
                    onToggleFavorite = {},
                    onSetPromptHidden = { _, _ -> },
                    onSetCategoryHidden = { _, _ -> },
                    onDeletePrompt = {},
                    onDeleteCategory = { id, cascade -> deleteCalls.add(id to cascade) },
                    onMovePrompt = { _, _ -> },
                    onMoveCategory = { _, _ -> },
                    onCreateCategory = {},
                    onRenameCategory = { _, _ -> },
                    onSetShowHidden = {},
                    onOpenConnectionSettings = {},
                    onImportCsv = {},
                    onExportCsv = {},
                    categoriesProvider = { emptyList() },
                    helpBody = "",
                    helpReadMoreUrl = "",
                )
            }
        }
    }

    /** The row overflow's `IconButton`: unlabelled, but the `CategoryHeader` `Row` it sits inside is
     *  itself click-action (its own `onToggle`) with `mergeDescendants = true`, which folds the
     *  header's `Text(category.name)` into the ROW's own semantics -- so the icon button is found as
     *  a click-action node whose ancestor carries the category's own name, true only inside that row. */
    private fun openCategoryOverflow(name: String = category.name) {
        compose.onNode(
            hasClickAction() and hasAnyAncestor(hasText(name, substring = true)),
        ).performClick()
    }

    @Test fun deleteMenuItem_opensChooserWithBothOptions() {
        show()
        openCategoryOverflow()
        compose.onNodeWithText("Delete category").performClick()
        compose.onNodeWithText("Delete category and its prompts").assertExists()
        compose.onNodeWithText("Move prompts to root and delete category").assertExists()
    }

    @Test fun cascadeOption_answersDeleteWithCascadeTrue() {
        show()
        openCategoryOverflow()
        compose.onNodeWithText("Delete category").performClick()
        compose.onNodeWithText("Delete category and its prompts").performClick()
        assertEquals(listOf("cat1" to true), deleteCalls)
    }

    @Test fun keepOption_answersDeleteWithCascadeFalse() {
        show()
        openCategoryOverflow()
        compose.onNodeWithText("Delete category").performClick()
        compose.onNodeWithText("Move prompts to root and delete category").performClick()
        assertEquals(listOf("cat1" to false), deleteCalls)
    }

    /** Fix round 1, Important finding 1: `deleteCategoryConfirm(cat.name)` goes through
     *  `AbOptionsDialog`'s `message`, which is always HTML-parsed. Before `plainTextToHtml` wrapped
     *  it, a name containing `<`, `>` or `&` would have its "tag" silently stripped/misinterpreted --
     *  if that regressed, this text would NOT be found (the literal "<b>"/"&" would be gone or the
     *  match would only see "A  C"), so the node search itself is the proof, not just an assertion
     *  tacked onto a passing render. */
    @Test fun categoryNameWithHtmlSpecialCharacters_rendersLiteralInTheConfirmMessage() {
        val cat = category.copy(name = "A <b> & C")
        show(cat)
        openCategoryOverflow(name = cat.name)
        compose.onNodeWithText("Delete category").performClick()
        compose.onNodeWithText("Delete category \"A <b> & C\"?").assertExists()
    }

    @Test fun cancelOption_answersNeither() {
        show()
        openCategoryOverflow()
        compose.onNodeWithText("Delete category").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<Pair<String, Boolean>>(), deleteCalls)
        compose.onNodeWithText("Delete category and its prompts").assertDoesNotExist()
    }
}
