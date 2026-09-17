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
import androidx.appcompat.widget.PopupMenu
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import net.bible.sharedcore.reading.ReadingViewVisibility
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

class SpeakTransportVisibilityChanged(val value: Boolean)

class MainBibleActivity : CustomTitlebarActivityBase(), ReadingHostActivity {
    lateinit var binding: MainBibleViewBinding
    lateinit var empty: EmptyBinding
    /** The placeholder [freeze] swaps in while another Activity is on top of this one. */
    lateinit var frozenBinding: FrozenBinding

    private var mWholeAppWasInBackground = false

    // We need to have this here in order to initialize BibleContentManager early enough.
    val windowControl: WindowControl by inject()

    /**
     * The app bootstrap (reading-host re-typing R7, design spec §3.4). The eight app-startup
     * responsibilities `onCreate` used to perform inline live here now and are called by BOTH hosts;
     * this Activity holds no copy of them. Built as a field initialiser, so it exists before
     * `onCreate` runs and before [windowRepository] is first read.
     */
    internal val readingAppBootstrap = ReadingAppBootstrap(this)
    val speakControl: SpeakControl by inject()
    val bookmarkControl: BookmarkControl by inject()

    // handle requests from main menu
    val searchControl: SearchControl by inject()
    val documentControl: DocumentControl by inject()
    val navigationControl: NavigationControl by inject()
    val pageControl: PageControl by inject()
    val linkControl: LinkControl by inject()

    lateinit var documentViewManager: DocumentViewManager

    /**
     * Reading-host re-typing R3: the ~35-member COMMAND SURFACE this activity used to hold inline
     * (design spec §3.2). Constructed here, once per host -- it owns [BibleViewFactory],
     * [MenuCommandHandler] and the text-settings image picker, all of which are per-host instance
     * objects rather than Koin singletons, so a second instance would be a silent bug.
     * `ReadingCommandsDelegationTest` pins that with `assertSame`.
     */
    val readingCommands = ReadingCommands(this)

    /** The per-host `BibleView` cache. R3 moved the instance itself onto [readingCommands]; this is
     *  a view onto that one object, never a second one. */
    val bibleViewFactory get() = readingCommands.bibleViewFactory

    /** Assigned unconditionally in [setupUi]; stays a nullable `var` because external readers
     * reach it through `as? MainBibleActivity` and are legitimately null for any other foreground
     * activity. Lets [DocumentViewManager.buildView] mirror classic's forced recreate (see its
     * kdoc). */
    var composeReadingViewHost: ComposeReadingViewHost? = null
    private val mainMenuCommandHandler get() = readingCommands.mainMenuCommandHandler

    /**
     * R3: the in-place colours editor's own image picker. The registration, the pending
     * continuation and this lambda all live on [ReadingCommands] now (design spec §3.2); this
     * delegating property survives because `ComposeReadingViewHost` reads it ON THE ACTIVITY and is
     * not re-typed until R6.
     */
    internal val textSettingsImagePicker: suspend () -> String? get() = readingCommands.textSettingsImagePicker

    val llmDialogHelper = LlmDialogHelper(this)

    private val navigationView: NavigationView by lazy {
        binding.drawerLayout.findViewById(R.id.navigationView)!!
    }

    private val versionTextView: TextView by lazy {
        binding.drawerLayout.findViewById(R.id.versionText)!!
    }

    private var navigationBarHeight = 0
    private var actionBarHeight = 0
    internal var transportBarHeight = 0
    internal var windowButtonHeight = 0

    private var hasHwKeys: Boolean = false

    internal var transportBarVisible = false
        get() = if (isFullScreen) false else field
        set(value) {
            if (field == value) return
            binding.speakButton.alpha = if(value) 0.7F else 1.0F
            field = value
            ABEventBus.post(SpeakTransportVisibilityChanged(value))
        }

    // Agent log widget visibility and height for offset calculation
    internal var agentLogVisible = false
    internal var agentLogHeight = 0

    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()

    val multiWinMode
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInMultiWindowMode else false

