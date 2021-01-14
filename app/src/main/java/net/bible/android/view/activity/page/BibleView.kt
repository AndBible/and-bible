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
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Build
import android.os.Looper
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkAddedOrUpdatedEvent
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarksDeletedEvent
import net.bible.android.control.bookmark.LABEL_UNLABELED_ID
import net.bible.android.control.bookmark.LabelAddedOrUpdatedEvent
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.event.window.ScrollSecondaryWindowEvent
import net.bible.android.control.event.window.WindowSizeChangedEvent
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.DecrementBusyCount
import net.bible.android.control.page.window.IncrementBusyCount
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.versification.toV11n
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.android.database.bookmarks.intToColorArray
import net.bible.android.database.json
import net.bible.android.view.activity.base.DocumentView
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.page.screen.AfterRemoveWebViewEvent
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.android.view.activity.page.screen.WebViewsBuiltEvent
import net.bible.android.view.util.UiUtils
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.sword.BookAndKey
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLConnection
import kotlin.math.min

class BibleViewInputFocusChanged(val view: BibleView, val newFocus: Boolean)

class OsisFragment(val xml: String, val key: Key?, val bookId: String) {
    val ordinalRangeJson: String get () {
        val key = key;
        return if(key is VerseRange) {
            "[${key.start.ordinal}, ${key.end.ordinal}]"
        } else "null"
    }
    val keyStr: String get () {
        val osisId = if (key is VerseRange) {
            "${key.start.ordinal}_${key.end.ordinal}"
        } else {
            key?.osisID?.replace(".", "-")
        }
        return "$bookId--${osisId ?: "error"}"
    }
    val features: String get () {
        return if(key is BookAndKey) {
            val type = when {
                key.document.hasFeature(FeatureType.HEBREW_DEFINITIONS) -> "hebrew"
                key.document.hasFeature(FeatureType.GREEK_DEFINITIONS) -> "greek"
                else -> null
            }
            if (type != null) {
                "{type: '${type}', keyName: '${key.key.name}'}"
            } else "undefined"
        } else "undefined"
    }
}

@Serializable
data class ClientBookmark(val id: Long, val ordinalRange: List<Int>, val offsetRange: List<Int>?,
                          val labels: List<Long>, val book: String?, val createdAt: Long, val lastUpdatedOn: Long,
                          val notes: String?) {
    constructor(bookmark: BookmarkEntities.Bookmark, labels: List<Long>) :
        this(bookmark.id,
            listOf(bookmark.ordinalStart, bookmark.ordinalEnd),
            bookmark.textRange?.clientList,
            labels.toMutableList().also { if(it.isEmpty()) it.add(LABEL_UNLABELED_ID) }, bookmark.book?.initials,
            bookmark.createdAt.time, bookmark.lastUpdatedOn.time, bookmark.notes
        )
}

@Serializable
data class ClientBookmarkStyle(val color: List<Int>)

@Serializable
data class ClientBookmarkLabel(val id: Long, val style: ClientBookmarkStyle)


