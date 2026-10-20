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
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities.MarginSize
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedlogic.settings.TextDisplayArray
import net.bible.sharedlogic.settings.TextDisplayString
import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The real text display prefs over bundles without a window (so no window repository is needed): value, inheritance,
 * persistence of recently used types, titles and the flags the pane menu and the settings screen read.
 */
class SharedTextDisplayPrefItemsTest {
    private val source = object : ContentSource {
        override fun openSource(uri: String): Source? = null
        override fun displayName(uri: String): String? = null
        override fun mimeType(uri: String): String? = null
    }

    private class MapSettings : AppSettings {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = (m[key] as String?) ?: default
        override fun getLong(key: String, default: Long) = (m[key] as Long?) ?: default
        override fun getInt(key: String, default: Int) = (m[key] as Int?) ?: default
        override fun getBoolean(key: String, default: Boolean) = (m[key] as Boolean?) ?: default
        override fun getDouble(key: String, default: Double) = (m[key] as Double?) ?: default
        override fun getFloat(key: String, default: Float) = (m[key] as Float?) ?: default
        override fun setString(key: String, value: String?) { m[key] = value }
        override fun setLong(key: String, value: Long?) { m[key] = value }
        override fun setInt(key: String, value: Int?) { m[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
        override fun setDouble(key: String, value: Double?) { m[key] = value }
        override fun setFloat(key: String, value: Float?) { m[key] = value }
    }

    private val appSettings = MapSettings()
    private val bundle = SettingsBundle(level = SettingsLevel.WORKSPACE)

    private fun items(images: Map<String, String> = emptyMap(), settings: AppSettings = appSettings) = SharedTextDisplayPrefItems(
        strings = { StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) }) },
        contentSource = { source },
        windowControl = { error("these bundles name no window and the types tested need no active page") },
        documentControl = { error("no document control needed") },
        settings = { settings },
        backgroundImages = { images },
    )

    // The three numeric titles use %d, %1.1f and %1$d/%2$d/%3$d; the English templates in strings.xml are
    // "%d pt", "Line spacing %1.1fx" and "Margin size (%1$d/%2$d/%3$d mm)".
    @Test fun fontSizeFormatsAnInt() = assertEquals("16 pt", items().string(TextDisplayString.FONT_SIZE_PT, 16))

    @Test fun lineSpacingFormatsOneDecimal() =
        assertEquals("Line spacing 1.6x", items().string(TextDisplayString.LINE_SPACING_PT, 1.6f))

    @Test fun strongsEntriesAreTheEnglishLabelsInOrder() =
        assertEquals(listOf("Hidden Links", "Links", "Text and Links"), items().array(TextDisplayArray.STRONGS_MODE_ENTRIES))

    // --- values

    private val windowFree = Types.entries - setOf(Types.REDLETTERS, Types.MYNOTES, Types.TOPMARGIN)

    @Test fun everyWindowFreeTypeHasAConcreteDefaultAndATitle() {
        val items = items()
        for (type in windowFree) {
            val item = items.item(bundle, type)
            assertNotNull(item.value, "default of $type")
            assertTrue(item.title!!.isNotBlank(), "title of $type")
            if (type != Types.STRONGS) assertNotNull(item.iconKey, "icon of $type") // STRONGS asks the document for its testament
        }
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

    @Test fun aWorkspaceWriteLivesInTheBundleAndIsInheritedUntilSet() {
        val items = items()
        assertTrue(items.item(bundle, Types.FONTSIZE).inherited)
        items.item(bundle, Types.FONTSIZE).value = 22
        assertEquals(22, bundle.workspaceSettings.fontSize)
        assertEquals(22, items.item(bundle, Types.FONTSIZE).value)
        assertFalse(items.item(bundle, Types.FONTSIZE).inherited)
        items.item(bundle, Types.FONTSIZE).setNonSpecific()
        assertNull(bundle.workspaceSettings.fontSize)
        assertEquals(16, items.item(bundle, Types.FONTSIZE).value)
    }

    @Test fun writingTheParentsValueClearsTheOverride() {
        val items = items()
        val global = TextDisplaySettings().also { it.fontSize = 20 }
        val b = SettingsBundle(level = SettingsLevel.WORKSPACE, globalSettings = global)
        items.item(b, Types.FONTSIZE).value = 24
        assertEquals(24, b.workspaceSettings.fontSize)
        items.item(b, Types.FONTSIZE).value = 20 // what the parent gives
        assertNull(b.workspaceSettings.fontSize)
    }

    @Test fun aWindowLevelWriteInheritsFromTheWorkspaceThenGlobalThenDefault() {
        val items = items()
        val workspace = TextDisplaySettings().also { it.fontSize = 18 }
        val pm = TextDisplaySettings()
        val b = SettingsBundle(level = SettingsLevel.WINDOW, workspaceSettings = workspace, pageManagerSettings = pm)
        assertEquals(18, items.item(b, Types.FONTSIZE).value)
        assertTrue(items.item(b, Types.FONTSIZE).inherited)
        items.item(b, Types.FONTSIZE).value = 12
        assertEquals(12, pm.fontSize)
        items.item(b, Types.FONTSIZE).value = 18
        assertNull(pm.fontSize, "equal to the workspace value: the window override goes away")
    }

    @Test fun globalResetWritesTheDefaultBack() {
        val global = TextDisplaySettings().also { it.fontSize = 30 }
        val b = SettingsBundle(level = SettingsLevel.GLOBAL, globalSettings = global)
        items().item(b, Types.FONTSIZE).setNonSpecific()
        assertEquals(16, global.fontSize)
    }

    // --- recently used (persisted under Android's key)

    @Test fun aWriteRecordsTheTypeAsRecentlyUsedAndItPersists() {
        val items = items()
        items.item(bundle, Types.FONTSIZE).value = 22
        items.item(bundle, Types.JUSTIFY).value = false
        items.displaySettingChanged(Types.COLORS)
        assertEquals(listOf(Types.COLORS, Types.FONTSIZE, Types.JUSTIFY), items.recent.sorted)
        // a second instance over the same settings sees them: the list is stored, not held
        assertEquals(listOf(Types.COLORS, Types.FONTSIZE, Types.JUSTIFY), items().recent.sorted)
        assertNotNull(appSettings.getString("lastDisplaySettings"))
    }

    @Test fun recentKeepsTheFiveNewestWithoutDuplicates() {
        val items = items()
        listOf(Types.FONTSIZE, Types.JUSTIFY, Types.XREFS, Types.FOOTNOTES, Types.STRONGS, Types.MORPH, Types.XREFS).forEach(items::displaySettingChanged)
        // newest first: XREFS, MORPH, STRONGS, FOOTNOTES, JUSTIFY (FONTSIZE fell off); the menus sort by name
        assertEquals(listOf(Types.FOOTNOTES, Types.JUSTIFY, Types.MORPH, Types.STRONGS, Types.XREFS), items.recent.sorted)
    }

    @Test fun anUnreadableStoredListIsEmptyNotACrash() {
        appSettings.setString("lastDisplaySettings", "not json")
        assertEquals(emptyList(), items().recent.sorted)
    }

    // --- flags and titles

    @Test fun booleanAndDialogFlagsFollowTheValueType() {
        val items = items()
        assertTrue(items.item(bundle, Types.JUSTIFY).isBoolean)
        assertFalse(items.item(bundle, Types.JUSTIFY).opensDialog)
        assertFalse(items.item(bundle, Types.FONTSIZE).isBoolean)
        assertTrue(items.item(bundle, Types.FONTSIZE).opensDialog)
        assertTrue(items.item(bundle, Types.MARGINSIZE).opensDialog)
    }

    @Test fun dynamicTitlesShowTheCurrentValue() {
        val items = items()
        assertEquals("Font size (16 pt)", items.item(bundle, Types.FONTSIZE).title)
        items.item(bundle, Types.FONTSIZE).value = 22
        assertEquals("Font size (22 pt)", items.item(bundle, Types.FONTSIZE).title)
        assertEquals("Font family (sans-serif)", items.item(bundle, Types.FONTFAMILY).title)
        assertEquals("Margin size (3/3/170 mm)", items.item(bundle, Types.MARGINSIZE).title)
        assertTrue(items.item(bundle, Types.MARGINSIZE).summary!!.contains("Tip:"))
        assertNull(items.item(bundle, Types.FONTSIZE).summary)
    }

    @Test fun staticTitlesAreTheSettingLabels() {
        val labels = KeyedSettingLabels(StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) }))
        val items = items()
        for (type in windowFree - setOf(Types.FONTSIZE, Types.FONTFAMILY, Types.MARGINSIZE, Types.LINE_SPACING)) {
            assertEquals(labels.label(bundle, type), items.item(bundle, type).title, "title of $type")
        }
    }

    @Test fun dependentItemsAreDisabledAndReadAsOffUntilTheirParentIsOn() {
        val items = items()
        // footnotes default to on, so the inline variant is enabled; switching footnotes off disables it and it reads false
        assertTrue(items.item(bundle, Types.FOOTNOTES_INLINE).enabled)
        items.item(bundle, Types.FOOTNOTES_INLINE).value = true
        assertEquals(true, items.item(bundle, Types.FOOTNOTES_INLINE).value)
        items.item(bundle, Types.FOOTNOTES).value = false
        assertFalse(items.item(bundle, Types.FOOTNOTES_INLINE).enabled)
        assertEquals(false, items.item(bundle, Types.FOOTNOTES_INLINE).value, "whatever is stored")
        // expanded cross references follow the cross references setting
        assertTrue(items.item(bundle, Types.EXPAND_XREFS).enabled)
        items.item(bundle, Types.XREFS).value = false
        assertFalse(items.item(bundle, Types.EXPAND_XREFS).enabled)
    }

    @Test fun windowFreeStrongsAndMorphologyAreEnabled() {
        val items = items()
        assertTrue(items.item(bundle, Types.STRONGS).enabled)
        assertTrue(items.item(bundle, Types.MORPH).enabled)
        assertTrue(items.item(bundle, Types.NON_STRONGS_WORD_ITALIC).enabled)
    }

    @Test fun einkOnlyItemsAreVisibleInEinkMode() {
        val items = items()
        for (t in listOf(Types.PAGE_BUTTONS, Types.SCROLL_HELPER_LINES, Types.SCROLL_HELPER_LINE_STYLE)) {
            assertFalse(items.item(bundle, t).visible, "$t hidden by default")
        }
        appSettings.setBoolean("eink_mode", true)
        for (t in listOf(Types.PAGE_BUTTONS, Types.SCROLL_HELPER_LINES, Types.SCROLL_HELPER_LINE_STYLE)) {
            assertTrue(items.item(bundle, t).visible, "$t shown in e-ink mode")
        }
        assertTrue(items.item(bundle, Types.JUSTIFY).visible)
    }

    @Test fun passThroughMembers() {
        val items = items(mapOf("a" to "Image A"))
        assertSame(source, items.contentSource())
        assertEquals(mapOf("a" to "Image A"), items.backgroundImageNames())
        assertEquals(listOf("sans-serif", "serif", "monospace"), items.fonts.map { it.value })
        // The labels are Android's picker labels (FontDefinition.name), not hardcoded English words of their own.
        assertEquals(listOf("Sans serif", "Serif", "Monospace"), items.fonts.map { it.label })
    }
}
