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

package net.bible.android.view.activity.page

import android.content.ClipData
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.progress.ProgressControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.event.UserMessages
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.MultiFragmentDocument
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.versification.toVerseRange
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.EditAction
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.progress.ReadingSource
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.service.common.ReadingProgressSettings
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.sword.SwordContentFacade
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.bookmarksMyNotesPlaylist
import net.bible.service.common.displayName
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mybible.myBibleIntToBibleBook
import net.bible.service.sword.mysword.mySwordIntToBibleBook
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.NoteEditorEntityType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.lang.ClassCastException


/**
 * How [BibleJavascriptInterface.refChooserDialog] renders the chosen verse for the JS side: SHORT
 * book-name form, whatever `BookName.isFullBookName()` happens to be globally, and that global
 * restored afterwards. Verbatim from the version that read the chooser Activity's result — the
 * format is part of the JS contract, not incidental — extracted only so it can be tested without a
 * WebView (`RefChooserSheetTest`).
 *
 * `null` (a dismissed chooser) renders as the empty string, which is what the JS promise resolves
 * to when the user picks nothing.
 */
internal fun refChooserVerseName(verseStr: String?): String {
    val verse = if(verseStr == null) null else VerseFactory.fromString(KJVA, verseStr)

    return synchronized(BookName::class.java) {
        val oldValue = BookName.isFullBookName()
        BookName.setFullBookName(false)
        try {
            verse?.getName() ?: ""
        } finally {
            BookName.setFullBookName(oldValue)
        }
    }
}

