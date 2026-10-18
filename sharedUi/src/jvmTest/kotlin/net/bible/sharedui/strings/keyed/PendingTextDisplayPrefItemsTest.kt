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

package net.bible.sharedui.strings.keyed

import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.MarginSize
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.service.sword.ContentSource
import net.bible.sharedlogic.settings.TextDisplayArray
import net.bible.sharedlogic.settings.TextDisplayString
import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The C7 pending text display prefs: in-memory handles with real defaults, strings through the generated tables. */
class PendingTextDisplayPrefItemsTest {
    private val source = object : ContentSource {
        override fun openSource(uri: String): Source? = null
        override fun displayName(uri: String): String? = null
        override fun mimeType(uri: String): String? = null
    }
    private val bundle = SettingsBundle()
    private fun items(images: Map<String, String> = emptyMap()) = PendingTextDisplayPrefItems(
        strings = { StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) }) },
        contentSource = { source },
        backgroundImages = { images },
    )

    // The three numeric titles use %d, %1.1f and %1$d/%2$d/%3$d; the English templates in strings.xml are
    // "%d pt", "Line spacing %1.1fx" and "Margin size (%1$d/%2$d/%3$d mm)".
    @Test fun fontSizeFormatsAnInt() = assertEquals("16 pt", items().string(TextDisplayString.FONT_SIZE_PT, 16))

    @Test fun lineSpacingFormatsOneDecimal() =
        assertEquals("Line spacing 1.6x", items().string(TextDisplayString.LINE_SPACING_PT, 1.6f))

    @Test fun marginTitleFillsThePositionalArguments() =
        assertEquals("Margin size (3/4/170 mm)", items().string(TextDisplayString.MARGIN_SIZE_MM_TITLE, 3, 4, 170))

    @Test fun strongsEntriesAreTheEnglishLabelsInOrder() =
        assertEquals(listOf("Hidden Links", "Links", "Text and Links"), items().array(TextDisplayArray.STRONGS_MODE_ENTRIES))

    @Test fun everySettingTypeHasAConcreteDefault() {
        val items = items()
        for (type in Types.entries) assertNotNull(items.item(bundle, type).value, "default of $type")
    }

    @Test fun defaultsHaveTheValueTypesTheServiceCasts() {
        val items = items()
        assertEquals(16, items.item(bundle, Types.FONTSIZE).value)
        assertEquals("sans-serif", items.item(bundle, Types.FONTFAMILY).value)
        assertEquals(MarginSize(3, 3, 170), items.item(bundle, Types.MARGINSIZE).value)
        assertEquals(false, items.item(bundle, Types.SHOW_READING_PROGRESS).value)
        assertEquals(true, items.item(bundle, Types.JUSTIFY).value)
        assertEquals(emptyList<Any>(), items.item(bundle, Types.BOOKMARKS_HIDELABELS).value)
    }

    @Test fun aWrittenValueSurvivesANewHandleAndSetNonSpecificRestoresTheDefault() {
        val items = items()
        items.item(bundle, Types.FONTSIZE).value = 22
        assertEquals(22, items.item(bundle, Types.FONTSIZE).value)
        assertEquals(0, items.item(bundle, Types.TOPMARGIN).value, "other types are untouched")
        items.item(bundle, Types.FONTSIZE).setNonSpecific()
        assertEquals(16, items.item(bundle, Types.FONTSIZE).value)
    }

    @Test fun handlesAreEnabledVisibleAndHaveNoIcon() {
        val h = items().item(bundle, Types.JUSTIFY)
        assertTrue(h.enabled && h.visible)
        assertEquals(null, h.iconKey)
    }

    @Test fun passThroughMembers() {
        val items = items(mapOf("a" to "Image A"))
        assertSame(source, items.contentSource())
        assertEquals(mapOf("a" to "Image A"), items.backgroundImageNames())
        assertEquals(listOf("sans-serif", "serif", "monospace"), items.fonts.map { it.value })
        items.displaySettingChanged(Types.COLORS)
        items.displaySettingChanged(Types.FONTSIZE)
        assertEquals(listOf(Types.COLORS, Types.FONTSIZE), items.changedTypes)
    }
}
