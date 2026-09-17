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

import android.os.Bundle
import android.util.Log
import net.bible.android.control.page.CurrentPageManager
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKeySerialized
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException

/**
 * What a key chooser's RESULT means, applied to a [CurrentPageManager] and to nothing else.
 *
 * **Why it exists (reading-host re-typing T8b, step 0).** The three key-chooser arms of
 * `CurrentGeneralBookPage.startKeyChooser` used to hand their result back through
 * `startActivityForResult(…, STD_REQUEST_CODE)`, and the dispatcher that read it lived — and still
 * lives — only on [MainBibleActivity] (`onActivityResult`). Every one of the four call sites of
 * `startKeyChooser` passes an `ActivityBase`, not a `MainBibleActivity`, so on any other host
 * (`NavHostComposeActivity` once it hosts the reading view, and any Activity at all for
 * `CurrentPageManager`'s auto-open-after-document-switch path) `ActivityBase.onActivityResult`
 * found no `resultByCode[1 - ASYNC_REQUEST_CODE_START]`, fell through to `super`, and the user's
 * selection was discarded in silence. T8a made that reachable by removing the
 * `if (context !is MainBibleActivity) return` that had been suppressing the whole chooser.
 *
 * The arms now `awaitIntent` instead — the shape the StudyPad arm beside them has always used, and
 * the one [net.bible.android.view.activity.base.ActivityBase] resolves for **any** host — and apply
 * the answer here. This object is the ONE implementation: [MainBibleActivity]'s own
 * `STD_REQUEST_CODE` arms (still live for the choosers that have not moved: dictionary, map, the
 * passage grid, `ReadingCommands.composeChooseDocument`) and [ReadingCommands]' extracted
 * `applyChosen*` both delegate here, so the two cannot drift.
 *
 * **Every function takes the [CurrentPageManager] explicitly, and callers pass the page's own.**
 * `windowControl.windowRepository` holds whichever reading host RESUMED last, which is not
 * necessarily the host whose window this chooser was opened for (the identity finding of
 * R6c1/R6d, and the reason T8a item 1 re-homed `workspaceSettings` onto
 * `pageManager.window.windowRepository`). A page always knows the manager that owns it; nothing
 * here has to ask a host or a global.
 */
object KeyChooserResults {
    private const val TAG = "KeyChooserResults"

    /**
     * Classic `MainBibleActivity.applyChosenDocument`'s body, minus the toolbar refresh its caller
     * still owns. The `FakeBookFactory` fallback is the whole reason this is not inlined at the two
     * call sites: it only matters for pseudo-documents, so a second copy could lose it and nothing
     * would notice.
     */
    fun applyChosenDocument(pageManager: CurrentPageManager, bookStr: String?) {
        val book = Books.installed().getBook(bookStr)
            ?: FakeBookFactory.pseudoDocuments.first { it.initials == bookStr }
        pageManager.setCurrentDocument(book)
    }

    /**
     * Classic `MainBibleActivity.openMyDocumentPage` verbatim.
     *
     * A book's key map is a snapshot built when JSword activated it, so it can be out of date with
     * the database — and if it happened to be built while the page table was unreadable, it stays
     * empty for the rest of the session. Rebuild it and retry once before falling back to opening
     * the document without a key.
     */
    fun openMyDocumentPage(pageManager: CurrentPageManager, book: Book, pageKey: String) {
        val key = try {
            book.getKey(pageKey)
        } catch (e: NoSuchKeyException) {
            Log.w(TAG, "Page key '$pageKey' missing from ${book.initials} key map, rebuilding it", e)
            MyDocumentBookManager.refreshDocument(book.initials)
            try {
                book.getKey(pageKey)
            } catch (e2: NoSuchKeyException) {
                Log.e(TAG, "Page key '$pageKey' not found in ${book.initials}, opening book without key", e2)
                pageManager.setCurrentDocument(book)
                return
            }
        }
        pageManager.setCurrentDocumentAndKey(book, key)
    }

    /**
     * What a key chooser handed back, read out of the result extras and named.
     *
     * A value type, and the reading is a pure function of strings ([chosenKeyFrom]), for the reason
     * `deliverNavResult` is split out of `NavResultChannel`: the part that decides WHAT a result
     * means is the part that has to be testable, and it cannot be while it is wrapped around
     * JSword's `Books.installed()` and a live `CurrentPageManager`.
     */
    sealed interface ChosenKey {
        /** [ActivityResultKind.ChooseDocument]: `ChooseDocumentComposeActivity`'s `book` extra. */
        data class Document(val bookInitials: String?) : ChosenKey

        /** [ActivityResultKind.MyDocumentPages]: `NavResultIntents.forMyDocumentPages`'s Selected shape. */
        data class MyDocumentPage(val documentInitials: String, val pageKey: String) : ChosenKey

