/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view

import android.content.Context
import android.content.Intent
import net.bible.android.view.activity.ai.AiConnectionSettingsComposeActivity
import net.bible.android.view.activity.ai.AiModelsComposeActivity
import net.bible.android.view.activity.ai.AiProvidersComposeActivity
import net.bible.android.view.activity.ai.AiPromptsComposeActivity
import net.bible.android.view.activity.ai.AiDocumentFilterComposeActivity
import net.bible.android.view.activity.ai.GlobalToolPermissionsComposeActivity
import net.bible.android.view.activity.ai.PromptEditComposeActivity
import net.bible.android.view.activity.ai.RawLlmLogComposeActivity
import net.bible.android.view.activity.ai.RawLogHistoryComposeActivity
import net.bible.android.view.activity.ai.ToolInfoComposeActivity
import net.bible.android.view.activity.backup.BackupComposeActivity
import net.bible.android.view.activity.bookmark.BookmarksComposeActivity
import net.bible.android.view.activity.bookmark.LabelEditComposeActivity
import net.bible.android.view.activity.bookmark.ManageLabelsComposeActivity
import net.bible.android.view.activity.cloud.CloudDocumentsComposeActivity
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.download.CustomRepositoriesComposeActivity
import net.bible.android.view.activity.download.CustomRepositoryEditorComposeActivity
import net.bible.android.view.activity.download.DownloadComposeActivity
import net.bible.android.view.activity.download.ProgressStatusComposeActivity
import net.bible.android.view.activity.installzip.InstallZipComposeActivity
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity
import net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.activity.navigation.HistoryComposeActivity
import net.bible.android.view.mydocuments.MyDocumentsComposeActivity
import net.bible.android.view.mydocuments.MyDocumentPagesComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingListComposeActivity
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorComposeActivity
import net.bible.android.view.activity.progress.ReadingProgressComposeActivity
import net.bible.android.view.activity.settings.ReadingProgressSettingsComposeActivity
import net.bible.android.view.activity.settings.SettingsComposeActivity
import net.bible.android.view.activity.settings.SyncSettingsComposeActivity
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.android.view.activity.StartupComposeActivity
import net.bible.android.view.activity.search.EpubSearchComposeActivity
import net.bible.android.view.activity.search.EpubSearchResultsComposeActivity
import net.bible.android.view.activity.search.SearchComposeActivity
import net.bible.android.view.activity.search.SearchIndexComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressComposeActivity
import net.bible.android.view.activity.search.SearchResultsComposeActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity
import net.bible.service.common.CommonUtils

/** Screens that have both a classic (XML) and a new (Compose) implementation. */
enum class Screen { Calculator, History, SearchIndexProgress, SearchIndex, SearchResults, ReadingPlanSelector, DailyReadingList, ReadingPlan, ChooseGeneralBookKey, ChooseMapKey, ChooseDictionaryWord, GridChoosePassageBook, ChooseDocument, Download, FirstDownload, Search, EpubSearch, EpubSearchResults, MyDocuments, MyDocumentPages, CloudDocuments, WorkspaceSelector, AiConnectionSettings, AiProviders, AiModels, AiPrompts, PromptEdit, GlobalToolPermissions, ToolInfo, AiDocumentFilter, RawLogHistory, RawLlmLog, LabelEdit, ManageLabels, Bookmarks, ReadingProgress, Settings, ReadingProgressSettings, SyncSettings, Startup, InstallZip, TextDisplaySettings, CustomRepositories, CustomRepositoryEditor, Backup, ProgressStatus }

/**
 * Central old/new routing indirection (Strangler Fig), now fully collapsed: slice S12 was the last
 * one with a flag branch left in [targetFor], so the map is unconditional and the `use_compose_ui`
 * flag no longer selects an implementation here at all. This is the seed of the future CMP
 * navigation graph (Batch Z); spec 10.1's epilogue turns it into that graph and retires
 * [useComposeFor], whose sole surviving production caller is `StartupActivity`.
 */
object ScreenLauncher {
    fun useComposeFor(@Suppress("UNUSED_PARAMETER") screen: Screen): Boolean =
        CommonUtils.settings.getBoolean("use_compose_ui", false)

