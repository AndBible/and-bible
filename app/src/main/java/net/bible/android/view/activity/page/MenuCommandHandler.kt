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
import android.util.Log
import android.view.MenuItem
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
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.sharedcore.nav.NavRoutes
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.service.common.CommonUtils
import net.bible.service.common.BuildVariant
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult

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
 *
 * Reading-host re-typing R6c2: no `MainBibleActivity` in any type position. Everything this handler
 * asks its host for is either the plain Android Activity surface ([hostActivity] -- an
 * `ActivityBase`, which both reading hosts really are; see [ReadingCommandsHostCallbacks]'s kdoc for
 * why that is not a cast in disguise) or one of the two late-bound suppliers below, which
 * [ReadingCommands] binds when it constructs the one handler per host.
 *
 * Constructed with named arguments from [ReadingCommands]; `MainBibleActivity.kt` also declares a
 * one-argument adapter of the same name for the Robolectric net, which builds a handler straight
 * from the Activity at four call sites and is not edited in this batch.
 */
class MenuCommandHandler(
    private val hostActivity: ActivityBase,
    /** The mounted reading-view host, or `null` before one is installed. Read at call time: the
     *  History and Speak rows below both depend on whether one is up RIGHT NOW. */
    private val composeReadingViewHost: () -> ComposeReadingViewHost?,
    /** [ReadingCommands.composeSearchIfHosted] -- the search row's retarget into the reading view's
     *  own search; `false` means "not hosted, use the classic Intent". */
    private val composeSearchIfHosted: () -> Boolean,
) : KoinComponent {
    val searchControl: SearchControl by inject()
    val windowControl: WindowControl by inject()
    val downloadControl: DownloadControl by inject()
    private val appDialogs: AppDialogController by inject()


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
                    val intent = NavHostComposeActivity.intentFor(hostActivity, NavRoutes.chooseDocument())
                    hostActivity.startActivityForResult(intent, STD_REQUEST_CODE)
                }
                R.id.rateButton -> {
                    val htmlMessage = hostActivity.run {
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
                    appDialogs.post(
                        AppDialogRequest.Message(
                            title = hostActivity.getString(R.string.rate_title),
                            message = htmlMessage,
                            confirmText = hostActivity.getString(if (isSamsung) R.string.okay else R.string.proceed_google_play),
                            dismissText = hostActivity.getString(R.string.cancel),
                            cancellable = true,
                        ),
                    ) { result ->
                        if (result != AppDialogResult.Ok) return@post
                        val samsungUri = Uri.parse("samsungapps://AppRating/"+BibleApplication.application.packageName)
                        val uri = Uri.parse("market://details?id=" + BibleApplication.application.packageName)
                        val intent = Intent(Intent.ACTION_VIEW, if(isSamsung) samsungUri else uri).apply{
                            // To count with Play market backstack, After pressing back button,
                            // to taken back to our application, we need to add following flags to intent.
                            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                        }
                        try {
                            hostActivity.startActivityForResult(intent, STD_REQUEST_CODE)
                        } catch (e: ActivityNotFoundException) {
                            val httpSamsungUri = Uri.parse("https://apps.samsung.com/appquery/AppRating.as?appId=" +BibleApplication.application.packageName)
                            val httpUri = Uri.parse("https://play.google.com/store/apps/details?id=" + BibleApplication.application.packageName)
                            hostActivity.startActivityForResult(Intent(Intent.ACTION_VIEW, if(isSamsung) httpSamsungUri else httpUri), STD_REQUEST_CODE)
                        }
                    }
                }
                R.id.backupMainMenu -> {
                    hostActivity.lifecycleScope.launch(Dispatchers.Main) {
                        BackupControl.backupPopup(hostActivity)
                    }
                    isHandled = true
                }
                R.id.searchButton -> {
                    // F6 Task 8b entry point 4: retarget into the reading view's search when a
                    // Compose host is mounted; classic behaviour unchanged otherwise. Review
                    // Important 2: `isSearchable` gates BOTH branches, not just the classic one —
                    // `composeSearchIfHosted` (since F43 Task 6) admits every document type, so
                    // without this gate a hosted search became reachable from My Notes/dictionary/
                    // map/non-EPUB-general-book pages that classic always refused (entry point 6
                    // already keeps this same gate around its own retarget,
                    // `MainBibleActivity.onKeyUp`).
                    if (currentPage.isSearchable) {
                        if (composeSearchIfHosted()) {
                            isHandled = true
                        } else {
                            handlerIntent = searchControl.getSearchIntent(currentPage.currentDocument, hostActivity)
                        }
                    }
                }
                R.id.settingsButton -> {
                    handlerIntent = ScreenLauncher.intentFor(hostActivity, Screen.Settings)
                    // force the bible view to be refreshed after returning from settings screen because notes, verses, etc. may be switched on or off
                    requestCode = IntentHelper.REFRESH_DISPLAY_ON_FINISH
                }
                R.id.managePrompts -> {
                    handlerIntent = ScreenLauncher.intentFor(hostActivity, Screen.AiPrompts)
                }
                R.id.historyButton -> {
                    // Round 15b T4: History opens as a quick sheet over the reading view. The
                    // epilogue deleted the classic Activity, HistoryComposeActivity and the
                    // `Screen.History` enum entry, so there is no Intent route left to fall back
                    // to. `composeReadingViewHost` is declared nullable, so SOME null handling is
                    // compiler-mandated; this takes the same shape `speakButton` below does --
                    // fall through with `isHandled` still false, leaving the menu row an unhandled
                    // no-op. The host is installed in `setupUi` before any menu can be opened, so
                    // the null arm is unreachable in practice.
                    val host = composeReadingViewHost()
                    if (host != null) {
                        host.showHistorySheet()
                        isHandled = true
                    }
                }
                R.id.bookmarksButton -> handlerIntent = ScreenLauncher.intentFor(hostActivity, Screen.Bookmarks)
                R.id.studyPadsButton -> {
                    // Screen.ManageLabels is deliberately not in ScreenLauncher.MIGRATED (its `data`
                    // argument is required -- see ScreenLauncher.kt's MIGRATED-map comment), and the
                    // classic ManageLabelsComposeActivity that ScreenLauncher.targetFor used to
                    // resolve it to is gone (nav-graph slices 2+4 Task 7), so this builds the nav-host
                    // Intent directly, the same way SearchControl/LinkControl reach argument-carrying
                    // routes outside the graph. The "data" extra on the RESULT is unchanged --
                    // NavResultIntents.forManageLabels still writes it under that key.
                    val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.STUDYPAD)
                        .applyFrom(windowControl.windowRepository.workspaceSettings)
                        .toJSON()
                    val intent = NavHostComposeActivity.intentFor(hostActivity, NavRoutes.manageLabels(data))
                    hostActivity.lifecycleScope.launch (Dispatchers.Main) {
                        val result = hostActivity.awaitIntent(intent)
                        if(result.resultCode == Activity.RESULT_OK) {
                            val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
                        }
                    }
                }
                R.id.myDocumentsButton -> {
                    handlerIntent = ScreenLauncher.intentFor(hostActivity, Screen.MyDocuments)
                }
                R.id.speakButton -> {
                    // Round 13a retargeted this into the reading view; round 14b §8 changed WHAT it
                    // opens: the transport BAR, not the settings sheet. From a menu row the settings
                    // sheet was a dead end — it carries no play control, and bar visibility is a
                    // separate state this path never touched, so the user reached the settings with
                    // no idea how to start playback. Idempotent by design (spec D3): a menu row is a
                    // one-way action and must never hide the bar again.
                    //
                    // `handlerIntent` stays null so the shared dispatch below does not also start an
                    // activity (mirrors the `searchButton` case right above). `composeReadingViewHost`
                    // is declared nullable, so SOME null handling is compiler-mandated; what this
                    // shape chooses is to fall through with `isHandled` still false, leaving the
                    // menu row an unhandled no-op rather than opening anything.
                    if(currentPage.isSpeakable) {
                        val host = composeReadingViewHost()
                        if (host != null) {
                            host.showSpeakTransport()
                            isHandled = true
                        }
                    }
                }
                R.id.dailyReadingPlanButton -> {
                    handlerIntent = ScreenLauncher.intentFor(hostActivity, Screen.ReadingPlan)
                    isHandled = true
                }
                R.id.readingProgressButton -> {
                    handlerIntent = ScreenLauncher.intentFor(hostActivity, Screen.ReadingProgress)
                    isHandled = true
                }
                R.id.downloadButton -> if (downloadControl.checkDownloadOkay()) {
                    handlerIntent = NavHostComposeActivity.intentFor(hostActivity, NavRoutes.download())
                    requestCode = IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH
                }
                R.id.helpButton -> {
                    CommonUtils.showHelp(hostActivity, showVersion = true)
                    isHandled = true
                }
                R.id.appLicence -> {
                    val messageHtml = BibleApplication.application.resources.openRawResource(R.raw.license).readBytes().decodeToString()

                    appDialogs.post(
                        AppDialogRequest.Message(
                            title = hostActivity.getString(R.string.app_licence_title),
                            message = messageHtml,
                            confirmText = hostActivity.getString(android.R.string.ok),
                            cancellable = true,
                        ),
                    )
                    isHandled = true
                }
                R.id.bugReport -> {
                    hostActivity.lifecycleScope.launch {
                        BugReport.reportBug(hostActivity, source = "manual")
                    }
                    isHandled = true
                }
                R.id.tellFriend -> {
                    val homepage = Uri.parse(homepageLink)
                    val playstore = Uri.parse("https://play.google.com/store/apps/details?id=" + BibleApplication.application.packageName)

                    val appName = hostActivity.getString(R.string.app_name_long)
                    val message1 = hostActivity.getString(R.string.tell_friend_message1, appName)
                    val message2 = hostActivity.getString(R.string.tell_friend_message2)
                    val playStoreLink = hostActivity.getString(R.string.tell_friend_message3, playstore)
                    val message4 = hostActivity.getString(R.string.tell_friend_message4, homepage)

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
                    val chooserIntent = Intent.createChooser(emailIntent, hostActivity.getString(R.string.tell_friend_title))
                    hostActivity.startActivityForResult(chooserIntent, STD_REQUEST_CODE)
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
                    handlerIntent = ScreenLauncher.intentFor(hostActivity, Screen.SyncSettings)
                    isHandled = true
                }
            }

            if (handlerIntent != null) {
                hostActivity.startActivityForResult(handlerIntent, requestCode)
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
                CommonUtils.restartApp(hostActivity)
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
