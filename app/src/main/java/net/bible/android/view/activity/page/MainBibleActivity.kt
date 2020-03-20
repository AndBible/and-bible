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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.ContextMenu
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import kotlinx.android.synthetic.main.main_bible_view.*
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.BibleContentManager
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.passage.PassageChangedEvent
import net.bible.android.control.event.passage.PreBeforeCurrentPageChangeEvent
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.DaggerMainBibleActivityComponent
import net.bible.android.view.activity.MainBibleActivityModule
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.mynote.MyNotes
import net.bible.android.view.activity.navigation.ChooseDictionaryWord
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.page.actionbar.BibleActionBarManager
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator
import net.bible.android.view.activity.page.screen.DocumentViewManager
import net.bible.android.view.activity.settings.DirtyTypesSerializer
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.speak.GeneralSpeakActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorActivity
import net.bible.android.view.util.UiUtils
import net.bible.service.common.CommonUtils
import net.bible.service.common.TitleSplitter
import net.bible.service.db.DatabaseContainer
import net.bible.service.device.ScreenSettings
import net.bible.service.device.speak.event.SpeakEvent
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.BookName
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** The main activity screen showing Bible text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class MainBibleActivity : CustomTitlebarActivityBase(), VerseActionModeMediator.ActionModeMenuDisplay {
    private var mWholeAppWasInBackground = false

    // We need to have this here in order to initialize BibleContentManager early enough.
    @Inject
    lateinit var bibleContentManager: BibleContentManager

    @Inject
    lateinit var documentViewManager: DocumentViewManager

    @Inject lateinit var bibleActionBarManager: BibleActionBarManager

    @Inject
    lateinit var windowControl: WindowControl
    @Inject
    lateinit var speakControl: SpeakControl

    // handle requests from main menu
    @Inject
    lateinit var mainMenuCommandHandler: MenuCommandHandler
    @Inject
    lateinit var bibleKeyHandler: BibleKeyHandler
    @Inject
    lateinit var backupControl: BackupControl
    @Inject
    lateinit var searchControl: SearchControl
    @Inject
    lateinit var documentControl: DocumentControl
    @Inject
    lateinit var navigationControl: NavigationControl

    override var nightTheme = R.style.MainBibleViewNightTheme
    override var dayTheme = R.style.MainBibleViewTheme

    private var statusBarHeight = 0.0F
    private var navigationBarHeight = 0.0F
    private var actionBarHeight = 0.0F
    private var transportBarHeight = 0.0F

    private var hasHwKeys: Boolean = false
    private val bottomNavBarVisible get() = CommonUtils.isPortrait && !hasHwKeys
    private val rightNavBarVisible get() = false
    private val leftNavBarVisible get() = false
    private var transportBarVisible = false

    val dao get() = DatabaseContainer.db.workspaceDao()

    val isMyNotes
        get() =
            if (::documentControl.isInitialized) {
                documentControl.isMyNotes
            } else false

    val multiWinMode
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInMultiWindowMode else false

    // Top offset with only statusbar
    val topOffset1 get() = if (!isFullScreen) statusBarHeight else 0.0F
    // Top offset with only statusbar and toolbar
    val topOffset2 get() = topOffset1 + if (!isFullScreen) actionBarHeight else 0.0F
    // Top offset with only statusbar and toolbar taken into account always
    val topOffsetWithActionBar get() = topOffset1 + actionBarHeight
    // Top offset with only statusbar and toolbar taken into account always
    val topOffsetWithActionBarAndStatusBar get() = statusBarHeight + actionBarHeight

    // Bottom offset with only navigation bar
    val bottomOffset1
        get() =
            if (CommonUtils.isPortrait && bottomNavBarVisible && !isFullScreen && !multiWinMode) navigationBarHeight - 2 else 0.0F

    // Bottom offset with navigation bar and transport bar
    val bottomOffset2 get() = bottomOffset1 + if (transportBarVisible) transportBarHeight else 0.0F
    //val bottomOffset2 get() = 200F //bottomOffset1 + if (transportBarVisible) transportBarHeight else 0.0F
    // Right offset with navigation bar
    val rightOffset1 get() = if (rightNavBarVisible) navigationBarHeight else 0.0F
    // Left offset with navigation bar
    val leftOffset1 get() = if (leftNavBarVisible) navigationBarHeight else 0.0F

    /**
     * return percentage scrolled down page
     */
    private val currentPosition: Float
        get() = documentViewManager.documentView.currentPosition

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "Creating MainBibleActivity")

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }

        // This is singleton so we can do this.
        mainBibleActivity = this
        ScreenSettings.refreshNightMode()
        currentNightMode = ScreenSettings.nightMode
        super.onCreate(savedInstanceState, true)

        setContentView(R.layout.main_bible_view)
        DaggerMainBibleActivityComponent.builder()
            .applicationComponent(BibleApplication.application.applicationComponent)
            .mainBibleActivityModule(MainBibleActivityModule(this))
            .build()
            .inject(this)
        // When I mess up database, I can re-create database like this.
        //backupControl.resetDatabase()

        backupControl.clearBackupDir()
        windowRepository.initialize()
        hasHwKeys = ViewConfiguration.get(this).hasPermanentMenuKey()

        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (statusBarId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(statusBarId).toFloat()
        }

        val navBarId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (navBarId > 0) {
            navigationBarHeight = resources.getDimensionPixelSize(navBarId).toFloat()
        }

        setSupportActionBar(toolbar)
        showSystemUI()

        updateToolbar()

        val tv = TypedValue()
        if (theme.resolveAttribute(R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics).toFloat()
        }

        if (theme.resolveAttribute(R.attr.transportBarHeight, tv, true)) {
            transportBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics).toFloat()
        }

        toolbar.setContentInsetsAbsolute(0, 0)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()
            mainMenuCommandHandler.handleMenuRequest(menuItem)
        }
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {
                if(newState == DrawerLayout.STATE_SETTLING) {
                    showSystemUI(false)
                }

            }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            override fun onDrawerOpened(drawerView: View) {}

            override fun onDrawerClosed(drawerView: View) {
                if (isFullScreen) {
                    hideSystemUI()
                } else {
                    showSystemUI()

                }
            }

        })

        // create related objects
        documentViewManager.buildView()
        // register for passage change and appToBackground events
        ABEventBus.getDefault().register(this)

        // force all windows to be populated
        windowControl.windowSync.reloadAllWindows(true)
        updateActions()
        refreshScreenKeepOn()
        if(!initialized)
            requestSdcardPermission()
        setupToolbarButtons()

        speakTransport.visibility = View.GONE
        updateSpeakTransportVisibility()
        setupToolbarFlingDetection()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if(!initialized)
            showBetaNotice()
        initialized = true
    }

    private fun showBetaNotice() {
        val verFull = CommonUtils.applicationVersionName
        val ver = verFull.split("#")[0]

        if(!ver.endsWith("-beta")) return

        val displayedVer = preferences.getString("beta-notice-displayed", "")

        if(displayedVer != ver) {

            val par1 = getString(R.string.beta_notice_content_1)
            val par2 = getString(R.string.beta_notice_content_2,
                 " <a href=\"https://github.com/AndBible/and-bible/issues\">"
                    + "${getString(R.string.beta_notice_github_issues)}</a>"
            )
            val par3 = getString(R.string.beta_notice_content_3,
                " <a href=\"https://github.com/AndBible/and-bible\">"
                    + "${getString(R.string.beta_notice_github)}</a>"

            )
            val htmlMessage = "$par1<br><br> $par2<br><br> $par3 <br><br> <i>Version: $verFull</i>"

            val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
            } else {
                Html.fromHtml(htmlMessage)
            }

            val d = AlertDialog.Builder(this)
                .setTitle(getString(R.string.beta_notice_title))
                .setMessage(spanned)
                .setNeutralButton(getString(R.string.beta_notice_dismiss), null)
                .setPositiveButton(getString(R.string.beta_notice_dismiss_until_update)) { _, _ ->
                    preferences.edit().putString("beta-notice-displayed", ver).apply()
                }
                .create()
            d.show()
            d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun setupToolbarFlingDetection() {
        val scaledMinimumDistance = CommonUtils.convertDipsToPx(40)
        var minScaledVelocity = ViewConfiguration.get(mainBibleActivity).scaledMinimumFlingVelocity
        minScaledVelocity = (minScaledVelocity * 0.66).toInt()

        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                Log.d(TAG, "onFling")
                val vertical = Math.abs(e1.y - e2.y).toDouble()
                val horizontal = Math.abs(e1.x - e2.x).toDouble()

                if (vertical > scaledMinimumDistance && Math.abs(velocityY) > minScaledVelocity) {
                    val intent = Intent(this@MainBibleActivity, WorkspaceSelectorActivity::class.java)
                    startActivityForResult(intent, WORKSPACE_CHANGED)
                    return true

                } else if (horizontal > scaledMinimumDistance && Math.abs(velocityX) > minScaledVelocity) {
                    if (e1.x > e2.x) {
                        nextWorkspace()
                    } else {
                        previousWorkspace()
                    }
                    return true
                }

                return super.onFling(e1, e2, velocityX, velocityY)
            }

            override fun onLongPress(e: MotionEvent?) {
                startActivityForResult(Intent(this@MainBibleActivity, ChooseDocument::class.java), IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
            }

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                val intent = Intent(this@MainBibleActivity, pageControl.currentPageManager.currentPage.keyChooserActivity)
                startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE)
                return true
            }

        }
        val gestureDetector = GestureDetectorCompat(this, gestureListener)
        pageTitleContainer.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onPause() {
        fullScreen = false;
        super.onPause()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
            drawerLayout.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (drawerLayout.isDrawerVisible(GravityCompat.START) && keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }

        //TODO make Long press Back work for screens other than main window e.g. does not work from search screen because wrong window is displayed
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "Back Long")
            // a long press of the back key. do our work, returning true to consume it.  by returning true, the framework knows an action has
            // been performed on the long press, so will set the cancelled flag for the following up event.
            val intent = Intent(this, History::class.java)
            startActivityForResult(intent, 1)
            return true
        }

        return super.onKeyLongPress(keyCode, event)
    }

    private fun setupToolbarButtons() {
        homeButton.setOnClickListener {
            if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
                drawerLayout.closeDrawers()
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        strongsButton.setOnClickListener {
            val prefOptions = dummyStrongsPrefOption
            prefOptions.value = !(prefOptions.value == true)
            prefOptions.handle()
            invalidateOptionsMenu()
            updateStrongsButton()
        }

        strongsButton.setOnLongClickListener {
            startActivityForResult(Intent(this, ChooseDictionaryWord::class.java), ActivityBase.STD_REQUEST_CODE)
            true
        }

        speakButton.setOnClickListener { speakControl.toggleSpeak() }
        speakButton.setOnLongClickListener {
            val isBible = windowControl.activeWindowPageManager.currentPage.bookCategory == BookCategory.BIBLE
            val intent = Intent(this, if (isBible) BibleSpeakActivity::class.java else GeneralSpeakActivity::class.java)
            startActivity(intent)
            true
        }
        searchButton.setOnClickListener { startActivityForResult(searchControl.getSearchIntent(documentControl.currentDocument), ActivityBase.STD_REQUEST_CODE) }
        bookmarkButton.setOnClickListener { startActivityForResult(Intent(this, Bookmarks::class.java), STD_REQUEST_CODE) }
    }

    private val dummyStrongsPrefOption
        get() = StrongsPreference(
            SettingsBundle(
                pageManagerSettings = windowControl.activeWindow.pageManager.textDisplaySettings,
                workspaceId = windowRepository.id,
                workspaceName = windowRepository.name,
                workspaceSettings = windowRepository.textDisplaySettings,
                windowId = windowControl.activeWindow.id
            ))


    val workspaces get() = dao.allWorkspaces()
    val windowRepository get() = windowControl.windowRepository

    private fun previousWorkspace() {
        val workspaces = workspaces
        if(workspaces.size < 2) return
        windowRepository.saveIntoDb()
        val currentWorkspacePos = workspaces.indexOf(workspaces.find {it.id == currentWorkspaceId})

        currentWorkspaceId = if(currentWorkspacePos > 0) workspaces[currentWorkspacePos - 1].id else workspaces[workspaces.size -1].id
    }

    private fun nextWorkspace() {
        val workspaces = workspaces
        if(workspaces.size < 2) return
        windowRepository.saveIntoDb()

        val currentWorkspacePos = workspaces.indexOf(workspaces.find {it.id == currentWorkspaceId})

        currentWorkspaceId = if(currentWorkspacePos < workspaces.size - 1) workspaces[currentWorkspacePos + 1].id else workspaces[0].id
    }

    private var currentWorkspaceId
        get() = windowRepository.id
        set(value) {
            windowRepository.loadFromDb(value)

            preferences.edit().putLong("current_workspace_id", windowRepository.id).apply()
            documentViewManager.buildView()
            windowControl.windowSync.reloadAllWindows()
            windowRepository.updateVisibleWindowsTextDisplaySettings()

            ABEventBus.getDefault().post(ToastEvent(windowRepository.name))

            invalidateOptionsMenu()
            updateTitle()
            updateToolbar()
        }

    private fun getItemOptions(item: MenuItem): OptionsMenuItemInterface {
        val settingsBundle = SettingsBundle(workspaceId = windowRepository.id, workspaceName = windowRepository.name, workspaceSettings = windowRepository.textDisplaySettings)
        return when(item.itemId) {
            R.id.allTextOptions -> CommandPreference(launch = { _, _, _ ->
                val intent = Intent(this, TextDisplaySettingsActivity::class.java)
                intent.putExtra("settingsBundle", settingsBundle.toJson())
                startActivityForResult(intent, TEXT_DISPLAY_SETTINGS_CHANGED)
            })
            R.id.textOptionsSubMenu -> SubMenuPreference(false)
            R.id.textOptionItem -> getPrefItem(settingsBundle, CommonUtils.lastDisplaySettings[item.order])
            R.id.splitMode -> SplitModePreference()
            R.id.autoPinMode -> WindowPinningPreference()

            R.id.tiltToScroll -> TiltToScrollPreference()
            R.id.nightMode -> NightModePreference()
            R.id.switchToWorkspace -> CommandPreference(launch = { _, _, _ ->
                val intent = Intent(this, WorkspaceSelectorActivity::class.java)
                startActivityForResult(intent, WORKSPACE_CHANGED)
            })
            else -> throw RuntimeException("Illegal menu item")
        }
    }

    private val preferences = CommonUtils.sharedPreferences

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_bible_options_menu, menu)

        val lastSettings = CommonUtils.lastDisplaySettings
        if(lastSettings.isNotEmpty()) {
            for ((idx, t) in lastSettings.withIndex()) {
                menu.add(R.id.textOptionsGroup, R.id.textOptionItem, idx, t.name)
            }
        }
        MenuCompat.setGroupDividerEnabled(menu, true)

        fun handleMenu(menu: Menu) {
            for(item in menu.children) {
                val itmOptions = getItemOptions(item)
                item.isVisible = itmOptions.visible
                item.isEnabled = itmOptions.enabled
                item.isCheckable = itmOptions.isBoolean
                if(itmOptions.title != null) {
                    item.title = itmOptions.title
                }

                if(item.hasSubMenu()) {
                    handleMenu(item.subMenu)
                    continue;
                }

                item.isChecked = itmOptions.value == true
            }
        }
        handleMenu(menu)
        return true
    }

    private fun handlePrefItem(item: MenuItem) {
        val itemOptions = getItemOptions(item)
        if(itemOptions is SubMenuPreference)
            return
        if(itemOptions.isBoolean) {
            itemOptions.value = !(itemOptions.value == true)
            itemOptions.handle()
            item.isChecked = itemOptions.value == true
            invalidateOptionsMenu()
        } else {
            val onReady = {
                if(itemOptions is Preference) {
                    windowRepository.updateWindowTextDisplaySettingsValues(setOf(itemOptions.type), windowRepository.textDisplaySettings)
                }
                if(itemOptions.requiresReload) {
                    ABEventBus.getDefault().post(SynchronizeWindowsEvent(true))
                } else {
                    windowRepository.updateVisibleWindowsTextDisplaySettings()
                }
                invalidateOptionsMenu()
            }
            val onReset = {
                if(itemOptions is Preference) {
                    itemOptions.value = TextDisplaySettings.default.getValue(itemOptions.type)!!
                }
                onReady()
            }
            itemOptions.openDialog(this, {onReady()}, onReset)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        handlePrefItem(item)
        return true
    }

    private val documentTitleText: String
        get() = pageControl.currentPageManager.currentPage.currentDocument?.name?:""

    class KeyIsNull: Exception()

    private val pageTitleText: String
        get() {
            val doc = pageControl.currentPageManager.currentPage.currentDocument
            var key = pageControl.currentPageManager.currentPage.key
            if(doc?.bookCategory == BookCategory.BIBLE) {
                key = pageControl.currentBibleVerse
                if(key.verse == 0) {
                    key = Verse(key.versification, key.book, key.chapter, 1)
                }
            }
            return key?.name ?: throw KeyIsNull()
        }

    val bibleOverlayText: String
        get() {
            val bookName = pageControl.currentPageManager.currentPage.currentDocument?.abbreviation
            BookName.setFullBookName(false)
            val text = pageTitleText
            BookName.setFullBookName(true)
            return "$bookName:$text"
        }

    private fun updateTitle() {
        try {
            pageTitle.text = pageTitleText
            val layout = pageTitle.layout
            if(layout!= null && layout.lineCount > 0 && layout.getEllipsisCount(0) > 0) {
                val oldValue = BookName.isFullBookName()
                BookName.setFullBookName(false)
                pageTitle.text = pageTitleText
                BookName.setFullBookName(oldValue)
            }
        } catch (e: KeyIsNull) {
            Log.e(TAG, "Key is null, not updating", e)
        }
        documentTitle.text = documentTitleText
        updateStrongsButton()
    }

    private fun updateStrongsButton() {
        if(documentControl.isNewTestament) {
            strongsButton.setImageResource(R.drawable.ic_strongs_greek)
        } else {
            strongsButton.setImageResource(R.drawable.ic_strongs_hebrew)
        }
        if(dummyStrongsPrefOption.value == false) {
            strongsButton.alpha = 0.7F
        } else
            strongsButton.alpha = 1.0F
    }

    override fun updateActions() {
        updateTitle()

        val suggestedBible = documentControl.suggestedBible
        val suggestedCommentary = documentControl.suggestedCommentary

        var visibleButtonCount = 0
        val screenWidth = resources.displayMetrics.widthPixels
        val approximateSize = 53 * resources.displayMetrics.density
        val maxWidth = (screenWidth * 0.5).roundToInt()
        val maxButtons: Int = (maxWidth / approximateSize).toInt()
        val showSearch = documentControl.isBibleBook || documentControl.isCommentary

        bibleButton.visibility = if (visibleButtonCount < maxButtons && suggestedBible != null) {
            bibleButton.setOnClickListener { menuForDocs(it, documentControl.biblesForVerse) }
            visibleButtonCount += 1
            View.VISIBLE
        } else View.GONE

        commentaryButton.visibility = if (suggestedCommentary != null && visibleButtonCount < maxButtons) {
            commentaryButton.setOnClickListener { menuForDocs(it, documentControl.commentariesForVerse) }
            visibleButtonCount += 1
            View.VISIBLE
        } else View.GONE

        strongsButton.visibility = if (visibleButtonCount < maxButtons && documentControl.isStrongsInBook) {
            visibleButtonCount += 1

            View.VISIBLE
        } else View.GONE


        fun addSearch() {
            searchButton.visibility = if (visibleButtonCount < maxButtons && showSearch && !isMyNotes)
            {
                visibleButtonCount += 1
                View.VISIBLE
            } else View.GONE
        }
        fun addSpeak() {
            speakButton.visibility = if (visibleButtonCount < maxButtons && speakControl.isStopped && !isMyNotes)
            {
                visibleButtonCount += 1
                View.VISIBLE
            } else View.GONE
        }

        fun addBookmarks() {
            bookmarkButton.visibility = if (visibleButtonCount < maxButtons && !isMyNotes) {
                visibleButtonCount += 1
                View.VISIBLE
            } else View.GONE
        }

        val speakLastUsed = preferences.getLong("speak-last-used", 0)
        val searchLastUsed = preferences.getLong("search-last-used", 0)
        val bookmarksLastUsed = preferences.getLong("bookmarks-last-used", 0)

        val funs = arrayListOf(Pair(speakLastUsed, {addSpeak()}),
            Pair(searchLastUsed, {addSearch()}),
            Pair(bookmarksLastUsed, {addBookmarks()}))
        funs.sortBy { -it.first }

        for(p in funs) {
            p.second()
        }

        invalidateOptionsMenu()

        val btn = navigationView.menu.findItem(R.id.searchButton)
        btn.isEnabled = showSearch
    }

    fun onEventMainThread(passageEvent: CurrentVerseChangedEvent) {
        updateTitle()
    }

    fun onEventMainThread(speakEvent: SpeakEvent) {
        if(!speakEvent.isTemporarilyStopped) {
            updateSpeakTransportVisibility()
        }
        updateActions()
    }

    private fun menuForDocs(v: View, documents: List<Book>): Boolean {
        val menu = PopupMenu(this, v)
        val docs = documents.sortedWith(compareBy({it.language.code}, {it.abbreviation}))
        docs.forEachIndexed { i, book ->
            if(windowControl.activeWindow.pageManager.currentPage.currentDocument != book) {
                menu.menu.add(Menu.NONE, i, Menu.NONE, "${book.abbreviation} (${book.language.code})")
            }
        }

        menu.setOnMenuItemClickListener { item ->
            setCurrentDocument(docs[item.itemId])
            true
        }
        menu.show()
        return true
    }

    private fun setCurrentDocument(book: Book?) {
        windowControl.activeWindow.pageManager.setCurrentDocument(book)
        if(book != null) {
            val bookCategory = book.bookCategory
            // see net.bible.android.control.page.CurrentPageBase.getDefaultBook
            CommonUtils.sharedPreferences.edit().putString("default-${bookCategory.name}", book.initials).apply()
        }
    }

    class FullScreenEvent(val isFullScreen: Boolean)
    private var isFullScreen = false

    var fullScreen
        get() = isFullScreen
        set(value) {
            if(value != isFullScreen) {
                toggleFullScreen()
            }
        }

    private fun toggleFullScreen() {
        sharedActivityState.toggleFullScreen()
        isFullScreen = sharedActivityState.isFullScreen
        ABEventBus.getDefault().post(FullScreenEvent(isFullScreen))

        if (!isFullScreen) {
            showSystemUI()
            Log.d(TAG, "Fullscreen off")
            toolbar.translationY = -toolbar.height.toFloat()
            supportActionBar?.show()
            toolbar.animate().translationY(topOffset1)
                .setInterpolator(DecelerateInterpolator())
                .start()
            updateActions()
        } else {
            Log.d(TAG, "Fullscreen on")
            hideSystemUI()
            toolbar.animate().translationY(-toolbar.height.toFloat())
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { supportActionBar?.hide() }
                .start()
        }
        updateSpeakTransportVisibility()
    }

    fun resetSystemUi() {
        if(isFullScreen)
            hideSystemUI()
        else
            showSystemUI()

        updateToolbar()
    }

    private val sharedActivityState = SharedActivityState.instance

    private fun hideSystemUI() {
        var uiFlags = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN)

        // only hide navigation bar in portrait mode
        if (CommonUtils.isPortrait)
            uiFlags = (uiFlags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

        window.decorView.systemUiVisibility = uiFlags
    }

    private fun showSystemUI(setNavBarColor: Boolean=true) {
        var uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ScreenSettings.nightMode) {
                uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

        // only need to un-hide navigation bar in portrait mode
        if (CommonUtils.isPortrait)
            uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(setNavBarColor) {
                val colors = windowRepository.lastVisibleWindow.pageManager.actualTextDisplaySettings.colors!!
                val color = if(ScreenSettings.nightMode) colors.nightBackground else colors.dayBackground
                window.navigationBarColor = color?: UiUtils.bibleViewDefaultBackgroundColor
                UiUtils.bibleViewDefaultBackgroundColor
            } else {
                val typedValue = TypedValue()
                val found = theme.resolveAttribute(android.R.attr.navigationBarColor, typedValue, true)
                window.navigationBarColor = typedValue.data
            }
        }
        window.decorView.systemUiVisibility = uiFlags
    }

    private fun updateSpeakTransportVisibility() {
        if(speakTransport.visibility == View.VISIBLE && (isFullScreen || speakControl.isStopped)) {
            transportBarVisible = false
            speakTransport.animate().translationY(speakTransport.height.toFloat())
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { speakTransport.visibility = View.GONE }
                .start()
            ABEventBus.getDefault().post(TransportBarVisibilityChanged(false))
        } else if (speakTransport.visibility == View.GONE && !speakControl.isStopped){
            transportBarVisible = true
            speakTransport.translationY = speakTransport.height.toFloat()
            speakTransport.visibility = View.VISIBLE
            speakTransport.animate().translationY(-bottomOffset1)
                .setInterpolator(DecelerateInterpolator())
                .start()
            ABEventBus.getDefault().post(TransportBarVisibilityChanged(true))
        }
    }

    class TransportBarVisibilityChanged(val visible: Boolean)

    private fun refreshScreenKeepOn() {
        val keepOn = preferences.getBoolean(SCREEN_KEEP_ON_PREF, false)
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        beforeDestroy()
        ABEventBus.getDefault().unregister(this)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (menuInfo != null) {
            val inflater = menuInflater
            inflater.inflate(R.menu.link_context_menu, menu)
            val openLinksInSpecialWindowByDefault = preferences.getBoolean("open_links_in_special_window_pref", true)
            val item =
                if(openLinksInSpecialWindowByDefault)
                    menu.findItem(R.id.open_link_in_special_window)
                else
                    menu.findItem(R.id.open_link_in_this_window)
            item.isVisible = false
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as BibleView.BibleViewContextMenuInfo?
        if (info != null) {
            info.activate(item.itemId)
            return true
        }
        return false
    }

    /**
     * called if the app is re-entered after returning from another app.
     * Trigger redisplay in case mobile has gone from light to dark or vice-versa
     */
    override fun onRestart() {
        super.onRestart()
        refreshScreenKeepOn()
        if (mWholeAppWasInBackground) {
            mWholeAppWasInBackground = false
            refreshIfNightModeChange()
        }
    }

    /**
     * Need to know when app is returned to foreground to check the screen colours
     */
    fun onEvent(event: AppToBackgroundEvent) {
        if (event.isMovedToBackground) {
            mWholeAppWasInBackground = true
        }
        else {
            updateActions()
        }
    }

    override fun onScreenTurnedOff() {
        super.onScreenTurnedOff()
        documentViewManager.documentView.onScreenTurnedOff()
    }

    override fun onScreenTurnedOn() {
        super.onScreenTurnedOn()
        ScreenSettings.refreshNightMode()
        refreshIfNightModeChange()
        documentViewManager.documentView.onScreenTurnedOn()
    }

    var currentNightMode: Boolean = false

    private fun beforeDestroy() {
        documentViewManager.destroy()
        bibleActionBarManager.destroy()
    }

    fun refreshIfNightModeChange(): Boolean {
        // colour may need to change which affects View colour and html
        // first refresh the night mode setting using light meter if appropriate
        ScreenSettings.checkMonitoring()
        val isNightMode = ScreenSettings.nightMode
        if (currentNightMode != isNightMode) {
            if(!windowRepository.isBusy) {
                recreate()
                currentNightMode = isNightMode
                return true
            } else {
                // Cancel night mode setting
                ScreenSettings.setLastNightMode(currentNightMode)
            }
        }
        return false
    }

    fun onEvent(event: ScreenSettings.NightModeChanged) {
        if(CurrentActivityHolder.getInstance().currentActivity == this) {
            refreshIfNightModeChange()
        }
    }

    /**
     * adding android:configChanges to manifest causes this method to be called on flip, etc instead of a new instance and onCreate, which would cause a new observer -> duplicated threads
     */
    private fun updateToolbar() {
        navigationView.setPadding(0, 0, 0, bottomOffset1.roundToInt())
        speakTransport.translationY = -bottomOffset1
        toolbar.translationY = topOffset1
        toolbar.setPadding(leftOffset1.roundToInt(), 0, rightOffset1.roundToInt(), 0)
        if(isFullScreen)
            hideSystemUI()
        else
            showSystemUI()
    }

    class ConfigurationChanged(val configuration: Configuration)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateToolbar()
        ABEventBus.getDefault().post(ConfigurationChanged(newConfig))
        windowControl.windowSizesChanged()
        refreshIfNightModeChange()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "Keycode:$keyCode")
        // common key handling i.e. KEYCODE_DPAD_RIGHT & KEYCODE_DPAD_LEFT
        if (bibleKeyHandler.onKeyUp(keyCode, event)) {
            return true
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH && windowControl.activeWindowPageManager.currentPage.isSearchable) {
            val intent = searchControl.getSearchIntent(windowControl.activeWindowPageManager.currentPage.currentDocument)
            if (intent != null) {
                startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE)
            }
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "Activity result:$resultCode")
        when(requestCode) {
            REQUEST_PICK_FILE_FOR_BACKUP_RESTORE -> {
                if (resultCode == Activity.RESULT_OK) {
                    CurrentActivityHolder.getInstance().currentActivity = this
                    Dialogs.instance.showMsg(R.string.restore_confirmation, true) {
                        ABEventBus.getDefault().post(ToastEvent(getString(R.string.loading_backup)))
                        thread {
                            val inputStream = contentResolver.openInputStream(data!!.data!!)
                            if(backupControl.restoreDatabaseViaIntent(inputStream!!)) {
                                windowControl.windowSync.setResyncRequired()
                                runOnUiThread {
                                    documentViewManager.clearBibleViewFactory()
                                    currentWorkspaceId = 0
                                }
                            }
                        }
                    }
                }
            }
            WORKSPACE_CHANGED -> {
                val extras = data?.extras
                val workspaceId = extras?.getLong("workspaceId")
                val changed = extras?.getBoolean("changed")

                if(resultCode == Activity.RESULT_OK) {
                    if(workspaceId != 0L && workspaceId != currentWorkspaceId) {
                        currentWorkspaceId = workspaceId!!
                    } else if(changed == true) {
                        currentWorkspaceId = currentWorkspaceId
                    }
                }
                return
            }
            COLORS_CHANGED -> {
                val extras = data?.extras!!
                val edited = extras.getBoolean("edited")
                val reset = extras.getBoolean("reset")
                val windowId = extras.getLong("windowId")
                val colorsStr = extras.getString("colors")

                if(!edited && !reset) return

                val colors = if(reset)
                    if(windowId != 0L) {
                        null
                    } else TextDisplaySettings.default.colors
                else
                    WorkspaceEntities.Colors.fromJson(colorsStr!!)

                if(windowId != 0L) {
                    val window = windowRepository.getWindow(windowId)!!
                    window.pageManager.textDisplaySettings.colors = colors
                    window.bibleView?.updateTextDisplaySettings()
                } else {
                    windowRepository.textDisplaySettings.colors = colors
                    windowRepository.updateWindowTextDisplaySettingsValues(setOf(TextDisplaySettings.Types.COLORS), windowRepository.textDisplaySettings)
                    windowRepository.updateVisibleWindowsTextDisplaySettings()
                }
                resetSystemUi()
                invalidateOptionsMenu()
            }
            TEXT_DISPLAY_SETTINGS_CHANGED -> {
                val extras = data?.extras!!

                val edited = extras.getBoolean("edited")
                val reset = extras.getBoolean("reset")

                val settingsBundle = SettingsBundle.fromJson(extras.getString("settingsBundle")!!)
                val requiresReload = extras.getBoolean("requiresReload")

                if(!edited && !reset) return

                val dirtyTypes = DirtyTypesSerializer.fromJson(extras.getString("dirtyTypes")!!).dirtyTypes

                workspaceSettingsChanged(settingsBundle, requiresReload, reset, dirtyTypes)
                return
            }
            STD_REQUEST_CODE -> {
                val classes = arrayOf(GridChoosePassageBook::class.java.name, Bookmarks::class.java.name)
                if (classes.contains(data?.component?.className)) {
                    val verseStr = data?.extras!!.getString("verse")
                    val verse = try {
                        VerseFactory.fromString(navigationControl.versification, verseStr)
                    } catch (e: NoSuchVerseException) {
                        ABEventBus.getDefault().post(ToastEvent(getString(R.string.verse_not_found)))
                        return
                    }
                    if (pageControl.currentPageManager.isMyNoteShown) {
                        val doc = pageControl.currentPageManager.currentBible.currentDocument
                        pageControl.currentPageManager.setCurrentDocument(doc)
                    }

                    windowControl.activeWindowPageManager.currentPage.setKey(verse)
                    return
                }
                if(data?.component?.className == MyNotes::class.java.name) {
                    invalidateOptionsMenu()
                    documentViewManager.buildView()
                }
            }
            IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH -> {
                documentControl.checkIfAnyPageDocumentsDeleted()
                return
            }
            else -> throw RuntimeException("Unhandled request code $requestCode")
        }
        super.onActivityResult(requestCode, resultCode, data)
        when {
            mainMenuCommandHandler.restartIfRequiredOnReturn(requestCode) -> {
                // restart done in above
            }
            mainMenuCommandHandler.isDisplayRefreshRequired(requestCode) -> {
                preferenceSettingsChanged()
            }
            mainMenuCommandHandler.isDocumentChanged(requestCode) -> updateActions()
        }

    }

    private fun workspaceSettingsChanged(settingsBundle: SettingsBundle, requiresReload: Boolean = false,
                                         reset: Boolean = false, dirtyTypes: Set<TextDisplaySettings.Types>? = null) {
        val windowId = settingsBundle.windowId
        if(windowId != null) {
            val window = windowRepository.getWindow(windowId)!!
            window.pageManager.textDisplaySettings = if(reset)
                TextDisplaySettings()
            else
                settingsBundle.pageManagerSettings!!

            if(requiresReload)
                window.updateText()
            else {
                window.bibleView?.updateTextDisplaySettings()
            }
        } else {
            if(reset) {
                windowRepository.textDisplaySettings = TextDisplaySettings.default
            } else {
                windowRepository.textDisplaySettings = settingsBundle.workspaceSettings
            }
            if(dirtyTypes != null) {
                windowRepository.updateWindowTextDisplaySettingsValues(dirtyTypes, settingsBundle.workspaceSettings)
            }
            if(requiresReload) {
                ABEventBus.getDefault().post(SynchronizeWindowsEvent(true))
            } else {
                windowRepository.updateVisibleWindowsTextDisplaySettings()
            }
        }
        invalidateOptionsMenu()
        resetSystemUi()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            SDCARD_READ_REQUEST -> if (grantResults.isNotEmpty()) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    documentControl.enableManualInstallFolder()
                } else {
                    documentControl.turnOffManualInstallFolderSetting()
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun preferenceSettingsChanged() {
        resetSystemUi()
        if(!refreshIfNightModeChange()) {
            requestSdcardPermission()
            invalidateOptionsMenu()
            documentViewManager.buildView()
            ABEventBus.getDefault().post(SynchronizeWindowsEvent(true))
        }
    }

    private fun requestSdcardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val requestSdCardPermission = preferences.getBoolean(REQUEST_SDCARD_PERMISSION_PREF, false)
            if (requestSdCardPermission && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), SDCARD_READ_REQUEST)
            }
        }
    }

    /**
     * allow current page to save any settings or data before being changed
     */
    fun onEvent(event: PreBeforeCurrentPageChangeEvent) {
        val currentPage = windowControl.activeWindowPageManager.currentPage
        // save current scroll position so history can return to correct place in document
        currentPage.currentYOffsetRatio = currentPosition
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        updateActions()
    }

    fun onEvent(event: NumberOfWindowsChangedEvent) {
        invalidateOptionsMenu()
    }

    /**
     * called by PassageChangeMediator after a new passage has been changed and displayed
     */
    fun onEventMainThread(event: PassageChangedEvent) {
        updateActions()
   }

    override fun onResume() {
        super.onResume()
        // allow webView to start monitoring tilt by setting focus which causes tilt-scroll to resume
        documentViewManager.documentView.asView().requestFocus()
    }

    /**
     * Some menu items must be hidden for certain document types
     */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // construct the options menu
        super.onPrepareOptionsMenu(menu)

        // disable some options depending on document type
        windowControl.activeWindowPageManager.currentPage.updateOptionsMenu(menu)

        // must return true for menu to be displayed
        return true
    }

    override fun isVerseActionModeAllowed(): Boolean {
        return !drawerLayout.isDrawerVisible(navigationView)
    }

    override fun showVerseActionModeMenu(actionModeCallbackHandler: ActionMode.Callback) {
        Log.d(TAG, "showVerseActionModeMenu")

        runOnUiThread {
            showSystemUI()
            val actionMode = startSupportActionMode(actionModeCallbackHandler)
            // Fix for onPrepareActionMode not being called: https://code.google.com/p/android/issues/detail?id=159527
            actionMode?.invalidate()
        }
    }

    override fun clearVerseActionMode(actionMode: ActionMode) {
        runOnUiThread {
            actionMode.finish()
            resetSystemUi()
        }
    }

    /**
     * user swiped right
     */
    operator fun next() {
        if (documentViewManager.documentView.isPageNextOkay) {
            windowControl.activeWindowPageManager.currentPage.next()
        }
    }

    /**
     * user swiped left
     */
    fun previous() {
        if (documentViewManager.documentView.isPagePreviousOkay) {
            windowControl.activeWindowPageManager.currentPage.previous()
        }
    }


    companion object {
        lateinit var mainBibleActivity: MainBibleActivity
        var initialized = false
        private const val SDCARD_READ_REQUEST = 2

        // ActivityBase.STD_REQUEST_CODE = 1
        const val REQUEST_PICK_FILE_FOR_BACKUP_RESTORE = 2
        const val TEXT_DISPLAY_SETTINGS_CHANGED = 4
        const val COLORS_CHANGED = 5
        const val WORKSPACE_CHANGED = 6

        private const val SCREEN_KEEP_ON_PREF = "screen_keep_on_pref"
        private const val REQUEST_SDCARD_PERMISSION_PREF = "request_sdcard_permission_pref"

        private const val TAG = "MainBibleActivity"
    }
}

