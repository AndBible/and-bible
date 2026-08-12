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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.report.BugReport
import net.bible.android.control.search.SearchControl
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.service.common.CommonUtils
import net.bible.service.common.BuildVariant
import net.bible.service.common.htmlToSpan

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val contributeLink = "https://github.com/AndBible/and-bible/wiki/How-to-contribute"
const val needHelpLink = "https://github.com/AndBible/and-bible/wiki/Support"
const val howToAdd = "https://github.com/AndBible/and-bible/wiki/FAQ#please-add-module-x-to-and-bible"
const val textIssue = "https://github.com/AndBible/and-bible/wiki/FAQ#i-found-text-issue-in-one-of-the-bible--commentary-etc-modules-in-and-bible"
const val buyDevelopmentLink = "https://shop.andbible.org"
const val homepageLink = "https://andbible.org"

/** Handle requests from the main menu
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class MenuCommandHandler(val mainBibleActivity: MainBibleActivity) : KoinComponent {
    val searchControl: SearchControl by inject()
    val windowControl: WindowControl by inject()
    val downloadControl: DownloadControl by inject()


    private inline val isSamsung get() = BuildVariant.DistributionChannel.isSamsung

    /**
     * on Click handlers
     */
    /**
     * Kept for the classic call sites (options menu, classic `NavigationView` listener). Reads only
     * `itemId`, so it is a pure delegate to [handleMenuRequest] — do not add `MenuItem`-dependent
     * logic here without also giving the Compose path an equivalent.
     */
    fun handleMenuRequest(menuItem: MenuItem): Boolean = handleMenuRequest(menuItem.itemId)

    /**
     * The id-based entry point. The Compose reading-view drawer dispatches here via
     * `DrawerMenuStateBuilder.resIdFor(id)`; the classic `MenuItem` overload above delegates to it.
     * Body is the former `handleMenuRequest(MenuItem)` verbatim, with `menuItem.itemId` → [itemId].
     */
    fun handleMenuRequest(itemId: Int): Boolean {
        var isHandled = false

        // Activities
        run {
            var handlerIntent: Intent? = null
            var requestCode = STD_REQUEST_CODE
            // Handle item selection
            val currentPage = windowControl.activeWindowPageManager.currentPage
            when (itemId) {
                R.id.chooseDocumentButton -> {
                    val intent = ScreenLauncher.intentFor(mainBibleActivity, Screen.ChooseDocument)
                    mainBibleActivity.startActivityForResult(intent, STD_REQUEST_CODE)
                }
                R.id.rateButton -> {
                    val htmlMessage = mainBibleActivity.run {
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
                    val spanned = htmlToSpan(htmlMessage)

                    val d = AlertDialog.Builder(mainBibleActivity)
                        .setTitle(R.string.rate_title)
                        .setMessage(spanned)
                        .setPositiveButton(if(isSamsung) R.string.okay else R.string.proceed_google_play) {_, _ ->
                            val samsungUri = Uri.parse("samsungapps://AppRating/"+mainBibleActivity.packageName)
                            val uri = Uri.parse("market://details?id=" + mainBibleActivity.packageName)
                            val intent = Intent(Intent.ACTION_VIEW, if(isSamsung) samsungUri else uri).apply{
                                // To count with Play market backstack, After pressing back button,
                                // to taken back to our application, we need to add following flags to intent.
                                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                            }
                            try {
                                mainBibleActivity.startActivityForResult(intent, STD_REQUEST_CODE)
                            } catch (e: ActivityNotFoundException) {
                                val httpSamsungUri = Uri.parse("https://apps.samsung.com/appquery/AppRating.as?appId=" +mainBibleActivity.packageName)
                                val httpUri = Uri.parse("https://play.google.com/store/apps/details?id=" + mainBibleActivity.packageName)
                                mainBibleActivity.startActivityForResult(Intent(Intent.ACTION_VIEW, if(isSamsung) httpSamsungUri else httpUri), STD_REQUEST_CODE)
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
                    mainBibleActivity.lifecycleScope.launch(Dispatchers.Main) {
                        BackupControl.backupPopup(mainBibleActivity)
                    }
                    isHandled = true
                }
                R.id.searchButton -> {
                    // F6 Task 8b entry point 4: retarget into the reading view's search when a
                    // Compose host is mounted; classic behaviour unchanged otherwise. Review
                    // Important 2: `isSearchable` gates BOTH branches, not just the classic one —
                    // `composeSearchIfHosted`'s `searchOpensInReadingView` only excludes EPUB, so
                    // without this gate a hosted search became reachable from My Notes/dictionary/
                    // map/general-book pages that classic always refused (entry point 6 already
                    // keeps this same gate around its own retarget, `MainBibleActivity.onKeyUp`).
                    if (currentPage.isSearchable) {
                        if (mainBibleActivity.composeSearchIfHosted()) {
                            isHandled = true
                        } else {
                            handlerIntent = searchControl.getSearchIntent(currentPage.currentDocument, mainBibleActivity)
                        }
                    }
                }
                R.id.settingsButton -> {
                    handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.Settings)
                    // force the bible view to be refreshed after returning from settings screen because notes, verses, etc. may be switched on or off
                    requestCode = IntentHelper.REFRESH_DISPLAY_ON_FINISH
                }
                R.id.managePrompts -> {
                    handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.AiPrompts)
                }
                R.id.historyButton -> handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.History)
                R.id.bookmarksButton -> handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.Bookmarks)
                R.id.studyPadsButton -> {
                    val intent = ScreenLauncher.intentFor(mainBibleActivity, Screen.ManageLabels)
                    intent.putExtra("data", ManageLabels.ManageLabelsData(mode = ManageLabels.Mode.STUDYPAD)
                        .applyFrom(windowControl.windowRepository.workspaceSettings)
                        .toJSON())
                    mainBibleActivity.lifecycleScope.launch (Dispatchers.Main) {
                        val result = mainBibleActivity.awaitIntent(intent)
                        if(result.resultCode == Activity.RESULT_OK) {
                            val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
                        }
                    }
                }
                R.id.myDocumentsButton -> {
                    handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.MyDocuments)
                }
                R.id.speakButton -> {
                    if(currentPage.isSpeakable) {
                        handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.BibleSpeak)
                    }
                }
                R.id.dailyReadingPlanButton -> {
                    handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.ReadingPlan)
                    isHandled = true
                }
                R.id.readingProgressButton -> {
                    handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.ReadingProgress)
                    isHandled = true
                }
                R.id.downloadButton -> if (downloadControl.checkDownloadOkay()) {
                    handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.Download)
                    requestCode = IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH
                }
                R.id.helpButton -> {
                    CommonUtils.showHelp(mainBibleActivity, showVersion = true)
                    isHandled = true
                }
                R.id.appLicence -> {
                    val messageHtml = mainBibleActivity.resources.openRawResource(R.raw.license).readBytes().decodeToString()

                    val spanned = htmlToSpan(messageHtml)

                    val d = AlertDialog.Builder(mainBibleActivity)
                        .setTitle(R.string.app_licence_title)
                        .setMessage(spanned)
                        .setPositiveButton(android.R.string.ok) { _, _ ->  }
                        .create()

                    d.show()
                    d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
                    isHandled = true
                }
                R.id.bugReport -> {
                    mainBibleActivity.lifecycleScope.launch {
                        BugReport.reportBug(mainBibleActivity, source = "manual")
                    }
                    isHandled = true
                }
                R.id.tellFriend -> {
                    val homepage = Uri.parse(homepageLink)
                    val playstore = Uri.parse("https://play.google.com/store/apps/details?id=" + mainBibleActivity.packageName)

                    val appName = mainBibleActivity.getString(R.string.app_name_long)
                    val message1 = mainBibleActivity.getString(R.string.tell_friend_message1, appName)
                    val message2 = mainBibleActivity.getString(R.string.tell_friend_message2)
                    val playStoreLink = mainBibleActivity.getString(R.string.tell_friend_message3, playstore)
                    val message4 = mainBibleActivity.getString(R.string.tell_friend_message4, homepage)

                    val message = if(isSamsung)
                        """
                        $message1 $message2 
                        
                        $message4
                    """.trimIndent()
                    else """
                        $message1 $message2 
                        
                        $playStoreLink 
                        
                        $message4
                    """.trimIndent()


                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, message)
                        type = "text/plain"
                    }
                    val chooserIntent = Intent.createChooser(emailIntent, mainBibleActivity.getString(R.string.tell_friend_title))
                    mainBibleActivity.startActivityForResult(chooserIntent, STD_REQUEST_CODE)
                    isHandled = true
                }
                R.id.howToContribute -> {
                   CommonUtils.openLink(contributeLink)
                   isHandled = true
                }
                R.id.buyDevelopment -> {
                    CommonUtils.openLink(buyDevelopmentLink)
                    isHandled = true
                }
                R.id.needHelp -> {
                    CommonUtils.openLink(needHelpLink)
                    isHandled = true
                }
                R.id.googleDriveSync -> {
                    handlerIntent = ScreenLauncher.intentFor(mainBibleActivity, Screen.SyncSettings)
                    isHandled = true
                }
            }

            if (handlerIntent != null) {
                mainBibleActivity.startActivityForResult(handlerIntent, requestCode)
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
                CommonUtils.restartApp(mainBibleActivity)
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
