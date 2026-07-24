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

package net.bible.android.view.activity.page

import android.view.MenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
class DrawerMenuStateBuilderTest {

    /**
     * The drift guard: the builder's static table must match `main_bible_drawer_menu.xml` in ids
     * AND declaration order, including submenu nesting. If somebody edits the XML without touching
     * the table (or vice versa), this fails instead of the drawer silently losing a row.
     */
    @Test
    fun staticTable_matchesInflatedMenuXml() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val menu = MenuBuilder(context)
        MenuInflater(context).inflate(R.menu.main_bible_drawer_menu, menu)

        val xmlIdNames = mutableListOf<String>()
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val sub = item.subMenu
            if (sub != null) {
                for (j in 0 until sub.size()) {
                    xmlIdNames += context.resources.getResourceEntryName(sub.getItem(j).itemId)
                }
            } else {
                xmlIdNames += context.resources.getResourceEntryName(item.itemId)
            }
        }

        assertEquals(xmlIdNames, DrawerMenuStateBuilder.entryIdNames)
    }

    @Test
    fun build_allVisible_hasFourGroupsAnd22Items() {
        val state = DrawerMenuStateBuilder.build(
            showSearch = true, showSpeak = true, isCloudSyncAvailable = true, isRateVisible = true)
        assertEquals(4, state.groups.size)
        assertEquals(null, state.groups[0].title)
        assertEquals(22, state.groups.sumOf { it.items.size })
    }

    @Test
    fun build_hidesGoogleDriveSyncWhenCloudSyncUnavailable() {
        val state = DrawerMenuStateBuilder.build(
            showSearch = true, showSpeak = true, isCloudSyncAvailable = false, isRateVisible = true)
        val ids = state.groups.flatMap { it.items }.map { it.id }
        assertFalse(ids.contains("googleDriveSync"))
        assertTrue(ids.contains("backupMainMenu"))
        assertEquals(21, ids.size)
    }

    @Test
    fun build_hidesRateButtonWhenNotVisible() {
        val state = DrawerMenuStateBuilder.build(
            showSearch = true, showSpeak = true, isCloudSyncAvailable = true, isRateVisible = false)
        assertFalse(state.groups.flatMap { it.items }.map { it.id }.contains("rateButton"))
    }

    @Test
    fun build_disablesSearchAndSpeakWithoutHidingThem() {
        val state = DrawerMenuStateBuilder.build(
            showSearch = false, showSpeak = false, isCloudSyncAvailable = true, isRateVisible = true)
        val byId = state.groups.flatMap { it.items }.associateBy { it.id }
        assertFalse(byId.getValue("searchButton").enabled)
        assertFalse(byId.getValue("speakButton").enabled)
        assertTrue(byId.getValue("bookmarksButton").enabled)
    }

    @Test
    fun build_resolvesLabelsAndIconKeys() {
        val state = DrawerMenuStateBuilder.build(
            showSearch = true, showSpeak = true, isCloudSyncAvailable = true, isRateVisible = true)
        val search = state.groups.flatMap { it.items }.single { it.id == "searchButton" }
        assertEquals(application.getString(R.string.search), search.label)
        assertEquals("ic_search_24dp", search.iconKey)
    }

    @Test
    fun resIdFor_roundTripsEveryEntry() {
        for (idName in DrawerMenuStateBuilder.entryIdNames) {
            assertTrue("no resId for $idName", DrawerMenuStateBuilder.resIdFor(idName) != 0)
        }
    }
}
