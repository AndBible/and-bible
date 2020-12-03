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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.iterator
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.event.window.ScrollSecondaryWindowEvent
import net.bible.android.control.event.window.WindowSizeChangedEvent
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.OsisFragment
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.DecrementBusyCount
import net.bible.android.control.page.window.IncrementBusyCount
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.toV11n
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.json
import net.bible.android.view.activity.base.DocumentView
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator
import net.bible.android.view.activity.page.screen.AfterRemoveWebViewEvent
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.android.view.activity.page.screen.WebViewsBuiltEvent
import net.bible.android.view.util.UiUtils
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import java.lang.ref.WeakReference

/** The WebView component that shows the main bible and commentary text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

/**
 * Constructor.  This version is only needed if you will be instantiating
 * the object manually (not from a layout XML file).
 */

@SuppressLint("ViewConstructor")
class BibleView(val mainBibleActivity: MainBibleActivity,
                internal var windowRef: WeakReference<Window>,
                private val windowControl: WindowControl,
                private val bibleKeyHandler: BibleKeyHandler,
                private val pageControl: PageControl,
                private val pageTiltScrollControl: PageTiltScrollControl,
                private val linkControl: LinkControl,
                private val bookmarkControl: BookmarkControl
) :
        WebView(mainBibleActivity.applicationContext),
        DocumentView,
        VerseActionModeMediator.VerseHighlightControl
{
    private lateinit var bibleJavascriptInterface: BibleJavascriptInterface

    private lateinit var pageTiltScroller: PageTiltScroller
    private var hideScrollBar: Boolean = false

    private var wasAtRightEdge: Boolean = false
    private var wasAtLeftEdge: Boolean = false

    internal var minChapter = -1
    internal var maxChapter = -1

    //private var loadedChapters = mutableSetOf<Int>()


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
    }

    private fun onActionMenuItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.highlight1 -> {
                executeJavascript("bibleView.highlight1();")
                mode.finish()
                return true
            }
            else -> false
        }
    }

    private fun onPrepareActionMenu(mode: ActionMode, menu: Menu) {
        mode.menuInflater.inflate(R.menu.bibleview_selection, menu)
        //menu.add(Menu.FIRST, 0, 100, "Test")
    }

    // TODO: remove after Lollipop support is dropped.
    private inner class ActionModeCallback(val callback: ActionMode.Callback): ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            callback.onCreateActionMode(mode, menu)
            onPrepareActionMenu(mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val wasUpdated = callback.onPrepareActionMode(mode, menu)
            return wasUpdated
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return onActionMenuItemClicked(mode, item) || callback.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            return callback.onDestroyActionMode(mode)
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private inner class ActionModeCallback2(val callback: ActionMode.Callback): ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            callback.onCreateActionMode(mode, menu)
            onPrepareActionMenu(mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val wasUpdated = callback.onPrepareActionMode(mode, menu)
            val menuItems = ArrayList<MenuItem>()
            for(m in menu) {
                menuItems.add(m)
            }
            menu.clear()
            for(m in menuItems.reversed()) {
                menu.add(0, m.itemId, m.order, m.title)
            }
            return wasUpdated
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return onActionMenuItemClicked(mode, item) || callback.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            return callback.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: Rect?) {
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

    /**
     * This is not passed into the constructor due to a cyclic dependency. bjsi ->
     */
    fun setBibleJavascriptInterface(bibleJavascriptInterface: BibleJavascriptInterface) {
        this.bibleJavascriptInterface = bibleJavascriptInterface
        addJavascriptInterface(bibleJavascriptInterface, "android")
    }

    class BibleLink(val type: String, val target: String) {
        val url get() = "$type:$target"
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initialise() {
        Log.d(TAG, "initialise")
        webViewClient = BibleViewClient()

        //WebViewCompat.postWebMessage(this, WebMessageCompat("test"))
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            val listener = WebViewCompat.WebMessageListener { view, message, sourceOrigin, isMainFrame, replyProxy ->
                //TODO("Not yet implemented")
                Log.d(TAG, "Message from js: ${message.data}")
                replyProxy.postMessage("test back to js side!")

            }
            val rules = setOf("*")
            WebViewCompat.addWebMessageListener(this, "androidBibleView", rules, listener)
        }

        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                Log.d(TAG, message)
                result.confirm()
                return true
            }
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(TAG, "JS console ${consoleMessage.messageLevel()}: ${consoleMessage.message()}")
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
        loadUrl("file:///android_asset/bibleview-js/index.html")
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
        const val SCHEME_W = "ab-w"
        const val SCHEME_REFERENCE = "ab-reference"
    }

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
                        //contextMenuInfo = MultiLinkContextMenuInfo(links)
                        //view.showContextMenu()
                        linkControl.loadApplicationUrl(links)
                    } else {
                        // TODO: open link directly if only one entry
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
                else -> true // TODO: throw some error document instead
            }

            if(loaded) {
                gestureListener.setDisableSingleTapOnce(true)
                super.shouldOverrideUrlLoading(view, req)
                return true
            }
            else {
                return super.shouldOverrideUrlLoading(view, req)
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

    /**
     * Either enable verse selection or the default text selection
     */
    private fun enableSelection() {
        if (window.pageManager.isBibleShown) {
            // handle long click ourselves and prevent webview showing text selection automatically
            setOnLongClickListener(BibleViewLongClickListener(false))
            //isLongClickable = false
        } else {
            // reset handling of long press
            setOnLongClickListener(BibleViewLongClickListener(false))
        }
    }

    internal interface BibleViewContextMenuInfo: ContextMenuInfo {
        fun onContextItemSelected(item: MenuItem): Boolean
        fun onCreateContextMenu(menu: ContextMenu, v: View, menuInflater: MenuInflater)
    }

    internal inner class MultiLinkContextMenuInfo(private val links: List<BibleLink>): BibleViewContextMenuInfo {
        override fun onContextItemSelected(item: MenuItem): Boolean {
            return linkControl.loadApplicationUrl(links[item.itemId])
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInflater: MenuInflater) {
            links.forEachIndexed { i, l ->
                val title = "${l.type}:${l.target}"
                menu.add(Menu.NONE, i, Menu.NONE, title)
            }
        }

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

        Log.d(TAG, "Show $initialVerse, $jumpToYOffsetRatio Window:$window, settings: toolbarOFfset:${toolbarOffset}, \n actualSettings: ${displaySettings.toJson()}")

        latestBookmarks = bookmarks
        latestOsisObjStr = getOsisObjStr(osisFrags)

        withContext(Dispatchers.Main) {
            updateBackgroundColor()
            applyFontSize()
            //enableSelection()
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
        executeJavascriptOnUiThread("bibleView.setConfig(${displaySettings.toJson()});")

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
            bibleView.setTitle("BibleView-${window.id}");
            bibleView.setConfig(${displaySettings.toJson()});
            bibleView.replaceOsis($osisObjStr);
            bibleView.setupContent({
                jumpToOrdinal: ${initialVerse?.ordinal}, 
                jumpToYOffsetRatio: null,
                toolBarOffset: $toolbarOffset,
            });            
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
            executeJavascriptOnUiThread("bibleView.setToolbarOffset($toolbarOffset, {immediate: true});")
    }

    fun onEvent(event: MainBibleActivity.FullScreenEvent) {
        if(isTopWindow && contentVisible && window.isVisible)
            executeJavascriptOnUiThread("bibleView.setToolbarOffset($toolbarOffset);")
    }

    fun onEvent(event: WebViewsBuiltEvent) {
        checkWindows = true
    }

    val isTopWindow
        get() = !CommonUtils.isSplitVertically || windowControl.windowRepository.firstVisibleWindow == window

    val toolbarOffset
        get() =
            if(isTopWindow && !SharedActivityState.instance.isFullScreen)
                (mainBibleActivity.topOffset2
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
            executeJavascript("bibleView.setToolbarOffset($toolbarOffset, {doNotScroll: true});")
            if (window.pageManager.currentPage.bookCategory == BookCategory.BIBLE) {
                executeJavascript("registerVersePositions()")
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
        val jumpToId = "v-${verse.toV11n(initialVerse!!.versification).ordinal}"
        val now = if(!contentVisible || restoreOngoing) "true" else "false"
        executeJavascript("bibleView.scrollToVerse('$jumpToId', $now, $toolbarOffset);")
    }

    override fun enableVerseTouchSelection() {
        gestureListener.setVerseSelectionMode(true)
        executeJavascriptOnUiThread("enableVerseTouchSelection()")
    }

    override fun disableVerseTouchSelection() {
        executeJavascriptOnUiThread("disableVerseTouchSelection()")
        gestureListener.setVerseSelectionMode(false)
    }

    override fun highlightVerse(chapterVerse: ChapterVerse, start: Boolean) {
        val offset = if(isTopWindow) (mainBibleActivity.topOffset2 / mainBibleActivity.resources.displayMetrics.density) else 0f
        executeJavascriptOnUiThread("highlightVerse('" + chapterVerse.toHtmlId() + "' , $start, $offset)")
    }

    override fun unhighlightVerse(chapterVerse: ChapterVerse) {
        executeJavascriptOnUiThread("unhighlightVerse('" + chapterVerse.toHtmlId() + "')")
    }

    override fun clearVerseHighlight() {
        executeJavascriptOnUiThread("clearVerseHighlight()")
    }

    private fun executeJavascriptOnUiThread(javascript: String) {
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
        Log.d(TAG, "Executing JS: $javascript")
        evaluateJavascript("$javascript;", callBack)
    }


    @Serializable
    data class ClientBookmark(val id: Long, val ordinalRange: List<Int>, val elementRange: List<List<Int>>?, val labels: List<Long>, val book: String?)

    @Serializable
    data class ClientBookmarkStyle(val color: List<Int>)

    @Serializable
    data class ClientBookmarkLabel(val id: Long, val style: ClientBookmarkStyle?)

    private fun getOsisObjStr(frags: List<OsisFragment>): String {
        val bookmarkLabels = json.encodeToString(serializer(), bookmarkLabels.map {
            ClientBookmarkLabel(it.id, it.bookmarkStyle?.let { v -> ClientBookmarkStyle(v.colorArray) })
        })
        val bookmarks = json.encodeToString(serializer(), latestBookmarks.map {
            val labels = bookmarkControl.labelsForBookmark(it).toMutableList()
            if(labels.isEmpty())
                labels.add(bookmarkControl.LABEL_UNLABELLED)
            ClientBookmark(it.id, arrayListOf(it.ordinalStart, it.ordinalEnd), it.textRange?.toClientList(), labels.map { it.id }, it.book?.initials )
        })
        val xmlList = frags.map {"""{xml: `${it.xml}`, key:'${it.keyStr}', ordinalRange: ${it.ordinalRangeJson}"""}.joinToString(",")
        return """{
            contents: [$xmlList],
            bookmarks: $bookmarks,
            bookmarkLabels: $bookmarkLabels,
        }"""

    }

    fun insertTextAtTop(chapter: Int, osisFragment: List<OsisFragment>) {
        addChapter(chapter)
        executeJavascriptOnUiThread("bibleView.insertThisTextAtTop(${getOsisObjStr(osisFragment)});")
    }

    fun insertTextAtEnd(chapter: Int, osisFragment: List<OsisFragment>) {
        addChapter(chapter)
        executeJavascriptOnUiThread("bibleView.insertThisTextAtEnd(${getOsisObjStr(osisFragment)});")
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
