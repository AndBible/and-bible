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

package net.bible.android.control.page.window

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #3896: opening the FIRST links window (a verse tapped in a TSK-e or Compare window) must tell
 * every open window that the window set changed. The window that used to be the lowest one is no
 * longer at the bottom, so its BibleView has to drop the bottom bar offset (the bottom touch block
 * painted in the page background); without the [WindowChange.WindowsChanged] notification it kept
 * it, which showed as a white rectangle over the bottom of that window until the window set
 * changed again (e.g. minimising the links window).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowControlShowLinkTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository

    private val windowState: WindowStateServiceImpl get() = GlobalContext.get().get()

    @Before
    fun setUp() {
        repo = net.bible.test.testWindowRepository()
        windowControl = WindowControl()
        windowControl.windowRepository = repo
        repo.initializeAtEdge()
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase(repo.scope)
    }

    private fun windowChangesDuring(block: () -> Unit): List<WindowChange> {
        val seen = mutableListOf<WindowChange>()
        val subscription = windowState.windowChanges.subscribe { seen += it }
        try { block() } finally { subscription.cancel() }
        return seen
    }

    @Test
    fun showingTheFirstLinkCreatesTheLinksWindowAndNotifiesTheWindowSetChange() {
        val first = repo.activeWindow
        val second = repo.addNewWindow(first)
        repo.activeWindow = second
        assertEquals(listOf(first, second), repo.visibleWindows, "premise: two windows, the second at the bottom")
        val kjv = Books.installed().getBook("KJV") as SwordBook
        val key = Verse(kjv.versification, BibleBook.JOHN, 3, 16)

        val seen = windowChangesDuring { windowControl.showLink(kjv, key) }

        val links = repo.windowList.single { it.isLinksWindow }
        assertTrue(WindowChange.WindowsChanged in seen, "window set change must be notified, got $seen")
        assertEquals(listOf(first, second, links), repo.visibleWindows)
        assertEquals(links, repo.lastVisibleWindow, "the new links window is the bottom window")
        assertEquals(links, windowControl.activeWindow, "the links window showing the link becomes active")
        // The Compose split lays panes out from this snapshot: it must carry the links window last too.
        assertEquals(
            listOf(first, second, links).map { it.id.toString() },
            windowState.layout.value.windows.filter { it.isVisible }.map { it.id },
        )
    }

    @Test
    fun showingALinkInAnAlreadyVisibleLinksWindowDoesNotNotifyAgain() {
        val kjv = Books.installed().getBook("KJV") as SwordBook
        val source = repo.activeWindow
        windowControl.showLink(kjv, Verse(kjv.versification, BibleBook.JOHN, 3, 16))
        val links = repo.windowList.single { it.isLinksWindow }
        assertTrue(links.isVisible)
        // Back to the source window, as after reading the link: a link tapped IN the links window
        // would open a chained links window instead (#2469).
        repo.activeWindow = source

        val seen = windowChangesDuring { windowControl.showLink(kjv, Verse(kjv.versification, BibleBook.JOHN, 3, 17)) }

        assertTrue(WindowChange.WindowsChanged !in seen, "nothing about the window set changed, got $seen")
        assertEquals(links, repo.windowList.single { it.isLinksWindow })
    }
    // ---- #4141: a link from a maximised window opens in the links window shown beside it ----

    private val kjv get() = Books.installed().getBook("KJV") as SwordBook
    private fun verse(chapter: Int, verse: Int) = Verse(kjv.versification, BibleBook.JOHN, chapter, verse)

    /** A maximised source window showing John 1:1 (beside a hidden second window), and a link (John 3:16) shown from it. */
    private fun showLinkFromMaximisedSource(): Triple<Window, Window, Window> {
        val source = repo.activeWindow
        val other = repo.addNewWindow(source)
        repo.activeWindow = source
        source.pageManager.setCurrentDocumentAndKey(kjv, verse(1, 1))
        windowControl.maximiseWindow(source)
        windowControl.showLink(kjv, verse(3, 16))
        return Triple(source, repo.windowList.single { it.isLinksWindow }, other)
    }

    @Test
    fun aLinkFromAMaximisedWindowIsShownInTheLinksWindowBesideIt() {
        val (source, links, other) = showLinkFromMaximisedSource()

        assertEquals(source.id, repo.maximizedWindowId, "the source stays maximised")
        assertEquals(listOf(source, links), repo.visibleWindows, "two panes: the maximised window and its links window")
        assertFalse(other.isVisible, "other windows stay hidden")
        assertEquals(links, windowControl.activeWindow)
        assertEquals(verse(3, 16), links.pageManager.currentBible.singleKey)
        assertEquals(verse(1, 1), source.pageManager.currentBible.singleKey, "the source keeps its position")
        val layout = windowState.layout.value
        assertEquals(source.id.toString(), layout.maximizedWindowId)
        assertEquals(listOf(source, links).map { it.id.toString() }, layout.windows.filter { it.isVisible }.map { it.id })
    }

    @Test
    fun theFirstLinkNotifiesTheWindowSetChange() {
        val source = repo.activeWindow
        windowControl.maximiseWindow(source)

        val seen = windowChangesDuring { windowControl.showLink(kjv, verse(3, 16)) }

        assertTrue(WindowChange.WindowsChanged in seen, "the second pane must be laid out, got $seen")
    }

    @Test
    fun furtherLinksFromEitherPaneReuseTheLinksWindow() {
        val (source, links, _) = showLinkFromMaximisedSource()

        // From the links pane (active after the first link): no chained links window (#2469) in maximised mode.
        windowControl.showLink(kjv, verse(3, 17))
        assertEquals(verse(3, 17), links.pageManager.currentBible.singleKey)
        assertEquals(listOf(links), repo.windowList.filter { it.isLinksWindow })

        // From the maximised window again.
        repo.activeWindow = source
        windowControl.showLink(kjv, verse(3, 18))
        assertEquals(verse(3, 18), links.pageManager.currentBible.singleKey)
        assertEquals(listOf(source, links), repo.visibleWindows)
    }

    @Test
    fun closingTheLinksWindowReturnsToThePlainMaximisedWindow() {
        val (source, links, _) = showLinkFromMaximisedSource()

        windowControl.closeWindow(links)

        assertEquals(source.id, repo.maximizedWindowId)
        assertEquals(listOf(source), repo.visibleWindows)
        assertEquals(source, windowControl.activeWindow)
    }

    @Test
    fun dismissingTheLinksPaneMinimisesItAndTheNextLinkShowsItAgain() {
        val (source, links, _) = showLinkFromMaximisedSource()

        windowControl.dismissWindow(links)
        assertEquals(listOf(source), repo.visibleWindows)
        assertEquals(source, windowControl.activeWindow)

        windowControl.showLink(kjv, verse(3, 17))
        assertEquals(listOf(source, links), repo.visibleWindows)
    }

    @Test
    fun unMaximiseFromTheRailKeepsTheLinksWindowAsTheNormalLinksWindow() {
        val (source, links, other) = showLinkFromMaximisedSource()

        WindowCommandsImpl(windowControl).unMaximise()

        assertNull(repo.maximizedWindowId)
        assertTrue(links in repo.visibleWindows, "the links window stays open in the split")
        assertTrue(source in repo.visibleWindows)
        assertEquals(other.isVisible, other.windowState == WindowLayout.WindowState.VISIBLE, "other windows as in the split")
    }

    @Test
    fun maximisingAWindowWhoseLinksWindowIsOpenShowsItAloneUntilUnMaximised() {
        val source = repo.activeWindow
        windowControl.showLink(kjv, verse(3, 16))
        val links = repo.windowList.single { it.isLinksWindow }
        assertTrue(links in repo.visibleWindows, "premise: the links window is open in the split")

        windowControl.maximiseWindow(source)
        assertEquals(listOf(source), repo.visibleWindows)
        assertEquals(source, windowControl.activeWindow, "the hidden links window is not left active")

        windowControl.unMaximise()
        assertTrue(links in repo.visibleWindows, "un-maximise brings the links window back")
    }
    /** Split with the source, its links window L and L's chained links window L2 (a link tapped inside L). */
    private fun splitWithAChainedLinksWindow(): Triple<Window, Window, Window> {
        val source = repo.activeWindow
        windowControl.showLink(kjv, verse(3, 16))
        val links = repo.windowList.single { it.isLinksWindow }
        assertEquals(links, windowControl.activeWindow)
        windowControl.showLink(kjv, verse(3, 17))
        val chained = repo.windowList.single { it.isLinksWindow && it != links }
        assertEquals(listOf(source, links, chained), repo.visibleWindows, "premise: L2 is open in the split")
        repo.activeWindow = source
        return Triple(source, links, chained)
    }

    @Test
    fun closingTheLinksPaneDoesNotBringInAChainedLinksWindow() {
        val (source, links, chained) = splitWithAChainedLinksWindow()
        windowControl.maximiseWindow(source)
        windowControl.showLink(kjv, verse(3, 18))
        assertEquals(listOf(source, links), repo.visibleWindows, "premise: L beside the maximised window")

        windowControl.closeWindow(links)

        assertEquals(listOf(source), repo.visibleWindows, "plain maximised window, not the chained L2")
        assertFalse(chained.isVisible)
        assertEquals(source, windowControl.activeWindow)
        assertNull(repo.primaryTargetLinksWindowId, "the closed primary links window is no longer the target")
    }

    @Test
    fun afterTheLinksPaneClosedTheNextLinkShowsItsNewLinksWindowBesideTheMaximisedWindow() {
        val (source, links, chained) = splitWithAChainedLinksWindow()
        windowControl.maximiseWindow(source)
        windowControl.showLink(kjv, verse(3, 18))
        windowControl.closeWindow(links)

        val seen = windowChangesDuring { windowControl.showLink(kjv, verse(3, 19)) }

        assertEquals(chained.id, repo.primaryTargetLinksWindowId, "the remaining links window is the new target")
        assertEquals(listOf(source, chained), repo.visibleWindows)
        assertEquals(verse(3, 19), chained.pageManager.currentBible.singleKey)
        assertEquals(chained, windowControl.activeWindow, "the pane that shows the link is active")
        assertTrue(WindowChange.WindowsChanged in seen, "the new pane must be laid out, got $seen")
    }

    @Test
    fun aHiddenLinksWindowShownByALinkAndThenMinimisedStaysMinimisedAfterUnMaximise() {
        val source = repo.activeWindow
        windowControl.showLink(kjv, verse(3, 16))
        val links = repo.windowList.single { it.isLinksWindow }
        repo.activeWindow = source
        windowControl.maximiseWindow(source)
        windowControl.showLink(kjv, verse(3, 17))
        assertEquals(listOf(source, links), repo.visibleWindows)

        windowControl.minimiseWindow(links)
        windowControl.unMaximise()

        assertFalse(links.isVisible, "the user's minimise wins over the state before maximising")
        assertEquals(listOf(source), repo.visibleWindows)
    }
}
