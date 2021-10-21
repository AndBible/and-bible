/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.text.TextUtils
import android.util.LayoutDirection
import android.util.Log
import android.view.ActionMode
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.KeyEvent
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
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.serializer
import net.bible.android.activity.R
import net.bible.android.common.toV11n
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
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.ClientBookmark
import net.bible.android.control.page.ClientBookmarkLabel
import net.bible.android.control.page.Document
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.DocumentWithBookmarks
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.DecrementBusyCount
import net.bible.android.control.page.window.IncrementBusyCount
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.versification.toVerseRange
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.json
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
import net.bible.android.view.util.UiUtils
import net.bible.android.view.util.widget.ShareWidget
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.AndBibleAddons.fontsByModule
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.buildActivityComponent
import net.bible.service.common.ReloadAddonsEvent
import net.bible.service.device.ScreenSettings
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.SystemKJVA
import org.crosswire.jsword.versification.system.Versifications
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLConnection
import java.util.*
import javax.inject.Inject
import kotlin.math.min


class BibleViewInputFocusChanged(val view: BibleView, val newFocus: Boolean)
class AppSettingsUpdated


@Serializable
class Selection(val bookInitials: String?, val startOrdinal: Int,
                val startOffset: Int?, val endOrdinal: Int, val endOffset: Int?,
                val bookmarks: List<Long>,
                val notes: String? = null,
                val text: String = ""
)
{
    constructor(bookmark: BookmarkEntities.Bookmark):
        this(
            bookmark.book?.initials,
            bookmark.ordinalStart,
            bookmark.startOffset,
            bookmark.ordinalEnd,
            bookmark.endOffset,
            emptyList(),
            bookmark.notes
        )

    constructor(bookInitials: String, startOrdinal: Int, endOrdinal: Int?):
        this(
            bookInitials,
            startOrdinal,
            0,
            endOrdinal?: startOrdinal,
            null,
            emptyList(),
            null
        )

    @Transient @Inject lateinit var windowControl: WindowControl

    init {
        buildActivityComponent().inject(this)
    }

    val hasRange get() = startOffset != null && endOffset != null

    val book: SwordBook get() = (Books.installed().getBook(bookInitials) as SwordBook?) ?: windowControl.defaultBibleDoc(false)
    val verseRange: VerseRange get() {
        val v11n = book.versification ?: KJVA
        return VerseRange(v11n, Verse(v11n, startOrdinal), Verse(v11n, endOrdinal))
    }
}

