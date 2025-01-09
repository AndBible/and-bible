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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.LayoutDirection
import android.util.Log
import android.view.ActionMode
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.PathHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.serializer
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.bookmark.BookmarkAddedOrUpdatedEvent
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkNoteModifiedEvent
import net.bible.android.control.bookmark.BookmarkToLabelAddedOrUpdatedEvent
import net.bible.android.control.bookmark.BookmarksDeletedEvent
import net.bible.android.control.bookmark.LabelAddedOrUpdatedEvent
import net.bible.android.control.bookmark.StudyPadOrderEvent
import net.bible.android.control.bookmark.StudyPadTextEntryDeleted
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.event.window.ScrollSecondaryWindowEvent
import net.bible.android.control.event.window.WindowSizeChangedEvent
import net.bible.android.control.link.LinkControl
import net.bible.android.control.link.WindowMode
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.ClientBibleBookmark
import net.bible.android.control.page.ClientBookmarkLabel
import net.bible.android.control.page.ClientGenericBookmark
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.Document
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.versification.toVerseRange
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.json
import net.bible.android.misc.wrapString
import net.bible.android.view.activity.base.DocumentView
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.page.screen.AfterRemoveWebViewEvent
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.android.view.activity.page.screen.RestoreButtonsVisibilityChanged
import net.bible.android.view.activity.page.screen.WebViewsBuiltEvent
import net.bible.android.view.activity.page.screen.clipboardKey
import net.bible.android.view.activity.search.SearchIndex
import net.bible.android.view.activity.search.SearchResults
import net.bible.android.view.util.UiUtils
import net.bible.android.view.util.widget.ShareWidget
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.AndBibleAddons.fontsByModule
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.buildActivityComponent
import net.bible.service.common.CommonUtils.parseAndBibleReference
import net.bible.service.common.ReloadAddonsEvent
import net.bible.service.device.ScreenSettings
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.search.SearchType
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.SystemKJVA
import org.crosswire.jsword.versification.system.Versifications
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URLConnection
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

class BibleViewInputFocusChanged(val view: BibleView, val newFocus: Boolean)
class AppSettingsUpdated

const val MAX_DOC_STR_LENGTH = 4000000;
private val notFound = WebResourceResponse(null, null, null)

const val white = -1
const val black = -16777216

@Serializable
class Selection(
    val bookInitials: String?,
    val startOrdinal: Int,
    val startOffset: Int?,
    val endOrdinal: Int,
    val endOffset: Int?,
    val bookmarks: List<IdType>,
    val notes: String? = null,
    val text: String = "",
    val osisRef: String? = null,
)
{
    constructor(bookmark: BookmarkEntities.BibleBookmarkWithNotes):
        this(
            bookInitials = bookmark.book?.initials,
            startOrdinal = bookmark.ordinalStart,
            startOffset = bookmark.startOffset,
            endOrdinal = bookmark.ordinalEnd,
            endOffset = bookmark.endOffset,
            bookmarks = emptyList(),
            notes = bookmark.notes
        )

    constructor(bookInitials: String, startOrdinal: Int, endOrdinal: Int?):
        this(
            bookInitials = bookInitials,
            startOrdinal = startOrdinal,
            startOffset = 0,
            endOrdinal = endOrdinal?: startOrdinal,
            endOffset = null,
            bookmarks = emptyList(),
        )
    constructor(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int?):
        this(
            bookInitials = bookInitials,
            osisRef = osisRef,
            startOrdinal = startOrdinal,
            startOffset = 0,
            endOrdinal = endOrdinal?: startOrdinal,
            endOffset = null,
            bookmarks = emptyList(),
        )

    @Transient @Inject lateinit var windowControl: WindowControl

    init {
        buildActivityComponent().inject(this)
    }

    val hasRange get() = startOffset != null && endOffset != null

    val book: Book? get() = Books.installed().getBook(bookInitials)
    val swordBook: SwordBook? get() =
        if(book is SwordBook)
            book as SwordBook? ?: windowControl.defaultBibleDoc(false)
        else null
    val verseRange: VerseRange? get() {
        swordBook?: return null
        val v11n = swordBook?.versification ?: KJVA
        return VerseRange(v11n, Verse(v11n, startOrdinal), Verse(v11n, endOrdinal))
    }

    fun copyToClipboard() {
        CommonUtils.copyToClipboard(
            ClipData.newPlainText(verseRange?.name, CommonUtils.getShareableDocumentText(this))
        )
    }
}

