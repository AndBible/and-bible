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
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
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

/**
 * The screens [ScreenLauncher] routes to. Named for what they ARE, not for how they are built --
 * this used to be "screens that have both a classic (XML) and a new (Compose) implementation", and
 * after Batch Z-late phase 1 none of them has a classic half left.
 *
 * Not every screen is an Activity, so not every screen is in here. `History` and `BibleSpeak` both
 * left the enum in the epilogue: each is now a sheet drawn over the reading view by
 * `ComposeReadingViewHost`, so a routing target for either would name an Activity that no longer
 * exists. Reaching them is a host call (`showHistorySheet()`, `showSpeakTransport()`), not a launch.
 */
enum class Screen { Calculator, SearchIndexProgress, SearchIndex, SearchResults, ReadingPlanSelector, DailyReadingList, ReadingPlan, ChooseGeneralBookKey, ChooseMapKey, ChooseDictionaryWord, GridChoosePassageBook, ChooseDocument, Download, FirstDownload, Search, EpubSearch, EpubSearchResults, MyDocuments, MyDocumentPages, CloudDocuments, WorkspaceSelector, AiConnectionSettings, AiProviders, AiModels, AiPrompts, PromptEdit, GlobalToolPermissions, ToolInfo, AiDocumentFilter, RawLogHistory, RawLlmLog, LabelEdit, ManageLabels, Bookmarks, ReadingProgress, Settings, ReadingProgressSettings, SyncSettings, Startup, InstallZip, TextDisplaySettings, CustomRepositories, CustomRepositoryEditor, Backup, ProgressStatus }

/**
 * Central routing indirection, and all that survives of the old/new Strangler Fig: [targetFor] is a
 * plain [Screen] to `Class` map, and there is no longer any second implementation for it to choose
 * between. Batch Z-late phase 1 deleted every classic screen slice by slice, and its epilogue
 * (spec 10.1) removed the settings flag that used to pick a side, so what is left is one place that
 * answers "which Activity IS this screen" -- the seed of the future CMP navigation graph, which
 * replaces this map rather than the flag.
 *
 * Keeping the indirection after the choice disappeared is deliberate: callers name a [Screen], not
 * an Activity class, so the whole tree does not have to be edited again when a screen's host class
 * changes -- which is exactly what the navigation graph will change it to.
 */
object ScreenLauncher {
    /**
     * Screens that now live in the Compose navigation graph rather than in an Activity of their
     * own, mapped to the route that opens them. [intentFor] aims these at
     * [NavHostComposeActivity]; every other screen still resolves through [targetFor].
     *
     * This is the coexistence seam for the nav-graph migration: because 91 of this object's 99 call
     * sites take the Intent from [intentFor] and drive it themselves, moving a screen into the
     * graph changes NO caller. Entries are added one cluster at a time.
     */
    val MIGRATED: Map<Screen, String> = mapOf(
        Screen.ToolInfo to NavRoutes.AI_TOOL_INFO,
        Screen.AiDocumentFilter to NavRoutes.AI_DOCUMENT_FILTER,
        Screen.GlobalToolPermissions to NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS,
        Screen.AiModels to NavRoutes.AI_MODELS,
        Screen.AiConnectionSettings to NavRoutes.AI_CONNECTION_SETTINGS,
        Screen.AiProviders to NavRoutes.aiProviders(startEasySetup = false),
        Screen.PromptEdit to NavRoutes.promptEdit(),
    )

    /** The Activity class implementing [screen]. */
    fun targetFor(screen: Screen): Class<*> = when (screen) {
        Screen.Calculator -> CalculatorComposeActivity::class.java
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
    fun intentFor(context: Context, screen: Screen): Intent =
        MIGRATED[screen]
            ?.let { route -> NavHostComposeActivity.intentFor(context, route) }
            ?: Intent(context, targetFor(screen))

    fun open(context: Context, screen: Screen) {
        context.startActivity(intentFor(context, screen))
    }
}
