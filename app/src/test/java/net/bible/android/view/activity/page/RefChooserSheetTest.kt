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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.versification.BookName
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NAVIGATE_TO_VERSE_PREF = "navigate_to_verse_pref"

/**
 * nav-graph slice 7 Task 10 / spec §6.1.1 + §6.3.
 *
 * `BibleJavascriptInterface.refChooserDialog` used to round-trip the full-screen passage grid
 * through `awaitIntent`, forcing verse-level drill-down on the Intent it built. It now opens the
 * reading view's own Grid quick sheet instead — and the quick sheet's Grid arm reads the
 * `navigate_to_verse_pref` preference, which defaults to FALSE. Opening the existing sheet
 * unchanged would therefore have stopped the JS reference chooser at chapter level for every user
 * who never turned that preference on, which is the behaviour trap §6.1.1 records.
 *
 * The host is built the same way `MainBibleActivityHandleWindowPaneMenuItemTest` builds it: a
 * Robolectric [MainBibleActivity] that is never `.create()`d and a [ComposeReadingViewHost] that is
 * never `install()`ed, because none of the entry points exercised here touch `activity.binding`.
 */
@OptIn(ExperimentalCoroutinesApi::class) // `getCompleted()` — every assertion first checks `isCompleted`.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class RefChooserSheetTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: MainBibleActivity
    private lateinit var host: ComposeReadingViewHost

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(MainBibleActivity::class.java).get()
        activity.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        host = ComposeReadingViewHost(activity)
    }

    @After
    fun tearDown() {
        CommonUtils.settings.removeBoolean(NAVIGATE_TO_VERSE_PREF)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    /**
     * THE TRAP (spec §6.1.1). The JS chooser always drilled to verse level; the sheet's Grid arm
     * reads a preference that is off by default. The sheet the JS bridge opens must carry the flag
     * itself, not inherit the preference.
     */
    @Test
    fun theJsRefChooserReachesVerseLevelEvenWhenThePrefIsOff() {
        CommonUtils.settings.setBoolean(NAVIGATE_TO_VERSE_PREF, false)

        host.openVerseChooserSheetForResult()

        assertEquals(
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid, navigateToVerse = true),
            host.quickSheet.value,
            "the JS reference chooser must reach verse level regardless of the preference",
        )
    }

    /** The other half of the trap: the ordinary title-tap path must keep obeying the preference. */
    @Test
    fun theQuickSheetKeyChooserStillHonoursThePref() {
        CommonUtils.settings.setBoolean(NAVIGATE_TO_VERSE_PREF, false)
        host.showKeyChooserSheet(KeyChooserKind.Grid, emptyList())
        assertEquals(
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid, navigateToVerse = false),
            host.quickSheet.value,
            "with the preference off the ordinary Grid sheet must stop at chapter level",
        )

        CommonUtils.settings.setBoolean(NAVIGATE_TO_VERSE_PREF, true)
        host.showKeyChooserSheet(KeyChooserKind.Grid, emptyList())
        assertEquals(
            ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid, navigateToVerse = true),
            host.quickSheet.value,
            "with the preference on the ordinary Grid sheet must reach verse level",
        )
    }

    /**
     * The sheet's selection callback resolves the JS request instead of navigating the reading
     * view — the `awaitIntent` result the old path read is replaced by this completion, and the
     * active window's key must be untouched (a JS reference chooser reports a verse back to the
     * page; it does not move the reader to it).
     */
    @Test
    fun choosingAVerseCompletesTheJsRequestInsteadOfNavigating() {
        val keyBefore = windowRepository.activeWindow.pageManager.currentPage.key

        val pending = host.openVerseChooserSheetForResult()
        assertFalse(pending.isCompleted, "sanity: nothing is chosen yet")

        host.onGridPassageChosen("Gen.1.1")

        assertTrue(pending.isCompleted, "choosing must resolve the JS request, not just act on it")
        assertEquals("Gen.1.1", pending.getCompleted())
        assertNull(host.quickSheet.value, "choosing must close the sheet")
        assertEquals(
            keyBefore,
            windowRepository.activeWindow.pageManager.currentPage.key,
            "the JS chooser reports a verse back to the page; it must not navigate the reader",
        )
    }

    /**
     * Dismissing the sheet must still answer the JS call. `bibleView.response(callId, …)` is a
     * promise on the JS side: a request that is never resolved leaks a pending promise for the life
     * of the page, so the cancelled case has to complete with no verse rather than simply return.
     */
    @Test
    fun dismissingTheSheetCompletesTheJsRequestWithNoVerse() {
        val pending = host.openVerseChooserSheetForResult()

        host.closeQuickSheet()

        assertTrue(pending.isCompleted, "a dismissed sheet must not leave the JS promise pending")
        assertNull(pending.getCompleted())
        assertNull(host.quickSheet.value)
    }

    /** Opening some other quick sheet abandons the outstanding request rather than stranding it. */
    @Test
    fun openingAnotherQuickSheetAbandonsTheOutstandingJsRequest() {
        val pending = host.openVerseChooserSheetForResult()

        host.showQuickSheet(ReadingQuickSheet.History)

        assertTrue(pending.isCompleted, "an abandoned request must not leave the JS promise pending")
        assertNull(pending.getCompleted())
    }

    /**
     * The returned name's formatting is part of the JS contract, not incidental: the chooser always
     * answers in SHORT book-name form, whatever `BookName.isFullBookName()` happens to be set to
     * globally, and it restores that global afterwards.
     */
    @Test
    fun theChosenVerseIsReturnedToJsInShortBookNameForm() {
        val original = BookName.isFullBookName()
        try {
            BookName.setFullBookName(true)
            assertEquals("Gen 1:1", refChooserVerseName("Gen.1.1"))
            assertTrue(BookName.isFullBookName(), "the global full-book-name flag must be restored")

            BookName.setFullBookName(false)
            assertEquals("Gen 1:1", refChooserVerseName("Gen.1.1"))
            assertFalse(BookName.isFullBookName(), "the global full-book-name flag must be restored")
        } finally {
            BookName.setFullBookName(original)
        }
    }

    /** A cancelled chooser answers JS with an empty string, exactly as the Intent path did. */
    @Test
    fun noChosenVerseIsReturnedToJsAsAnEmptyString() {
        assertEquals("", refChooserVerseName(null))
    }
}