/** The WebView component that shows the bible and other documents */
@SuppressLint("ViewConstructor")
class BibleView(val mainBibleActivity: MainBibleActivity,
                internal var windowRef: WeakReference<Window>,
                internal val windowControl: WindowControl,
                private val pageControl: PageControl,
                private val pageTiltScrollControl: PageTiltScrollControl,
                internal val linkControl: LinkControl,
                internal val bookmarkControl: BookmarkControl,
                internal val downloadControl: DownloadControl,
                private val searchControl: SearchControl
) : WebView(mainBibleActivity.applicationContext), DocumentView
{
    private lateinit var bibleJavascriptInterface: BibleJavascriptInterface

    private lateinit var pageTiltScroller: PageTiltScroller
    private var hideScrollBar: Boolean = false

    private var wasAtRightEdge: Boolean = false
    private var wasAtLeftEdge: Boolean = false

    private var minChapter = -1
    private var maxChapter = -1

    private var gestureDetector: GestureDetectorCompat

    /** Used to prevent scroll off bottom using auto-scroll
     * see http://stackoverflow.com/questions/5069765/android-webview-how-to-autoscroll-a-page
     */
    //TODO get these once, they probably won't change
    private val maxVerticalScroll: Int
        get() = computeVerticalScrollRange() - computeVerticalScrollExtent()

    private val maxHorizontalScroll: Int
        get() = computeHorizontalScrollRange() - computeHorizontalScrollExtent()

    private val gestureListener  = BibleGestureListener(mainBibleActivity, this)

    private var toBeDestroyed = false

    @Volatile private var latestDocumentStr: String? = null
    @Volatile private var needsDocument: Boolean = false
    @Volatile private var htmlLoadingOngoing: Boolean = true

    var window: Window
        get() = windowRef.get()!!
        set(value) {
            windowRef = WeakReference(value)
        }

    class BibleViewTouched(val onlyTouch: Boolean = false)

    init {
        //if ((0 != BibleApplication.application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) || CommonUtils.isBeta) {
            setWebContentsDebuggingEnabled(true)
        //}
        gestureDetector = GestureDetectorCompat(context, gestureListener)
        setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                true
            } else v.performClick()
        }
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        setOnLongClickListener(BibleViewLongClickListener())
    }

    private var step2 = false

    private fun onActionMenuItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.add_bookmark -> {
                findViewTreeLifecycleOwner()
                val cat = currentSelection?.book?.bookCategory
                if(cat != null && cat != BookCategory.BIBLE) {
                    makeBookmark()
                    mode.finish()
                } else {
                    step2 = true
                    mode.menu.clear()
                    mode.invalidate()
                }
                return false
            }
            R.id.add_bookmark_selection -> {
                makeBookmark()
                mode.finish()
                return true
            }
            R.id.add_bookmark_whole_verse -> {
                makeBookmark(wholeVerse = true)
                mode.finish()
                return true
            }
            R.id.compare -> {
                compareSelection()
                mode.finish()
                return true
            }
            R.id.share_verses -> {
                val sel = currentSelection ?: return true
                ShareWidget.dialog(mainBibleActivity, sel)
                return true
            }
            R.id.open_ref -> {
                val ref = currentSelectionRef
                if(ref != null) { linkControl.showLink(null,  ref) }
                return true
            }
            R.id.copy -> {
                val clip = ClipData.newPlainText(application.getString(R.string.add_bookmark3), currentSelectionText)
                CommonUtils.copyToClipboard(clip)
                return true
            }
            R.id.web_search -> {
                if (currentSelectionText != null) { openWebSearch(mainBibleActivity, currentSelectionText!!) }
                return true
            }
            R.id.search -> {
                val text = currentSelectionText
                val sel = currentSelection
                val selText = sel?.text?:text?:return true
                val currentBible = currentPageManager.currentBible.currentDocument ?: return true
                val searchText = searchControl.decorateSearchString(
                    selText,
                    SearchType.PHRASE,
                    SearchControl.SearchBibleSection.ALL,
                    ""
                )
                val searchParams = Bundle().apply {
                    putString(SearchControl.SEARCH_TEXT, searchText)
                    putString(SearchControl.SEARCH_DOCUMENT, currentBible.initials)
                    putString(SearchControl.TARGET_DOCUMENT, currentBible.initials)
                }

                val intent = Intent(
                    mainBibleActivity,
                    if (currentBible.indexStatus != IndexStatus.DONE) SearchIndex::class.java else SearchResults::class.java
                ).apply {
                    putExtras(searchParams)
                }
                mainBibleActivity.startActivity(intent)

                return true
            }
            else -> false
        }
    }

    fun makeBookmark(selection: Selection? = currentSelection, wholeVerse: Boolean = false, openNotes: Boolean = false) {
        selection?: return
        Log.i(TAG, "makeBookmark")

        val initialLabels = workspaceSettings.autoAssignLabels
        val primaryLabelId = workspaceSettings.autoAssignPrimaryLabel

        val textRange =
            if (selection.startOffset != null && selection.endOffset != null)
                BookmarkEntities.TextRange(selection.startOffset, selection.endOffset)
            else null

        val bookmark: BookmarkEntities.BaseBookmarkWithNotes =
            if(selection.book?.bookCategory == BookCategory.BIBLE) {
                val verseRange = selection.verseRange
                BookmarkEntities.BibleBookmarkWithNotes(verseRange!!, textRange, wholeVerse, selection.swordBook)
            } else {
                BookmarkEntities.GenericBookmarkWithNotes(
                    key = selection.osisRef!!,
                    book = selection.book!!,
                    ordinalStart = selection.startOrdinal,
                    ordinalEnd = selection.endOrdinal,
                    textRange = textRange,
                    wholeVerse = wholeVerse,
                    new = true,
                )
            }
        if(primaryLabelId != null) {
            val label = bookmarkControl.labelById(primaryLabelId)
            if(label != null) {
                bookmark.primaryLabelId = primaryLabelId
            }
        }

        bookmarkControl.addOrUpdateBookmark(bookmark, initialLabels)
        if(initialLabels.isEmpty() || openNotes) {
            executeJavascriptOnUiThread(
                "bibleView.emit('bookmark_clicked', '${bookmark.id}', {openLabels: true, openNotes: $openNotes});"
            )
        }
    }

    fun openWebSearch(context: Context, query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Browser app not available to search: " + query)
        }
    }

    internal fun compareSelection(selection: Selection? = currentSelection) {
        Log.i(TAG, "compareSelection")
        val verseRange = selection?.verseRange ?: return
        linkControl.openCompare(verseRange)
    }

    val scope get() = mainBibleActivity.lifecycleScope

    internal fun assignLabels(bookmark: BookmarkEntities.BaseBookmarkWithNotes) = scope.launch(Dispatchers.IO) {
        val labels = bookmarkControl.labelsForBookmark(bookmark).map { it.id }
        val intent = Intent(mainBibleActivity, ManageLabels::class.java)
        intent.putExtra("data", ManageLabels.ManageLabelsData(
            mode = ManageLabels.Mode.ASSIGN,
            selectedLabels = labels.toMutableSet(),
            bookmarkPrimaryLabel = bookmark.primaryLabelId
        ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON())
        val result = mainBibleActivity.awaitIntent(intent)

        if(result.resultCode == Activity.RESULT_OK) {
            val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
            bookmark.primaryLabelId = resultData.bookmarkPrimaryLabel
            bookmarkControl.addOrUpdateBookmark(bookmark, resultData.selectedLabels)
            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
        }
    }

    var menuPrepared = false
    var currentSelection: Selection? = null
    var currentSelectionRef: Key? = null
    var currentSelectionText: String? = null

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent() = Intent()
        .setAction(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(Intent.EXTRA_PROCESS_TEXT, currentSelectionText!!)

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        val packageManager: PackageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(createProcessTextIntent(), PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(createProcessTextIntent(), 0)
        }.filter { it.activityInfo.name != SearchResults::class.qualifiedName }
    }

    private fun getLabel(resolveInfo: ResolveInfo): CharSequence {
        return resolveInfo.loadLabel(context.packageManager)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo) =
        createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)

    private fun onPrepareActionMenu(mode: ActionMode, menu: Menu): Boolean {
        Log.i(TAG, "onPrepareActionMode $menuPrepared ${currentSelection?.verseRange}")

        if(menuPrepared) {
            mode.menu.clear()
            mode.menuInflater.inflate(R.menu.bibleview_selection, menu)

            val sel = currentSelection

            // For some reason, these do not seem to be correct from XML, even though specified there
            if(isBible && CommonUtils.settings.getBoolean("disable_two_step_bookmarking", false)) {
                menu.findItem(R.id.add_bookmark_selection).run {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    setVisible(true)
                }
                menu.findItem(R.id.add_bookmark_whole_verse).run{
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    setVisible(true)
                }
            } else {
                menu.findItem(R.id.add_bookmark).run {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    setVisible(true)
                }
            }
            menu.findItem(R.id.compare).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.findItem(R.id.share_verses).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            if(sel == null) {
                menu.findItem(R.id.add_bookmark).isVisible = false
                menu.findItem(R.id.add_bookmark_selection).isVisible = false
                menu.findItem(R.id.add_bookmark_whole_verse).isVisible = false
            }
            if(!isBible) {
                menu.findItem(R.id.compare).isVisible = false
                menu.findItem(R.id.share_verses).isVisible = false
            }
            val ref = currentSelectionRef
            if(ref != null) {
                val item = menu.findItem(R.id.open_ref)
                item.isVisible = true
                synchronized(BookName::class.java) {
                    val wasFullBookName = BookName.isFullBookName()
                    BookName.setFullBookName(false)
                    item.title = context.getString(R.string.go_to_ref, ref.name)
                    BookName.setFullBookName(wasFullBookName)
                }
            }
            if(ref == null && currentSelectionText != null) {
                val item = menu.findItem(R.id.search)
                item.isVisible = true
                item.title = if(currentSelectionText!!.length < 16) context.getString(R.string.search_what, currentSelectionText) else context.getString(R.string.search)
            }
            if (currentSelectionText != null) {
                menu.findItem(R.id.web_search).apply {
                    isVisible = true
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentSelectionText != null) {
                var menuItemOrder = 100
                for (resolveInfo in getSupportedActivities()) {
                    menu.add(Menu.NONE, Menu.NONE,
                        menuItemOrder++,
                        getLabel(resolveInfo))
                        .setIntent(createProcessTextIntentForResolveInfo(resolveInfo))
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                if(!isBible) {
                    menu.findItem(R.id.copy).isVisible = true
                }
            }

            menuPrepared = false
            return true
        }
        else {
            if(step2) {
                mode.menu.clear()
                mode.menuInflater.inflate(R.menu.bibleview_selection2, menu)
                step2 = false
                return true
            }
            if (editingTextInJs) {
                return true
            } else {
                menu.clear()
                scope.launch {
                    if (setCurrentSelection())
                        menuPrepared = true

                    withContext(Dispatchers.Main) {
                        mode.invalidate()
                    }
                }
                return false
            }
        }
    }

    /** @return true if bibleView.querySelection() result was not null */
    private suspend fun setCurrentSelection(): Boolean = withContext(Dispatchers.Main) {
        val result = evaluateJavascriptAsync("bibleView.querySelection()")
        if (result != "null") {
            val sel = try {
                json.decodeFromString(serializer<Selection?>(), result)
            } catch (e: SerializationException) {
                null
            }
            val selText = try { json.decodeFromString(serializer(), result) } catch (e: SerializationException) { result }
            currentSelection = sel
            currentSelectionText = sel?.text ?: selText
            currentSelectionRef = linkControl.resolveRef(currentSelectionText?: "")
            return@withContext true
        }

        return@withContext false
    }

    fun copySelectionToClipboard(selection: Selection? = null) {
        scope.launch {
            // use currentSelection for partial selected text, otherwise
            // JS has to send Selection by book and ordinals which is passed in selection parameter
            currentSelection = null
            if (selection == null)
                setCurrentSelection()
            (currentSelection ?: selection)?.copyToClipboard()
        }
    }

    var editingTextInJs: Boolean = false

    fun stopSelection(removeRanges: Boolean = false) {
        currentSelection = null
        currentSelectionText = null
        currentSelectionRef = null
        menuPrepared = false
        if(removeRanges) executeJavascriptOnUiThread("bibleView.emit('remove_ranges')")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private inner class ActionModeCallback2(val callback: ActionMode.Callback): ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            executeJavascriptOnUiThread("bibleView.emit('set_action_mode', true);")
            val wasUpdated2 = callback.onCreateActionMode(mode, menu)
            val wasUpdated1 = false
            return wasUpdated1 || wasUpdated2
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val wasUpdated2 = callback.onPrepareActionMode(mode, menu)
            val wasUpdated1 = onPrepareActionMenu(mode, menu)
            return wasUpdated1 || wasUpdated2
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if(editingTextInJs) {
                val rv = callback.onActionItemClicked(mode, item)
                mode.finish()
                return rv
            }
            val handled = onActionMenuItemClicked(mode, item)
            if(handled) stopSelection(true)
            return handled
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            executeJavascriptOnUiThread("bibleView.emit('set_action_mode', false);")
            return callback.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if(callback is ActionMode.Callback2) {
                callback.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            super.startActionMode(ActionModeCallback2(callback), type)
        } else {
            super.startActionMode(ActionModeCallback(callback), type)
        }
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode {
        return super.startActionMode(ActionModeCallback(callback))
    }

    // This can be removed after Lollipop support is dropped.
    private inner class ActionModeCallback(val callback: ActionMode.Callback): ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            executeJavascriptOnUiThread("bibleView.emit('set_action_mode', true);")
            callback.onCreateActionMode(mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val wasUpdated1 = callback.onPrepareActionMode(mode, menu)
            val wasUpdated2 = onPrepareActionMenu(mode, menu)
            return wasUpdated1 || wasUpdated2
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return onActionMenuItemClicked(mode, item) || callback.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            executeJavascriptOnUiThread("bibleView.emit('set_action_mode', false);")
            return callback.onDestroyActionMode(mode)
        }
    }

    fun setBibleJavascriptInterface(bibleJavascriptInterface: BibleJavascriptInterface) {
        this.bibleJavascriptInterface = bibleJavascriptInterface
        addJavascriptInterface(bibleJavascriptInterface, "android")
    }

    class BibleLink(val type: String, val target: String, private val v11nName: String? = null, val forceDoc: Boolean = false) {
        val versification: Versification get() =
            Versifications.instance().getVersification(v11nName ?: SystemKJVA.V11N_NAME) ?: KJVA
        val url: String get() {
            return when(type) {
                "content" -> "$type:$target"
                "strong" -> "$type:$target"
                "robinson" -> "$type:$target"
                "strongMorph" -> "$type:$target"
                else -> {
                    if(target.startsWith("sword://"))
                        target
                    else {
                        var protocol = "osis:"
                        var ref = target
                        if (target.split(":").size > 1) {
                            protocol = "sword://"
                            ref = target.replace(":", "/")
                        }
                        "$protocol$ref"
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initialise() {
        Log.i(TAG, "initialise")
        webViewClient = BibleViewClient()

        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                Log.i(TAG, message)
                result.confirm()
                return true
            }
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                // If errorBox is enabled, console logging is handled in js interface so we don't want anything
                // from here anymore.
                if (!showErrorBox) {
                    Log.i(TAG, "bibleview-js: ${consoleMessage.messageLevel()} ${consoleMessage.message()}")
                }
                return true
            }
        }

        settings.javaScriptEnabled = true

        pageTiltScroller = PageTiltScroller(this, pageTiltScrollControl)
        pageTiltScroller.enableTiltScroll(true)

        // if this webview becomes (in)active then must start/stop auto-scroll
        listenEvents = true

        val locale = Locale.getDefault()
        val isRtl = TextUtils.getLayoutDirectionFromLocale(locale) == LayoutDirection.RTL
        val lang = locale.toLanguageTag()

        val fontModuleNames = AndBibleAddons.fontModuleNames.joinToString(",")
        val featureModuleNames = AndBibleAddons.featureModuleNames.joinToString(",")
        val styleModuleNames = AndBibleAddons.styleModuleNames.joinToString(",")
        // Workaround for #1756
        if (setOf(Build.VERSION_CODES.O, Build.VERSION_CODES.O_MR1).contains(Build.VERSION.SDK_INT)) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        loadUrl("https://appassets.androidplatform.net/assets/bibleview-js/index.html" +
            "?lang=$lang&fontModuleNames=$fontModuleNames&styleModuleNames=$styleModuleNames&featureModuleNames=$featureModuleNames&rtl=$isRtl&night=$nightMode")
    }

     fun onEvent(e: ReloadAddonsEvent) {
        val fontModuleNames = json.encodeToString(serializer(), AndBibleAddons.fontModuleNames)
        val featureModuleNames = json.encodeToString(serializer(), AndBibleAddons.featureModuleNames)
        val styleModuleNames = json.encodeToString(serializer(), AndBibleAddons.styleModuleNames)
        executeJavascriptOnUiThread("bibleView.emit('reload_addons', {fontModuleNames: $fontModuleNames, featureModuleNames: $featureModuleNames, styleModuleNames: $styleModuleNames});")
    }

    override fun destroy() {
        toBeDestroyed = true
        gestureListener.destroy()
        pageTiltScroller.destroy()
        removeJavascriptInterface("android")
    }

    var listenEvents: Boolean = false
        set(value) {
            if(value == field) return
            if(value) {
                ABEventBus.register(this)
            } else {
                ABEventBus.unregister(this)
            }
            field = value
        }

    fun doDestroy() {
        if(!toBeDestroyed) {
            destroy()
        }
        listenEvents = false
        Log.i(TAG, "Destroying Bibleview")
        super.destroy()
        val win = windowRef.get()
        if(win != null && win.bibleView === this) {
            win.bibleView = null
        }
        onDestroy?.invoke()
    }

    object UriConstants {
        const val SCHEME_DOWNLOAD = "download"
        const val SCHEME_ERROR = "ab-error"
        const val SCHEME_W = "ab-w"
        const val SCHEME_REFERENCE = "osis"
        const val MULTI_REFERENCE = "multi"
        const val SCHEME_MYNOTES = "my-notes"
        const val SCHEME_EPUB_REF = "epub-ref"
        const val SCHEME_STUDYPAD = "journal"
        const val SCHEME_FIND_ALL_OCCURRENCES = "ab-find-all"
    }

    class ModuleAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return notFound
            val (bookName, resourcePath) = parts
            val location = File(Books.installed().getBook(bookName).bookMetaData.location)
            val f = File(location, resourcePath)
            return if(f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else notFound
        }
    }

    inner class EpubResourcesAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val book = (firstDocument as? OsisDocument)?.book ?: return notFound
            val file: File = ((book as? SwordGenBook)?.backend as? EpubBackend)?.getResource(path) ?: return notFound
            if(!file.canRead()) return notFound
            return WebResourceResponse(URLConnection.guessContentTypeFromName(file.name), null, file.inputStream())
        }
    }

    class ModuleStylesAssetHandler: PathHandler {
        private val epubRe = Regex("""^epub/([^/]+)/([^/]+)/style.css$""")
        private val epubRelatedResourcesRe = Regex("""^epub/([^/]+)/(.*)$""")
        private val colorRe = Regex("""\b(background-color|background|background-image|color):[^;]+;""")
        private val bodyRe = Regex("""\bbody\s*\{[^}]*\}""")
        override fun handle(path: String): WebResourceResponse {
            val epubMatch = epubRe.matchEntire(path)
            if(epubMatch != null) {
                val bookInitials = epubMatch.groupValues[1]
                val keyStr = epubMatch.groupValues[2]
                val book = Books.installed().getBook(bookInitials)?: return notFound
                if (!book.isEpub) return notFound
                val key = try { book.getKey(keyStr) } catch (e: NoSuchKeyException) {return notFound}

                val styleSheets =
                    (if(book is SwordGenBook) {
                        val backend = book.backend
                        if (backend is EpubBackend) {
                            backend.styleSheets(key)
                        } else null
                    }  else null) ?: return notFound

                val content = styleSheets.joinToString("\n") { String(it.readBytes()) }
                    .replace(colorRe, "")
                    .replace(bodyRe, "")
                return WebResourceResponse(URLConnection.guessContentTypeFromName(path), null, content.byteInputStream())
            }
            val resourceMatch = epubRelatedResourcesRe.matchEntire(path)
            if(resourceMatch != null) {
                val bookInitials = resourceMatch.groupValues[1]
                val relativePath = resourceMatch.groupValues[2]
                val book = Books.installed().getBook(bookInitials)?: return notFound
                if (!book.isEpub) return notFound
                val file: File = ((book as? SwordGenBook)?.backend as? EpubBackend)?.getResource(relativePath) ?: return notFound
                if(!file.canRead()) return notFound
                return WebResourceResponse(URLConnection.guessContentTypeFromName(file.name), null, file.inputStream())
            }
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return notFound
            val (bookName, resourcePath) = parts
            val book = Books.installed().getBook(bookName) ?: return notFound

            val styleFile = book.bookMetaData.getProperty("AndBibleCSS") ?: return notFound

            val location = File(book.bookMetaData.location)
            var f = File(location, styleFile)
            if(resourcePath != "style.css") {
                f = File(f.parent, resourcePath)
            }

            return if (f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else notFound
        }
    }

    class FontsAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return notFound
            val (moduleName, resourcePath) = parts
            val book = Books.installed().getBook(moduleName) ?: return notFound
            if(resourcePath == "fonts.css") {
                val fontCss = StringBuilder()
                val fonts = fontsByModule[book.initials] ?: return notFound
                for(font in fonts) {
                    fontCss.append("""@font-face {
                        |font-family: '${font.name}';
                        |src: url('${font.path}') format('truetype');
                        |}
                        |""".trimMargin())
                }
                return WebResourceResponse("text/css", null, fontCss.toString().byteInputStream())
            }
            else {
                val location = File(book.bookMetaData.location)
                val f = File(location, resourcePath)
                return if (f.isFile && f.exists()) {
                    WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
                } else notFound
            }
        }
    }

    class FeatureAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return notFound;
            val (moduleName, resourcePath) = parts
            val book = Books.installed().getBook(moduleName) ?: return notFound
            val location = File(book.bookMetaData.location)
            val f = File(location, resourcePath)
            return if (f.isFile && f.exists() && checkSignature(f)) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else notFound
        }

        private fun checkSignature(file: File): Boolean {
            val signatureFile = File(file.path + ".sign")
            return CommonUtils.verifySignature(file, signatureFile)
        }
    }
    inner class MyAssetsPathHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            return try {
                val inputStream = context.resources.assets.open(path)
                val mimeType = when(File(path).extension) {
                    "js" -> "application/javascript"
                    "html" -> "text/html"
                    "css" -> "text/css"
                    "svg" -> "image/svg+xml"
                    else -> "text/plain"
                }
                WebResourceResponse(mimeType, null, inputStream)
            } catch (e: IOException) {
                Log.e(TAG, "Error opening asset path: $path", e)
                notFound
            }
        }
    }
    class NotFoundHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse = notFound
    }

    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", MyAssetsPathHandler())
        .addPathHandler("/module/", ModuleAssetHandler())
        .addPathHandler("/fonts/", FontsAssetHandler())
        .addPathHandler("/features/", FeatureAssetHandler())
        .addPathHandler("/module-style/", ModuleStylesAssetHandler())
        .addPathHandler("/epub/", EpubResourcesAssetHandler())
        .addPathHandler("/", NotFoundHandler())
        .build()


    fun openLink(uri: Uri): Boolean = when(uri.scheme) {
        UriConstants.SCHEME_W -> {
            val links = mutableListOf<BibleLink>()
            for (paramName in uri.queryParameterNames) {
                links.addAll(uri.getQueryParameters(paramName).map { BibleLink(paramName, it) })
            }
            if (links.size > 1) {
                linkControl.openMulti(links)
            } else {
                linkControl.loadApplicationUrl(links.first(), null)
            }
            true
        }
        UriConstants.SCHEME_MYNOTES -> {
            val ordinal = uri.getQueryParameter("ordinal")?.toInt()!!
            val v11n = uri.getQueryParameter("v11n")
            linkControl.openMyNotes(v11n!!, ordinal)
        }
        UriConstants.SCHEME_EPUB_REF -> {
            val bookStr = uri.getQueryParameter("book")!!
            val keyStr = uri.getQueryParameter("toKey")!!
            val idStr = uri.getQueryParameter("toId")!!

            val book = Books.installed().getBook(bookStr) as SwordGenBook
            val backend = book.backend as EpubBackend
            val key = backend.getKey(keyStr, idStr)

            key?.let {linkControl.showLink(book, BookAndKey(it, book, htmlId = idStr)) }
            true
        }
        UriConstants.MULTI_REFERENCE -> {
            val osisRefs = uri.getQueryParameters("osis")
            val v11n = uri.getQueryParameter("v11n")
            if (osisRefs != null) {
                linkControl.openMulti(osisRefs.map { BibleLink("osis", it, v11n) })
            } else false
        }
        UriConstants.SCHEME_STUDYPAD -> {
            val id = uri.getQueryParameter("id")
            val bookmarkId = uri.getQueryParameter("bookmarkId")
            if (id != null) {
                linkControl.openStudyPad(IdType(id), IdType(bookmarkId))
            } else false
        }
        UriConstants.SCHEME_REFERENCE -> {
            val osisRef = uri.getQueryParameter("osis")
            val doc = uri.getQueryParameter("doc")
            val ordinal = uri.getQueryParameter("ordinal")
            val v11n = uri.getQueryParameter("v11n")
            val forceDoc = uri.getBooleanQueryParameter("force-doc", false)
            val book = Books.installed().getBook(doc)
            if(ordinal != null) {
                val bookKey = book!!.getKey(osisRef).let {if(it is RangedPassage) it.first() else it }
                linkControl.showLink(book, BookAndKey(bookKey, book, OrdinalRange(ordinal.toInt())))
            } else if (osisRef != null) {
                linkControl.loadApplicationUrl(BibleLink("osis", osisRef.trim(), v11n, forceDoc = forceDoc), book)
            } else {
                val contentRef = uri.getQueryParameter("content")!!
                linkControl.loadApplicationUrl(BibleLink("content", contentRef.trim(), v11n, forceDoc = forceDoc), book)
            }
            true
        }
        UriConstants.SCHEME_FIND_ALL_OCCURRENCES -> {
            val type = uri.getQueryParameter("type")!!
            var name = uri.getQueryParameter("name")!!.lowercase()
            if(!(name.startsWith("g") || name.startsWith("h"))) {
                name = type[0] + name
            }
            linkControl.showAllOccurrences(name, SearchControl.SearchBibleSection.ALL)
            true
        }
        UriConstants.SCHEME_ERROR -> {
            linkControl.errorLink()
            true
        }
        UriConstants.SCHEME_DOWNLOAD -> {
            val initials = uri.getQueryParameter("initials")

            val intent = Intent(mainBibleActivity, DownloadActivity::class.java)
            intent.putExtra("search", initials)
            mainBibleActivity.startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
            true
        }
        else -> {
            Log.e(TAG, "Unsupported scheme ${uri.scheme}")
            true
        }
    }

    private inner class BibleViewClient: WebViewClient() {
        override fun onLoadResource(view: WebView, url: String) {
            Log.i(TAG, "onLoadResource:$url")
            super.onLoadResource(view, url)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
            return if(openLink(Uri.parse(url))) {
                true
            } else {
                super.shouldOverrideUrlLoading(view, url)
            }
        }
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            return if(openLink(req.url)) {
                true
            } else {
                super.shouldOverrideUrlLoading(view, req)
            }
        }


        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            Log.e(TAG, description)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)
    }

    private var contextMenuInfo: BibleViewContextMenuInfo? = null
    override fun getContextMenuInfo(): ContextMenuInfo? {
        return contextMenuInfo
    }

    private inner class BibleViewLongClickListener() : OnLongClickListener {
        override fun onLongClick(v: View): Boolean {
            Log.i(TAG, "onLongClickListener")
            val result = hitTestResult
            return if (result.type == HitTestResult.SRC_ANCHOR_TYPE) {
                contextMenuInfo = LinkLongPressContextMenuInfo(result.extra!!)
                v.showContextMenu()
            } else {
                contextMenuInfo = null
                false
            }
        }
    }

    internal interface BibleViewContextMenuInfo: ContextMenuInfo {
        fun onContextItemSelected(item: MenuItem): Boolean
        fun onCreateContextMenu(menu: ContextMenu, v: View, menuInflater: MenuInflater)
    }

    internal inner class LinkLongPressContextMenuInfo(private val targetLink: String) : BibleViewContextMenuInfo {
        override fun onContextItemSelected(item: MenuItem): Boolean {
            val uri = Uri.parse(targetLink)
            if(item.itemId == R.id.copy_link_to_clipboard) {
                val osisRef = uri.getQueryParameter("osis")?: return false
                val doc = uri.getQueryParameter("doc")
                val ordinal = uri.getQueryParameter("ordinal")?.toInt()
                val v11n = uri.getQueryParameter("v11n")
                val abUrl = CommonUtils.makeAndBibleUrl(
                    osisRef,
                    doc,
                    v11n,
                    ordinal
                )
                clipboardKey = parseAndBibleReference(abUrl)
                CommonUtils.copyToClipboard(
                    ClipData.newPlainText(abUrl, abUrl),
                    R.string.reference_copied_to_clipboard
                )
            } else {
                val windowMode = when (item.itemId) {
                    R.id.open_link_in_special_window -> WindowMode.WINDOW_MODE_SPECIAL
                    R.id.open_link_in_new_window -> WindowMode.WINDOW_MODE_NEW
                    R.id.open_link_in_this_window -> WindowMode.WINDOW_MODE_THIS
                    else -> WindowMode.WINDOW_MODE_UNDEFINED
                }
                linkControl.windowMode = windowMode
                openLink(uri)
                linkControl.windowMode = WindowMode.WINDOW_MODE_UNDEFINED
                contextMenuInfo = null
            }
            return true
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.link_context_menu, menu)

            val parsed: Uri = Uri.parse(targetLink)
            menu.findItem(R.id.copy_link_to_clipboard).isVisible = parsed.scheme == UriConstants.SCHEME_REFERENCE
            val openLinksInSpecialWindowByDefault = CommonUtils.settings.getBoolean("open_links_in_special_window_pref", true)
            val item =
                if(openLinksInSpecialWindowByDefault)
                    menu.findItem(R.id.open_link_in_special_window)
                else
                    menu.findItem(R.id.open_link_in_this_window)
            item.isVisible = false
        }
    }

    val backgroundColor: Int get() {
        val colors = window.pageManager.actualTextDisplaySettings.colors
        val monochromeMode = CommonUtils.settings.monochromeMode
        val nightBackground = if(monochromeMode) black else colors?.nightBackground
        val dayBackground = if(monochromeMode) white else colors?.dayBackground
        return (if(ScreenSettings.nightMode) nightBackground else dayBackground) ?: UiUtils.bibleViewDefaultBackgroundColor
    }

    var lastUpdated = 0L
    var bookmarkLabels: List<BookmarkEntities.Label> = emptyList()

    var firstDocument: Document? = null

    private val documentLoadingLock = object {}

    suspend fun loadDocument(document: Document,
                             updateLocation: Boolean = false,
                             key: Key? = null,
                             anchorOrdinal: OrdinalRange? = null,
                             htmlId: String? = null,
    )
    {
        val currentPage = window.pageManager.currentPage

        // make sure this has been created
        bookmarkControl.labelUnlabelled
        bookmarkControl.speakLabel

        bookmarkLabels = bookmarkControl.assignableLabels.toMutableList()
        initialKey = key

        initialAnchorOrdinal = anchorOrdinal
        initialHtmlId = htmlId

        if (lastUpdated == 0L || updateLocation) {
            if (listOf(DocumentCategory.BIBLE, DocumentCategory.MYNOTE).contains(currentPage.documentCategory)) {
                initialKey = KeyUtil.getVerse(window.pageManager.currentBibleVerse.verse)
            } else {
                initialAnchorOrdinal = currentPage.anchorOrdinal
                initialHtmlId = currentPage.htmlId
            }
        }

        contentVisible = false

        val chapter = (initialKey as? Verse)?.chapter
        if (chapter != null) {
            addChapter(chapter)
        }

        Log.i(TAG, "Show $initialKey, $initialAnchorOrdinal Window:$window, settings: topOffset:${topOffset}, \n actualSettings: ${displaySettings.toJson()}")
        this.firstDocument = document
        synchronized(documentLoadingLock) {
            var docStr = document.asJson
            // Ps 119 in KJV is only 70k. Let's give gracefully max 4000k until we give "page too large" error.
            // Our BibleView.js will freeze and eventually OOM-crash with ridiculously large documents.
            if(docStr.length > MAX_DOC_STR_LENGTH) {
                Log.e(TAG, "Page is too large to be shown, showing error instead, ${docStr.length}")
                val errorDoc = ErrorDocument(mainBibleActivity.getString(R.string.error_page_too_large), ErrorSeverity.NORMAL)
                docStr = errorDoc.asJson
                firstDocument = errorDoc
            }

            latestDocumentStr = docStr

            needsDocument = true
        }

        withContext(Dispatchers.Main) {
            enableZoomForMap(pageControl.currentPageManager.isMapShown)
        }

        if(!htmlLoadingOngoing) {
            withContext(Dispatchers.Main) {
                replaceDocument()
            }
        }
    }

    private fun addChapter(chapter: Int) {
        when {
            chapter < minChapter -> minChapter = chapter
            chapter > maxChapter -> maxChapter = chapter
            else -> Log.e(TAG, "Chapter already included")
        }
    }

    private var initialAnchorOrdinal: OrdinalRange? = null
    private var initialHtmlId: String? = null
    internal var initialKey: Key? = null
        set(value) {
            firstKey = value
            lastKey = value
            field = value
        }
    private var lastKey: Key? = null
    private var firstKey: Key? = null
    private val displaySettings get() = window.pageManager.actualTextDisplaySettings
    internal val workspaceSettings get() = windowControl.windowRepository.workspaceSettings

    fun updateTextDisplaySettings(onAttach: Boolean = false) {
        Log.i(TAG, "updateTextDisplaySettings")
        updateBackgroundColor()
        updateConfig(onAttach)
    }

    private val hasActiveIndicator get() =
        CommonUtils.settings.getBoolean("show_active_window_indicator", true)
            && isActive && windowControl.windowRepository.visibleWindows.size > 1

    private val isActive get() = windowControl.activeWindow.id == window.id

    private fun updateActive() =
        executeJavascriptOnUiThread("""bibleView.emit('set_active', {isActive: $isActive, hasActiveIndicator: $hasActiveIndicator})""")

    private val showErrorBox get() = if(CommonUtils.isBeta) CommonUtils.settings.getBoolean("show_errorbox", false) else false

    private fun getUpdateConfigCommand(initial: Boolean): String {
        val favouriteLabels = json.encodeToString(serializer(), bookmarkControl.favouriteLabels.map {it.id})
        val recentLabels = json.encodeToString(serializer(), workspaceSettings.recentLabels.map { it.labelId })
        val hideCompareDocuments = json.encodeToString(serializer(), workspaceSettings.hideCompareDocuments)
        val limitAmbiguousModalSize = json.encodeToString(serializer(), workspaceSettings.limitAmbiguousModalSize)
        val bibleModalButtons = json.encodeToString(serializer(),
            CommonUtils.settings.getStringSet("bible_bookmark_modal_buttons", setOf("BOOKMARK", "BOOKMARK_NOTES", "MY_NOTES", "SHARE", "COMPARE"))
        )
        val genericModalButtons = json.encodeToString(serializer(),
            CommonUtils.settings.getStringSet("gen_bookmark_modal_buttons", setOf("BOOKMARK", "BOOKMARK_NOTES", "SPEAK"))
        )
        val monochromeMode = CommonUtils.settings.monochromeMode
        val disableAnimations = CommonUtils.settings.disableAnimations
        return """
                bibleView.emit('set_config', {
                    config: ${displaySettings.toJson()}, 
                    appSettings: {
                        activeWindow: $isActive,
                        isBottomWindow: $isBottomWindow,
                        hasActiveIndicator: $hasActiveIndicator, 
                        nightMode: $nightMode, 
                        errorBox: $showErrorBox, 
                        favouriteLabels: $favouriteLabels, 
                        recentLabels: $recentLabels, 
                        hideCompareDocuments: $hideCompareDocuments,
                        limitAmbiguousModalSize: $limitAmbiguousModalSize,
                        windowId: '${window.displayId}',
                        bibleModalButtons: $bibleModalButtons, 
                        genericModalButtons: $genericModalButtons, 
                        monochromeMode: $monochromeMode,
                        disableAnimations: $disableAnimations,
                        fontSizeMultiplier: ${CommonUtils.settings.fontSizeMultiplierFloat},
                    }, 
                    initial: $initial,
                    });
                """
    }

    fun onEvent(event: AppSettingsUpdated) {
        updateConfig()
    }

    private fun updateConfig(initial: Boolean = false) {
        executeJavascriptOnUiThread(getUpdateConfigCommand(initial))
    }

    fun updateBackgroundColor() {
        Log.i(TAG, "updateBackgroundColor")
        setBackgroundColor(backgroundColor)
    }

    private val nightMode get() = mainBibleActivity.currentNightMode

    private var labelsUploaded = false

    fun adjustLoadingCount(adj: Int): Boolean {
        return executeJavascriptOnUiThread("""bibleView.emit("adjust_loading_count", ${adj})""")
    }

    private fun replaceDocument() {
        Log.i(TAG, "replaceDocument")

        val verse = if(isBible || isMyNotes) initialKey as? Verse else null
        val documentStr = synchronized(documentLoadingLock) {
            if(latestDocumentStr == null || !needsDocument) return
            needsDocument = false
            contentVisible = true
            minChapter = verse?.chapter ?: -1
            maxChapter = verse?.chapter ?: -1
            latestDocumentStr
        }

        if(!labelsUploaded) {
            val bookmarkLabels = json.encodeToString(serializer(), bookmarkLabels.map { ClientBookmarkLabel(it) })
            executeJavascriptOnUiThread("""bibleView.emit("update_labels", ${bookmarkLabels})""")
            labelsUploaded = true
       }

        val doc = firstDocument
        val jumpToId =
            if(doc is StudyPadDocument && doc.bookmarkId != null)
                "o-${abs(doc.bookmarkId.hashCode())}"
            else initialHtmlId

        executeJavascriptOnUiThread("""
            bibleView.emit("clear_document");
            ${getUpdateConfigCommand(true)}
            bibleView.emit("add_documents", $documentStr);
            bibleView.emit("setup_content", {
                jumpToOrdinal: ${verse?.ordinal}, 
                jumpToAnchor: ${initialAnchorOrdinal?.start},
                jumpToId: ${wrapString(jumpToId)},
                topOffset: $topOffset,
                bottomOffset: $bottomOffset,
            });            
            bibleView.emit("set_title", "BibleView-${window.displayId}");
            """
        )
    }

    /**
     * Enable or disable zoom controls depending on whether map is currently shown
     */
    private fun enableZoomForMap(isMap: Boolean) {
        Log.i(TAG, "enableZoomForMap $isMap")
        settings.builtInZoomControls = true
        settings.setSupportZoom(isMap)
        settings.displayZoomControls = false
        // http://stackoverflow.com/questions/3808532/how-to-set-the-initial-zoom-width-for-a-webview
        settings.loadWithOverviewMode = isMap
        //settings.useWideViewPort = isMap
    }


    var contentVisible = false

    /** prevent swipe right if the user is scrolling the page right  */
    override val isPageNextOkay: Boolean get () {
        var isOkay = true
        if(modalOpen) return false
        if(firstDocument is StudyPadDocument) return false
        if (window.pageManager.isMapShown) {
            // allow swipe right if at right side of map
            val isAtRightEdge = if(CommonUtils.isRtl) scrollX == 0 else scrollX >= maxHorizontalScroll

            // the first side swipe takes us to the edge and second takes us to next page
            isOkay = isAtRightEdge && wasAtRightEdge
            wasAtRightEdge = isAtRightEdge
            wasAtLeftEdge = false
        }
        return isOkay
    }

    /** prevent swipe left if the user is scrolling the page left  */
    override val isPagePreviousOkay: Boolean get () {
        var isOkay = true
        if(modalOpen) return false
        if(firstDocument is StudyPadDocument) return false
        if (window.pageManager.isMapShown) {
            // allow swipe left if at left edge of map
            val isAtLeftEdge = if(!CommonUtils.isRtl) scrollX == 0 else scrollX >= maxHorizontalScroll

            // the first side swipe takes us to the edge and second takes us to next page
            isOkay = isAtLeftEdge && wasAtLeftEdge
            wasAtLeftEdge = isAtLeftEdge
            wasAtRightEdge = false
        }
        return isOkay
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Log.i(TAG, "Focus changed so start/stop scroll $hasWindowFocus")
        if (hasWindowFocus) {
            resumeTiltScroll()
        } else {
            pauseTiltScroll()
        }
    }

    private fun pauseTiltScroll() {
        pageTiltScroller.enableTiltScroll(false)
    }

    private fun resumeTiltScroll() {
        // but if multiple windows then only if the current active window
        if (windowControl.isActiveWindow(window)) {
            pageTiltScroller.enableTiltScroll(true)
        }
    }

    var lastTouched: Long = 0L

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        lastTouched = System.currentTimeMillis()
        super.onScrollChanged(l, t, oldl, oldt)
    }

    /** ensure auto-scroll does not continue when screen is powered off
     */
    override fun onScreenTurnedOn() {
        resumeTiltScroll()
    }

    override fun onScreenTurnedOff() {
        pauseTiltScroll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        windowControl.activeWindow = window

        val handled = super.onTouchEvent(event)

        // Allow user to redefine viewing angle by touching screen
        pageTiltScroller.recalculateViewingPosition()

        return handled
    }

    fun scroll(forward: Boolean, scrollAmount: Int): Boolean {
        var ok = false
        hideScrollBar = true
        for (i in 0 until scrollAmount) {
            if (forward) {
                // scroll down/forward if not at bottom
                if (scrollY + 1 < maxVerticalScroll) {
                    scrollBy(0, 1)
                    ok = true
                }
            } else {
                // scroll up/backward if not at top
                if (scrollY > TOP_OF_SCREEN) {
                    // scroll up/back
                    scrollBy(0, -1)
                    ok = true
                }
            }
        }
        hideScrollBar = false
        return ok
    }

    /** allow vertical scroll bar to be hidden during auto-scroll
     */
    override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean {
        return if (!hideScrollBar) {
            super.awakenScrollBars(startDelay, invalidate)
        } else {
            false
        }
    }

    override fun asView(): View {
        return this
    }

    fun onEvent(event: BookmarkAddedOrUpdatedEvent) {
        val document = firstDocument

        val clientBookmark = when(event.bookmark) {
            is BookmarkEntities.BibleBookmarkWithNotes ->
                ClientBibleBookmark(event.bookmark,
                    when (document) {
                        is BibleDocument -> document.swordBook.versification
                        is MyNotesDocument -> KJVA
                        else -> null
                    })
            is BookmarkEntities.GenericBookmarkWithNotes -> ClientGenericBookmark(event.bookmark)
            else -> throw RuntimeException("Invalid type")
        }

        val bookmarkStr = clientBookmark.asJson
        executeJavascriptOnUiThread("""bibleView.emit("add_or_update_bookmarks",  [$bookmarkStr]);""")
    }

    fun onEvent(event: BookmarkNoteModifiedEvent) {
        executeJavascriptOnUiThread("""
            bibleView.emit("bookmark_note_modified", {id: "${event.bookmarkId}", lastUpdatedOn: ${event.lastUpdatedOn}, notes: ${json.encodeToString(serializer(), event.notes)}});
        """)
    }

    fun onEvent(event: StudyPadOrderEvent) {
        val doc = firstDocument
        if(doc !is StudyPadDocument || doc.label.id != event.labelId) return
        val studyPadTextEntryJson = json.encodeToString(serializer(), event.newStudyPadTextEntry)
        val bookmarkToLabels = json.encodeToString(serializer(), event.bookmarkToLabelsOrderChanged)
        val genericBookmarkToLabels = json.encodeToString(serializer(), event.genericBookmarkToLabelsOrderChanged)
        val studyPadItems = json.encodeToString(serializer(), event.studyPadOrderChanged)
        executeJavascriptOnUiThread("""
            bibleView.emit("add_or_update_study_pad",  {
                studyPadTextEntry: $studyPadTextEntryJson, 
                bookmarkToLabelsOrdered: $bookmarkToLabels, 
                genericBookmarkToLabelsOrdered: $genericBookmarkToLabels, 
                studyPadItemsOrdered: $studyPadItems
                });
        """)
    }

    fun onEvent(event: BookmarkToLabelAddedOrUpdatedEvent) {
        val doc = firstDocument
        if(doc !is StudyPadDocument || doc.label.id != event.bookmarkToLabel.labelId) return
        val bookmarkToLabelStr = when(event.bookmarkToLabel) {
            is BookmarkEntities.BibleBookmarkToLabel ->json.encodeToString(serializer(), event.bookmarkToLabel)
            is BookmarkEntities.GenericBookmarkToLabel -> json.encodeToString(serializer(), event.bookmarkToLabel)
            else -> throw RuntimeException("Illegal type")
        }
        executeJavascriptOnUiThread("""
            bibleView.emit("add_or_update_bookmark_to_label", $bookmarkToLabelStr);
        """)
    }

    fun onEvent(event: StudyPadTextEntryDeleted) {
        if(firstDocument !is StudyPadDocument) return
        executeJavascriptOnUiThread("""
            bibleView.emit("delete_study_pad_text_entry", "${event.studyPadTextEntryId}");
        """)
    }

    fun onEvent(event: LabelAddedOrUpdatedEvent) {
        val labelStr = json.encodeToString(serializer(), ClientBookmarkLabel(event.label))
        executeJavascriptOnUiThread("""bibleView.emit("update_labels", [$labelStr])""")
    }

    fun onEvent(event: BookmarksDeletedEvent) {
        val bookmarkIds = json.encodeToString(serializer(), event.bookmarkIds)
        executeJavascriptOnUiThread("bibleView.emit('delete_bookmarks', $bookmarkIds)")
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        if (window == event.activeWindow) {
            bibleJavascriptInterface.notificationsEnabled = true
            resumeTiltScroll()
        } else {
            bibleJavascriptInterface.notificationsEnabled = false
            pauseTiltScroll()
        }
        updateActive()
    }

    fun onEvent(event: ScrollSecondaryWindowEvent) {
        if (window == event.window) {
            scrollOrJumpToVerse(event.verse)
        }
    }

    private var checkWindows = false

    fun onEvent(event: MainBibleActivity.ConfigurationChanged) {
        checkWindows = true
    }

    fun onEvent(event: NumberOfWindowsChangedEvent) {
        if(window.isVisible) {
            updateOffsets(true)
            updateConfig()
        }
    }

    fun onEvent(event: MainBibleActivity.FullScreenEvent) = updateOffsets()

    fun onEvent(event: RestoreButtonsVisibilityChanged) = updateOffsets()

    fun onEvent(event: SpeakTransportVisibilityChanged) = updateOffsets(true)

    private fun updateOffsets(immediate: Boolean = false) {
        if(isTopWindow || isBottomWindow && contentVisible && window.isVisible)
            executeJavascriptOnUiThread("bibleView.emit('set_offsets', $topOffset, $bottomOffset, {immediate: $immediate});")
    }

    fun onEvent(event: WebViewsBuiltEvent) {
        checkWindows = true
    }

    private val isTopWindow
        get() = !mainBibleActivity.isSplitVertically || windowControl.windowRepository.firstVisibleWindow == window

    private val isBottomWindow
        get() = !mainBibleActivity.isSplitVertically || windowControl.windowRepository.lastVisibleWindow == window

    val topOffset
        get() =
            if(isTopWindow && !SharedActivityState.instance.isFullScreen)
                (mainBibleActivity.topOffset2
                    / mainBibleActivity.resources.displayMetrics.density)
            else 0F
    
    val bottomOffset
        get() =
            if(isBottomWindow)
                (mainBibleActivity.bottomOffset3
                    / mainBibleActivity.resources.displayMetrics.density)
            else 0F

    private var separatorMoving = false

    fun onEvent(event: WindowSizeChangedEvent) {
        Log.i(TAG, "window size changed")
        separatorMoving = !event.isFinished
        if(!separatorMoving && !mainBibleActivity.isSplitVertically) {
            checkWindows = true
            doCheckWindows()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if(lastUpdated != 0L && !separatorMoving && w != ow) {
            doCheckWindows()
        }
    }

    private fun doCheckWindows() {
        if(!htmlLoadingOngoing && checkWindows) {
            executeJavascript("bibleView.emit('set_offsets', $topOffset, $bottomOffset, {doNotScroll: true});")
            if (window.pageManager.currentPage.documentCategory == DocumentCategory.BIBLE) {
                scrollOrJumpToVerse(window.pageManager.currentBible.currentBibleVerse.verse, true)
            }
            checkWindows = false
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.i(TAG, "Detached from window")
        // prevent random verse changes while layout is being rebuild because of window changes
        bibleJavascriptInterface.notificationsEnabled = false
        pauseTiltScroll()
    }

    fun onEventMainThread(event: WebViewsBuiltEvent) {
        if(toBeDestroyed)
            doDestroy()
    }

    fun onEventMainThread(event: AfterRemoveWebViewEvent) {
        if(toBeDestroyed)
            doDestroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.i(TAG, "Attached to window")
        if (windowControl.isActiveWindow(window)) {
            bibleJavascriptInterface.notificationsEnabled = true

            resumeTiltScroll()
        }
        if(contentVisible) {
            updateTextDisplaySettings(true)
        }
        flushTasks()
    }

    fun scrollOrJumpToVerse(key: Key, forceNow: Boolean = false) {
        Log.i(TAG, "Scroll or jump to:$key")
        var toVerse: Verse
        var endVerse: Verse? = null
        when (key) {
            is VerseRange -> {
                toVerse = key.start
                endVerse = key.end
            }
            is Verse -> toVerse = key
            is RangedPassage -> {
                val range = key.toVerseRange
                toVerse = range.start
                endVerse = range.end
            }
            else -> throw RuntimeException("illegal type")
        }
        val v = initialKey as? Verse
        if(firstDocument is MyNotesDocument) {
            toVerse = toVerse.toV11n(KJVA)
            endVerse = endVerse?.toV11n(KJVA)
        } else if(v != null) {
            toVerse = toVerse.toV11n(v.versification)
            endVerse = endVerse?.toV11n(v.versification)
        }
        val jumpToId = "o-${toVerse.ordinal}"
        val now = !contentVisible || forceNow
        val highlight = !contentVisible || endVerse != null
        fun boolString(value: Boolean?): String {
            if(value == null) return "null"
            return if(value) "true" else "false"
        }
        executeJavascriptOnUiThread("bibleView.emit('scroll_to_verse', '$jumpToId', {now: ${boolString(now)}, highlight: ${boolString(highlight)}, ordinalStart: ${toVerse.ordinal}, ordinalEnd: ${endVerse?.ordinal}});")
        if(isActive) {
            PassageChangeMediator.onCurrentVerseChanged(window)
        }
    }
    fun scrollOrJumpToOrdinal(ordinal: OrdinalRange?, htmlId: String?, bookInitials: String?, osisRef: String?, forceNow: Boolean = false) {
        Log.i(TAG, "Scroll or jump to ordinal:$ordinal")
        val now = !contentVisible || forceNow
        fun boolString(value: Boolean?): String {
            if(value == null) return "null"
            return if(value) "true" else "false"
        }

        val highlight = !contentVisible || ordinal?.end != null
        val jumpToId = if(ordinal != null) "o-${ordinal.start}" else htmlId!!

        executeJavascriptOnUiThread("bibleView.emit('scroll_to_verse', '$jumpToId', {now: ${boolString(now)}, highlight: ${boolString(highlight)}, bookInitials: '$bookInitials', osisRef: '$osisRef', ordinalStart: ${ordinal?.start}, ordinalEnd: ${ordinal?.end}});")
        if(isActive) {
            PassageChangeMediator.onCurrentVerseChanged(window)
        }
    }

    fun highlightOrdinalRange(bookInitials: String, osisRef: String, range: IntRange) {
        executeJavascriptOnUiThread("bibleView.emit('scroll_to_verse', null, {now: false, highlight: true, bookInitials: '$bookInitials', osisRef: '$osisRef', ordinalStart: ${range.first}, ordinalEnd: ${range.last}});")
    }

    fun highlightBibleOrdinalRange(range: IntRange) {
        executeJavascriptOnUiThread("bibleView.emit('scroll_to_verse', null, {now: false, highlight: true, ordinalStart: ${range.first}, ordinalEnd: ${range.last}});")
    }
    fun executeJavascriptOnUiThread(javascript: String): Boolean {
        if(htmlLoadingOngoing) {
            Log.e(TAG,"HTML not yet ready, js execution is doomed to fail. $javascript")
            return false
        }
        runOnUiThread { executeJavascript(javascript) }
        return true
    }

    private val taskQueue = LinkedList<() -> Unit>()

    private val uiThreadLock = object {}
    private fun runOnUiThread(runnable: () -> Unit) = synchronized(uiThreadLock) {
        // If there are any tasks, we must put them to queue, to make sure they are run in the correct order
        val wasEmpty = taskQueue.isEmpty()
        val isAttached = isAttachedToWindow

        if(Looper.myLooper() == Looper.getMainLooper() && wasEmpty && isAttached) {
            Log.i(TAG, "TaskQueue Executing runnable immediately")
            runnable()
        } else {
            Log.i(TAG, "TaskQueue Adding runnable to queue")
            taskQueue.addLast(runnable)
            if (wasEmpty && isAttached) {
                Log.i(TAG, "TaskQueue Scheduling flushing tasks")
                post { flushTasks() }
            }
        }
    }

    private fun flushTasks()  = synchronized(uiThreadLock) {
        Log.i(TAG, "TaskQueue flushTasks ${taskQueue.size}")
        while (taskQueue.size > 0) {
            taskQueue.pop().invoke()
        }
        Log.i(TAG, "TaskQueue flushTasks done.")
    }

    private fun executeJavascript(javascript: String, callBack: ((rv: String) -> Unit)? = null) {
        Log.i(TAG, "Executing JS: ${javascript.slice(0 until  min(javascript.length, 500))}")
        if(htmlLoadingOngoing) {
            Log.e(TAG,"HTML not yet ready, js execution is doomed to fail. $javascript")
            return;
        }
        evaluateJavascript("$javascript;", callBack)
    }

    private suspend fun evaluateJavascriptAsync(javascript: String): String {
        val result = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            evaluateJavascript(javascript) { result.complete(it) }
        }
        return result.await()
    }

    private val isBible get() = firstDocument is BibleDocument
    private val isCommentary get() = (firstDocument as? OsisDocument)?.book?.bookCategory == BookCategory.COMMENTARY
    private val isMyNotes get() = firstDocument is MyNotesDocument

    val verseRangeLoaded: VerseRange? get() {
        val key = (firstKey as? Verse)?: return null
        return CommonUtils.getWholeChapters(key.versification, key.book, minChapter, maxChapter)
    }

    private val requestMoreLock = object {}

    fun requestMoreToBeginning(callId: Long) = synchronized(requestMoreLock) {
        Log.i(TAG, "requestMoreTextAtTop")
        if (isBible) {
            val newChap = minChapter - 1
            if (newChap < 1) {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            addChapter(newChap)

            val currentPage = window.pageManager.currentBible
            scope.launch(Dispatchers.IO) {
                val doc = currentPage.getDocumentForChapter(newChap)
                executeJavascriptOnUiThread("bibleView.response($callId, ${doc.asJson});")
            }
        } else {
            val currentPage = window.pageManager.currentGeneralBook
            firstKey ?: run {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            val prevKey = currentPage.getKeyPlus(firstKey, -1)
            if(prevKey == firstKey) {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }

            firstKey = prevKey

            scope.launch(Dispatchers.IO) {
                val doc = currentPage.getPageContent(prevKey)
                executeJavascriptOnUiThread("bibleView.response($callId, ${doc.asJson});")
            }
        }
    }

    fun requestMoreToEnd(callId: Long) = synchronized(requestMoreLock) {
        Log.i(TAG, "requestMoreTextAtEnd")
        if (isBible) {
            val currentPage = window.pageManager.currentBible
            val newChap = maxChapter + 1
            val verse = currentPage.currentBibleVerse.verse
            val lastChap = verse.versification.getLastChapter(verse.book)
            if (newChap > lastChap) {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            addChapter(newChap)

            scope.launch(Dispatchers.IO) {
                val doc = currentPage.getDocumentForChapter(newChap)
                executeJavascriptOnUiThread("bibleView.response($callId, ${doc.asJson});")
            }
        } else {
            val currentPage = window.pageManager.currentGeneralBook
            lastKey ?: run {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            val nextKey = currentPage.getKeyPlus(lastKey, 1)
            if(nextKey == lastKey) {
                executeJavascriptOnUiThread("bibleView.response($callId, null);")
                return@synchronized
            }
            lastKey = nextKey
            scope.launch(Dispatchers.IO) {
                val doc = currentPage.getPageContent(nextKey)
                executeJavascriptOnUiThread("bibleView.response($callId, ${doc.asJson});")
            }
        }
    }

    fun hasChapterLoaded(chapter: Int) = chapter in minChapter..maxChapter

    fun setClientReady() = runOnUiThread {
        htmlLoadingOngoing = false
        replaceDocument()
        updateActive()
    }

    var modalOpen = false

    private fun closeModals() {
        executeJavascriptOnUiThread("bibleView.emit('close_modals')")
    }

    fun backButtonPressed(): Boolean {
        if(modalOpen) {
            closeModals()
            return true;
        }
        return false
    }

    fun volumeUpPressed(): Boolean {
        executeJavascriptOnUiThread("bibleView.emit('scroll_up')")
        return true
    }

    fun volumeDownPressed(): Boolean {
        executeJavascriptOnUiThread("bibleView.emit('scroll_down')")
        return true
    }

    fun exportHtml() {
        executeJavascriptOnUiThread("bibleView.emit('export_html')")
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if(focused && windowRepository.activeWindow.id != window.id) {
            windowRepository.activeWindow = window
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
    }

    fun parseRef(callId: Long, s: String) {
        val ref = wrapString(linkControl.resolveRef(s, (firstDocument as? BibleDocument)?.swordBook)?.osisRef)
        executeJavascriptOnUiThread("bibleView.response($callId, $ref);")
    }

    var onDestroy: (() -> Unit)? = null

    private val TAG get() = "BibleView[${windowRef.get()?.displayId}]"

    companion object {
        // never go to 0 because a bug in Android prevents invalidate after loadDataWithBaseURL so
        // no scrollOrJumpToVerse will occur
        private const val TOP_OF_SCREEN = 1
    }

    private val currentPageManager: CurrentPageManager
        get() = windowControl.activeWindowPageManager


}