    /** The Activity class implementing [screen]. Unconditional since slice S12 — see the object kdoc. */
    fun targetFor(screen: Screen): Class<*> = when (screen) {
        Screen.Calculator -> CalculatorComposeActivity::class.java
        Screen.History -> HistoryComposeActivity::class.java
        Screen.SearchIndexProgress -> SearchIndexProgressComposeActivity::class.java
        Screen.SearchIndex -> SearchIndexComposeActivity::class.java
        Screen.SearchResults -> SearchResultsComposeActivity::class.java
        Screen.ReadingPlanSelector -> ReadingPlanSelectorComposeActivity::class.java
        Screen.DailyReadingList -> DailyReadingListComposeActivity::class.java
        Screen.ReadingPlan -> DailyReadingComposeActivity::class.java
        Screen.ChooseGeneralBookKey -> ChooseGeneralBookKeyComposeActivity::class.java
        Screen.ChooseMapKey -> ChooseMapKeyComposeActivity::class.java
        Screen.ChooseDictionaryWord -> ChooseDictionaryWordComposeActivity::class.java
        Screen.GridChoosePassageBook -> GridChoosePassageComposeActivity::class.java
        Screen.ChooseDocument -> ChooseDocumentComposeActivity::class.java
        Screen.Download -> DownloadComposeActivity::class.java
        Screen.FirstDownload ->
            // Compose FirstDownload = the shared DownloadComposeActivity + EXTRA_FIRST_DOWNLOAD.
            // intentFor stays generic (no extra injected here); callers add
            // DownloadComposeActivity.EXTRA_FIRST_DOWNLOAD themselves.
            DownloadComposeActivity::class.java
        Screen.Search -> SearchComposeActivity::class.java
        Screen.EpubSearch -> EpubSearchComposeActivity::class.java
        Screen.EpubSearchResults -> EpubSearchResultsComposeActivity::class.java
        Screen.MyDocuments -> MyDocumentsComposeActivity::class.java
        Screen.MyDocumentPages -> MyDocumentPagesComposeActivity::class.java
        Screen.CloudDocuments -> CloudDocumentsComposeActivity::class.java
        Screen.WorkspaceSelector -> WorkspaceSelectorComposeActivity::class.java
        Screen.AiConnectionSettings -> AiConnectionSettingsComposeActivity::class.java
        Screen.AiProviders -> AiProvidersComposeActivity::class.java
        Screen.AiModels -> AiModelsComposeActivity::class.java
        Screen.AiPrompts -> AiPromptsComposeActivity::class.java
        Screen.PromptEdit -> PromptEditComposeActivity::class.java
        Screen.GlobalToolPermissions -> GlobalToolPermissionsComposeActivity::class.java
        Screen.ToolInfo -> ToolInfoComposeActivity::class.java
        Screen.AiDocumentFilter -> AiDocumentFilterComposeActivity::class.java
        Screen.RawLogHistory -> RawLogHistoryComposeActivity::class.java
        Screen.RawLlmLog -> RawLlmLogComposeActivity::class.java
        Screen.LabelEdit -> LabelEditComposeActivity::class.java
        Screen.ManageLabels -> ManageLabelsComposeActivity::class.java
        Screen.Bookmarks -> BookmarksComposeActivity::class.java
        Screen.ReadingProgress -> ReadingProgressComposeActivity::class.java
        Screen.Settings -> SettingsComposeActivity::class.java
        Screen.ReadingProgressSettings -> ReadingProgressSettingsComposeActivity::class.java
        Screen.SyncSettings -> SyncSettingsComposeActivity::class.java
        Screen.Startup -> StartupComposeActivity::class.java
        Screen.InstallZip -> InstallZipComposeActivity::class.java
        Screen.TextDisplaySettings -> TextDisplaySettingsComposeActivity::class.java
        Screen.CustomRepositories -> CustomRepositoriesComposeActivity::class.java
        Screen.CustomRepositoryEditor -> CustomRepositoryEditorComposeActivity::class.java
        Screen.Backup -> BackupComposeActivity::class.java
        Screen.ProgressStatus -> ProgressStatusComposeActivity::class.java
    }

    /**
     * Intent for [screen], routed old/new. Callers that need the result (the calculator's PIN
     * unlock uses `startActivityForResult` / `awaitIntent`) build on this instead of [open].
     */
    fun intentFor(context: Context, screen: Screen): Intent = Intent(context, targetFor(screen))

    fun open(context: Context, screen: Screen) {
        context.startActivity(intentFor(context, screen))
    }
}
