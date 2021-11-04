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

package net.bible.android.view.activity.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.report.BugReport
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.readingplan.DailyReading
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList
import net.bible.android.view.activity.settings.SettingsActivity
import net.bible.android.view.activity.speak.GeneralSpeakActivity
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.service.common.CommonUtils

import javax.inject.Inject

const val contributeLink = "https://github.com/AndBible/and-bible/wiki/How-to-contribute"
const val needHelpLink = "https://github.com/AndBible/and-bible/wiki/Support"
const val howToAdd = "https://github.com/AndBible/and-bible/wiki/FAQ#please-add-module-x-to-and-bible"
const val textIssue = "https://github.com/AndBible/and-bible/wiki/FAQ#i-found-text-issue-in-one-of-the-bible--commentary-etc-modules-in-and-bible"

/** Handle requests from the main menu
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class MenuCommandHandler @Inject
constructor(private val callingActivity: MainBibleActivity,
            private val searchControl: SearchControl,
            private val windowControl: WindowControl,
            private val downloadControl: DownloadControl,
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
            val currentPage = windowControl.activeWindowPageManager.currentPage
            when (menuItem.itemId) {
                R.id.chooseDocumentButton -> {
                    val intent = Intent(callingActivity, ChooseDocument::class.java)
                    callingActivity.startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
                }
                R.id.rateButton -> {
                    val htmlMessage = callingActivity.run {
                        val email = "help.andbible@gmail.com"
                        val bugReport = getString(R.string.bug_report)

                        val howToHelp = getString(R.string.how_to_help)
                        val howToHelpLink = "<a href='$howToAdd'>$howToHelp</a>"

                        val sendEmail = getString(R.string.send_email)
                        val textMaintainers = getString(R.string.text_maintainers)
                        val textMaintainersLink = "<a href='${textIssue}'>$textMaintainers</a>"

                        val sendEmailLink = "<a href='mailto:$email'>$sendEmail</a> ($email)"

                        val msg1 = getString(R.string.rate_message1, sendEmailLink, bugReport)
                        val msg2 = getString(R.string.rate_message2, textMaintainersLink)
                        val msg3 = getString(R.string.rate_message3, howToHelpLink)
                        val msg4 = getString(R.string.rate_message4)
                        val msg5 = getString(R.string.rate_message5)
                        val msg6 = getString(R.string.rate_message6)

                        """
                            $msg5<br><br>
                            $msg6 <br><br>
                            $msg1 <br><br>
                            $msg2 <br><br>
                            $msg3 $msg4""".trimIndent()
                    }
                    val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        Html.fromHtml(htmlMessage)
                    }

                    val d = AlertDialog.Builder(callingActivity)
                        .setTitle(R.string.rate_title)
                        .setMessage(spanned)
                        .setPositiveButton(R.string.proceed_google_play) {_, _ ->
                            val uri = Uri.parse("market://details?id=" + callingActivity.packageName)
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply{
                                // To count with Play market backstack, After pressing back button,
                                // to taken back to our application, we need to add following flags to intent.
                                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                            }
                            try {
                                callingActivity.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                val httpUri = Uri.parse("https://play.google.com/store/apps/details?id=" + callingActivity.packageName)
                                callingActivity.startActivity(Intent(Intent.ACTION_VIEW, httpUri))
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                    d.show()
                    d.findViewById<TextView>(android.R.id.message)?.run {
                        movementMethod = LinkMovementMethod.getInstance()
                    }

                }
                R.id.backupMainMenu -> {
                    GlobalScope.launch(Dispatchers.Main) {
                        BackupControl.backupPopup(callingActivity)
                    }
                    isHandled = true
                }
                R.id.searchButton -> {
                    if(currentPage.isSearchable) {
                        handlerIntent = searchControl.getSearchIntent(currentPage.currentDocument, callingActivity)
                    }
                }
                R.id.settingsButton -> {
                    handlerIntent = Intent(callingActivity, SettingsActivity::class.java)
                    // force the bible view to be refreshed after returning from settings screen because notes, verses, etc. may be switched on or off
                    requestCode = IntentHelper.REFRESH_DISPLAY_ON_FINISH
                }
                R.id.historyButton -> handlerIntent = Intent(callingActivity, History::class.java)
                R.id.bookmarksButton -> handlerIntent = Intent(callingActivity, Bookmarks::class.java)
                R.id.studyPadsButton -> {
                    val intent = Intent(callingActivity, ManageLabels::class.java)
                    intent.putExtra("data", ManageLabels.ManageLabelsData(mode = ManageLabels.Mode.STUDYPAD)
                        .applyFrom(windowControl.windowRepository.workspaceSettings)
                        .toJSON())
                    GlobalScope.launch (Dispatchers.Main) {
                        val result = callingActivity.awaitIntent(intent)
                        if(result?.resultCode == Activity.RESULT_OK) {
                            val resultData = ManageLabels.ManageLabelsData.fromJSON(result.resultData.getStringExtra("data")!!)
                            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
                        }
                    }
                }
                R.id.speakButton -> {
                    if(currentPage.isSpeakable) {
                        val isBible = currentPage.documentCategory == DocumentCategory.BIBLE
                        handlerIntent = Intent(callingActivity, if (isBible) BibleSpeakActivity::class.java else GeneralSpeakActivity::class.java)
                    }
                }
                R.id.dailyReadingPlanButton -> {
                    handlerIntent = Intent(callingActivity, DailyReading::class.java)
                    isHandled = true
                }
                R.id.downloadButton -> if (downloadControl.checkDownloadOkay()) {
                    handlerIntent = Intent(callingActivity, DownloadActivity::class.java)
                    requestCode = IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH
                }
                R.id.helpButton -> {
                    CommonUtils.showHelp(callingActivity, showVersion = true)
                    isHandled = true
                }
                R.id.appLicence -> {
                    val messageHtml = callingActivity.resources.openRawResource(R.raw.license).readBytes().decodeToString()

                    val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Html.fromHtml(messageHtml, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        Html.fromHtml(messageHtml)
                    }

                    val d = AlertDialog.Builder(callingActivity)
                        .setTitle(R.string.app_licence_title)
                        .setMessage(spanned)
                        .setPositiveButton(android.R.string.ok) { _, _ ->  }
                        .create()

                    d.show()
                    d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
                    isHandled = true
                }
                R.id.bugReport -> {
                    GlobalScope.launch {
                        BugReport.reportBug(callingActivity, source = "manual")
                    }
                    isHandled = true
                }
                R.id.tellFriend -> {
                    val homepage = Uri.parse("https://andbible.github.io")
                    val playstore = Uri.parse("https://play.google.com/store/apps/details?id=" + callingActivity.packageName)

                    val appName = callingActivity.getString(R.string.app_name_long)
                    val message1 = callingActivity.getString(R.string.tell_friend_message1, appName)
                    val message2 = callingActivity.getString(R.string.tell_friend_message2)
                    val message3 = callingActivity.getString(R.string.tell_friend_message3, playstore)
                    val message4 = callingActivity.getString(R.string.tell_friend_message4, homepage)

                    val message = """
                        $message1 $message2 
                        
                        $message3 
                        
                        $message4
                    """.trimIndent()


                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, message)
                        type = "text/plain"
                    }
                    val chooserIntent = Intent.createChooser(emailIntent, callingActivity.getString(R.string.tell_friend_title))
                    callingActivity.startActivity(chooserIntent)
                    isHandled = true
                }
                R.id.howToContribute -> {
                   callingActivity.startActivity(Intent(Intent.ACTION_VIEW,
                       Uri.parse(contributeLink)))
                   isHandled = true
                }
                R.id.needHelp -> {
                    callingActivity.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse(needHelpLink)))
                    isHandled = true
                }
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


    companion object {

        private const val TAG = "MainMenuCommandHandler"

        internal fun equals(a: String?, b: String?): Boolean {
            return a === b || (a != null && a == b)
        }
    }
}
