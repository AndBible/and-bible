/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.page.CurrentPage
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.platform.AndroidKeyChooserLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.base.IntentHelper
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.search.searchIntent
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.reading.bridge.ReadingNavigator
import org.crosswire.jsword.book.Book
import org.koin.java.KoinJavaComponent

/**
 * Android [ReadingNavigator]: the activity starts the reading bridge and BibleView used to build
 * inline. Launches are posted to the main thread (DL-8: bridge calls arrive on the JavaBridge thread
 * and the reading host navigates its graph from the result-bearing start).
 *
 * @param host the activity that starts the screens and receives their results
 * @param workspaceSettings seeds and receives the label chooser's workspace-wide settings
 */
class AndroidReadingNavigator(
    private val host: () -> ActivityBase,
    private val downloadControl: () -> DownloadControl = { KoinJavaComponent.get(DownloadControl::class.java) },
    private val workspaceSettings: () -> WorkspaceEntities.WorkspaceSettings? = { null },
) : ReadingNavigator {
    private fun onMain(block: suspend () -> Unit) {
        val activity = host()
        activity.lifecycleScope.launch(Dispatchers.Main) { block() }
    }

    override fun openDownloads(addons: Boolean) = onMain {
        if (!downloadControl().checkDownloadOkay()) return@onMain
        val activity = host()
        val intent = NavHostComposeActivity.intentFor(activity, NavRoutes.download(addons = addons))
        activity.startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
    }

    override fun openDownloadSearch(initials: String?) {
        val activity = host()
        val intent = NavHostComposeActivity.intentFor(activity, NavRoutes.download(search = initials))
        activity.startActivityForResult(intent, IntentHelper.UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH)
    }

    /**
     * The route is built directly: `Screen.ReadingProgress`'s MIGRATED route is argument-free, so a
     * `putExtra` on a `ScreenLauncher` intent would drop the tab (`NavHostRoutingGuardTest
     * .migratedScreenArgumentIsNeverDroppedByAPutExtra`). `STD_REQUEST_CODE` still matters: the screen
     * returns `ActivityResultKind.ReadingProgress` to `MainBibleActivity.onActivityResult`.
     */
    override fun openReadingProgress(tab: Int) = onMain {
        val activity = host()
        activity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity, NavRoutes.readingProgress(tab)), STD_REQUEST_CODE)
    }

    /** Still for-result although the screen never calls `setResult` (pre-existing, harmless). */
    override fun openReadingProgressSettings() = onMain {
        val activity = host()
        activity.startActivityForResult(ScreenLauncher.intentFor(activity, Screen.ReadingProgressSettings), STD_REQUEST_CODE)
    }

    override fun openWorkspaceSelector() {
        val activity = host()
        val intent = NavHostComposeActivity.intentFor(activity, NavRoutes.WORKSPACE_SELECTOR)
        activity.startActivityForResult(intent, WORKSPACE_CHANGED)
    }

    override fun openSearch(document: Book?) {
        val activity = host()
        searchIntent(document, activity)?.let { activity.startActivityForResult(it, STD_REQUEST_CODE) }
    }

    /**
     * `Screen.PromptEdit` is migrated with an argument-free route, so the concrete route is built here
     * (a `putExtra` on a `ScreenLauncher` intent would be dropped).
     */
    override fun openPromptEditor(promptId: String) = onMain {
        val activity = host()
        activity.startActivity(NavHostComposeActivity.intentFor(activity, NavRoutes.promptEdit(promptId = promptId)))
    }

    override fun openExternalUrl(url: String) {
        CommonUtils.openLink(url, forceAsk = true)
    }

    override fun openWebLink(url: String) {
        CommonUtils.openLink(url)
    }

    override fun startKeyChooser(page: CurrentPage) {
        AndroidKeyChooserLauncher { host() }.startKeyChooser(page)
    }

    /**
     * `Screen.ManageLabels` is not in `ScreenLauncher.MIGRATED` (its `data` argument is required), so
     * the nav-host Intent is built directly. The "data" extra of the RESULT is unchanged
     * (`NavResultIntents.forManageLabels`). Updates the bookmark's primary label and the workspace
     * label settings from the result; persisting the bookmark is the caller's job.
     */
    override suspend fun assignLabels(
        bookmark: BookmarkEntities.BaseBookmarkWithNotes,
        labelIds: List<IdType>,
    ): List<IdType>? = withContext(Dispatchers.Main) {
        val activity = host()
        val data = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.ASSIGN,
            selectedLabels = labelIds.toMutableSet(),
            bookmarkPrimaryLabel = bookmark.primaryLabelId
        ).applyFrom(workspaceSettings()).toJSON()
        val result = activity.awaitIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.manageLabels(data)))
        if (result.resultCode != Activity.RESULT_OK) return@withContext null
        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
        bookmark.primaryLabelId = resultData.bookmarkPrimaryLabel
        workspaceSettings()?.updateFrom(resultData)
        resultData.selectedLabels.toList()
    }
}
