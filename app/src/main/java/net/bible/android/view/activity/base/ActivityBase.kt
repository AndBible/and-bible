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

package net.bible.android.view.activity.base

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.android.view.activity.StartupActivity
import net.bible.android.view.activity.comingFromStartupActivity
import net.bible.android.view.activity.discrete.CalculatorActivity
import net.bible.android.view.util.UiUtils.setActionBarColor
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.history.HistoryTraversal
import net.bible.service.history.HistoryTraversalFactory
import javax.inject.Inject

var firstTime = true

/** Base class for activities
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class ActivityBase : AppCompatActivity(), AndBibleActivity {
    private var isScreenOn = true

    // some screens are highly customised and the theme looks odd if it changes
    open val allowThemeChange = true
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

        if(!doNotInitializeApp) {
            CommonUtils.initializeApp()
        }

        if (allowThemeChange) {
            applyTheme()
        }

        super.onCreate(savedInstanceState)

        if(!doNotInitializeApp) {
            if(CommonUtils.showCalculator) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            refreshScreenKeepOn()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ScreenSettings.nightMode) {
                val uiFlags = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = uiFlags
            }
        }
    }

    protected fun buildActivityComponent() = CommonUtils.buildActivityComponent()

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
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    /**	This will be called automatically for you on 2.0 or later
     */
    override fun onBackPressed() {
        if (!historyTraversal.goBack()) {
            super.onBackPressed()
        }
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

    fun showErrorMsg(msgResId: Int) {
        Dialogs.showErrorMsg(msgResId)
    }

    protected fun returnErrorToPreviousScreen() {
        // just pass control back to the previous screen
        val resultIntent = Intent(this, this.javaClass)
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
    }

    protected fun returnToPreviousScreen() {
        // just pass control back to the previous screen
        val resultIntent = Intent(this, this.javaClass)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    protected fun returnToTop() {
        // just pass control back to the previous screen
        val resultIntent = Intent(this, this.javaClass)
        setResult(RESULT_RETURN_TO_TOP, resultIntent)
        finish()
    }

    override fun onResume() {
        CurrentActivityHolder.activate(this)
        super.onResume()
        Log.i(TAG, "onResume wasPaused:$wasPaused returningFromCalculator:$returningFromCalculator")
        val fromStartupActivity = comingFromStartupActivity
        comingFromStartupActivity = false
        if (
            this !is CalculatorActivity
            && !fromStartupActivity
            && this !is StartupActivity
            && CommonUtils.showCalculator
            && wasPaused
            && !returningFromCalculator
        ) {
            val handlerIntent = Intent(this@ActivityBase, CalculatorActivity::class.java)
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
        try {
            val inputMethodManager: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
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
        CommonUtils.onyxSupport?.setupOnyxFast()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.i(TAG, "onNewIntent $this ${intent?.action}")
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
    @Inject
    internal fun setNewHistoryTraversal(historyTraversalFactory: HistoryTraversalFactory) {
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

    suspend fun awaitIntent(intent: Intent) : ActivityResult
    {
        val activityResult = CompletableDeferred<ActivityResult>()
        val resultCode = currentCode++
        resultByCode[resultCode] = activityResult
        startActivityForResult(intent, resultCode + ASYNC_REQUEST_CODE_START)
        return activityResult.await()
    }

    val preferences get() = CommonUtils.settings

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

    open fun freeze() {}

    open fun unFreeze() {}

    val TAG get() = "Base-${this::class.java.simpleName}"

    companion object {
        private const val SCREEN_KEEP_ON_PREF = "screen_keep_on_pref"

        // standard request code for startActivityForResult
        const val STD_REQUEST_CODE = 1
        const val CALCULATOR_REQUEST = 6000
        const val ASYNC_REQUEST_CODE_START = 1900

        // Special result that requests all activities to exit until the main/top Activity is reached
        const val RESULT_RETURN_TO_TOP = 900

    }
}
