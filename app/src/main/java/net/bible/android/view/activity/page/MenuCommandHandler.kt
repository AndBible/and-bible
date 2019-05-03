/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.download.Download
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.android.view.activity.mynote.MyNotes
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.page.MainBibleActivity.Companion.BACKUP_RESTORE_REQUEST
import net.bible.android.view.activity.page.MainBibleActivity.Companion.BACKUP_SAVE_REQUEST
import net.bible.android.view.activity.page.MainBibleActivity.Companion.REQUEST_PICK_FILE_FOR_BACKUP_RESTORE
import net.bible.android.view.activity.page.screen.WindowMenuCommandHandler
import net.bible.android.view.activity.readingplan.DailyReading
import net.bible.android.view.activity.settings.SettingsActivity
import net.bible.android.view.activity.speak.GeneralSpeakActivity
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.BookCategory

import javax.inject.Inject
import kotlin.concurrent.thread

/** Handle requests from the main menu
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class MenuCommandHandler @Inject
constructor(private val callingActivity: MainBibleActivity,
            private val searchControl: SearchControl,
            private val windowMenuCommandHandler: WindowMenuCommandHandler,
            private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider,
            private val windowControl: WindowControl,
            private val downloadControl: DownloadControl,
            private val backupControl: BackupControl
) {

    /**
     * on Click handlers
     */
    @SuppressLint("RestrictedApi")
    fun handleMenuRequest(menuItem: MenuItem): Boolean {
        var isHandled = false

        // Activities
        run {
            var handlerIntent: Intent? = null
            var requestCode = ActivityBase.STD_REQUEST_CODE
            // Handle item selection
            when (menuItem.itemId) {
                R.id.chooseDocumentButton -> {
                    val intent = Intent(callingActivity, ChooseDocument::class.java)
                    callingActivity.startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE)
                }
                R.id.rateButton -> {
                    val uri = Uri.parse("market://details?id=" + callingActivity.packageName)
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply{
                        // To count with Play market backstack, After pressing back button,
                        // to taken back to our application, we need to add following flags to intent.
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                        }
                    }
                    try {
                        callingActivity.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        val httpUri = Uri.parse("http://play.google.com/store/apps/details?id=" + callingActivity.packageName)
                        callingActivity.startActivity(Intent(Intent.ACTION_VIEW, httpUri))
                    }
                }
                R.id.backupMainMenu -> {
                    val view: View = callingActivity.findViewById(R.id.homeButton)
                    val menu = PopupMenu(callingActivity, view).apply {
                        menuInflater.inflate(R.menu.backup_submenu, menu)
                        setOnMenuItemClickListener {handleMenuRequest(it)}
                    }

                    val menuHelper = MenuPopupHelper(callingActivity, menu.menu as MenuBuilder, view)
                    menuHelper.setForceShowIcon(true)
                    menuHelper.show()
                }
                R.id.searchButton -> handlerIntent = searchControl.getSearchIntent(activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument)
                R.id.settingsButton -> {
                    handlerIntent = Intent(callingActivity, SettingsActivity::class.java)
                    // force the bible view to be refreshed after returning from settings screen because notes, verses, etc. may be switched on or off
                    requestCode = IntentHelper.REFRESH_DISPLAY_ON_FINISH
                }
                R.id.historyButton -> handlerIntent = Intent(callingActivity, History::class.java)
                R.id.bookmarksButton -> handlerIntent = Intent(callingActivity, Bookmarks::class.java)
                R.id.manageLabels -> {
                    handlerIntent = Intent(callingActivity, ManageLabels::class.java)
                    requestCode = IntentHelper.REFRESH_DISPLAY_ON_FINISH
                }
                R.id.mynotesButton -> handlerIntent = Intent(callingActivity, MyNotes::class.java)
                R.id.speakButton -> {
                    val isBible = windowControl.activeWindowPageManager.currentPage
                            .bookCategory == BookCategory.BIBLE
                    handlerIntent = Intent(callingActivity, if (isBible) BibleSpeakActivity::class.java else GeneralSpeakActivity::class.java)
                }
                R.id.dailyReadingPlanButton -> handlerIntent = Intent(callingActivity, DailyReading::class.java)
                R.id.downloadButton -> if (downloadControl.checkDownloadOkay()) {
                    handlerIntent = Intent(callingActivity, Download::class.java)
                    requestCode = IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH
                }
                R.id.installZipButton -> handlerIntent = Intent(callingActivity, InstallZip::class.java)
                R.id.helpButton -> {
                    val app = BibleApplication.application
                    val versionMsg = app.getString(R.string.version_text, CommonUtils.applicationVersionName)

                    val helpTitles = arrayOf(R.string.help_nav_title, R.string.help_speech_title,
                            R.string.help_mynote_title, R.string.help_bookmarks_title, R.string.help_search_title,
                            R.string.help_contextmenus_title)
                    val helpTexts = arrayOf(R.string.help_nav_text, R.string.help_speech_text,
                            R.string.help_mynote_text, R.string.help_bookmarks_text, R.string.help_search_text,
                            R.string.help_contextmenus_text)

                    var htmlMessage = ""

                    for(idx in 0 until helpTitles.size) {
                        val helpText = app.getString(helpTexts[idx]).replace("\n", "<br>")
                        htmlMessage += "<b>${app.getString(helpTitles[idx])}</b><br>$helpText<br><br>"
                    }
                    htmlMessage += "<i>$versionMsg</i>"

                    val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        Html.fromHtml(htmlMessage)
                    }

                    val d = AlertDialog.Builder(callingActivity)
                            .setTitle(R.string.help)
                            .setMessage(spanned)
                            .setPositiveButton(android.R.string.ok) { _, _ ->  }
                            .create()

                    d.show()
                    d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
                }
                R.id.backup -> {
                    backupControl.backupDatabaseViaIntent(callingActivity)
                    isHandled = true
                }
                R.id.restore -> {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "application/*"
                    callingActivity.startActivityForResult(intent, REQUEST_PICK_FILE_FOR_BACKUP_RESTORE)
                    isHandled = true
                }
                R.id.backupStorage -> {
                    if (ContextCompat.checkSelfPermission(callingActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(callingActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), BACKUP_SAVE_REQUEST)
                    } else {
                        backupControl.backupDatabase()
                    }
                    isHandled = true
                }
                R.id.restoreStorage -> {
                    if (ContextCompat.checkSelfPermission(callingActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(callingActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), BACKUP_RESTORE_REQUEST)
                    } else {
                        backupControl.restoreDatabase()
                    }
                    isHandled = true
                }
            }//handlerIntent = new Intent(callingActivity, Help.class);

            if (!isHandled) {
                isHandled = windowMenuCommandHandler.handleMenuRequest(menuItem)
            }

            if (handlerIntent != null) {
                callingActivity.startActivityForResult(handlerIntent, requestCode)
                isHandled = true
            }
        }

        return isHandled
    }

    fun restartIfRequiredOnReturn(requestCode: Int): Boolean {
        if (requestCode == IntentHelper.REFRESH_DISPLAY_ON_FINISH) {
            Log.i(TAG, "Refresh on finish")
            if (!equals(CommonUtils.localePref ?: "", BibleApplication.application.localeOverrideAtStartUp)) {
                // must restart to change locale
                CommonUtils.restartApp(callingActivity)
            }
        }
        return false
    }

    fun isDisplayRefreshRequired(requestCode: Int): Boolean {
        return requestCode == IntentHelper.REFRESH_DISPLAY_ON_FINISH
    }

    fun isDocumentChanged(requestCode: Int): Boolean {
        return requestCode == IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH
    }

    companion object {

        private const val TAG = "MainMenuCommandHandler"

        internal fun equals(a: String?, b: String?): Boolean {
            return a === b || (a != null && a == b)
        }
    }
}
