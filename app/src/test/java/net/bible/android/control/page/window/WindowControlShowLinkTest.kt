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
    // ---- #4141: a link from a maximised window opens in the links window, maximised in its place ----

    private val kjv get() = Books.installed().getBook("KJV") as SwordBook
    private fun verse(chapter: Int, verse: Int) = Verse(kjv.versification, BibleBook.JOHN, chapter, verse)

    /** A maximised source window showing John 1:1, and a link (John 3:16) shown from it. */
    private fun showLinkFromMaximisedSource(): Pair<Window, Window> {
        val source = repo.activeWindow
        source.pageManager.setCurrentDocumentAndKey(kjv, verse(1, 1))
        windowControl.maximiseWindow(source)
        windowControl.showLink(kjv, verse(3, 16))
        return source to repo.windowList.single { it.isLinksWindow }
    }

    @Test
    fun aLinkFromAMaximisedWindowIsShownInTheLinksWindowMaximisedInItsPlace() {
        val (source, links) = showLinkFromMaximisedSource()

        assertEquals(links.id, repo.maximizedWindowId, "the links window takes the maximised place")
        assertEquals(listOf(links), repo.visibleWindows)
        assertEquals(links, windowControl.activeWindow)
        assertEquals(verse(3, 16), links.pageManager.currentBible.singleKey)
        assertEquals(verse(1, 1), source.pageManager.currentBible.singleKey, "the source keeps its position")
        assertEquals(links.id.toString(), windowState.layout.value.maximizedWindowId)
    }

    @Test
    fun returningFromTheMaximisedLinksWindowMaximisesThePreviousWindowAgain() {
        val (source, _) = showLinkFromMaximisedSource()

        assertTrue(windowControl.returnFromMaximisedLinksWindow())

        assertEquals(source.id, repo.maximizedWindowId)
        assertEquals(listOf(source), repo.visibleWindows)
        assertEquals(source, windowControl.activeWindow)
        assertEquals(verse(1, 1), source.pageManager.currentBible.singleKey)
        assertFalse(windowControl.returnFromMaximisedLinksWindow(), "nothing more to return to")
        assertEquals(source.id, repo.maximizedWindowId)
    }

    @Test
    fun aMaximisedWindowTheUserMaximisedHasNothingToReturnTo() {
        val source = repo.activeWindow
        windowControl.maximiseWindow(source)

        assertFalse(windowControl.returnFromMaximisedLinksWindow())
        assertEquals(source.id, repo.maximizedWindowId)
    }

    @Test
    fun chainedLinksReturnOneStepAtATime() {
        val (source, links) = showLinkFromMaximisedSource()
        // A link tapped in the links window opens in its own (chained) links window (#2469).
        windowControl.showLink(kjv, verse(3, 17))
        val chained = links.targetLinksWindow
        assertEquals(chained.id, repo.maximizedWindowId)
        assertEquals(verse(3, 17), chained.pageManager.currentBible.singleKey)

        assertTrue(windowControl.returnFromMaximisedLinksWindow())
        assertEquals(links.id, repo.maximizedWindowId)
        assertTrue(windowControl.returnFromMaximisedLinksWindow())
        assertEquals(source.id, repo.maximizedWindowId)
    }

    @Test
    fun aSecondLinkFromTheSourceReusesTheReturnPoint() {
        val (source, links) = showLinkFromMaximisedSource()
        windowControl.returnFromMaximisedLinksWindow()
        windowControl.showLink(kjv, verse(3, 17))

        assertEquals(links.id, repo.maximizedWindowId)
        assertTrue(windowControl.returnFromMaximisedLinksWindow())
        assertEquals(source.id, repo.maximizedWindowId)
        assertFalse(windowControl.returnFromMaximisedLinksWindow())
    }

    @Test
    fun closingTheMaximisedLinksWindowReturnsToThePreviousMaximisedWindow() {
        val (source, links) = showLinkFromMaximisedSource()
        assertEquals(links.id, repo.maximizedWindowId, "premise: the links window is maximised")

        windowControl.closeWindow(links)

        assertEquals(source.id, repo.maximizedWindowId)
        assertEquals(listOf(source), repo.visibleWindows)
        assertEquals(source, windowControl.activeWindow)
    }

    @Test
    fun unMaximiseFromTheRailReturnsToThePreviousMaximisedWindowFirst() {
        val (source, _) = showLinkFromMaximisedSource()
        val commands = WindowCommandsImpl(windowControl)

        commands.unMaximise()
        assertEquals(source.id, repo.maximizedWindowId, "the rail's un-maximise leaves the links window first")

        commands.unMaximise()
        assertNull(repo.maximizedWindowId)
    }

    @Test
    fun aClosedWindowOnTheReturnStackIsSkipped() {
        val (source, links) = showLinkFromMaximisedSource()
        windowControl.showLink(kjv, verse(3, 17))
        val chained = links.targetLinksWindow
        assertEquals(chained.id, repo.maximizedWindowId)
        repo.close(links)

        assertTrue(windowControl.returnFromMaximisedLinksWindow())

        assertEquals(source.id, repo.maximizedWindowId, "the closed links window is skipped")
        assertEquals(source, windowControl.activeWindow)
    }

    @Test
    fun aMaximisedLinksWindowWhoseReturnWindowIsGoneUnMaximises() {
        val (source, links) = showLinkFromMaximisedSource()
        repo.close(source)

        assertTrue(windowControl.returnFromMaximisedLinksWindow(), "Back is consumed instead of leaving the user stuck")

        assertNull(repo.maximizedWindowId)
        assertTrue(links in repo.visibleWindows)
        assertFalse(windowControl.returnFromMaximisedLinksWindow())
    }

    @Test
    fun closingTheMaximisedLinksWindowWhoseReturnWindowIsGoneLeavesMaximisedMode() {
        val first = repo.activeWindow
        val other = repo.addNewWindow(first)
        repo.activeWindow = first
        val (source, links) = showLinkFromMaximisedSource()
        repo.close(source)
        assertTrue(windowControl.isWindowRemovable(links), "fixture: another window remains")

        windowControl.closeWindow(links)

        assertNull(repo.maximizedWindowId)
        assertEquals(listOf(other), repo.visibleWindows)
    }

    @Test
    fun maximisingAnotherWindowForgetsTheReturnPoint() {
        val (_, links) = showLinkFromMaximisedSource()
        windowControl.unMaximise()
        assertNull(repo.maximizedWindowId)

        windowControl.maximiseWindow(links)

        assertFalse(windowControl.returnFromMaximisedLinksWindow())
        assertEquals(links.id, repo.maximizedWindowId)
    }
}
