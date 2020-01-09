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

package net.bible.android.view.activity.base

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.KeyEvent
import android.view.View

import net.bible.android.activity.R
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.history.HistoryTraversal
import net.bible.service.history.HistoryTraversalFactory
import net.bible.service.sword.SwordDocumentFacade

import javax.inject.Inject

/** Base class for activities
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class ActivityBase : AppCompatActivity(), AndBibleActivity {
    private var isScreenOn = true

    // some screens are highly customised and the theme looks odd if it changes
    private var allowThemeChange = true

    private lateinit var _contentView: View
    private lateinit var historyTraversal: HistoryTraversal

    private var integrateWithHistoryManagerInitialValue: Boolean = false

    @Inject lateinit var swordDocumentFacade: SwordDocumentFacade

    protected open var nightTheme = R.style.AppThemeNight
    protected open var dayTheme = R.style.AppThemeDay


    /** Called when the activity is first created.  */
    @SuppressLint("MissingSuperCall")
    public override fun onCreate(savedInstanceState: Bundle?) {
        this.onCreate(savedInstanceState, false)
    }

    fun applyTheme() {
        if (ScreenSettings.nightMode) {
            setTheme(nightTheme)
        } else {
            setTheme(dayTheme)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ScreenSettings.nightMode) {
                val uiFlags = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = uiFlags
            }
        }

    }

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?, integrateWithHistoryManager: Boolean) {
        if (allowThemeChange) {
            applyTheme()
        }

        super.onCreate(savedInstanceState)

        Log.i(localClassName, "onCreate:" + this)

        this.integrateWithHistoryManagerInitialValue = integrateWithHistoryManager

        // Register current activity in onCreate and onResume
        CurrentActivityHolder.getInstance().currentActivity = this

        // fix for null context class loader (http://code.google.com/p/android/issues/detail?id=5697)
        // this affected jsword dynamic classloading
        Thread.currentThread().contextClassLoader = javaClass.classLoader

        // if locale is overridden then have to force title to be translated here
        LocaleHelper.translateTitle(this)
    }

    protected fun buildActivityComponent() = CommonUtils.buildActivityComponent()

    override fun startActivity(intent: Intent) {
        historyTraversal.beforeStartActivity()

        super.startActivity(intent)
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        historyTraversal.beforeStartActivity()

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

    private fun setLightsOutMode(isLightsOut: Boolean) {
        if (::_contentView.isInitialized) {
            if (isLightsOut) {
                _contentView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
            } else {
                _contentView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
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

    override fun isIntegrateWithHistoryManager(): Boolean {
        return historyTraversal.isIntegrateWithHistoryManager
    }

    override fun setIntegrateWithHistoryManager(integrateWithHistoryManager: Boolean) {
        historyTraversal.isIntegrateWithHistoryManager = integrateWithHistoryManager
    }

    /** allow activity to enhance intent to correctly restore state  */
    override fun getIntentForHistoryList(): Intent {
        return intent
    }

    fun showErrorMsg(msgResId: Int) {
        Dialogs.getInstance().showErrorMsg(msgResId)
    }

    protected fun showHourglass() {
        Dialogs.getInstance().showHourglass()
    }

    protected fun dismissHourglass() {
        Dialogs.getInstance().dismissHourglass()
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
        super.onResume()
        Log.i(localClassName, "onResume:" + this)
        CurrentActivityHolder.getInstance().currentActivity = this

        //allow action to be called on screen being turned on
        if (!isScreenOn && ScreenSettings.isScreenOn) {
            onScreenTurnedOn()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(localClassName, "onPause:" + this)
        if (isScreenOn && !ScreenSettings.isScreenOn) {
            onScreenTurnedOff()
        }
    }

    protected open fun onScreenTurnedOff() {
        Log.d(TAG, "Window turned off")
        isScreenOn = false
    }

    protected open fun onScreenTurnedOn() {
        Log.d(TAG, "Window turned on")
        isScreenOn = true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(localClassName, "onDestroy:" + this)
    }

    override fun onRestart() {
        super.onRestart()
        Log.i(localClassName, "onRestart:" + this)
    }

    override fun onStart() {
        super.onStart()
        Log.i(localClassName, "onStart:" + this)
    }


    override fun onStop() {
        super.onStop()
        Log.i(localClassName, "onStop:" + this)
        // screen can still be considered as current screen if put on stand-by
        // removing this if causes speech to stop when screen is put on stand-by
        if (isScreenOn) {
            // call this onStop, although it is not guaranteed to be called, to ensure an overlap between dereg and reg of current activity, otherwise AppToBackground is fired mistakenly
            CurrentActivityHolder.getInstance().iAmNoLongerCurrent(this)
        }
    }

    fun setAllowThemeChange(allowThemeChange: Boolean) {
        this.allowThemeChange = allowThemeChange
    }

    /** custom title bar code to add the FEATURE_CUSTOM_TITLE just before setContentView
     * and set the new titlebar layout just after
     */
    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        _contentView = window.decorView.findViewById(android.R.id.content)
    }

    fun getContentView() = _contentView

    /**
     * Each activity instance needs its own HistoryTraversal object
     * @param historyTraversalFactory
     */
    @Inject
    internal fun setNewHistoryTraversal(historyTraversalFactory: HistoryTraversalFactory) {
        // Ensure we don't end up overwriting the initialised class
        if (!::historyTraversal.isInitialized) {
            this.historyTraversal = historyTraversalFactory.createHistoryTraversal(integrateWithHistoryManagerInitialValue)
        }
    }

    companion object {

        // standard request code for startActivityForResult
        const val STD_REQUEST_CODE = 1

        // Special result that requests all activities to exit until the main/top Activity is reached
        const val RESULT_RETURN_TO_TOP = 900

        private const val TAG = "ActivityBase"
    }
}
