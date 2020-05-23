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

package net.bible.android.view.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.WarmUp
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.download.FirstDownload
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils

import org.apache.commons.lang3.StringUtils

import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Called first to show download screen if no documents exist
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class StartupActivity : CustomTitlebarActivityBase() {

    @Inject lateinit var warmUp: WarmUp

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.startup_view)

        buildActivityComponent().inject(this)

        // do not show an actionBar/title on the splash screen
        supportActionBar!!.hide()

        val versionTextView = findViewById<View>(R.id.versionText) as TextView
        val versionMsg = BibleApplication.application.getString(R.string.version_text, CommonUtils.applicationVersionName)
        versionTextView.text = versionMsg

        var abortErrorMsgId = 0

        // check for external storage
        // it would be great to check in the Application but how to show dialog from Application?
        if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            abortErrorMsgId = R.string.no_sdcard_error
        }

        // show fatal startup msg and close app
        if (abortErrorMsgId != 0) {
            Dialogs.instance.showErrorMsg(abortErrorMsgId) {
                // this causes the blue splashscreen activity to finish and since it is the top the app closes
                finish()
            }
            // this aborts further warmUp but leaves blue splashscreen activity
            return
        }

        GlobalScope.launch {
            try {
                // force Sword to initialise itself
                withContext(Dispatchers.IO) {
                    warmUp.warmUpSwordNow()
                }
            } finally {
                // switch back to ui thread to continue
                withContext(Dispatchers.Main) {
                    postBasicInitialisationControl()
                }
            }
        }
    }

    private suspend fun postBasicInitialisationControl() = withContext(Dispatchers.Main) {
        if (swordDocumentFacade.bibles.isEmpty()) {
            Log.i(TAG, "Invoking download activity because no bibles exist")
            if(askIfGotoDownloadActivity()) {
                doGotoDownloadActivity()
            } else {
                finish()
                // ensure app exits to force Sword to reload or if a sdcard/jsword folder is created it may not be recognised
                System.exit(2)
            }

        } else {
            Log.i(TAG, "Going to main bible view")
            gotoMainBibleActivity()
        }
    }

    private suspend fun askIfGotoDownloadActivity() = suspendCoroutine<Boolean> {
        val view = layoutInflater.inflate(R.layout.first_time_dialog, null)
        AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.okay) { dialog, id -> it.resume(true) }
                .setNegativeButton(R.string.cancel) { dialog, id -> it.resume(false)}
            .create().show()
    }

    private fun doGotoDownloadActivity() {
        var errorMessage: String? = null

        if (CommonUtils.megabytesFree < SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
            errorMessage = getString(R.string.storage_space_warning)
        }

        if (StringUtils.isBlank(errorMessage)) {
            val handlerIntent = Intent(this, FirstDownload::class.java)
            startActivityForResult(handlerIntent, DOWNLOAD_DOCUMENT_REQUEST)
        } else {
            Dialogs.instance.showErrorMsg(errorMessage) { finish() }
        }
    }

    /**
     * Load from Zip link on first_time_dialog has been clicked
     */
    fun onLoadFromZip(v: View) {
        Log.i(TAG, "Load from Zip clicked")

        val handlerIntent = Intent(this, InstallZip::class.java)
        startActivityForResult(handlerIntent, DOWNLOAD_DOCUMENT_REQUEST)
    }

    private fun gotoMainBibleActivity() {
        Log.i(TAG, "Going to MainBibleActivity")
        val handlerIntent = Intent(this, MainBibleActivity::class.java)
        startActivity(handlerIntent)
        finish()
    }

    /** on return from download we may go to bible
     * on return from bible just exit
     */
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "Activity result:$resultCode")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == DOWNLOAD_DOCUMENT_REQUEST) {
            Log.i(TAG, "Returned from Download")
            if (swordDocumentFacade.bibles.isNotEmpty()) {
                Log.i(TAG, "Bibles now exist so go to main bible view")
                // select appropriate default verse e.g. John 3.16 if NT only
                pageControl.setFirstUseDefaultVerse()

                gotoMainBibleActivity()
            } else {
                Log.i(TAG, "No Bibles exist so start again")
                GlobalScope.launch(Dispatchers.Main) {
                    postBasicInitialisationControl()
                }
            }
        }
    }

    companion object {

        private val TAG = "StartupActivity"

        private val DOWNLOAD_DOCUMENT_REQUEST = 2
    }
}
