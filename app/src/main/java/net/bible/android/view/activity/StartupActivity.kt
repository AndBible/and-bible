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

package net.bible.android.view.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import net.bible.android.activity.R
import net.bible.android.activity.databinding.SpinnerBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.mountAppDialogOverlay
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.installzip.InstallZipEvent
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.checkPoorTranslations
import net.bible.service.common.htmlToSpan
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.hasUsableBible
import net.bible.service.sword.unlockLockedBiblesIfNoneUsable
import net.bible.sharedcore.nav.NavRoutes


var comingFromStartupActivity = false

/**
 * Boot only (slice 8 approach A): splash, checks, DB init, crash check, calculator, poor
 * translations, unlock attempt, then [handOff].
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class StartupActivity : CustomTitlebarActivityBase() {
    private lateinit var spinnerBinding: SpinnerBinding

    override val doNotInitializeApp = true

    private suspend fun checkForExternalStorage(): Boolean {
        var time = 0L
        val delayMillis = 50L
        val timeout = 15000L
        var success = true
        while(Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            delay(delayMillis)
            time += delayMillis;
            if(time > timeout) {
                Log.e(TAG, "waitForBibleView timed out")
                success = false
                break
            }
        }
        if(!success) {
            Dialogs.showMsg2(this@StartupActivity, R.string.no_sdcard_error)
            finish()
        }
        return success
    }

    private suspend fun checkWebView(): Boolean {
        val info = WebViewCompat.getCurrentWebViewPackage(applicationContext)
        Log.i(TAG, "checkWebView: WebView version ${info?.packageName} ${info?.versionName}")

        if(info?.packageName == "com.huawei.webview") return true // We won't check huawei version number as it does not follow Chromium version numbering.

        val versionNum = info?.versionName?.split(".")?.first()?.split(" ")?.first()?.toIntOrNull() ?: return true // null -> can't check
        val minimumVersion = 83 // tested with Android Emulator API 30 and looks to function OK
        if(versionNum < minimumVersion) {
            val playUrl = "https://play.google.com/store/apps/details?id=${info.packageName}"
            val playLink = "<a href=\"$playUrl\">${getString(R.string.play)}</a>"

            val msg = getString(R.string.old_webview, info.versionName, minimumVersion.toString(), getString(R.string.app_name_medium), playLink)

            val spanned = htmlToSpan(msg)

            return suspendCoroutine {
                val dlgBuilder = AlertDialog.Builder(this)
                    .setMessage(spanned)
                    .setCancelable(false)
                    .setPositiveButton(R.string.proceed_anyway) { _, _ -> it.resume(true) }
                    .setNeutralButton(R.string.close) { _, _ ->
                        it.resume(false)
                        finish()
                    }

                val d = dlgBuilder.show()
                d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
            }
        }
        return true
    }

    /** Called when the activity is first created.  */
    @SuppressLint("ApplySharedPref")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "StartupActivity.onCreate")
        super.onCreate(savedInstanceState)
        ABEventBus.register(this) {
            onMain<InstallZipEvent> { e ->
                spinnerBinding.progressText.text = e.message
            }
        }
        spinnerBinding = SpinnerBinding.inflate(layoutInflater)
        if(CommonUtils.isDiscrete) {
            spinnerBinding.imageView.setImageResource(
                R.drawable.ic_calculator_color
            )
            spinnerBinding.splashTitleText.text = getString(R.string.app_name_calculator)
        }
        setContentView(spinnerBinding.root)
        mountAppDialogOverlay()
        supportActionBar!!.hide()

        lifecycleScope.launch {
            if (!checkForExternalStorage()) return@launch
            if(!BuildVariant.Appearance.isDiscrete) {
                ErrorReportControl.checkCrash(this@StartupActivity)
            }
            // fixNightMode() for StartupActivity needs to be here
            if(firstTime) {
                firstTime = false
                delay(250)
                recreate()
                return@launch
            }

            // switch back to ui thread to continue
            postBasicInitialisationControl()
        }
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    private suspend fun initializeDatabase() {
        withContext(Dispatchers.IO) {
            DatabaseContainer.ready = true
            DatabaseContainer.instance
        }
    }

    private suspend fun postBasicInitialisationControl() = withContext(Dispatchers.Main) {
        if(!checkWebView()) return@withContext

        // When I mess up database, I can re-create database like this.
        //BackupControl.deleteAllDatabases()

        initializeDatabase()

        // When enabled, go to the calculator first,
        // even when there are no Bible documents already installed.
        if(!checkCalculator()) return@withContext
        if(BuildVariant.Appearance.isDiscrete) {
            ErrorReportControl.checkCrash(this@StartupActivity)
        }
        if (SwordDocumentFacade.bibles.isEmpty()) {
            Log.i(TAG, "No bibles: the handoff opens the first-run welcome")
            // only show the splash screen if user has no bibles
            if(!checkPoorTranslations(this@StartupActivity)) return@withContext
        } else {
            spinnerBinding.progressText.text = getString(R.string.initializing_app)
        }
        handOff()
    }

    /**
     * The boot handoff (slice 8 §2, §4 gate a). The unlock attempt `gotoMainBibleActivity` made, then the
     * ONE predicate chooses the start route -- READING, or WELCOME (the Compose first-run welcome, which
     * the every-Bible-locked path now joins instead of the deleted classic XML one, M1). Initialising the
     * app belongs to READING only: WELCOME starts uninitialised (spec §3.1 rule 2). `comingFromStartupActivity`
     * is set for both, exactly as before: the host's first `onResume` must not raise the calculator the user
     * has just passed here.
     */
    private fun handOff() {
        lifecycleScope.launch(Dispatchers.Main) {
            unlockLockedBiblesIfNoneUsable(this@StartupActivity)
            val route = startRouteForBoot(hasUsableBible())
            if (route == NavRoutes.READING) CommonUtils.initializeAppCoroutine()
            comingFromStartupActivity = true
            startActivity(bootHandoffIntent(this@StartupActivity, intent, route))
            finish()
        }
    }

    private suspend fun checkCalculator(): Boolean {
        if(CommonUtils.showCalculator) {
            Log.i(TAG, "Going to Calculator")
            val handlerIntent = ScreenLauncher.intentFor(this, Screen.Calculator)
            while(true) {
                when(awaitIntent(handlerIntent).resultCode) {
                    RESULT_OK -> break
                    RESULT_CANCELED -> {
                        finish()
                        return false
                    }
                }
            }
        }
        return true
    }

    companion object {
        private val TAG = "StartupActivity"
    }
}

/** Slice 8 §4 gate (a): the start route, from the one predicate. */
internal fun startRouteForBoot(usable: Boolean): String = if (usable) NavRoutes.READING else NavRoutes.WELCOME

/**
 * The handoff Intent `gotoMainBibleActivity` built (T8b), for either start route. `openLink` and both flag
 * sets are unchanged: `FLAG_ACTIVITY_MULTIPLE_TASK` stays on the `ACTION_VIEW` arm (a second live host is
 * what R7b's per-host tokens exist for). A WELCOME host keeps the `openLink`, and gate (b)'s
 * `bootstrapIfNeeded()` dispatches it (Review Focus 2).
 */
internal fun bootHandoffIntent(context: Context, launching: Intent?, route: String): Intent {
    val handlerIntent = NavHostComposeActivity.intentFor(context, route)
    if (launching?.action == Intent.ACTION_VIEW) {
        handlerIntent.putExtra("openLink", launching.dataString)
        handlerIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    } else {
        handlerIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return handlerIntent
}