/** The WebView component that shows the bible and other documents */
@SuppressLint("ViewConstructor")
class BibleView(val mainBibleActivity: MainBibleActivity,
                internal var windowRef: WeakReference<Window>,
                val windowControl: WindowControl,
                private val bibleKeyHandler: BibleKeyHandler,
                private val pageControl: PageControl,
                private val pageTiltScrollControl: PageTiltScrollControl,
                val linkControl: LinkControl,
                internal val bookmarkControl: BookmarkControl,
                internal val downloadControl: DownloadControl,
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

    private val gestureListener  = BibleGestureListener(mainBibleActivity)

    private var toBeDestroyed = false

    @Volatile private var latestDocumentStr: String? = null
    @Volatile private var needsDocument: Boolean = false
    @Volatile private var htmlLoadingOngoing: Boolean = true
        set(value) {
            if(value != field) {
                ABEventBus.getDefault().post(if (value) IncrementBusyCount() else DecrementBusyCount())
            }
            field = value
        }

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
        setOnLongClickListener(BibleViewLongClickListener())
    }

    var showSystem = false

    var step2 = false

    private fun onActionMenuItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.add_bookmark -> {
                step2 = true
                mode.menu.clear()
                mode.invalidate()
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
                val sel = currentSelection
                if (sel != null)
                    ShareWidget.dialog(mainBibleActivity, sel)
                return true
            }
            R.id.system_items -> {
                showSystem = true
                mode.menu.clear()
                mode.invalidate()
                return false
            }
            else -> false
        }
    }

    fun makeBookmark(selection: Selection? = currentSelection, wholeVerse: Boolean = false, openNotes: Boolean = false) {
        selection?: return
        Log.d(TAG, "makeBookmark")
        val book = Books.installed().getBook(selection.bookInitials)
        if(book !is SwordBook) {
            // TODO: error response to JS
            return
        }

        val v11n = book.versification
        val verseRange = VerseRange(v11n, Verse(v11n, selection.startOrdinal), Verse(v11n, selection.endOrdinal))
        val textRange =
            if(selection.startOffset != null && selection.endOffset != null)
                BookmarkEntities.TextRange(selection.startOffset, selection.endOffset)
            else null
        val bookmark = BookmarkEntities.Bookmark(verseRange, textRange, wholeVerse, book)
        val initialLabels = workspaceSettings.autoAssignLabels

        val primaryLabelId = workspaceSettings.autoAssignPrimaryLabel
        if(primaryLabelId != null) {
            val label = bookmarkControl.labelById(primaryLabelId)
            if(label != null) {
                bookmark.primaryLabelId = primaryLabelId
            }
        }

        bookmarkControl.addOrUpdateBookmark(bookmark, initialLabels)
        if(initialLabels.isEmpty() || openNotes) {
            executeJavascriptOnUiThread(
                "bibleView.emit('bookmark_clicked', ${bookmark.id}, {openLabels: true, openNotes: $openNotes});"
            )
        }
    }

    internal fun compareSelection(selection: Selection? = currentSelection) {
        selection?: return
        Log.d(TAG, "compareSelection")
        val book = Books.installed().getBook(selection.bookInitials)
        if(book !is SwordBook) {
            return
        }

        val v11n = book.versification
        val verseRange = VerseRange(v11n, Verse(v11n, selection.startOrdinal), Verse(v11n, selection.endOrdinal))
        linkControl.openCompare(verseRange)
    }

    internal fun assignLabels(bookmarkId: Long) = GlobalScope.launch(Dispatchers.IO) {
        val bookmark = bookmarkControl.bookmarksByIds(listOf(bookmarkId)).first()
        val labels = bookmarkControl.labelsForBookmark(bookmark).map { it.id }
        val intent = Intent(mainBibleActivity, ManageLabels::class.java)
        intent.putExtra("data", ManageLabels.ManageLabelsData(
            mode = ManageLabels.Mode.ASSIGN,
            selectedLabels = labels.toMutableSet(),
            bookmarkPrimaryLabel = bookmark.primaryLabelId
        ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON())
        val result = mainBibleActivity.awaitIntent(intent)

        if(result?.resultCode == Activity.RESULT_OK) {
            val resultData = ManageLabels.ManageLabelsData.fromJSON(result.resultData.getStringExtra("data")!!)
            bookmark.primaryLabelId = resultData.bookmarkPrimaryLabel
            bookmarkControl.addOrUpdateBookmark(bookmark, resultData.selectedLabels)
            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
        }
    }

    var menuPrepared = false
    var currentSelection: Selection? = null

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent() = Intent()
        .setAction(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(Intent.EXTRA_PROCESS_TEXT, currentSelection!!.text)

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        val packageManager: PackageManager = context.packageManager
        return packageManager.queryIntentActivities(createProcessTextIntent(),
            0)
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
        Log.d(TAG, "onPrepareActionMode $menuPrepared ${currentSelection?.verseRange}")
        if(menuPrepared) {
            mode.menu.clear()
            mode.menuInflater.inflate(R.menu.bibleview_selection, menu)
            // For some reason, these do not seem to be correct from XML, even though specified there
            if(CommonUtils.settings.getBoolean("disable_two_step_bookmarking", false)) {
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
            if(currentSelection == null) {
                menu.findItem(R.id.add_bookmark).isVisible = false
                menu.findItem(R.id.add_bookmark_selection).isVisible = false
                menu.findItem(R.id.add_bookmark_whole_verse).isVisible = false
                menu.findItem(R.id.compare).isVisible = false
                menu.findItem(R.id.share_verses).isVisible = false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentSelection != null) {
                var menuItemOrder = 100
                for (resolveInfo in getSupportedActivities()) {
                    menu.add(Menu.NONE, Menu.NONE,
                        menuItemOrder++,
                        getLabel(resolveInfo))
                        .setIntent(createProcessTextIntentForResolveInfo(resolveInfo))
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                menu.findItem(R.id.system_items).isVisible = false
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
            if (showSystem || firstDocument !is BibleDocument || editingTextInJs) {
                showSystem = false
                return true
            } else {
                menu.clear()
                GlobalScope.launch {
                    val result = evaluateJavascriptAsync("bibleView.querySelection()")
                    val sel = json.decodeFromString(serializer<Selection?>(), result)
                    if (sel !== null) {
                        currentSelection = sel
                        menuPrepared = true
                    } else {
                        showSystem = true
                    }
                    withContext(Dispatchers.Main) {
                        mode.invalidate()
                    }
                }
                return false
            }
        }
    }

    var editingTextInJs: Boolean = false

    fun stopSelection(removeRanges: Boolean = false) {
        currentSelection = null
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
            val handled1 = onActionMenuItemClicked(mode, item)
            val handled2 = callback.onActionItemClicked(mode, item)
            if(handled1) stopSelection(true)
            return handled1 || handled2
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
        Log.d(TAG, "initialise")
        webViewClient = BibleViewClient()

        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                Log.d(TAG, message)
                result.confirm()
                return true
            }
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                // If errorBox is enabled, console logging is handled in js interface so we don't want anything
                // from here anymore.
                if (!showErrorBox) {
                    Log.d(TAG, "bibleview-js: ${consoleMessage.messageLevel()} ${consoleMessage.message()}")
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
        pageTiltScroller.destroy()
        removeJavascriptInterface("android")
    }

    var listenEvents: Boolean = false
        set(value) {
            if(value == field) return
            if(value) {
                ABEventBus.getDefault().register(this)
            } else {
                ABEventBus.getDefault().unregister(this)
            }
            field = value
        }

    fun doDestroy() {
        if(!toBeDestroyed) {
            destroy()
        }
        listenEvents = false
        Log.d(TAG, "Destroying Bibleview")
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
        const val SCHEME_JOURNAL = "journal"
        const val SCHEME_FIND_ALL_OCCURRENCES = "ab-find-all"
    }

    class ModuleAssetHandler: WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return null;
            val (bookName, resourcePath) = parts
            val location = File(Books.installed().getBook(bookName).bookMetaData.location)
            val f = File(location, resourcePath)
            return if(f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else null
        }
    }

    class ModuleStylesAssetHandler: WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return null;
            val (bookName, resourcePath) = parts
            val book = Books.installed().getBook(bookName) ?: return null
            val styleFile = book.bookMetaData.getProperty("AndBibleCSS") ?: return null

            val location = File(book.bookMetaData.location)
            var f = File(location, styleFile)
            if(resourcePath != "style.css") {
                f = File(f.parent, resourcePath)
            }

            return if (f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else null
        }
    }

    class FontsAssetHandler: WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return null;
            val (moduleName, resourcePath) = parts
            val book = Books.installed().getBook(moduleName) ?: return null
            if(resourcePath == "fonts.css") {
                val fontCss = StringBuilder()
                val fonts = fontsByModule[book.initials] ?: return null
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
                } else null
            }
        }
    }

    class FeatureAssetHandler: WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val parts = path.split("/", limit = 2);
            if(parts.size != 2) return null;
            val (moduleName, resourcePath) = parts
            val book = Books.installed().getBook(moduleName) ?: return null
            val location = File(book.bookMetaData.location)
            val f = File(location, resourcePath)
            return if (f.isFile && f.exists() && checkSignature(f)) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else null
        }

        private fun checkSignature(file: File): Boolean {
            val signatureFile = File(file.path + ".sign")
            return CommonUtils.verifySignature(file, signatureFile)
        }
    }

    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/module/", ModuleAssetHandler())
        .addPathHandler("/fonts/", FontsAssetHandler())
        .addPathHandler("/features/", FeatureAssetHandler())
        .addPathHandler("/module-style/", ModuleStylesAssetHandler())
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
                linkControl.loadApplicationUrl(links.first())
            }
            true
        }
        UriConstants.SCHEME_MYNOTES -> {
            val ordinal = uri.getQueryParameter("ordinal")?.toInt()!!
            val v11n = uri.getQueryParameter("v11n")
            linkControl.openMyNotes(v11n!!, ordinal)
        }
        UriConstants.MULTI_REFERENCE -> {
            val osisRefs = uri.getQueryParameters("osis")
            val v11n = uri.getQueryParameter("v11n")
            if (osisRefs != null) {
                linkControl.openMulti(osisRefs.map { BibleLink("osis", it, v11n) })
            } else false
        }
        UriConstants.SCHEME_JOURNAL -> {
            val id = uri.getQueryParameter("id")?.toLongOrNull()
            val bookmarkId = uri.getQueryParameter("bookmarkId")?.toLongOrNull()
            if (id != null) {
                linkControl.openJournal(id, bookmarkId)
            } else false
        }
        UriConstants.SCHEME_REFERENCE -> {
            val osisRef = uri.getQueryParameter("osis")
            val v11n = uri.getQueryParameter("v11n")
            val forceDoc = uri.getBooleanQueryParameter("force-doc", false)
            if (osisRef != null) {
                linkControl.loadApplicationUrl(BibleLink("osis", osisRef, v11n, forceDoc = forceDoc))
            } else {
                val contentRef = uri.getQueryParameter("content")!!
                linkControl.loadApplicationUrl(BibleLink("content", contentRef, v11n, forceDoc = forceDoc))
            }
            true
        }
        UriConstants.SCHEME_FIND_ALL_OCCURRENCES -> {
            val type = uri.getQueryParameter("type")
            val name = uri.getQueryParameter("name")
            linkControl.showAllOccurrences(name!!, SearchControl.SearchBibleSection.ALL, type!![0].toString())
            true
        }
        UriConstants.SCHEME_ERROR -> {
            linkControl.errorLink()
            true
        }
        UriConstants.SCHEME_DOWNLOAD -> {
            val initials = uri.getQueryParameter("initials")

            val intent = Intent(MainBibleActivity.mainBibleActivity, DownloadActivity::class.java)
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
            Log.d(TAG, "onLoadResource:$url")
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
            Log.d(TAG, "onLongClickListener")
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
            when (item.itemId) {
                R.id.open_link_in_special_window -> linkControl.setWindowMode(LinkControl.WINDOW_MODE_SPECIAL)
                R.id.open_link_in_new_window -> linkControl.setWindowMode(LinkControl.WINDOW_MODE_NEW)
                R.id.open_link_in_this_window -> linkControl.setWindowMode(LinkControl.WINDOW_MODE_THIS)
            }
            openLink(Uri.parse(targetLink))
            linkControl.setWindowMode(LinkControl.WINDOW_MODE_UNDEFINED)
            contextMenuInfo = null
            return true
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.link_context_menu, menu)
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
        return (if(ScreenSettings.nightMode) colors?.nightBackground else colors?.dayBackground) ?: UiUtils.bibleViewDefaultBackgroundColor
    }

    var lastUpdated = 0L
    var bookmarkLabels: List<BookmarkEntities.Label> = emptyList()

    var firstDocument: Document? = null

    suspend fun loadDocument(document: Document,
                             updateLocation: Boolean = false,
                             verse: Verse? = null,
                             anchorOrdinal: Int? = null)
    {
        val currentPage = window.pageManager.currentPage

        // make sure this has been created
        bookmarkControl.labelUnlabelled
        bookmarkControl.speakLabel

        bookmarkLabels = bookmarkControl.assignableLabels.toMutableList()
        initialVerse = verse

        initialAnchorOrdinal = anchorOrdinal

        if (lastUpdated == 0L || updateLocation) {
            if (listOf(DocumentCategory.BIBLE, DocumentCategory.MYNOTE).contains(currentPage.documentCategory)) {
                initialVerse = KeyUtil.getVerse(window.pageManager.currentBibleVerse.verse)
            } else {
                initialAnchorOrdinal = currentPage.anchorOrdinal
            }
        }

        contentVisible = false

        val chapter = initialVerse?.chapter
        if (chapter != null) {
            addChapter(chapter)
        }

        Log.d(TAG, "Show $initialVerse, $initialAnchorOrdinal Window:$window, settings: topOffset:${topOffset}, \n actualSettings: ${displaySettings.toJson()}")
        this.firstDocument = document
        synchronized(this) {
            var docStr = document.asJson
            // Ps 119 in KJV is only 70k. Let's give gracefully max 500k until we give "page too large" error.
            // Our BibleView.js will freeze and eventually OOM-crash with ridiculously large documents.
            if(docStr.length > 500000) {
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

    private var initialAnchorOrdinal: Int? = null
    internal var initialVerse: Verse? = null
    private val displaySettings get() = window.pageManager.actualTextDisplaySettings
    internal val workspaceSettings get() = windowControl.windowRepository.workspaceSettings

    fun updateTextDisplaySettings(onAttach: Boolean = false) {
        Log.d(TAG, "updateTextDisplaySettings")
        updateBackgroundColor()
        updateConfig(onAttach)
    }

    private val hasActiveIndicator get() =
        CommonUtils.settings.getBoolean("show_active_window_indicator", true)
            && windowControl.activeWindow.id == window.id && windowControl.windowRepository.visibleWindows.size > 1

    private val isActive get() = windowControl.activeWindow.id == window.id

    private fun updateActive() =
        executeJavascriptOnUiThread("""bibleView.emit('set_active', {isActive: $isActive, hasActiveIndicator: $hasActiveIndicator})""")

    private val showErrorBox get() = if(CommonUtils.isBeta) CommonUtils.settings.getBoolean("show_errorbox", false) else false

    private fun getUpdateConfigCommand(initial: Boolean): String {
        val favouriteLabels = json.encodeToString(serializer(), workspaceSettings.favouriteLabels)
        val recentLabels = json.encodeToString(serializer(), workspaceSettings.recentLabels.map { it.labelId })
        val hideCompareDocuments = json.encodeToString(serializer(), workspaceSettings.hideCompareDocuments)
        val limitAmbiguousModalSize = json.encodeToString(serializer(), workspaceSettings.limitAmbiguousModalSize)
        return """
                bibleView.emit('set_config', {
                    config: ${displaySettings.toJson()}, 
                    appSettings: {
                        activeWindow: $isActive, 
                        nightMode: $nightMode, 
                        errorBox: $showErrorBox, 
                        favouriteLabels: $favouriteLabels, 
                        recentLabels: $recentLabels, 
                        hideCompareDocuments: $hideCompareDocuments,
                        limitAmbiguousModalSize: $limitAmbiguousModalSize,
                    }, 
                    initial: $initial
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
        Log.d(TAG, "updateBackgroundColor")
        setBackgroundColor(backgroundColor)
    }

    private val nightMode get() = mainBibleActivity.currentNightMode

    var labelsUploaded = false

    fun adjustLoadingCount(adj: Int) {
        executeJavascriptOnUiThread("""bibleView.emit("adjust_loading_count", ${adj})""")
    }

    private fun replaceDocument() {
        Log.d(TAG, "replaceDocument")
        val documentStr = latestDocumentStr
        synchronized(this) {
            needsDocument = false
            contentVisible = true
            minChapter = initialVerse?.chapter ?: -1
            maxChapter = initialVerse?.chapter ?: -1
        }

        if(!labelsUploaded) {
            val bookmarkLabels = json.encodeToString(serializer(), bookmarkLabels.map { ClientBookmarkLabel(it) })
            executeJavascriptOnUiThread("""bibleView.emit("update_labels", ${bookmarkLabels})""")
            labelsUploaded = true
       }

        val doc = firstDocument
        val jumpToId =
            if(doc is StudyPadDocument && doc.bookmarkId != null)
                "studypad-bookmark-${doc.bookmarkId}"
            else null

        executeJavascriptOnUiThread("""
            bibleView.emit("clear_document");
            ${getUpdateConfigCommand(true)}
            bibleView.emit("add_documents", $documentStr);
            bibleView.emit("setup_content", {
                jumpToOrdinal: ${initialVerse?.ordinal}, 
                jumpToAnchor: $initialAnchorOrdinal,
                jumpToId: "$jumpToId",
                topOffset: $topOffset,
                bottomOffset: $bottomOffset,
            });            
            bibleView.emit("set_title", "BibleView-${window.id}");
            """
        )
    }

    /**
     * Enable or disable zoom controls depending on whether map is currently shown
     */
    private fun enableZoomForMap(isMap: Boolean) {
        Log.d(TAG, "enableZoomForMap $isMap")
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
        Log.d(TAG, "Focus changed so start/stop scroll $hasWindowFocus")
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        //TODO allow DPAD_LEFT to always change page and navigation between links using dpad
        // placing BibleKeyHandler second means that DPAD left is unable to move to prev page if strongs refs are shown
        // vice-versa (webview second) means right & left can not be used to navigate between Strongs links

        // common key handling i.e. KEYCODE_DPAD_RIGHT & KEYCODE_DPAD_LEFT to change chapter
        return if (bibleKeyHandler.onKeyUp(keyCode, event)) {
            true
        } else super.onKeyUp(keyCode, event)

        // allow movement from link to link in current page
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
        if(document !is DocumentWithBookmarks) return
        if(document is MyNotesDocument && !document.verseRange.overlaps(event.bookmark.kjvVerseRange)) return

        val clientBookmark = ClientBookmark(event.bookmark,
            when (document) {
                is BibleDocument -> document.swordBook.versification
                is MyNotesDocument -> KJVA
                else -> null
            }
        )
        val bookmarkStr = clientBookmark.asJson
        executeJavascriptOnUiThread("""
            bibleView.emit("add_or_update_bookmarks",  [$bookmarkStr]);
        """)
    }

    fun onEvent(event: BookmarkNoteModifiedEvent) {
        executeJavascriptOnUiThread("""
            bibleView.emit("bookmark_note_modified", {id: ${event.bookmarkId}, lastUpdatedOn: ${event.lastUpdatedOn}, notes: ${json.encodeToString(serializer(), event.notes)}});
        """)
    }

    fun onEvent(event: StudyPadOrderEvent) {
        val doc = firstDocument
        if(doc !is StudyPadDocument || doc.label.id != event.labelId) return
        val journalJson = json.encodeToString(serializer(), event.newStudyPadTextEntry)
        val bookmarkToLabels = json.encodeToString(serializer(), event.bookmarkToLabelsOrderChanged)
        val journals = json.encodeToString(serializer(), event.studyPadOrderChanged)
        executeJavascriptOnUiThread("""
            bibleView.emit("add_or_update_journal",  {journal: $journalJson, bookmarkToLabelsOrdered: $bookmarkToLabels, journalsOrdered: $journals});
        """)
    }

    fun onEvent(event: BookmarkToLabelAddedOrUpdatedEvent) {
        val doc = firstDocument
        if(doc !is StudyPadDocument || doc.label.id != event.bookmarkToLabel.labelId) return
        val bookmarkToLabel = json.encodeToString(serializer(), event.bookmarkToLabel)
        executeJavascriptOnUiThread("""
            bibleView.emit("add_or_update_bookmark_to_label", $bookmarkToLabel);
        """)
    }

    fun onEvent(event: StudyPadTextEntryDeleted) {
        if(firstDocument !is StudyPadDocument) return
        executeJavascriptOnUiThread("""
            bibleView.emit("delete_journal", ${event.journalId});
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
            scrollOrJumpToVerse(event.verse, window.restoreOngoing)
        }
    }

    private var checkWindows = false

    fun onEvent(event: MainBibleActivity.ConfigurationChanged) {
        checkWindows = true
    }

    fun onEvent(event: NumberOfWindowsChangedEvent) {
        if(window.isVisible) {
            executeJavascriptOnUiThread("bibleView.emit('set_offsets', $topOffset, $bottomOffset, {immediate: true});")
            updateActive()
        }
    }

    fun onEvent(event: MainBibleActivity.FullScreenEvent) {
        if((isTopWindow || isBottomWindow) && contentVisible && window.isVisible)
            executeJavascriptOnUiThread("bibleView.emit('set_offsets', $topOffset, $bottomOffset);")
    }

    fun onEvent(event: RestoreButtonsVisibilityChanged) {
        if(isBottomWindow && contentVisible && window.isVisible)
            executeJavascriptOnUiThread("bibleView.emit('set_offsets', $topOffset, $bottomOffset);")
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
        Log.d(TAG, "window size changed")
        separatorMoving = !event.isFinished
        if(!separatorMoving && !mainBibleActivity.isSplitVertically) {
            doCheckWindows(true)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if(lastUpdated != 0L && !separatorMoving && w != ow) {
            doCheckWindows()
        }
    }

    private fun doCheckWindows(force: Boolean = false) {
        if(checkWindows || force) {
            if(!htmlLoadingOngoing) executeJavascript("bibleView.emit('set_offsets', $topOffset, $bottomOffset, {doNotScroll: true});")
            if (window.pageManager.currentPage.documentCategory == DocumentCategory.BIBLE) {
                scrollOrJumpToVerse(window.pageManager.currentBible.currentBibleVerse.verse, true)
            }
            checkWindows = false
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "Detached from window")
        // prevent random verse changes while layout is being rebuild because of window changes
        bibleJavascriptInterface.notificationsEnabled = false
        pauseTiltScroll()
    }

    fun onEventMainThread(event: AfterRemoveWebViewEvent) {
        if(toBeDestroyed)
            doDestroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "Attached to window")
        if (windowControl.isActiveWindow(window)) {
            bibleJavascriptInterface.notificationsEnabled = true

            resumeTiltScroll()
        }
        if(contentVisible) {
            updateTextDisplaySettings(true)
        }
    }

    fun scrollOrJumpToVerse(key: Key, restoreOngoing: Boolean = false) {
        Log.d(TAG, "Scroll or jump to:$key")
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
        val v = initialVerse
        if(firstDocument is MyNotesDocument) {
            toVerse = toVerse.toV11n(KJVA)
            endVerse = endVerse?.toV11n(KJVA)
        } else if(v != null) {
            toVerse = toVerse.toV11n(v.versification)
            endVerse = endVerse?.toV11n(v.versification)
        }
        val jumpToId = "o-${toVerse.ordinal}"
        val now = !contentVisible || restoreOngoing
        val highlight = !contentVisible || endVerse != null
        fun boolString(value: Boolean?): String {
            if(value == null) return "null"
            return if(value) "true" else "false"
        }
        executeJavascriptOnUiThread("bibleView.emit('scroll_to_verse', '$jumpToId', {now: ${boolString(now)}, highlight: ${boolString(highlight)}, ordinalStart: ${toVerse.ordinal}, ordinalEnd: ${endVerse?.ordinal}});")
    }

    fun executeJavascriptOnUiThread(javascript: String) {
        runOnUiThread { executeJavascript(javascript) }
    }

    private fun runOnUiThread(runnable: () -> Unit) {
        if(Looper.myLooper() == Looper.getMainLooper()) {
            runnable()
        } else {
            post(runnable)
        }
    }

    private fun executeJavascript(javascript: String, callBack: ((rv: String) -> Unit)? = null) {
        val end = min(javascript.length, 500)
        val subStr = javascript.slice(0 until end)

        Log.d(TAG, "Executing JS: $subStr")
        evaluateJavascript("$javascript;", callBack)
    }

    private suspend fun evaluateJavascriptAsync(javascript: String): String {
        val result = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            evaluateJavascript(javascript) { result.complete(it) }
        }
        return result.await()
    }

    fun requestPreviousChapter(callId: Long) = GlobalScope.launch(Dispatchers.IO) {
        Log.d(TAG, "requestMoreTextAtTop")
        if (firstDocument is BibleDocument) {
            val newChap = minChapter - 1

            if(newChap < 1) return@launch

            val currentPage = window.pageManager.currentBible
            val doc = currentPage.getDocumentForChapter(newChap)
            addChapter(newChap)
            executeJavascriptOnUiThread("bibleView.response($callId, ${doc.asJson});")
        }
    }

    fun requestNextChapter(callId: Long) = GlobalScope.launch(Dispatchers.IO) {
        Log.d(TAG, "requestMoreTextAtEnd")
        if (firstDocument is BibleDocument) {
            val newChap = maxChapter + 1
            val currentPage = window.pageManager.currentBible
            val verse = currentPage.currentBibleVerse.verse
            val lastChap = verse.versification.getLastChapter(verse.book)

            if(newChap > lastChap) return@launch
            val doc = currentPage.getDocumentForChapter(newChap)
            addChapter(newChap)
            executeJavascriptOnUiThread("bibleView.response($callId, ${doc.asJson});")
        }
    }

    fun hasChapterLoaded(chapter: Int) = chapter in minChapter..maxChapter

    fun setClientReady() {
        htmlLoadingOngoing = false
        if(latestDocumentStr != null && needsDocument) {
            replaceDocument()
        }
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

    var onDestroy: (() -> Unit)? = null

    private val TAG get() = "BibleView[${windowRef.get()?.id}]"

    companion object {
        // never go to 0 because a bug in Android prevents invalidate after loadDataWithBaseURL so
        // no scrollOrJumpToVerse will occur
        private const val TOP_OF_SCREEN = 1
    }
}
