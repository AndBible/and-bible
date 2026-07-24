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
     * The drift guard: the builder's static table must match `main_bible_drawer_menu.xml` in ids,
     * titles, declaration order AND submenu grouping. Compared group-by-group (rather than a single
     * flattened id list) so this catches two things a flat id-list comparison would miss:
     *  - a stale `titleRes` (XML title changed, table title didn't — id order is unaffected), and
     *  - an item moved to a *different* submenu (id order can stay byte-identical while the group
     *    membership silently changes, which would ship the row under the wrong section heading).
     * Icons are intentionally NOT compared here: a `Drawable` instance has no stable identity to
     * assert against (see `entryIconNames` / `ComposeReadingViewHostTest` for icon-key coverage).
     */
    @Test
    fun staticTable_matchesInflatedMenuXml() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val menu = MenuBuilder(context)
        MenuInflater(context).inflate(R.menu.main_bible_drawer_menu, menu)

        // Group the inflated XML exactly like DrawerMenuStateBuilder's StaticGroup shape:
        // consecutive top-level items with no submenu form one untitled group (the top tier, no
        // heading in the XML); each top-level item WITH a submenu is its own titled group.
        val xmlGroups = mutableListOf<Pair<String?, List<Pair<String, String>>>>()
        var untitledBucket = mutableListOf<Pair<String, String>>()
        fun flushUntitledBucket() {
            if (untitledBucket.isNotEmpty()) {
                xmlGroups += null to untitledBucket.toList()
                untitledBucket = mutableListOf()
            }
        }
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val sub = item.subMenu
            if (sub != null) {
                flushUntitledBucket()
                val subItems = (0 until sub.size()).map { j ->
                    val subItem = sub.getItem(j)
                    context.resources.getResourceEntryName(subItem.itemId) to subItem.title.toString()
                }
                xmlGroups += item.title.toString() to subItems
            } else {
                untitledBucket += context.resources.getResourceEntryName(item.itemId) to item.title.toString()
            }
        }
        flushUntitledBucket()

        val builderGroups = DrawerMenuStateBuilder.groupsForDriftTest.map { (headingRes, entries) ->
            headingRes?.let { context.getString(it) } to entries.map { (idName, titleRes) ->
                idName to context.getString(titleRes)
            }
        }

        assertEquals("group count", xmlGroups.size, builderGroups.size)
        for (i in xmlGroups.indices) {
            assertEquals("group $i heading", xmlGroups[i].first, builderGroups[i].first)
            assertEquals("group $i items (id+title, in order)", xmlGroups[i].second, builderGroups[i].second)
        }
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
