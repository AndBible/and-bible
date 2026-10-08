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

package net.bible.android.view.activity.base

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.view.activity.StartupActivity
import net.bible.android.view.activity.comingFromStartupActivity
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.util.UiUtils.setActionBarColor
import net.bible.android.view.util.VolumeButtonScroll
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.history.HistoryTraversal
import net.bible.service.history.HistoryTraversalFactory
import org.koin.android.ext.android.inject

var firstTime = true

/** Base class for activities
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class ActivityBase : AppCompatActivity(), AndBibleActivity {
    private var isScreenOn = true

    // some screens are highly customised and the theme looks odd if it changes
    open val allowThemeChange = true
    open val disableBaseSetupUi = false
    open val integrateWithHistoryManager: Boolean = false

    protected lateinit var historyTraversal: HistoryTraversal

    open val doNotInitializeApp = false

    private var doNotMarkPaused = false
    private var wasPaused = false
    private var returningFromCalculator = false

    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    public override fun onCreate(savedInstanceState: Bundle?) {
        CurrentActivityHolder.activate(this)
        setNewHistoryTraversal(historyTraversalFactory)

        if(!doNotInitializeApp) {
            CommonUtils.initializeApp()
        }

        if (allowThemeChange) {
            applyTheme()
        }

        super.onCreate(savedInstanceState)
        if (!disableBaseSetupUi) {
            setupUi()
        }

        if(!doNotInitializeApp) {
            applyInitialisedWindowState()
        }

        Log.i(TAG, "onCreate")

        // if locale is overridden then have to force title to be translated here
        LocaleHelper.translateTitle(this)
        setActionBarColor(supportActionBar)

        wasPaused = false
        Log.i(TAG, "onCreate: loading state: $savedInstanceState")
        if(savedInstanceState != null) {
            doNotMarkPaused = savedInstanceState.getBoolean("doNotMarkPaused", false)
            wasPaused = savedInstanceState.getBoolean("wasPaused", false)
            returningFromCalculator = savedInstanceState.getBoolean("returningFromCalculator", false)
        }
        fixNightMode()
    }

    /**
     * The window state an initialised app applies: FLAG_SECURE while the calculator disguise is on (Recents
     * must not show content) and the keep-screen-on preference. Extracted in slice 8 (plan Correction 6) so a
     * host that STARTED uninitialised -- `NavHostComposeActivity` on WELCOME or BACKUP -- can apply it when
     * it initialises later (D1: entering InstallZip; E2: Welcome's gate (b)).
     */
    protected fun applyInitialisedWindowState() {
        if(CommonUtils.showCalculator) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        refreshScreenKeepOn()
    }

    private fun setupUi() {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            rootView?.let { root ->
                ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    // Apply default padding to prevent content overlap
                    // For Android 15+, apply system bar insets as padding to the root view
                    view.setPadding(
                        insets.left,
                        insets.top,
                        insets.right,
                        insets.bottom
                    )
                    windowInsets
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ScreenSettings.nightMode) {
                val uiFlags = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = uiFlags
            }
        }
    }

    open fun fixNightMode() {
        // First launched activity is not having proper night mode if we are using manual mode.
        // This hack fixes it.
        if(firstTime && allowThemeChange && !doNotInitializeApp) {
            firstTime = false
            lifecycleScope.launch {
                delay(250)
                recreate()
            }
            return
        }
    }

    fun applyTheme() {
        val newNightMode = if (ScreenSettings.nightMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        Log.i(TAG, "applyTheme: nightMode = ${ScreenSettings.nightMode}")
        AppCompatDelegate.setDefaultNightMode(newNightMode)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("doNotMarkPaused", doNotMarkPaused)
        outState.putBoolean("wasPaused", wasPaused)
        outState.putBoolean("returningFromCalculator", returningFromCalculator)
        Log.i(TAG, "Saving saved state from $outState")

        super.onSaveInstanceState(outState)
    }

    override fun startActivity(intent: Intent) {
        if(integrateWithHistoryManager) {
            historyTraversal.beforeStartActivity()
        }

        super.startActivity(intent)
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        if(integrateWithHistoryManager) {
            historyTraversal.beforeStartActivity()
        }

        super.startActivityForResult(intent, requestCode)
    }

    /**
     * Override locale.  If user has selected a different ui language to the devices default language
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.localized(newBase))
    }

    /**
     * Step one entry back in the reading history, if there is one.
     *
     * Called by the history-route destinations' back handlers and the chooser cancel arms: reading-host
     * re-typing T8b moved `CurrentGeneralBookPage`'s three key-chooser arms onto [awaitIntent], and its
     * `STD_REQUEST_CODE` + `RESULT_CANCELED` guard goes back when a cancelled chooser has left the page with
     * no key at all.
     *
     * `open` since slice 8 B1: `NavHostComposeActivity` replays the reading history directly while
     * its graph is on `reading`, because its host-global `isIntegrateWithHistoryManager` is off there
     * and [HistoryTraversal.goBack] would refuse (classic `MainBibleActivity` had it on).
     */
    open fun goBackInHistory(): Boolean =
        ::historyTraversal.isInitialized && historyTraversal.goBack()

    /**
     * Leave the screen the user is looking at (slice 8 spec §5.1 item 2). For a classic one-screen
     * Activity that is finishing it; `NavHostComposeActivity` overrides it to pop its back stack, because
     * there the "screen" is a destination and finishing would take every other destination -- the
     * reading view included -- with it. `HistoryManager.goBack()` is the caller.
     */
    open fun leaveCurrentScreen() {
        finish()
    }

    /**
     * Whether this activity should let the base class handle volume-key page scrolling.
     * Screens that own the volume keys themselves (e.g. the reading destination) override to false.
     */
    protected open val enableGenericVolumeScroll: Boolean get() = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (enableGenericVolumeScroll &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            CommonUtils.settings.getBoolean("volume_keys_scroll", true)
        ) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager?.isMusicActive != true) {
                val root = findViewById<View>(android.R.id.content)
                val scrollable = root?.let { VolumeButtonScroll.findScrollableView(it) }
                if (scrollable != null) {
                    VolumeButtonScroll.scroll(
                        scrollable,
                        down = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN,
                        animate = !CommonUtils.settings.disableAnimations
                    )
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** called by Android 2.0 +
     */
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        // ignore long press on search because it causes errors
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            // ignore
            return true
        }

        //TODO make Long press back - currently the History screen does not show the correct screen after item selection if not called from main window
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            // ignore
            true
        } else super.onKeyLongPress(keyCode, event)

    }

    override var isIntegrateWithHistoryManager: Boolean
        get() = historyTraversal.isIntegrateWithHistoryManager
        set(value) {
            historyTraversal.isIntegrateWithHistoryManager = value
        }

    /** allow activity to enhance intent to correctly restore state  */
    override val intentForHistoryList: Intent get() = intent

    override fun onResume() {
        CurrentActivityHolder.activate(this)
        super.onResume()
        Log.i(TAG, "onResume wasPaused:$wasPaused returningFromCalculator:$returningFromCalculator")
        val fromStartupActivity = comingFromStartupActivity
        comingFromStartupActivity = false
        if (
            this !is CalculatorComposeActivity
            && !fromStartupActivity
            && this !is StartupActivity
            && CommonUtils.showCalculator
            && wasPaused
            && !returningFromCalculator
        ) {
            val handlerIntent = ScreenLauncher.intentFor(this@ActivityBase, Screen.Calculator)
            startActivityForResult(handlerIntent, CALCULATOR_REQUEST)
            returningFromCalculator = true
        } else {
            returningFromCalculator = false
        }
        wasPaused = false

        //allow action to be called on screen being turned on
        if (!isScreenOn && ScreenSettings.isScreenOn) {
            onScreenTurnedOn()
        }
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        doNotMarkPaused = true
        super.startActivity(intent, options)
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        doNotMarkPaused = true
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onPause() {
        super.onPause()
        if(!doNotMarkPaused) {
            wasPaused = true
        }
        doNotMarkPaused = false
        Log.i(TAG, "onPause: $this")
        if (isScreenOn && !ScreenSettings.isScreenOn) {
            onScreenTurnedOff()
        }
        closeKeyboard()
    }


    fun closeKeyboard() {
        // Most of our activities have no input field, so having no focused view is the normal case
        // and not an error: there is no keyboard to close. It used to be logged as an NPE from
        // every single onPause, which made real crashes with the same message hard to spot.
        val windowToken = currentFocus?.windowToken ?: return
        try {
            val inputMethodManager: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
        } catch (e: Exception) {
            Log.e(TAG, "closeKeyboard: $e")
        }
    }

    protected open fun onScreenTurnedOff() {
        Log.i(TAG, "Window turned off")
        isScreenOn = false
    }

    protected open fun onScreenTurnedOn() {
        Log.i(TAG, "Window turned on")
        isScreenOn = true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    override fun onRestart() {
        super.onRestart()
        if(!doNotInitializeApp) {
            refreshScreenKeepOn()
        }
        Log.i(TAG, "onRestart")
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "onNewIntent $this ${intent.action}")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop")
        CurrentActivityHolder.deactivate(this)
    }

    /**
     * Each activity instance needs its own HistoryTraversal object
     * @param historyTraversalFactory
     */
    private val historyTraversalFactory: HistoryTraversalFactory by inject()

    fun setNewHistoryTraversal(historyTraversalFactory: HistoryTraversalFactory) {
        // Ensure we don't end up overwriting the initialised class
        if (!::historyTraversal.isInitialized) {
            this.historyTraversal = historyTraversalFactory.createHistoryTraversal(integrateWithHistoryManager)
        }
    }

    private var currentCode : Int = 0
    private var resultByCode = mutableMapOf<Int, CompletableDeferred<ActivityResult>>()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult: requestCode = $requestCode, resultCode = $resultCode, data is${if (data != null) " not" else ""} null")
        if(requestCode == CALCULATOR_REQUEST) {
            if(resultCode == RESULT_CANCELED) {
                finishAffinity()
            }
            return
        }
        resultByCode[requestCode - ASYNC_REQUEST_CODE_START]?.let {
            it.complete(ActivityResult(resultCode, data))
            resultByCode.remove(requestCode - ASYNC_REQUEST_CODE_START)
        } ?: run {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Fix batch 1 §2.3 (F99): always on Main. Callers await from any dispatcher (BibleView's
     * assignLabels ran on IO), but everything below is main-thread state: `resultByCode` and
     * `currentCode` are plain fields, and since F53 (`aca478714`) a self-launch here is a
     * `NavController.navigate`, which asserts the main thread.
     */
    suspend fun awaitIntent(intent: Intent): ActivityResult = withContext(Dispatchers.Main.immediate) {
        val activityResult = CompletableDeferred<ActivityResult>()
        val resultCode = currentCode++
        resultByCode[resultCode] = activityResult
        startActivityForResult(intent, resultCode + ASYNC_REQUEST_CODE_START)
        activityResult.await()
    }

    protected val preferences get() = CommonUtils.settings

    private var deferredActivityResult = CompletableDeferred<ActivityResult>()
    private val deferredActivityResultMutex = Mutex()

    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            deferredActivityResult.complete(it)
        }
    suspend fun awaitPendingIntent(pendingIntent: PendingIntent): ActivityResult = deferredActivityResultMutex.withLock {
        val defer = CompletableDeferred<ActivityResult>()
        deferredActivityResult = defer
        intentSenderLauncher.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        )
        return defer.await()
    }


    private fun refreshScreenKeepOn() {
        val keepOn = preferences.getBoolean(SCREEN_KEEP_ON_PREF, false)
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Swap this Activity's content view (and its owner-stream subscriptions) out while ANOTHER Activity is on top,
     * and back when it returns; [CurrentActivityHolder.activate]/[CurrentActivityHolder.deactivate] are the only
     * callers. `MainBibleActivity` was the only override and was deleted in slice 8; the hooks are kept because a
     * second live `NavHostComposeActivity` is still reachable through `StartupActivity`'s `ACTION_VIEW` handoff --
     * see `CurrentActivityHolder.activate`. Pinned by `ReadingDestinationInGraphTest.freezeAndUnFreezeStayWhileASecondHostIsReachable`.
     */
    open fun freeze() {}

    /** @see freeze */
    open fun unFreeze() {}

    val TAG get() = "Base-${this::class.java.simpleName}"

    companion object {
        private const val SCREEN_KEEP_ON_PREF = "screen_keep_on_pref"

        // standard request code for startActivityForResult
        const val STD_REQUEST_CODE = 1
        const val CALCULATOR_REQUEST = 6000
        const val ASYNC_REQUEST_CODE_START = 1900
    }
}

/**
 * The pixel size of a dimension attribute on this Activity's theme, or `0` when the theme does not
 * define it.
 *
 * Reading-host re-typing R6d fix round 1 (review Important). `MainBibleActivity.resolveVariables`
 * spelled this out three times inline, and R6d then spelled a fourth copy into
 * `NavHostComposeActivity` so its inset ledger could answer `transportBarHeight`/
 * `windowButtonHeight`. Both hosts are live at once until slice 7 Task 13, so a copy in each is
 * the divergence `ReadingChromePortDriftTest` was built for -- and this one is pure Android with
 * no host state in it, so the honest fix is one implementation rather than a guard over four.
 *
 * `0` on an unresolved attribute is exactly what classic's `if (theme.resolveAttribute(...))`
 * left behind: its three fields are initialised to `0` and `resolveVariables()` runs once, from
 * `onCreate`.
 */
fun android.app.Activity.themePixelSize(@AttrRes attr: Int): Int {
    val tv = TypedValue()
    return if (theme.resolveAttribute(attr, tv, true)) {
        TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
    } else 0
}
