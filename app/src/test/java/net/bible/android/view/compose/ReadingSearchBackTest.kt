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

import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.crosswire.jsword.passage.Verse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F6 Task 9 — the back chain that closes the reading-view search sheet/mode, and the long-press-back
 * swallow while search is active. The reading destination's `PassThroughBackHandler` (predictive back is on) and the
 * host's `onKeyLongPress` (the nav host's since slice 8 F2; classic `MainBibleActivity`'s before) are the live back
 * routes, so a Compose `BackHandler` inside the reading view would never be reached before the search guard; the
 * branch has to live in the host's back chain instead.
 *
 * The host's own `searchController`/`isDrawerOpen` state IS the recording, no fake/mock needed.
 * `aBackWithSearchClosedIsNotConsumed` asserts the search guard's own return value rather than the
 * full method (with nothing open the chain falls through to history / the exit warning, which
 * `ReadingHostBackChainTest` pins). Slice 8 F2 moved the fixture from a never-`.create()`d
 * `MainBibleActivity` onto a set-up reading-route nav host -- see [setUp] for why it must be set up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingSearchBackTest {
    private lateinit var controller: ActivityController<NavHostComposeActivity>
    private lateinit var activity: NavHostComposeActivity

    /**
     * Slice 8 F2: a fully set-up reading-route [NavHostComposeActivity] rather than classic's
     * never-`.create()`d `MainBibleActivity`. The reading destination's back handler and the nav host's `onKeyLongPress` run the
     * reading chain only while `readingDestinationIsCurrent()`, which reads the graph's `navController`
     * -- null on a never-created host, where every press would fall to `super` and the long-press
     * assertion below would pass on `ActivityBase`'s unconditional `true` for BACK. `firstTime` and
     * [resetComposeUiDispatcher] for the reasons `ReadingHostBackChainTest.host()` and slice 8 D1 give.
     * The `ComposeReadingViewHost` is still installed directly when the destination has not composed
     * one, exactly as classic's fixture did.
     */
    @Before
    fun setUp() {
        firstTime = false
        resetComposeUiDispatcher()
        controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).setup()
        activity = controller.get()

        val kjv = Books.installed().getBook("KJV") as SwordBook
        val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        activity.hostWindowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)

        if (activity.composeReadingViewHost == null) activity.composeReadingViewHost = ComposeReadingViewHost(activity)
    }

    @After
    fun tearDown() {
        controller.close()
        ReadingViewVisibility.setVisible(false)
        ReadingHostPresence.setForeground(null)
        DatabaseResetter.resetDatabase()
    }

    private fun host() = activity.composeReadingViewHost!!

    @Test
    fun theFirstBackClosesTheSheetAndTheSecondLeavesSearchMode() {
        host().openSearch("light")
        assertTrue(host().searchController.searchModeActive.value, "sanity")
        assertTrue(host().searchController.sheetVisible.value, "sanity")

        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(host().searchController.searchModeActive.value, "first press must only close the sheet")
        assertFalse(host().searchController.sheetVisible.value, "first press closes the sheet")

        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(host().searchController.searchModeActive.value, "second press leaves search mode")
    }

    @Test
    fun aBackWithSearchClosedIsNotConsumed() {
        assertFalse(host().searchController.searchModeActive.value, "sanity: search is closed")
        assertFalse(host().isDrawerOpen, "sanity: drawer is closed")

        assertFalse(
            activity.readingCommands.composeCloseSearchIfOpen(),
            "with nothing open, the guard must not consume the press, so onBackPressed falls through",
        )
    }

    @Test
    fun theDrawerStillWinsOverSearch() {
        host().openSearch("light")
        host().openDrawer()
        assertTrue(host().searchController.searchModeActive.value, "sanity")
        assertTrue(host().isDrawerOpen, "sanity")

        activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(host().isDrawerOpen, "the drawer closes")
        assertTrue(host().searchController.searchModeActive.value, "search must be untouched")
        assertTrue(host().searchController.sheetVisible.value, "search must be untouched")
    }

    /**
     * Step 3: with a focused search field now reachable, long-press back must not fall through to
     * opening History out from under it — the same swallow the open drawer already gets. Both
     * branches are driven through the real [NavHostComposeActivity.onKeyLongPress] on the set-up
     * host: first a positive control (search closed -> the History quick sheet opens, so the
     * observable below really can see "opened History"), then the swallowed case.
     */
    @Test
    fun longPressBackIsSwallowedWhileSearchIsActive() {
        // Positive control: with search closed a long BACK opens History on this host.
        assertTrue(activity.onKeyLongPress(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertEquals(
            ReadingQuickSheet.History,
            host().quickSheet.value,
            "positive control: with search closed a long BACK must open the History sheet",
        )
        host().quickSheet.value = null

        host().openSearch("light")

        val consumed = activity.onKeyLongPress(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))

        assertTrue(consumed, "must not fall through to opening History")
        // Slice 8 F2: `consumed` alone cannot tell a swallow from "opened History" -- the nav host's
        // onKeyLongPress returns true for both -- so the History sheet itself is asserted closed.
        assertNotEquals(
            ReadingQuickSheet.History,
            host().quickSheet.value,
            "a long BACK while search is active must be swallowed, not open the History sheet",
        )
        assertTrue(host().searchController.searchModeActive.value, "swallowing must not itself close search")
    }
}
