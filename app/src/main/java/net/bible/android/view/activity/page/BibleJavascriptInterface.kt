/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import net.bible.android.SharedConstants
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.MultiFragmentDocument
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.android.view.util.widget.ShareWidget
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.bookmarksMyNotesPlaylist
import net.bible.service.common.displayName
import net.bible.service.common.htmlToSpan
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.mybible.myBibleIntToBibleBook
import net.bible.service.sword.mysword.mySwordIntToBibleBook
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.BookName
import java.io.File
import java.lang.ClassCastException


class BibleJavascriptInterface(
	private val bibleView: BibleView
) {
    private val currentPageManager: CurrentPageManager get() = bibleView.window.pageManager
    val bookmarkControl get() = bibleView.bookmarkControl
    val downloadControl get() = bibleView.downloadControl

    val mainBibleActivity = bibleView.mainBibleActivity
    var notificationsEnabled = false
    val scope get() = mainBibleActivity.lifecycleScope

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
            if(curPage is CurrentGeneralBookPage && doc is OsisDocument && curPage.key?.osisRef != keyStr) {
                curPage.currentDocument?.getKey(keyStr)?.let {
                    curPage.doSetKey(it)
                    ABEventBus.post(CurrentVerseChangedEvent(window = bibleView.window))
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
        ABEventBus.post(AppSettingsUpdated())
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
    fun refChooserDialog(callId: Long) {
        scope.launch {
            val intent = Intent(mainBibleActivity, GridChoosePassageBook::class.java).apply {
                putExtra("isScripture", true)
                putExtra("navigateToVerse", true)
            }
            val result = mainBibleActivity.awaitIntent(intent)
            val verseStr = result?.data?.getStringExtra("verse")


            val verse = if(verseStr == null) null else VerseFactory.fromString(KJVA, verseStr)

            val verseName = synchronized(BookName::class.java) {
                val oldValue = BookName.isFullBookName()
                BookName.setFullBookName(false)
                val text = verse?.name ?: ""
                BookName.setFullBookName(oldValue)
                text
            }

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
        ABEventBus.post(BibleViewInputFocusChanged(bibleView, newValue))
    }

    @JavascriptInterface
    fun openEpubLink(bookInitials: String, toKeyStr: String, toId: String) {
        val book = Books.installed().getBook(bookInitials) as SwordGenBook
        val backend = book.backend as EpubBackend
        val key = backend.getKey(toKeyStr, toId)
        scope.launch(Dispatchers.Main) {
            bibleView.linkControl.showLink(book, BookAndKey(key, book, htmlId = toId))
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
                    bibleView.linkControl.loadApplicationUrl(bibleLink)
                }
            }
            link.startsWith("S:") -> {
                // MyBible strongs
                val (prefix, rest) = link.split(":", limit=2)
                val bibleLink = BibleView.BibleLink("strong", target=rest)
                scope.launch(Dispatchers.Main) {
                    bibleView.linkControl.loadApplicationUrl(bibleLink)
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
                    bibleView.linkControl.loadApplicationUrl(bibleLink)
                }
            }
            link.startsWith("#s") || link.startsWith("#d") -> {
                // MySword strongs links
                val rest = link.substring(2)
                val bibleLink = BibleView.BibleLink("strong", target=rest)
                scope.launch(Dispatchers.Main) {
                    bibleView.linkControl.loadApplicationUrl(bibleLink)
                }
            }
            else -> {
                mainBibleActivity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
        }
    }

    @JavascriptInterface
    fun openDownloads() {
        if (!downloadControl.checkDownloadOkay()) return
        val intent = Intent(mainBibleActivity, DownloadActivity::class.java)
        intent.putExtra("addons", true)
        mainBibleActivity.startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
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
    fun getActiveLanguages(): String {
        //Get the languages for each of the installed bibles and return the language codes as a json list.
        val languages = SwordDocumentFacade.bibles.map { "\"" + it.bookMetaData.language.code + "\""}
        return "[" + languages.distinct().joinToString(",") + "]"
    }

    @JavascriptInterface
    fun toast(text: String) {
        ABEventBus.post(ToastEvent(text))
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
            ShareWidget.dialog(mainBibleActivity, bookmark)
        }
    }

    private fun positiveOrNull(value: Int): Int? {
        if(value < 0) return null
        return value
    }

    @JavascriptInterface
    fun shareVerse(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            ShareWidget.dialog(mainBibleActivity, Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)))
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
    fun compare(bookInitials: String, verseOrdinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            bibleView.compareSelection(Selection(bookInitials, verseOrdinal, positiveOrNull(endOrdinal)))
        }
    }

    @JavascriptInterface
    fun openStudyPad(labelId: String, bookmarkId: String) {
        scope.launch(Dispatchers.Main) {
            bibleView.linkControl.openStudyPad(IdType(labelId), IdType(bookmarkId))
        }
    }

    @JavascriptInterface
    fun openMyNotes(v11n: String, ordinal: Int) {
        scope.launch(Dispatchers.Main) {
            bibleView.linkControl.openMyNotes(v11n, ordinal)
        }
    }

    @JavascriptInterface
    fun speak(bookInitials: String, ordinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(bookInitials) as SwordBook
            val verse = Verse(book.versification, ordinal)
            mainBibleActivity.speakControl.speakBible(book, verse, force = true)
        }
    }

    @JavascriptInterface
    fun speakGeneric(bookInitials: String, osisRef: String, ordinal: Int, endOrdinal: Int) {
        scope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(bookInitials)
            val key = book.getKey(osisRef)
            val singleKey = try {KeyUtil.getVerse(key)} catch (e: ClassCastException) {key}
            val ordinalRange = OrdinalRange(ordinal, positiveOrNull(endOrdinal))
            val bookAndKey = BookAndKey(singleKey, book, ordinalRange)
            mainBibleActivity.speakControl.speakGeneric(bookAndKey)
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
    fun reportModalState(value: Boolean) {
        bibleView.modalOpen = value
    }

    @JavascriptInterface
    fun setBookmarkWholeVerse(bookmarkId: String, value: Boolean) {
        val bookmark = bookmarkControl.bibleBookmarkById(IdType(bookmarkId))!!
        if(!value && bookmark.textRange == null) {
            ABEventBus.post(ToastEvent(R.string.cant_change_wholeverse))
            return
        }
        bookmark.wholeVerse = value

        bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        if(value) ABEventBus.post(ToastEvent(R.string.whole_verse_turned_on))
    }

    @JavascriptInterface
    fun setGenericBookmarkWholeVerse(bookmarkId: String, value: Boolean) {
        val bookmark = bookmarkControl.genericBookmarkById(IdType(bookmarkId))!!
        if(!value && bookmark.textRange == null) {
            ABEventBus.post(ToastEvent(R.string.cant_change_wholeverse))
            return
        }
        bookmark.wholeVerse = value

        bookmarkControl.addOrUpdateGenericBookmark(bookmark)
        if(value) ABEventBus.post(ToastEvent(R.string.whole_verse_turned_on))
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
        ABEventBus.post(AppSettingsUpdated())
    }

    @JavascriptInterface
    fun helpDialog(content: String, title: String?) {
        AlertDialog.Builder(mainBibleActivity)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton(mainBibleActivity.getString(R.string.okay), null)
            .show()
    }

    @JavascriptInterface
    fun helpBookmarks() {
        val verseTip = mainBibleActivity.getString(R.string.verse_tip)
        val bookmarksMyNotesHelp = mainBibleActivity.getString(R.string.help_bookmarks_text)
        val message = "<i><a href=\"$bookmarksMyNotesPlaylist\">${mainBibleActivity.getString(R.string.watch_tutorial_video)}</a></i>" +
            "<br><br><b>$verseTip</b><br><br>$bookmarksMyNotesHelp"

        val d = AlertDialog.Builder(mainBibleActivity)
            .setTitle(R.string.bookmarks_and_mynotes_title)
            .setMessage(htmlToSpan(message))
            .setPositiveButton(mainBibleActivity.getString(R.string.okay), null)
            .create()

        d.show()

        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    @JavascriptInterface
    fun shareHtml(html: String) {
        val targetDir = File(SharedConstants.internalFilesDir, "backup/")
        targetDir.mkdirs()
        val targetFile = File(targetDir, "shared.html")
        targetFile.writeText(html)
        val uri = FileProvider.getUriForFile(mainBibleActivity, BuildConfig.APPLICATION_ID + ".provider", targetFile)

        val docName = when(val firstDoc = bibleView.firstDocument) {
            is StudyPadDocument -> firstDoc.label.displayName
            is MultiFragmentDocument -> mainBibleActivity.getString(R.string.multi_description)
            is MyNotesDocument -> mainBibleActivity.getString(R.string.my_notes_abbreviation)
            else -> throw RuntimeException("Illegal doc type")
        }
        val titleStr = mainBibleActivity.getString(R.string.export_fileformat, "HTML")
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, docName)
            putExtra(Intent.EXTRA_TEXT, titleStr)
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "text/html"
        }

        // Add the "Save" option to the chooser
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "text/html"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "shared.html")

        val chooserIntent = Intent.createChooser(emailIntent, titleStr)
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(saveIntent))

        scope.launch(Dispatchers.Main) {
            val result = mainBibleActivity.awaitIntent(chooserIntent)
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data?.data != null) {
                val destinationUri = data.data!!

                withContext(Dispatchers.IO) {
                    mainBibleActivity.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        mainBibleActivity.contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }

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
                "AltKeyW" -> mainBibleActivity.documentViewManager.splitBibleArea?.binding?.restoreButtons?.requestFocus()
                "AltKeyM" -> {
                    mainBibleActivity.binding.drawerLayout.open()
                    mainBibleActivity.binding.drawerLayout.requestFocus()
                }
                "AltKeyO" -> mainBibleActivity.showOptionsMenu()
                "AltKeyG" -> bibleView.window.pageManager.currentPage.startKeyChooser(mainBibleActivity)
            }
        }
    }

    private val TAG get() = "BibleView[${bibleView.windowRef.get()?.displayId}] JSInt"
}
