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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.AlignmentSpan
import android.text.style.ImageSpan
import android.util.Log
import android.util.TypedValue
import android.view.ContextMenu
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.activity.databinding.EmptyBinding
import net.bible.android.activity.databinding.FrozenBinding
import net.bible.android.activity.databinding.MainBibleViewBinding
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
import net.bible.android.control.link.LinkControl
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.defaultWorkspaceColor
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.navigation.ChooseDictionaryWord
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKey
import net.bible.android.view.activity.page.screen.DocumentViewManager
import net.bible.android.view.activity.settings.DirtyTypesSerializer
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorActivity
import net.bible.android.view.util.UiUtils
import net.bible.android.view.util.widget.SpeakTransportWidget
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.common.betaIntroVideo
import net.bible.service.common.htmlToSpan
import net.bible.service.common.windowPinningVideo
import net.bible.service.common.newFeaturesIntroVideo
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.WorkspacesUpdatedViaSyncEvent
import net.bible.service.device.ScreenSettings
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.download.DownloadManager
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.CloudSyncEvent
import net.bible.service.cloudsync.WorkspaceRefreshRequired
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeySerialized
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.exitProcess

/** The main activity screen showing Bible text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

const val DEFAULT_SYNC_INTERVAL = 5*60L // 5 minutes

private val syncScope = CoroutineScope(Dispatchers.IO)

class SpeakTransportVisibilityChanged(val value: Boolean)

class MainBibleActivity : CustomTitlebarActivityBase() {
    lateinit var binding: MainBibleViewBinding
    lateinit var empty: EmptyBinding
    lateinit var frozenBinding: FrozenBinding

    private var mWholeAppWasInBackground = false

    // We need to have this here in order to initialize BibleContentManager early enough.
    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var bookmarkControl: BookmarkControl

    // handle requests from main menu
    @Inject lateinit var searchControl: SearchControl
    @Inject lateinit var documentControl: DocumentControl
    @Inject lateinit var navigationControl: NavigationControl
    @Inject lateinit var pageControl: PageControl
    @Inject lateinit var linkControl: LinkControl

    lateinit var documentViewManager: DocumentViewManager
    lateinit var bibleViewFactory: BibleViewFactory
    private lateinit var mainMenuCommandHandler: MenuCommandHandler

    private var navigationBarHeight = 0
    private var actionBarHeight = 0
    private var transportBarHeight = 0
    private var windowButtonHeight = 0

    private var hasHwKeys: Boolean = false

    private var transportBarVisible = false
        set(value) {
            binding.speakButton.alpha = if(value) 0.7F else 1.0F
            field = value
            ABEventBus.post(SpeakTransportVisibilityChanged(value))
        }

    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()

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

    val workspaceSettings: WorkspaceEntities.WorkspaceSettings get() = windowRepository.workspaceSettings
    override val integrateWithHistoryManager: Boolean = true

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "Creating MainBibleActivity")

        ScreenSettings.refreshNightMode()
        currentNightMode = ScreenSettings.nightMode
        super.onCreate(savedInstanceState)

        CommonUtils.prepareData()

        binding = MainBibleViewBinding.inflate(layoutInflater)
        empty = EmptyBinding.inflate(layoutInflater)
        frozenBinding = FrozenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if(BuildVariant.Appearance.isDiscrete ||
            BuildVariant.DistributionChannel.isHuawei ||
            BuildVariant.DistributionChannel.isFdroid ||
            BuildVariant.DistributionChannel.isAmazon
        ) {
            binding.navigationView.menu.findItem(R.id.rateButton).isVisible = false
        }

        CommonUtils.buildActivityComponent().inject(this)

        windowRepository = WindowRepository(lifecycleScope)
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        documentViewManager = DocumentViewManager(this)
        bibleViewFactory = BibleViewFactory(this)
        mainMenuCommandHandler = MenuCommandHandler(this)

        if(CommonUtils.isDiscrete) {
            binding.bibleButton.setImageResource(R.drawable.ic_baseline_menu_book_24)
        }

        // use context to setup backup control dirs
        BackupControl.clearBackupDir()

        resolveVariables()
        setupUi()

        // register for passage change and appToBackground events
        ABEventBus.register(this)

        setupToolbarButtons()
        setupToolbarFlingDetection()
        setSoftKeyboardMode()

        // First launched activity is not having proper night mode if we are using manual mode.
        // This hack fixes it. See also ActivityBase.fixNightMode.
        if (firstTime) {
            firstTime = false
            lifecycleScope.launch {
                delay(250)
                recreate()
            }
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            if(!initialized) {
                requestSdcardPermission()
                ErrorReportControl.checkCrash(this@MainBibleActivity)
                if(!CommonUtils.checkPoorTranslations(this@MainBibleActivity)) exitProcess(2)
                showBetaNotice()
                showStableNotice()
                showFirstTimeHelp()
                if(!CommonUtils.isDiscrete) {
                    ABEventBus.post(ToastEvent(windowRepository.name))
                }
                checkDocBackupDBInSync()
            }
            initialized = true
        }
        if(intent.hasExtra("openLink")) {
            val uri = Uri.parse(intent.getStringExtra("openLink"))
            openLink(uri)
        }
        val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connManager.registerDefaultNetworkCallback(networkCallback)
        }
    }

    val networkCallback = object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            syncScope.launch { startSync() }
        }
    }

    override fun fixNightMode() {} // handle this manually here

    private fun setupUi() {
        documentViewManager.buildView()
        windowControl.windowSync.reloadAllWindows(true)
        updateActions()
        ABEventBus.post(ConfigurationChanged(resources.configuration))
        binding.syncIcon.visibility = View.INVISIBLE
        updateToolbar()
        updateBottomBars()
        if (!CommonUtils.isCloudSyncAvailable) {
            binding.navigationView.menu.findItem(R.id.googleDriveSync).isVisible = false
        }
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawers()
            mainMenuCommandHandler.handleMenuRequest(menuItem)
        }

        var currentSliderOffset = 0.0F

        if (CommonUtils.settings.monochromeMode) {
            binding.drawerLayout.setScrimColor(Color.TRANSPARENT)
        }

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

            override fun onDrawerClosed(drawerView: View) {
                windowRepository.activeWindow.bibleView?.requestFocus()
            }
        })
    }

    private fun resolveVariables() {
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

        transportBarVisible = !speakControl.isStopped

    }

    /**
     * Checks if the list of documents installed matches the list of
     * books in the backup database.
     *
     * Backup database is used to allow user to quickly reinstall all
     * available books if moving to a new device.
     */
    private fun checkDocBackupDBInSync() {
        val docs = SwordDocumentFacade.documents
        val knownInstalled = docDao.getKnownInstalled()
        if (knownInstalled.isEmpty()) {
            Log.i(TAG, "There is at least one Bible, but Bible Backup DB is empty, populate with first time books");
            val allDocs = docs.map {
                SwordDocumentInfo(it.initials, it.name, it.abbreviation, it.language.name, it.getProperty(DownloadManager.REPOSITORY_KEY) ?: "")
            }
            docDao.insert(allDocs)
        } else {
            knownInstalled.map {
                Log.i(TAG, "The ${it.name} is installed")
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
                
                val spanned = htmlToSpan(pinningText)

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
        Log.i(TAG, "showStableNotice: $displayedVer $ver")

        if(displayedVer != ver) {
            val videoMessage = getString(R.string.upgrade_video_message, CommonUtils.mainVersion)
            val appName = getString(R.string.app_name_long)
            val par1 = getString(R.string.stable_notice_par1, CommonUtils.mainVersion, appName)
            val buy = getString(R.string.buy_development)
            val support = getString(R.string.buy_development2)
            val heartIcon = ImageSpan(CommonUtils.getTintedDrawable(R.drawable.baseline_attach_money_24))
            val biggerLogoDrawable = CommonUtils.getResourceDrawable(R.drawable.ic_logo, this)!!
            biggerLogoDrawable.setBounds(0, 0, biggerLogoDrawable.intrinsicWidth*2, biggerLogoDrawable.intrinsicHeight*2)
            val logoSpan = ImageSpan(biggerLogoDrawable)
            val centerSpan = AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER)
            val imageStr = SpannableString("*")
            val iconStr = SpannableString("*")
            iconStr.setSpan(heartIcon, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            imageStr.setSpan(logoSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            imageStr.setSpan(centerSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

            val spanned = TextUtils.concat(
                htmlToSpan("$par1<br><br>"),
                if(BuildVariant.Appearance.isDiscrete) "" else imageStr,
                htmlToSpan("<br><br><big><a href=\"$newFeaturesIntroVideo\"><b>$videoMessage</b></a></big>"),
                htmlToSpan("<br><br>"),
                iconStr,
                htmlToSpan("&nbsp;<small><a href=\"$buyDevelopmentLink\">$support ($buy)</a></small>")
            )

            val d = AlertDialog.Builder(this)
                .setTitle(getString(R.string.stable_notice_title))
                .setMessage(spanned)
                .setIcon(R.drawable.ic_logo)
                .setNeutralButton(getString(R.string.beta_notice_dismiss)) { _, _ -> it.resume(false)}
                .setPositiveButton(getString(R.string.beta_notice_dismiss_until_update)) { _, _ ->
                    Log.i(TAG, "showStableNotice: saving $ver")
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

        val announceVersion = 2
        val displayedVer = preferences.getInt("beta-notice-displayed2", 0)

        if(displayedVer < announceVersion) {
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
            val extraMessage = ""
            //""""
            //    |<b>DEVELOPER'S SPECIAL NOTICE FOR BETA TESTERS (6th Oct 2023)</b><br><br>
            //    |Stable release is approaching. If everything goes as planned
            //    |(especially if there are no important UI translations lacking)
            //    |we will release 5.0 to stable channels around 2nd November, 2023. <br>
            //    |<br>
            //    |Please test new features and report any bugs (crashes or misbehaviors) you find
            //    |using either Main Menu -> Report a bug or via <a href="https://github.com/AndBible/and-bible/issues/new/choose">Github</a>.
            //    |<br>
            //    |<br>
            //    | Best regards, Tuomas<br><br>
            //    | P.S. You can now support AndBible development financially by <a href="$buyDevelopmentLink">buying development hours</a>.
            //    | <br><br>
            //    | (Standard beta notice below)
            //    | <br><br>
            //""".trimMargin()
            val htmlMessage = "$extraMessage$videoMessageLink<br><br>$par1<br><br> $par2<br><br> $par3 <br><br> <i>${getString(R.string.version_text, CommonUtils.applicationVersionName)}</i>"

            val spanned = htmlToSpan(htmlMessage)

            val d = AlertDialog.Builder(this)
                .setTitle(getString(R.string.beta_notice_title))
                .setMessage(spanned)
                .setIcon(R.drawable.ic_logo)
                .setNeutralButton(getString(R.string.beta_notice_dismiss)) { _, _ -> it.resume(false)}
                .setPositiveButton(getString(R.string.beta_notice_dismiss_until_update)) { _, _ ->
                    preferences.setInt("beta-notice-displayed2", announceVersion)
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
        var minScaledVelocity = ViewConfiguration.get(this).scaledMinimumFlingVelocity
        minScaledVelocity = (minScaledVelocity * 0.66).toInt()

        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                Log.i(TAG, "onFling")
                e1 ?: return false
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

            override fun onLongPress(e: MotionEvent) {
                startActivityForResult(Intent(this@MainBibleActivity, ChooseDocument::class.java), STD_REQUEST_CODE)
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
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

    private var lastBackPressed: Long? = null

    override fun onBackPressed() {
        Log.i(TAG, "onBackPressed $fullScreen")
        if(fullScreen) {
            toggleFullScreen()
            return
        }
        val lastBackPressed = lastBackPressed
        if (binding.drawerLayout.isDrawerVisible(GravityCompat.START)) {
            binding.drawerLayout.closeDrawers()
        } else {
            if (!documentViewManager.documentView.backButtonPressed() && !historyTraversal.goBack()) {
                if(lastBackPressed == null || lastBackPressed < now - 1000) {
                    this.lastBackPressed = now
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
            Log.i(TAG, "Back Long")
            // a long press of the back key. do our work, returning true to consume it.  by returning true, the framework knows an action has
            // been performed on the long press, so will set the cancelled flag for the following up event.
            val intent = Intent(this, History::class.java)
            startActivityForResult(intent, STD_REQUEST_CODE)
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
                val intent = Intent(this@MainBibleActivity, BibleSpeakActivity::class.java)
                startActivityForResult(intent, STD_REQUEST_CODE)
                true
            }
            searchButton.setOnClickListener {
                searchControl.getSearchIntent(documentControl.currentDocument, this@MainBibleActivity)?.let { intent ->
                    startActivityForResult(intent, STD_REQUEST_CODE)
                }
            }
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
    lateinit var windowRepository: WindowRepository

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

            preferences.setString("current_workspace_id", windowRepository.id.toString())
            documentViewManager.buildView(forceUpdate = true)
            windowControl.windowSync.reloadAllWindows()
            windowRepository.updateAllWindowsTextDisplaySettings()

            ABEventBus.post(ToastEvent(windowRepository.name))

            updateBottomBars()
            updateTitle()
        }

    private fun getItemOptions(itemId: Int, order: Int = 0): OptionsMenuItemInterface {
        val settingsBundle = SettingsBundle(
            workspaceId = windowRepository.id,
            workspaceName = windowRepository.name,
            workspaceSettings = windowRepository.textDisplaySettings.apply {
                colors?.workspaceColor = windowRepository.workspaceSettings.workspaceColor
            },
        )
        return when(itemId) {
            R.id.allTextOptions -> CommandPreference(launch = { _, _, _ ->
                val intent = Intent(this, TextDisplaySettingsActivity::class.java)
                intent.putExtra("settingsBundle", settingsBundle.toJson())
                startActivityForResult(intent, TEXT_DISPLAY_SETTINGS_CHANGED)
            }, opensDialog = true)
            R.id.autoAssignLabels -> AutoAssignPreference(windowRepository.workspaceSettings)
            R.id.textOptionsSubMenu -> SubMenuPreference(false)
            R.id.textOptionItem -> getPrefItem(settingsBundle, CommonUtils.lastDisplaySettingsSorted[order])
            R.id.splitMode -> SplitModePreference(this)
            R.id.autoPinMode -> WindowPinningPreference()
            R.id.tiltToScroll -> TiltToScrollPreference(this)
            R.id.nightMode -> NightModePreference(this)
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
                    handleMenu(item.subMenu!!)
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
            synchronized(BookName::class.java) {
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
                synchronized(BookName::class.java) {
                    val oldValue = BookName.isFullBookName()
                    BookName.setFullBookName(false)
                    binding.pageTitle.text = pageTitleText
                    BookName.setFullBookName(oldValue)
                }
            }
        } catch (_: KeyIsNull) {}
        binding.documentTitle.text = documentTitleText
        updateStrongsButton()
    }

    private fun updateStrongsButton() {
        if(documentControl.isNewTestament) {
            when (dummyStrongsPrefOption.value) {
                0 -> binding.strongsButton.setImageResource(R.drawable.ic_strongs_greek)
                1 -> binding.strongsButton.setImageResource(R.drawable.ic_strongs_greek_links)
                2 -> binding.strongsButton.setImageResource(R.drawable.ic_strongs_greek_links_text)
                3 -> binding.strongsButton.setImageResource(R.drawable.ic_strongs_greek)
            }
        } else {
            when (dummyStrongsPrefOption.value) {
                0 -> binding.strongsButton.setImageResource(R.drawable.ic_strongs_hebrew)
                1 -> binding.strongsButton.setImageResource(R.drawable.ic_strongs_hebrew_links)
                2 -> binding.strongsButton.setImageResource(R.drawable.ic_strongs_hebrew_links_text)
                3 -> binding.strongsButton.setImageResource(R.drawable.ic_strongs_hebrew)
            }
        }
        if(dummyStrongsPrefOption.value == 0) {
            val alpha = if(CommonUtils.settings.disableAnimations) 0.8F else 0.5F
            binding.strongsButton.alpha = alpha
        } else
            binding.strongsButton.alpha = 1.0F
    }

    private val currentDocument get() = windowControl.activeWindow.pageManager.currentPage.currentDocument
    private val toolbarButtonSetting get() = preferences.getString("toolbar_button_actions", "default")

    override fun updateActions() {
        updateTitle()
        val biblesForVerse = documentControl.biblesForVerse
        val commentariesForVerse = documentControl.commentariesForVerse

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
                menuForDocs(view, commentariesForVerse + SwordDocumentFacade.getBooks(BookCategory.GENERAL_BOOK))
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
                Pair(speakLastUsed) { addSpeak() },
                Pair(searchLastUsed) { addSearch() },
            )
            funs.sortBy { -it.first }

            for(p in funs) {
                p.second()
            }

            workspaceButton.visibility = if (visibleButtonCount < maxButtons)
            {
                workspaceButton.setOnClickListener {
                    val intent = Intent(this@MainBibleActivity, WorkspaceSelectorActivity::class.java)
                    startActivityForResult(intent, WORKSPACE_CHANGED)
                }
                visibleButtonCount += 1
                View.VISIBLE
            } else View.GONE

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
        startActivityForResult(intent, STD_REQUEST_CODE)
    }

    fun onEventMainThread(passageEvent: CurrentVerseChangedEvent) {
        if(paused) return
        updateTitle()
    }

    fun onEventMainThread(event: CloudSyncEvent) {
        binding.syncIcon.visibility = if(event.running) View.VISIBLE else View.INVISIBLE
    }

    private fun openLink(uri: Uri) {
        when (uri.host) {
            "read.andbible.org" -> {
                val key = CommonUtils.parseAndBibleReference(uri) ?: return
                windowControl.showLink(key.document, key)
            }
            "stepbible.org" -> {
                val qParam = uri.getQueryParameter("q") ?: return

                val docRegex = Regex("""version=([^&|]+)""")
                val refRegex = Regex("""reference=([^&|]+)""")

                val versionMatch = docRegex.find(qParam)
                val version = if (versionMatch != null) versionMatch.groups[1]?.value else null
                val doc = if (version != null) Books.installed().getBook(version) else null

                val defV11n = if (doc is SwordBook) doc.versification else KJVA
                val v11nStr = uri.getQueryParameter("v11n")
                val v11n = if (v11nStr == null) defV11n else Versifications.instance().getVersification(v11nStr) ?: defV11n

                val refMatch = refRegex.find(qParam) ?: return
                val keyStr = refMatch.groups[1]?.value ?: return

                val key = PassageKeyFactory.instance().getKey(v11n, keyStr)
                windowControl.showLink(doc, key)
            }
            "www.bible.com" -> {
                val urlRegex = Regex("""/(\w+)/bible/(\w+)/([\w\d]+)\.(\d+)\.(\w+)""")
                val match = urlRegex.find(uri.path.toString()) ?: return
                val book = match.groups[3]?.value ?: return
                val chapter = match.groups[4]?.value?.toInt() ?: return
                val docStr = match.groups[5]?.value
                val doc = if (docStr != null) Books.installed().getBook(docStr) else null
                val defV11n = if (doc is SwordBook) doc.versification else KJVA

                val key = VerseFactory.fromString(defV11n, "$book.$chapter")
                windowControl.showLink(doc, key)
            }
        }
    }

    fun onEventMainThread(speakEvent: SpeakEvent) {
        if(!speakEvent.isTemporarilyStopped) {
            updateBottomBars()
        }
        updateActions()
    }

    private fun menuForDocs(v: View, documents: List<Book>) {
        val menu = PopupMenu(this, v)
        val docs = documents.sortedWith(compareBy({it.language.code}, {it.abbreviation}))
        docs.forEachIndexed { i, book ->
            val item = menu.menu.add(Menu.NONE, i, Menu.NONE, getString(R.string.something_with_parenthesis, book.abbreviation, book.language.code))
            if(currentDocument == book) {
                item.isEnabled = false
            }
        }

        if (docs.size == 2) {
            setCurrentDocument(docs.first { it != currentDocument })
        } else {
            menu.setOnMenuItemClickListener { item ->
                setCurrentDocument(docs[item.itemId])
                true
            }
            menu.show()
        }
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
        ABEventBus.post(FullScreenEvent(isFullScreen))
        updateToolbar()
        updateBottomBars()
        if(isFullScreen) {
            ABEventBus.post(ToastEvent(R.string.exit_fullscreen))
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
        val monochromeMode = CommonUtils.settings.monochromeMode
        var uiFlags = View.SYSTEM_UI_FLAG_VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ScreenSettings.nightMode) {
                uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            if(windowRepository.visibleWindows.isNotEmpty()) {
                val colors = TextDisplaySettings.actual(null, windowRepository.textDisplaySettings).colors!!

                val toolbarColor = if (ScreenSettings.nightMode)
                    resources.getColor(R.color.actionbar_background_night, theme)
                else if (monochromeMode) {
                    Color.BLACK
                } else {
                    workspaceSettings.workspaceColor ?: defaultWorkspaceColor
                }

                binding.run {
                    homeButton.setBackgroundColor(toolbarColor)
                    pageTitle.setBackgroundColor(toolbarColor)
                    syncIcon.setBackgroundColor(toolbarColor)
                    documentTitle.setBackgroundColor(toolbarColor)
                    toolbarButtonLayout.setBackgroundColor(toolbarColor)
                }

                if (ScreenSettings.nightMode){
                    binding.homeButton.drawable.setTint(workspaceSettings.workspaceColor ?: defaultWorkspaceColor)
                }

                val color = if (setNavBarColor && !monochromeMode) {
                    val color = if (ScreenSettings.nightMode) colors.nightBackground else colors.dayBackground
                    color ?: UiUtils.bibleViewDefaultBackgroundColor
                } else {
                    val typedValue = TypedValue()
                    theme.resolveAttribute(android.R.attr.navigationBarColor, typedValue, true)
                    typedValue.data
                }

                // Set the status bar to the same color
                window.run {
                    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    statusBarColor = toolbarColor
                    navigationBarColor = color
                }

                binding.speakTransport.setBackgroundColor(color)
            }
        }
        window.decorView.systemUiVisibility = uiFlags
    }

    private fun updateBottomBars() {
        Log.i(TAG, "updateBottomBars")
        if(isFullScreen || !transportBarVisible) {
            binding.speakTransport.animate()
                .translationY(binding.speakTransport.height.toFloat())
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { binding.speakTransport.visibility = View.GONE }
                .apply {
                    if(CommonUtils.settings.disableAnimations) {
                        duration = 0
                    }
                }
                .start()
        } else {
            binding.speakTransport.visibility = View.VISIBLE
            binding.speakTransport.animate()
                .translationY(-bottomOffset1.toFloat())
                .setInterpolator(DecelerateInterpolator())
                .apply {
                    if(CommonUtils.settings.disableAnimations) {
                        duration = 0
                    }
                }
                .start()
        }
        ABEventBus.post(UpdateRestoreWindowButtons())
    }

    class UpdateRestoreWindowButtons

    override fun onDestroy() {
        documentViewManager.removeView()
        bibleViewFactory.clear()
        super.onDestroy()
        beforeDestroy()
        ABEventBus.unregister(this)
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
     * called if the activity is re-entered.
     * Trigger redisplay in case mobile has gone from light to dark or vice-versa
     */
    override fun onRestart() {
        super.onRestart()
        lifecycleScope.launch {
            if (mWholeAppWasInBackground) {
                mWholeAppWasInBackground = false
                refreshIfNightModeChange()
            }
        }
    }

    private var syncJob: Job? = null

    private suspend fun startSync() {
        if(CommonUtils.isCloudSyncEnabled) {
            synchronize(true)
            if(syncJob != null) {
                Log.e(TAG, "syncJob already exists")
            } else {
                syncJob = lifecycleScope.launch { periodicSync() }
            }
        }
    }

    private suspend fun periodicSync() {
        Log.i(TAG, "Periodic sync starting")
        while (CommonUtils.isCloudSyncEnabled && syncJob?.isCancelled == false) {
            delay(60*1000) // 1 minute
            if(syncJob?.isCancelled == false) synchronize()
        }
    }

    private val lastTouched: Long get() {
        return windowRepository.windowList.mapNotNull { it.bibleView?.lastTouched }.max()
    }

    private val syncInterval get() =
        CommonUtils.settings.getLong("gdrive_sync_interval", DEFAULT_SYNC_INTERVAL) * 1000
    private val lastSynchronized get() =
        CommonUtils.settings.getLong("globalLastSynchronized", 0L)

    private val now get() = System.currentTimeMillis()

    private suspend fun synchronize(force: Boolean = false) {
        if(CommonUtils.isCloudSyncEnabled) {
            windowRepository.saveIntoDb(false)
            if (force || (now - max(lastSynchronized, lastTouched) > syncInterval && CloudSync.hasChanges())) {
                Log.i(TAG, "Performing periodic sync")
                if(!CloudSync.signedIn) {
                    CloudSync.signIn(this@MainBibleActivity)
                }
                CloudSync.start()
                CloudSync.waitUntilFinished()
            }
        }
    }

    fun onEvent(event: CloudSyncEvent) {
        if (!event.running) {
            CommonUtils.settings.setLong("globalLastSynchronized", now)
        }
    }

    private fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun onEvent(event: AppToBackgroundEvent) {
        if (event.isMovedToBackground) {
            mWholeAppWasInBackground = true
            stopPeriodicSync()
            syncScope.launch { synchronize(true) }
        } else {
            updateActions()
            syncScope.launch { startSync() }
        }
    }

    fun onEventMainThread(event: WorkspacesUpdatedViaSyncEvent) {
        val entries = event.updated
        val workspaceDeleted = entries.any {
            it.tableName == "Workspace" &&
            it.type == LogEntryTypes.DELETE &&
            it.entityId1 == currentWorkspaceId
        }
        if(workspaceDeleted) {
            currentWorkspaceId = workspaces.first().id
        }

        val windowsChanged = entries.any { entry ->
            entry.tableName in listOf("Window", "PageManager") &&
            windowRepository.windowList.firstOrNull { it.id == entry.entityId1 } != null
        }

        val workspaceChanged = entries.any {
            it.tableName == "Workspace" &&
            it.type == LogEntryTypes.UPSERT &&
            it.entityId1 == currentWorkspaceId
        }
        if(windowsChanged || workspaceChanged) {
            currentWorkspaceId = currentWorkspaceId
        }
    }

    fun onEventMainThread(event: WorkspaceRefreshRequired) {
        currentWorkspaceId = workspaces.first().id
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
    }

    fun refreshIfNightModeChange(): Boolean {
        // colour may need to change which affects View colour and html
        // first refresh the night mode setting using light meter if appropriate
        ScreenSettings.checkMonitoring()
        applyTheme()
        return true
    }

    fun onEvent(event: ScreenSettings.NightModeChanged) {
        if(paused) return
        if(CurrentActivityHolder.currentActivity == this) {
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
                Log.i(TAG, "Fullscreen on")
                toolbarLayout.animate().translationY(-toolbarLayout.height.toFloat())
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction { toolbarLayout.visibility = View.GONE }
                    .apply {
                        if(CommonUtils.settings.disableAnimations) {
                            duration = 0
                        }
                    }.start()
            }
            else {
                showSystemUI()
                Log.i(TAG, "Fullscreen off")
                toolbarLayout.translationY = -toolbarLayout.height.toFloat()
                toolbarLayout.visibility = View.VISIBLE
                toolbarLayout.animate().translationY(topOffset1.toFloat())
                    .setInterpolator(DecelerateInterpolator())
                    .apply {
                        if(CommonUtils.settings.disableAnimations) {
                            duration = 0
                        }
                    }.start()
                updateActions()
            }
        }
    }

    class ConfigurationChanged(val configuration: Configuration)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "Configuration changed")

        refreshIfNightModeChange()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Log.i(TAG, "Keycode:$keyCode")
        // common key handling i.e. KEYCODE_DPAD_RIGHT & KEYCODE_DPAD_LEFT
        //if (bibleKeyHandler.onKeyUp(keyCode, event)) {
        //    return true
        if (keyCode == KeyEvent.KEYCODE_SEARCH && windowControl.activeWindowPageManager.currentPage.isSearchable) {
            searchControl.getSearchIntent(windowControl.activeWindowPageManager.currentPage.currentDocument, this)?.let { intent ->
                startActivityForResult(intent, STD_REQUEST_CODE)
            }
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    class MainBibleAfterRestore()

    fun onEventMainThread(e: MainBibleAfterRestore) {
        bookmarkControl.reset()
        documentViewManager.removeView()
        bibleViewFactory.clear()
        windowControl.windowSync.setResyncRequired()
        currentWorkspaceId = IdType.empty()
    }

    class UpdateMainBibleActivityDocuments

    private var updateDocumentsPending = false

    fun onEvent(e: UpdateMainBibleActivityDocuments) {
        updateDocumentsPending = true
    }

    private fun updateDocuments() {
        windowControl.windowSync.reloadAllWindows(true)
        updateActions()
        updateDocumentsPending = false
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "Activity result:$resultCode")
        val extras = data?.extras
        if (extras != null) {
            when (requestCode) {
                WORKSPACE_CHANGED -> {
                    val workspaceId = extras.getString("workspaceId")
                    val changed = extras.getBoolean("changed")

                    if (resultCode == Activity.RESULT_OK) {
                        if (workspaceId != null && IdType(workspaceId) != currentWorkspaceId) {
                            currentWorkspaceId = IdType(workspaceId)
                        } else if (changed) {
                            currentWorkspaceId = currentWorkspaceId
                        }
                    }
                    return
                }
                COLORS_CHANGED -> {
                    val edited = extras.getBoolean("edited")
                    val reset = extras.getBoolean("reset")
                    val windowId = extras.getString("windowId")
                    val colorsStr = extras.getString("colors")

                    if (!edited && !reset) return

                    val colors = if (reset)
                        if (windowId != null) {
                            null
                        } else TextDisplaySettings.default.colors
                    else
                        WorkspaceEntities.Colors.fromJson(colorsStr!!)

                    if (windowId != null) {
                        val window = windowRepository.getWindow(IdType(windowId))!!
                        window.pageManager.textDisplaySettings.colors = colors
                        window.bibleView?.updateTextDisplaySettings()
                    } else {
                        windowRepository.textDisplaySettings.colors = colors
                        windowRepository.updateWindowTextDisplaySettingsValues(
                            setOf(TextDisplaySettings.Types.COLORS),
                            windowRepository.textDisplaySettings
                        )
                        if(reset) {
                            windowRepository.workspaceSettings.workspaceColor = defaultWorkspaceColor
                        } else {
                            windowRepository.workspaceSettings.workspaceColor = colors!!.workspaceColor
                        }
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
                    CurrentActivityHolder.activate(this) // needed because startKeyChooser is using this
                    val classes = arrayOf(
                        GridChoosePassageBook::class.java.name,
                        Bookmarks::class.java.name
                    )
                    val genBookClasses = arrayOf(
                        ChooseGeneralBookKey::class.java.name,
                        ChooseDictionaryWord::class.java.name,
                        ChooseMapKey::class.java.name,
                    )
                    when(val className = data.component?.className) {
                        null -> {}
                        ChooseDocument::class.java.name -> {
                            val bookStr = extras.getString("book")
                            val book = Books.installed().getBook(bookStr) ?: FakeBookFactory.pseudoDocuments.first { it.initials == bookStr }
                            documentControl.changeDocument(book)
                            updateActions()
                            return
                        }
                        in classes -> {
                            val isFromBookmark = className == Bookmarks::class.java.name
                            val verseStr = extras.getString("verse")
                            val keyStr = extras.getString("key")
                            val bookStr = extras.getString("book")
                            if(verseStr != null) {
                                val verse = try {
                                    VerseFactory.fromString(navigationControl.versification, verseStr)
                                } catch (e: NoSuchVerseException) {
                                    ABEventBus.post(ToastEvent(getString(R.string.verse_not_found)))
                                    return
                                }
                                val pageManager = windowControl.activeWindowPageManager
                                if (isFromBookmark && !pageManager.isBibleShown) {
                                    pageManager.setCurrentDocumentAndKey(windowControl.defaultBibleDoc(false), verse)
                                } else
                                    pageManager.currentPage.setKey(verse, !isFromBookmark)
                            } else if (keyStr != null && bookStr != null){
                                val book = Books.installed().getBook(bookStr)
                                val key = book.getKey(keyStr)
                                val pageManager = windowControl.activeWindowPageManager
                                val ordinal = extras.getInt("ordinal")
                                pageManager.setCurrentDocumentAndKey(book, BookAndKey(key, book, OrdinalRange(ordinal)))
                            }
                            return
                        }
                        in genBookClasses -> {
                            val keyStr = extras.getString("key")
                            val bookStr = extras.getString("book")
                            val bookAndKeyStr = extras.getString("bookAndKey")
                            if(bookAndKeyStr != null) {
                                val bookAndKey = BookAndKeySerialized.fromJSON(bookAndKeyStr).bookAndKey
                                val pageManager = windowControl.activeWindowPageManager
                                pageManager.setCurrentDocumentAndKey(bookAndKey.document, bookAndKey)
                            } else {
                                val book = Books.installed().getBook(bookStr)
                                val key = book.getKey(keyStr)
                                windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
                            }
                            return
                        }
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

        val isExternal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            InputDevice.getDevice(event.deviceId)?.isExternal ?: false
        } else {
            false
        }

        if(keyCode == KeyEvent.KEYCODE_BACK && (event.source and InputDevice.SOURCE_KEYBOARD) != 0 && isExternal) {
            if (binding.drawerLayout.isDrawerVisible(GravityCompat.START)) {
                binding.drawerLayout.closeDrawers()
            }
            return true
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
                window.loadText()
            else {
                window.bibleView?.updateTextDisplaySettings()
            }
        } else {
            if(reset) {
                windowRepository.textDisplaySettings = TextDisplaySettings.default
                windowRepository.workspaceSettings.workspaceColor = defaultWorkspaceColor
            } else {
                windowRepository.textDisplaySettings = settingsBundle.workspaceSettings
                windowRepository.workspaceSettings.workspaceColor = settingsBundle.workspaceSettings.colors?.workspaceColor?: defaultWorkspaceColor
            }
            if(dirtyTypes != null) {
                windowRepository.updateWindowTextDisplaySettingsValues(dirtyTypes, settingsBundle.workspaceSettings)
            }
            if(requiresReload) {
                ABEventBus.post(SynchronizeWindowsEvent(true))
            } else {
                windowRepository.updateAllWindowsTextDisplaySettings()
            }
        }
        resetSystemUi()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.i(TAG, "onRequestPermissionResult $requestCode")
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
        documentViewManager.removeView()
        documentViewManager.buildView()
        ABEventBus.post(SynchronizeWindowsEvent(true))
        CommonUtils.changeAppIconAndName()
    }

    private fun requestSdcardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val requestSdCardPermission = preferences.getBoolean(REQUEST_SDCARD_PERMISSION_PREF, false)
            if (requestSdCardPermission && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), SDCARD_READ_REQUEST)
            }
        }
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        if(paused) return
        updateActions()
    }

    fun onEvent(event: NumberOfWindowsChangedEvent) {
        if(paused) return
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
        if(paused) return
        updateActions()
   }

    private var paused = false
    override fun onPause() {
        windowControl.windowRepository.saveIntoDb(false)
        paused = true
        fullScreen = false
        if(CommonUtils.showCalculator) {
            (window.decorView as ViewGroup).removeView(binding.root)
            super.setContentView(empty.root)
        }
        super.onPause()
    }

    override fun onResume() {
        paused = false
        var needRefresh = false
        if(windowControl.windowRepository != windowRepository) {
            windowControl.windowRepository = windowRepository
            needRefresh = true
        }
        super.onResume()
        if(CommonUtils.showCalculator && empty.root.parent != null) {
            (window.decorView as ViewGroup).removeView(empty.root)
            super.setContentView(binding.root)
        }
        if(needRefresh) {
            currentWorkspaceId = currentWorkspaceId // will reload workspace from db
        } else if(updateDocumentsPending) {
            updateDocuments()
        }
        // allow webView to start monitoring tilt by setting focus which causes tilt-scroll to resume
        documentViewManager.documentView.asView().requestFocus()
    }

    private var frozen = false

    override fun freeze() {
        if(CurrentActivityHolder.mainBibleActivities < 2) return
        if(!frozen) {
            ABEventBus.unregister(this)
            (window.decorView as ViewGroup).removeView(binding.root)
            super.setContentView(frozenBinding.root)
        }
        frozen = true
    }

    override fun unFreeze() {
        if(frozen) {
            windowControl.windowRepository = windowRepository
            ABEventBus.register(this)
            (window.decorView as ViewGroup).removeView(frozenBinding.root)
            super.setContentView(binding.root)
        }
        frozen = false
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

    fun activate(v: View) {
        CurrentActivityHolder.activate(this)
    }

    override fun onStart() {
        super.onStart()
        CommonUtils.onyxSupport?.setupOnyxNormal()
    }

    companion object {
        var initialized = false
        private const val SDCARD_READ_REQUEST = 2

        const val TEXT_DISPLAY_SETTINGS_CHANGED = 92
        const val COLORS_CHANGED = 93
        const val WORKSPACE_CHANGED = 94

        private const val REQUEST_SDCARD_PERMISSION_PREF = "request_sdcard_permission_pref"
    }
}

