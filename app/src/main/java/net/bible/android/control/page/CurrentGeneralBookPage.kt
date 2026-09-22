/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.page

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.common.toV11n
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.misc.OsisFragment
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.KeyChooserResults
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.service.sword.mydocument.myDocumentId
import net.bible.service.common.firstBibleDoc
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.DocumentNotFound
import net.bible.service.sword.OsisError
import net.bible.service.sword.StudyPadKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Passage
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import java.lang.Exception

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentGeneralBookPage internal constructor(
    pageManager: CurrentPageManager
) : CachedKeyPage(false, pageManager),
    CurrentPage
{

    override val documentCategory = DocumentCategory.GENERAL_BOOK

    val isSpecialDoc get() = setOf(FakeBookFactory.journalDocument, FakeBookFactory.multiDocument).contains(currentDocument)
    val isStudyPad get() = FakeBookFactory.journalDocument == currentDocument

    override val isSpeakable: Boolean get() = !isSpecialDoc

    /**
     * The workspace settings this page's key chooser seeds and writes back — **this page's own
     * window's**, never the Activity's and never `windowControl`'s.
     *
     * Reading-host re-typing T8a item 1. The body of [startKeyChooser] used to read
     * `context.workspaceSettings`, which only `MainBibleActivity` declares (`:400`), and guarded
     * that read with `if(context !is MainBibleActivity) return` — added by `b718fe85c` (2022) in
     * the same commit that widened the parameter from `MainBibleActivity` to [ActivityBase], i.e. a
     * TYPE artefact, not a policy. On `NavHostComposeActivity` it made every general-book /
     * StudyPad / multi-document key-chooser tap a silent no-op.
     *
     * A `WindowRepository` reached through the HOST (`ReadingHostActivity.hostWindowRepository`)
     * would have worked for three of the four callers; this one is correct for all four and needs
     * no host at all. `Window.windowRepository` is a constructor property and `Window`'s `init`
     * sets `pageManager.window = this`, so the page a key chooser is opened FOR always knows the
     * repository that owns it — which is the repository whose workspace the chooser must edit, even
     * with two live reading hosts (the identity finding of R6c1/R6d). `CurrentPageManager`'s
     * auto-open path in particular calls in with `CurrentActivityHolder.currentActivity`, which may
     * be any Activity at all.
     *
     * A `get()`, not a captured value: `WindowRepository.workspaceSettings` is a `var` that
     * `loadFromDb` replaces, and classic re-read `context.workspaceSettings` on both sides of the
     * `awaitIntent` below. Same two reads, same instants.
     */
    private val workspaceSettings: WorkspaceEntities.WorkspaceSettings
        get() = pageManager.window.windowRepository.workspaceSettings

    /**
     * Open the key chooser for this page's document.
     *
     * Runs on ANY [ActivityBase], exactly as [CurrentBiblePage], [CurrentDictionaryPage],
     * [CurrentMapPage] and [CurrentCommentaryPage] always have — see [workspaceSettings] for the
     * `!is MainBibleActivity` early return that used to stand here and why it is gone. There is no
     * replacement check and no path that cannot proceed, so nothing here returns silently.
     *
     * **All four arms await their own answer** (reading-host re-typing T8b step 0). Three of them
     * used to hand the result back through `startActivityForResult(…, STD_REQUEST_CODE)`, whose
     * dispatcher lives only on `MainBibleActivity.onActivityResult`. Every caller of this function
     * passes an [ActivityBase] — `BibleJavascriptInterface`'s `CtrlKeyB`, `ReadingCommands`'
     * `composeStartKeyChooser` fallback, `CurrentPageManager.setCurrentDocument`'s
     * auto-open-after-switch (which uses `CurrentActivityHolder.currentActivity`, i.e. whatever is
     * resumed) and `MainBibleActivity` itself — so on every other host
     * `ActivityBase.onActivityResult` found no `resultByCode[1 - ASYNC_REQUEST_CODE_START]`, fell
     * through to `super` and DISCARDED the selection in silence. [ActivityBase.awaitIntent] is
     * resolved by `ActivityBase` itself and therefore works on all of them; it is what the StudyPad
     * arm below has always used, and the other three now match it.
     *
     * The answer is applied through [KeyChooserResults], the single implementation
     * `MainBibleActivity`'s surviving `STD_REQUEST_CODE` arms delegate to as well.
     */
    override fun startKeyChooser(context: ActivityBase) {
        context.lifecycleScope.launch(Dispatchers.Main) {
            val doc = currentDocument
            when {
                doc == FakeBookFactory.journalDocument -> {
                    // Screen.ManageLabels is deliberately not in ScreenLauncher.MIGRATED (its `data`
                    // argument is required), and the classic ManageLabelsComposeActivity
                    // ScreenLauncher.targetFor used to resolve it to is gone (nav-graph slices 2+4
                    // Task 7), so this builds the nav-host Intent directly. The "data" extra on the
                    // RESULT is unchanged -- NavResultIntents.forManageLabels still writes it under
                    // that key.
                    val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.STUDYPAD)
                        .applyFrom(workspaceSettings)
                        .toJSON()
                    val result = context.awaitIntent(
                        NavHostComposeActivity.intentFor(context, NavRoutes.manageLabels(data))
                    )
                    if(result.resultCode == Activity.RESULT_OK) {
                        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                        workspaceSettings.updateFrom(resultData)
                    }
                }
                doc == FakeBookFactory.multiDocument ->
                    awaitChosenKey(context, ScreenLauncher.intentFor(context, Screen.ChooseDocument))
                doc?.isMyDocument == true -> {
                    val docId = doc.myDocumentId
                    if (docId != null) {
                        // Screen.MyDocumentPages is deliberately not in ScreenLauncher.MIGRATED (all
                        // three of its route arguments are required), and its classic
                        // MyDocumentPagesComposeActivity is gone (deleted nav-graph slice 4 Task 9)
                        // -- so this builds the nav-host Intent directly rather than through
                        // ScreenLauncher, the same shape the StudyPad branch above already uses for
                        // ManageLabels.
                        awaitChosenKey(
                            context,
                            NavHostComposeActivity.intentFor(
                                context,
                                NavRoutes.myDocumentPages(
                                    documentId = docId.toString(),
                                    documentInitials = doc.initials,
                                    documentName = doc.name,
                                ),
                            ),
                        )
                    }
                }
                else -> awaitChosenKey(context, ScreenLauncher.intentFor(context, Screen.ChooseGeneralBookKey))
            }
        }
    }

    /**
     * Launch one key chooser and apply what it hands back, on whatever [ActivityBase] opened it.
     *
     * **Why the result is applied to [pageManager] and not to `windowControl.activeWindowPageManager`
     * as classic's dispatcher did**: the same reason [workspaceSettings] is read off
     * `pageManager.window.windowRepository`. `windowControl`'s repository is whichever reading host
     * RESUMED last, not necessarily the one this page's window belongs to (the identity finding of
     * R6c1/R6d), while the page a chooser was opened FOR always knows its own manager. In the single
     * -host case the two are the same object, so this is classic's behaviour everywhere classic ran.
     *
     * **The cancel guard is classic's**, ported rather than dropped: `MainBibleActivity
     * .onActivityResult`'s first statement goes back in history when a cancelled `STD_REQUEST_CODE`
     * chooser has left the page with no key at all (a general book switched to but never opened).
     * [ActivityBase.awaitIntent] uses its own request codes, so that statement can no longer see
     * these four arms; it still covers every chooser that has not moved. `key` here is this page's
     * own, for the reason above; classic read `currentPage.key` off the active window.
     *
     * **When it resumes matters, and it is later than the dispatcher's was.** `awaitIntent`
     * completes its `CompletableDeferred` from `onActivityResult`, but this coroutine runs on
     * `Dispatchers.Main` (not `.immediate`), so the continuation is POSTED and runs after
     * `ActivityThread` has finished the same looper message — i.e. after the host's `onResume`.
     * On `NavHostComposeActivity` that is the difference between applying a key into a host that
     * has not yet reclaimed `windowControl.windowRepository`, re-declared itself foreground or
     * re-armed its bootstrap bridge, and one that has: the `AddHistoryItem` that
     * `setCurrentDocumentAndKey` posts is read by `HistoryManager.createHistoryItem` through
     * `ReadingViewVisibility.isVisible`, which is false until `ReadingHostPresence` says this host
     * is foreground. Classic needed `CurrentActivityHolder.activate` +
     * `ReadingHostPresence.setForeground` + `ReadingViewVisibility.setActivityVisible` in its
     * dispatcher precisely because `onActivityResult` runs BEFORE `onResume`; awaiting needs none of
     * them, and adds no pre-composition producer of history items — so
     * [net.bible.sharedcore.reading.ReadingViewVisibility]'s bootstrap-bridge invariant is untouched.
     */
    private suspend fun awaitChosenKey(context: ActivityBase, intent: Intent) {
        val result = context.awaitIntent(intent)
        if (result.resultCode == Activity.RESULT_CANCELED) {
            if (key == null) context.goBackInHistory()
            return
        }
        KeyChooserResults.apply(pageManager, result.data?.extras)
    }


    private val defaultBibleDoc get() = pageManager.currentBible.currentDocument ?: firstBibleDoc

    override val currentPageContent: Document
        get() {
            return when(val key = key) {
                is StudyPadKey -> {
                    val bookmarks = pageManager.bookmarkControl.getBibleBookmarksWithLabel(key.label, addData = true)
                    val genericBookmarks = pageManager.bookmarkControl.getGenericBookmarksWithLabel(key.label, addData = true)
                    val journalTextEntries = pageManager.bookmarkControl.getStudyPadTextEntriesForLabel(key.label)
                    val bookmarkToLabels = bookmarks.mapNotNull { pageManager.bookmarkControl.getBookmarkToLabel(it, key.label.id) as BookmarkEntities.BibleBookmarkToLabel? }
                    val genericBookmarkToLabels = genericBookmarks.mapNotNull { pageManager.bookmarkControl.getBookmarkToLabel(it, key.label.id) as BookmarkEntities.GenericBookmarkToLabel? }
                    val entryId = key.entryId
                    StudyPadDocument(key.label, entryId, bookmarks, genericBookmarks, bookmarkToLabels, genericBookmarkToLabels, journalTextEntries)
                }
                is BookAndKeyList -> {
                    // A multi-document key is ASSEMBLED, not asked for: a Strong's tap builds one
                    // Robinson entry per installed morphology document (LinkControl.kt:329). A
                    // document that is not installed therefore contributes an error card with its
                    // own download link that the user never requested -- drop it instead.
                    //
                    // A key genuinely missing from an INSTALLED document is different: that is
                    // information, not noise, and must still produce its card.
                    //
                    // Both cases throw the SAME exception class, DocumentNotFound
                    // (SwordContentFacade.kt:170 for "not installed", :174 and :359 for "key not in
                    // document") -- so the two cannot be told apart by catch-type alone. dropUninstalled
                    // re-checks Books.installed() for the specific document inside the catch instead.
                    fun fragmentFor(bookAndKey: BookAndKey, dropUninstalled: Boolean): OsisFragment? {
                        val doc = bookAndKey.document ?: defaultBibleDoc
                        var k = bookAndKey.key
                        return try {
                            if(doc is SwordBook) {
                                k = when(k) {
                                    is Passage -> k.toV11n(doc.versification)
                                    is VerseRange -> k.toV11n(doc.versification)
                                    is Verse -> k.toV11n(doc.versification)
                                    else -> k
                                }
                            }
                            OsisFragment(SwordContentFacade.readOsisFragment(doc, k), k, doc)
                        } catch (e: OsisError) {
                            if(dropUninstalled && e is DocumentNotFound && Books.installed().getBook(doc.initials) == null) {
                                Log.i(TAG, "Dropping an uninstalled document from an assembled key: ${doc.initials}")
                                null
                            } else {
                                Log.e(TAG, "Fragment could not be read")
                                OsisFragment(e.xml, k, doc)
                            }
                        }
                    }
                    val bookAndKeys = key.filterIsInstance<BookAndKey>()
                    val frags = bookAndKeys.mapNotNull { fragmentFor(it, dropUninstalled = true) }
                    // If everything dropped (every document in the assembled key is uninstalled),
                    // fall back to today's behaviour -- showing every card -- rather than an empty page.
                    val shown = frags.ifEmpty { bookAndKeys.mapNotNull { fragmentFor(it, dropUninstalled = false) } }
                    MultiFragmentDocument(shown, state = pageManager.jsState)
                }
                else -> super.currentPageContent
            }
        }

    /** set key without notification
     *
     * @param key
     */
    override fun doSetKey(key: Key?) {
        _key = key
    }

    override fun next() {
        val key = key
        when (currentDocument) {
            FakeBookFactory.journalDocument -> {
                val nextLabel = pageManager.bookmarkControl.getNextLabel((key as StudyPadKey).label)
                setKey(StudyPadKey(nextLabel))
            }
            FakeBookFactory.multiDocument -> {}
            else -> {
                setKey(getKeyPlus(1))
            }
        }
    }

    override fun previous() {
        val key = key
        when (currentDocument) {
            FakeBookFactory.journalDocument -> {
                val nextLabel = pageManager.bookmarkControl.getPrevLabel((key as StudyPadKey).label)
                setKey(StudyPadKey(nextLabel))
            }
            FakeBookFactory.multiDocument -> {}
            else -> {
                setKey(getKeyPlus(-1))
            }
        }
    }

    override val isSingleKey = true
	override val key: Key? get() = _key

	/** can we enable the main menu search button
     */
    override val isSearchable: Boolean get() = currentDocument?.isEpub == true

    override val isSyncable: Boolean = false

    override fun restoreFrom(entity: WorkspaceEntities.Page?) {
        when (entity?.document) {
            FakeBookFactory.journalDocument.initials -> {
                val splitted = entity!!.key?.split(":")?: return
                if(splitted.size != 2) return
                val id = splitted[1]
                val label = pageManager.bookmarkControl.labelById(IdType(id))
                if (label != null) {
                    doSetKey(StudyPadKey(label))
                    localSetCurrentDocument(FakeBookFactory.journalDocument)
                    anchorOrdinal = entity.anchorOrdinal?.let { OrdinalRange(it) }
                }
            }
            FakeBookFactory.multiDocument.initials -> {
                val refs = entity!!.key!!.split("||").map { it.split(":") }.mapNotNull {
                    try {
                        val isUnspecifiedDoc = it[0] == "null"
                        val book: Book? = if(isUnspecifiedDoc) pageManager.currentBible.currentDocument else Books.installed().getBook(it[0])
                        val key = if (book is SwordBook) {
                            PassageKeyFactory.instance().getKey(book.versification, it[1])
                        } else {
                            book?.getKey(it[1])
                        }
                        if(book == null || key == null) null else BookAndKey(key, if(isUnspecifiedDoc) null else book)
                    } catch (e: Exception) {
                        null
                    }
                }

                val key = BookAndKeyList()
                for (ref in refs) {
                    key.addAll(ref)
                }
                doSetKey(key)
                localSetCurrentDocument(FakeBookFactory.multiDocument)
            }
            else -> {
                super.restoreFrom(entity)
            }
        }
    }

    companion object {
        private const val TAG = "CurrentGeneralBookPage"
    }
}