        /**
         * [ActivityResultKind.GenBookKey], in both of the two shapes
         * `ChooseGeneralBookKeyComposeActivity.buildResult` produces: a serialised `BookAndKey`
         * (an EPUB table-of-contents entry, which carries its OWN document), or a `book`+`key` pair.
         */
        data class GenBookKey(
            val bookAndKeyJson: String?,
            val bookInitials: String?,
            val osisRef: String?,
        ) : ChosenKey

        /**
         * A result of one of the three kinds that names nothing to open —
         * `MyDocumentPagesResult.Saved`, which is `RESULT_OK` with neither key set. Claimed (it IS
         * this chooser's answer) but applied as nothing, exactly as classic's dispatcher did.
         */
        data object Nothing : ChosenKey
    }

    /**
     * Read [kind] plus the result's string extras into a [ChosenKey], or null when the result was
     * not one of the three the general-book key choosers produce.
     *
     * @param extra reads one string extra by name — `Bundle::getString` in production, a map in
     *   a test. Nothing Android or JSword crosses this boundary.
     */
    internal fun chosenKeyFrom(kind: String?, extra: (String) -> String?): ChosenKey? =
        when (ActivityResultKind.fromExtra(kind)) {
            ActivityResultKind.ChooseDocument -> ChosenKey.Document(extra("book"))
            ActivityResultKind.MyDocumentPages -> {
                val initials = extra("documentInitials")
                val pageKey = extra("pageKey")
                if (initials != null && pageKey != null) ChosenKey.MyDocumentPage(initials, pageKey)
                else ChosenKey.Nothing
            }
            ActivityResultKind.GenBookKey -> ChosenKey.GenBookKey(
                bookAndKeyJson = extra("bookAndKey"),
                bookInitials = extra("book"),
                osisRef = extra("key"),
            )
            else -> null
        }

    /**
     * The `book`/`key`/`bookAndKey` triple a [ActivityResultKind.GenBookKey] result carries, read
     * back into the pair `applyChosenGenBookKey` takes. [MainBibleActivity]'s own `GenBookKey` arm
     * reads it through here too, so the two readings of the same extras cannot diverge.
     *
     * The document is returned explicitly rather than derived from the key because it cannot be: an
     * EPUB table-of-contents entry is a `BookAndKey` carrying its OWN document, which is not the
     * page's current document, while every other key carries none.
     */
    fun genBookKeyFrom(extras: Bundle): Pair<Book?, Key> =
        resolveGenBookKey(
            ChosenKey.GenBookKey(
                bookAndKeyJson = extras.getString("bookAndKey"),
                bookInitials = extras.getString("book"),
                osisRef = extras.getString("key"),
            )
        )

    private fun resolveGenBookKey(chosen: ChosenKey.GenBookKey): Pair<Book?, Key> {
        chosen.bookAndKeyJson?.let {
            val bookAndKey = BookAndKeySerialized.fromJSON(it).bookAndKey
            return bookAndKey.document to bookAndKey
        }
        val book = Books.installed().getBook(chosen.bookInitials)
            ?: FakeBookFactory.giveDoesNotExist(chosen.bookInitials!!)
        return book to book.getKey(chosen.osisRef)
    }

    /**
     * Apply a `RESULT_OK` key-chooser result carried by [extras] to [pageManager].
     *
     * **No toolbar push, deliberately** (T8b fix round 1, M6). Classic's `MyDocumentPages` and
     * `MyDocuments` arms call `updateActions()` after their apply
     * (`MainBibleActivity.kt:1776`, `:1790`), and those calls are untouched -- classic still makes
     * them. Nothing here mirrors them because nothing here needs to: the only caller is
     * `CurrentGeneralBookPage`'s awaited chooser, whose host is a Compose reading view that
     * OBSERVES page state rather than being pushed at, and [CurrentPageManager.setCurrentDocument] /
     * [CurrentPageManager.setCurrentDocumentAndKey] already drive `PassageChangeMediator`.
     * `ReadingCommands.applyChosenDocument` keeps its explicit `onToolbarStateMayHaveChanged()`
     * because that one is also the document QUICK SHEET's apply path, which returns no Intent and
     * therefore posts nothing.
     *
     * @return true when [extras] named one of the three kinds the general-book key choosers
     *   produce and it has been applied; false for anything else, so a caller can tell "not mine"
     *   from "applied" rather than assuming.
     */
    fun apply(pageManager: CurrentPageManager, extras: Bundle?): Boolean {
        extras ?: return false
        val chosen = chosenKeyFrom(extras.getString(ActivityResultKind.EXTRA)) { extras.getString(it) }
            ?: return false
        when (chosen) {
            is ChosenKey.Document -> applyChosenDocument(pageManager, chosen.bookInitials)
            is ChosenKey.MyDocumentPage -> {
                val book = Books.installed().getBook(chosen.documentInitials)
                if (book != null) openMyDocumentPage(pageManager, book, chosen.pageKey)
            }
            is ChosenKey.GenBookKey -> {
                val (book, key) = resolveGenBookKey(chosen)
                pageManager.setCurrentDocumentAndKey(book, key)
            }
            ChosenKey.Nothing -> {}
        }
        return true
    }
}
