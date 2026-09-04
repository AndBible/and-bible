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
import net.bible.android.view.activity.ai.AiConnectionSettingsActivity
import net.bible.android.view.activity.ai.AiConnectionSettingsComposeActivity
import net.bible.android.view.activity.ai.AiModelsActivity
import net.bible.android.view.activity.ai.AiModelsComposeActivity
import net.bible.android.view.activity.ai.AiProvidersActivity
import net.bible.android.view.activity.ai.AiProvidersComposeActivity
import net.bible.android.view.activity.ai.AiPromptsComposeActivity
import net.bible.android.view.activity.ai.AiDocumentFilterActivity
import net.bible.android.view.activity.ai.AiDocumentFilterComposeActivity
import net.bible.android.view.activity.ai.AiSettingsActivity
import net.bible.android.view.activity.ai.GlobalToolPermissionsActivity
import net.bible.android.view.activity.ai.GlobalToolPermissionsComposeActivity
import net.bible.android.view.activity.ai.PromptEditActivity
import net.bible.android.view.activity.ai.PromptEditComposeActivity
import net.bible.android.view.activity.ai.RawLlmLogActivity
import net.bible.android.view.activity.ai.RawLlmLogComposeActivity
import net.bible.android.view.activity.ai.RawLogHistoryActivity
import net.bible.android.view.activity.ai.RawLogHistoryComposeActivity
import net.bible.android.view.activity.ai.ToolInfoActivity
import net.bible.android.view.activity.ai.ToolInfoComposeActivity
import net.bible.android.control.backup.BackupActivity
import net.bible.android.view.activity.backup.BackupComposeActivity
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.bookmark.BookmarksComposeActivity
import net.bible.android.view.activity.bookmark.LabelEditActivity
import net.bible.android.view.activity.bookmark.LabelEditComposeActivity
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.ManageLabelsComposeActivity
import net.bible.android.view.activity.cloud.CloudDocumentsComposeActivity
import net.bible.android.view.activity.discrete.CalculatorActivity
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.download.CustomRepositories
import net.bible.android.view.activity.download.CustomRepositoriesComposeActivity
import net.bible.android.view.activity.download.CustomRepositoryEditor
import net.bible.android.view.activity.download.CustomRepositoryEditorComposeActivity
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.download.DownloadComposeActivity
import net.bible.android.view.activity.download.FirstDownload
import net.bible.android.view.activity.download.ProgressStatus
import net.bible.android.view.activity.download.ProgressStatusComposeActivity
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.android.view.activity.installzip.InstallZipComposeActivity
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity
import net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.navigation.HistoryComposeActivity
import net.bible.android.view.mydocuments.MyDocumentsComposeActivity
import net.bible.android.view.mydocuments.MyDocumentPagesComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingListComposeActivity
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorComposeActivity
import net.bible.android.view.activity.progress.ReadingProgressComposeActivity
import net.bible.android.view.activity.progress.ReadingProgressSettingsActivity
import net.bible.android.view.activity.settings.ReadingProgressSettingsComposeActivity
import net.bible.android.view.activity.settings.SettingsActivity
import net.bible.android.view.activity.settings.SettingsComposeActivity
import net.bible.android.view.activity.settings.SyncSettingsActivity
import net.bible.android.view.activity.settings.SyncSettingsComposeActivity
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.android.view.activity.StartupActivity
import net.bible.android.view.activity.StartupComposeActivity
import net.bible.android.view.activity.search.EpubSearchComposeActivity
import net.bible.android.view.activity.search.EpubSearchResultsComposeActivity
import net.bible.android.view.activity.search.SearchComposeActivity
import net.bible.android.view.activity.search.SearchIndexComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressComposeActivity
import net.bible.android.view.activity.search.SearchResultsComposeActivity
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity
import net.bible.service.common.CommonUtils

/** Screens that have both a classic (XML) and a new (Compose) implementation. */
enum class Screen { Calculator, History, SearchIndexProgress, SearchIndex, SearchResults, ReadingPlanSelector, DailyReadingList, ReadingPlan, ChooseGeneralBookKey, ChooseMapKey, ChooseDictionaryWord, GridChoosePassageBook, ChooseDocument, Download, FirstDownload, Search, EpubSearch, EpubSearchResults, MyDocuments, MyDocumentPages, CloudDocuments, BibleSpeak, WorkspaceSelector, AiConnectionSettings, AiProviders, AiModels, AiPrompts, PromptEdit, GlobalToolPermissions, ToolInfo, AiDocumentFilter, RawLogHistory, RawLlmLog, LabelEdit, ManageLabels, Bookmarks, ReadingProgress, Settings, ReadingProgressSettings, SyncSettings, Startup, InstallZip, TextDisplaySettings, CustomRepositories, CustomRepositoryEditor, Backup, ProgressStatus }

/**
 * Central old/new routing indirection (Strangler Fig). Chooses the classic or Compose
 * implementation per screen from the global `use_compose_ui` debug flag. This is the seed of
 * the future CMP navigation graph (Batch Z). Task 11 attached the Compose calculator host.
 */
object ScreenLauncher {
    fun useComposeFor(@Suppress("UNUSED_PARAMETER") screen: Screen): Boolean =
        CommonUtils.settings.getBoolean("use_compose_ui", false)

