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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import kotlinx.android.synthetic.main.main_bible_view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

/** The main activity screen showing Bible text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class MainBibleActivity : CustomTitlebarActivityBase(), VerseActionModeMediator.ActionModeMenuDisplay {
    private var mWholeAppWasInBackground = false

    // We need to have this here in order to initialize BibleContentManager early enough.
    @Inject lateinit var bibleContentManager: BibleContentManager
    @Inject lateinit var documentViewManager: DocumentViewManager
    @Inject lateinit var bibleActionBarManager: BibleActionBarManager
    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var speakControl: SpeakControl

    // handle requests from main menu
    @Inject lateinit var mainMenuCommandHandler: MenuCommandHandler
    @Inject lateinit var bibleKeyHandler: BibleKeyHandler
    @Inject lateinit var backupControl: BackupControl
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var documentControl: DocumentControl
    @Inject lateinit var navigationControl: NavigationControl

    private var navigationBarHeight = 0
    private var actionBarHeight = 0
    private var transportBarHeight = 0

    private var hasHwKeys: Boolean = false

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

    // Top offset with only statusbar and toolbar
    val topOffset2: Int get() {
        return topOffset1 + if (!(isFullScreen && actionMode == null)) actionBarHeight else 0
    }
    // Top offset with only statusbar and toolbar taken into account always
    val topOffsetWithActionBar get() = topOffset1 + actionBarHeight

    // Offsets with system insets only
    private var topOffset1 = 0
        get() = if(isFullScreen && actionMode == null) 0 else field

    private var bottomOffset1 = 0
        get() = if(isFullScreen || (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && multiWinMode)) 0 else field

    var rightOffset1 = 0
        get() = if(isFullScreen) 0 else field

    var leftOffset1 = 0
        get() = if(isFullScreen) 0 else field

    // Bottom offset with navigation bar and transport bar
    val bottomOffset2 get() = bottomOffset1 + if (transportBarVisible) transportBarHeight else 0

    private var isPaused = false
    /**
     * return percentage scrolled down page
     */
    private val currentPosition: Float
        get() = documentViewManager.documentView?.currentPosition ?: 0F

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "Creating MainBibleActivity")

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

        // use context to setup backup control dirs
        BackupControl.setupDirs(this)
        // When I mess up database, I can re-create database like this.
        //backupControl.resetDatabase()

        backupControl.clearBackupDir()
        windowRepository.initialize()
        var firstTime = true

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets: WindowInsetsCompat ->
            if (!isPaused) {
                val heightChanged =
                    bottomOffset1 != insets.systemWindowInsetBottom || topOffset1 != insets.systemWindowInsetTop
                val widthChanged =
                    leftOffset1 != insets.systemWindowInsetLeft || rightOffset1 != insets.systemWindowInsetRight
                bottomOffset1 = insets.systemWindowInsetBottom
                topOffset1 = insets.systemWindowInsetTop
                leftOffset1 = insets.systemWindowInsetLeft
                rightOffset1 = insets.systemWindowInsetRight
                Log.d(TAG, "onApplyWindowInsets $bottomOffset1 $topOffset1 $leftOffset1 $rightOffset1")

                if (firstTime) {
                    postInitialize()
                }

                if (widthChanged || heightChanged)
                    displaySizeChanged(firstTime)

                if (firstTime)
                    firstTime = false
            }

            ViewCompat.onApplyWindowInsets(view, insets)
        }


        // Mainly for old devices (older than API 21)
        hasHwKeys = ViewConfiguration.get(this).hasPermanentMenuKey()

        val navBarId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (navBarId > 0) {
            navigationBarHeight = resources.getDimensionPixelSize(navBarId)
        }

        setSupportActionBar(toolbar)

        val tv = TypedValue()
        if (theme.resolveAttribute(R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        }

        if (theme.resolveAttribute(R.attr.transportBarHeight, tv, true)) {
            transportBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        }

        toolbar.setContentInsetsAbsolute(0, 0)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()
            mainMenuCommandHandler.handleMenuRequest(menuItem)
        }

        var currentSliderOffset = 0.0F

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {
                when(newState) {
                    DrawerLayout.STATE_SETTLING -> {
                        showSystemUI(false)
                    }
                    DrawerLayout.STATE_IDLE -> {
                        if(currentSliderOffset == 0.0F) {
                            if (isFullScreen) {
                                hideSystemUI()
                            } else {
                                showSystemUI()
                            }
                        }
                    }
                    DrawerLayout.STATE_DRAGGING -> {
                        showSystemUI(false)
                    }
                }

            }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                currentSliderOffset = slideOffset
            }

            override fun onDrawerOpened(drawerView: View) {}

            override fun onDrawerClosed(drawerView: View) {}
        })
        // register for passage change and appToBackground events
        ABEventBus.getDefault().register(this)

        setupToolbarButtons()
        setupToolbarFlingDetection()

        refreshScreenKeepOn()
        if(!initialized)
            requestSdcardPermission()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        speakTransport.visibility = View.GONE

        if(!initialized) {
            GlobalScope.launch(Dispatchers.Main) {
                showBetaNotice()
                showFirstTimeHelp()
            }
        }
        initialized = true
    }

    private fun postInitialize() {
        // Perform initialization that requires that offsets are set up correctly.
        Log.d(TAG, "postInitialize")
        documentViewManager.buildView()
        windowControl.windowSync.reloadAllWindows(true)
        updateActions()
        ABEventBus.getDefault().post(ConfigurationChanged(resources.configuration))
    }

    private fun displaySizeChanged(firstTime: Boolean) {
        Log.d(TAG, "displaySizeChanged $firstTime")
        updateToolbar()
        updateBottomBars()
        if(!firstTime) {
            ABEventBus.getDefault().post(ConfigurationChanged(resources.configuration))
            windowControl.windowSizesChanged()
        }
    }

    private suspend fun showFirstTimeHelp()  {
        val pinningHelpShown = preferences.getBoolean("pinning-help-shown", false)
        if(!pinningHelpShown) {
            val save = suspendCoroutine<Boolean> {
                val pinningTitle = getString(R.string.help_window_pinning_title)
                var pinningText = getString(R.string.help_window_pinning_text)

                pinningText += "<br><i><a href=\"https://youtu.be/27b1g-D3ibA\">${getString(R.string.watch_tutorial_video)}</a></i><br>"
                
                val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(pinningText, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    Html.fromHtml(pinningText)
                }
                val d = AlertDialog.Builder(this)
                    .setTitle(pinningTitle)
                    .setMessage(spanned)
                    .setNeutralButton(getString(R.string.first_time_help_show_next_time), null)
                    .setPositiveButton(getString(R.string.first_time_help_do_not_show_again)) { _, _ ->
                        it.resume(true)
                    }
                    .show()

                d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
            }
            if(save) {
                preferences.edit().putBoolean("pinning-help-shown", true).apply()
            }
        }
    }

    private suspend fun showBetaNotice() = suspendCoroutine<Boolean> {
        val verFull = CommonUtils.applicationVersionName
        val ver = verFull.split("#")[0]

        if(!ver.endsWith("-beta")) {
            it.resume(false)
            return@suspendCoroutine
        }

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
                .setNeutralButton(getString(R.string.beta_notice_dismiss)) { _, _ -> it.resume(false)}
                .setPositiveButton(getString(R.string.beta_notice_dismiss_until_update)) { _, _ ->
                    preferences.edit().putString("beta-notice-displayed", ver).apply()
                    it.resume(true)
                }
                .setOnCancelListener {_ -> it.resume(false)}
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
        isPaused = true;
        super.onPause()
    }

    private var lastBackPressed: Long? = null

    override fun onBackPressed() {
        val lastBackPressed = lastBackPressed
        if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
            drawerLayout.closeDrawers()
        } else {
            if (!historyTraversal.goBack()) {
                if(lastBackPressed == null || lastBackPressed < System.currentTimeMillis() - 1000) {
                    this.lastBackPressed = System.currentTimeMillis()
                    Toast.makeText(this, getString(R.string.one_more_back_press), Toast.LENGTH_SHORT).show()
                } else {
                    this.lastBackPressed = null
                    super.onBackPressed()
                }
            } else {
                this.lastBackPressed = null
            }
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
            documentViewManager.buildView(forceUpdate = true)
            windowControl.windowSync.reloadAllWindows()
            windowRepository.updateVisibleWindowsTextDisplaySettings()

            ABEventBus.getDefault().post(ToastEvent(windowRepository.name))

            invalidateOptionsMenu()
            updateTitle()
            //updateToolbar()
        }

    private fun getItemOptions(item: MenuItem): OptionsMenuItemInterface {
        val settingsBundle = SettingsBundle(workspaceId = windowRepository.id, workspaceName = windowRepository.name, workspaceSettings = windowRepository.textDisplaySettings)
        return when(item.itemId) {
            R.id.allTextOptions -> CommandPreference(launch = { _, _, _ ->
                val intent = Intent(this, TextDisplaySettingsActivity::class.java)
                intent.putExtra("settingsBundle", settingsBundle.toJson())
                startActivityForResult(intent, TEXT_DISPLAY_SETTINGS_CHANGED)
            }, opensDialog = true)
            R.id.textOptionsSubMenu -> SubMenuPreference(false)
            R.id.textOptionItem -> getPrefItem(settingsBundle, CommonUtils.lastDisplaySettings[item.order])
            R.id.splitMode -> SplitModePreference()
            R.id.autoPinMode -> WindowPinningPreference()
            R.id.tiltToScroll -> TiltToScrollPreference()
            R.id.nightMode -> NightModePreference()
            R.id.switchToWorkspace -> CommandPreference(launch = { _, _, _ ->
                val intent = Intent(this, WorkspaceSelectorActivity::class.java)
                startActivityForResult(intent, WORKSPACE_CHANGED)
            }, opensDialog = true)
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
                if(itmOptions.opensDialog) {
                    item.title = getString(R.string.add_ellipsis, item.title.toString())
                }
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
            val isBible = doc?.bookCategory == BookCategory.BIBLE
            if(isBible) {
                key = pageControl.currentBibleVerse
            }
            return if(key is Verse && key.verse == 0) {
                CommonUtils.getWholeChapter(key, false).name
            } else key?.name ?: throw KeyIsNull()
        }

    val bibleOverlayText: String
        get() {
            val bookName = pageControl.currentPageManager.currentPage.currentDocument?.abbreviation
            synchronized(BookName::class) {
                val oldValue = BookName.isFullBookName()
                BookName.setFullBookName(false)
                val text = pageTitleText
                BookName.setFullBookName(oldValue)
                return "$bookName:$text"
            }
        }

    private fun updateTitle() {
        try {
            pageTitle.text = pageTitleText
            val layout = pageTitle.layout
            if(layout!= null && layout.lineCount > 0 && layout.getEllipsisCount(0) > 0) {
                synchronized(BookName::class) {
                    val oldValue = BookName.isFullBookName()
                    BookName.setFullBookName(false)
                    pageTitle.text = pageTitleText
                    BookName.setFullBookName(oldValue)
                }
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

    private val currentDocument get() = windowControl.activeWindow.pageManager.currentPage.currentDocument

    override fun updateActions() {
        updateTitle()
        val biblesForVerse = documentControl.biblesForVerse.filter {currentDocument != it}
        val commentariesForVerse = documentControl.commentariesForVerse.filter {currentDocument != it}

        var visibleButtonCount = 0
        val screenWidth = resources.displayMetrics.widthPixels
        val approximateSize = 53 * resources.displayMetrics.density
        val maxWidth = (screenWidth * 0.5).roundToInt()
        val maxButtons: Int = (maxWidth / approximateSize).toInt()
        val showSearch = documentControl.isBibleBook || documentControl.isCommentary

        bibleButton.visibility = if (visibleButtonCount < maxButtons && biblesForVerse.isNotEmpty()) {
            bibleButton.setOnClickListener { menuForDocs(it, biblesForVerse) }
            bibleButton.setOnLongClickListener {
                val intent = Intent(this, ChooseDocument::class.java)
                intent.putExtra("type", "BIBLE")
                startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
                true
            }
            visibleButtonCount += 1
            View.VISIBLE
        } else View.GONE

        commentaryButton.visibility = if (commentariesForVerse.isNotEmpty() && visibleButtonCount < maxButtons) {
            commentaryButton.setOnClickListener { menuForDocs(it, commentariesForVerse) }
            commentaryButton.setOnLongClickListener {
                val intent = Intent(this, ChooseDocument::class.java)
                intent.putExtra("type", "COMMENTARY")
                startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
                true
            }
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
            updateBottomBars()
        }
        updateActions()
    }

    private fun menuForDocs(v: View, documents: List<Book>): Boolean {
        val menu = PopupMenu(this, v)
        val docs = documents.sortedWith(compareBy({it.language.code}, {it.abbreviation}))
        docs.forEachIndexed { i, book ->
            if(documentControl.isMyNotes || currentDocument != book) {
                menu.menu.add(Menu.NONE, i, Menu.NONE, getString(R.string.something_with_parenthesis, book.abbreviation, book.language.code))
            }
        }

        if (docs.size == 1) {
            setCurrentDocument(docs[0])
        } else {
            menu.setOnMenuItemClickListener { item ->
                setCurrentDocument(docs[item.itemId])
                true
            }
            menu.show()
        }
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
        updateToolbar()
        updateBottomBars()
    }

    fun resetSystemUi() {
        if(isFullScreen)
            hideSystemUI()
        else
            showSystemUI()
    }

    private val sharedActivityState = SharedActivityState.instance

    private fun hideSystemUI() {
        var uiFlags = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ScreenSettings.nightMode) {
                uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

        window.decorView.systemUiVisibility = uiFlags
    }

    private fun showSystemUI(setNavBarColor: Boolean=true) {
        var uiFlags = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ScreenSettings.nightMode) {
                uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            val color = if(setNavBarColor) {
                val colors = windowRepository.lastVisibleWindow.pageManager.actualTextDisplaySettings.colors!!
                val color = if(ScreenSettings.nightMode) colors.nightBackground else colors.dayBackground
                color?: UiUtils.bibleViewDefaultBackgroundColor
            } else {
                val typedValue = TypedValue()
                theme.resolveAttribute(android.R.attr.navigationBarColor, typedValue, true)
                typedValue.data
            }
            window.navigationBarColor = color
            speakTransport.setBackgroundColor(color)
        }
        window.decorView.systemUiVisibility = uiFlags
    }

    private fun updateBottomBars() {
        Log.d(TAG, "updateBottomBars")
        if(isFullScreen || speakControl.isStopped) {
            transportBarVisible = false
            speakTransport.animate()
                .translationY(speakTransport.height.toFloat())
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { speakTransport.visibility = View.GONE }
                .start()
        } else {
            transportBarVisible = true
            speakTransport.visibility = View.VISIBLE
            speakTransport.animate()
                .translationY(-bottomOffset1.toFloat())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        ABEventBus.getDefault().post(UpdateRestoreWindowButtons())
    }

    class UpdateRestoreWindowButtons

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
        documentViewManager.documentView?.onScreenTurnedOff()
    }

    override fun onScreenTurnedOn() {
        super.onScreenTurnedOn()
        ScreenSettings.refreshNightMode()
        refreshIfNightModeChange()
        documentViewManager.documentView?.onScreenTurnedOn()
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
        applyTheme()
        return true
    }

    fun onEvent(event: ScreenSettings.NightModeChanged) {
        if(CurrentActivityHolder.getInstance().currentActivity == this) {
            refreshIfNightModeChange()
        }
    }

    private fun updateToolbar() {
        toolbar.setPadding(leftOffset1, 0, rightOffset1, 0)
        setActionModeToolbarPadding()
        navigationView.setPadding(leftOffset1, 0, rightOffset1, bottomOffset1)
        speakTransport.setPadding(leftOffset1, 0, rightOffset1, 0)
        if(isFullScreen) {
            hideSystemUI()
            Log.d(TAG, "Fullscreen on")
            toolbar.animate().translationY(-toolbar.height.toFloat())
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { supportActionBar?.hide() }
                .start()
        }
        else {
            showSystemUI()
            Log.d(TAG, "Fullscreen off")
            toolbar.translationY = -toolbar.height.toFloat()
            supportActionBar?.show()
            toolbar.animate().translationY(topOffset1.toFloat())
                .setInterpolator(DecelerateInterpolator())
                .start()
            updateActions()
        }
    }

    class ConfigurationChanged(val configuration: Configuration)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed")

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
                        GlobalScope.launch(Dispatchers.IO) {
                            val inputStream = contentResolver.openInputStream(data!!.data!!)
                            if (backupControl.restoreDatabaseViaIntent(inputStream!!)) {
                                windowControl.windowSync.setResyncRequired()
                                withContext(Dispatchers.Main) {
                                    documentViewManager.clearBibleViewFactory()
                                    currentWorkspaceId = 0
                                }
                            }
                        }
                    }
                }
            }
            REQUEST_PICK_FILE_FOR_BACKUP_DB -> {
                mainBibleActivity.windowRepository.saveIntoDb()
                DatabaseContainer.db.sync()
                GlobalScope.launch(Dispatchers.IO) {
                    backupControl.backupDatabaseToUri(data!!.data!!)
                }
            }
            REQUEST_PICK_FILE_FOR_BACKUP_MODULES -> {
                GlobalScope.launch(Dispatchers.IO) {
                    backupControl.backupModulesToUri(data!!.data!!)
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
                val className = data?.component?.className
                if (className != null && classes.contains(className)) {
                    val verseStr = data.extras!!.getString("verse")
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
                if(className == MyNotes::class.java.name) {
                    invalidateOptionsMenu()
                    documentViewManager.buildView()
                }
            }
            IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH -> {
                documentControl.checkIfAnyPageDocumentsDeleted()
                updateActions()
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

    private fun preferenceSettingsChanged() {
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
        isPaused = false
        // allow webView to start monitoring tilt by setting focus which causes tilt-scroll to resume
        documentViewManager.documentView?.asView()?.requestFocus()
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

    private var actionMode: ActionMode? = null
    private var wasFullScreen : Boolean = false
    override fun showVerseActionModeMenu(actionModeCallbackHandler: ActionMode.Callback) {
        Log.d(TAG, "showVerseActionModeMenu")

        GlobalScope.launch(Dispatchers.Main) {
            wasFullScreen = isFullScreen
            fullScreen = false
            actionMode = startSupportActionMode(actionModeCallbackHandler)

            setActionModeToolbarPadding()

            // Fix for onPrepareActionMode not being called: https://code.google.com/p/android/issues/detail?id=159527
            actionMode?.invalidate()
        }
    }

    private fun setActionModeToolbarPadding() {
        val toolbar = actionMode?.customView?.findViewById<Toolbar>(R.id.toolbarContextual)
        toolbar?.setPadding(leftOffset1, 0, rightOffset1, 0)
    }

    override fun clearVerseActionMode(actionMode: ActionMode) {
        GlobalScope.launch(Dispatchers.Main) {
            actionMode.finish()
            this@MainBibleActivity.actionMode = null
            fullScreen = wasFullScreen
        }
    }

    /**
     * user swiped right
     */
    operator fun next() {
        if (documentViewManager.documentView!!.isPageNextOkay) {
            windowControl.activeWindowPageManager.currentPage.next()
        }
    }

    /**
     * user swiped left
     */
    fun previous() {
        if (documentViewManager.documentView!!.isPagePreviousOkay) {
            windowControl.activeWindowPageManager.currentPage.previous()
        }
    }


    companion object {
        lateinit var mainBibleActivity: MainBibleActivity
        var initialized = false
        private const val SDCARD_READ_REQUEST = 2

        const val REQUEST_PICK_FILE_FOR_BACKUP_RESTORE = 2
        const val TEXT_DISPLAY_SETTINGS_CHANGED = 4
        const val COLORS_CHANGED = 5
        const val WORKSPACE_CHANGED = 6
        const val REQUEST_PICK_FILE_FOR_BACKUP_DB = 7
        const val REQUEST_PICK_FILE_FOR_BACKUP_MODULES = 8


        private const val SCREEN_KEEP_ON_PREF = "screen_keep_on_pref"
        private const val REQUEST_SDCARD_PERMISSION_PREF = "request_sdcard_permission_pref"

        private const val TAG = "MainBibleActivity"
    }
}

