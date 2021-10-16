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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.activity.databinding.MainBibleViewBinding
import net.bible.android.control.BibleContentManager
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.passage.PassageChangedEvent
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.DaggerMainBibleActivityComponent
import net.bible.android.view.activity.MainBibleActivityModule
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.page.actionbar.BibleActionBarManager
import net.bible.android.view.activity.page.screen.DocumentViewManager
import net.bible.android.view.activity.settings.DirtyTypesSerializer
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.speak.GeneralSpeakActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorActivity
import net.bible.android.view.util.UiUtils
import net.bible.android.view.util.widget.SpeakTransportWidget
import net.bible.service.common.CommonUtils
import net.bible.service.common.betaIntroVideo
import net.bible.service.common.windowPinningVideo
import net.bible.service.common.newFeaturesIntroVideo
import net.bible.service.db.DatabaseContainer
import net.bible.service.device.ScreenSettings
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.download.DownloadManager
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
import kotlin.system.exitProcess

/** The main activity screen showing Bible text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class MainBibleActivity : CustomTitlebarActivityBase() {
    private lateinit var binding: MainBibleViewBinding

    private var mWholeAppWasInBackground = false

    // We need to have this here in order to initialize BibleContentManager early enough.
    @Inject lateinit var bibleContentManager: BibleContentManager
    @Inject lateinit var documentViewManager: DocumentViewManager
    @Inject lateinit var bibleActionBarManager: BibleActionBarManager
    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var bookmarkControl: BookmarkControl

    // handle requests from main menu
    @Inject lateinit var mainMenuCommandHandler: MenuCommandHandler
    @Inject lateinit var bibleKeyHandler: BibleKeyHandler
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var documentControl: DocumentControl
    @Inject lateinit var navigationControl: NavigationControl
    @Inject lateinit var bibleViewFactory: BibleViewFactory
    @Inject lateinit var pageControl: PageControl

    private var navigationBarHeight = 0
    private var actionBarHeight = 0
    private var transportBarHeight = 0
    private var windowButtonHeight = 0

    private var hasHwKeys: Boolean = false

    private var transportBarVisible = false
        set(value) {
            binding.speakButton.alpha = if(value) 0.7F else 1.0F
            field = value
        }

    val dao get() = DatabaseContainer.db.workspaceDao()
    val docDao get() = DatabaseContainer.db.swordDocumentInfoDao()

    val multiWinMode
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInMultiWindowMode else false

    // Top offset with only statusbar and toolbar
    val topOffset2 = 0

    // Top offset with only statusbar and toolbar taken into account always
    val topOffsetWithActionBar get() = topOffset1 + actionBarHeight

    // Offsets with system insets only
    private val topOffset1 = 0
    private val bottomOffset1 = 0
    val rightOffset1 = 0
    val leftOffset1 = 0

    // Bottom offset with navigation bar and transport bar
    val bottomOffset2 get() = bottomOffset1 + if (transportBarVisible) transportBarHeight else 0

    // Bottom offset with navigation bar and transport bar and window buttons
    val bottomOffset3 get() = bottomOffset2 + if (restoreButtonsVisible) windowButtonHeight else 0

    private val restoreButtonsVisible get() = preferences.getBoolean("restoreButtonsVisible", false)

    private var isPaused = false

    val workspaceSettings: WorkspaceEntities.WorkspaceSettings get() = windowRepository.workspaceSettings

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "Creating MainBibleActivity")
        // This is singleton so we can do this.
        if(_mainBibleActivity != null) {
            throw RuntimeException("MainBibleActivity was created second time!")
        }
        _mainBibleActivity = this

        ScreenSettings.refreshNightMode()
        currentNightMode = ScreenSettings.nightMode
        super.onCreate(savedInstanceState, true)

        CommonUtils.prepareData()

        binding = MainBibleViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DaggerMainBibleActivityComponent.builder()
            .applicationComponent(BibleApplication.application.applicationComponent)
            .mainBibleActivityModule(MainBibleActivityModule(this))
            .build()
            .inject(this)

        // use context to setup backup control dirs
        BackupControl.setupDirs(this)

        BackupControl.clearBackupDir()

        windowRepository.initialize()

        documentViewManager.buildView()
        windowControl.windowSync.reloadAllWindows(true)
        updateActions()
        ABEventBus.getDefault().post(ConfigurationChanged(resources.configuration))

        updateToolbar()
        updateBottomBars()

        // Mainly for old devices (older than API 21)
        hasHwKeys = ViewConfiguration.get(this).hasPermanentMenuKey()

        val navBarId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (navBarId > 0) {
            navigationBarHeight = resources.getDimensionPixelSize(navBarId)
        }

        val tv = TypedValue()
        if (theme.resolveAttribute(R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        }

        if (theme.resolveAttribute(R.attr.transportBarHeight, tv, true)) {
            transportBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        }

        if (theme.resolveAttribute(R.attr.windowButtonHeight, tv, true)) {
            windowButtonHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        }

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawers()
            mainMenuCommandHandler.handleMenuRequest(menuItem)
        }

        var currentSliderOffset = 0.0F

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
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
        setSoftKeyboardMode()

        if(!initialized)
            requestSdcardPermission()

        binding.speakTransport.visibility = View.GONE

        if(!initialized) {
            GlobalScope.launch(Dispatchers.Main) {
                ErrorReportControl.checkCrash(this@MainBibleActivity)
                if(!CommonUtils.checkPoorTranslations(this@MainBibleActivity)) exitProcess(2)
                showBetaNotice()
                showStableNotice()
                showFirstTimeHelp()
                ABEventBus.getDefault().post(ToastEvent(windowRepository.name))
            }
            GlobalScope.launch {
                checkDocBackupDBInSync()
            }
        }
        initialized = true
    }

    /**
     * Checks if the list of documents installed matches the list of
     * books in the backup database.
     *
     * Backup database is used to allow user to quickly reinstall all
     * available books if moving to a new device.
     */
    private fun checkDocBackupDBInSync() {
        val docs = swordDocumentFacade.documents
        val knownInstalled = docDao.getKnownInstalled()
        if (knownInstalled.isEmpty()) {
            Log.i(TAG, "There is at least one Bible, but Bible Backup DB is empty, populate with first time books");
            val allDocs = docs.map {
                SwordDocumentInfo(it.initials, it.name, it.abbreviation, it.language.name, it.getProperty(DownloadManager.REPOSITORY_KEY) ?: "")
            }
            docDao.insert(allDocs)
        } else {
            knownInstalled.map {
                Log.d(TAG, "The ${it.name} is installed")
            }
        }
    }

    private suspend fun showFirstTimeHelp()  {
        val pinningHelpShown = preferences.getBoolean("pinning-help-shown", false)
        if(!pinningHelpShown) {
            val save = CommonUtils.isFirstInstall || CommonUtils.mainVersionFloat >= 3.4 || suspendCoroutine<Boolean> {
                val pinningTitle = getString(R.string.help_window_pinning_title)
                var pinningText = getString(R.string.help_window_pinning_text)

                pinningText += "<br><i><a href=\"$windowPinningVideo\">${getString(R.string.watch_tutorial_video)}</a></i><br>"
                
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
                preferences.setBoolean("pinning-help-shown", true)
            }
        }
    }

    private suspend fun showStableNotice() = suspendCoroutine<Boolean> {
        if(CommonUtils.isBeta) {
            it.resume(false)
            return@suspendCoroutine
        }

        val ver = CommonUtils.mainVersion

        val displayedVer = preferences.getString("stable-notice-displayed", "")

        if(displayedVer != ver) {
            val videoMessage = getString(R.string.upgrade_video_message, CommonUtils.mainVersion)
            val videoMessageLink = "<a href=\"$newFeaturesIntroVideo\"><b>$videoMessage</b></a>"
            val appName = getString(R.string.app_name_long)
            val par1 = getString(R.string.stable_notice_par1, CommonUtils.mainVersion, appName)

            val htmlMessage = "$par1<br><br>$videoMessageLink"

            val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
            } else {
                Html.fromHtml(htmlMessage)
            }

            val d = AlertDialog.Builder(this)
                .setTitle(getString(R.string.stable_notice_title))
                .setMessage(spanned)
                .setIcon(R.drawable.ic_logo)
                .setNeutralButton(getString(R.string.beta_notice_dismiss)) { _, _ -> it.resume(false)}
                .setPositiveButton(getString(R.string.beta_notice_dismiss_until_update)) { _, _ ->
                    preferences.setString("stable-notice-displayed", ver)
                    it.resume(true)
                }
                .setOnCancelListener {_ -> it.resume(false)}
                .create()
            d.show()
            d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
        } else {
            it.resume(false)
        }
    }

    private suspend fun showBetaNotice() = suspendCoroutine<Boolean> {
        if(!CommonUtils.isBeta) {
            it.resume(false)
            return@suspendCoroutine
        }

        val verFull = CommonUtils.applicationVersionName
        val ver = verFull.split("#")[0]

        val displayedVer = preferences.getString("beta-notice-displayed", "")

        if(displayedVer != ver) {
            val videoMessage = getString(R.string.upgrade_video_message, CommonUtils.mainVersion)
            val videoMessageLink = "<a href=\"${betaIntroVideo}\"><b>$videoMessage</b></a>"

            val par1 = getString(R.string.beta_notice_content_1)
            val par2 = getString(R.string.beta_notice_content_2,
                 " <a href=\"https://github.com/AndBible/and-bible/issues\">"
                    + "${getString(R.string.beta_notice_github_issues)}</a>"
            )
            val par3 = getString(R.string.beta_notice_content_3,
                " <a href=\"https://github.com/AndBible/and-bible\">"
                    + "${getString(R.string.beta_notice_github)}</a>"

            )
            val htmlMessage = "$videoMessageLink<br><br>$par1<br><br> $par2<br><br> $par3 <br><br> <i>${getString(R.string.version_text, verFull)}</i>"

            val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
            } else {
                Html.fromHtml(htmlMessage)
            }

            val d = AlertDialog.Builder(this)
                .setTitle(getString(R.string.beta_notice_title))
                .setMessage(spanned)
                .setIcon(R.drawable.ic_logo)
                .setNeutralButton(getString(R.string.beta_notice_dismiss)) { _, _ -> it.resume(false)}
                .setPositiveButton(getString(R.string.beta_notice_dismiss_until_update)) { _, _ ->
                    preferences.setString("beta-notice-displayed", ver)
                    it.resume(true)
                }
                .setOnCancelListener {_ -> it.resume(false)}
                .create()
            d.show()
            d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
        } else {
            it.resume(false)
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
                pageControl.currentPageManager.currentPage.startKeyChooser(this@MainBibleActivity)
                return true
            }

        }
        val gestureDetector = GestureDetectorCompat(this, gestureListener)
        binding.pageTitleContainer.setOnTouchListener { v, event ->
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
        Log.d(TAG, "onBackPressed $fullScreen")
        if(fullScreen) {
            toggleFullScreen()
            return
        }
        val lastBackPressed = lastBackPressed
        if (binding.drawerLayout.isDrawerVisible(GravityCompat.START)) {
            binding.drawerLayout.closeDrawers()
        } else {
            if (!documentViewManager.documentView.backButtonPressed() && !historyTraversal.goBack()) {
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
        if (binding.drawerLayout.isDrawerVisible(GravityCompat.START) && keyCode == KeyEvent.KEYCODE_BACK) {
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
        binding.apply {
            optionsMenu.setOnClickListener {
                showOptionsMenu()
            }
            homeButton.setOnClickListener {
                if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
                    drawerLayout.closeDrawers()
                } else {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
            }

            strongsButton.setOnClickListener {
                val prefOptions = dummyStrongsPrefOption
                prefOptions.value = (prefOptions.value as Int + 1) % 3
                prefOptions.handle()
                updateStrongsButton()
            }

            strongsButton.setOnLongClickListener {
                val prefOptions = dummyStrongsPrefOption
                fun apply() {
                    prefOptions.handle()
                    updateStrongsButton()
                }
                prefOptions.openDialog(this@MainBibleActivity, onChanged = {apply()}, onReset = {apply()})
            }

            speakButton.setOnClickListener {
                if(transportBarVisible) {
                    if(speakControl.isStopped) {
                       transportBarVisible = false
                    }
                } else {
                    transportBarVisible = true
                }
                updateBottomBars()
            }

            speakButton.setOnLongClickListener {
                val isBible = windowControl.activeWindowPageManager.currentPage.documentCategory == DocumentCategory.BIBLE
                val intent = Intent(this@MainBibleActivity, if (isBible) BibleSpeakActivity::class.java else GeneralSpeakActivity::class.java)
                startActivity(intent)
                true
            }
            searchButton.setOnClickListener { startActivityForResult(searchControl.getSearchIntent(documentControl.currentDocument), STD_REQUEST_CODE) }
        }
    }

    fun onEvent(event: SpeakEvent) {
        if(event.isSpeaking) {
            transportBarVisible = true
            updateBottomBars()
        } else if(event.isStopped) {
            transportBarVisible = false
            updateBottomBars()
        }
    }

    fun onEvent(event: SpeakTransportWidget.HideTransportEvent) {
        transportBarVisible = false
        updateBottomBars()
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
            documentViewManager.removeView()
            bibleViewFactory.clear()
            windowRepository.loadFromDb(value)

            preferences.setLong("current_workspace_id", windowRepository.id)
            documentViewManager.buildView(forceUpdate = true)
            windowControl.windowSync.reloadAllWindows()
            windowRepository.updateAllWindowsTextDisplaySettings()

            ABEventBus.getDefault().post(ToastEvent(windowRepository.name))

            updateBottomBars()
            updateTitle()
        }

    private fun getItemOptions(itemId: Int, order: Int = 0): OptionsMenuItemInterface {
        val settingsBundle = SettingsBundle(workspaceId = windowRepository.id, workspaceName = windowRepository.name, workspaceSettings = windowRepository.textDisplaySettings)
        return when(itemId) {
            R.id.allTextOptions -> CommandPreference(launch = { _, _, _ ->
                val intent = Intent(this, TextDisplaySettingsActivity::class.java)
                intent.putExtra("settingsBundle", settingsBundle.toJson())
                startActivityForResult(intent, TEXT_DISPLAY_SETTINGS_CHANGED)
            }, opensDialog = true)
            R.id.autoAssignLabels -> AutoAssignPreference(windowRepository.workspaceSettings)
            R.id.textOptionsSubMenu -> SubMenuPreference(false)
            R.id.textOptionItem -> getPrefItem(settingsBundle, CommonUtils.lastDisplaySettingsSorted[order])
            R.id.splitMode -> SplitModePreference()
            R.id.autoPinMode -> WindowPinningPreference()
            R.id.tiltToScroll -> TiltToScrollPreference()
            R.id.nightMode -> NightModePreference()
            R.id.fullscreen -> CommandPreference(launch = { _, _, _ ->
                fullScreen = true
            })
            R.id.switchToWorkspace -> CommandPreference(launch = { _, _, _ ->
                val intent = Intent(this, WorkspaceSelectorActivity::class.java)
                startActivityForResult(intent, WORKSPACE_CHANGED)
            }, opensDialog = true)
            else -> throw RuntimeException("Illegal menu item")
        }
    }
    private fun getItemOptions(item: MenuItem) = getItemOptions(item.itemId, item.order)

    @SuppressLint("RestrictedApi")
    fun showOptionsMenu() {
        val popup = PopupMenu(this, binding.optionsMenu)
        val menu = popup.menu
        val menuHelper = MenuPopupHelper(this, menu as MenuBuilder, binding.optionsMenu)
        popup.setOnMenuItemClickListener { menuItem ->
            handlePrefItem(menuItem)
            true
        }
        menuHelper.setForceShowIcon(true)

        menuInflater.inflate(R.menu.main_bible_options_menu, menu)

        val lastSettings = CommonUtils.lastDisplaySettingsSorted
        if(lastSettings.isNotEmpty()) {
            for ((idx, t) in lastSettings.withIndex()) {
                val itm = getItemOptions(R.id.textOptionItem, idx)
                if(itm.enabled && itm.visible) {
                    menu.add(R.id.textOptionsGroup, R.id.textOptionItem, idx, t.name)
                }
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
                if(itmOptions.icon != null) {
                    item.setIcon(itmOptions.icon!!)
                    item.icon = CommonUtils.combineIcons(itmOptions.icon!!, R.drawable.ic_workspace_overlay_24dp)
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
        menu.findItem(R.id.allTextOptions).icon = CommonUtils.combineIcons(R.drawable.ic_text_options_24dp, R.drawable.ic_workspace_overlay_24dp)
        handleMenu(menu)
        menuHelper.show()
    }

    private fun handlePrefItem(item: MenuItem) {
        val itemOptions = getItemOptions(item)
        if(itemOptions is SubMenuPreference)
            return
        if(itemOptions.isBoolean) {
            itemOptions.value = !(itemOptions.value == true)
            itemOptions.handle()
            item.isChecked = itemOptions.value == true
            if(itemOptions is Preference) {
                windowRepository.updateWindowTextDisplaySettingsValues(setOf(itemOptions.type), windowRepository.textDisplaySettings)
            }
        } else {
            val onReady = {
                if(itemOptions is Preference) {
                    windowRepository.updateWindowTextDisplaySettingsValues(setOf(itemOptions.type), windowRepository.textDisplaySettings)
                }
                windowRepository.updateAllWindowsTextDisplaySettings()
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

    private val documentTitleText: String
        get() = pageControl.currentPageManager.currentPage.currentDocumentName

    class KeyIsNull: Exception()

    private val pageTitleText: String
        get() {
            val doc = pageControl.currentPageManager.currentPage.currentDocument
            var key = pageControl.currentPageManager.currentPage.displayKey
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
            binding.pageTitle.text = pageTitleText
            val layout = binding.pageTitle.layout
            if(layout!= null && layout.lineCount > 0 && layout.getEllipsisCount(0) > 0) {
                synchronized(BookName::class) {
                    val oldValue = BookName.isFullBookName()
                    BookName.setFullBookName(false)
                    binding.pageTitle.text = pageTitleText
                    BookName.setFullBookName(oldValue)
                }
            }
        } catch (e: KeyIsNull) {
            Log.e(TAG, "Key is null, not updating", e)
        }
        binding.documentTitle.text = documentTitleText
        updateStrongsButton()
    }

    private fun updateStrongsButton() {
        if(documentControl.isNewTestament) {
            binding.strongsButton.setImageResource(R.drawable.ic_strongs_greek)
        } else {
            binding.strongsButton.setImageResource(R.drawable.ic_strongs_hebrew)
        }
        if(dummyStrongsPrefOption.value == 0) {
            binding.strongsButton.alpha = 0.7F
        } else
            binding.strongsButton.alpha = 1.0F
    }

    private val currentDocument get() = windowControl.activeWindow.pageManager.currentPage.currentDocument
    private val toolbarButtonSetting get() = preferences.getString("toolbar_button_actions", "default")

    override fun updateActions() {
        updateTitle()
        val biblesForVerse = documentControl.biblesForVerse.filter {currentDocument != it}
        val commentariesForVerse = documentControl.commentariesForVerse.filter {currentDocument != it}

        val suggestedBible = documentControl.suggestedBible
        val suggestedCommentary = documentControl.suggestedCommentary

        var visibleButtonCount = 0
        val screenWidth = resources.displayMetrics.widthPixels
        val approximateSize = 53 * resources.displayMetrics.density
        val maxWidth = (screenWidth * 0.5).roundToInt()
        val maxButtons: Int = (maxWidth / approximateSize).toInt()
        val showSearch = documentControl.currentPage.currentPage.isSearchable
        val showSpeak = documentControl.currentPage.currentPage.isSpeakable

        fun shouldShowBibleButton(): Boolean =
            toolbarButtonSetting?.let {
                (it.startsWith("swap-") && suggestedBible != null) ||
                    (!it.startsWith("swap-") && biblesForVerse.isNotEmpty())
            } ?: false


        fun shouldShowCommentaryButton(): Boolean =
            toolbarButtonSetting?.let {
                (it.startsWith("swap-") && suggestedCommentary != null) ||
                    (!it.startsWith("swap-") && commentariesForVerse.isNotEmpty())
            } ?: false

        fun bibleClick(view: View) {
            if (toolbarButtonSetting?.startsWith("swap-") == true)
                setCurrentDocument(documentControl.suggestedBible);
            else
                menuForDocs(view, biblesForVerse)
        }

        fun commentaryClick(view: View) {
            if (toolbarButtonSetting?.startsWith("swap-") == true)
                setCurrentDocument(documentControl.suggestedCommentary);
            else
                menuForDocs(view, commentariesForVerse)
        }

        fun bibleLongPress(view: View) {
            if (toolbarButtonSetting == "swap-menu")
                menuForDocs(view, biblesForVerse)
            else {
                startDocumentChooser("BIBLE")
            }
        }

        fun commentaryLongPress(view: View) {
            if (toolbarButtonSetting == "swap-menu")
                menuForDocs(view, commentariesForVerse)
            else
                startDocumentChooser("COMMENTARY")
        }

        binding.apply {
            bibleButton.visibility = if (visibleButtonCount < maxButtons && shouldShowBibleButton()) {
                bibleButton.setOnClickListener { bibleClick(it) }
                bibleButton.setOnLongClickListener { bibleLongPress(it); true }
                visibleButtonCount += 1
                View.VISIBLE
            } else View.GONE

            commentaryButton.visibility = if (shouldShowCommentaryButton() && visibleButtonCount < maxButtons) {
                commentaryButton.setOnClickListener { commentaryClick(it) }
                commentaryButton.setOnLongClickListener { commentaryLongPress(it); true }
                visibleButtonCount += 1
                View.VISIBLE
            } else View.GONE

            strongsButton.visibility = if (visibleButtonCount < maxButtons && documentControl.isStrongsInBook) {
                visibleButtonCount += 1

                View.VISIBLE
            } else View.GONE


            fun addSearch() {
                searchButton.visibility = if (visibleButtonCount < maxButtons && showSearch)
                {
                    visibleButtonCount += 1
                    View.VISIBLE
                } else View.GONE
            }
            fun addSpeak() {
                speakButton.visibility = if (visibleButtonCount < maxButtons && speakControl.isStopped && showSpeak)
                {
                    visibleButtonCount += 1
                    View.VISIBLE
                } else View.GONE
            }

            val speakLastUsed = preferences.getLong("speak-last-used", 0)
            val searchLastUsed = preferences.getLong("search-last-used", 0)

            val funs = arrayListOf(
                Pair(speakLastUsed, {addSpeak()}),
                Pair(searchLastUsed, {addSearch()}),
            )
            funs.sortBy { -it.first }

            for(p in funs) {
                p.second()
            }

            if(!showSpeak && transportBarVisible && speakControl.isStopped) {
                transportBarVisible = false
                updateBottomBars()
            }

            navigationView.menu.findItem(R.id.searchButton).isEnabled = showSearch
            navigationView.menu.findItem(R.id.speakButton).isEnabled = showSpeak
        }
    }

    /** @param type can be BIBLE or COMMENTARY */
    private fun startDocumentChooser(type: String) {
        val intent = Intent(this, ChooseDocument::class.java)
        intent.putExtra("type", type)
        startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
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
            if(currentDocument != book) {
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
            CommonUtils.settings.setString("default-${bookCategory.name}", book.initials)
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
        if(isFullScreen) {
            ABEventBus.getDefault().post(ToastEvent(R.string.exit_fullscreen))
        }
    }

    fun resetSystemUi() {
        if(isFullScreen)
            hideSystemUI()
        else
            showSystemUI()
    }

    private val sharedActivityState = SharedActivityState.instance

    private fun hideSystemUI() {
        var uiFlags = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
        var uiFlags = View.SYSTEM_UI_FLAG_VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ScreenSettings.nightMode) {
                uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            if(windowRepository.visibleWindows.isNotEmpty()) {
                val color = if (setNavBarColor) {
                    val colors = windowRepository.lastVisibleWindow.pageManager.actualTextDisplaySettings.colors!!
                    val color = if (ScreenSettings.nightMode) colors.nightBackground else colors.dayBackground
                    color ?: UiUtils.bibleViewDefaultBackgroundColor
                } else {
                    val typedValue = TypedValue()
                    theme.resolveAttribute(android.R.attr.navigationBarColor, typedValue, true)
                    typedValue.data
                }
                window.navigationBarColor = color
                binding.speakTransport.setBackgroundColor(color)
            }
        }
        window.decorView.systemUiVisibility = uiFlags
    }

    private fun updateBottomBars() {
        Log.d(TAG, "updateBottomBars")
        if(isFullScreen || !transportBarVisible) {
            binding.speakTransport.animate()
                .translationY(binding.speakTransport.height.toFloat())
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { binding.speakTransport.visibility = View.GONE }
                .start()
        } else {
            binding.speakTransport.visibility = View.VISIBLE
            binding.speakTransport.animate()
                .translationY(-bottomOffset1.toFloat())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        ABEventBus.getDefault().post(UpdateRestoreWindowButtons())
    }

    class UpdateRestoreWindowButtons

    override fun onDestroy() {
        documentViewManager.removeView()
        bibleViewFactory.clear()
        super.onDestroy()
        beforeDestroy()
        ABEventBus.getDefault().unregister(this)
        _mainBibleActivity = null
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        if(menuInfo is BibleView.BibleViewContextMenuInfo) {
            menuInfo.onCreateContextMenu(menu, v, menuInflater)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        item.menuInfo.let {
            if (it is BibleView.BibleViewContextMenuInfo) {
                return it.onContextItemSelected(item)
            }
        }
        return false
    }

    /**
     * called if the app is re-entered after returning from another app.
     * Trigger redisplay in case mobile has gone from light to dark or vice-versa
     */
    override fun onRestart() {
        super.onRestart()
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
        applyTheme()
        return true
    }

    fun onEvent(event: ScreenSettings.NightModeChanged) {
        if(CurrentActivityHolder.getInstance().currentActivity == this) {
            refreshIfNightModeChange()
        }
    }

    private fun updateToolbar() {
        binding.apply {
            toolbarLayout.setPadding(leftOffset1, 0, rightOffset1, 0)
            navigationView.setPadding(leftOffset1, 0, rightOffset1, bottomOffset1)
            speakTransport.setPadding(leftOffset1, 0, rightOffset1, 0)
            if(isFullScreen) {
                hideSystemUI()
                Log.d(TAG, "Fullscreen on")
                toolbarLayout.animate().translationY(-toolbarLayout.height.toFloat())
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { toolbarLayout.visibility = View.GONE }
                    .start()
            }
            else {
                showSystemUI()
                Log.d(TAG, "Fullscreen off")
                toolbarLayout.translationY = -toolbarLayout.height.toFloat()
                toolbarLayout.visibility = View.VISIBLE
                toolbarLayout.animate().translationY(topOffset1.toFloat())
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                updateActions()
            }
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
            startActivityForResult(intent, STD_REQUEST_CODE)
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    fun afterRestore() {
        bookmarkControl.reset()
        documentViewManager.removeView()
        bibleViewFactory.clear()
        windowControl.windowSync.setResyncRequired()
        Dialogs.instance.showMsg(R.string.restore_success)
        currentWorkspaceId = 0
    }

    fun updateDocuments() {
        windowControl.windowSync.reloadAllWindows(true)
        updateActions()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "Activity result:$resultCode")
        val extras = data?.extras
        if (extras != null) {
            when (requestCode) {
                WORKSPACE_CHANGED -> {
                    val workspaceId = extras.getLong("workspaceId")
                    val changed = extras.getBoolean("changed")

                    if (resultCode == Activity.RESULT_OK) {
                        if (workspaceId != 0L && workspaceId != currentWorkspaceId) {
                            currentWorkspaceId = workspaceId
                        } else if (changed) {
                            currentWorkspaceId = currentWorkspaceId
                        }
                    }
                    return
                }
                COLORS_CHANGED -> {
                    val edited = extras.getBoolean("edited")
                    val reset = extras.getBoolean("reset")
                    val windowId = extras.getLong("windowId")
                    val colorsStr = extras.getString("colors")

                    if (!edited && !reset) return

                    val colors = if (reset)
                        if (windowId != 0L) {
                            null
                        } else TextDisplaySettings.default.colors
                    else
                        WorkspaceEntities.Colors.fromJson(colorsStr!!)

                    if (windowId != 0L) {
                        val window = windowRepository.getWindow(windowId)!!
                        window.pageManager.textDisplaySettings.colors = colors
                        window.bibleView?.updateTextDisplaySettings()
                    } else {
                        windowRepository.textDisplaySettings.colors = colors
                        windowRepository.updateWindowTextDisplaySettingsValues(
                            setOf(TextDisplaySettings.Types.COLORS),
                            windowRepository.textDisplaySettings
                        )
                        windowRepository.updateAllWindowsTextDisplaySettings()
                    }
                    resetSystemUi()
                }
                TEXT_DISPLAY_SETTINGS_CHANGED -> {
                    val edited = extras.getBoolean("edited")
                    val reset = extras.getBoolean("reset")

                    val settingsBundle = SettingsBundle.fromJson(extras.getString("settingsBundle")!!)
                    val requiresReload = extras.getBoolean("requiresReload")

                    if (!edited && !reset) return

                    val dirtyTypes = DirtyTypesSerializer.fromJson(extras.getString("dirtyTypes")!!).dirtyTypes

                    workspaceSettingsChanged(settingsBundle, requiresReload, reset, dirtyTypes)
                    return
                }
                STD_REQUEST_CODE -> {
                    val classes = arrayOf(GridChoosePassageBook::class.java.name, Bookmarks::class.java.name)
                    val className = data.component?.className
                    if (className != null && classes.contains(className)) {
                        val isFromBookmark = className == Bookmarks::class.java.name
                        val verseStr = extras.getString("verse")
                        val verse = try {
                            VerseFactory.fromString(navigationControl.versification, verseStr)
                        } catch (e: NoSuchVerseException) {
                            ABEventBus.getDefault().post(ToastEvent(getString(R.string.verse_not_found)))
                            return
                        }
                        val pageManager = windowControl.activeWindowPageManager
                        if (isFromBookmark && !pageManager.isBibleShown) {
                            pageManager.setCurrentDocumentAndKey(windowControl.defaultBibleDoc(false), verse)
                        } else
                            pageManager.currentPage.setKey(verse)
                        return
                    }
                }
            }
        }

        if (requestCode == IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH) {
            updateActions()
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
        when {
            mainMenuCommandHandler.restartIfRequiredOnReturn(requestCode) -> {
                // restart done in above
            }
            mainMenuCommandHandler.isDisplayRefreshRequired(requestCode) -> {
                preferenceSettingsChanged()
            }
        }

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        if(listOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP).contains(keyCode) && !speakControl.isSpeaking && am?.isMusicActive != true) {
            return when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN ->
                    windowControl.activeWindow.bibleView?.volumeDownPressed()?: false
                KeyEvent.KEYCODE_VOLUME_UP ->
                    windowControl.activeWindow.bibleView?.volumeUpPressed()?: false
                else -> super.onKeyDown(keyCode, event)
            }
        }

        return super.onKeyDown(keyCode, event)
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
                windowRepository.updateAllWindowsTextDisplaySettings()
            }
        }
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
        requestSdcardPermission()
        documentViewManager.buildView()
        ABEventBus.getDefault().post(SynchronizeWindowsEvent(true))
    }

    private fun requestSdcardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val requestSdCardPermission = preferences.getBoolean(REQUEST_SDCARD_PERMISSION_PREF, false)
            if (requestSdCardPermission && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), SDCARD_READ_REQUEST)
            }
        }
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        updateActions()
    }

    fun onEvent(event: NumberOfWindowsChangedEvent) {
        setSoftKeyboardMode()
    }

    private fun setSoftKeyboardMode() {
        if (windowControl.isMultiWindow) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
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
        documentViewManager.documentView.asView().requestFocus()
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

    val isSplitVertically: Boolean get() {
        val reverse = windowRepository.workspaceSettings.enableReverseSplitMode
        return if(reverse) !CommonUtils.isPortrait else CommonUtils.isPortrait
    }

    companion object {
        var _mainBibleActivity: MainBibleActivity? = null
        val mainBibleActivity get() = _mainBibleActivity!!
        var initialized = false
        private const val SDCARD_READ_REQUEST = 2

        const val TEXT_DISPLAY_SETTINGS_CHANGED = 92
        const val COLORS_CHANGED = 93
        const val WORKSPACE_CHANGED = 94

        private const val REQUEST_SDCARD_PERMISSION_PREF = "request_sdcard_permission_pref"

        private const val TAG = "MainBibleActivity"
    }
}

