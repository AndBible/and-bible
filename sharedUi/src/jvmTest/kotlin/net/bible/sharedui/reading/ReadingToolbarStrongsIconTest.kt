/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * Licensed under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.reading

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Issue #4041: the Strong's toolbar glyph is Greek in the New Testament and Hebrew in the Old. */
@OptIn(ExperimentalTestApi::class)
class ReadingToolbarStrongsIconTest {
    /** A 24x24 painter that counts how often the toolbar draws it. */
    private class CountingPainter : Painter() {
        var draws = 0
        override val intrinsicSize = Size(24f, 24f)
        override fun DrawScope.onDraw() { draws++ }
    }

    private val other = ColorPainter(Color.Gray)

    @Test
    fun strongsForPicksGreekInTheNewTestamentAndHebrewOtherwise() {
        val hebrew = ColorPainter(Color.Red)
        val greek = ColorPainter(Color.Blue)
        val icons = ReadingToolbarIcons(other, other, other, hebrew, other, other, other, other, other, strongsGreek = greek)
        assertSame(greek, icons.strongsFor(isNewTestament = true))
        assertSame(hebrew, icons.strongsFor(isNewTestament = false))
        val hebrewOnly = icons.copy(strongsGreek = null)
        assertSame(hebrew, hebrewOnly.strongsFor(isNewTestament = true), "no Greek glyph: Hebrew is the fallback")
    }

    @Test
    fun toolbarDrawsTheGlyphOfTheCurrentTestamentAndFollowsNavigation() = runComposeUiTest {
        val hebrew = CountingPainter()
        val greek = CountingPainter()
        var isNewTestament by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                AbTheme(darkTheme = false) {
                    ReadingToolbar(
                        ToolbarState("Matt 1", "KJV", false, true, true, true, 1, true, true, true,
                            isNewTestament = isNewTestament),
                        ReadingToolbarIcons(other, other, other, hebrew, other, other, other, other, other, strongsGreek = greek),
                        ReadingToolbarCallbacks(
                            onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
                            onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
                            onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {},
                            onSpeak = {}, onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
                        ),
                    )
                }
            }
        }
        runOnIdle {
            assertTrue(greek.draws > 0, "New Testament draws the Greek glyph")
            assertEquals(0, hebrew.draws, "New Testament must not draw the Hebrew glyph")
        }
        runOnIdle { isNewTestament = false }
        runOnIdle { assertTrue(hebrew.draws > 0, "navigating to the Old Testament draws the Hebrew glyph") }
    }
}
