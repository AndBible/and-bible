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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SpinnerBinding
import net.bible.android.activity.databinding.StartupViewBinding
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.download.FirstDownload
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.util.Hourglass
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Books


/** Called first to show download screen if no documents exist
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class StartupActivity : CustomTitlebarActivityBase() {
    private lateinit var spinnerBinding: SpinnerBinding
    private lateinit var startupViewBinding: StartupViewBinding

    val docs get() = DatabaseContainer.db.swordDocumentInfoDao()
    private val previousInstallDetected: Boolean get() = docs.getKnownInstalled().isNotEmpty();


    private suspend fun getListOfBooksUserWantsToRedownload(context: Context) : List<String>? {
        var result: List<String>?;
        withContext(Dispatchers.Main) {
            result = suspendCoroutine {
                val books = docs.getKnownInstalled().sortedBy { it.language }
                val bookNames = books.map {
                    context.getString(R.string.something_with_parenthesis, it.name, it.language)
                }.toTypedArray()

                val checkedItems = bookNames.map { true }.toBooleanArray()
                val dialog = AlertDialog.Builder(context)
                    .setPositiveButton(R.string.okay) { d, _ ->
                        val selectedBooks = books.filterIndexed { index, book -> checkedItems[index] }.map{ it.initials }
                        if(selectedBooks.isEmpty()) {
                            it.resume(null)
                        } else {
                            it.resume(selectedBooks)
                        }
                    }
                    .setMultiChoiceItems(bookNames, checkedItems) { _, pos, value ->
                        checkedItems[pos] = value
                    }
                    .setNeutralButton(R.string.select_all) { _, _ -> it.resume(null) }
                    .setNegativeButton(R.string.cancel) { _, _ -> it.resume(null) }
                    .setOnCancelListener {_ -> it.resume(null)}
                    .setTitle(getString(R.string.redownload))
                    .create()

                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        val allSelected = checkedItems.find { !it } == null
                        val newValue = !allSelected
                        val v = dialog.listView
                        for (i in 0 until v.count) {
                            v.setItemChecked(i, newValue)
                            checkedItems[i] = newValue
                        }
                        (it as Button).text = getString(if (allSelected) R.string.select_all else R.string.select_none)
                    }
                }
                dialog.show()
            }
        }
        return result;
    }

    private fun checkForExternalStorage(): Boolean {
        var abortErrorMsgId = 0

        if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            abortErrorMsgId = R.string.no_sdcard_error
        }

        if (abortErrorMsgId != 0) {
            Dialogs.instance.showErrorMsg(abortErrorMsgId) {
                finish()
            }
            return false;
        }
        return true
    }

    private suspend fun checkWebView(): Boolean {
        val info = WebViewCompat.getCurrentWebViewPackage(applicationContext)
        val versionNum = info?.versionName?.split(".")?.first()?.split(" ")?.first()?.toInt() ?: 0
        val minimumVersion = 69 // tested with Android Emulator API 24
        if(versionNum < minimumVersion) {
            val playUrl = "https://play.google.com/store/apps/details?id=com.google.android.webview"
            val playLink = "<a href=\"$playUrl\">${getString(R.string.play)}</a>"

            val msg = getString(R.string.old_webview, info?.versionName, minimumVersion.toString(), getString(R.string.app_name_medium), playLink)

            val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(msg, Html.FROM_HTML_MODE_LEGACY)
            } else {
                Html.fromHtml(msg)
            }

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
        super.onCreate(savedInstanceState)
        spinnerBinding = SpinnerBinding.inflate(layoutInflater)
        startupViewBinding = StartupViewBinding.inflate(layoutInflater)
        setContentView(spinnerBinding.root)
        // do not show an actionBar/title on the splash screen
        buildActivityComponent().inject(this)
        supportActionBar!!.hide()
        if (!checkForExternalStorage()) return;

        BackupControl.setupDirs(this)
        GlobalScope.launch {
            ErrorReportControl.checkCrash(this@StartupActivity)
            // switch back to ui thread to continue
            withContext(Dispatchers.Main) {
                postBasicInitialisationControl()
            }
        }
    }

    private suspend fun initializeDatabase() {
        val oldText = spinnerBinding.progressText.text
        spinnerBinding.progressText.text = getString(R.string.upgrading_database)
        withContext(Dispatchers.IO) {
            DatabaseContainer.db
        }
        spinnerBinding.progressText.text = oldText
    }

    private val docDao get() = DatabaseContainer.db.swordDocumentInfoDao()

    private suspend fun postBasicInitialisationControl() = withContext(Dispatchers.Main) {
        if(!checkWebView()) return@withContext

        if (swordDocumentFacade.bibles.isEmpty()) {
            Log.i(TAG, "Invoking download activity because no bibles exist")
            // only show the splash screen if user has no bibles
            showFirstLayout()
        } else {
            Log.i(TAG, "Going to main bible view")

            // When I mess up database, I can re-create database like this.
            // BackupControl.resetDatabase()
            initializeDatabase()

            CommonUtils.initializeApp()

            gotoMainBibleActivity()
            spinnerBinding.progressText.text =getString(R.string.initializing_app)
        }
    }

    private fun showFirstLayout() {
        setContentView(startupViewBinding.root)
        startupViewBinding.welcomeMessage.text = getString(R.string.welcome_message, getString(R.string.app_name_long))

        val versionMsg = BibleApplication.application.getString(R.string.version_text, CommonUtils.applicationVersionName)
        startupViewBinding.versionText.text = versionMsg


        // if a previous list of books is available to be installed,
        // allow the user to requickly redownload them all.
        val redownloadButton = startupViewBinding.redownloadButton
        val redownloadTextView = startupViewBinding.redownloadMessage
        if (previousInstallDetected) {
            // do something
            Log.d(TAG, "A previous install was detected")
            redownloadTextView.text = getString(R.string.redownload_message)
            redownloadTextView.visibility = View.VISIBLE
            redownloadButton.setOnClickListener {
                GlobalScope.launch(Dispatchers.Main)  {
                    val books = getListOfBooksUserWantsToRedownload(this@StartupActivity);
                    if (books != null) {
                        val intentHandler = Intent(this@StartupActivity, FirstDownload::class.java);
                        intentHandler.putExtra(DownloadActivity.DOCUMENT_IDS_EXTRA, ArrayList(books));
                        startActivityForResult(intentHandler, DOWNLOAD_DOCUMENT_REQUEST)
                    }
                }
            }
        } else {
            Log.d(TAG, "Showing restore button because nothing to redownload")
            // hide button because nothing to download
            redownloadButton.text = getString(R.string.restore_database)
            redownloadButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "application/*"
                startActivityForResult(intent, REQUEST_PICK_FILE_FOR_BACKUP_RESTORE)
            }
        }

    }

    fun doGotoDownloadActivity(v: View) {
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
        handlerIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        GlobalScope.launch(Dispatchers.Main) {
            if(swordDocumentFacade.bibles.filter { !it.isLocked }.isEmpty()) {
                for (it in swordDocumentFacade.bibles.filter { it.isLocked }) {
                    CommonUtils.unlockDocument(this@StartupActivity, it)
                }
                if (swordDocumentFacade.bibles.filter { !it.isLocked }.isEmpty()) {
                    showFirstLayout()
                    return@launch
                }
            }

            startActivity(handlerIntent)
            finish()
        }
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
                GlobalScope.launch(Dispatchers.Main) {
                    gotoMainBibleActivity()
                }

            } else {
                Log.i(TAG, "No Bibles exist so start again")
                GlobalScope.launch(Dispatchers.Main) {
                    postBasicInitialisationControl()
                }
            }
        } else if (requestCode == REQUEST_PICK_FILE_FOR_BACKUP_RESTORE) {
            // this and the one in MainActivity could potentially be merged into the same thing
            if (resultCode == Activity.RESULT_OK) {
                CurrentActivityHolder.getInstance().currentActivity = this
                Dialogs.instance.showMsg(R.string.restore_confirmation, true) {
                    ABEventBus.getDefault().post(ToastEvent(getString(R.string.loading_backup)))
                    val hourglass = Hourglass(this)
                    GlobalScope.launch(Dispatchers.IO) {
                        hourglass.show()
                        val inputStream = contentResolver.openInputStream(data!!.data!!)
                        if (BackupControl.restoreDatabaseViaIntent(inputStream!!)) {
                            Log.d(TAG, "Restored database successfully")

                            withContext(Dispatchers.Main) {
                                Dialogs.instance.showMsg(R.string.restore_success)
                                postBasicInitialisationControl()
                            }
                        }
                        hourglass.dismiss()
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = "StartupActivity"

        private val DOWNLOAD_DOCUMENT_REQUEST = 2
        private val REQUEST_PICK_FILE_FOR_BACKUP_RESTORE = 1
    }
}
