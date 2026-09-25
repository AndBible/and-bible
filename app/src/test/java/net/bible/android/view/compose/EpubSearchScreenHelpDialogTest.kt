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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.LinkAnnotation
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbHtmlText
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
 * Task 15, Step 3: the EPUB search form's FTS5 query-syntax help moved off the host
 * (`NavHostComposeActivity.showEpubSearchHelp`) into `EpubSearchFormController.helpOpen` and is now
 * rendered by [EpubSearchScreen] itself -- this proves the screen shows the dialog only when open,
 * answers OK by dismissing, and that the inline wiki link is wired for `AbLinkRouting`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class EpubSearchScreenHelpDialogTest {
    @get:Rule val compose = createComposeRule()

    private var dismissCalls = 0

    private fun show(helpOpen: Boolean) = compose.setContent {
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
                    onOpenLink = {},
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

    /** Same Dialog-window limitation as `AppSettingsScreenDialogTest`'s link test -- see that test's
     *  kdoc (and `AppDialogHostTest.linkAnnotationIsPresentInTheRenderedBody`'s, the original probe)
     *  for why a real click on a link inside an `AlertDialog` is not driveable here. */
    @Test fun helpOpen_bodyCarriesTheFts5LinkAnnotation() {
        show(helpOpen = true)
        val node = compose.onNodeWithText("FTS5 Full-text Query Syntax", substring = true).fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].single()
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("https://www.sqlite.org/fts5.html#full_text_query_syntax"), links)
    }

    @Test fun onOpenLink_wrapperForwardsClicks() {
        val forwarded = mutableListOf<String>()
        val wrapper = object : UriHandler {
            override fun openUri(uri: String) { forwarded += uri }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides wrapper) {
                AbHtmlText("<a href=\"https://www.sqlite.org/fts5.html#full_text_query_syntax\">probe</a>")
            }
        }
        compose.onNodeWithText("probe").performClick()
        assertEquals(listOf("https://www.sqlite.org/fts5.html#full_text_query_syntax"), forwarded)
    }
}
