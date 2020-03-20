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

package net.bible.android.view.activity.page.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.children
import kotlinx.android.synthetic.main.split_bible_area.view.*
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.SettingsBundle
import net.bible.android.view.activity.MainBibleActivityModule
import net.bible.android.view.activity.DaggerMainBibleActivityComponent
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.BibleViewFactory
import net.bible.android.view.activity.page.CommandPreference
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.activity.page.Preference
import net.bible.android.view.activity.page.SubMenuPreference
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.android.view.util.widget.WindowButtonWidget
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import org.crosswire.jsword.versification.BookName
import java.lang.IndexOutOfBoundsException
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.math.max

internal val isSplitVertically get() = CommonUtils.isSplitVertically


@SuppressLint("ViewConstructor")
class SplitBibleArea(
): FrameLayout(mainBibleActivity) {

    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var bibleViewFactory: BibleViewFactory

    private val res = BibleApplication.application.resources

    private val windowSeparatorWidthPixels: Int = res.getDimensionPixelSize(R.dimen.window_separator_width)
    private val windowSeparatorTouchExpansionWidthPixels: Int = res.getDimensionPixelSize(R.dimen.window_separator_touch_expansion_width)

    private val bibleRefOverlayOffset: Int = res.getDimensionPixelSize(R.dimen.bible_ref_overlay_offset)

    private val bibleFrames: MutableList<BibleFrame> = ArrayList()
    private val hiddenAlpha get() = if(ScreenSettings.nightMode) HIDDEN_ALPHA_NIGHT else HIDDEN_ALPHA
    private val restoreButtonsList: MutableList<WindowButtonWidget> = ArrayList()
    private var lastSplitVertically: Boolean = isSplitVertically
    private val orientationChanges get() = lastSplitVertically != isSplitVertically
    private var bibleReferenceOverlay = TextView(context).apply {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            setBackgroundResource(R.drawable.bible_reference_overlay)
        }
        visibility = View.GONE
        ellipsize = TextUtils.TruncateAt.MIDDLE
        setLines(1)
        gravity = Gravity.CENTER
        translationY = -bibleRefOverlayOffset.toFloat()
        text = try {mainBibleActivity.bibleOverlayText} catch (e: MainBibleActivity.KeyIsNull) {""}
        textSize = 18F
    }
    private var buttonsVisible = true

    init {
        DaggerMainBibleActivityComponent.builder()
            .applicationComponent(BibleApplication.application.applicationComponent)
            .mainBibleActivityModule(MainBibleActivityModule(mainBibleActivity))
            .build()
            .inject(this)

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.split_bible_area, this, true)
        addView(bibleReferenceOverlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        ABEventBus.getDefault().register(this)
    }
    private val windowRepository = windowControl.windowRepository

    fun destroy() {
        ABEventBus.getDefault().unregister(this)
        bibleViewFactory.clear()
    }

    fun update() {
        biblesLinearLayout.orientation = if (isSplitVertically) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        removeSeparators()
        if(orientationChanges)
            removeAllFrames()
        updateWindows()
        addSeparators()
        updateRestoreButtons()

        resetTouchTimer()
        mainBibleActivity.resetSystemUi()
        lastSplitVertically = isSplitVertically
    }

    private fun removeSeparators() {
        for(v in biblesLinearLayout.children.filter { it is Separator }.map{it as Separator}) {
            v.frame1.removeView(v.touchDelegateView1)
            v.frame2.removeView(v.touchDelegateView2)
            biblesLinearLayout.removeView(v)
        }
    }

    private fun updateWindows() {
        val windows = windowRepository.visibleWindows
        fun getBf(i: Int) = try {bibleFrames[i] } catch (e: IndexOutOfBoundsException) {null}

        for((i, w) in windows.withIndex()) {
            var firstIs = false
            var bf: BibleFrame
            do {
                bf = getBf(i) ?: break
                if(bf.window.id == w.id) {
                    firstIs = true
                    bf.updateWindowButton()
                    break
                }
                if (bf.window.id != w.id) {
                    removeFrame(bf)
                }
            } while(true)
            if(!firstIs) {
                addBibleFrame(BibleFrame(w, this))
            }
        }
        while(bibleFrames.size > windows.size) {
            removeFrame(getBf(windows.size)!!)
        }
    }

    private fun removeAllFrames() {
        for(i in bibleFrames.toList()) {
            removeFrame(i)
        }
    }

    private fun removeFrame(frame: BibleFrame) {
        frame.destroy()
        biblesLinearLayout.removeView(frame)
        bibleFrames.remove(frame)
    }

    private fun addBibleFrame(frame: BibleFrame) {
        val windowWeight = max(frame.window.weight, 0.1F)
        val lp = if (isSplitVertically)
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, windowWeight)
        else
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, windowWeight)
        biblesLinearLayout.addView(frame, lp)
        bibleFrames.add(frame)
    }

    private fun addSeparators() {
        val size = bibleFrames.size
        for(i in 0 until size - 1) {
            addSeparator(bibleFrames[i], bibleFrames[i+1])
        }
    }

    private fun addSeparator(bf1: BibleFrame, bf2: BibleFrame) {
        val separator = Separator(
            context = context,
            separatorWidth = windowSeparatorWidthPixels,
            parentLayout = biblesLinearLayout,
            frame1 = bf1,
            frame2 = bf2,
            numWindows = bibleFrames.size,
            isPortrait = isSplitVertically,
            windowControl = windowControl
        )

        addBottomOrRightSeparatorTouchExtension(isSplitVertically, bf1, separator)
        addTopOrLeftSeparatorTouchExtension(isSplitVertically, bf2, separator)

        val lp = if (isSplitVertically)
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, windowSeparatorWidthPixels, 0f)
        else
            LinearLayout.LayoutParams(windowSeparatorWidthPixels, ViewGroup.LayoutParams.MATCH_PARENT, 0f)

        val currentPos = biblesLinearLayout.children.indexOf(bf1)
        biblesLinearLayout.addView(separator, currentPos + 1, lp)
    }

    @SuppressLint("RtlHardcoded")
    private fun addBottomOrRightSeparatorTouchExtension(isPortrait: Boolean,
                                                        frame: BibleFrame,
                                                        separator: Separator) {
        // add first touch delegate to framelayout which extends the touch area, otherwise it is difficult to select the separator to move it
        val layoutParams = if (isPortrait)
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, windowSeparatorTouchExpansionWidthPixels, Gravity.BOTTOM)
        else
            LayoutParams(windowSeparatorTouchExpansionWidthPixels, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT)
        frame.addView(separator.touchDelegateView1, layoutParams)
        // separator will adjust layouts when dragged
        separator.view1LayoutParams = frame.layoutParams as LinearLayout.LayoutParams
    }

    @SuppressLint("RtlHardcoded")
    private fun addTopOrLeftSeparatorTouchExtension(isPortrait: Boolean,
                                                    frame: BibleFrame,
                                                    separator: Separator) {
        // add separator handle touch delegate to framelayout
        val layoutParams = if (isPortrait)
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, windowSeparatorTouchExpansionWidthPixels, Gravity.TOP)
        else
            LayoutParams(windowSeparatorTouchExpansionWidthPixels, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT)
        frame.addView(separator.touchDelegateView2, layoutParams)

        // separator will adjust layouts when dragged
        separator.view2LayoutParams = frame.layoutParams as LinearLayout.LayoutParams
    }

    private fun updateRestoreButtons() {
        restoreButtonsList.clear()
        restoreButtons.removeAllViews()
        pinnedRestoreButtons.removeAllViews()


        restoreButtonsContainer.translationY = -mainBibleActivity.bottomOffset2
        restoreButtonsContainer.translationX = -mainBibleActivity.rightOffset1

        val maxWindow = windowRepository.maximizedWindow
        if(maxWindow != null) {
            val restoreButton = createUnmaximiseButton(maxWindow)
            restoreButtonsList.add(restoreButton)
            restoreButtons.addView(restoreButton,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }

        if(windowControl.isSingleWindow) return

        val windows = windowRepository.windows.filter {!it.isLinksWindow}

        val pinnedWindows = windows.filter { it.isPinMode }
        val nonPinnedWindows = windows.filter { !it.isPinMode }

        for (win in pinnedWindows) {
            Log.d(TAG, "Show restore button")
            val restoreButton = createRestoreButton(win)
            restoreButtonsList.add(restoreButton)
            pinnedRestoreButtons.addView(restoreButton,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        for (win in nonPinnedWindows) {
            Log.d(TAG, "Show restore button")
            val restoreButton = createRestoreButton(win)
            restoreButtonsList.add(restoreButton)
            restoreButtons.addView(restoreButton,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    fun onEvent(event: MainBibleActivity.FullScreenEvent) {
        toggleWindowButtonVisibility(true, force=true)
        resetTouchTimer()
    }

    fun onEvent(event: CurrentVerseChangedEvent) {
        updateBibleReference()
        if (event.window != null) updateMinimizedButtonLetter(event.window)
    }

    private fun updateBibleReference() {
        mainBibleActivity.runOnUiThread {
            try {
                bibleReferenceOverlay.text = mainBibleActivity.bibleOverlayText
            } catch(e: MainBibleActivity.KeyIsNull) {
                Log.e(TAG, "Key is null, can't update", e)
            }
        }
    }

    private fun updateMinimizedButtonLetter(w: Window) {
        restoreButtonsList.find { it.window?.id == w.id }?.text = getDocumentInitial(w)
    }

    fun onEvent(event: MainBibleActivity.ConfigurationChanged) {
        toggleWindowButtonVisibility(true, force=true)
        resetTouchTimer()
    }

    fun onEvent(event: MainBibleActivity.TransportBarVisibilityChanged) {
        toggleWindowButtonVisibility(true, force=true)
        resetTouchTimer()
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        toggleWindowButtonVisibility(true, force=true)
        resetTouchTimer()
        updateBibleReference()
    }

    private var sleepTimer: Timer = Timer("TTS sleep timer")
    private var timerTask: TimerTask? = null

    fun onEvent(event: BibleView.BibleViewTouched) {
        resetTouchTimer()
    }

    private fun resetTouchTimer() {
        toggleWindowButtonVisibility(true)
        timerTask?.cancel()

        timerTask = object : TimerTask() {
            override fun run() {
                toggleWindowButtonVisibility(false)
            }
        }
        sleepTimer.schedule(timerTask, 2000L)
    }

    private val windowButtons get() = bibleFrames.map { it.windowButton }

    private fun toggleWindowButtonVisibility(show: Boolean, force: Boolean = false) {
        if(buttonsVisible == show && !force) {
            return
        }
        mainBibleActivity.runOnUiThread {
            for ((idx, b) in windowButtons.withIndex()) {
                if(b == null) continue
                b.animate().apply {
                    // When switching to/from fullscreen, take into account the toolbar offset.
                    translationY(
                        if (windowControl.isSingleWindow) -mainBibleActivity.bottomOffset2
                        else (
                            if(CommonUtils.isSplitVertically) {
                                if(idx == 0)
                                    mainBibleActivity.topOffset2
                                else 0.0F
                            }
                            else mainBibleActivity.topOffset2
                            )
                    )

                    if(show) {
                        alpha(VISIBLE_ALPHA)
                        interpolator = DecelerateInterpolator()
                    }  else {
                        alpha(hiddenAlpha)
                        interpolator = AccelerateInterpolator()
                    }
                    start()
                }
            }

            updateRestoreButtons(show)
            updateBibleReferenceOverlay(show)
        }
        buttonsVisible = show
    }

    private fun updateRestoreButtons(show: Boolean) {
        if(show) {
            restoreButtonsContainer.visibility = View.VISIBLE
            restoreButtonsContainer.animate()
                .alpha(VISIBLE_ALPHA)
                .translationY(-mainBibleActivity.bottomOffset2)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }  else {
            if(mainBibleActivity.fullScreen) {
                restoreButtonsContainer.animate().alpha(hiddenAlpha)
                    .setInterpolator(AccelerateInterpolator())
                    .start()
            }
        }
    }

    private fun updateBibleReferenceOverlay(_show: Boolean) {
        val show = mainBibleActivity.fullScreen && _show
        if(show) {
            bibleReferenceOverlay.visibility = View.VISIBLE
            bibleReferenceOverlay.animate().alpha(1.0f)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }  else {
            bibleReferenceOverlay.animate().alpha(0f)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { bibleReferenceOverlay.visibility = View.GONE }
                .start()
        }
    }

    private fun createRestoreButton(window: Window): WindowButtonWidget {
        return WindowButtonWidget(window, windowControl,true, mainBibleActivity).apply {
            text = getDocumentInitial(window)
            setOnClickListener { windowControl.restoreWindow(window) }
            setOnLongClickListener { v-> showPopupWindow(window, v); true }
        }
    }

    private fun createUnmaximiseButton(window: Window): WindowButtonWidget {
        return WindowButtonWidget(
            window,
            windowControl = windowControl,
            isRestoreButton = true,
            context = mainBibleActivity
        ).apply {
            setOnClickListener { windowControl.unMaximise() }
            text = ""
            setOnLongClickListener { v-> showPopupWindow(window, v); true }
        }
    }

    /**
     * Get the first initial of the doc in the window to show in the minimise restore button
     */
    private fun getDocumentInitial(window: Window): String {
        return try {
            val abbrv = window.pageManager.currentPage.currentDocument?.abbreviation
            return abbrv ?: ""
            //abbrv?.substring(0, 1) ?: ""
        } catch (e: Exception) {
            " "
        }

    }

    private fun handlePrefItem(window: Window, item: MenuItem) {
        val itemOptions = getItemOptions(window, item)
        if(itemOptions is SubMenuPreference)
            return

        if(itemOptions.isBoolean) {
            itemOptions.value = !(itemOptions.value == true)
            itemOptions.handle()
            item.isChecked = itemOptions.value == true
        } else {
            val onReady = {
                if(itemOptions.requiresReload) {
                    window.updateText()
                } else {
                    window.bibleView?.updateTextDisplaySettings()
                }
                Unit
            }
            itemOptions.openDialog(mainBibleActivity, {onReady()}, onReady)
        }
    }

    @SuppressLint("RestrictedApi")
    internal fun showPopupWindow(window: Window, view: View) {
        // ensure actions affect the right window
        timerTask?.cancel()
        toggleWindowButtonVisibility(true)
        if(window.isVisible) {
            windowControl.activeWindow = window
        }

        val popup = PopupMenu(mainBibleActivity, view)
        popup.setOnMenuItemClickListener { menuItem ->
            resetTouchTimer()
            handlePrefItem(window, menuItem)
            true
        }

        val inflater = popup.menuInflater
        val menu = popup.menu
        inflater.inflate(R.menu.window_popup_menu, menu)

        val moveWindowsSubMenu = menu.findItem(R.id.moveWindowSubMenu).subMenu

        moveWindowsSubMenu.removeItem(R.id.moveItem)

        var count = 0

        val oldValue = BookName.isFullBookName()

        BookName.setFullBookName(false)
        val windowList = windowRepository.windowList.filter {it.isPinMode == window.isPinMode}
        val thisIdx = windowList.indexOf(window)
        windowList.forEach {
            if(it.id != window.id) {
                val p = it.pageManager.currentPage
                val title = BibleApplication.application.getString(R.string.move_window_to_position, "${count + 1} (${p.currentDocument?.abbreviation}: ${p.key?.name})")
                val item = moveWindowsSubMenu.add(Menu.NONE, R.id.moveItem, count, title)
                item.setIcon(if (thisIdx > count) R.drawable.ic_arrow_drop_up_grey_24dp else R.drawable.ic_arrow_drop_down_grey_24dp)
            }
            count ++;
        }
        BookName.setFullBookName(oldValue)

        val textOptionsSubMenu = menu.findItem(R.id.textOptionsSubMenu).subMenu
        textOptionsSubMenu.removeItem(R.id.textOptionItem)

        val lastSettings = CommonUtils.lastDisplaySettings
        if(lastSettings.isNotEmpty()) {
            for ((idx, t) in lastSettings.withIndex()) {
                textOptionsSubMenu.add(Menu.NONE, R.id.textOptionItem, idx, t.name)
            }
        } else {
            menu.removeItem(R.id.textOptionsSubMenu)
            val item = menu.add(Menu.NONE, R.id.allTextOptions, 1000, R.string.all_text_options_window_menutitle_alone)
            item.setIcon(R.drawable.ic_text_format_white_24dp)
        }


        fun handleMenu(menu: Menu) {
            for(item in menu.children) {
                val itmOptions = getItemOptions(window, item)
                if(itmOptions.title != null) {
                    item.title = itmOptions.title
                }
                item.isVisible = itmOptions.visible
                item.isEnabled = itmOptions.enabled
                item.isCheckable = itmOptions.isBoolean
                if(itmOptions is Preference) {
                    if (itmOptions.inherited) {
                        item.setIcon(R.drawable.ic_sync_white_24dp)
                    } else {
                        item.setIcon(R.drawable.ic_sync_disabled_green_24dp)
                    }
                }

                if(item.hasSubMenu()) {
                    handleMenu(item.subMenu)
                    continue;
                }

                item.isChecked = itmOptions.value == true
            }
        }
        handleMenu(menu)

        val menuHelper = MenuPopupHelper(mainBibleActivity, menu as MenuBuilder, view)
        menuHelper.setOnDismissListener {
            resetTouchTimer()
            mainBibleActivity.resetSystemUi()
        }

        menuHelper.setForceShowIcon(true)
        menuHelper.show()
    }

    private fun getItemOptions(window: Window, item: MenuItem): OptionsMenuItemInterface {
        val settingsBundle = SettingsBundle(
            windowId = window.id,
            pageManagerSettings = window.pageManager.textDisplaySettings,
            workspaceId = windowControl.windowRepository.id,
            workspaceName = windowControl.windowRepository.name,
            workspaceSettings = windowControl.windowRepository.textDisplaySettings
        )

        val isMaximised = windowRepository.isMaximized

        return when(item.itemId) {

            R.id.windowNew -> CommandPreference(
                launch = {_, _, _ -> windowControl.addNewWindow()},
                visible = !window.isLinksWindow && !window.isMinimised && !isMaximised
            )
            R.id.windowSynchronise -> CommandPreference(
                handle = {windowControl.setSynchronised(window, !window.isSynchronised)},
                value = window.isSynchronised,
                visible = !window.isLinksWindow
            )
            R.id.pinMode -> CommandPreference(
                handle = {windowControl.setPinMode(window, !window.isPinMode)},
                value = window.isPinMode,
                visible = !window.isLinksWindow && !isMaximised && !windowRepository.windowBehaviorSettings.autoPin
            )
            R.id.moveWindowSubMenu -> SubMenuPreference(false,
                visible = !window.isLinksWindow && !isMaximised
            )
            R.id.textOptionsSubMenu -> SubMenuPreference(
                onlyBibles = false,
                visible = window.isVisible
            )
            R.id.windowClose -> CommandPreference(
                launch = { _, _, _ ->  windowControl.closeWindow(window)},
                visible = windowControl.isWindowRemovable(window) && !isMaximised
            )
            R.id.windowMinimise -> CommandPreference(
                launch = {_, _, _ -> windowControl.minimiseWindow(window)},
                visible = windowControl.isWindowMinimisable(window) && !isMaximised
            )
            R.id.windowMaximise -> CommandPreference(
                launch = {_, _, _ -> windowControl.maximiseWindow(window)},
                visible = !isMaximised && !window.isLinksWindow
            )
            R.id.allTextOptions -> CommandPreference(
                launch = {_, _, _ ->
                    val intent = Intent(mainBibleActivity, TextDisplaySettingsActivity::class.java)
                    intent.putExtra("settingsBundle", settingsBundle.toJson())
                    mainBibleActivity.startActivityForResult(intent, MainBibleActivity.TEXT_DISPLAY_SETTINGS_CHANGED)
                },
                visible = window.isVisible
            )
            R.id.moveItem -> CommandPreference({_, _, _ ->
                windowControl.moveWindow(window, item.order)
                Log.d(TAG, "Number ${item.order}")
            },
                visible = !window.isLinksWindow
            )
            R.id.textOptionItem -> getPrefItem(settingsBundle, CommonUtils.lastDisplaySettings[item.order])

            else -> throw RuntimeException("Illegal menu item")
        }
    }

    companion object {
        private const val TAG = "SplitBibleArea"
        private const val HIDDEN_ALPHA = 0.2F
        private const val HIDDEN_ALPHA_NIGHT = 0.5F
        private const val VISIBLE_ALPHA = 1.0F
    }

}
