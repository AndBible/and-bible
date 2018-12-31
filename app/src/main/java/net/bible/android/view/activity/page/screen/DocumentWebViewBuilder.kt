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

package net.bible.android.view.activity.page.screen

import android.annotation.SuppressLint
import android.graphics.Typeface

import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.Window.WindowOperation
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.BibleViewFactory
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils

import javax.inject.Inject


/**
 * TEMP NOTE: http://stackoverflow.com/questions/961944/overlapping-views-in-android
 * FOR OVERLAY IMAGE
 */
/**
 * Build the main WebView component for displaying most document types
 *
 * Structure of the layout:
 * parent
 * windowFrameLayout
 * bibleView
 * separatorExtension (touch delegate for next separator)
 * separator
 * windowFrameLayout
 * bibleView
 * separatorExtension (touch delegate for previous separator)
 * minimiseButton
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class DocumentWebViewBuilder @Inject constructor(
        private val windowControl: WindowControl,
        private val mainBibleActivity: MainBibleActivity,
        private val bibleViewFactory: BibleViewFactory,
        private val windowMenuCommandHandler: WindowMenuCommandHandler
) {

    private var isWindowConfigurationChanged = true

    private var isLaidOutWithHorizontalSplit: Boolean = false

    private val WINDOW_SEPARATOR_WIDTH_PX: Int
    private val WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX: Int
    private val WINDOW_BUTTON_TEXT_COLOUR: Int
    private val WINDOW_BUTTON_BACKGROUND_COLOUR: Int
    private val BUTTON_SIZE_PX: Int

    private var previousParent: LinearLayout? = null

    init {

        val res = BibleApplication.application.resources
        WINDOW_SEPARATOR_WIDTH_PX = res.getDimensionPixelSize(R.dimen.window_separator_width)
        WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX = res.getDimensionPixelSize(R.dimen.window_separator_touch_expansion_width)
        WINDOW_BUTTON_TEXT_COLOUR = res.getColor(R.color.window_button_text_colour)
        WINDOW_BUTTON_BACKGROUND_COLOUR = res.getColor(R.color.window_button_background_colour)

        BUTTON_SIZE_PX = res.getDimensionPixelSize(R.dimen.minimise_restore_button_size)

        // Be notified of any changes to window config
        ABEventBus.getDefault().register(this)
    }

    /**
     * Record changes to scplit screen config so can redraw screen from scratch.
     */
    fun onEvent(event: NumberOfWindowsChangedEvent) {
        isWindowConfigurationChanged = true
    }

    /**
     * Enable switch from Bible WebView to MyNote view
     */
    fun removeWebView(parent: ViewGroup) {
        val isWebView = isWebViewShowing(parent)

        if (isWebView) {
            parent.tag = ""
            removeChildViews(parent)
        }
    }

    @SuppressLint("RtlHardcoded")
    fun addWebView(parent: LinearLayout) {

        val pref = CommonUtils.getSharedPreference(SPLIT_MODE_PREF, SPLIT_MODE_AUTOMATIC)

        val isSplitHorizontally: Boolean
        when (pref) {
            SPLIT_MODE_AUTOMATIC -> isSplitHorizontally = CommonUtils.isPortrait()
            SPLIT_MODE_VERTICAL -> isSplitHorizontally = false
            SPLIT_MODE_HORIZONTAL -> isSplitHorizontally = true
            else -> throw RuntimeException("Illegal preference")
        }

        val isWebView = isWebViewShowing(parent)
        parent.tag = TAG

        if (!isWebView ||
                isWindowConfigurationChanged ||
                isSplitHorizontally != isLaidOutWithHorizontalSplit) {
            Log.d(TAG, "Layout web view")

            val windows = windowControl.windowRepository.visibleWindows

            // ensure we have a known starting point - could be none, 1, or 2 webviews present
            removeChildViews(previousParent)

            parent.orientation = if (isSplitHorizontally) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            var currentWindowFrameLayout: ViewGroup? = null
            var previousSeparator: Separator? = null

            var windowNo = 0

            for (window in windows) {
                Log.d(TAG, "Layout screen " + window.screenNo + " of " + windows.size)

                currentWindowFrameLayout = FrameLayout(this.mainBibleActivity)

                val bibleView = getCleanView(window)

                // trigger recalc of verse positions in case width changes e.g. minimize/restore web view
                bibleView.setVersePositionRecalcRequired(true)

                val windowWeight = window.windowLayout.weight
                val lp = if (isSplitHorizontally)
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, windowWeight)
                else
                    LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, windowWeight)

                parent.addView(currentWindowFrameLayout, lp)

                // add bible to framelayout
                val frameLayoutParamsBibleWebView = FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                currentWindowFrameLayout.addView(bibleView, frameLayoutParamsBibleWebView)

                if (windowNo > 0) {
                    val separator = previousSeparator

                    // extend touch area of separator
                    addTopOrLeftSeparatorExtension(isSplitHorizontally, currentWindowFrameLayout, lp, separator!!)
                }

                // Add screen separator
                if (windowNo < windows.size - 1) {
                    val nextWindow = windows[windowNo + 1]
                    val separator = createSeparator(parent, window, nextWindow, isSplitHorizontally, windows.size)

                    // extend touch area of separator
                    addBottomOrRightSeparatorExtension(isSplitHorizontally, currentWindowFrameLayout, lp, separator)

                    // Add actual separator line dividing two windows
                    parent.addView(separator, if (isSplitHorizontally)
                        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_WIDTH_PX, 0f)
                    else
                        LinearLayout.LayoutParams(WINDOW_SEPARATOR_WIDTH_PX, LayoutParams.MATCH_PARENT, 0f))
                    // allow extension to be added in next screen
                    previousSeparator = separator
                }

                // create default action button for top right of each window
                val defaultWindowActionButton = createDefaultWindowActionButton(window)
                currentWindowFrameLayout.addView(defaultWindowActionButton,
                        FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.TOP or Gravity.RIGHT))

                windowNo++
            }

            // Display minimised screens
            val minimisedWindowsFrameContainer = LinearLayout(mainBibleActivity)
            currentWindowFrameLayout!!.addView(minimisedWindowsFrameContainer,
                    FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, BUTTON_SIZE_PX,
                            Gravity.BOTTOM or Gravity.RIGHT))
            val minimisedScreens = windowControl.windowRepository.minimisedScreens
            for (i in minimisedScreens.indices) {
                Log.d(TAG, "Show restore button")
                val restoreButton = createRestoreButton(minimisedScreens[i])
                minimisedWindowsFrameContainer.addView(restoreButton,
                        LinearLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX))
            }

            previousParent = parent
            isLaidOutWithHorizontalSplit = isSplitHorizontally
            isWindowConfigurationChanged = false
        }
    }

    private fun createDefaultWindowActionButton(window: Window): View {
        val defaultWindowActionButton: View
        if (window.defaultOperation == WindowOperation.CLOSE) {
            // close button for the links window
            defaultWindowActionButton = createCloseButton(window)
        } else if (window.defaultOperation == WindowOperation.MAXIMISE) {
            // normalise button for maximised window
            defaultWindowActionButton = createMaximiseToggleButton(window)
        } else {
            // minimise button for normal window
            defaultWindowActionButton = createMinimiseButton(window)
        }
        return defaultWindowActionButton
    }

    /**
     * Add extension preceding separator
     */
    @SuppressLint("RtlHardcoded")
    private fun addBottomOrRightSeparatorExtension(isPortrait: Boolean,
                                                   previousWindowLayout: ViewGroup,
                                                   previousLp: LinearLayout.LayoutParams,
                                                   separator: Separator) {
        // add first touch delegate to framelayout which extends the touch area, otherwise it is difficult to select the separator to move it
        val frameLayoutParamsSeparatorDelegate = if (isPortrait)
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.BOTTOM)
        else
            FrameLayout.LayoutParams(WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.MATCH_PARENT, Gravity.RIGHT)
        previousWindowLayout.addView(separator.touchDelegateView1, frameLayoutParamsSeparatorDelegate)
        // separator will adjust layouts when dragged
        separator.setView1LayoutParams(previousLp)
    }

    /**
     * Add extension after separator
     */
    @SuppressLint("RtlHardcoded")
    private fun addTopOrLeftSeparatorExtension(isPortrait: Boolean,
                                               currentWindowLayout: ViewGroup,
                                               lp: LinearLayout.LayoutParams,
                                               separator: Separator) {
        // add separator handle touch delegate to framelayout
        val frameLayoutParamsSeparatorDelegate = if (isPortrait)
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.TOP)
        else
            FrameLayout.LayoutParams(WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.MATCH_PARENT, Gravity.LEFT)
        currentWindowLayout.addView(separator.touchDelegateView2, frameLayoutParamsSeparatorDelegate)

        // separator will adjust layouts when dragged
        separator.setView2LayoutParams(lp)
    }

    protected fun createSeparator(
            parent: LinearLayout,
            window: Window,
            nextScreen: Window,
            isPortrait: Boolean,
            numWindows: Int): Separator {
        return Separator(this.mainBibleActivity, WINDOW_SEPARATOR_WIDTH_PX, parent, window, nextScreen,
                numWindows, isPortrait, windowControl)
    }

    /**
     * parent contains Frame, seperator, Frame.
     * Frame contains BibleView
     * @param parent
     */
    private fun removeChildViews(parent: ViewGroup?) {
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val view = parent.getChildAt(i)
                if (view is ViewGroup) {
                    // this detaches the BibleView from it's containing Frame
                    removeChildViews(view)
                }
            }

            parent.removeAllViews()
        }
    }

    /**
     * Attempt to fix occasional error: "The specified child already has a parent. You must call removeView() on the child's parent first."
     */
    private fun getCleanView(window: Window): BibleView {
        val bibleView = getView(window)
        val parent = bibleView.parent
        if (parent != null && parent is ViewGroup) {
            parent.removeView(bibleView)
        }
        return bibleView
    }

    fun getView(window: Window): BibleView {
        return bibleViewFactory.createBibleView(window)
    }

    private fun createCloseButton(window: Window): Button {
        return createTextButton("X", OnClickListener { windowControl.closeWindow(window) },
                WindowButtonLongClickListener(window))
    }

    private fun createMaximiseToggleButton(window: Window): Button {
        return createImageButton(R.drawable.ic_menu_unmaximise,
                OnClickListener { windowControl.unmaximiseWindow(window) },
                WindowButtonLongClickListener(window))
    }

    private fun createMinimiseButton(window: Window): Button {
        return createTextButton("━━", OnClickListener { windowControl.minimiseWindow(window) },
                WindowButtonLongClickListener(window))
    }

    private fun createRestoreButton(window: Window): Button {
        // restore button
        return createTextButton(getDocumentInitial(window),
                OnClickListener { windowControl.restoreWindow(window) }, null)
    }

    /**
     * Get the first initial of the doc in the window to show in the minimise restore button
     */
    protected fun getDocumentInitial(window: Window): String {
        try {
            return window.pageManager.currentPage.currentDocument.abbreviation.substring(0, 1)
        } catch (e: Exception) {
            return " "
        }

    }

    private fun createTextButton(text: String, onClickListener: OnClickListener, onLongClickListener: OnLongClickListener?): Button {
        val button = Button(this.mainBibleActivity)
        button.text = text
        button.setBackgroundColor(WINDOW_BUTTON_BACKGROUND_COLOUR)
        button.width = BUTTON_SIZE_PX
        button.height = BUTTON_SIZE_PX
        button.setTextColor(WINDOW_BUTTON_TEXT_COLOUR)
        button.setTypeface(null, Typeface.BOLD)
        button.setSingleLine(true)
        button.setOnClickListener(onClickListener)
        button.setOnLongClickListener(onLongClickListener)
        return button
    }

    private fun createImageButton(drawableId: Int, onClickListener: OnClickListener, onLongClickListener: OnLongClickListener): Button {
        val button = Button(this.mainBibleActivity)
        button.setBackgroundColor(WINDOW_BUTTON_BACKGROUND_COLOUR)
        button.setBackgroundResource(drawableId)
        button.width = BUTTON_SIZE_PX
        button.height = BUTTON_SIZE_PX
        button.setOnClickListener(onClickListener)
        button.setOnLongClickListener(onLongClickListener)
        return button
    }

    private inner class WindowButtonLongClickListener(private val window: Window) : OnLongClickListener {

        @SuppressLint("RestrictedApi")
        override fun onLongClick(v: View): Boolean {
            // ensure actions affect the right window
            windowControl.activeWindow = window

            val popup = PopupMenu(mainBibleActivity, v)
            popup.setOnMenuItemClickListener { menuItem -> windowMenuCommandHandler.handleMenuRequest(menuItem) }

            val inflater = popup.menuInflater
            inflater.inflate(R.menu.window_popup_menu, popup.menu)

            // enable/disable and set synchronised checkbox
            windowControl.updateOptionsMenu(popup.menu)

            val menuHelper = MenuPopupHelper(mainBibleActivity, popup.menu as MenuBuilder, v)
            menuHelper.setForceShowIcon(true)
            menuHelper.show()

            return true
        }
    }

    private fun isWebViewShowing(parent: ViewGroup): Boolean {
        val tag = parent.tag
        return tag != null && tag == TAG
    }

    companion object {

        private val SPLIT_MODE_PREF = "split_mode_pref"
        private val SPLIT_MODE_AUTOMATIC = "automatic"
        private val SPLIT_MODE_VERTICAL = "vertical"
        private val SPLIT_MODE_HORIZONTAL = "horizontal"

        private val TAG = "DocumentWebViewBuilder"
    }
}
