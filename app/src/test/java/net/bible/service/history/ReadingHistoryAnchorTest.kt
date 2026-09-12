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
package net.bible.service.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Slice 7 Task 3 / spec §5.1.
 *
 * `HistoryManager.createHistoryItem` used to ask `CurrentActivityHolder.currentActivity is
 * MainBibleActivity`; it now asks [ReadingViewVisibility]. That branch is the ONLY producer of
 * [KeyHistoryItem], which is in turn the only item type `getEntities`/`restoreFrom` persist — so a
 * silent regression here empties the verse back-stack AND stops history surviving a restart, with
 * no compile error and no other failing test.
 *
 * The fixture is a real `WindowControl`/`WindowRepository`/`Window` graph on Robolectric (same
 * style as `WindowPaneMenuStateBuilderTest`), because `Window` and `WindowControl` are non-`open`
 * Kotlin classes that Mockito's default mock maker cannot stub, and because `createHistoryItem`
 * reads a real `currentPage.currentDocument` / `singleKey` / `anchorOrdinal`.
 *
 * Note on the predicate's OTHER side: in a unit test `CurrentActivityHolder.currentActivity` is
 * always null, so the old `is MainBibleActivity` check is permanently false here. That is why the
 * "no item" case is paired with [theVisibilityFlagIsWhatGatesKeyHistoryItemCreation], a
 * differential test whose visible-true half fails if the predicate is reverted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHistoryAnchorTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var historyManager: HistoryManager

    private val window: Window get() = windowRepository.activeWindow

    /** Psalm 139:2 in KJV — a real installed module, so `getEntities`/`restoreFrom` can round-trip. */
    private val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 139, 2)

    private fun historyManagerWithOneWindow(): HistoryManager {
        val kjv = requireNotNull(Books.installed().getBook("KJV")) { "KJV test module must be installed" }
        window.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)
        windowControl.activeWindow = window
        return historyManager
    }

    @Before
    fun setUp() {
        ReadingViewVisibility.setVisible(false)
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()
        historyManager = HistoryManager(windowControl)
    }

    @After
    fun tearDown() {
        ReadingViewVisibility.setVisible(false)
        ABEventBus.unregister(historyManager)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    @Test
    fun aKeyHistoryItemIsCreatedWhenTheReadingViewIsVisible() {
        ReadingViewVisibility.setVisible(true)
        val manager = historyManagerWithOneWindow()
        manager.addHistoryItem(window)
        assertTrue(
            manager.getHistory(window.id).firstOrNull() is KeyHistoryItem,
            "the reading view is visible, so the verse position must be recorded"
        )
    }

    @Test
    fun noKeyHistoryItemIsCreatedWhenTheReadingViewIsNotVisible() {
        ReadingViewVisibility.setVisible(false)
        val manager = historyManagerWithOneWindow()
        manager.addHistoryItem(window)
        assertTrue(
            manager.getHistory(window.id).none { it is KeyHistoryItem },
            "nothing is looking at the reading view, so there is no verse position to record"
        )
    }

    /**
     * The discriminating form of the two tests above: ONE manager, one window, the flag flipped
     * between the two calls. Reverting the predicate to `currentActivity is MainBibleActivity`
     * fails this at the first assertion (nothing is ever recorded); hard-wiring the branch to
     * `true` fails it at the second (a second item appears).
     */
    @Test
    fun theVisibilityFlagIsWhatGatesKeyHistoryItemCreation() {
        val manager = historyManagerWithOneWindow()

        ReadingViewVisibility.setVisible(true)
        manager.addHistoryItem(window)
        assertEquals(1, manager.getHistory(window.id).count { it is KeyHistoryItem })

        ReadingViewVisibility.setVisible(false)
        window.pageManager.currentBible.setCurrentDocumentAndKey(
            requireNotNull(Books.installed().getBook("KJV")),
            Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 23, 1)
        )
        manager.addHistoryItem(window)
        assertEquals(
            1, manager.getHistory(window.id).count { it is KeyHistoryItem },
            "the flag is off, so the second move must not be recorded"
        )
    }

    /**
     * `getEntities`/`restoreFrom` (`HistoryManager.kt:80-114`) persist ONLY `KeyHistoryItem`, so the
     * predicate is also what keeps history alive across a restart. A test that only checked item
     * creation would miss a regression in persistence.
     */
    @Test
    fun keyHistoryItemsStillRoundTripThroughTheEntities() {
        ReadingViewVisibility.setVisible(true)
        val manager = historyManagerWithOneWindow()
        manager.addHistoryItem(window)

        val entities = manager.getEntities(window.id)
        assertEquals(1, entities.size, "the visible reading view must produce one persistable entity")
        assertEquals("KJV", entities.single().document)

        manager.clear()
        assertEquals(0, manager.getHistory(window.id).size)

        manager.restoreFrom(window, entities)
        val restored = manager.getHistory(window.id)
        assertEquals(1, restored.size)
        assertEquals(verse.osisID, (restored.single() as KeyHistoryItem).key.osisID)
    }
}
