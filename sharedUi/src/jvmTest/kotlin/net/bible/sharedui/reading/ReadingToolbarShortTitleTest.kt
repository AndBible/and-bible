/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * Licensed under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** #4200: the toolbar title falls back to the short book name when the full title does not fit. */
@OptIn(ExperimentalTestApi::class)
class ReadingToolbarShortTitleTest {
    @Test fun fittingTitlePicksFullWhenItFitsShortWhenNotAndFullWithoutAShortVariant() {
        assertEquals("long", fittingPageTitle("long", "s") { true })
        assertEquals("s", fittingPageTitle("long", "s") { it == "s" })
        assertEquals("long", fittingPageTitle("long", null) { false })
    }

    private val state = ToolbarState("2. Mooseksen kirja 1:2", "KJV", false, true, true, true, 1, true, true, true,
        pageTitleShort = "2Moos 1:2")

    @Test fun narrowWidthShowsShortTitleAndWideShowsFullAndItFollowsWidthChanges() = runComposeUiTest {
        var width by mutableStateOf(100.dp)
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false) {
                    Box(Modifier.width(width)) { FittingPageTitle(state.pageTitle, state.pageTitleShort) }
                }
            }
        }
        onNodeWithText("2Moos 1:2").assertIsDisplayed()
        width = 600.dp
        waitForIdle()
        onNodeWithText("2. Mooseksen kirja 1:2").assertIsDisplayed()
        width = 100.dp
        waitForIdle()
        onNodeWithText("2Moos 1:2").assertIsDisplayed()
    }
}