class BibleJavascriptInterface(
	private val bibleView: BibleView
) : KoinComponent {
    private val currentPageManager: CurrentPageManager get() = bibleView.window.pageManager
    val linkControl get() = bibleView.linkControl
    val bookmarkControl get() = bibleView.bookmarkControl
    val downloadControl get() = bibleView.downloadControl

    /**
     * R6a: the host as a plain Android Activity. This class used to hold `bibleView
     * .mainBibleActivity` and reach 13 distinct members through it; nine of those are the ordinary
     * Activity API (`startActivity`, `startActivityForResult`, `getString`, `lifecycleScope`, an
     * `AlertDialog.Builder` receiver) or library helpers that demand an `Activity`/`ActivityBase`
     * BY SIGNATURE — `CommonUtils.showHelpDialog`, `BackupControl.saveOrShare`,
     * `SearchControl.getSearchIntent`, `CurrentPage.startKeyChooser`. Those keep their spelling
     * against [ActivityBase], which BOTH reading hosts really are, so nothing is cast and nothing
     * can fail at Task 8. The four
     * that were genuinely `MainBibleActivity`-only went into [BibleViewHostCallbacks]; two more
     * (`speakControl`, `searchControl`) are Koin singletons and are injected here instead of
     * borrowed off the Activity, which is R1's move.
     */
    private val hostActivity: ActivityBase get() = bibleView.hostCallbacks.hostActivity
    private val hostCallbacks: BibleViewHostCallbacks get() = bibleView.hostCallbacks
    private val speakControl: SpeakControl by inject()
    private val searchControl: SearchControl by inject()
    var notificationsEnabled = false
    val scope get() = hostActivity.lifecycleScope

    @JavascriptInterface
    fun scrolledToOrdinal(keyStr: String, ordinal: Int) {
        val doc = bibleView.firstDocument
        if (doc is BibleDocument || doc is MyNotesDocument) {
            currentPageManager.currentBible.setCurrentVerseOrdinal(ordinal,
                when (doc) {
                    is BibleDocument -> (bibleView.initialKey as Verse).versification
                    is MyNotesDocument -> KJVA
                    else -> throw RuntimeException("Unsupported doc")
                }, bibleView.window)
        } else if(doc is OsisDocument || doc is StudyPadDocument) {
            val curPage = currentPageManager.currentPage
            // Commentaries (CurrentCommentaryPage) and general books (CurrentGeneralBookPage) are
            // both addressed by a single osisRef key. When infinite scroll brings a new block/entry
            // into view its document carries a different osisRef, so update the page key and notify
            // listeners (title bar / synced windows). Bible & MyNotes are handled above by ordinal.
            if((curPage is CurrentGeneralBookPage || curPage is CurrentCommentaryPage) && doc is OsisDocument) {
                if(curPage.updateKeyFromScrolledOsisRef(keyStr)) {
                    PassageChangeMediator.onCurrentVerseChanged(bibleView.window)
                }
            }
            curPage.anchorOrdinal = OrdinalRange(ordinal)
        }
    }

    @JavascriptInterface
    fun setClientReady() {
        Log.i(TAG, "set client ready")

        bibleView.setClientReady()
    }

    @JavascriptInterface
    fun setLimitAmbiguousModalSize(value: Boolean) {
        Log.i(TAG, "setLimitAmbiguousModalSize")
        bibleView.workspaceSettings.limitAmbiguousModalSize = value
        WorkspaceChanges.notifySettingsEdited()
    }

    @JavascriptInterface
    fun requestMoreToBeginning(callId: Long) {
        Log.i(TAG, "Request more text at top")
        bibleView.requestMoreToBeginning(callId)
    }

    @JavascriptInterface
    fun requestMoreToEnd(callId: Long) {
        Log.i(TAG, "Request more text at end")
        bibleView.requestMoreToEnd(callId)
    }

    @JavascriptInterface
    fun goToNextChapter() {
        Log.i(TAG, "Go to next chapter")
        scope.launch(Dispatchers.Main) {
            try {
                currentPageManager.currentPage.next()
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to next chapter", e)
            }
        }
    }

    @JavascriptInterface
    fun goToPreviousChapter() {
        Log.i(TAG, "Go to previous chapter")
        scope.launch(Dispatchers.Main) {
            try {
                currentPageManager.currentPage.previous()
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to previous chapter", e)
            }
        }
    }

    @JavascriptInterface
    fun parseRef(callId: Long, s: String) {
        Log.i(TAG, "Request more text at end")
        bibleView.parseRef(callId, s)
    }

    /**
     * The JS reference chooser (`deferredCall(... window.android.refChooserDialog(callId))`).
     *
     * nav-graph slice 7 §6.3: this opens the reading view's OWN Grid quick sheet and is resolved by
     * the sheet's selection callback, replacing a full-screen chooser launched through
     * `ScreenLauncher` and awaited as an Activity result. That removes one of the three documented
     * paths that bypassed `MainBibleActivity.composeStartKeyChooser`'s sheet gate.
     *
     * Two behaviours the old Intent forced are forced on the sheet instead, or the JS chooser would
     * change under the user: verse-level drill-down (spec §6.1.1 — the ordinary sheet follows a
     * preference that is off by default, and a chooser that stops at a chapter cannot answer this
     * call) and scripture-only books, which the sheet's Grid arm already hard-codes.
     *
     * The host is declared nullable so SOME null handling is compiler-mandated, but a `BibleView`
     * only ever exists inside a mounted host's pane, so that arm is unreachable in practice; it
     * answers JS with no verse rather than launching anything, the same shape as round 15b's
     * History sheet.
     */
    @JavascriptInterface
    fun refChooserDialog(callId: Long) {
        scope.launch {
            val verseStr = hostCallbacks.composeReadingViewHost()
                ?.openVerseChooserSheetForResult()
                ?.await()

            val verseName = refChooserVerseName(verseStr)

            bibleView.executeJavascriptOnUiThread("bibleView.response($callId, '$verseName');")
        }
    }

    @JavascriptInterface
    fun saveBookmarkNote(bookmarkId: String, note: String?) {
        bookmarkControl.saveBibleBookmarkNote(IdType(bookmarkId), if(note?.trim()?.isEmpty() == true) null else note)
    }

    @JavascriptInterface
    fun saveGenericBookmarkNote(bookmarkId: String, note: String?) {
        bookmarkControl.saveGenericBookmarkNote(IdType(bookmarkId), if(note?.trim()?.isEmpty() == true) null else note)
    }

    @JavascriptInterface
    fun removeBookmark(bookmarkId: String) {
        bookmarkControl.deleteBibleBookmarksById(listOf(IdType(bookmarkId)))
    }

    @JavascriptInterface
    fun removeGenericBookmark(bookmarkId: String) {
        bookmarkControl.deleteGenericBookmarksById(listOf(IdType(bookmarkId)))
    }

    @JavascriptInterface
    fun assignLabels(bookmarkId: String) {
        val bookmark = bookmarkControl.bibleBookmarkById(IdType(bookmarkId))!!
        bibleView.assignLabels(bookmark)
    }

    @JavascriptInterface
    fun genericAssignLabels(bookmarkId: String) {
        val bookmark = bookmarkControl.genericBookmarkById(IdType(bookmarkId))!!
        bibleView.assignLabels(bookmark)
    }

    @JavascriptInterface
    fun setBookmarkEditAction(bookmarkId: String, valueStr: String) {
        val editAction = json.decodeFromString<EditAction>(serializer(), valueStr)
        bookmarkControl.updateBookmarkEditAction(IdType(bookmarkId), editAction)
    }

    @JavascriptInterface
    fun console(loggerName: String, message: String) {
        Log.i(TAG, "Console[$loggerName] $message")
    }

    @JavascriptInterface
    fun selectionCleared() {
        Log.i(TAG, "Selection cleared!")
        bibleView.stopSelection()
    }

    @JavascriptInterface
    fun reportInputFocus(newValue: Boolean) {
        Log.i(TAG, "Focus mode now $newValue")
    }

    @JavascriptInterface
    fun openEpubLink(bookInitials: String, toKeyStr: String, toId: String) {
        val book = Books.installed().getBook(bookInitials) as SwordGenBook
        val backend = book.backend as EpubBackend
        val key = backend.getKey(toKeyStr, toId) ?: return
        scope.launch(Dispatchers.Main) {
            linkControl.showLink(book, BookAndKey(key, book, htmlId = toId))
        }
    }

    @JavascriptInterface
    fun openExternalLink(link: String) {
        when {
            link.startsWith("B:") -> {
                // MyBible links
                val (book, rest) = link.split(" ", limit=2)
                val bookInt = book.split(":")[1].toInt()
                val bibleBook = myBibleIntToBibleBook[bookInt]?: return
                val lnk = "${bibleBook.osis} $rest"
                val bibleLink = BibleView.BibleLink("content", target=lnk)
                scope.launch(Dispatchers.Main) {
                    linkControl.loadApplicationUrl(bibleLink)
                }
            }
            link.startsWith("S:") -> {
                // MyBible strongs
                val (prefix, rest) = link.split(":", limit=2)
                val bibleLink = BibleView.BibleLink("strong", target=rest)
                scope.launch(Dispatchers.Main) {
                    linkControl.loadApplicationUrl(bibleLink)
                }
            }
            link.startsWith("#b") -> {
                // MySword bible links
                val rest = link.substring(2)
                val (bookInt, chapInt, verInt) = rest.split(".").map { it.toInt() }
                val bibleBook = mySwordIntToBibleBook[bookInt]?: return
                val lnk = "${bibleBook.osis}.$chapInt.$verInt"
                val bibleLink = BibleView.BibleLink("content", target=lnk)
                scope.launch(Dispatchers.Main) {
                    linkControl.loadApplicationUrl(bibleLink)
                }
            }
            link.startsWith("#s") || link.startsWith("#d") -> {
                // MySword strongs links
                val rest = link.substring(2)
                val bibleLink = BibleView.BibleLink("strong", target=rest)
                scope.launch(Dispatchers.Main) {
                    linkControl.loadApplicationUrl(bibleLink)
                }
            }
            link.startsWith("sword://") || link.startsWith("osis:") -> {
                // Internal app links (e.g. sword://CalvinCommentaries/Eph.1.11)
                val bibleLink = BibleView.BibleLink("sword", target=link)
                scope.launch(Dispatchers.Main) {
                    linkControl.loadApplicationUrl(bibleLink)
                }
            }
            link.startsWith("strongs://") -> {
                // Document-independent Strong's links (e.g. strongs://G2316, strongs://H430)
                val ref = link.removePrefix("strongs://")
                val bibleLink = BibleView.BibleLink("strong", target=ref)
                scope.launch(Dispatchers.Main) {
                    linkControl.loadApplicationUrl(bibleLink)
                }
            }
            link.startsWith("morphology://") -> {
                // Document-independent morphology links (e.g. morphology://robinson/V-PAI-3S)
                val rest = link.removePrefix("morphology://")
                val slashIdx = rest.indexOf('/')
                val morphType = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
                val code = if (slashIdx >= 0) rest.substring(slashIdx + 1) else ""
                val bibleLink = BibleView.BibleLink(morphType, target=code)
                scope.launch(Dispatchers.Main) {
                    linkControl.loadApplicationUrl(bibleLink)
                }
            }
            else -> {
                CommonUtils.openLink(link, forceAsk=true)
            }
        }
    }

    @JavascriptInterface
    fun openDownloads() {
        if (!downloadControl.checkDownloadOkay()) return
        val intent = NavHostComposeActivity.intentFor(hostActivity, NavRoutes.download(addons = true))
        hostActivity.startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
    }

    @JavascriptInterface
    fun setEditing(enabled: Boolean) {
        bibleView.editingTextInJs = enabled
    }

    @JavascriptInterface
    fun createNewStudyPadEntry(labelId: String, entryType: String, afterEntryId: String) {
        val entryOrderNumber: Int = when (entryType) {
            "bookmark" -> bookmarkControl.getBibleBookmarkToLabel(IdType(afterEntryId), IdType(labelId))!!.orderNumber
            "generic-bookmark" -> bookmarkControl.getGenericBookmarkToLabel(IdType(afterEntryId), IdType(labelId))!!.orderNumber
            "journal" -> bookmarkControl.getStudyPadById(IdType(afterEntryId))!!.orderNumber
            "none" -> -1
            else -> throw RuntimeException("Illegal entry type")
        }
        bookmarkControl.createStudyPadEntry(IdType(labelId), entryOrderNumber)
    }

    @JavascriptInterface
    fun deleteStudyPadEntry(studyPadId: String) = bookmarkControl.deleteStudyPadTextEntry(IdType(studyPadId))

    @JavascriptInterface
    fun removeBookmarkLabel(bookmarkId: String, labelId: String) = bookmarkControl.removeBibleBookmarkLabel(IdType(bookmarkId), IdType(labelId))

    @JavascriptInterface
    fun removeGenericBookmarkLabel(bookmarkId: String, labelId: String) = bookmarkControl.removeGenericBookmarkLabel(IdType(bookmarkId), IdType(labelId))

    @JavascriptInterface
    fun updateOrderNumber(labelId: String, data: String) {
        val deserialized: Map<String, List<Pair<String, Int>>> = json.decodeFromString(serializer(), data)
        val studyPadTextItems = deserialized["studyPadTextItems"]!!.map { bookmarkControl.getStudyPadById(IdType(it.first))!!.apply { orderNumber = it.second } }
        val bookmarksToLabels = deserialized["bookmarks"]!!.map { bookmarkControl.getBibleBookmarkToLabel(IdType(it.first), IdType(labelId))!!.apply { orderNumber = it.second } }
        val genericBookmarksToLabels = deserialized["genericBookmarks"]!!.map { bookmarkControl.getGenericBookmarkToLabel(IdType(it.first), IdType(labelId))!!.apply { orderNumber = it.second } }
        bookmarkControl.updateOrderNumbers(IdType(labelId), bookmarksToLabels, genericBookmarksToLabels, studyPadTextItems)
    }

    @JavascriptInterface
    fun setStudyPadCursor(labelId: String, orderNumber: Int) {
        val windowRepository = bookmarkControl.windowControl.windowRepository
        val workspaceSettings = windowRepository.workspaceSettings
        workspaceSettings.studyPadCursors[IdType(labelId)] = orderNumber
        WorkspaceChanges.notifySettingsEdited()
    }

    @JavascriptInterface
    fun getActiveLanguages(): String {
        //Get the languages for each of the installed bibles and return the language codes as a json list.
        val languages = SwordDocumentFacade.bibles.map { "\"" + it.bookMetaData.language.code + "\""}
        return "[" + languages.distinct().joinToString(",") + "]"
    }

    @JavascriptInterface
    fun toast(text: String) {
        UserMessages.toast(text)
    }

    @JavascriptInterface
    fun updateStudyPadTextEntry(data: String) {
        val entry: BookmarkEntities.StudyPadTextEntryWithText = json.decodeFromString(serializer(), data)
        bookmarkControl.updateStudyPadTextEntry(entry.studyPadTextEntryEntity)
    }

    @JavascriptInterface
    fun updateStudyPadTextEntryText(id: String, text: String) {
        bookmarkControl.updateStudyPadTextEntryText(IdType(id), text)
    }

    @JavascriptInterface
    fun updateBookmarkToLabel(data: String) {
        val entry: BookmarkEntities.BibleBookmarkToLabel = json.decodeFromString(serializer(), data)
        bookmarkControl.updateBibleBookmarkTimestamp(entry.bookmarkId)
        bookmarkControl.updateBookmarkToLabel(entry)
    }

    @JavascriptInterface
    fun updateGenericBookmarkToLabel(data: String) {
        val entry: BookmarkEntities.GenericBookmarkToLabel = json.decodeFromString(serializer(), data)
        bookmarkControl.updateGenericBookmarkTimestamp(entry.bookmarkId)
        bookmarkControl.updateBookmarkToLabel(entry)
    }

    @JavascriptInterface
    fun shareBookmarkVerse(bookmarkId: String) {
        val bookmark = bookmarkControl.bibleBookmarkById(IdType(bookmarkId))!!
        scope.launch(Dispatchers.Main) {
            hostCallbacks.composeReadingViewHost()?.showShareSheet(
                SwordContentFacade.buildShareVersesInput(Selection(bookmark))
            )
        }
    }

    private fun positiveOrNull(value: Int): Int? {
        if(value < 0) return null
        return value
    }

    @JavascriptInterface
    fun shareVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            hostCallbacks.composeReadingViewHost()?.showShareSheet(
                SwordContentFacade.buildShareVersesInput(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)))
            )
        }
    }

    @JavascriptInterface
    fun copyVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            bibleView.copySelectionToClipboard(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)))
        }
    }

    @JavascriptInterface
    fun addBookmark(bookInitials: String, startOrdinal: Int, endOrdinal: Int, addNote: Boolean) {
        bibleView.makeBookmark(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)), true, addNote)
    }

    @JavascriptInterface
    fun addGenericBookmark(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int, addNote: Boolean) {
        bibleView.makeBookmark(Selection(bookInitials, osisRef, startOrdinal, positiveOrNull(endOrdinal)), true, addNote)
    }

    @JavascriptInterface
    fun addParagraphBreakBookmark(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        bibleView.addParagraphBreakBookmark(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)))
    }

    @JavascriptInterface
    fun addGenericParagraphBreakBookmark(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int) {
        bibleView.addParagraphBreakBookmark(Selection(bookInitials, osisRef, startOrdinal, positiveOrNull(endOrdinal)))
    }

    @JavascriptInterface
    fun createWholePageBookmark(bookInitials: String, bookKey: String) {
        bibleView.createWholePageBookmark(bookInitials, bookKey)
    }

    @JavascriptInterface
    fun compare(bookInitials: String, verseOrdinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            bibleView.compareSelection(Selection(bookInitials, verseOrdinal, positiveOrNull(endOrdinal)))
        }
    }

    @JavascriptInterface
    fun memorize(bookInitials: String, verseOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, verseOrdinal, endOrdinal)
        if (verseRange != null) {
            ProgressControl.addMemorizationTargetIfNeeded(verseRange)
        }
        scope.launch(Dispatchers.Main) {
            bibleView.memorizeSelection(Selection(bookInitials, verseOrdinal, positiveOrNull(endOrdinal)))
        }
    }

    private fun verseRangeFromOrdinals(bookInitials: String, startOrdinal: Int, endOrdinal: Int): VerseRange? {
        val book = Books.installed().getBook(bookInitials) ?: return null
        val v11n = (book as? AbstractPassageBook)?.versification ?: return null
        val effectiveEnd = if (endOrdinal > 0) endOrdinal else startOrdinal
        return VerseRange(v11n, Verse(v11n, startOrdinal), Verse(v11n, effectiveEnd))
    }

    @JavascriptInterface
    fun markAsMemorized(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, startOrdinal, endOrdinal) ?: return
        ProgressControl.markVerseMemorized(verseRange)
    }

    @JavascriptInterface
    fun addMemorizationTarget(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, startOrdinal, endOrdinal) ?: return
        ProgressControl.addMemorizationTarget(verseRange)
    }

    @JavascriptInterface
    fun unmarkMemorized(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, startOrdinal, endOrdinal) ?: return
        ProgressControl.unmarkVerseMemorized(verseRange)
    }

    @JavascriptInterface
    fun removeMemorizationTarget(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        val verseRange = verseRangeFromOrdinals(bookInitials, startOrdinal, endOrdinal) ?: return
        ProgressControl.removeMemorizationTargetByRange(verseRange)
    }

    @JavascriptInterface
    fun setReadingProgressSettings(json: String) {
        ReadingProgressSettings.setBundleFromJson(json)
        ReadingProgressSettings.notifyChanged()
    }

    /**
     * The ONLY caller that names a tab. `Screen.ReadingProgress` is MIGRATED and its MIGRATED route
     * is the argument-FREE one, so a `ScreenLauncher.intentFor(...).putExtra(EXTRA_TAB, tab)` would
     * silently drop the tab — the route is the only thing `NavHostComposeActivity` reads. The route
     * is therefore built directly, as `NavHostRoutingGuardTest
     * .migratedScreenArgumentIsNeverDroppedByAPutExtra` requires.
     *
     * `startActivityForResult(..., STD_REQUEST_CODE)` is unchanged and still matters: this screen
     * returns `ActivityResultKind.ReadingProgress` to `MainBibleActivity.onActivityResult`.
     */
    @JavascriptInterface
    fun openReadingProgress(tab: Int) {
        scope.launch(Dispatchers.Main) {
            val intent = NavHostComposeActivity.intentFor(hostActivity, NavRoutes.readingProgress(tab))
            hostActivity.startActivityForResult(intent, STD_REQUEST_CODE)
        }
    }

    /**
     * Still `startActivityForResult` although this screen never calls `setResult`, so it always
     * returns `RESULT_CANCELED` into `MainBibleActivity.kt:2882-2890`'s early branch. That is
     * pre-existing, harmless and deliberately NOT changed here.
     */
    @JavascriptInterface
    fun openReadingProgressSettings() {
        scope.launch(Dispatchers.Main) {
            val intent = ScreenLauncher.intentFor(hostActivity, Screen.ReadingProgressSettings)
            hostActivity.startActivityForResult(intent, STD_REQUEST_CODE)
        }
    }

    /**
     * Show a help dialog for a Vue-side view. The [scopeKey] string is
     * resolved server-side to a (title, message, helpUrl) triple so
     * that the JS side cannot inject arbitrary URLs.
     */
    @JavascriptInterface
    fun showHelpDialog(scopeKey: String) {
        val (titleRes, messageRes, helpUrl) = when (scopeKey) {
            "memorize" -> Triple(R.string.help, R.string.help_memorize_text, DocsLinks.page("memorize"))
            else -> {
                Log.w(TAG, "Unknown help scope: $scopeKey")
                return
            }
        }
        scope.launch(Dispatchers.Main) {
            CommonUtils.showHelpDialog(hostActivity, titleRes, messageRes, helpUrl)
        }
    }

    /**
     * Records a new read-history row for this chapter. Always inserts — JS-side state
     * (autoTrackDone) prevents duplicate auto-track inserts. The `source` parameter is
     * the string name of a [ReadingSource] value; unknown strings fall back to MANUAL.
     */
    @JavascriptInterface
    fun recordChapterRead(bookInitials: String, startOrdinal: Int, chapter: Int, source: String) {
        val book = Books.installed().getBook(bookInitials) ?: return
        val v11n = (book as? AbstractPassageBook)?.versification ?: return
        val verse = Verse(v11n, startOrdinal)
        val readingSource = try { ReadingSource.valueOf(source) } catch (_: Exception) { ReadingSource.MANUAL }
        ProgressControl.recordChapterRead(v11n, verse.book, chapter, bookInitials, readingSource)
    }

    @JavascriptInterface
    fun openChapterReadHistory(bookInitials: String, startOrdinal: Int, chapter: Int) {
        val book = Books.installed().getBook(bookInitials) ?: return
        val v11n = (book as? AbstractPassageBook)?.versification ?: return
        val kjvBook = Verse(v11n, startOrdinal).toV11n(KJVA).book
        scope.launch(Dispatchers.Main) {
            hostCallbacks.composeReadingViewHost()?.showReadHistorySheet(kjvBook.name, chapter)
        }
    }

    @JavascriptInterface
    fun saveState(newState: String) {
        bibleView.window.pageManager.jsState = newState
    }

    @JavascriptInterface
    fun openStudyPad(labelId: String, bookmarkId: String) {
        scope.launch(Dispatchers.Main) {
            linkControl.openStudyPad(IdType(labelId), IdType(bookmarkId))
        }
    }

    @JavascriptInterface
    fun openMyNotes(v11n: String, ordinal: Int) {
        scope.launch(Dispatchers.Main) {
            linkControl.openMyNotes(v11n, ordinal)
        }
    }

    @JavascriptInterface
    fun openAiDocPage(documentInitials: String, pageKey: String) {
        scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(documentInitials) ?: return@launch
            val key = try {
                book.getKey(pageKey)
            } catch (e: NoSuchKeyException) {
                Log.w(TAG, "AI document page not found: $pageKey in $documentInitials", e)
                return@launch
            } ?: return@launch
            linkControl.showLink(book, key)
        }
    }

    // Platform-dialog removal Task 10: `openAiDocPageChooser` (a native `AlertDialog` list picker
    // over multiple AI doc-marker pages) is deleted -- its TS caller was never wired up
    // (`app/bibleview-js/src/composables/android.ts:127` declared it with no call site; grep
    // confirmed), so this was dead on both sides of the JS bridge.

    @JavascriptInterface
    fun speak(bookInitials: String, v11nName: String, ordinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(bookInitials) as SwordBook
            val v11n = Versifications.instance().getVersification(v11nName)
            val verse = Verse(v11n, ordinal).toV11n(book.versification)
            if(speakControl.isSpeaking) {
                speakControl.pause(willContinueAfterThis = true, toast = false)
            }
            speakControl.speakBible(book, verse)
        }
    }

    @JavascriptInterface
    fun speakMemorizationLoop(bookInitials: String, v11nName: String, ordinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(bookInitials) as SwordBook
            val v11n = Versifications.instance().getVersification(v11nName)
            val startVerse = Verse(v11n, ordinal).toV11n(book.versification)
            val endVerse = Verse(v11n, endOrdinal).toV11n(book.versification)
            if (speakControl.isSpeaking) {
                speakControl.pause(willContinueAfterThis = true, toast = false)
            }
            speakControl.speakMemorizationLoop(book, startVerse, endVerse)
        }
    }

    @JavascriptInterface
    fun speakGeneric(bookInitials: String, osisRef: String, ordinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(bookInitials)
            val origKey = try {
                book!!.getKey(osisRef)
            } catch (e: NoSuchKeyException) {
                val bookAndKey = linkControl.getStrongsKey(book!!, osisRef)
                bookAndKey?.key ?: return@launch
            }
            val key = (origKey as? RangedPassage)?.toVerseRange ?:  try {KeyUtil.getVerse(origKey)} catch (e: ClassCastException) {origKey}
            val ordinalRange = OrdinalRange(ordinal, positiveOrNull(endOrdinal))
            val bookAndKey = BookAndKey(key, book, ordinalRange)
            if(speakControl.isSpeaking) {
                speakControl.pause(willContinueAfterThis = true, toast = false)
            }
            speakControl.speakGeneric(bookAndKey)
        }
    }

    @JavascriptInterface
    fun setAsPrimaryLabel(bookmarkId: String, labelId: String) {
        val label = bookmarkControl.labelById(IdType(labelId))!!
        if(label.isUnlabeledLabel) {
            return
        }
        bookmarkControl.setAsPrimaryLabelForBible(IdType(bookmarkId), IdType(labelId))
        bibleView.windowControl.windowRepository.updateRecentLabels(listOf(IdType(labelId)))
    }

    @JavascriptInterface
    fun setAsPrimaryLabelGeneric(bookmarkId: String, labelId: String) {
        val label = bookmarkControl.labelById(IdType(labelId))!!
        if(label.isUnlabeledLabel) {
            return
        }
        bookmarkControl.setAsPrimaryLabelForGeneric(IdType(bookmarkId), IdType(labelId))
        bibleView.windowControl.windowRepository.updateRecentLabels(listOf(IdType(labelId)))
    }

    @JavascriptInterface
    fun toggleBookmarkLabel(bookmarkId: String, labelId: String) {
        val bookmark = bookmarkControl.bibleBookmarkById(IdType(bookmarkId))!!
        return bookmarkControl.toggleBookmarkLabel(bookmark, labelId)
    }

    @JavascriptInterface
    fun toggleGenericBookmarkLabel(bookmarkId: String, labelId: String) {
        val bookmark = bookmarkControl.genericBookmarkById(IdType(bookmarkId))!!
        return bookmarkControl.toggleBookmarkLabel(bookmark, labelId)
    }

    @JavascriptInterface
    fun setBookmarkCustomIcon(bookmarkId: String, value: String?) {
        val bookmark = bookmarkControl.bibleBookmarkById(IdType(bookmarkId))!!
        bookmark.customIcon = value
        bookmarkControl.addOrUpdateBibleBookmark(bookmark)
    }

    @JavascriptInterface
    fun setGenericBookmarkCustomIcon(bookmarkId: String, value: String?) {
        val bookmark = bookmarkControl.genericBookmarkById(IdType(bookmarkId))!!
        bookmark.customIcon = value
        bookmarkControl.addOrUpdateGenericBookmark(bookmark)
    }

    @JavascriptInterface
    fun reportModalState(value: Boolean) {
        bibleView.modalOpen = value
    }

    @JavascriptInterface
    fun setBookmarkWholeVerse(bookmarkId: String, value: Boolean) {
        val bookmark = bookmarkControl.bibleBookmarkById(IdType(bookmarkId))!!
        if(!value && bookmark.textRange == null) {
            UserMessages.toast(R.string.cant_change_wholeverse)
            return
        }
        bookmark.wholeVerse = value

        bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        if(value) UserMessages.toast(R.string.whole_verse_turned_on)
    }

    @JavascriptInterface
    fun setGenericBookmarkWholeVerse(bookmarkId: String, value: Boolean) {
        val bookmark = bookmarkControl.genericBookmarkById(IdType(bookmarkId))!!
        if(!value && bookmark.textRange == null) {
            UserMessages.toast(R.string.cant_change_wholeverse)
            return
        }
        bookmark.wholeVerse = value

        bookmarkControl.addOrUpdateGenericBookmark(bookmark)
        if(value) UserMessages.toast(R.string.whole_verse_turned_on)
    }

    @JavascriptInterface
    fun toggleCompareDocument(documentId: String) {
        Log.i(TAG, "toggleCompareDocument")
        val hideDocs = bibleView.workspaceSettings.hideCompareDocuments
        if(hideDocs.contains(documentId)) {
            hideDocs.remove(documentId)
        } else {
            hideDocs.add(documentId)
        }
        WorkspaceChanges.notifySettingsEdited()
    }

    /** [content] is now parsed as the app's small HTML subset (`AbHtmlText`/`parseHtmlRuns`) rather
     *  than shown as literal plain text -- harmless for the callers today (no `<`/`&` in their
     *  strings), but a caller adding one later would see it interpreted, not escaped. */
    @JavascriptInterface
    fun helpDialog(content: String, title: String?) {
        // Platform-dialog removal Task 18: the reading view owns this dialog now (a `ReadingDialog`
        // over the pane, not a `ReadingOverlay` -- see `ComposeReadingViewHost.ReadingDialog`'s kdoc).
        hostCallbacks.composeReadingViewHost()?.showHelp(title, content)
    }

    @JavascriptInterface
    fun helpBookmarks() {
        val verseTip = hostActivity.getString(R.string.verse_tip)
        val bookmarksMyNotesHelp = hostActivity.getString(R.string.help_bookmarks_text)
        val message = "<i><a href=\"$bookmarksMyNotesPlaylist\">${hostActivity.getString(R.string.watch_tutorial_video)}</a></i>" +
            "<br><br><b>$verseTip</b><br><br>$bookmarksMyNotesHelp"

        // Platform-dialog removal Task 18: same reading-view-owned dialog state as [helpDialog].
        hostCallbacks.composeReadingViewHost()?.showHelp(hostActivity.getString(R.string.bookmarks_and_mynotes_title), message)
    }

    @JavascriptInterface
    fun shareHtml(html: String) {
        val targetDir = File(SharedConstants.internalFilesDir, "backup/")
        targetDir.mkdirs()
        val targetFile = File(targetDir, "shared.html")
        targetFile.writeText(html)

        val docName = when(val firstDoc = bibleView.firstDocument) {
            is StudyPadDocument -> firstDoc.label.displayName
            is MultiFragmentDocument -> hostActivity.getString(R.string.multi_description)
            is MyNotesDocument -> hostActivity.getString(R.string.my_notes_abbreviation)
            else -> throw RuntimeException("Illegal doc type")
        }

        val titleStr = hostActivity.getString(R.string.export_fileformat, "HTML")
        scope.launch {
            BackupControl.saveOrShare(
                hostActivity,
                targetFile,
                fileName = "shared.html",
                shareMimeType = "text/html",
                saveMimeType = "text/html",
                chooserTitle = titleStr,
                message = titleStr,
                subject = docName
            )
        }
    }

    @JavascriptInterface
    fun onKeyDown(key: String) {
        Log.i(TAG, "key $key")
        scope.launch(Dispatchers.Main) {
            when (key) {
                "AltArrowDown" -> windowControl.focusNextWindow()
                "AltArrowRight" -> windowControl.focusNextWindow()
                "AltArrowUp" -> windowControl.focusPreviousWindow()
                "AltArrowLeft" -> windowControl.focusPreviousWindow()
                // "AltKeyW" (focus the window restore-buttons strip) is RETIRED, not ported: it
                // reached into classic `SplitBibleArea`'s inflated strip, which the Compose reading
                // view has no counterpart for, so the shortcut has been a silent no-op since the
                // host was introduced. Removed with the strip in Batch Z-late's epilogue (spec 10.3).
                "AltKeyM" -> {
                    // Batch Z-early A7 fix B: on the compose path the native DrawerLayout is locked
                    // — but the lock gates ViewDragHelper gestures only, and `open()` bypasses it
                    // entirely, so this shortcut used to raise the native NavigationView on top of
                    // the Compose drawer (two drawers at once). Retarget it; the guard returns
                    // `false` only before the host is installed, leaving the classic fallback
                    // below to run exactly as before in that brief window (R6a moved its two
                    // `binding.drawerLayout` lines behind `openDrawerAndFocusIt` unchanged --
                    // `binding` is the host window's, not the reading view's).
                    if (hostCallbacks.composeOpenDrawerIfHosted()) return@launch
                    hostCallbacks.openDrawerAndFocusIt()
                }
                // Nav-graph slice 7 Task 2: this used to call the native `showOptionsMenu()`
                // `PopupMenu`, which anchored on `binding.optionsMenu` inside the now-GONE classic
                // `toolbarLayout`. It now opens the SAME Compose overflow menu the toolbar's own
                // 3-dot button opens. `composeReadingViewHost` is null only before the host is
                // installed in `setupUi`, and this interface is reachable only from a `BibleView`
                // the host has already mounted, so the shortcut is not lost in practice.
                "AltKeyO" -> hostCallbacks.composeReadingViewHost()?.openOverflowMenu()
                // Round 15b Task 9: deliberately NOT rerouted to the quick sheet, unlike the
                // toolbar title's tap. `startKeyChooser` is left untouched precisely so its
                // non-reading-view callers keep working, and this is one of them: the shortcut acts
                // on `bibleView.window`, which is not necessarily the ACTIVE window the host's sheet
                // would read. Rerouting it would be a behaviour change, not a consistency fix.
                // (nav-graph slice 7 Task 10: the second reason this note used to give -- that the
                // same file's `refChooserDialog` needed an Activity result a sheet could not produce
                // -- is gone. That caller now opens the sheet and is resolved by a
                // `CompletableDeferred`; this shortcut's active-window mismatch is unaffected, so
                // it stays on the classic path. Spec §6.3 records the two remaining bypasses.)
                "CtrlKeyB" -> bibleView.window.pageManager.currentPage.startKeyChooser(hostActivity)
                "CtrlKeyW" -> {
                    // M1 (whole-branch review fix wave): guard on the MOUNTED HOST -- see
                    // MainBibleActivity's R.id.switchToWorkspace for the full rationale.
                    val host = hostCallbacks.composeReadingViewHost()
                    if (host != null) {
                        host.showWorkspaceSheet()
                    } else {
                        val intent = NavHostComposeActivity.intentFor(hostActivity, NavRoutes.WORKSPACE_SELECTOR)
                        hostActivity.startActivityForResult(intent, WORKSPACE_CHANGED)
                    }
                }
                "CtrlKeyC" -> bibleView.copySelectionToClipboard()
                "CtrlKeyF" -> {
                    // F6 Task 8b entry point 5: retarget into the reading view's search when a
                    // Compose host is mounted; classic behaviour unchanged otherwise.
                    if (!hostCallbacks.composeSearchIfHosted(/* seedQuery = */ null, /* preDecorated = */ false)) {
                        val intent = searchControl.getSearchIntent(windowControl.activeWindowPageManager.currentPage.currentDocument, hostActivity)
                        intent?.let {hostActivity.startActivityForResult(it, ActivityBase.STD_REQUEST_CODE)}
                    }
                }
                "Space" -> {
                    if(!speakControl.isStopped) {
                        speakControl.toggleSpeak(true)
                    }
                }
            }
        }
    }

    @JavascriptInterface
    fun crash() {
        scope.launch {
            hostCallbacks.crashAllBibleViews()
        }
    }

    @JavascriptInterface
    fun llmAction(bookInitials: String, startOrdinal: Int, endOrdinal: Int, text: String) {
        scope.launch(Dispatchers.Main) {
            hostCallbacks.showLlmPromptSelector(
                Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal), text = text),
                PromptContext.VERSE_SELECTION,
            )
        }
    }

    @JavascriptInterface
    fun llmActionGeneric(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int, text: String) {
        scope.launch(Dispatchers.Main) {
            hostCallbacks.showLlmPromptSelector(
                Selection(bookInitials, osisRef, startOrdinal, positiveOrNull(endOrdinal), text = text),
                PromptContext.VERSE_SELECTION,
            )
        }
    }

    @Serializable
    data class NoteEditorLlmContext(
        val entityType: String,
        val entityId: String,
        val currentText: String,
        val contentType: String
    )

    /**
     * Trigger LLM prompt selector for the note editor context.
     * Called from Vue.js MarkdownEditor/HtmlEditor AI button.
     */
    @JavascriptInterface
    fun noteEditorLlmAction(contextJson: String) {
        scope.launch(Dispatchers.Main) {
            val ctx = json.decodeFromString<NoteEditorLlmContext>(serializer(), contextJson)

            // Try to get verse context from the bookmark for better AI context
            var bookInitials: String? = null
            var startOrdinal = 0
            var endOrdinal = 0
            if (ctx.entityType == NoteEditorEntityType.BOOKMARK_NOTE.name) {
                val bookmark = bookmarkControl.bibleBookmarkById(IdType(ctx.entityId))
                if (bookmark != null) {
                    bookInitials = bookmark.book?.initials
                    startOrdinal = bookmark.ordinalStart
                    endOrdinal = bookmark.ordinalEnd
                }
            }

            val selection = Selection(
                bookInitials = bookInitials,
                startOrdinal = startOrdinal,
                startOffset = null,
                endOrdinal = endOrdinal,
                endOffset = null,
                bookmarks = emptyList(),
                noteEditorEntityType = try { NoteEditorEntityType.valueOf(ctx.entityType) } catch (_: IllegalArgumentException) { null },
                noteEditorEntityId = ctx.entityId,
                noteEditorContent = ctx.currentText,
                noteEditorContentType = ctx.contentType,
            )
            hostCallbacks.showLlmPromptSelector(selection, PromptContext.NOTE_EDITOR)
        }
    }

    private val windowControl get() = bibleView.windowControl

    @JavascriptInterface
    fun getMyDocumentPageRawContent(callId: Long, bookInitials: String, pageKey: String) {
        scope.launch {
            val result = MyDocumentBookManager.getPageRawContent(bookInitials, pageKey)
            val jsonResult = if (result != null) {
                json.encodeToString(serializer(), result)
            } else {
                "null"
            }
            bibleView.executeJavascriptOnUiThread("bibleView.response($callId, $jsonResult);")
        }
    }

    @JavascriptInterface
    fun shareMyDocumentContent(bookInitials: String, pageKey: String) {
        scope.launch {
            val result = MyDocumentBookManager.getPageRawContent(bookInitials, pageKey) ?: return@launch
            withContext(Dispatchers.Main) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, result.content)
                    putExtra(Intent.EXTRA_SUBJECT, result.title)
                    type = "text/plain"
                }
                hostActivity.startActivity(Intent.createChooser(sendIntent, hostActivity.getString(R.string.share)))
            }
        }
    }

    @JavascriptInterface
    fun copyMyDocumentContent(bookInitials: String, pageKey: String) {
        scope.launch {
            val result = MyDocumentBookManager.getPageRawContent(bookInitials, pageKey) ?: return@launch
            withContext(Dispatchers.Main) {
                CommonUtils.copyToClipboard(ClipData.newPlainText(result.title, result.content))
            }
        }
    }

    @JavascriptInterface
    fun saveMyDocumentPageContent(bookInitials: String, pageId: String, content: String, title: String?) {
        scope.launch {
            MyDocumentBookManager.savePageContent(IdType(pageId), content, title)
        }
    }

    @JavascriptInterface
    fun reloadMyDocumentPage(bookInitials: String) {
        scope.launch {
            MyDocumentBookManager.refreshDocument(bookInitials)
        }
    }

    @JavascriptInterface
    fun regenerateMyDocumentPage(pageId: String) {
        val id = IdType(pageId)
        scope.launch(Dispatchers.Main) {
            hostCallbacks.showRegenerate(id, bibleView)
        }
    }

    @JavascriptInterface
    fun deleteMyDocumentPage(pageId: String) {
        val id = IdType(pageId)
        scope.launch(Dispatchers.Main) {
            // Platform-dialog removal Task 18: the QUESTION moves to the reading view's own dialog
            // state; the deletion + window bookkeeping stays here, since it needs [windowControl] and
            // the triggering [bibleView] (see `ComposeReadingViewHost.showDeleteDocumentPageConfirm`'s
            // kdoc).
            hostCallbacks.composeReadingViewHost()?.showDeleteDocumentPageConfirm {
                MyDocumentBookManager.deleteAIDocumentPage(id)
                val window = bibleView.window
                if (windowControl.isWindowRemovable(window)) {
                    windowControl.closeWindow(window)
                } else {
                    window.pageManager.setCurrentDocument(window.pageManager.currentBible.currentDocument)
                }
            }
        }
    }

    @JavascriptInterface
    fun openPromptEditor(promptId: String) {
        scope.launch(Dispatchers.Main) {
            // Screen.PromptEdit is migrated (Task 7) -- ScreenLauncher.MIGRATED can only carry an
            // argument-less route, so a caller needing promptId must build the concrete route
            // itself and go straight to NavHostComposeActivity (a putExtra on top of
            // ScreenLauncher.intentFor's result would be silently dropped; see
            // NavHostRoutingGuardTest's migratedScreenArgumentIsNeverDroppedByAPutExtra).
            val intent = NavHostComposeActivity.intentFor(hostActivity, NavRoutes.promptEdit(promptId = promptId))
            hostActivity.startActivity(intent)
        }
    }

    private val TAG get() = "BibleView[${bibleView.windowRef.get()?.displayId}] JSInt"
}
