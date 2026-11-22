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
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.resetComposeUiDispatcher
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Issue #4196 on Android: a hardware Esc closes the reading view's layers (find mode here) as Back does. Android 16's
 * `Generic.kcm` maps ESCAPE to nothing (older releases sent a fallback BACK when the app left Esc unhandled), so the host
 * answers an Esc that no view consumed itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class EscapeKeyBackTest {
    @get:org.junit.Rule
    val compose = createEmptyComposeRule()

    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    private fun host(): NavHostComposeActivity {
        firstTime = false
        resetComposeUiDispatcher()
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { hostControllers += it }.setup().get()
        net.bible.test.awaitReadingGraph(activity) { compose.waitForIdle() }
        if (activity.composeReadingViewHost == null) {
            activity.composeReadingViewHost = net.bible.test.fixtureReadingHost(activity)
        }
        return activity
    }

    @Before fun readingViewIsWhatTheUserSees() = ReadingViewVisibility.setVisible(true)

    @After fun closeHosts() = net.bible.test.closeReadingHostsBeforeReset(hostControllers)

    private fun NavHostComposeActivity.searchModeActive() =
        composeReadingViewHost!!.searchController.searchModeActive.value

    private fun NavHostComposeActivity.openFind() {
        assertTrue(readingCommands.composeSearchIfHosted(), "sanity: find opens")
        compose.waitForIdle()
        assertTrue(searchModeActive(), "sanity: find mode is on")
    }

    /** One hardware Esc press through the Activity's key dispatch, as the input dispatcher delivers it. */
    private fun NavHostComposeActivity.pressEscape(metaState: Int = 0): Boolean {
        val down = dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0, metaState))
        val up = dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0, metaState))
        compose.waitForIdle()
        return down && up
    }

    @Test
    fun escapeClosesTheSearchSheetThenFindMode() {
        val activity = host()
        val search = activity.composeReadingViewHost!!.searchController
        activity.composeReadingViewHost!!.openSearch("light")
        compose.waitForIdle()
        assertTrue(search.searchModeActive.value, "sanity: find mode is on")
        assertTrue(search.sheetVisible.value, "sanity: its sheet is up")

        assertTrue(activity.pressEscape(), "the host answers an Esc no view consumed")
        assertFalse(search.sheetVisible.value, "the first Esc closes the sheet")
        assertTrue(search.searchModeActive.value, "and only the sheet")

        activity.pressEscape()
        assertFalse(search.searchModeActive.value, "the second Esc closes find mode, as Back does")
    }

    @Test
    fun escapeOnPlainReadingNeitherWarnsNorExits() {
        val activity = host()
        ShadowToast.reset()

        activity.pressEscape()
        activity.pressEscape()

        assertFalse(activity.isFinishing, "Esc must never exit the app from the plain Bible text")
        assertNull(ShadowToast.getLatestToast(), "nor show Back's 'press again to exit' warning")
    }

    @Test
    fun escapeWithAModifierIsNotBack() {
        val activity = host()
        activity.openFind()

        activity.pressEscape(KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
        activity.pressEscape(KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)

        assertTrue(activity.searchModeActive(), "Alt+Esc / Ctrl+Esc are the platform's HOME / MENU fallbacks, not Back")
    }

    /**
     * A view that uses Esc itself (the note editors in the BibleView, a Compose field) consumes it in the view tree;
     * the host must not also go back for it. Only the unconsumed key reaches the Activity's `onKeyDown`/`onKeyUp`.
     */
    @Test
    fun escapeConsumedByTheFocusedViewIsNotAlsoBack() {
        val activity = host()
        activity.openFind()
        val consumed = mutableListOf<Int>()
        val focused = View(activity).apply {
            isFocusable = true
            setOnKeyListener { _, _, event -> consumed += event.action; true }
        }
        activity.addContentView(focused, ViewGroup.LayoutParams(10, 10))
        // Key dispatch only descends into laid-out (bounded) views.
        activity.window.decorView.apply {
            measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 1080, 1920)
        }
        assertTrue(focused.requestFocus(), "sanity: stand-in holds focus")

        activity.pressEscape()

        assertTrue(consumed.isNotEmpty(), "sanity: the focused view received the Esc")
        assertTrue(activity.searchModeActive(), "a consumed Esc is not a second Back")
    }

    private fun historyDepth(): Int =
        GlobalContext.get().get<HistoryManager>().getEntities(CommonUtils.windowControl.activeWindow.id).size

    /** Two reading positions in the host's own window, as `ReadingHostBackChainTest.pushTwoReadingPositions` does. */
    private fun pushTwoReadingPositions() {
        val kjv = requireNotNull(Books.installed().getBook("KJV")) { "KJV test module must be installed" }
        val versification = Versifications.instance().getVersification("KJV")
        val manager = GlobalContext.get().get<HistoryManager>()
        val window = CommonUtils.windowControl.activeWindow
        window.pageManager.currentBible.setCurrentDocumentAndKey(kjv, Verse(versification, BibleBook.PS, 139, 2))
        manager.addHistoryItem(window)
        window.pageManager.currentBible.setCurrentDocumentAndKey(kjv, Verse(versification, BibleBook.PS, 23, 1))
        manager.addHistoryItem(window)
    }

    /** Back walks the reading history on plain reading; Esc only closes layers, like desktop's Esc. */
    @Test
    fun escapeDoesNotWalkTheReadingHistory() {
        val activity = host()
        pushTwoReadingPositions()
        val depth = historyDepth()
        assertTrue(depth >= 2, "sanity: history to walk")

        activity.pressEscape()

        assertEquals(depth, historyDepth(), "Esc must not step back in the reading history")
        assertFalse(activity.isFinishing)
    }
}