/** The WebView component that shows the bible and other documents */
@SuppressLint("ViewConstructor")
class BibleView(val mainBibleActivity: MainBibleActivity,
                internal var windowRef: WeakReference<Window>,
                private val windowControl: WindowControl,
                private val bibleKeyHandler: BibleKeyHandler,
                private val pageControl: PageControl,
                private val pageTiltScrollControl: PageTiltScrollControl,
                private val linkControl: LinkControl,
                internal val bookmarkControl: BookmarkControl
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
    private var latestOsisObjStr: String = ""
    private var needsOsisContent: Boolean = false
    private var htmlLoadingOngoing: Boolean = false
        set(value) {
            if(value != field) {
                ABEventBus.getDefault().post(if(value) IncrementBusyCount() else DecrementBusyCount())
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
        if (0 != BibleApplication.application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            setWebContentsDebuggingEnabled(true)
        }
        gestureDetector = GestureDetectorCompat(context, gestureListener)
        setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                true
            } else v.performClick()
        }
        settings.allowFileAccess = false
        settings.allowContentAccess = false
    }

    private fun onActionMenuItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.add_bookmark -> {
                makeBookmark()
                mode.finish()
                return true
            }
            R.id.remove_bookmark -> {
                val sel = currentSelection
                if(sel?.bookmarks?.isNotEmpty() == true) {
                    bookmarkControl.deleteBookmarksById(sel.bookmarks)
                }
                return true;
            }
            else -> false
        }
    }

    private fun makeBookmark() {
        val selection = currentSelection?: return
        Log.d(TAG, "makeBookmark")
        val book = Books.installed().getBook(selection.bookInitials)
        if(book !is SwordBook) {
            // TODO: error response to JS
            return
        }

        val v11n = book.versification
        val verseRange = VerseRange(v11n, Verse(v11n, selection.startOrdinal), Verse(v11n, selection.endOrdinal))
        val textRange = BookmarkEntities.TextRange(selection.startOffset, selection.endOffset)
        val bookmark = BookmarkEntities.Bookmark(verseRange, textRange, book)
        val initialLabels = displaySettings.bookmarks!!.assignLabels!!.toList()
        bookmarkControl.addOrUpdateBookmark(bookmark, initialLabels)

        val actionTextColor = CommonUtils.getResourceColor(R.color.snackbar_action_text)
        runOnUiThread {
            val currentView = mainBibleActivity.findViewById<View>(R.id.coordinatorLayout)
            Snackbar.make(currentView, R.string.bookmark_added, Snackbar.LENGTH_LONG)
                .setActionTextColor(actionTextColor)
                .setAction(R.string.assign_labels) { assignLabels(bookmark.id)
                }.show()
        }
    }

    internal fun assignLabels(bookmarkId: Long) = GlobalScope.launch(Dispatchers.IO) {
        val bookmark = bookmarkControl.bookmarksByIds(listOf(bookmarkId)).first()
        val labels = bookmarkControl.labelsForBookmark(bookmark).map { it.id }.toLongArray()
        val intent = Intent(mainBibleActivity, ManageLabels::class.java)
        intent.putExtra(BookmarkControl.LABEL_IDS_EXTRA, labels)
        intent.putExtra("title", mainBibleActivity.getString(R.string.assign_labels_new_bookmark))
        val result = mainBibleActivity.awaitIntent(intent)
        val resultLabels = result?.resultData?.extras?.getLongArray(BookmarkControl.LABEL_IDS_EXTRA)?.toList()
        if(resultLabels != null) {
            bookmarkControl.setLabelsByIdForBookmark(bookmark, resultLabels.toList())
        }
    }

    @Serializable
    class Selection(val bookInitials: String, val startOrdinal: Int,
                    val startOffset: Int, val endOrdinal: Int, val endOffset: Int, val bookmarks: List<Long>)
    {
        val verseRange: VerseRange get() {
            val v11n = (Books.installed().getBook(bookInitials) as SwordBook).versification
            return VerseRange(v11n, Verse(v11n, startOrdinal), Verse(v11n, endOrdinal))
        }

    }

    var menuPrepared = false
    var currentSelection: Selection? = null

    private fun onPrepareActionMenu(mode: ActionMode, menu: Menu): Boolean {
        if(bookCategory != BookCategory.BIBLE) return false
        mode.menuInflater.inflate(R.menu.bibleview_selection, menu)

        if(menuPrepared) {
            // For some reason, these do not seem to be correct from XML, even though specified there
            menu.findItem(R.id.add_bookmark).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.findItem(R.id.remove_bookmark).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            if(currentSelection == null) {
                val item = menu.findItem(R.id.add_bookmark)
                item.isVisible = false
            }
            if ((currentSelection?.bookmarks ?: emptyList()).isEmpty()) {
                val item = menu.findItem(R.id.remove_bookmark)
                item.isVisible = false
            }
            menuPrepared = false
            return true
        }
        else {
            GlobalScope.launch {
                val result = evaluateJavascriptAsync("bibleView.querySelection()")
                val sel = json.decodeFromString(serializer<Selection?>(), result)
                if(sel !== null) {
                    currentSelection = sel
                }

                menuPrepared = true
                withContext(Dispatchers.Main) {
                    menu.clear()
                    mode.invalidate()
                }
            }
            return false
        }
    }

    fun stopSelection(removeRanges: Boolean = false) {
        currentSelection = null
        menuPrepared = false
        if(removeRanges) executeJavascriptOnUiThread("bibleView.emit('remove_ranges')")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private inner class ActionModeCallback2(val callback: ActionMode.Callback): ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
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
            return callback.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if(callback is ActionMode.Callback2) {
                return callback.onGetContentRect(mode, view, outRect)
            } else {
                return super.onGetContentRect(mode, view, outRect)
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
            return callback.onDestroyActionMode(mode)
        }
    }

    fun setBibleJavascriptInterface(bibleJavascriptInterface: BibleJavascriptInterface) {
        this.bibleJavascriptInterface = bibleJavascriptInterface
        addJavascriptInterface(bibleJavascriptInterface, "android")
    }

    class BibleLink(val type: String, val target: String) {
        val url: String get() {
            return when(type) {
                "content" -> "$type:$target"
                "strong" -> "$type:$target"
                "robinson" -> "$type:$target"
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
                // Console logging is handled in js interface so we don't want anything from here anymore.
                return true
            }
        }

        settings.javaScriptEnabled = true

        applyPreferenceSettings()

        pageTiltScroller = PageTiltScroller(this, pageTiltScrollControl)
        pageTiltScroller.enableTiltScroll(true)

        // if this webview becomes (in)active then must start/stop auto-scroll
        listenEvents = true

        htmlLoadingOngoing = true;
        loadUrl("https://appassets.androidplatform.net/assets/bibleview-js/index.html")
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
        const val SCHEME_ERROR = "ab-error"
        const val SCHEME_W = "ab-w"
        const val SCHEME_REFERENCE = "ab-reference"
        const val SCHEME_FIND_ALL_OCCURRENCES = "ab-find-all"
    }

    class ModuleAssetHandler: WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val (bookName, resourcePath) = path.split("/", limit=2)
            val location = File(Books.installed().getBook(bookName).bookMetaData.location)
            val f = File(location, resourcePath)
            return if(f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(resourcePath), null, f.inputStream())
            } else null
        }

    }
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/module/", ModuleAssetHandler())
        .build()

    private inner class BibleViewClient: WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            val uri = req.url
            val loaded = when(uri.scheme) {
                UriConstants.SCHEME_W -> {
                    val links = mutableListOf<BibleLink>()
                    for(paramName in uri.queryParameterNames) {
                        links.addAll(uri.getQueryParameters(paramName).map { BibleLink(paramName, it) })
                    }
                    if(links.size > 1) {
                        linkControl.loadApplicationUrl(links)
                    } else {
                        linkControl.loadApplicationUrl(links.first())
                    }
                    true
                }
                UriConstants.SCHEME_REFERENCE -> {
                    val osisRef = uri.getQueryParameter("osis")
                    if(osisRef != null) {
                        linkControl.loadApplicationUrl(BibleLink("osis", osisRef))
                    } else {
                        val contentRef = uri.getQueryParameter("content")!!
                        linkControl.loadApplicationUrl(BibleLink("content", contentRef))
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
                else -> {
                    Log.e(TAG, "Unsupported scheme ${uri.scheme}")
                    true
                }
            }

            return if(loaded) {
                gestureListener.setDisableSingleTapOnce(true)
                super.shouldOverrideUrlLoading(view, req)
                true
            } else {
                super.shouldOverrideUrlLoading(view, req)
            }
        }

        override fun onLoadResource(view: WebView, url: String) {
            Log.d(TAG, "onLoadResource:$url")
            super.onLoadResource(view, url)
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            Log.e(TAG, description)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return assetLoader.shouldInterceptRequest(request.getUrl())
        }
    }

    private var contextMenuInfo: BibleViewContextMenuInfo? = null
    override fun getContextMenuInfo(): ContextMenuInfo? {
        return contextMenuInfo
    }

    private inner class BibleViewLongClickListener(private var defaultValue: Boolean) : OnLongClickListener {
        override fun onLongClick(v: View): Boolean {
            Log.d(TAG, "onLongClickListener")
            val result = hitTestResult
            return if (result.type == HitTestResult.SRC_ANCHOR_TYPE) {
                contextMenuInfo = LinkLongPressContextMenuInfo(result.extra!!)
                v.showContextMenu()
            } else {
                contextMenuInfo = null
                defaultValue
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
            linkControl.loadApplicationUrl(targetLink)
            linkControl.setWindowMode(LinkControl.WINDOW_MODE_UNDEFINED)
            contextMenuInfo = null
            return true
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.link_context_menu, menu)
            val openLinksInSpecialWindowByDefault = CommonUtils.sharedPreferences.getBoolean("open_links_in_special_window_pref", true)
            val item =
                if(openLinksInSpecialWindowByDefault)
                    menu.findItem(R.id.open_link_in_special_window)
                else
                    menu.findItem(R.id.open_link_in_this_window)
            item.isVisible = false
        }
    }

    /** apply settings set by the user using Preferences
     */
    override fun applyPreferenceSettings() {
        Log.d(TAG, "applyPreferenceSettings")
        applyFontSize()
    }

    private fun applyFontSize() {
        Log.d(TAG, "applyFontSize")
        val fontSize = pageControl.getDocumentFontSize(window)
        val oldFontSize = settings.defaultFontSize
        val fontFamily = window.pageManager.actualTextDisplaySettings.font!!.fontFamily!!
        settings.defaultFontSize = fontSize
        settings.standardFontFamily = fontFamily
        if(oldFontSize != fontSize) {
            doCheckWindows()
        }
    }

    /** may need updating depending on environmental brightness
     */
    override fun updateBackgroundColor() {
        Log.d(TAG, "updateBackgroundColor")
        setBackgroundColor(backgroundColor)
    }

    val backgroundColor: Int get() {
        val colors = window.pageManager.actualTextDisplaySettings.colors
        return (if(ScreenSettings.nightMode) colors?.nightBackground else colors?.dayBackground) ?: UiUtils.bibleViewDefaultBackgroundColor
    }

    var lastUpdated = 0L
    var latestBookmarks: List<BookmarkEntities.Bookmark> = emptyList()
    var bookmarkLabels: List<BookmarkEntities.Label> = emptyList()
    var bookCategory: BookCategory? = null

    suspend fun show(osisFrags: List<OsisFragment>,
                     bookmarks: List<BookmarkEntities.Bookmark>,
                     updateLocation: Boolean = false,
                     verse: Verse? = null,
                     yOffsetRatio: Float? = null)
    {
        val currentPage = window.pageManager.currentPage
        bookmarkLabels = bookmarkControl.allLabels
        initialVerse = verse

        var jumpToYOffsetRatio = yOffsetRatio

        if (lastUpdated == 0L || updateLocation) {
            if (currentPage is CurrentBiblePage) {
                initialVerse = KeyUtil.getVerse(window.pageManager.currentBible.currentBibleVerse.verse)
            } else {
                jumpToYOffsetRatio = currentPage.currentYOffsetRatio
            }
        }

        contentVisible = false

        val chapter = initialVerse?.chapter
        if (chapter != null) {
            addChapter(chapter)
        }

        Log.d(TAG, "Show $initialVerse, $jumpToYOffsetRatio Window:$window, settings: topOffset:${topOffset}, \n actualSettings: ${displaySettings.toJson()}")

        latestBookmarks = bookmarks
        latestOsisObjStr = getOsisObjStr(osisFrags)
        bookCategory = Books.installed().getBook(osisFrags.first().bookId).bookCategory

        withContext(Dispatchers.Main) {
            updateBackgroundColor()
            applyFontSize()
            enableZoomForMap(pageControl.currentPageManager.isMapShown)
        }

        if(!htmlLoadingOngoing) {
            withContext(Dispatchers.Main) {
                replaceOsis()
            }
        } else {
            synchronized(this) {
                needsOsisContent = true
            }
        }
    }

    private fun addChapter(chapter: Int) {
        if(chapter < minChapter) {
            minChapter = chapter
        } else if(chapter > maxChapter) {
            maxChapter = chapter
        } else {
            Log.e(TAG, "Chapter already included")
        }
    }

    private var initialVerse: Verse? = null
    private val displaySettings get() = window.pageManager.actualTextDisplaySettings

    fun updateTextDisplaySettings() {
        Log.d(TAG, "updateTextDisplaySettings")
        updateBackgroundColor()
        applyFontSize()
        executeJavascriptOnUiThread("bibleView.emit('set_config', {config: ${displaySettings.toJson()}});")
    }

    private fun replaceOsis() {
        var osisObjStr = ""
        synchronized(this) {
            osisObjStr = latestOsisObjStr
            needsOsisContent = false
            contentVisible = true
            minChapter = initialVerse?.chapter ?: -1
            maxChapter = initialVerse?.chapter ?: -1
        }

        executeJavascriptOnUiThread("""
            bibleView.emit("set_config", {config: ${displaySettings.toJson()}, initial:true});
            bibleView.emit("replace_osis", $osisObjStr);
            bibleView.emit("setup_content", {
                jumpToOrdinal: ${initialVerse?.ordinal}, 
                jumpToYOffsetRatio: null,
                topOffset: $topOffset,
                bottomOffset: $bottomOffset,
            });            
            bibleView.emit("set_title", "BibleView-${window.id}");
            """.trimIndent()
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
        if (window.pageManager.isMapShown) {
            // allow swipe right if at right side of map
            val isAtRightEdge = scrollX >= maxHorizontalScroll

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
        if (window.pageManager.isMapShown) {
            // allow swipe left if at left edge of map
            val isAtLeftEdge = scrollX == 0

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
        //Log.d(TAG, "BibleView onTouchEvent");
        windowControl.activeWindow = window

        val handled = super.onTouchEvent(event)

        // Allow user to redefine viewing angle by touching screen
        pageTiltScroller.recalculateViewingPosition()

        return handled
    }

    override val currentPosition: Float get () {
        // see http://stackoverflow.com/questions/1086283/getting-document-position-in-a-webview
        return scrollY.toFloat() / contentHeight.toFloat()
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
        val clientBookmark = ClientBookmark(event.bookmark, event.labels)
        val bookmarkStr = json.encodeToString(serializer(), clientBookmark)
        executeJavascriptOnUiThread("""bibleView.emit("add_or_update_bookmarks",  {bookmarks: [$bookmarkStr], labels: []});""")
    }

    fun onEvent(event: LabelAddedOrUpdatedEvent) {
        val defaultStyle = ClientBookmarkStyle(BookmarkStyle.YELLOW_STAR.colorArray)
        val labelStr = json.encodeToString(serializer(),
            ClientBookmarkLabel(event.label.id, event.label.color.let { v -> ClientBookmarkStyle(intToColorArray(v)) }?: defaultStyle))
        executeJavascriptOnUiThread("""
            bibleView.emit("add_or_update_bookmarks", 
            { bookmarks:[],
              labels: [$labelStr]
            })
            
        """.trimIndent())
    }

    fun onEvent(event: BookmarksDeletedEvent) {
        val bookmarkIds = json.encodeToString(serializer(), event.bookmarks)
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
    }

    fun onEvent(event: ScrollSecondaryWindowEvent) {
        if (window == event.window) {
            scrollOrJumpToVerseOnUIThread(event.verse)
        }
    }

    private var checkWindows = false

    fun onEvent(event: MainBibleActivity.ConfigurationChanged) {
        checkWindows = true
    }

    fun onEvent(event: NumberOfWindowsChangedEvent) {
        if(window.isVisible)
            executeJavascriptOnUiThread("bibleView.emit('set_offsets', $topOffset, $bottomOffset, {immediate: true});")
    }

    fun onEvent(event: MainBibleActivity.FullScreenEvent) {
        if(isTopWindow && contentVisible && window.isVisible)
            executeJavascriptOnUiThread("bibleView.emit('set_offsets', $topOffset, $bottomOffset);")
    }

    fun onEvent(event: WebViewsBuiltEvent) {
        checkWindows = true
    }

    private val isTopWindow
        get() = !CommonUtils.isSplitVertically || windowControl.windowRepository.firstVisibleWindow == window

    private val isBottomWindow
        get() = !CommonUtils.isSplitVertically || windowControl.windowRepository.lastVisibleWindow == window

    val topOffset
        get() =
            if(isTopWindow && !SharedActivityState.instance.isFullScreen)
                (mainBibleActivity.topOffset2
                    / mainBibleActivity.resources.displayMetrics.density)
            else 0F
    
    val bottomOffset
        get() =
            if(isBottomWindow && !SharedActivityState.instance.isFullScreen)
                (mainBibleActivity.bottomOffset2
                    / mainBibleActivity.resources.displayMetrics.density)
            else 0F

    private var separatorMoving = false

    fun onEvent(event: WindowSizeChangedEvent) {
        Log.d(TAG, "window size changed")
        separatorMoving = !event.isFinished
        if(!separatorMoving && !CommonUtils.isSplitVertically) {
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
            executeJavascript("bibleView.emit('set_offsets', $topOffset, $bottomOffset, {doNotScroll: true});")
            if (window.pageManager.currentPage.bookCategory == BookCategory.BIBLE) {
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

            // may have returned from MyNote view
            resumeTiltScroll()
        }
        if(contentVisible) {
            updateTextDisplaySettings()
        }
    }

    /** move the view so the selected verse is at the top or at least visible
     * @param verse
     */
    fun scrollOrJumpToVerseOnUIThread(verse: Verse) {
        val restoreOngoing = window.restoreOngoing
        runOnUiThread {
            scrollOrJumpToVerse(verse, restoreOngoing)
        }
    }

    /** move the view so the selected verse is at the top or at least visible
     */
    private fun scrollOrJumpToVerse(verse: Verse, restoreOngoing: Boolean = false) {
        Log.d(TAG, "Scroll or jump to:$verse")
        var toVerse = verse;
        val v = initialVerse
        if(v != null) {
            toVerse = verse.toV11n(v.versification)
        }
        val jumpToId = "v-${toVerse.ordinal}"
        val now = if(!contentVisible || restoreOngoing) "true" else "false"
        executeJavascript("bibleView.emit('scroll_to_verse', '$jumpToId', $now, $topOffset);")
    }

    internal fun executeJavascriptOnUiThread(javascript: String) {
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

    private fun getOsisObjStr(frags: List<OsisFragment>): String {
        val defaultStyle = ClientBookmarkStyle(BookmarkStyle.YELLOW_STAR.colorArray)
        val bookmarkLabels = json.encodeToString(serializer(), bookmarkLabels.map {
            ClientBookmarkLabel(it.id, it.color.let { v -> ClientBookmarkStyle(intToColorArray(v)) } ?: defaultStyle)
        })
        val bookmarks = json.encodeToString(serializer(), latestBookmarks.map { it ->
            val labels = bookmarkControl.labelsForBookmark(it).toMutableList()
            ClientBookmark(it, labels.map { lbl -> lbl.id })
        })
        val xmlList = frags.joinToString(",")  { """
            |{
            |   xml: `${it.xml.replace("`", "\\`")}`, 
            |   key:'${it.keyStr}', 
            |   features:${it.features}, 
            |   ordinalRange: ${it.ordinalRangeJson}
            |}""".trimMargin()
        }
        return """{
            contents: [$xmlList],
            bookmarks: $bookmarks,
            bookmarkLabels: $bookmarkLabels,
        }"""

    }

    fun requestMoreTextAtTop(callId: Long) = GlobalScope.launch(Dispatchers.IO) {
        Log.d(TAG, "requestMoreTextAtTop")
        val currentPage = window.pageManager.currentPage
        if (currentPage is CurrentBiblePage) {
            val newChap = minChapter - 1

            if(newChap < 1) return@launch

            val fragment = currentPage.getFragmentForChapter(newChap)
            addChapter(newChap)
            executeJavascriptOnUiThread("bibleView.response($callId, ${getOsisObjStr(fragment)});")
        }
    }

    fun requestMoreTextAtEnd(callId: Long) = GlobalScope.launch(Dispatchers.IO) {
        Log.d(TAG, "requestMoreTextAtEnd")
        val currentPage = window.pageManager.currentPage
        if (currentPage is CurrentBiblePage) {
            val newChap = maxChapter + 1
            val verse = currentPage.currentBibleVerse.verse
            val lastChap = verse.versification.getLastChapter(verse.book)

            if(newChap > lastChap) return@launch
            val fragment = currentPage.getFragmentForChapter(newChap)
            addChapter(newChap)
            executeJavascriptOnUiThread("bibleView.response($callId, ${getOsisObjStr(fragment)});")
        }
    }

    fun setContentReady() {
        synchronized(this) {
            if(needsOsisContent) {
                runOnUiThread {
                    replaceOsis()
                }
            } else {
                htmlLoadingOngoing = false
                contentVisible = true
            }
        }
    }

    fun hasChapterLoaded(chapter: Int) = chapter in minChapter..maxChapter

    fun setClientReady() {
        htmlLoadingOngoing = false;
        replaceOsis()
    }

    var onDestroy: (() -> Unit)? = null

    private val TAG get() = "BibleView[${windowRef.get()?.id}]"

    companion object {
        // never go to 0 because a bug in Android prevents invalidate after loadDataWithBaseURL so
        // no scrollOrJumpToVerse will occur
        private const val TOP_OF_SCREEN = 1
    }
}
