/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.graphics.Insets
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
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.android.common.toV11n
import net.bible.android.activity.R
import net.bible.android.activity.databinding.EmptyBinding
import net.bible.android.activity.databinding.FrozenBinding
import net.bible.android.activity.databinding.MainBibleViewBinding
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.control.event.onMain
import net.bible.android.control.progress.ActiveCycleChangedEvent
import net.bible.android.control.progress.ProgressControl
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
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.ai.LlmDialogHelper
import net.bible.android.view.activity.download.imageResource
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.android.view.activity.page.screen.DocumentViewManager
import net.bible.android.view.activity.page.screen.clipboardKey
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.android.view.util.UiUtils
import net.bible.android.view.util.widget.AgentLogVisibilityChanged
import net.bible.android.view.util.widget.HideTransportEvent
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.common.betaIntroVideo
import net.bible.service.common.htmlToSpan
import net.bible.service.common.windowPinningVideo
import net.bible.service.common.newFeaturesIntroVideo
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.WorkspacesUpdatedViaSyncEvent
import net.bible.service.db.exportStudyPads
import net.bible.service.device.ScreenSettings
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.download.DownloadManager
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.CloudSyncEvent
import net.bible.service.cloudsync.WorkspaceRefreshRequired
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.PendingAgentResult
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.isStudyPad
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeySerialized
import net.bible.service.sword.StudyPadKey
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.reading.KeyChooserRoute
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.QuickDocAction
import net.bible.sharedcore.reading.QuickDocMenuItem
import net.bible.sharedcore.reading.QuickDocPicker
import net.bible.sharedcore.reading.QuickDocRow
import net.bible.sharedcore.reading.shouldRestorePaneFocusOnDrawerClose
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.textSettingEditorPageFor
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedui.docCategoryOf
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import org.koin.android.ext.android.inject

