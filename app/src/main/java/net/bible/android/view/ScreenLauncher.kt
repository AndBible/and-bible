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
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.bookmark.BookmarksComposeActivity
import net.bible.android.view.activity.bookmark.LabelEditActivity
import net.bible.android.view.activity.bookmark.LabelEditComposeActivity
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.ManageLabelsComposeActivity
import net.bible.android.view.activity.cloud.CloudDocumentsActivity
import net.bible.android.view.activity.cloud.CloudDocumentsComposeActivity
import net.bible.android.view.activity.discrete.CalculatorActivity
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.download.DownloadComposeActivity
import net.bible.android.view.activity.download.FirstDownload
import net.bible.android.view.activity.navigation.ChooseDictionaryWord
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.navigation.ChooseDocument
import net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKey
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.activity.navigation.History
import net.bible.android.view.activity.navigation.HistoryComposeActivity
import net.bible.android.view.activity.mydocuments.MyDocumentsActivity
import net.bible.android.view.activity.mydocuments.MyDocumentPagesActivity
import net.bible.android.view.mydocuments.MyDocumentsComposeActivity
import net.bible.android.view.mydocuments.MyDocumentPagesComposeActivity
import net.bible.android.view.activity.readingplan.DailyReading
import net.bible.android.view.activity.readingplan.DailyReadingComposeActivity
import net.bible.android.view.activity.readingplan.DailyReadingList
import net.bible.android.view.activity.readingplan.DailyReadingListComposeActivity
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorComposeActivity
import net.bible.android.view.activity.progress.ReadingProgressActivity
import net.bible.android.view.activity.progress.ReadingProgressComposeActivity
import net.bible.android.view.activity.progress.ReadingProgressSettingsActivity
import net.bible.android.view.activity.settings.ReadingProgressSettingsComposeActivity
import net.bible.android.view.activity.settings.SettingsActivity
import net.bible.android.view.activity.settings.SettingsComposeActivity
import net.bible.android.view.activity.search.EpubSearch
import net.bible.android.view.activity.search.EpubSearchComposeActivity
import net.bible.android.view.activity.search.EpubSearchResults
import net.bible.android.view.activity.search.EpubSearchResultsComposeActivity
import net.bible.android.view.activity.search.Search
import net.bible.android.view.activity.search.SearchComposeActivity
import net.bible.android.view.activity.search.SearchIndex
import net.bible.android.view.activity.search.SearchIndexComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressComposeActivity
import net.bible.android.view.activity.search.SearchIndexProgressStatus
import net.bible.android.view.activity.search.SearchResults
import net.bible.android.view.activity.search.SearchResultsComposeActivity
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.speak.BibleSpeakComposeActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity
import net.bible.service.common.CommonUtils

/** Screens that have both a classic (XML) and a new (Compose) implementation. */
enum class Screen { Calculator, History, SearchIndexProgress, SearchIndex, SearchResults, ReadingPlanSelector, DailyReadingList, ReadingPlan, ChooseGeneralBookKey, ChooseMapKey, ChooseDictionaryWord, GridChoosePassageBook, ChooseDocument, Download, FirstDownload, Search, EpubSearch, EpubSearchResults, MyDocuments, MyDocumentPages, CloudDocuments, BibleSpeak, WorkspaceSelector, AiConnectionSettings, AiProviders, AiModels, AiPrompts, PromptEdit, GlobalToolPermissions, ToolInfo, AiDocumentFilter, RawLogHistory, RawLlmLog, LabelEdit, ManageLabels, Bookmarks, ReadingProgress, Settings, ReadingProgressSettings }

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
        Screen.SearchIndexProgress ->
            if (useComposeFor(screen)) SearchIndexProgressComposeActivity::class.java
            else SearchIndexProgressStatus::class.java
        Screen.SearchIndex ->
            if (useComposeFor(screen)) SearchIndexComposeActivity::class.java
            else SearchIndex::class.java
        Screen.SearchResults ->
            if (useComposeFor(screen)) SearchResultsComposeActivity::class.java
            else SearchResults::class.java
        Screen.ReadingPlanSelector ->
            if (useComposeFor(screen)) ReadingPlanSelectorComposeActivity::class.java
            else ReadingPlanSelectorList::class.java
        Screen.DailyReadingList ->
            if (useComposeFor(screen)) DailyReadingListComposeActivity::class.java
            else DailyReadingList::class.java
        Screen.ReadingPlan ->
            if (useComposeFor(screen)) DailyReadingComposeActivity::class.java
            else DailyReading::class.java
        Screen.ChooseGeneralBookKey ->
            if (useComposeFor(screen)) ChooseGeneralBookKeyComposeActivity::class.java
            else ChooseGeneralBookKey::class.java
        Screen.ChooseMapKey ->
            if (useComposeFor(screen)) ChooseMapKeyComposeActivity::class.java
            else ChooseMapKey::class.java
        Screen.ChooseDictionaryWord ->
            if (useComposeFor(screen)) ChooseDictionaryWordComposeActivity::class.java
            else ChooseDictionaryWord::class.java
        Screen.GridChoosePassageBook ->
            if (useComposeFor(screen)) GridChoosePassageComposeActivity::class.java
            else GridChoosePassageBook::class.java
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
        Screen.Search ->
            if (useComposeFor(screen)) SearchComposeActivity::class.java
            else Search::class.java
        Screen.EpubSearch ->
            if (useComposeFor(screen)) EpubSearchComposeActivity::class.java
            else EpubSearch::class.java
        Screen.EpubSearchResults ->
            if (useComposeFor(screen)) EpubSearchResultsComposeActivity::class.java
            else EpubSearchResults::class.java
        Screen.MyDocuments ->
            if (useComposeFor(screen)) MyDocumentsComposeActivity::class.java
            else MyDocumentsActivity::class.java
        Screen.MyDocumentPages ->
            if (useComposeFor(screen)) MyDocumentPagesComposeActivity::class.java
            else MyDocumentPagesActivity::class.java
        Screen.CloudDocuments ->
            if (useComposeFor(screen)) CloudDocumentsComposeActivity::class.java
            else CloudDocumentsActivity::class.java
        Screen.BibleSpeak ->
            if (useComposeFor(screen)) BibleSpeakComposeActivity::class.java
            else BibleSpeakActivity::class.java
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
        Screen.ReadingProgress ->
            if (useComposeFor(screen)) ReadingProgressComposeActivity::class.java
            else ReadingProgressActivity::class.java
        Screen.Settings ->
            if (useComposeFor(screen)) SettingsComposeActivity::class.java
            else SettingsActivity::class.java
        Screen.ReadingProgressSettings ->
            if (useComposeFor(screen)) ReadingProgressSettingsComposeActivity::class.java
            else ReadingProgressSettingsActivity::class.java
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