    // Reading-host re-typing R2: the window-inset ledger (topOffset2, bottomOffset1(WithoutIme),
    // bottomOffset2, bottomOffsetForWebView, imeHeight, imePaddingApplied, the search-sheet offsets
    // pair and the setOnApplyWindowInsetsListener body) moved to ReadingInsets — design spec §3.3.
    // BibleView's three reads go through `readingInsets` directly; the thin delegations below exist
    // because ComposeReadingViewHost and the Robolectric net (ReadingSearchEntryPointsTest) still call
    // these members by these names on the Activity and are not re-typed until R6.
    val readingInsets = ReadingInsets(this)
    val topOffset2 get() = readingInsets.topOffset2
    val bottomOffset2 get() = readingInsets.bottomOffset2
    val bottomOffsetForWebView get() = readingInsets.bottomOffsetForWebView
    val imeHeight get() = readingInsets.imeHeight

    /**
     * Whether the Compose reading-view search field currently holds focus. `false` only in the
     * brief window before `setupUi()` installs the host.
     */
    internal val composeSearchFieldFocused: Boolean
        get() = composeReadingViewHost?.searchFieldFocused?.value == true

    internal fun updateSearchSheetOffsets(visible: Boolean, heightPx: Int) =
        readingInsets.updateSearchSheetOffsets(visible, heightPx)

    internal fun onComposeSearchFieldFocusChanged() = readingInsets.onComposeSearchFieldFocusChanged()

