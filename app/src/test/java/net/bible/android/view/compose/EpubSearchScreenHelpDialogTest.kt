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

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.search.EpubSearchScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Clicks the FIRST [androidx.compose.ui.text.LinkAnnotation] inside this node's text, at the link's
 * own glyph position rather than the node's center -- unlike `AppDialogHostTest`'s link fixtures
 * (where the link IS the whole body, so the default center-click lands on it), the FTS5 help link
 * sits mid-sentence in fixed production text this test cannot simplify.
 */
private fun SemanticsNodeInteraction.performLinkClick() {
    val node = fetchSemanticsNode()
    val text = node.config[SemanticsProperties.Text].single()
    val link = text.getLinkAnnotations(0, text.length).first()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val box = layouts.single().getBoundingBox(link.start)
    performTouchInput { click(box.center) }
}

/**
 * Task 15, Step 3: the EPUB search form's FTS5 query-syntax help moved off the host
 * (`NavHostComposeActivity.showEpubSearchHelp`) into `EpubSearchFormController.helpOpen` and is now
 * rendered by [EpubSearchScreen] itself -- this proves the screen shows the dialog only when open,
 * answers OK by dismissing, and (C1) that the inline wiki link is wired through
 * [askBeforeOpeningLink]/[onOpenExternal].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class EpubSearchScreenHelpDialogTest {
    @get:Rule val compose = createComposeRule()

    private var dismissCalls = 0
    private val opened = mutableListOf<String>()

    private fun show(helpOpen: Boolean, askBeforeOpeningLink: Boolean = false) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                EpubSearchScreen(
                    title = "Search",
                    query = "",
                    mode = EpubSearchMode.ALL_WORDS,
                    onQueryChange = {},
                    onMode = {},
                    onSubmit = {},
                    onHelp = {},
                    onNavigateUp = {},
                    helpOpen = helpOpen,
                    onDismissHelp = { dismissCalls++ },
                    askBeforeOpeningLink = askBeforeOpeningLink,
                    onOpenExternal = { opened += it },
                )
            }
        }
    }

    @Test fun noDialogWhenClosed() {
        show(helpOpen = false)
        compose.onNodeWithText("FTS5 Full-text Query Syntax", substring = true).assertDoesNotExist()
    }

    @Test fun helpOpen_showsTitleAndBody_okAnswersDismiss() {
        show(helpOpen = true)
        compose.onNodeWithText("Search").assertExists()
        compose.onNodeWithText("Choose how the words are matched", substring = true).assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissCalls)
    }

    /** C1: a real click on the FTS5 help link, driven through the real `AbLinkRouting`-wrapped
     *  dialog, reaches [onOpenExternal] directly (see `AppDialogHostTest
     *  .linkTapInsideTheDialogWindowReachesTheHost`'s kdoc for why this is now real coverage rather
     *  than the "Dialog-window limitation" this replaces). */
    @Test fun helpOpen_linkTapReachesOnOpenExternal() {
        show(helpOpen = true)
        compose.onNodeWithText("FTS5 Full-text Query Syntax", substring = true).performLinkClick()
        assertEquals(listOf("https://www.sqlite.org/fts5.html#full_text_query_syntax"), opened)
    }

    /** C1: with [askBeforeOpeningLink], the same tap asks first -- OK opens it exactly once. The
     *  help dialog's own confirm button is ALSO labelled "OK" (`strings.okay`), so the question's
     *  button is picked out by taking the LAST match -- see `AppSettingsScreenDialogTest`'s equivalent test. */
    @Test fun helpOpen_askBeforeOpeningLink_asksThenOpensOnce() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        show(helpOpen = true, askBeforeOpeningLink = true)
        compose.onNodeWithText("FTS5 Full-text Query Syntax", substring = true).performLinkClick()
        compose.onNodeWithText(context.getString(R.string.external_link)).assertExists()
        assertEquals(emptyList<String>(), opened)

        val okNodes = compose.onAllNodesWithText(context.getString(R.string.okay))
        okNodes[okNodes.fetchSemanticsNodes().size - 1].performClick()
        assertEquals(listOf("https://www.sqlite.org/fts5.html#full_text_query_syntax"), opened)
        assertEquals(0, dismissCalls) // the help dialog itself is untouched by the link question
    }
}
