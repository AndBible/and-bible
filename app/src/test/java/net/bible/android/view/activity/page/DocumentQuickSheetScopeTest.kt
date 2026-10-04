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

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocumentQuickTab
import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The document quick sheet is built per [DocumentSheetScope]: its rows, its This-verse tab and its saved tab. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentQuickSheetScopeTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity
    private val documentControl: DocumentControl get() = GlobalContext.get().get()

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
    }

    @After
    fun tearDown() {
        DocumentSheetScope.entries.forEach { CommonUtils.settings.setString(it.tabSettingKey, null) }
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    @Test
    fun theBibleSheetOffersOnlyBiblesAndItsVerseTabIsBiblesForVerse() {
        val host = ComposeReadingViewHost(activity)
        val tabs = host.buildDocumentQuickTabsForHost(DocumentSheetScope.BIBLE)
        assertTrue(DocumentQuickTab.ALL in tabs.visible, "sanity: the test modules include Bibles")
        DocumentQuickTab.entries.forEach { tab ->
            assertTrue(tabs.rowsByTab.getValue(tab).all { it.category == DocCategory.BIBLE }, "$tab leaks a non-Bible")
        }
        val expected = documentControl.biblesForVerse.filterNot { it.isLocked }.map { it.initials }.toSet()
        assertEquals(expected, tabs.rowsByTab.getValue(DocumentQuickTab.FOR_VERSE).map { it.docId }.toSet())
    }

    @Test
    fun theCommentarySheetsVerseTabIsCommentariesForVerseAndAllAddsDictionaries() {
        val host = ComposeReadingViewHost(activity)
        val tabs = host.buildDocumentQuickTabsForHost(DocumentSheetScope.COMMENTARY)
        val forVerse = tabs.rowsByTab.getValue(DocumentQuickTab.FOR_VERSE).map { it.docId }.toSet()
        assertTrue(
            forVerse.all { id -> documentControl.commentariesForVerse.any { it.initials == id } },
            "This-verse must be commentariesForVerse only; got $forVerse",
        )
        val allCategories = tabs.rowsByTab.getValue(DocumentQuickTab.ALL).map { it.category }.toSet()
        assertTrue(DocCategory.DICTIONARY in allCategories, "the Strong's lexicons in the test modules must be offered; got $allCategories")
        assertFalse(DocCategory.BIBLE in allCategories)
    }

    @Test
    fun theTitleSheetStillOffersLastFilterAndNotAll() {
        val tabs = ComposeReadingViewHost(activity).buildDocumentQuickTabsForHost(DocumentSheetScope.ALL)
        assertFalse(DocumentQuickTab.ALL in tabs.visible)
        assertTrue(DocumentQuickTab.LAST_FILTER in tabs.visible)
    }

    @Test
    fun eachScopeRestoresItsOwnTab() {
        val host = ComposeReadingViewHost(activity)
        host.persistQuickDocTab(DocumentSheetScope.BIBLE, DocumentQuickTab.ALL.name)
        host.persistQuickDocTab(DocumentSheetScope.ALL, DocumentQuickTab.LAST_FILTER.name)
        val scopedVisible = listOf(DocumentQuickTab.RECENT, DocumentQuickTab.FOR_VERSE, DocumentQuickTab.ALL)
        assertEquals("ALL", host.restoreQuickDocTab(DocumentSheetScope.BIBLE, scopedVisible))
        assertEquals("RECENT", host.restoreQuickDocTab(DocumentSheetScope.COMMENTARY, scopedVisible), "nothing saved -> first visible")
        assertEquals(
            "RECENT",
            host.restoreQuickDocTab(DocumentSheetScope.ALL, listOf(DocumentQuickTab.RECENT, DocumentQuickTab.FOR_VERSE)),
            "a saved tab that is not visible falls back to the first visible one",
        )
    }
}
