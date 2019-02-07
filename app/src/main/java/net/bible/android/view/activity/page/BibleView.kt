/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
import android.annotation.TargetApi
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.GestureDetectorCompat
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.*
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.DocumentView
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.android.view.util.UiUtils
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import org.apache.commons.lang3.StringUtils

/** The WebView component that shows the main bible and commentary text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

/**
 * Constructor.  This version is only needed if you will be instantiating
 * the object manually (not from a layout XML file).
 */

class BibleView(val mainBibleActivity: MainBibleActivity,
                val windowNo: Window,
                private val windowControl: WindowControl,
                private val bibleKeyHandler: BibleKeyHandler,
                private val pageControl: PageControl,
                private val pageTiltScrollControl: PageTiltScrollControl,
                private val linkControl: LinkControl) :
        WebView(mainBibleActivity),
        DocumentView,
        VerseActionModeMediator.VerseHighlightControl,
        BibleViewTextInserter
{

    private var contextMenuInfo: BibleViewContextMenuInfo? = null

    private lateinit var bibleJavascriptInterface: BibleJavascriptInterface

    private var mJumpToChapterVerse = ChapterVerse.NOT_SET
    private var mJumpToYOffsetRatio = SharedConstants.NO_VALUE.toFloat()

    private var mIsVersePositionRecalcRequired = true

    private lateinit var mPageTiltScroller: PageTiltScroller
    private var hideScrollBar: Boolean = false

    private var wasAtRightEdge: Boolean = false
    private var wasAtLeftEdge: Boolean = false

    private var maintainMovingChapterVerse = ChapterVerse.NOT_SET

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

    class BibleViewTouched

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != BibleApplication.application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
        gestureDetector = GestureDetectorCompat(context, gestureListener)
        setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                true
            } else v.performClick()
        }
    }

    /**
     * This is not passed into the constructor due to a cyclic dependency. bjsi ->
     */
    fun setBibleJavascriptInterface(bibleJavascriptInterface: BibleJavascriptInterface) {
        this.bibleJavascriptInterface = bibleJavascriptInterface
        addJavascriptInterface(bibleJavascriptInterface, "jsInterface")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initialise() {

        /* WebViewClient must be set BEFORE calling loadUrl! */
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // load Strongs refs when a user clicks on a link
                val loaded = linkControl.loadApplicationUrl(url)

                if(loaded) {
                    gestureListener.setDisableSingleTapOnce(true)
                    super.shouldOverrideUrlLoading(view, url)
                    return true
                }
                else {
                    return super.shouldOverrideUrlLoading(view, url)
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

        // handle alerts
        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                Log.d(TAG, message)
                result.confirm()
                return true
            }
        }

        // need javascript to enable jump to anchors/verses
        settings.javaScriptEnabled = true

        applyPreferenceSettings()

        mPageTiltScroller = PageTiltScroller(this, pageTiltScrollControl)
        mPageTiltScroller.enableTiltScroll(true)

        // if this webview becomes (in)active then must start/stop auto-scroll
        ABEventBus.getDefault().register(this)

        // initialise split state related code - always screen1 is selected first
        onEvent(CurrentWindowChangedEvent(windowControl.activeWindow))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // update the height in ScreenSettings. Global screen height must be taken from parent view
        // that contains all windows.
        if (parent != null && parent.parent != null) {
            ScreenSettings.setContentViewHeightPx((parent.parent as View).measuredHeight)
        }
    }

    /** apply settings set by the user using Preferences
     */
    override fun applyPreferenceSettings() {
        applyFontSize()

        changeBackgroundColour()

        ScreenSettings.setContentViewHeightPx(height)
    }

    private fun applyFontSize() {
        val fontSize = pageControl.getDocumentFontSize(windowNo)
        settings.defaultFontSize = fontSize

        // 1.6 is taken from css - line-height: 1.6em;
        ScreenSettings.setLineHeightDips((1.6 * fontSize).toInt())
    }

    /** may need updating depending on environmental brightness
     */
    override fun changeBackgroundColour() {
        // if night mode then set dark background colour

        val changed = if(mainBibleActivity.ready) {
            UiUtils.applyTheme(mainBibleActivity)
        } else true

        UiUtils.setBibleViewBackgroundColour(this, ScreenSettings.isNightMode())
    }

    override fun show(origHtml: String, chapterVerse: ChapterVerse, jumpToYOffsetRatio: Float) {
        var html = origHtml
        Log.d(TAG, "Show(html,$chapterVerse,$jumpToYOffsetRatio) Window:$windowNo")
        // set background colour if necessary
        changeBackgroundColour()

        // call this from here because some documents may require an adjusted font size e.g. those using Greek font
        applyFontSize()

        var delta = 0.0F

        if(!SharedActivityState.getInstance().isFullScreen
                && (!mainBibleActivity.isSplitHorizontally || windowControl.windowRepository.firstWindow == windowNo))
        {
            delta = mainBibleActivity.topOffset2 / mainBibleActivity.resources.displayMetrics.density
            html = html.replace("<div id='start'>", "<div id='start' style='height:${delta}px'>")
        }

        // If verse 1 then later code will jump to top of screen because it looks better than going to verse 1
        html = html.replace("</body>", "<script>$(document).ready(function() {scrollToVerse('${getIdToJumpTo(chapterVerse)}', true, $delta);})</script></body>")
        mJumpToYOffsetRatio = jumpToYOffsetRatio

        // either enable verse selection or the default text selection
        html = enableSelection(html)

        // allow zooming if map
        enableZoomForMap(pageControl.currentPageManager.isMapShown)

        loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", "http://historyUrl" + historyUrlUniquify++)

        // ensure jumpToOffset is eventually called during initialisation.  It will normally be called automatically but sometimes is not i.e. after jump to verse 1 at top of screen then press back.
        // don't set this value too low or it may trigger before a proper upcoming computeVerticalScrollEvent
        // 100 was good for my Nexus 4 but 500 for my G1 - it would be good to get a reflection of processor speed and adjust appropriately
        invokeJumpToOffsetIfRequired((if (CommonUtils.isSlowDevice()) 500 else 350).toLong())
    }

    /**
     * Enable or disable zoom controls depending on whether map is currently shown
     */
    private fun enableZoomForMap(isMap: Boolean) {
        settings.builtInZoomControls = true
        settings.setSupportZoom(isMap)
        settings.displayZoomControls = false
        // http://stackoverflow.com/questions/3808532/how-to-set-the-initial-zoom-width-for-a-webview
        settings.loadWithOverviewMode = isMap
        settings.useWideViewPort = isMap
    }

    /**
     * This is called fairly late in initialisation so override to invoke jump to offset position
     */
    override fun computeVerticalScrollExtent(): Int {
        val result = super.computeVerticalScrollExtent()

        // trigger jump to appropriate verse or offset into a book or commentary page...
        invokeJumpToOffsetIfRequired(0)

        return result
    }

    /**
     * Trigger jump to correct offset
     */
    private fun invokeJumpToOffsetIfRequired(delay: Long) {
        if (ChapterVerse.isSet(mJumpToChapterVerse) || mJumpToYOffsetRatio != SharedConstants.NO_VALUE.toFloat()) {
            postDelayed({ jumpToOffset() }, delay)
        }
    }

    private fun jumpToOffset() {
        if (contentHeight > 0) {
            if (mIsVersePositionRecalcRequired) {
                mIsVersePositionRecalcRequired = false
                executeJavascript("registerVersePositions()")
            }

            bibleJavascriptInterface.setNotificationsEnabled(windowControl.isActiveWindow(windowNo))

            // screen is changing shape/size so constantly maintain the current verse position
            // main difference from jumpToVerse is that this is not cleared after jump
            if (ChapterVerse.isSet(maintainMovingChapterVerse)) {
                scrollOrJumpToVerse(maintainMovingChapterVerse)
            }

            // go to any specified verse or offset
            if (ChapterVerse.isSet(mJumpToChapterVerse)) {
                // must clear mJumpToChapterVerse because setting location causes another onPageFinished
                val jumpToChapterVerse = mJumpToChapterVerse
                mJumpToChapterVerse = ChapterVerse.NOT_SET

                scrollOrJumpToVerse(jumpToChapterVerse)

            } else if (mJumpToYOffsetRatio != SharedConstants.NO_VALUE.toFloat()) {
                val contentHeight = contentHeight
                val y = (contentHeight.toFloat() * mJumpToYOffsetRatio).toInt()

                // must zero mJumpToYOffsetRatio because setting location causes another onPageFinished
                mJumpToYOffsetRatio = SharedConstants.NO_VALUE.toFloat()

                // Top of the screen is handled by scrollToVerse in the page loading.
                // We want to take care of only to go to specific point in commentary/generalbook page
                // when pressing back button.
                if(y > TOP_OF_SCREEN) {
                    scrollTo(0, y)
                }
            }
        }
    }

    /** prevent swipe right if the user is scrolling the page right  */
    override fun isPageNextOkay(): Boolean {
        var isOkay = true
        if (windowNo.pageManager.isMapShown) {
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
    override fun isPagePreviousOkay(): Boolean {
        var isOkay = true
        if (windowNo.pageManager.isMapShown) {
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
        Log.d(TAG, "Focus changed so start/stop scroll")
        if (hasWindowFocus) {
            resumeTiltScroll()
        } else {
            pauseTiltScroll()
        }
    }

    private fun pauseTiltScroll() {
        Log.d(TAG, "Pausing tilt to scroll $windowNo")
        mPageTiltScroller.enableTiltScroll(false)
    }

    private fun resumeTiltScroll() {
        // but if multiple windows then only if the current active window
        if (windowControl.isActiveWindow(windowNo)) {
            Log.d(TAG, "Resuming tilt to scroll $windowNo")
            mPageTiltScroller.enableTiltScroll(true)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        //Log.d(TAG, "BibleView onTouchEvent");
        windowControl.activeWindow = windowNo

        val handled = super.onTouchEvent(event)

        // Allow user to redefine viewing angle by touching screen
        mPageTiltScroller.recalculateViewingPosition()

        return handled
    }

    override fun getCurrentPosition(): Float {
        // see http://stackoverflow.com/questions/1086283/getting-document-position-in-a-webview
        val contentHeight = contentHeight
        val scrollY = scrollY

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
        if (windowNo == event.activeWindow) {
            bibleJavascriptInterface.setNotificationsEnabled(true)
            resumeTiltScroll()
        } else {
            bibleJavascriptInterface.setNotificationsEnabled(false)
            pauseTiltScroll()
        }
    }

    fun onEvent(event: UpdateSecondaryWindowEvent) {
        if (windowNo == event.updateScreen) {
            changeBackgroundColour()
            show(event.html, event.chapterVerse, SharedConstants.NO_VALUE.toFloat())
        }
    }

    fun onEvent(event: ScrollSecondaryWindowEvent) {
        if (windowNo == event.window && handler != null) {
            scrollOrJumpToVerseOnUIThread(event.chapterVerse)
        }
    }

    fun onEvent(event: WindowSizeChangedEvent) {
        Log.d(TAG, "window size changed")
        val isScreenVerse = event.isVerseNoSet(windowNo)
        if (isScreenVerse) {
            this.maintainMovingChapterVerse = event.getChapterVerse(windowNo)
        }

        // when move finished the verse positions will have changed if in Landscape so recalc positions
        val isMoveFinished = event.isFinished
        if (isMoveFinished && isScreenVerse) {
            val chapterVerse = event.getChapterVerse(windowNo)
            setJumpToVerse(chapterVerse)

            val handler = handler
            handler?.postDelayed({
                // clear jump value if still set
                this@BibleView.maintainMovingChapterVerse = ChapterVerse.NOT_SET

                // ensure we are in the correct place after screen settles
                scrollOrJumpToVerse(chapterVerse)
                executeJavascript("registerVersePositions()")
            }, (WindowControl.SCREEN_SETTLE_TIME_MILLIS / 2).toLong())
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "Detached from window")
        // prevent random verse changes while layout is being rebuild because of window changes
        bibleJavascriptInterface.setNotificationsEnabled(false)
        pauseTiltScroll()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "Attached to window")
        if (windowControl.isActiveWindow(windowNo)) {
            bibleJavascriptInterface.setNotificationsEnabled(true)

            // may have returned from MyNote view
            resumeTiltScroll()
        }
    }

    fun onEvent(event: NumberOfWindowsChangedEvent) {
        if (visibility == View.VISIBLE && event.isVerseNoSet(windowNo)) {
            setJumpToVerse(event.getChapterVerse(windowNo))
        }
    }

    fun setVersePositionRecalcRequired(mIsVersePositionRecalcRequired: Boolean) {
        this.mIsVersePositionRecalcRequired = mIsVersePositionRecalcRequired
    }

    private fun setJumpToVerse(chapterVerse: ChapterVerse) {
        this.mJumpToChapterVerse = chapterVerse
    }

    /** move the view so the selected verse is at the top or at least visible
     * @param verse
     */
    fun scrollOrJumpToVerseOnUIThread(verse: ChapterVerse) {
        runOnUiThread(Runnable { scrollOrJumpToVerse(verse) })
    }

    /** move the view so the selected verse is at the top or at least visible
     */
    private fun scrollOrJumpToVerse(chapterVerse: ChapterVerse) {
        Log.d(TAG, "Scroll or jump to:$chapterVerse")
        if (ChapterVerse.isSet(chapterVerse)) {
            // jump to correct verse
            // required format changed in 4.2 http://stackoverflow.com/questions/14771970/how-to-call-javascript-in-android-4-2
            if(!SharedActivityState.getInstance().isFullScreen && (!mainBibleActivity.isSplitHorizontally || windowControl.windowRepository.firstWindow == windowNo)) {
                val delta = (mainBibleActivity.topOffset2) / mainBibleActivity.resources.displayMetrics.density
                executeJavascript("scrollToVerse('${getIdToJumpTo(chapterVerse)}', false, ${delta})")
            } else {
                executeJavascript("scrollToVerse('${getIdToJumpTo(chapterVerse)}')")
            }
        }
    }

    internal inner class BibleViewLongClickListener(private var defaultValue: Boolean) : View.OnLongClickListener {

        override fun onLongClick(v: View): Boolean {
            val result = hitTestResult
            return if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                setContextMenuInfo(result.extra!!)
                v.showContextMenu()
            } else {
                contextMenuInfo = null
                defaultValue
            }
        }
    }

    /**
     * if verse 1 then jump to just after chapter divider at top of screen
     */
    private fun getIdToJumpTo(chapterVerse: ChapterVerse): String {
        return if (chapterVerse.verse > 1) {
            chapterVerse.toHtmlId()
        } else {
            chapterVerse.toChapterHtmlId()
        }
    }

    /**
     * Either enable verse selection or the default text selection
     */
    private fun enableSelection(origHtml: String): String {
        var html = origHtml
        if (windowNo.pageManager.isBibleShown) {
            // handle long click ourselves and prevent webview showing text selection automatically
            setOnLongClickListener(BibleViewLongClickListener(true))
            isLongClickable = false

            // need to enable verse selection after page load, but not always so can't use onload
            html += "<script>enableVerseLongTouchSelectionMode();</script>"

        } else {
            // reset handling of long press
            setOnLongClickListener(BibleViewLongClickListener(false))
        }

        return html
    }

    private fun setContextMenuInfo(target: String) {
        this.contextMenuInfo = BibleViewContextMenuInfo(this, target)
    }

    override fun getContextMenuInfo(): ContextMenuInfo? {
        return contextMenuInfo
    }

    internal inner class BibleViewContextMenuInfo(targetView: View, private var targetLink: String) : ContextMenu.ContextMenuInfo {
        private var targetView: BibleView = targetView as BibleView

        fun activate(itemId: Int) {
            when (itemId) {
                R.id.open_link_in_special_window -> targetView.linkControl.setWindowMode(LinkControl.WINDOW_MODE_SPECIAL)
                R.id.open_link_in_new_window -> targetView.linkControl.setWindowMode(LinkControl.WINDOW_MODE_NEW)
                R.id.open_link_in_main_window -> targetView.linkControl.setWindowMode(LinkControl.WINDOW_MODE_MAIN)
                R.id.open_link_in_this_window -> targetView.linkControl.setWindowMode(LinkControl.WINDOW_MODE_THIS)
            }
            targetView.linkControl.loadApplicationUrl(targetLink)
            targetView.linkControl.setWindowMode(LinkControl.WINDOW_MODE_UNDEFINED)
            contextMenuInfo = null
        }
    }

    override fun enableVerseTouchSelection() {
        gestureListener.setVerseSelectionMode(true)
        executeJavascriptOnUiThread("enableVerseTouchSelection()")
    }

    override fun disableVerseTouchSelection() {
        executeJavascriptOnUiThread("disableVerseTouchSelection()")
        gestureListener.setVerseSelectionMode(false)
    }

    override fun highlightVerse(chapterVerse: ChapterVerse) {
        executeJavascriptOnUiThread("highlightVerse('" + chapterVerse.toHtmlId() + "')")
    }

    override fun unhighlightVerse(chapterVerse: ChapterVerse) {
        executeJavascriptOnUiThread("unhighlightVerse('" + chapterVerse.toHtmlId() + "')")
    }

    override fun clearVerseHighlight() {
        executeJavascriptOnUiThread("clearVerseHighlight()")
    }

    private fun executeJavascriptOnUiThread(javascript: String) {
        runOnUiThread(Runnable { executeJavascript(javascript) })
    }

    private fun runOnUiThread(runnable: Runnable) {
        handler?.post(runnable)
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun executeJavascript(javascript: String) {
        Log.d(TAG, "Executing JS:" + StringUtils.abbreviate(javascript, 100))
        evaluateJavascript("$javascript;", null)
    }

    override fun insertTextAtTop(textId: String, text: String) {
        executeJavascriptOnUiThread("insertThisTextAtTop('$textId','$text')")
    }

    override fun insertTextAtEnd(textId: String, text: String) {
        executeJavascriptOnUiThread("insertThisTextAtEnd('$textId','$text')")
    }

    companion object {

        // struggling to ensure correct initial positioning of pages, giving the page a unique history
        // url seemed to help - maybe it then is sure each page is unique so resets everything
        private var historyUrlUniquify = 1

        // never go to 0 because a bug in Android prevents invalidate after loadDataWithBaseURL so
        // no scrollOrJumpToVerse will occur
        private const val TOP_OF_SCREEN = 1

        private const val TAG = "BibleView"
    }
}