    internal val restoreButtonsVisible get() = windowRepository.workspaceSettings.restoreButtonsVisible

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
                readingAppBootstrap.stopPeriodicSync()
                syncScope.launch { readingAppBootstrap.synchronize(true) }
            } else {
                updateActions()
                syncScope.launch { readingAppBootstrap.startSync() }
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
            readingAppBootstrap.setSoftKeyboardMode()
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
        // TEMPORARY, with the three other setActivityVisible calls below (onResume, onPause,
        // onActivityResult): they go away with the task that makes the reading destination's
        // `content` slot render the real reading view. Until then `ComposeReadingViewHost` is
        // constructed with a MainBibleActivity, so this Activity is the only live reading view and
        // the destination's DisposableEffect never runs — leaving the flag with no owner at all.
        //
        // This has to be here and not only in onResume: `ActivityBase.onCreate`'s FIRST line is
        // `CurrentActivityHolder.activate(this)`, so the OLD predicate (`currentActivity is
        // MainBibleActivity`) was true for the whole of onCreate/onStart — and this very method
        // posts AddHistoryItem inside that window, through the `openLink` deep-link branch below
        // (-> WindowControl.showLink -> setCurrentDocumentAndKey -> CurrentPageBase.setKey(
        // addHistoryItem = true) -> ABEventBus.post(AddHistoryItem), and the bus is synchronous).
        // With the flag false there, createHistoryItem would not merely drop the item: it would
        // fall through to the `currentActivity is AndBibleActivity` arm — this class IS one, with
        // integrateWithHistoryManager = true — and record a WRONG IntentHistoryItem carrying the
        // deep-link intent, whose revertTo() re-starts it. The same window is what keeps goBack()'s
        // condition honest after a "Don't keep activities" recreation; see HistoryManager.goBack.
        ReadingViewVisibility.setActivityVisible(true)

        readingAppBootstrap.prepareData()

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


        readingAppBootstrap.createWindowRepository()

        documentViewManager = DocumentViewManager(bibleViewFactory) { composeReadingViewHost?.rebuild() }

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
        readingAppBootstrap.setSoftKeyboardMode()

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
            readingAppBootstrap.showFirstRunNotices()
        }
        if(intent.hasExtra("openLink")) {
            val uri = Uri.parse(intent.getStringExtra("openLink"))
            readingAppBootstrap.openDeepLink(uri)
        }
        readingAppBootstrap.registerNetworkCallback()
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
            // Reading-host re-typing R2: the Activity keeps this listener's REGISTRATION (it owns
            // the window) and forwards the offset computation into ReadingInsets — design spec §3.3.
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
                val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

                readingInsets.onWindowInsetsApplied(systemBarInsets, imeInsets)

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

    // ---- Compose command surface: delegating stubs (reading-host re-typing R3) ----
    // Every member from here down that forwards to [readingCommands] used to hold its own body in
    // this file. R3 moved the bodies to [ReadingCommands] (design spec §3.2) and left these stubs,
    // which are LOAD-BEARING: `ComposeReadingViewHost` and the eight Robolectric classes that are
    // this batch's safety net call these members on the Activity and are not re-typed until R6.
    // Each stub is a pure delegation -- no branch, no state, nothing to drift.

    override fun toggleDrawer() = readingCommands.composeToggleDrawer()

    internal val composeDrawerOpen: Boolean get() = readingCommands.composeDrawerOpen

    internal fun composeCloseDrawerIfOpen(): Boolean = readingCommands.composeCloseDrawerIfOpen()

    internal fun composeOpenDrawerIfHosted(): Boolean = readingCommands.composeOpenDrawerIfHosted()

    // ---- Compose drawer side-effect parity (Batch Z-early A7) ----
    // The classic `DrawerLayout.DrawerListener` installed in `setupUi` (:634-665) has three side
    // effects; on the compose path the native listener never fires (that drawer is locked closed),
    // so `ComposeReadingViewHost` derives the same edges from the Material3 `DrawerState` and calls
    // these. Each one only *forwards* to the private logic classic already used, so nothing on the
    // classic path changes — and nothing on the classic path calls them.

    /** Compose-drawer parity for classic `STATE_SETTLING`/`STATE_DRAGGING` → `showSystemUI(false)`. */
    override fun showSystemUiTransient() { showSystemUI(false) }

    /** Compose-drawer parity for classic `STATE_IDLE` at slide offset 0. */
    override fun applyIdleSystemUi() {
        if (isFullScreen) hideSystemUI() else showSystemUI()
    }

    /**
     * Compose-drawer parity for classic `onDrawerClosed` — see
     * [net.bible.sharedcore.reading.shouldRestorePaneFocusOnDrawerClose] for why it is conditional.
     * Exposed separately from [restorePaneFocus] so the decision is assertable as a plain
     * function call, rather than only by rendering the drawer and the search bar together. (Not for
     * want of a Compose UI test harness — the earlier wording here claimed the repo has none and can
     * get none, which is false: `compose-ui-test` is in `:app`'s test source set and
     * `AbSearchableOptionSheetContentTest` uses `createComposeRule()`. Whole-branch review, Blocker 2.)
     */
    internal fun drawerShouldRestorePaneFocus(): Boolean =
        shouldRestorePaneFocusOnDrawerClose(searchBarOpen = composeSearchModeActive)

    /** Compose-drawer parity for classic `onDrawerClosed`. */
    override fun restorePaneFocus() {
        if (!drawerShouldRestorePaneFocus()) return
        windowRepository.activeWindow.bibleView?.requestFocus()
    }

    internal fun composeSearch() = readingCommands.composeSearch()

    internal fun composeSearchIfHosted(seedQuery: String? = null, preDecorated: Boolean = false): Boolean =
        readingCommands.composeSearchIfHosted(seedQuery, preDecorated)

    internal fun composeSearchStrongsIfHosted(ref: String, translationIds: List<String>): Boolean =
        readingCommands.composeSearchStrongsIfHosted(ref, translationIds)

    internal fun composeCloseSearchIfOpen(): Boolean = readingCommands.composeCloseSearchIfOpen()

    internal val composeSearchModeActive: Boolean get() = readingCommands.composeSearchModeActive

    internal fun composeToggleSpeak() = readingCommands.composeToggleSpeak()

    internal fun composeShowSpeakTransport() = readingCommands.composeShowSpeakTransport()

    internal fun composeSpeakLong() = readingCommands.composeSpeakLong()

    /**
     * Switch to a workspace by id. Extracted from the WORKSPACE_CHANGED result arm so round 15b's
     * quick sheet and the full selector's activity result cannot drift apart.
     */
    internal fun switchToWorkspace(workspaceId: String) {
        currentWorkspaceId = IdType(workspaceId)
    }

    internal fun quickSwitchToWorkspace(workspaceId: String) = readingCommands.quickSwitchToWorkspace(workspaceId)

    internal fun composeCycleWorkspace(forward: Boolean) = readingCommands.composeCycleWorkspace(forward)

    internal fun composeStartKeyChooser() = readingCommands.composeStartKeyChooser()

    internal fun composeChooseDocument() = readingCommands.composeChooseDocument()

    internal fun applyChosenDocument(bookStr: String?) = readingCommands.applyChosenDocument(bookStr)

    internal fun applyChosenVerse(verseStr: String, isFromBookmark: Boolean = false) =
        readingCommands.applyChosenVerse(verseStr, isFromBookmark)

    internal fun applyChosenGenBookKey(book: Book?, key: Key) = readingCommands.applyChosenGenBookKey(book, key)

    internal fun composeCycleStrongs() = readingCommands.composeCycleStrongs()

    internal fun composeStrongsLong() = readingCommands.composeStrongsLong()

    internal fun composeBibleClick(anchor: View) = readingCommands.composeBibleClick(anchor)

    internal fun composeBibleLongClick() = readingCommands.composeBibleLongClick()

    internal fun composeCommentaryClick(anchor: View) = readingCommands.composeCommentaryClick(anchor)

    internal fun composeCommentaryLongClick() = readingCommands.composeCommentaryLongClick()

    internal fun composeQuickDocItems(books: List<Book>): List<QuickDocMenuItem> =
        readingCommands.composeQuickDocItems(books)

    internal fun composeQuickDocSelect(id: String) = readingCommands.composeQuickDocSelect(id)

    internal fun windowLabelFor(id: String): String = readingCommands.windowLabelFor(id)

    internal fun windowTopLabelFor(id: String): String? = readingCommands.windowTopLabelFor(id)

    internal fun windowIconFor(id: String): Painter? = readingCommands.windowIconFor(id)

    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal val dummyStrongsPrefOption
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
    /**
     * R7: a view onto [ReadingAppBootstrap.windowRepository], not a second field. It stays a
     * settable `var` because seven of the batch's untouchable Robolectric classes assign
     * `activity.windowRepository = ...` after `controller.create()`, and the bootstrap's sync path
     * must keep reading whatever they assigned -- which it does, because there is only ever one
     * field. Keeping one field is also what preserves `onCreate`'s ordering: classic assigned this
     * property BEFORE `initialize()`, and `createWindowRepository` still does.
     */
    var windowRepository: WindowRepository
        get() = readingAppBootstrap.windowRepository
        set(value) { readingAppBootstrap.windowRepository = value }

    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal fun cycleWorkspace(forward: Boolean) {
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

    fun buildOptionsMenuItems(): List<OptionsMenuItem> = readingCommands.buildOptionsMenuItems()

    fun handleOptionsMenuItem(id: String): Boolean = readingCommands.handleOptionsMenuItem(id)

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

    internal fun handleDrawerItemClick(itemId: Int) = readingCommands.handleDrawerItemClick(itemId)

    fun handleWindowPaneMenuItem(windowId: String, id: String): Boolean =
        readingCommands.handleWindowPaneMenuItem(windowId, id)

    private val documentTitleText: String
        get() = pageControl.currentPageManager.currentPage.currentDocumentName

    class KeyIsNull: Exception()

    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal val pageTitleText: String
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

    val bibleOverlayText: String get() = readingCommands.bibleOverlayText

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

    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal fun updateStrongsButton() {
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

    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal val currentDocument get() = windowControl.activeWindow.pageManager.currentPage.currentDocument
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
    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal fun startDocumentChooser(type: String) {
        val intent = ScreenLauncher.intentFor(this, Screen.ChooseDocument)
        intent.putExtra("type", type)
        startActivityForResult(intent, STD_REQUEST_CODE)
    }

    class AgentLogOffsetsUpdated

    /** See [updateSearchSheetOffsets]. */
    class SearchSheetOffsetsUpdated

    /** See [onComposeSearchFieldFocusChanged]. */
    class ImePaddingChanged


    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal fun menuForDocs(v: View, documents: List<Book>) {
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

    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal fun setCurrentDocument(book: Book?) {
        windowControl.activeWindow.pageManager.setCurrentDocument(book)
        if(book != null) {
            val bookCategory = book.bookCategory
            // see net.bible.android.control.page.CurrentPageBase.getDefaultBook
            CommonUtils.settings.setString("default-${bookCategory.name}", book.initials)
        }
    }

    class FullScreenEvent(val isFullScreen: Boolean)
    private var isFullScreen = false

    /**
     * [ReadingHostActivity.hostContext] — this Activity, as the plain Context the reading view's
     * bare value-passes want (spec §2.1).
     */
    override val hostContext: Context get() = this

    override var fullScreen
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
    // R3: widened from `private` to `internal` so [ReadingCommands] can reach it. It stays HERE
    // because the CLASSIC toolbar/`updateActions()` path still calls it too (design spec §3.2).
    internal fun updateBottomBars() {
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

    private val now get() = System.currentTimeMillis()

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
                    // TEMPORARY, see onCreate. onActivityResult runs BEFORE onResume, and the
                    // chooser results handled below call setKey(…, addHistoryItem = true) /
                    // setCurrentDocument(…), which post AddHistoryItem. The activate() above exists
                    // so the OLD predicate is true for exactly those posts; this line keeps the NEW
                    // predicate true at the same moment, so the swap really is behaviour-neutral
                    // while the reading view is still an Activity.
                    ReadingViewVisibility.setActivityVisible(true)
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
        readingAppBootstrap.requestSdcardPermission()
        ABEventBus.post(SynchronizeWindowsEvent(true))
        CommonUtils.changeAppIconAndName()
        // Returning from Settings is what re-reads the toolbar snapshot (e.g. the
        // `toolbar_button_actions` swap mode) and the settings the host reads inside its
        // composition (`hide_bible_reference_overlay`, `hide_window_buttons`,
        // `full_screen_hide_buttons_pref`); the two DocumentViewManager calls this replaced had
        // been no-ops on the Compose path since Pre-A/B P3, and are gone with the classic split.
        composeReadingViewHost?.refreshHostedState(rebuildComposition = true)
    }


    /**
     * R7: a view onto [ReadingAppBootstrap.hostPaused]. One flag, written here by `onPause`/
     * `onResume` exactly as before, read by this class's event guards AND by the extracted
     * `networkCallback`'s `onAvailable` -- which is why it is the bootstrap that stores it.
     */
    private var paused: Boolean
        get() = readingAppBootstrap.hostPaused
        set(value) { readingAppBootstrap.hostPaused = value }
    override fun onPause() {
        // TEMPORARY, see onCreate. HistoryManager asks ReadingViewVisibility instead of
        // `CurrentActivityHolder.currentActivity is MainBibleActivity` (spec §5.1); while the
        // reading view is still an Activity, this Activity's lifecycle is what drives the flag's
        // Activity input. The `reading` destination's DisposableEffect owns the OTHER input and is
        // untouched by this call.
        ReadingViewVisibility.setActivityVisible(false)
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
        // TEMPORARY, see onCreate.
        ReadingViewVisibility.setActivityVisible(true)
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

    /**
     * See [net.bible.android.view.activity.base.ActivityBase.freeze] for why this still exists.
     *
     * The `mainBibleActivities < 2` guard is the ordinary case: with only ONE reading Activity
     * alive, whatever is on top of it is a secondary screen, and the reading view underneath must
     * stay exactly as it is. Only a SECOND `MainBibleActivity` — which
     * `StartupActivity.gotoMainBibleActivity()`'s `FLAG_ACTIVITY_MULTIPLE_TASK` branch really does
     * create for an `ACTION_VIEW` deep link — makes freezing the right answer, and the
     * `ABEventBus.unregister(this)` below is the half that matters: without it both instances
     * handle every bus event.
     */
    override fun freeze() {
        if(CurrentActivityHolder.mainBibleActivities < 2) return
        if(!frozen) {
            ABEventBus.unregister(this)
            (window.decorView as ViewGroup).removeView(binding.root)
            super.setContentView(frozenBinding.root)
        }
        frozen = true
    }

    /** @see freeze */
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
        const val WORKSPACE_CHANGED = 94
    }
}