    /** The Activity class implementing [screen] under the current `use_compose_ui` flag. */
    fun targetFor(screen: Screen): Class<*> = when (screen) {
        Screen.Calculator ->
            if (useComposeFor(screen)) CalculatorComposeActivity::class.java
            else CalculatorActivity::class.java
        Screen.History ->
            if (useComposeFor(screen)) HistoryComposeActivity::class.java
            else History::class.java
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
        Screen.ChooseDocument ->
            if (useComposeFor(screen)) ChooseDocumentComposeActivity::class.java
            else ChooseDocument::class.java
        Screen.Download ->
            if (useComposeFor(screen)) DownloadComposeActivity::class.java
            else DownloadActivity::class.java
        Screen.FirstDownload ->
            // Compose FirstDownload = the shared DownloadComposeActivity + EXTRA_FIRST_DOWNLOAD.
            // intentFor stays generic (no extra injected here); callers add
            // DownloadComposeActivity.EXTRA_FIRST_DOWNLOAD themselves.
            if (useComposeFor(screen)) DownloadComposeActivity::class.java
            else FirstDownload::class.java
        Screen.Search -> SearchComposeActivity::class.java
        Screen.EpubSearch -> EpubSearchComposeActivity::class.java
        Screen.EpubSearchResults -> EpubSearchResultsComposeActivity::class.java
        Screen.MyDocuments -> MyDocumentsComposeActivity::class.java
        Screen.MyDocumentPages -> MyDocumentPagesComposeActivity::class.java
        Screen.CloudDocuments -> CloudDocumentsComposeActivity::class.java
        // Round 13a: there is no Compose Speak ACTIVITY any more — the Compose path opens
        // `ComposeReadingViewHost.showSpeakSettings()` (a sheet over the reading view) instead, and
        // `SpeakEntryPointGuardTest` enforces that every Compose call site does so. Reaching this
        // line therefore always means the classic screen.
        Screen.BibleSpeak -> BibleSpeakActivity::class.java
        Screen.WorkspaceSelector ->
            if (useComposeFor(screen)) WorkspaceSelectorComposeActivity::class.java
            else WorkspaceSelectorActivity::class.java
        Screen.AiConnectionSettings ->
            if (useComposeFor(screen)) AiConnectionSettingsComposeActivity::class.java
            else AiConnectionSettingsActivity::class.java
        Screen.AiProviders ->
            if (useComposeFor(screen)) AiProvidersComposeActivity::class.java
            else AiProvidersActivity::class.java
        Screen.AiModels ->
            if (useComposeFor(screen)) AiModelsComposeActivity::class.java
            else AiModelsActivity::class.java
        Screen.AiPrompts ->
            if (useComposeFor(screen)) AiPromptsComposeActivity::class.java
            else AiSettingsActivity::class.java
        Screen.PromptEdit ->
            if (useComposeFor(screen)) PromptEditComposeActivity::class.java
            else PromptEditActivity::class.java
        Screen.GlobalToolPermissions ->
            if (useComposeFor(screen)) GlobalToolPermissionsComposeActivity::class.java
            else GlobalToolPermissionsActivity::class.java
        Screen.ToolInfo ->
            if (useComposeFor(screen)) ToolInfoComposeActivity::class.java
            else ToolInfoActivity::class.java
        Screen.AiDocumentFilter ->
            if (useComposeFor(screen)) AiDocumentFilterComposeActivity::class.java
            else AiDocumentFilterActivity::class.java
        Screen.RawLogHistory ->
            if (useComposeFor(screen)) RawLogHistoryComposeActivity::class.java
            else RawLogHistoryActivity::class.java
        Screen.RawLlmLog ->
            if (useComposeFor(screen)) RawLlmLogComposeActivity::class.java
            else RawLlmLogActivity::class.java
        Screen.LabelEdit ->
            if (useComposeFor(screen)) LabelEditComposeActivity::class.java
            else LabelEditActivity::class.java
        Screen.ManageLabels ->
            if (useComposeFor(screen)) ManageLabelsComposeActivity::class.java
            else ManageLabels::class.java
        Screen.Bookmarks ->
            if (useComposeFor(screen)) BookmarksComposeActivity::class.java
            else Bookmarks::class.java
        Screen.ReadingProgress -> ReadingProgressComposeActivity::class.java
        Screen.Settings ->
            if (useComposeFor(screen)) SettingsComposeActivity::class.java
            else SettingsActivity::class.java
        Screen.ReadingProgressSettings ->
            if (useComposeFor(screen)) ReadingProgressSettingsComposeActivity::class.java
            else ReadingProgressSettingsActivity::class.java
        Screen.SyncSettings ->
            if (useComposeFor(screen)) SyncSettingsComposeActivity::class.java
            else SyncSettingsActivity::class.java
        Screen.Startup ->
            if (useComposeFor(screen)) StartupComposeActivity::class.java
            else StartupActivity::class.java
        Screen.InstallZip ->
            if (useComposeFor(screen)) InstallZipComposeActivity::class.java
            else InstallZip::class.java
        Screen.TextDisplaySettings ->
            if (useComposeFor(screen)) TextDisplaySettingsComposeActivity::class.java
            else TextDisplaySettingsActivity::class.java
        Screen.CustomRepositories ->
            if (useComposeFor(screen)) CustomRepositoriesComposeActivity::class.java
            else CustomRepositories::class.java
        Screen.CustomRepositoryEditor ->
            if (useComposeFor(screen)) CustomRepositoryEditorComposeActivity::class.java
            else CustomRepositoryEditor::class.java
        Screen.Backup ->
            if (useComposeFor(screen)) BackupComposeActivity::class.java
            else BackupActivity::class.java
        Screen.ProgressStatus ->
            if (useComposeFor(screen)) ProgressStatusComposeActivity::class.java
            else ProgressStatus::class.java
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
