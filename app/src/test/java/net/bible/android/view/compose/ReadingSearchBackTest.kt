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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
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
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F6 Task 9 — the back chain that closes the reading-view search sheet/mode, and the long-press-back
 * swallow while search is active. `MainBibleActivity.onBackPressed`/`onKeyLongPress` are the only
 * live back route today (predictive back is opted out via `AndroidManifest.xml`'s
 * `android:enableOnBackInvokedCallback="false"`, documented there as temporary until targetSdk 37 —
 * see `app/build.gradle.kts`'s `targetSdk = 36`), so a Compose `BackHandler` inside the reading view
 * would never fire; the branch has to live in this method instead.
 *
 * Built the same "real (never-`.create()`'d) `MainBibleActivity` + real (never-`.install()`ed)
 * `ComposeReadingViewHost`" way as [ReadingSearchEntryPointsTest]: the host's own
 * `searchController`/`isDrawerOpen` state IS the recording, no fake/mock needed. Two of the three
 * cases return early out of `onBackPressed` before touching `binding`/`documentViewManager`
 * (both lateinit, only set in the real `onCreate()`), so they can drive the real method directly.
 * The third (`aBackWithSearchClosedIsNotConsumed`) asserts the new guard's own return value instead
 * of the full method: with both compose branches inert, `onBackPressed` falls through into
 * `binding.drawerLayout`/`documentViewManager`, neither of which this never-`.create()`'d activity
 * has — driving the guard directly is what the brief's "recording host seam rather than a real
 * activity" calls for here, and is the same scoping precedent
 * [ReadingSearchEntryPointsTest.composeSearchIfHostedReturnsFalseWhenNoHostIsInstalled] already sets
 * for a guard whose OTHER outcome is cheap to drive through the real call site.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingSearchBackTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: MainBibleActivity

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(MainBibleActivity::class.java).get()
        activity.windowRepository = windowRepository

        val kjv = Books.installed().getBook("KJV") as SwordBook
        val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)

        activity.composeReadingViewHost = ComposeReadingViewHost(activity)
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun host() = activity.composeReadingViewHost!!

    @Test
    fun theFirstBackClosesTheSheetAndTheSecondLeavesSearchMode() {
        host().openSearch("light")
        assertTrue(host().searchController.searchModeActive.value, "sanity")
        assertTrue(host().searchController.sheetVisible.value, "sanity")

        activity.onBackPressed()
        assertTrue(host().searchController.searchModeActive.value, "first press must only close the sheet")
        assertFalse(host().searchController.sheetVisible.value, "first press closes the sheet")

        activity.onBackPressed()
        assertFalse(host().searchController.searchModeActive.value, "second press leaves search mode")
    }

    @Test
    fun aBackWithSearchClosedIsNotConsumed() {
        assertFalse(host().searchController.searchModeActive.value, "sanity: search is closed")
        assertFalse(host().isDrawerOpen, "sanity: drawer is closed")

        assertFalse(
            activity.composeCloseSearchIfOpen(),
            "with nothing open, the guard must not consume the press, so onBackPressed falls through",
        )
    }

    @Test
    fun theDrawerStillWinsOverSearch() {
        host().openSearch("light")
        host().openDrawer()
        assertTrue(host().searchController.searchModeActive.value, "sanity")
        assertTrue(host().isDrawerOpen, "sanity")

        activity.onBackPressed()

        assertFalse(host().isDrawerOpen, "the drawer closes")
        assertTrue(host().searchController.searchModeActive.value, "search must be untouched")
        assertTrue(host().searchController.sheetVisible.value, "search must be untouched")
    }

    /**
     * Step 3: with a focused search field now reachable, long-press back must not fall through to
     * opening History out from under it — the same swallow [composeDrawerOpen] already gets. Only
     * the swallowed case is driven through the real [MainBibleActivity.onKeyLongPress]: the
     * fallthrough (History) branch reads `binding.drawerLayout` right after the compose guards
     * regardless of Task 9 (pre-existing, not this task's code), which this never-`.create()`'d
     * activity has no `binding` for — same reason [aBackWithSearchClosedIsNotConsumed] drives its
     * guard directly instead of the full method.
     */
    @Test
    fun longPressBackIsSwallowedWhileSearchIsActive() {
        host().openSearch("light")

        val consumed = activity.onKeyLongPress(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))

        assertTrue(consumed, "must not fall through to opening History")
        assertTrue(host().searchController.searchModeActive.value, "swallowing must not itself close search")
    }
}