/** The main activity screen showing Bible text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

const val DEFAULT_SYNC_INTERVAL = 5*60L // 5 minutes

// Bump this when introducing additional sync targets to re-trigger the
// "new sync targets available" notice for users who already dismissed it.
private const val NEW_SYNC_TARGETS_ANNOUNCE_VERSION = 1

private val syncScope = CoroutineScope(Dispatchers.IO)

class SpeakTransportVisibilityChanged(val value: Boolean)

class MainBibleActivity : CustomTitlebarActivityBase() {
    lateinit var binding: MainBibleViewBinding
    lateinit var empty: EmptyBinding
    lateinit var frozenBinding: FrozenBinding

    private var mWholeAppWasInBackground = false

    // We need to have this here in order to initialize BibleContentManager early enough.
    val windowControl: WindowControl by inject()
    val speakControl: SpeakControl by inject()
    val bookmarkControl: BookmarkControl by inject()

    // handle requests from main menu
    val searchControl: SearchControl by inject()
    val documentControl: DocumentControl by inject()
    val navigationControl: NavigationControl by inject()
    val pageControl: PageControl by inject()
    val linkControl: LinkControl by inject()

    lateinit var documentViewManager: DocumentViewManager
    lateinit var bibleViewFactory: BibleViewFactory
    /** Assigned unconditionally in [setupUi]; stays a nullable `var` because external readers
     * reach it through `as? MainBibleActivity` and are legitimately null for any other foreground
     * activity. Lets [DocumentViewManager.buildView] mirror classic's forced recreate (see its
     * kdoc). */
    var composeReadingViewHost: ComposeReadingViewHost? = null
    private lateinit var mainMenuCommandHandler: MenuCommandHandler

    // Registered eagerly (constructor-time property, mirroring TextDisplaySettingsComposeActivity's
    // own photoPicker/pendingPick) so it's ready well before RESUMED, whichever reading-view sheet
    // is showing — Settings editor sheets T10, the reading view's in-place text-settings editor.
    private var pendingBackgroundImagePick: CancellableContinuation<String?>? = null
    private val backgroundImagePicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            pendingBackgroundImagePick?.resume(uri?.toString())
            pendingBackgroundImagePick = null
        }

    /** This Activity's own image picker for the in-place colours editor
     * ([composeReadingViewHost]'s [net.bible.sharedcore.settings.ColorSettingsController]). Each
     * host owns its own -- the service takes it as a PARAMETER precisely so two hosts cannot
     * clobber one another; see [net.bible.sharedcore.settings.TextDisplaySettingsService
     * .importBackgroundImage]'s kdoc for why that matters, and
     * [net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity]'s own
     * `imagePicker` for the twin of this property. */
    internal val textSettingsImagePicker: suspend () -> String? = {
        suspendCancellableCoroutine { cont ->
            pendingBackgroundImagePick = cont
            backgroundImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            cont.invokeOnCancellation { pendingBackgroundImagePick = null }
        }
    }

    val llmDialogHelper = LlmDialogHelper(this)

    private val navigationView: NavigationView by lazy {
        binding.drawerLayout.findViewById(R.id.navigationView)!!
    }

    private val versionTextView: TextView by lazy {
        binding.drawerLayout.findViewById(R.id.versionText)!!
    }

    private var navigationBarHeight = 0
    private var actionBarHeight = 0
    private var transportBarHeight = 0
    private var windowButtonHeight = 0

    private var hasHwKeys: Boolean = false

    private var transportBarVisible = false
        get() = if (isFullScreen) false else field
        set(value) {
            if (field == value) return
            binding.speakButton.alpha = if(value) 0.7F else 1.0F
            field = value
            ABEventBus.post(SpeakTransportVisibilityChanged(value))
        }

    // Agent log widget visibility and height for offset calculation
    private var agentLogVisible = false
    private var agentLogHeight = 0

    // F6 Task 8b Step 3: the Compose reading-view search sheet's (visible, measured-height-in-px)
    // pair, fed by ComposeReadingViewHost.install() — see updateSearchSheetOffsets. Mirrors
    // agentLogVisible/agentLogHeight above (Compose-only; always false/0 before the host is installed).
    private var searchSheetVisible = false
    private var searchSheetHeight = 0

    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()

    val multiWinMode
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInMultiWindowMode else false

    // Top offset with only statusbar and toolbar
    val topOffset2 = 0

    // Offsets with system insets only - will be updated by setupEdgeToEdge()
    private var bottomOffset1 = 0
    private var bottomOffset1WithoutIme = 0  // Always excludes IME (keyboard) height

    // Bottom offset with navigation bar, transport bar and agent log.
    // The term is dropped when the mainBibleView padding is handling the keyboard, and is the
    // IME-FREE offset otherwise — `bottomOffset1` includes the keyboard height while it is up, so
    // using it here would reserve the keyboard's space twice (see `imePaddingApplied`).
    val bottomOffset2 get() = (if (imePaddingApplied) 0 else bottomOffset1WithoutIme) +
        (if (transportBarVisible) transportBarHeight else 0) +
        (if (agentLogVisible) agentLogHeight else 0)

    // WebView bottom offset: navigation bar + transport + buttons + agent log + search sheet.
    // Same three cases as above: padding applied -> 0; no keyboard -> system bars; keyboard up with
    // the padding suppressed -> system bars, because the keyboard overlays and nothing should move.
    // `bottomOffset1WithoutIme` equals `bottomOffset1` whenever the keyboard is hidden, so the two
    // pre-existing cases are byte-identical.
    val bottomOffsetForWebView get() =
        (if (imePaddingApplied) 0 else bottomOffset1WithoutIme) +
            (if (transportBarVisible) transportBarHeight else 0) +
            (if (restoreButtonsVisible) windowButtonHeight else 0) +
            (if (agentLogVisible) agentLogHeight else 0) +
            (if (searchSheetVisible) searchSheetHeight else 0)

    // IME keyboard height in pixels (0 when keyboard hidden)
    val imeHeight get() = bottomOffset1 - bottomOffset1WithoutIme

    /**
     * Whether the Compose reading-view search field currently holds focus. `false` only in the
     * brief window before `setupUi()` installs the host.
     */
    internal val composeSearchFieldFocused: Boolean
        get() = composeReadingViewHost?.searchFieldFocused?.value == true

    /**
     * Whether the IME-height padding on `binding.mainBibleView` is in effect.
     *
     * The Compose search field lives in the TOOLBAR, at the top of the very container this padding
     * shrinks — so while THAT field owns the keyboard the padding buys nothing and costs the whole
     * layout: it shrinks the Compose tree, which flipped `SplitContent`'s orientation from stacked to
     * side-by-side mid-typing (A/B F6-B1).
     *
     * Keyed on the field's FOCUS, never on search mode being active. Search mode outlives the results
     * sheet by design (tap a result, Back closes the sheet, the toolbar stays in search mode until a
     * second Back), so a WebView note editor can be opened while search mode is still on — and that
     * editor must still be lifted above the keyboard, which is the whole reason this padding exists.
     *
     * [bottomOffset2] and [bottomOffsetForWebView] MUST read this same predicate: both zero their
     * navigation-bar term on the grounds that the padding is covering it.
     */
    private val imePaddingApplied: Boolean get() = imeHeight > 0 && !composeSearchFieldFocused

    private val restoreButtonsVisible get() = windowRepository.workspaceSettings.restoreButtonsVisible

    val workspaceSettings: WorkspaceEntities.WorkspaceSettings get() = windowRepository.workspaceSettings
    override val integrateWithHistoryManager: Boolean = true
    override val disableBaseSetupUi: Boolean = true
    override val enableGenericVolumeScroll: Boolean get() = false

    // Single set of ABEventBus subscriptions, shared by onCreate and unFreeze (which
    // re-registers after freeze unregistered). `this` inside a handler lambda resolves
    // to the Subscriptions DSL receiver, so any Activity reference is this@MainBibleActivity.
    private val eventSubscriptions: ABEventBus.Subscriptions.() -> Unit = {
        on<SpeakEvent> { event ->
            if(event.isSpeaking) {
                transportBarVisible = true
                updateBottomBars()
            } else if(event.isStopped) {
                transportBarVisible = false
                updateBottomBars()
            }
        }
        on<HideTransportEvent> { _ ->
            transportBarVisible = false
            updateBottomBars()
        }
        onMain<CurrentVerseChangedEvent> { passageEvent ->
            if(paused) return@onMain
            updateTitle()
        }
        onMain<CloudSyncEvent> { event ->
            binding.syncIcon.visibility = if(event.running) View.VISIBLE else View.INVISIBLE
        }
        onMain<AgentLogVisibilityChanged> { event ->
            Log.i(TAG, "AgentLogVisibilityChanged: visible=${event.visible}, height=${event.height}")
            agentLogVisible = event.visible
            agentLogHeight = event.height
            updateBottomBars()
            // Trigger BibleView offset updates after values are updated
            ABEventBus.post(AgentLogOffsetsUpdated())
        }
        onMain<SpeakEvent> { speakEvent ->
            if(!speakEvent.isTemporarilyStopped) {
                updateBottomBars()
            }
            updateActions()
        }
        on<CloudSyncEvent> { event ->
            if (!event.running) {
                CommonUtils.settings.setLong("globalLastSynchronized", now)
            }
        }
        on<AppToBackgroundEvent> { event ->
            if (event.isMovedToBackground) {
                mWholeAppWasInBackground = true
                stopPeriodicSync()
                syncScope.launch { synchronize(true) }
            } else {
                updateActions()
                syncScope.launch { startSync() }
            }
        }
        onMain<WorkspacesUpdatedViaSyncEvent> { event ->
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
        onMain<WorkspaceRefreshRequired> { event ->
            currentWorkspaceId = workspaces.first().id
        }
        on<ScreenSettings.NightModeChanged> { event ->
            if(paused) return@on
            if(CurrentActivityHolder.currentActivity == this@MainBibleActivity) {
                refreshIfNightModeChange()
            }
        }
        onMain<MainBibleAfterRestore> { e ->
            bookmarkControl.reset()
            bibleViewFactory.clear()
            windowControl.windowSync.setResyncRequired()
            currentWorkspaceId = IdType.empty()
        }
        on<UpdateMainBibleActivityDocuments> { e ->
            updateDocumentsPending = true
        }
        onMain<CurrentWindowChangedEvent> { event ->
            if(paused) return@onMain
            updateActions()
        }
        onMain<NumberOfWindowsChangedEvent> { event ->
            if(paused) return@onMain
            setSoftKeyboardMode()
        }
        onMain<PassageChangedEvent> { event ->
            if(paused) return@onMain
            updateActions()
        }
    }

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
            navigationView.menu.findItem(R.id.rateButton).isVisible = false
        }


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
        ABEventBus.register(this, eventSubscriptions)

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
                showNewSyncTargetsNotice()
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

    var networkAvailable: Boolean = false
    val networkCallback = object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            networkAvailable = true
            if (!paused) {
                syncScope.launch { startSync() }
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            networkAvailable = false
            stopPeriodicSync()
        }
    }

    override fun fixNightMode() {} // handle this manually here

    private fun setupUi() {
        // Compose reading view: mount ComposeView into mainBibleView. The classic `toolbarLayout`
        // row is hidden (GONE) by ComposeReadingViewHost.install; the drawer stays native.
        binding.mainBibleView.removeAllViews()
        val host = ComposeReadingViewHost(this)
        composeReadingViewHost = host
        host.install(binding.mainBibleView)
        // Batch Z-early A6: the Compose ModalNavigationDrawer replaces the native one; lock the
        // native DrawerLayout so it cannot be dragged open underneath it. NOTE (A7 fix B): the lock
        // gates GESTURES only -- `LOCK_MODE_LOCKED_*` is consulted by `ViewDragHelper`, while
        // `openDrawer(View, Boolean)` makes no `getDrawerLockMode` call, so every programmatic open
        // is retargeted at the Compose drawer by hand (see `composeToggleDrawer` and
        // `composeOpenDrawerIfHosted`).
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        host.rebuildDrawer()
        windowControl.windowSync.reloadAllWindows(true)
        updateActions()
        ABEventBus.post(ConfigurationChanged(resources.configuration))
        binding.syncIcon.visibility = View.INVISIBLE
        updateToolbar()
        updateBottomBars()
        if (!CommonUtils.isCloudSyncAvailable) {
            navigationView.menu.findItem(R.id.googleDriveSync).isVisible = false
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawers()
            mainMenuCommandHandler.handleMenuRequest(menuItem)
        }

        // Set version text in navigation drawer footer
        val versionMsg = getString(R.string.version_text, CommonUtils.applicationVersionName)
        versionTextView.text = versionMsg

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
                val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

                // Store base system bar offsets (without IME)
                bottomOffset1WithoutIme = systemBarInsets.bottom  // Always system bars only, never includes IME

                // bottomOffset1 includes IME when keyboard is visible (for Android UI positioning)
                if (imeInsets.bottom > 0) {
                    // Keyboard is visible - adjust the bottom offset to account for it
                    bottomOffset1 = maxOf(systemBarInsets.bottom, imeInsets.bottom)
                } else {
                    bottomOffset1 = systemBarInsets.bottom
                }

                // Resize the WebView area when the keyboard is visible, to fix position:fixed drift.
                // This restores the pre-Android 15 ADJUST_RESIZE behaviour manually.
                applyImePadding()

                // Trigger any layout updates that depend on these offsets
                updateBottomBars()
                updateToolbar()
                ABEventBus.post(SystemInsetsChangedEvent(systemBarInsets))
                windowInsets
            }
        }
    }

    class SystemInsetsChangedEvent(val insets: Insets)

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
            knownInstalled.forEach {
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

    private fun showNewSyncTargetsNotice() {
        val displayedVer = preferences.getInt("new-sync-targets-notice-displayed", 0)
        if (displayedVer >= NEW_SYNC_TARGETS_ANNOUNCE_VERSION) return

        // The notice is only relevant to users who already use device sync. Suppress
        // it for everyone else so that users who enable sync later don't see a stale
        // announcement about targets that aren't new to them.
        if (!CommonUtils.isCloudSyncEnabled) {
            preferences.setInt("new-sync-targets-notice-displayed", NEW_SYNC_TARGETS_ANNOUNCE_VERSION)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.new_sync_targets_notice_title)
            .setMessage(R.string.new_sync_targets_notice_message)
            .setCancelable(false)
            .setNegativeButton(R.string.dismiss) { _, _ ->
                preferences.setInt("new-sync-targets-notice-displayed", NEW_SYNC_TARGETS_ANNOUNCE_VERSION)
            }
            .setPositiveButton(R.string.open_settings) { _, _ ->
                preferences.setInt("new-sync-targets-notice-displayed", NEW_SYNC_TARGETS_ANNOUNCE_VERSION)
                ScreenLauncher.open(this, Screen.SyncSettings)
            }
            .show()
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
                .setNeutralButton(getString(R.string.dismiss)) { _, _ -> it.resume(false)}
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

        val announceVersion = 3
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
            val extraMessage = """
                |<b>DEVELOPER'S SPECIAL NOTICE FOR BETA TESTERS (April 2026)</b><br><br>
                |Welcome to the 5.1 beta! Many new features have landed, including:<br>
                |<br>
                |• <b>AI assistant</b> with tool calling and support for multiple
                | providers (Claude, Grok, OpenRouter, OpenAI-compatible and more).<br>
                |• <b>Reading &amp; memorization progress tracking</b> with multiple
                | modes: Word Scramble, Word Order, Word Blur and Type It.<br>
                |• <b>My Documents</b>: your own editable, syncable pages.<br>
                |• <b>Multi-translation search</b> across several Bibles at once.<br>
                |• Many more improvements &mdash; see the "What's new" video above.<br>
                |<br>
                |Please test and report any bugs via
                |<a href="https://github.com/AndBible/and-bible/issues/new/choose">GitHub</a>
                | or Main Menu &rarr; Report a bug.<br>
                |<br>
                |Best regards, Tuomas<br><br>
                |P.S. You can support AndBible development financially by
                |<a href="$buyDevelopmentLink">sponsoring development hours</a>.
                | <br><br>
                | (Standard beta notice below)
                | <br><br>
            """.trimMargin()
            val htmlMessage = "$extraMessage$videoMessageLink<br><br>$par1<br><br> $par2<br><br> $par3 <br><br> <i>${getString(R.string.version_text, CommonUtils.applicationVersionName)}</i>"

            val spanned = htmlToSpan(htmlMessage)

            val d = AlertDialog.Builder(this)
                .setTitle(getString(R.string.beta_notice_title))
                .setMessage(spanned)
                .setIcon(R.drawable.ic_logo)
                .setNeutralButton(getString(R.string.dismiss)) { _, _ -> it.resume(false)}
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
                val vertical = abs(e1.y - e2.y).toDouble()
                val horizontal = abs(e1.x - e2.x).toDouble()

                if (vertical > scaledMinimumDistance && abs(velocityY) > minScaledVelocity) {
                    val intent = ScreenLauncher.intentFor(this@MainBibleActivity, Screen.WorkspaceSelector)
                    startActivityForResult(intent, WORKSPACE_CHANGED)
                    return true

                } else if (horizontal > scaledMinimumDistance && abs(velocityX) > minScaledVelocity) {
                    cycleWorkspace(forward = e1.x > e2.x)
                    return true
                }

                return super.onFling(e1, e2, velocityX, velocityY)
            }

            override fun onLongPress(e: MotionEvent) {
                startActivityForResult(ScreenLauncher.intentFor(this@MainBibleActivity, Screen.ChooseDocument), STD_REQUEST_CODE)
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
        // Batch Z-early A7 fix A: this override never calls `onBackPressedDispatcher.onBackPressed()`,
        // so it is the ONLY live back route today: `AndroidManifest.xml` explicitly declares
        // `android:enableOnBackInvokedCallback="false"` (targetSdk is 36, `app/build.gradle.kts`,
        // where that flag would otherwise default to true) — a temporary opt-out documented there as
        // ignored again from targetSdk 37, see the Z-late pointer below. So predictive back /
        // `OnBackInvokedCallback` dispatch is OFF and Material3's `PredictiveBackHandler(enabled =
        // drawerState.isOpen)` inside `ModalNavigationDrawer` is never invoked; this method fires for
        // every back press, Compose drawer open or not. Hence this explicit `composeCloseDrawerIfOpen()`
        // check: the native `isDrawerVisible` branch below is always false on the compose path (that
        // drawer is locked), so without this line, back with the Compose drawer open would fall
        // through to WebView-back → `historyTraversal.goBack()` → the double-back exit toast, drawer
        // still open. Inert only before the host is installed (`false`), which keeps the branch
        // order below correct regardless. Once that opt-out is removed (see the Z-late pointer below), the two
        // dispatch routes become mutually exclusive (the dispatcher intercepts first while the drawer
        // is open, and this method is not reached) — so the guard stays correct either way, it just
        // becomes redundant on that future path rather than dead now.
        if (composeCloseDrawerIfOpen()) return
        // F6 Task 9: the reading-view search's two-stage back — first press closes the
        // results/index sheet (keeping the query and results), second leaves search mode. See
        // `composeCloseSearchIfOpen`/`ComposeReadingViewHost.closeSearchIfOpen`. Placed after the
        // drawer guard (the drawer is modal — it must win) and before the fullscreen guard below,
        // though the two can never both apply anyway: entering search always leaves fullscreen
        // first (`ReadingSearchController.open`'s `onLeaveFullScreen`), so the fullscreen branch is
        // already unreachable while search is open — this ordering just makes that explicit rather
        // than relying on it. Inert only before the host is installed (`false`), which keeps the
        // branch order below correct regardless.
        //
        // Z-late pointer: this method is reached at all only because `AndroidManifest.xml` declares
        // `android:enableOnBackInvokedCallback="false"` (a temporary opt-out, documented there as
        // ignored again from targetSdk 37 — this app is already at targetSdk 36, see
        // `app/build.gradle.kts`). Once that opt-out is removed, `onBackPressed()` stops being
        // called for a real BACK press and this branch (and the drawer one above it) must move to
        // an `OnBackPressedCallback` on `onBackPressedDispatcher`, or search becomes un-closable by
        // back. Tracked in the superrepo's `.local/todo-predictive-back-api36.md`.
        if (composeCloseSearchIfOpen()) return
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
        // Batch Z-early A7 fix C: the same swallow for the Compose drawer — classic consumes a long
        // BACK while the drawer is open (without closing it) rather than launching History, and the
        // native check below can no longer see an open drawer on the compose path. Inert only
        // before the host is installed (`false`).
        if (composeDrawerOpen && keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }
        // F6 Task 9: the same swallow for the Compose reading-view search — a focused search field
        // is reachable here now, and a long-press back must not fall through to opening History out
        // from under it. Inert only before the host is installed (`false`).
        if (composeSearchModeActive && keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }
        if (binding.drawerLayout.isDrawerVisible(GravityCompat.START) && keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }

        //TODO make Long press Back work for screens other than main window e.g. does not work from search screen because wrong window is displayed
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(TAG, "Back Long")
            // a long press of the back key. do our work, returning true to consume it.  by returning true, the framework knows an action has
            // been performed on the long press, so will set the cancelled flag for the following up event.
            // Round 15b T4: History opens as a quick sheet over the reading view. The epilogue
            // deleted the classic Activity, HistoryComposeActivity and the `Screen.History` enum
            // entry, so there is no Intent route left to fall back to. `composeReadingViewHost` is
            // declared nullable, so SOME null handling is compiler-mandated; a null host now falls
            // through to `super.onKeyLongPress` -- i.e. long BACK is simply not consumed -- rather
            // than launching anything. The host is installed in `setupUi` before a key event can
            // reach this activity, so that arm is unreachable in practice.
            val host = composeReadingViewHost
            if (host != null) {
                host.showHistorySheet()
                return true
            }
        }

        return super.onKeyLongPress(keyCode, event)
    }

    private fun setupToolbarButtons() {
        binding.apply {
            // `optionsMenu`'s click listener is gone with `showOptionsMenu` (nav-graph slice 7
            // Task 2): the button lives in the classic `toolbarLayout`, which the Compose host sets
            // to GONE, and the Compose toolbar's own overflow button drives
            // `ComposeReadingViewHost.openOverflowMenu` instead. The view itself goes with the XML
            // in Task 11.
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
                // Round 13a: the Speak settings are a sheet over the reading view.
                composeReadingViewHost?.showSpeakSettings()
                true
            }
            searchButton.setOnClickListener {
                searchControl.getSearchIntent(documentControl.currentDocument, this@MainBibleActivity)?.let { intent ->
                    startActivityForResult(intent, STD_REQUEST_CODE)
                }
            }
        }
    }

    // ---- Compose toolbar bridge (Batch 12b-B Task 5) ----
    // Thin `internal` wrappers so `ComposeReadingViewHost`'s `ReadingToolbarCallbacks` (the
    // Compose reading toolbar) can drive the exact same actions as the classic
    // toolbar's click/long-click/fling listeners above and in `updateActions()` /
    // `setupToolbarFlingDetection()`, without widening any of those private members' own
    // visibility — each wrapper just calls into the existing private logic from within this class.

    /**
     * Batch Z-early A6: on the Compose path the ☰ button toggles the Compose
     * `ModalNavigationDrawer` (owned by [composeReadingViewHost]), not the native `DrawerLayout` —
     * which `setupUi` locks closed on that path.
     */
    internal fun composeToggleDrawer() {
        val host = composeReadingViewHost
        if (host != null) {
            host.toggleDrawer()
        } else {
            // Defensive: the Compose toolbar only exists on the compose path, where the host is
            // always installed. Keep the native behaviour as a fallback rather than no-op.
            if (binding.drawerLayout.isDrawerVisible(GravityCompat.START)) {
                binding.drawerLayout.closeDrawers()
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    /**
     * Whether the Compose drawer is open — `false` only before the host is installed, where the
     * native `binding.drawerLayout.isDrawerVisible(GravityCompat.START)` remains the answer used
     * elsewhere. Batch Z-early A7 fix C.
     */
    internal val composeDrawerOpen: Boolean get() = composeReadingViewHost?.isDrawerOpen == true

    /**
     * Closes the Compose drawer if it is open; returns whether it did (i.e. whether the caller's
     * event has been consumed). Always `false` before the host is installed, so a caller can use it
     * as a leading guard without changing anything either way. Batch Z-early A7 fix A/C.
     */
    internal fun composeCloseDrawerIfOpen(): Boolean {
        val host = composeReadingViewHost ?: return false
        if (!host.isDrawerOpen) return false
        host.closeDrawer()
        return true
    }

    /**
     * Opens the Compose drawer if a host is installed; returns whether it did.
     * Always `false` before the host is installed, so a caller can use it as a leading guard and
     * fall through to its existing native `drawerLayout.open()`.
     *
     * Batch Z-early A7 fix B: `setupUi`'s `LOCK_MODE_LOCKED_CLOSED` only gates `ViewDragHelper`
     * gestures — `DrawerLayout.openDrawer(View, Boolean)` makes no `getDrawerLockMode` call at all —
     * so a programmatic open (the Alt+M hardware-keyboard shortcut in `BibleJavascriptInterface`)
     * would still raise the NATIVE `NavigationView` underneath the Compose one, i.e. two drawers.
     * The classic call's companion `drawerLayout.requestFocus()` has no counterpart here and needs
     * none: the Compose drawer is a modal sheet that takes over input while open.
     */
    internal fun composeOpenDrawerIfHosted(): Boolean {
        val host = composeReadingViewHost ?: return false
        host.openDrawer()
        return true
    }

    // ---- Compose drawer side-effect parity (Batch Z-early A7) ----
    // The classic `DrawerLayout.DrawerListener` installed in `setupUi` (:634-665) has three side
    // effects; on the compose path the native listener never fires (that drawer is locked closed),
    // so `ComposeReadingViewHost` derives the same edges from the Material3 `DrawerState` and calls
    // these. Each one only *forwards* to the private logic classic already used, so nothing on the
    // classic path changes — and nothing on the classic path calls them.

    /** Compose-drawer parity for classic `STATE_SETTLING`/`STATE_DRAGGING` → `showSystemUI(false)`. */
    internal fun drawerShowSystemUiTransient() { showSystemUI(false) }

    /** Compose-drawer parity for classic `STATE_IDLE` at slide offset 0. */
    internal fun drawerApplyIdleSystemUi() {
        if (isFullScreen) hideSystemUI() else showSystemUI()
    }

    /**
     * Compose-drawer parity for classic `onDrawerClosed` — see
     * [net.bible.sharedcore.reading.shouldRestorePaneFocusOnDrawerClose] for why it is conditional.
     * Exposed separately from [drawerRestorePaneFocus] so the decision is assertable as a plain
     * function call, rather than only by rendering the drawer and the search bar together. (Not for
     * want of a Compose UI test harness — the earlier wording here claimed the repo has none and can
     * get none, which is false: `compose-ui-test` is in `:app`'s test source set and
     * `AbSearchableOptionSheetContentTest` uses `createComposeRule()`. Whole-branch review, Blocker 2.)
     */
    internal fun drawerShouldRestorePaneFocus(): Boolean =
        shouldRestorePaneFocusOnDrawerClose(searchBarOpen = composeSearchModeActive)

    /** Compose-drawer parity for classic `onDrawerClosed`. */
    internal fun drawerRestorePaneFocus() {
        if (!drawerShouldRestorePaneFocus()) return
        windowRepository.activeWindow.bibleView?.requestFocus()
    }

    /**
     * The Compose toolbar's search button (F6 entry point 2). With a Compose host mounted, search
     * now happens IN the reading view for every document type — the toolbar enters search mode and
     * the results/index sheet rises — instead of starting the search Activity chain. A document that
     * cannot be searched at all is handled downstream by `searchKindFor` → `Unavailable` →
     * [ComposeReadingViewHost.searchUnavailableDocName]'s snackbar, not by keeping it off this path.
     *
     * The Intent route survives only for the case where `composeReadingViewHost` is null — in
     * practice only the brief window before `setupUi()` installs it. This method is only reached
     * from the Compose toolbar, so that branch is unreachable in practice; it is kept as a
     * defensive fallback rather than removed, the same idiom as the Z-early drawer retargeting.
     */
    internal fun composeSearch() {
        val host = composeReadingViewHost
        if (host != null) {
            host.openSearch()
            return
        }
        searchControl.getSearchIntent(documentControl.currentDocument, this)?.let { intent ->
            startActivityForResult(intent, STD_REQUEST_CODE)
        }
    }

    /**
     * Opens the reading-view search for a hosted Compose activity; returns whether it did. Always
     * `false` before the host is installed, so every caller can use this as a leading guard and fall
     * through to its existing classic `Intent` unchanged — the same idiom as
     * [composeOpenDrawerIfHosted]. F6 Task 8b entry points 4 (`MenuCommandHandler`'s drawer search
     * row), 5 (`BibleJavascriptInterface`'s Ctrl+F) and 6 (the device SEARCH key, below).
     *
     * [preDecorated] marks [seedQuery] as ALREADY run through
     * [net.bible.android.control.search.SearchControl.decorateSearchString] (entry point 7,
     * `BibleView`'s selection "Search…") rather than a raw user query — see
     * [ComposeReadingViewHost.openSearch]'s kdoc for why that needs a different re-decoration path
     * than a fresh query.
     */
    internal fun composeSearchIfHosted(seedQuery: String? = null, preDecorated: Boolean = false): Boolean {
        val host = composeReadingViewHost ?: return false
        host.openSearch(seedQuery, preDecorated)
        return true
    }

    /**
     * Strong's find-all (F6 Task 8b entry point 8, [net.bible.android.control.link.LinkControl.showAllOccurrences])
     * retargeted into a hosted Compose activity's search; returns whether it did. Same "leading
     * guard, fall through to classic otherwise" idiom as [composeSearchIfHosted] — `LinkControl`
     * calls this only once it has already decided the search document is indexed (the not-indexed
     * branch keeps classic's `Screen.SearchIndex` route unconditionally: prompting to index a
     * document other than the active window's is Task 11's machinery, which does not exist yet).
     *
     * A `false` return means the caller MUST use its own classic fallback — this is not just "no
     * host mounted" any more: [ComposeReadingViewHost.openSearchStrongs] also returns `false` (and
     * does nothing) when the active window's document is an EPUB, because Strong's find-all is a
     * Bible concept — it searches Strong's-enabled BIBLES, not the open document — so an EPUB on
     * screen must not swallow the request silently. (F43 Task 6 fix round 1: this method used to
     * return `true` unconditionally whenever a host was mounted, which turned that EPUB decline into
     * a silent no-op instead of falling through to the classic Strong's search.)
     */
    internal fun composeSearchStrongsIfHosted(ref: String, translationIds: List<String>): Boolean {
        val host = composeReadingViewHost ?: return false
        return host.openSearchStrongs(ref, translationIds)
    }

    /**
     * F6 Task 9: the reading-view search's two-stage back, wired into [onBackPressed] as a leading
     * guard right after [composeCloseDrawerIfOpen] — first press closes the results/index sheet
     * (keeping the query and results), second leaves search mode. Returns whether the press was
     * consumed. Always `false` before the host is installed, same idiom as [composeCloseDrawerIfOpen].
     */
    internal fun composeCloseSearchIfOpen(): Boolean = composeReadingViewHost?.closeSearchIfOpen() ?: false

    /**
     * Whether the Compose reading-view search mode is active — `false` only before the host is
     * installed. F6 Task 9: used in [onKeyLongPress] to swallow a long-press back while a search
     * field is focused, the same role [composeDrawerOpen] plays for the drawer there.
     */
    internal val composeSearchModeActive: Boolean
        get() = composeReadingViewHost?.searchController?.searchModeActive?.value == true

    /**
     * F6 Task 8b Step 3: [ComposeReadingViewHost.install]'s report of the search sheet's live
     * (visible, measured-height-in-px) state — the fourth term in [bottomOffsetForWebView], mirroring
     * [agentLogVisible]/[agentLogHeight]. Posts [SearchSheetOffsetsUpdated] (the same "recompute and
     * push to the WebView" idiom as [AgentLogOffsetsUpdated]) so [BibleView.updateOffsets] picks up
     * the new value; a no-op when nothing actually changed, so a benign recomposition doesn't spam
     * `set_offsets` calls.
     */
    internal fun updateSearchSheetOffsets(visible: Boolean, heightPx: Int) {
        if (searchSheetVisible == visible && searchSheetHeight == heightPx) return
        searchSheetVisible = visible
        searchSheetHeight = heightPx
        ABEventBus.post(SearchSheetOffsetsUpdated())
    }

    /**
     * Applies (or removes) the IME-height padding on the Compose/WebView container.
     *
     * Reads live state, so it needs no snapshot of the insets: the listener's original condition
     * `imeInsets.bottom > systemBarInsets.bottom` is algebraically identical to `imeHeight > 0`
     * (`imeHeight` is `maxOf(sb, ime) - sb`), and both offsets it derives from are fields the listener
     * keeps up to date.
     */
    private fun applyImePadding() {
        binding.mainBibleView.setPadding(0, 0, 0, if (imePaddingApplied) bottomOffset1 else 0)
    }

    /**
     * The Compose search field gained or lost focus, which changes [imePaddingApplied] without
     * changing any inset — so the insets listener never fires and both the padding and the WebView's
     * offsets would go stale. Same "recompute and push to the WebView" idiom as
     * [updateSearchSheetOffsets] and the agent log's.
     */
    internal fun onComposeSearchFieldFocusChanged() {
        applyImePadding()
        ABEventBus.post(ImePaddingChanged())
    }

    internal fun composeToggleSpeak() {
        if (transportBarVisible) {
            if (speakControl.isStopped) {
                transportBarVisible = false
            }
        } else {
            transportBarVisible = true
        }
        updateBottomBars()
    }

    /**
     * Round 14b §8: show the transport bar, idempotently — the main-menu Speak item's whole
     * behaviour. Deliberately NOT [composeToggleSpeak]: a menu row is a one-way action, so "Speak"
     * must never HIDE the bar (spec D3), and a user who picks it twice must not be worse off than a
     * user who picked it once.
     *
     * Lives here rather than in `ComposeReadingViewHost` because [transportBarVisible] is this
     * activity's private field and the single source of truth for bar visibility — the Compose side
     * only ever OBSERVES it, through the `SpeakTransportVisibilityChanged` the setter posts. The
     * setter's own `if (field == value) return` is what makes this idempotent: a second call posts
     * no event and triggers no recomposition.
     */
    internal fun composeShowSpeakTransport() {
        transportBarVisible = true
        updateBottomBars()
    }

    /** The Compose toolbar's Speak long-press: opens the Speak settings SHEET over the reading view. */
    internal fun composeSpeakLong() {
        composeReadingViewHost?.showSpeakSettings()
    }

    /**
     * Switch to a workspace by id. Extracted from the WORKSPACE_CHANGED result arm so round 15b's
     * quick sheet and the full selector's activity result cannot drift apart.
     */
    internal fun switchToWorkspace(workspaceId: String) {
        currentWorkspaceId = IdType(workspaceId)
    }

    /**
     * Whole-branch review fix C1: the quick sheet's own route to [switchToWorkspace].
     *
     * Every OTHER route to `currentWorkspaceId`'s setter saves the outgoing workspace first:
     * [cycleWorkspace] calls `windowRepository.saveIntoDb()` immediately before switching; the
     * classic `WorkspaceSelectorActivity` path calls it too and additionally gets an `onPause`; the
     * Compose selector calls `service.saveCurrentIntoDb()`. The quick sheet never pauses the
     * activity, so without this it is the only path that reaches `windowRepository.loadFromDb`
     * (whose first act is `clear()`) with the outgoing workspace's windows, page managers and
     * `HistoryManager` entries never written to `dao` -- silently discarding unsaved layout/history
     * changes on a quick switch. Mirrors [cycleWorkspace]'s save call exactly.
     *
     * Deliberately NOT folded into [switchToWorkspace] itself: that function is also the
     * `WORKSPACE_CHANGED` result arm's body, which runs AFTER the full selector has already renamed
     * the outgoing workspace in the DB. Saving there would recompute `contentText` from the STALE
     * in-memory `name` and push it back over a rename the user just made in the selector.
     */
    internal fun quickSwitchToWorkspace(workspaceId: String) {
        windowRepository.saveIntoDb()
        switchToWorkspace(workspaceId)
    }

    internal fun composeCycleWorkspace(forward: Boolean) = cycleWorkspace(forward)

    /**
     * The Compose toolbar title's TAP. Round 15b Task 9: with a Compose host mounted, the three key
     * choosers simple enough for a sheet (`KeyChooserRoute`, spec §4.6) now open over the reading
     * view instead of starting their full screen; everything else takes the classic path below,
     * unchanged.
     *
     * Three separate conditions fall through to the classic path. Two are real cases, not
     * belt-and-braces: a page shape `KeyChooserRoute` deliberately keeps on its own screen
     * (dictionary, StudyPad, my-document, multi-document), and a chosen kind whose key list is
     * EMPTY — where both chooser activities apply a fallback selection and finish without drawing
     * anything, which a sheet cannot reproduce (E3). The third, no host mounted yet, is defensive
     * rather than reachable in practice: this method's only caller is the Compose toolbar's title
     * tap ([ComposeReadingViewHost.install]'s `onTitleTap`), wired inside `install()` itself, so the
     * host is already non-null by the time this can fire.
     *
     * `CurrentPage.startKeyChooser` itself is deliberately NOT touched, so `CurrentPageManager`'s
     * auto-open and `BibleJavascriptInterface.refChooserDialog` — which needs a real Intent result —
     * behave exactly as today.
     */
    internal fun composeStartKeyChooser() {
        val host = composeReadingViewHost
        val sheet = host?.currentKeyChooserPage()?.let { KeyChooserRoute.sheetFor(it) }
        if (host != null && sheet != null) {
            // Resolved HERE, once, and handed to the sheet — never resolved again inside it. The
            // emptiness decision below and the sheet's rows must come from the same list (a
            // `KeyRow`'s id is an INDEX into it), and a second resolution is expensive on the UI
            // thread: `EpubBackendState.tocKeys` is not cached at all and rebuilds every `Key` on
            // each access. `ComposeReadingViewHost.showKeyChooserSheet`'s kdoc has the full note.
            val keys = host.resolveKeyChooserKeys(sheet.kind)
            if (host.keyChooserSheetHasRows(sheet.kind, keys)) {
                host.showKeyChooserSheet(sheet.kind, keys)
                return
            }
        }
        pageControl.currentPageManager.currentPage.startKeyChooser(this)
    }

    /**
     * The Compose toolbar title's long-press. Round 15b Task 5: with a Compose host mounted this now
     * opens the document QUICK sheet over the reading view (spec §4.5) instead of starting the full
     * `ChooseDocument` screen — which the sheet's own footer row still reaches. Null host = the
     * full-screen path, unchanged below.
     *
     * The reroute lives HERE rather than at the toolbar callback so there is exactly ONE conditional
     * and one classic fall-through for this entry point, and so any later caller of this internal
     * entry point gets the sheet too.
     */
    internal fun composeChooseDocument() {
        val host = composeReadingViewHost
        if (host != null) {
            host.showDocumentSheet()
            return
        }
        startActivityForResult(ScreenLauncher.intentFor(this, Screen.ChooseDocument), STD_REQUEST_CODE)
    }

    /**
     * Apply a document chosen by the user to the active window.
     *
     * Extracted from the `ChooseDocument` `onActivityResult` arm so that round 15b's document quick
     * sheet — which returns no Intent and therefore cannot use that arm — and the existing activity
     * result cannot drift apart. The `FakeBookFactory` fallback is the reason: it only matters for
     * pseudo-documents, so a second copy could lose it and nothing would notice.
     */
    internal fun applyChosenDocument(bookStr: String?) {
        val book = Books.installed().getBook(bookStr) ?: FakeBookFactory.pseudoDocuments.first { it.initials == bookStr }
        documentControl.changeDocument(book)
        updateActions()
    }

    /**
     * Apply a chosen verse to the active window.
     *
     * Extracted from the `onActivityResult` `in classes` arm so that round 15b's grid quick sheet —
     * which returns no Intent and therefore cannot use that arm — and the existing activity result
     * cannot drift apart. The `NoSuchVerseException` branch is the reason: it only fires on a
     * malformed OSIS id, so a second copy could lose it and nothing would notice.
     */
    internal fun applyChosenVerse(verseStr: String, isFromBookmark: Boolean = false) {
        val verse = try {
            VerseFactory.fromString(navigationControl.versification, verseStr)
        } catch (e: NoSuchVerseException) {
            ABEventBus.post(ToastEvent(getString(R.string.verse_not_found)))
            return
        }
        val pageManager = windowControl.activeWindowPageManager
        if (isFromBookmark && !pageManager.isBibleShown) {
            pageManager.setCurrentDocumentAndKey(windowControl.defaultBibleDoc(false), verse)
        } else {
            pageManager.currentPage.setKey(verse, !isFromBookmark)
        }
    }

    /**
     * Apply a general-book / map / dictionary key chosen by the user to the active window.
     *
     * Extracted from the `in genBookClasses` `onActivityResult` arm so that round 15b's key-chooser
     * quick sheets — which return no Intent and therefore cannot use that arm — and the existing
     * activity result cannot drift apart. [book] is passed explicitly rather than derived from
     * [key] because it cannot be: an EPUB table-of-contents entry is a `BookAndKey` carrying its
     * OWN document, which is not the page's current document, while every other key carries none.
     */
    internal fun applyChosenGenBookKey(book: Book?, key: Key) {
        windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
    }

    internal fun composeCycleStrongs() {
        val prefOptions = dummyStrongsPrefOption
        prefOptions.value = (prefOptions.value as Int + 1) % 3
        prefOptions.handle()
        updateStrongsButton()
        composeReadingViewHost?.refreshHostedState()
    }

    internal fun composeStrongsLong() {
        val prefOptions = dummyStrongsPrefOption
        fun apply() {
            prefOptions.handle()
            updateStrongsButton()
            composeReadingViewHost?.refreshHostedState()
        }
        prefOptions.openDialog(this, onChanged = { apply() }, onReset = { apply() })
    }

    /** @param anchor the Compose toolbar's ComposeView (classic `bibleButton` is inside the now-GONE `toolbarLayout` on this path). */
    internal fun composeBibleClick(anchor: View) {
        if (toolbarButtonSetting?.startsWith("swap-") == true) {
            setCurrentDocument(documentControl.suggestedBible)
        } else {
            menuForDocs(anchor, documentControl.biblesForVerse)
        }
    }

    /**
     * Nav-graph slice 7 Task 2: the `swap-menu` branch opens the Compose quick-doc menu (the same
     * `openBibleQuickDoc` seam the non-swap SHORT press already used), not the native `menuForDocs`
     * `PopupMenu` — so this no longer needs a `View` to anchor on.
     */
    internal fun composeBibleLongClick() {
        if (toolbarButtonSetting == "swap-menu") {
            composeReadingViewHost?.openBibleQuickDoc(composeQuickDocItems(documentControl.biblesForVerse))
        } else {
            startDocumentChooser("BIBLE")
        }
    }

    /** @param anchor the Compose toolbar's ComposeView (classic `commentaryButton` is inside the now-GONE `toolbarLayout` on this path). */
    internal fun composeCommentaryClick(anchor: View) {
        if (toolbarButtonSetting?.startsWith("swap-") == true) {
            setCurrentDocument(documentControl.suggestedCommentary)
        } else {
            menuForDocs(
                anchor,
                documentControl.commentariesForVerse
                    + SwordDocumentFacade.getBooks(BookCategory.GENERAL_BOOK)
                    + SwordDocumentFacade.getBooks(BookCategory.DICTIONARY)
            )
        }
    }

    /** [composeBibleLongClick]'s counterpart; same slice 7 Task 2 change. */
    internal fun composeCommentaryLongClick() {
        if (toolbarButtonSetting == "swap-menu") {
            // Mirrors classic `commentaryLongPress` exactly: unlike `commentaryClick`/
            // `composeCommentaryClick`, the long-press menu does NOT append
            // GENERAL_BOOK/DICTIONARY books.
            composeReadingViewHost?.openCommentaryQuickDoc(composeQuickDocItems(documentControl.commentariesForVerse))
        } else {
            startDocumentChooser("COMMENTARY")
        }
    }

    /** Compose-path quick-doc dispatch: returns the popup items to show (or empty if it switched
     *  directly / had nothing). Mirrors classic `menuForDocs` (:1905-1924) via `QuickDocPicker`. */
    internal fun composeQuickDocItems(books: List<Book>): List<QuickDocMenuItem> {
        val byId = books.associateBy { it.initials }
        val rows = books.map {
            QuickDocRow(
                it.initials,
                getString(R.string.something_with_parenthesis, it.abbreviation, it.language.code),
                it.language.code,
                it.abbreviation,
                category = docCategoryOf(it.bookCategory),
            )
        }
        return when (val a = QuickDocPicker.action(rows, currentDocument?.initials ?: "")) {
            is QuickDocAction.None -> emptyList()
            is QuickDocAction.SwitchDirectly -> { setCurrentDocument(byId[a.id]); emptyList() }
            is QuickDocAction.ShowPopup -> { composeQuickDocBooksById = byId; a.items }
        }
    }

    /** id -> Book for the currently-open compose quick-doc menu (resolves `onSelect`). */
    private var composeQuickDocBooksById: Map<String, Book> = emptyMap()

    /** Compose quick-doc menu selection -> set the doc (mirrors classic `menuForDocs`' click listener). */
    internal fun composeQuickDocSelect(id: String) { setCurrentDocument(composeQuickDocBooksById[id]) }

    // ---- Compose window-tab rail bridge (Batch 12b follow-on, Plan A Task 7) ----
    // `windowLabelFor`/`windowTopLabelFor`/`windowIconFor` resolve a `ComposeReadingViewHost`-supplied
    // opaque window id to the live `Window` and mirror classic `SplitBibleArea.getWindowButtonTitleText` /
    // `WindowButtonWidget.updateSettings`'s `docType` image (`WindowButtonWidget.kt:75-151`) — the
    // label/top-label/icon shown per tab in the Compose `WindowTabBar` (Task 5). Deliberately plain
    // (non-`@Composable`) functions: `WindowTabBar`'s `windowLabel`/`windowTopLabel`/`windowIcon`
    // parameters are plain lambda types (not `@Composable` ones), so nothing in this call chain may
    // invoke a composable (e.g. `painterResource`) — `windowIconFor` builds its `Painter` via
    // `BitmapPainter` instead, which needs no composition context.

    /** Cache of resource id -> [Painter], since the doc-type icon set is small and fixed (one per [BookCategory]). */
    private val composeWindowIconCache = mutableMapOf<Int, Painter>()

    /** Short per-window label for the Compose restore rail — mirrors classic `getWindowButtonTitleText`. */
    internal fun windowLabelFor(id: String): String {
        val window = windowRepository.getWindow(IdType(id)) ?: return ""
        return try {
            val curdoc = window.pageManager.currentPage.currentDocument ?: return " "
            if (curdoc.isStudyPad) {
                (window.pageManager.currentPage.key as? StudyPadKey)?.name ?: " "
            } else {
                curdoc.abbreviation
            }
        } catch (e: Exception) { " " }
    }

    /**
     * The rail button's tiny top row: classic `topButtonText`, i.e. `pageManager.titleText`
     * (`WindowButtonWidget.kt:148`). Returns `null` — meaning "render no top row" — for an unknown
     * window or when the page has no title, matching classic's `?: ""` + `View.GONE` handling
     * (`WindowButtonWidget.kt:148-149`).
     *
     * Deliberately NOT part of `WindowSnapshot`: this is a rendering label, not window state, and
     * the snapshot is the iOS-facing model.
     */
    internal fun windowTopLabelFor(id: String): String? {
        val window = windowRepository.getWindow(IdType(id)) ?: return null
        return try {
            window.pageManager.titleText.takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }
    }

    /** Doc-type icon for the Compose restore rail — mirrors classic `docType.setImageResource(document.imageResource)`. */
    internal fun windowIconFor(id: String): Painter? {
        val window = windowRepository.getWindow(IdType(id)) ?: return null
        val resId = window.pageManager.currentPage.currentDocument?.imageResource ?: return null
        return composeWindowIconCache.getOrPut(resId) {
            val drawable = ContextCompat.getDrawable(this, resId) ?: return null
            BitmapPainter(drawable.toBitmap().asImageBitmap())
        }
    }

    private val dummyStrongsPrefOption
        get() = StrongsPreference(
            SettingsBundle(
                level = SettingsLevel.WINDOW,
                pageManagerSettings = windowControl.activeWindow.pageManager.textDisplaySettings,
                workspaceId = windowRepository.id,
                workspaceName = windowRepository.name,
                workspaceSettings = windowRepository.textDisplaySettings,
                globalSettings = CommonUtils.globalTextDisplaySettings,
                windowId = windowControl.activeWindow.id
            ))


    val workspaces get() = dao.allWorkspaces()
    lateinit var windowRepository: WindowRepository

    private fun cycleWorkspace(forward: Boolean) {
        val workspaces = workspaces
        if(workspaces.size < 2) return
        windowRepository.saveIntoDb()
        val currentWorkspacePos = workspaces.indexOf(workspaces.find {it.id == currentWorkspaceId})
        val nextPos = if (forward) {
            if (currentWorkspacePos < workspaces.size - 1) currentWorkspacePos + 1 else 0
        } else {
            if (currentWorkspacePos > 0) currentWorkspacePos - 1 else workspaces.size - 1
        }
        currentWorkspaceId = workspaces[nextPos].id
    }

    private var currentWorkspaceId
        get() = windowRepository.id
        set(value) {
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
            level = SettingsLevel.WORKSPACE,
            workspaceId = windowRepository.id,
            workspaceName = windowRepository.name,
            workspaceSettings = windowRepository.textDisplaySettings.apply {
                colors?.workspaceColor = windowRepository.workspaceSettings.workspaceColor
            },
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        return when(itemId) {
            R.id.allTextOptions -> CommandPreference(launch = { _, _, _ ->
                startActivity(TextDisplaySettingsComposeActivity.intentFor(
                    this, SettingsScope.Workspace(windowRepository.id.toString())))
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
                // M1 (whole-branch review fix wave): guard on the MOUNTED HOST. The host is
                // mounted by `setupUi`, so it is null until then and null in any state where the
                // reading view is not up; History (MenuCommandHandler.kt / this file's
                // long-press-back) already guards the same way. The alternative this replaced was a
                // live settings read, which could disagree with what is actually on screen.
                val host = composeReadingViewHost
                if (host != null) {
                    host.showWorkspaceSheet()
                } else {
                    val intent = ScreenLauncher.intentFor(this, Screen.WorkspaceSelector)
                    startActivityForResult(intent, WORKSPACE_CHANGED)
                }
            }, opensDialog = true)
            R.id.llmActionsSubMenu -> CommandPreference(
                launch = { _, _, _ ->
                    val selection = Selection(
                        bookInitials = null,
                        startOrdinal = -1,
                        startOffset = null,
                        endOrdinal = -1,
                        endOffset = null,
                        bookmarks = emptyList(),
                    )
                    composeReadingViewHost?.showPromptSelector(selection, PromptContext.WORKSPACE_MENU, null)
                },
                visible = CommonUtils.settings.llmConfigured,
                opensDialog = true,
            )
            else -> throw RuntimeException("Illegal menu item")
        }
    }

    /**
     * Builds the Compose reading-view toolbar's overflow menu item list — the Compose counterpart
     * of the deleted native `showOptionsMenu`'s build loop, delegated to
     * [OptionsMenuStateBuilder.build]. Exists as a
     * thin bridge (rather than widening [getItemOptions] itself) so the private method's access
     * stays unchanged; see [OptionsMenuStateBuilder]'s kdoc. Called by
     * [net.bible.android.view.activity.page.screen.ComposeReadingViewHost] when the overflow
     * button is tapped, and again after [handleOptionsMenuItem] flips a toggle (to reflect the new
     * checked state).
     */
    fun buildOptionsMenuItems(): List<OptionsMenuItem> =
        OptionsMenuStateBuilder.build { resId, order -> getItemOptions(resId, order) }

    /**
     * Dispatches a click on one of [buildOptionsMenuItems]' rows — the Compose counterpart of
     * the deleted native `handlePrefItem`, delegated to [OptionsMenuStateBuilder.dispatch]. Returns whether the
     * overflow menu should stay open (see that function's kdoc): `true` for a boolean toggle
     * (the host rebuilds the list to show the flipped check), `false` once a dialog/activity/
     * action has been launched (the host closes the menu).
     */
    fun handleOptionsMenuItem(id: String): Boolean =
        OptionsMenuStateBuilder.dispatch(this, { resId, order -> getItemOptions(resId, order) }, id)

    // ---- Compose navigation drawer bridge (Batch Z-early A6) ----

    /**
     * Mirrors the classic `rateButton.isVisible = false` guard (this file, ~452-458) — negated,
     * because that guard states when the item is HIDDEN. Duplicating the condition rather than
     * hoisting it: the classic guard is a build-variant check inside `onCreate`'s body, and the
     * drawer needs it as a value. Read by
     * [net.bible.android.view.activity.page.screen.ComposeReadingViewHost.rebuildDrawer].
     */
    internal val drawerRateVisible: Boolean get() = !(
        BuildVariant.Appearance.isDiscrete ||
            BuildVariant.DistributionChannel.isHuawei ||
            BuildVariant.DistributionChannel.isFdroid ||
            BuildVariant.DistributionChannel.isAmazon
        )

    /**
     * Dispatches a click on one of the Compose drawer's rows — through the SAME
     * [MenuCommandHandler.handleMenuRequest] the classic `NavigationView`'s
     * `setNavigationItemSelectedListener` (`setupUi`, ~564) calls, so every row's command behaviour
     * is classic's by construction. Closing the drawer is the Compose side's own business (the
     * host clears its open-request before calling this), mirroring that listener's `closeDrawers()`.
     */
    internal fun handleDrawerItemClick(itemId: Int) {
        mainMenuCommandHandler.handleMenuRequest(itemId)
    }

    // ---- Compose per-window (☰) pane menu bridge (Batch 12b-followon Plan B Task 5) ----
    // Dispatches a click on one of `WindowPaneMenuStateBuilder.build(window)`'s rows — the
    // Compose per-window menu's counterpart of classic `SplitBibleArea.handlePrefItem`/
    // `getItemOptions(window, itemId, order)` (`screen/SplitBibleArea.kt:709-726, 865-1065`).
    // `SplitBibleArea.getItemOptions`/`handlePrefItem` are `private` and close over their own
    // `View`/`MenuItem`, so — exactly like `WindowPaneMenuStateBuilder`'s build half — there is no
    // clean seam to call them from here; every bridged branch below reproduces the classic action
    // body verbatim (a `SplitBibleArea.kt:NNN` comment marks each one).

    /**
     * Dispatches a click on one of [WindowPaneMenuStateBuilder.build]'s rows for the pane whose ☰
     * menu produced it. Called by [ComposeReadingViewHost]'s `onPaneMenuItemClick`.
     *
     * ATOMIC items (native-in-Compose, per [WindowPaneMenuStateBuilder]'s kdoc) act through
     * [composeReadingViewHost]'s [ReadingViewController] command seam — the SAME seam the pane
     * overlay's own gestures and the restore rail already drive, so every window mutation from
     * the Compose reading view refreshes the SSOT through one path. Falls back to [windowControl]
     * directly only if the host is somehow absent (defensive — this dispatcher is only reachable
     * from the Compose path, where [composeReadingViewHost] is always installed; this fallback is
     * what lets this function be unit-tested without booting the full Compose host) or for
     * `changeToNormal`'s compound add+flag+close (the seam has no such compound command, so it
     * always goes straight through [windowControl], same as classic).
     *
     * Returns whether the menu should stay open (mirrors [OptionsMenuStateBuilder.dispatch]'s
     * contract): `true` for [WindowPaneMenuStateBuilder.ID_PIN_MODE] and a boolean
     * `textOptionItem` row (so the host can rebuild the list and show the flipped check), `false`
     * for every other item.
     */
    fun handleWindowPaneMenuItem(windowId: String, id: String): Boolean {
        val window = windowRepository.getWindow(IdType(windowId)) ?: return false
        val controller = composeReadingViewHost?.controller
        return when (val parsed = WindowPaneMenuStateBuilder.parseId(id)) {
            is WindowPaneMenuStateBuilder.ParsedId.MoveItem -> {
                // SplitBibleArea.kt:896-898, :987-992
                controller?.onMove(windowId, parsed.order) ?: windowControl.moveWindow(window, parsed.order)
                false
            }
            is WindowPaneMenuStateBuilder.ParsedId.SyncGroupItem -> {
                // SplitBibleArea.kt:899-901, :993-996
                controller?.onChangeSyncGroup(windowId, parsed.order) ?: windowControl.changeSyncGroup(window, parsed.order)
                false
            }
            is WindowPaneMenuStateBuilder.ParsedId.TextOptionItem -> handleWindowTextOptionItem(window, parsed.order)
            // SplitBibleArea.kt:1005-1007. A/B batch 4a F4: the target window comes from the menu
            // row's own order now (classic's shape), not from a picker dialog.
            is WindowPaneMenuStateBuilder.ParsedId.CopySettingsToWindow -> {
                windowControl.copySettingsToWindow(window, parsed.order)
                false
            }
            is WindowPaneMenuStateBuilder.ParsedId.StaticItem -> handleWindowPaneStaticItem(window, parsed.id, controller)
        }
    }

    /** The `moveWindowSubMenu`/`syncGroupSubMenu`/`textOptionsSubMenu` container ids never reach
     * here — [net.bible.sharedui.reading.WindowPaneMenuRows] treats a non-empty `submenu` row as
     * "enter submenu" (`onEnterSubmenu`), not a leaf click (`onItemClick`). */
    private fun handleWindowPaneStaticItem(window: Window, id: String, controller: ReadingViewController?): Boolean {
        val windowId = window.id.toString()
        return when (id) {
            // SplitBibleArea.kt:880-883
            WindowPaneMenuStateBuilder.ID_WINDOW_NEW -> {
                controller?.onAddWindow(windowId) ?: windowControl.addNewWindow(window)
                false
            }
            // SplitBibleArea.kt:974-977
            WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE -> {
                controller?.onMaximise(windowId) ?: windowControl.maximiseWindow(window)
                false
            }
            // SplitBibleArea.kt:970-973
            WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE -> {
                controller?.onMinimise(windowId) ?: windowControl.minimiseWindow(window)
                false
            }
            // SplitBibleArea.kt:884-890 -- compound (add a new non-links window, close this one);
            // no single seam command for this, so it always goes directly through windowControl,
            // same as classic (the "or directly windowControl" case named in the Task-5 brief).
            WindowPaneMenuStateBuilder.ID_CHANGE_TO_NORMAL -> {
                windowControl.addNewWindow(window).also { it.isLinksWindow = false }
                windowControl.closeWindow(window)
                false
            }
            // SplitBibleArea.kt:891-895 -- checkable toggle; stays open so the host can rebuild
            // and show the flipped check, exactly like `OptionsMenuStateBuilder.dispatch`'s
            // boolean branch.
            WindowPaneMenuStateBuilder.ID_PIN_MODE -> {
                val newValue = !window.isPinMode
                controller?.onSetPin(windowId, newValue) ?: windowControl.setPinMode(window, newValue)
                true
            }
            // SplitBibleArea.kt:997-999
            WindowPaneMenuStateBuilder.ID_DISABLE_SYNC -> {
                controller?.onSetSynchronised(windowId, false) ?: windowControl.setSynchronised(window, false)
                false
            }
            // SplitBibleArea.kt:906-909
            WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE -> {
                controller?.onClose(windowId) ?: windowControl.closeWindow(window)
                false
            }

            // ---- Bridge rows: reproduce the classic action body verbatim (private in SplitBibleArea) ----

            // SplitBibleArea.kt:865-874 (settingsBundle), :978-986 (window-level allTextOptions --
            // distinct from this activity's OWN workspace-level `getItemOptions(R.id.allTextOptions)`
            // used by the overflow menu).
            WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS -> {
                startActivity(TextDisplaySettingsComposeActivity.intentFor(
                    this, SettingsScope.Window(window.id.toString(), windowRepository.id.toString())))
                false
            }
            // SplitBibleArea.kt:1002-1004
            WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WORKSPACE -> {
                windowControl.copySettingsToWorkspace(window)
                false
            }
            // SplitBibleArea.kt:1008-1010
            WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_GLOBAL -> {
                windowControl.copySettingsToGlobal(window)
                false
            }
            // SplitBibleArea.kt:1011-1019
            WindowPaneMenuStateBuilder.ID_EXPORT_HTML -> {
                window.bibleView?.exportHtml()
                false
            }
            // SplitBibleArea.kt:1020-1026
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD -> {
                (window.bibleView?.firstDocument as? StudyPadDocument)?.label?.let { label ->
                    lifecycleScope.launch { exportStudyPads(this@MainBibleActivity, label) }
                }
                false
            }
            // SplitBibleArea.kt:1027-1035
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV -> {
                (window.bibleView?.firstDocument as? StudyPadDocument)?.label?.let { label ->
                    lifecycleScope.launch {
                        val bookmarks = bookmarkControl.getBibleBookmarksWithLabel(label)
                        bookmarkControl.exportBookmarksToCSV(this@MainBibleActivity, bookmarks)
                    }
                }
                false
            }
            // SplitBibleArea.kt:1036-1048
            WindowPaneMenuStateBuilder.ID_ADD_WHOLE_PAGE_BOOKMARK -> {
                val currentPage = window.pageManager.currentPage
                val book = currentPage.currentDocument
                val key = currentPage.key
                if (book != null && key != null) {
                    window.bibleView?.createWholePageBookmark(book.initials, key.osisRef)
                }
                false
            }
            // SplitBibleArea.kt:1049-1063
            WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU -> {
                val currentPage = window.pageManager.currentPage
                val book = currentPage.currentDocument
                val key = currentPage.key
                if (book != null && key != null) {
                    val selection = Selection(book.initials, key.osisRef, -1, -1)
                    // This ☰ pane menu only exists on the Compose path (composeReadingViewHost
                    // installed), so route straight through the host; the classic call is kept as
                    // an `?:` fallback for safety rather than assumed unreachable.
                    composeReadingViewHost?.showPromptSelector(selection, PromptContext.WINDOW_MENU, currentPage.documentCategory)
                        ?: llmDialogHelper.showPromptSelector(selection, PromptContext.WINDOW_MENU, currentPage.documentCategory)
                }
                false
            }
            // SplitBibleArea.kt:950-967
            WindowPaneMenuStateBuilder.ID_COPY_REFERENCE -> {
                val doc = window.pageManager.currentPage.currentDocument
                val key = window.pageManager.currentPage.singleKey
                if (doc != null && key != null) {
                    val ordinalRange = window.pageManager.currentPage.anchorOrdinal
                    val ordinal = ordinalRange?.start
                    clipboardKey = BookAndKey(key, doc, ordinalRange)
                    val url = CommonUtils.makeAndBibleUrl(keyStr = key.osisRef, docInitials = doc.initials, ordinal = ordinal)
                    CommonUtils.copyToClipboard(ClipData.newPlainText(key.name, url), R.string.reference_copied_to_clipboard)
                }
                false
            }
            // SplitBibleArea.kt:929-949
            WindowPaneMenuStateBuilder.ID_GO_TO_REFERENCE -> {
                clipboardKey?.let {
                    if (it.document?.bookCategory == BookCategory.BIBLE && window.pageManager.isVersePageShown) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else if (it.document == null) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else {
                        window.pageManager.setCurrentDocumentAndKey(it.document, it)
                    }
                }
                false
            }
            // SplitBibleArea.kt:910-928
            WindowPaneMenuStateBuilder.ID_GO_TO_SPEAK -> {
                speakControl.speakBookAndKey?.let {
                    if (it.document?.bookCategory == BookCategory.BIBLE && window.pageManager.isVersePageShown) {
                        window.pageManager.setCurrentDocumentAndKey(null, it.key)
                    } else {
                        window.pageManager.setCurrentDocumentAndKey(it.document, it)
                    }
                }
                false
            }
            // moveWindowSubMenu/syncGroupSubMenu/textOptionsSubMenu container ids never reach a
            // leaf onItemClick (see this function's kdoc); llmActionsSubMenu is handled above (it
            // has no `submenu` children despite the name -- see WindowPaneMenuStateBuilder).
            else -> false
        }
    }

    /**
     * SplitBibleArea.kt:1000: the dynamic `textOptionItem` row -- classic `handlePrefItem`'s
     * generic isBoolean/openDialog dispatch, reproduced at WINDOW level. Minus the `MenuItem`-only
     * bits (`item.isChecked`, `invalidateOptionsMenu()`): there is no `MenuItem` on this path, and
     * the host rebuilds the whole item list instead (mirrors
     * [OptionsMenuStateBuilder.dispatch]'s identical carve-out).
     *
     * Settings editor sheets T11: the non-boolean branch carries the SAME sheet-vs-dialog
     * interception as [OptionsMenuStateBuilder.dispatch] -- a sheet-editable
     * [Preference.type] opens IN PLACE over the reading view via
     * `composeReadingViewHost.showTextSettingEditor` when the host is installed, reusing the same
     * `onReady` closure `openDialog` would otherwise have received; everything else still calls
     * `openDialog` unchanged. The scope passed is
     * `settingsBundle.toScope()` at WINDOW level, so the sheet edits THIS pane's own setting,
     * matching what the ☰ pane menu means (as opposed to the workspace-level scope
     * [OptionsMenuStateBuilder.dispatch] passes for the 3-dot overflow menu).
     */
    private fun handleWindowTextOptionItem(window: Window, order: Int): Boolean {
        val settingsBundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            windowId = window.id,
            pageManagerSettings = window.pageManager.textDisplaySettings,
            workspaceId = windowRepository.id,
            workspaceName = windowRepository.name,
            workspaceSettings = windowRepository.textDisplaySettings,
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        val itemOptions = getPrefItem(settingsBundle, CommonUtils.lastDisplaySettingsSorted[order])
        return if (itemOptions.isBoolean) {
            itemOptions.value = !(itemOptions.value == true)
            itemOptions.handle()
            true
        } else {
            val onReady: () -> Unit = { window.bibleView?.updateTextDisplaySettings() }
            val host = composeReadingViewHost
            val page = (itemOptions as? Preference)?.let { textSettingEditorPageFor(it.type.name) }
            if (page != null && host != null) {
                // WINDOW-scoped: settingsBundle.toScope() carries level=WINDOW, so the sheet edits
                // this pane's own setting — which is what the pane menu means.
                host.showTextSettingEditor(settingsBundle.toScope(), page, onReady)
                return false
            }
            itemOptions.openDialog(this, { onReady() }, onReady)
            false
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
                try {
                    return "$bookName:$pageTitleText"
                } finally {
                    BookName.setFullBookName(oldValue)
                }
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
                    try {
                        binding.pageTitle.text = pageTitleText
                    } finally {
                        BookName.setFullBookName(oldValue)
                    }
                }
            }
        } catch (_: KeyIsNull) {}
        binding.documentTitle.text = documentTitleText
        updateStrongsButton()
    }

    private fun updateStrongsButton() {
        val prefValue = dummyStrongsPrefOption.value
        val icons = if (documentControl.isNewTestament)
            intArrayOf(R.drawable.ic_strongs_greek, R.drawable.ic_strongs_greek_links, R.drawable.ic_strongs_greek_links_text)
        else
            intArrayOf(R.drawable.ic_strongs_hebrew, R.drawable.ic_strongs_hebrew_links, R.drawable.ic_strongs_hebrew_links_text)

        val iconIndex = if (prefValue as Int in 1..2) prefValue else 0
        binding.strongsButton.setImageResource(icons[iconIndex])

        binding.strongsButton.alpha = if (prefValue == 0) {
            if (CommonUtils.settings.disableAnimations) 0.8F else 0.5F
        } else 1.0F

        if (CommonUtils.settings.monochromeMode) {
            binding.strongsButton.imageTintList = ColorStateList.valueOf(Color.BLACK)
        }
    }

    private val currentDocument get() = windowControl.activeWindow.pageManager.currentPage.currentDocument
    /** Not `private`: read by [net.bible.android.view.activity.page.screen.ComposeReadingViewHost] to
     *  decide whether the compose-path Bible/Commentary button drives the swap-doc shortcut or the
     *  Compose quick-doc menu (Batch 12g Task 8). */
    internal val toolbarButtonSetting get() = preferences.getString("toolbar_button_actions", "default")

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
                menuForDocs(view,
                    commentariesForVerse
                        + SwordDocumentFacade.getBooks(BookCategory.GENERAL_BOOK)
                        + SwordDocumentFacade.getBooks(BookCategory.DICTIONARY)
                )
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
                    val intent = ScreenLauncher.intentFor(this@MainBibleActivity, Screen.WorkspaceSelector)
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
            // Batch Z-early A6: push the same two values into the Compose drawer. They are locals
            // of this function, so they must be pushed from here — the host caches them for
            // rebuilds triggered from elsewhere. No-op only if the host isn't installed yet —
            // ComposeReadingViewHost.dispose() does not null this var, so it is never a live
            // "classic" branch after that either.
            composeReadingViewHost?.rebuildDrawer(showSearch = showSearch, showSpeak = showSpeak)
            // Pre-A/B P3: same reasoning one level up — this function is classic's own "toolbar
            // state may have changed" signal (12 call sites), but only 3 of them coincide with an
            // event `ToolbarStateServiceImpl` subscribes to, so the Compose toolbar would stay
            // stale after e.g. a finished download, a document chooser result or a return from
            // background. Also a no-op before the host is installed.
            composeReadingViewHost?.refreshHostedState()
        }
    }

    /** @param type can be BIBLE or COMMENTARY */
    private fun startDocumentChooser(type: String) {
        val intent = ScreenLauncher.intentFor(this, Screen.ChooseDocument)
        intent.putExtra("type", type)
        startActivityForResult(intent, STD_REQUEST_CODE)
    }

    class AgentLogOffsetsUpdated

    /** See [updateSearchSheetOffsets]. */
    class SearchSheetOffsetsUpdated

    /** See [onComposeSearchFieldFocusChanged]. */
    class ImePaddingChanged

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            var uiFlags = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ScreenSettings.nightMode) {
                    uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }

            window.decorView.systemUiVisibility = uiFlags
        }
    }

    private fun showSystemUI(setNavBarColor: Boolean=true) {
        // Nothing here touches the reading toolbar any more: the Compose `ReadingToolbar` (via
        // `MaterialTheme.colorScheme`/`AbTheme`) owns its own colors, and `toolbarLayout` is GONE
        // (see `ComposeReadingViewHost.install`). What survives is the window-level chrome that
        // was always applied unconditionally -- the system-bar show/hide/appearance flags,
        // `navigationBarColor`. The classic `speakTransport` bar's background write went with the
        // bar itself (spec 10.4): the Compose `SpeakTransportBar` paints its own surface.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.apply {
                if (CommonUtils.settings.hideStatusBar) {
                    // Keep the navigation bar (and AndBible's own toolbar) visible, but hide only
                    // the Android status bar. Swiping from the top edge reveals it transiently.
                    show(WindowInsets.Type.navigationBars())
                    hide(WindowInsets.Type.statusBars())
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
                if (!ScreenSettings.nightMode) {
                    var appearance = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    if (CommonUtils.settings.monochromeMode) {
                        appearance = appearance or WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    }
                    // A/B batch 3 review fix (Important 1): the status-bar *icon appearance* is
                    // owned by `LocalSystemBarSync`/`applySystemBarColor` (called from
                    // `ReadingToolbar`/`AbTopAppBar` via a `SideEffect`), derived from the actual
                    // container colour rather than the classic toolbar's fixed
                    // "dark unless monochrome" rule. So `APPEARANCE_LIGHT_STATUS_BARS` is
                    // deliberately absent from the MASK: this call must neither set nor clear it,
                    // leaving the seam its single writer. (Classic cleared it in the
                    // day+non-monochrome case, right for its dark `#444444` toolbar and exactly
                    // wrong for a light M3 surface.) A consequence worth knowing before editing:
                    // the `APPEARANCE_LIGHT_STATUS_BARS` bit the monochrome clause above ORs into
                    // `appearance` is therefore INERT -- outside the mask, it is neither set nor
                    // cleared. Deleting that clause would be exactly as behaviour-neutral as
                    // keeping it; it stays to preserve the INTENT (what monochrome asks for) for
                    // the day the bit re-enters the mask, not because anything today depends on it.
                    //
                    // The NAVIGATION-bar appearance bit is not this call's alone either
                    // (whole-branch review, Minor 4). (1) Since round 12b §3,
                    // `SystemBarSync.applySystemBarColor` writes `isAppearanceLightNavigationBars`
                    // whenever `fillWindowBackground = true` (`SystemBarSync.kt:103-107`). It stays
                    // untouched in THIS window only because the two composables that sync are
                    // `ReadingToolbar` (which passes `false`, `ReadingToolbar.kt:351`) and
                    // `AbScaffold`/`AbTopAppBar` (which pass `true` but are never composed inside this
                    // activity — the reading search sheet deliberately avoids `AbTopAppBar` for
                    // exactly this reason, `SearchSheetContent.kt:50-56`). Compose an `AbScaffold`
                    // into the reading view and this mask stops being the only writer.
                    // (2) The bit written here is OVERWRITTEN a few dozen lines below, from the pane
                    // background, whenever there is any visible window — so this write is the value
                    // that survives only in the no-visible-windows path.
                    setSystemBarsAppearance(
                        appearance,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        } else {
            var uiFlags = View.SYSTEM_UI_FLAG_VISIBLE
            if (CommonUtils.settings.hideStatusBar) {
                // Hide only the status bar (not the navigation bar) while keeping the toolbar.
                uiFlags = (uiFlags
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ScreenSettings.nightMode) {
                    // Classic's SYSTEM_UI_FLAG_LIGHT_STATUS_BAR bit is not set here, mirroring the
                    // API-R+ branch's intent (Important 1). The parallel stops at the intent, and
                    // this branch IS live -- minSdk is 23. Below API 30 there is no mask: the
                    // `systemUiVisibility = uiFlags` assignment a few lines down writes every bit
                    // at once, so it also CLEARS whatever `SystemBarSync.applySystemBarColor` set
                    // through `WindowInsetsControllerCompat`, which on API < 30 targets this very
                    // flag on this very field. So the Compose seam is NOT the single writer here,
                    // whatever the API-R+ comment can say for its own masked call -- the two race,
                    // and whichever ran last wins. Pre-existing, unchanged by the flag collapse,
                    // and never audited on real API 23-29 hardware.
                    uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
            window.decorView.systemUiVisibility = uiFlags
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(windowRepository.visibleWindows.isNotEmpty()) {
                val colors = TextDisplaySettings.actual(null, windowRepository.textDisplaySettings, CommonUtils.globalTextDisplaySettings).colors!!

                val color = if (setNavBarColor && !CommonUtils.settings.monochromeMode) {
                    val color = if (ScreenSettings.nightMode) colors.nightBackground else colors.dayBackground
                    color ?: UiUtils.bibleViewDefaultBackgroundColor
                } else {
                    val typedValue = TypedValue()
                    theme.resolveAttribute(android.R.attr.navigationBarColor, typedValue, true)
                    typedValue.data
                }

                // For Android 15, be more careful with status bar and navigation bar colors
                // as some of these may be deprecated or ignored in edge-to-edge mode
                window.run {
                    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        // No `statusBarColor` write: the Compose seam owns the status bar
                        // (`LocalSystemBarSync`), same single-writer rule as the appearance mask above.
                        navigationBarColor = color
                    }
                }

                // Round 12b §3: the navigation bar's ICON contrast, on ALL API levels — the colour
                // write above is deprecated and platform-ignored from API 35, so on 35/36 nothing
                // told the system whether it is drawing 3-button icons on a light or a dark
                // surface, and the home/back glyphs could come out unreadable. `color` is the right
                // source for the DOMINANT case: with no bottom bar the WebView extends under the
                // navigation bar and `color` IS the pane background.
                //
                // KNOWN GAP, not a claim of correctness (whole-branch review, Important 3). The
                // earlier comment here said that when a bottom bar covers the strip it is "a theme
                // surface following the same day/night state, so the same value still holds". That is
                // the very premise this round's root cause disproves: a user's Bible background can
                // be LIGHT in dark mode. Night mode + `setNavBarColor` + a light night background +
                // a visible bar therefore asks for dark glyphs over the bar's dark-scheme
                // `surfaceColorAtElevation(3.dp)`, and day mode mirrors it. Not a regression (night
                // mode previously kept whatever the last day-mode pass set, also wrong) and fine for
                // default backgrounds. The fix is to sync from the OWNING BAR's container when a bar
                // owns the inset — `agentLogOwnsNavBarInset` already says which — with this
                // pane-derived value as the no-bar fallback; that hand-off is a later round.
                // Device sub-item under checklist item 3.
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    val navBarBackgroundIsLight = ColorUtils.calculateLuminance(color) >= 0.45
                    if (controller.isAppearanceLightNavigationBars != navBarBackgroundIsLight) {
                        controller.isAppearanceLightNavigationBars = navBarBackgroundIsLight
                    }
                }
            }
        }
    }

    /**
     * There are no bottom BARS left for this to lay out: the classic `speakTransport` bar and
     * `agentLogWidget` are deleted (spec 10.4), and the Compose `SpeakTransportBar`/`AgentLogPanel`
     * that replaced them place themselves inside `ReadingViewScreen` rather than being animated
     * into position from here. What survives is the restore-buttons broadcast, which was never
     * bar-specific -- `BibleView` re-reads its offsets on it.
     *
     * `transportBarVisible`/`transportBarHeight`/`bottomOffset1` are NOT dead with the bar, but for
     * two different reasons, so do not read the three as one. [transportBarVisible] and
     * [transportBarHeight] are read unconditionally by [bottomOffset2] and [bottomOffsetForWebView]
     * to size the Compose WebView's bottom padding -- that reservation is the pre-existing 12b/12f
     * double-reservation concern, tracked as a device-verification item and still not fixed here.
     * [bottomOffset1] is NOT what those two read (they take [bottomOffset1WithoutIme]); it is live
     * through [imeHeight] and through `mainBibleView`'s own bottom padding while the IME padding is
     * applied.
     */
    private fun updateBottomBars() {
        Log.i(TAG, "updateBottomBars")
        ABEventBus.post(UpdateRestoreWindowButtons())
    }

    class UpdateRestoreWindowButtons

    override fun onDestroy() {
        bibleViewFactory.clear()
        super.onDestroy()
        ABEventBus.unregister(this)
        // No-op only if the host was never installed (dispose() does not null this var, so a
        // second onDestroy call would still find it non-null); ordinarily this unregisters the
        // host's own ABEventBus subscriptions (NightModeChanged/FullScreenEvent) so an activity
        // recreation (e.g. config change) doesn't leak one registration per rotation — see
        // ComposeReadingViewHost.dispose kdoc.
        composeReadingViewHost?.dispose()
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
        CommonUtils.settings.getLong("cloud_sync_interval", DEFAULT_SYNC_INTERVAL) * 1000
    private val lastSynchronized get() =
        CommonUtils.settings.getLong("globalLastSynchronized", 0L)

    private val now get() = System.currentTimeMillis()

    private suspend fun synchronize(force: Boolean = false) {
        if(CommonUtils.isCloudSyncEnabled && networkAvailable) {
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

    private fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
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

    fun refreshIfNightModeChange(): Boolean {
        // colour may need to change which affects View colour and html
        // first refresh the night mode setting using light meter if appropriate
        ScreenSettings.checkMonitoring()
        applyTheme()
        return true
    }

    private fun updateToolbar() {
        // The Compose reading toolbar (`ComposeReadingViewHost`/`ReadingToolbar`) owns its own
        // height, padding, visibility and status-bar inset — see that class's `install()` — so
        // there is no toolbar view left for this function to lay out or animate. What remains is
        // the system-bar hide/show, which was never toolbar-specific: fullscreen must still
        // hide/show the OS status/navigation bars. The classic `speakTransport` bar's horizontal
        // padding write went with the bar itself (spec 10.4): it wrote the left/right system-bar
        // insets into a View that `ComposeReadingViewHost.install()` had already set to GONE, so
        // nothing observable was lost. The Compose `SpeakTransportBar` does not replace that write
        // -- it applies a NAVIGATION-BAR inset of its own
        // (`WindowInsets.navigationBars.exclude(WindowInsets.ime)`, and only when its
        // `applyNavBarInset` argument is true), and no horizontal or display-cutout inset at all.
        if(isFullScreen) {
            hideSystemUI()
            Log.i(TAG, "Fullscreen on")
        }
        else {
            showSystemUI()
            Log.i(TAG, "Fullscreen off")
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
            // F6 Task 8b entry point 6: retarget into the reading view's search when a Compose host
            // is mounted; classic behaviour unchanged otherwise.
            if (!composeSearchIfHosted()) {
                searchControl.getSearchIntent(windowControl.activeWindowPageManager.currentPage.currentDocument, this)?.let { intent ->
                    startActivityForResult(intent, STD_REQUEST_CODE)
                }
            }
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    class MainBibleAfterRestore

    class UpdateMainBibleActivityDocuments

    private var updateDocumentsPending = false

    private fun updateDocuments() {
        windowControl.windowSync.reloadAllWindows(true)
        updateActions()
        updateDocumentsPending = false
    }

    /**
     * Open a MyDocument page selected in the document or page chooser.
     *
     * A book's key map is a snapshot built when JSword activated it, so it can
     * be out of date with the database — and if it happened to be built while
     * the page table was unreadable, it stays empty for the rest of the
     * session. Rebuild it and retry once before falling back to opening the
     * document without a key.
     */
    private fun openMyDocumentPage(book: Book, pageKey: String) {
        val key = try {
            book.getKey(pageKey)
        } catch (e: NoSuchKeyException) {
            Log.w(TAG, "Page key '$pageKey' missing from ${book.initials} key map, rebuilding it", e)
            MyDocumentBookManager.refreshDocument(book.initials)
            try {
                book.getKey(pageKey)
            } catch (e2: NoSuchKeyException) {
                Log.e(TAG, "Page key '$pageKey' not found in ${book.initials}, opening book without key", e2)
                documentControl.changeDocument(book)
                return
            }
        }
        windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "Activity result:$resultCode")

        if (requestCode == STD_REQUEST_CODE && resultCode == Activity.RESULT_CANCELED) {
            val currentKey = windowControl.activeWindowPageManager.currentPage.key
            if (currentKey == null) {
                historyTraversal.goBack()
            }
            return
        }

        val extras = data?.extras
        if (extras != null) {
            when (requestCode) {
                WORKSPACE_CHANGED -> {
                    val workspaceId = extras.getString("workspaceId")
                    val changed = extras.getBoolean("changed")

                    if (resultCode == Activity.RESULT_OK) {
                        if (workspaceId != null && IdType(workspaceId) != currentWorkspaceId) {
                            switchToWorkspace(workspaceId)
                        } else if (changed) {
                            currentWorkspaceId = currentWorkspaceId
                        }
                    }
                    return
                }
                STD_REQUEST_CODE -> {
                    CurrentActivityHolder.activate(this) // needed because startKeyChooser is using this
                    when (val kind = ActivityResultKind.fromExtra(extras.getString(ActivityResultKind.EXTRA))) {
                        null -> {}
                        ActivityResultKind.ChooseDocument -> {
                            applyChosenDocument(extras.getString("book"))
                            return
                        }
                        ActivityResultKind.MyDocumentPages -> {
                            val bookInitials = extras.getString("documentInitials")
                            val pageKey = extras.getString("pageKey")
                            if (bookInitials != null && pageKey != null) {
                                val book = Books.installed().getBook(bookInitials)
                                if (book != null) {
                                    openMyDocumentPage(book, pageKey)
                                    updateActions()
                                }
                            }
                            return
                        }
                        ActivityResultKind.MyDocuments -> {
                            val bookInitials = extras.getString("documentInitials")
                            val pageKey = extras.getString("pageKey")
                            if (bookInitials != null) {
                                val book = Books.installed().getBook(bookInitials)
                                if (book != null) {
                                    if (pageKey != null) {
                                        openMyDocumentPage(book, pageKey)
                                    } else {
                                        documentControl.changeDocument(book)
                                    }
                                    updateActions()
                                }
                            }
                            return
                        }
                        ActivityResultKind.PassageGrid,
                        ActivityResultKind.Bookmarks,
                        ActivityResultKind.ReadingProgress -> {
                            if (kind == ActivityResultKind.ReadingProgress
                                && extras.getString("action") == "memorize") {
                                val startOrd = extras.getInt("startOrdinal")
                                val endOrd = extras.getInt("endOrdinal")
                                val defaultBible = windowControl.defaultBibleDoc(false)
                                val v11n = (defaultBible as SwordBook).versification
                                val verseRange = VerseRange(KJVA, Verse(KJVA, startOrd), Verse(KJVA, endOrd)).toV11n(v11n)
                                linkControl.openMemorize(BookAndKey(verseRange, defaultBible))
                                return
                            }
                            val isFromBookmark = kind == ActivityResultKind.Bookmarks
                            val verseStr = extras.getString("verse")
                            val keyStr = extras.getString("key")
                            val bookStr = extras.getString("book")
                            if(verseStr != null) {
                                applyChosenVerse(verseStr, isFromBookmark)
                            } else if (keyStr != null && bookStr != null) {
                                val book =
                                    Books.installed().getBook(bookStr) ?: FakeBookFactory.giveDoesNotExist(bookStr)
                                val key = book.getKey(keyStr)
                                val pageManager = windowControl.activeWindowPageManager
                                val ordinal = extras.getInt("ordinal")
                                pageManager.setCurrentDocumentAndKey(book, BookAndKey(key, book, OrdinalRange(ordinal)))
                            }
                            return
                        }
                        ActivityResultKind.GenBookKey -> {
                            val keyStr = extras.getString("key")
                            val bookStr = extras.getString("book")
                            val bookAndKeyStr = extras.getString("bookAndKey")
                            if(bookAndKeyStr != null) {
                                val bookAndKey = BookAndKeySerialized.fromJSON(bookAndKeyStr).bookAndKey
                                applyChosenGenBookKey(bookAndKey.document, bookAndKey)
                            } else {
                                val book =
                                    Books.installed().getBook(bookStr) ?: FakeBookFactory.giveDoesNotExist(bookStr!!)

                                applyChosenGenBookKey(book, book.getKey(keyStr))
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
        val volumeKeysScroll = CommonUtils.settings.getBoolean("volume_keys_scroll", true)
        if(listOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP).contains(keyCode) && !speakControl.isSpeaking && am?.isMusicActive != true && volumeKeysScroll) {
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
            // Batch Z-early A7 fix C: same close for the Compose drawer, which the native check
            // above can no longer see on the compose path. Inert only before the host is installed,
            // and placed after the classic write so its behaviour is untouched either way.
            composeCloseDrawerIfOpen()
            return true
        }

        return super.onKeyDown(keyCode, event)
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
        ABEventBus.post(SynchronizeWindowsEvent(true))
        CommonUtils.changeAppIconAndName()
        // Returning from Settings is what re-reads the toolbar snapshot (e.g. the
        // `toolbar_button_actions` swap mode) and the settings the host reads inside its
        // composition (`hide_bible_reference_overlay`, `hide_window_buttons`,
        // `full_screen_hide_buttons_pref`); the two DocumentViewManager calls this replaced had
        // been no-ops on the Compose path since Pre-A/B P3, and are gone with the classic split.
        composeReadingViewHost?.refreshHostedState(rebuildComposition = true)
    }

    private fun requestSdcardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val requestSdCardPermission = preferences.getBoolean(REQUEST_SDCARD_PERMISSION_PREF, false)
            if (requestSdCardPermission && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), SDCARD_READ_REQUEST)
            }
        }
    }

    private fun setSoftKeyboardMode() {
        // Android 15 edge-to-edge enforcement fix:
        // When targeting API 35+, traditional adjustPan/adjustResize may not work properly
        // with edge-to-edge mode. Use adjustNothing and handle keyboard insets manually
        // through WindowInsetsCompat.Type.ime() for better compatibility.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Try adjustNothing first for proper edge-to-edge behavior
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        } else if (windowControl.isMultiWindow) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
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

        // Check for pending AI agent results that completed while app was backgrounded
        handlePendingAgentResult()
    }

    private fun handlePendingAgentResult() {
        val session = AgentSessionManager.getCurrentSession() ?: return
        val result = session.pendingResult ?: return
        session.pendingResult = null
        when (result) {
            is PendingAgentResult.OpenDocument -> {
                linkControl.openAIDocument(result.documentInitials, result.pageKey)
            }
            is PendingAgentResult.OpenStudyPad -> {
                linkControl.openStudyPad(result.labelId, result.scrollToEntryId)
            }
        }
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
            ABEventBus.register(this, eventSubscriptions)
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

    // compose: unused entry (no caller) — kept classic-only; not routed through the Compose LLM
    // dialog host (Batch 12e-A T6) since it bypasses the prompt-selector dialog entirely.
    fun executeLlmPrompt(prompt: AgentPrompt, selection: Selection) =
        llmDialogHelper.maybeAskModel(prompt, selection, userSpecification = null)

    fun showLlmPromptSelector(selection: Selection, context: PromptContext = PromptContext.VERSE_SELECTION) {
        val documentCategory = windowRepository.activeWindow.pageManager.currentPage.documentCategory
        composeReadingViewHost?.showPromptSelector(selection, context, documentCategory)
    }

    /** Bridge for `BibleJavascriptInterface.regenerateMyDocumentPage` (Batch 12e-A T6): the Compose
     *  LLM dialog host's regenerate confirmation, over the reading view. */
    fun showRegenerate(pageId: IdType, bibleView: BibleView) {
        composeReadingViewHost?.showRegenerate(pageId, bibleView)
    }

    companion object {
        var initialized = false
        private const val SDCARD_READ_REQUEST = 2

        const val WORKSPACE_CHANGED = 94

        private const val REQUEST_SDCARD_PERMISSION_PREF = "request_sdcard_permission_pref"
    }
}

