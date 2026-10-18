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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host.slots

import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.service.sword.BookAndKeyList
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

/**
 * What the Speak settings sheet needs from the platform: a way into the system text-to-speech
 * settings. Android starts the TTS settings intent (falling back to the general settings); a
 * platform without such a screen can no-op.
 */
@kotlin.native.HiddenFromObjC
interface SpeakSettingsPlatform {
    /** Opens the platform's text-to-speech settings screen. */
    fun openSystemTtsSettings()
}

/**
 * What the quick sheets ask the platform to do once the user has picked something. Each member is
 * the body the matching full-screen chooser result runs, so the sheet and the screen cannot drift
 * apart.
 */
@kotlin.native.HiddenFromObjC
interface ReadingQuickActions {
    /**
     * Switches to workspace [id] from the quick sheet. Saves the outgoing workspace first (the quick
     * sheet never pauses the activity, so nothing else flushes it), then switches.
     */
    fun quickSwitchToWorkspace(id: String)

    /** Applies the document [bookStr] (an initials string, or null) chosen in the Documents sheet to the active window. */
    fun applyChosenDocument(bookStr: String?)

    /**
     * Applies a general-book, map or dictionary [key] chosen in a key-chooser sheet to the active
     * window. [book] is explicit because an EPUB table-of-contents key carries its own document.
     */
    fun applyChosenGenBookKey(book: Book?, key: Key)
}

/**
 * What the reading view's search needs from the platform: the document being read, the JSword
 * documents behind result rows, and the two ways a result is shown. Android backs it with the
 * window/link controllers; another platform supplies its own.
 */
@kotlin.native.HiddenFromObjC
interface ReadingSearchPlatform {
    /** The document the active window shows, or null (an error page). Read fresh on every call. */
    fun currentDocument(): Book?

    /** The active window's book name for the search request (`SearchControl.currentBookName`). */
    fun currentBookName(): String

    /** The JSword `Book` to portable [SearchDocumentInfo] mapping (category, EPUB flag, index state). */
    fun documentInfo(book: Book?): SearchDocumentInfo?

    /** The installed document with [initials] (any kind), or null. */
    fun documentByInitials(initials: String): Book?

    /** The installed SWORD document with [initials] a Bible result row points into, or null. */
    fun swordBookByInitials(initials: String): Book?

    /** The installed Bibles as (initials, abbreviation), sorted by abbreviation. */
    fun installedBibles(): List<Pair<String, String>>

    /**
     * Typing a Bible reference ("1 joh 3 16") jumps there in the active window; returns whether
     * [query] was a reference and the navigation happened.
     */
    fun tryOpenReference(query: String): Boolean

    /** Makes [book] at [key] the active window's page (no new window, no history pop). */
    fun showInActiveWindow(book: Book, key: Key)

    /** Shows [list] (the "open results in a window" multi-document) in the links window. */
    fun showInLinksWindow(list: BookAndKeyList)
}

/**
 * The index-build job feed: JSword's global `JobManager` on Android. [subscribe] registers ONE
 * listener and returns the call that removes it; the callback gets an identity key for the job and a
 * lazy "is it finished" read (evaluated on the host's own thread, not the listener's).
 */
@kotlin.native.HiddenFromObjC
interface SearchIndexJobs {
    /** Starts delivering work events to [onEvent]; the returned function stops it. */
    fun subscribe(onEvent: (job: Any, isFinished: () -> Boolean) -> Unit): () -> Unit

    /** A snapshot of every running job as progress rows. */
    fun snapshot(): List<ProgressJob>

    /** Whether no job is running anywhere. */
    fun isEveryJobFinished(): Boolean
}
