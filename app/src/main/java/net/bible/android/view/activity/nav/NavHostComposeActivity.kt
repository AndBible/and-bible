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
package net.bible.android.view.activity.nav

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.SaveOrShare
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.LabelAddedOrUpdatedEvent
import net.bible.android.control.link.LinkControl
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.report.AiBugReport
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.SettingsLevel
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.ai.resolvedCustomPromptValue
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.android.view.activity.bookmark.LabelEditContract
import net.bible.android.view.activity.bookmark.LabelEditMapper
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.bookmark.customIconMap
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.bookmark.toLabelItem
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.search.EPUB_SEARCH_TYPE_KEY
import net.bible.android.view.activity.search.epubKeyFor
import net.bible.android.view.activity.search.epubSearchModeFromClassicName
import net.bible.android.view.activity.search.toClassicSearchTypeName
import net.bible.android.view.activity.settings.AppSettingsServiceImpl
import net.bible.android.view.activity.settings.SettingsReset
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.pause
import net.bible.service.common.displayName
import net.bible.service.common.htmlToSpan
import net.bible.service.common.labelsAndBookmarksPlaylist
import net.bible.service.common.studyPadsVideo
import net.bible.service.download.FakeBookFactory
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exportStudyPads
import net.bible.service.db.ReadingPlansUpdatedViaSyncEvent
import net.bible.service.db.BookmarksUpdatedViaSyncEvent
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.PromptCsvUtils
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.readingplan.OneDaysReadingsDto
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.StudyPadKey
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mydocument.AiDocPagesChangedEvent
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.ai.AgentPermissionModeIds
import net.bible.sharedcore.ai.AiConnectionLabels
import net.bible.sharedcore.ai.AiConnectionSettingsController
import net.bible.sharedcore.ai.AiDocumentFilterController
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.AiPromptsController
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.DocumentFilterService
import net.bible.sharedcore.ai.GlobalToolPermissionsController
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.PromptEditController
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.ai.RawLlmLogController
import net.bible.sharedcore.ai.RawLogHistoryController
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.bookmark.BookmarksController
import net.bible.sharedcore.bookmark.DeletePrompt
import net.bible.sharedcore.bookmark.LabelEditController
import net.bible.sharedcore.bookmark.LabelEditService
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedcore.bookmark.defaultLabelName
import net.bible.sharedcore.bookmark.LabelEditResult as ControllerLabelEditResult
import net.bible.sharedcore.download.CustomRepositoryController
import net.bible.sharedcore.download.CustomRepositoryEditorController
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedcore.mydocuments.MyDocumentPagesController
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.LabelEditResult as NavLabelEditResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.progress.ReadHistoryEntry
import net.bible.sharedcore.progress.ReadingProgressController
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedcore.readingplan.DailyReadingController
import net.bible.sharedcore.readingplan.DailyReadingListController
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.DayEntry
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.ReadingPlanSelectorController
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.EpubSearchResultsController
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchFormController
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.search.SearchResultsController
import net.bible.sharedcore.search.SearchType
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedcore.settings.AppSettingsController
import net.bible.sharedcore.settings.AppSettingsLabels
import net.bible.sharedcore.settings.ReadingProgressSettingsController
import net.bible.sharedcore.settings.ReadingProgressSettingsLabels
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedcore.settings.SyncSettingsLabels
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.ai.nav.AiConnectionSettingsDeps
import net.bible.sharedui.ai.nav.AiDocumentFilterDeps
import net.bible.sharedui.ai.nav.AiModelsDeps
import net.bible.sharedui.ai.nav.AiNavDeps
import net.bible.sharedui.ai.nav.AiProvidersDeps
import net.bible.sharedui.ai.nav.AiPromptsDeps
import net.bible.sharedui.ai.nav.GlobalToolPermissionsDeps
import net.bible.sharedui.ai.nav.PromptEditDeps
import net.bible.sharedui.ai.nav.RawLlmLogDeps
import net.bible.sharedui.ai.nav.RawLogHistoryDeps
import net.bible.sharedui.ai.nav.ToolInfoDeps
import net.bible.sharedui.ai.nav.aiNavGraph
import net.bible.sharedui.bookmark.ManageLabelsHelpDialog
import net.bible.sharedui.bookmark.nav.BookmarkNavDeps
import net.bible.sharedui.bookmark.nav.BookmarksDeps
import net.bible.sharedui.bookmark.nav.LabelEditDeps
import net.bible.sharedui.bookmark.nav.ManageLabelsDeps
import net.bible.sharedui.bookmark.nav.bookmarkNavGraph
import net.bible.sharedui.download.nav.CustomRepositoriesDeps
import net.bible.sharedui.download.nav.CustomRepositoryEditorDeps
import net.bible.sharedui.download.nav.DownloadNavDeps
import net.bible.sharedui.download.nav.ProgressStatusDeps
import net.bible.sharedui.download.nav.downloadNavGraph
import net.bible.sharedui.mydocuments.nav.MyDocumentPagesDeps
import net.bible.sharedui.mydocuments.nav.MyDocumentsNavDeps
import net.bible.sharedui.mydocuments.nav.myDocumentsNavGraph
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbActionSheet
import net.bible.sharedui.components.AbActionSheetRow
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMultiSelectSheet
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.readingplan.nav.DailyReadingDeps
import net.bible.sharedui.readingplan.nav.DailyReadingLoad
import net.bible.sharedui.readingplan.nav.DayListDeps
import net.bible.sharedui.readingplan.nav.LoadedReadingDay
import net.bible.sharedui.readingplan.nav.ReadingPlanNavDeps
import net.bible.sharedui.readingplan.nav.ReadingPlanSelection
import net.bible.sharedui.readingplan.nav.SelectorDeps
import net.bible.sharedui.readingplan.nav.readingPlanNavGraph
import net.bible.sharedui.search.nav.IndexOutcome
import net.bible.sharedui.search.nav.EpubSearchFormDeps
import net.bible.sharedui.search.nav.EpubSearchResultsDeps
import net.bible.sharedui.search.nav.EpubSearchSetup
import net.bible.sharedui.search.nav.EpubSearchTarget
import net.bible.sharedui.search.nav.IndexTarget
import net.bible.sharedui.search.nav.SearchFormDeps
import net.bible.sharedui.search.nav.SearchFormSetup
import net.bible.sharedui.search.nav.SearchIndexPromptDeps
import net.bible.sharedui.search.nav.SearchIndexProgressDeps
import net.bible.sharedui.search.nav.SearchNavDeps
import net.bible.sharedui.search.nav.SearchResultsDeps
import net.bible.sharedui.search.nav.SearchSubmission
import net.bible.sharedui.search.nav.TranslationSelection
import net.bible.sharedui.search.nav.searchNavGraph
import net.bible.sharedui.settings.nav.AppSettingsDeps
import net.bible.sharedui.settings.nav.ReadHistoryRequest
import net.bible.sharedui.settings.nav.ReadingProgressDeps
import net.bible.sharedui.settings.nav.ReadingProgressSettingsDeps
import net.bible.sharedui.settings.nav.SettingsNavDeps
import net.bible.sharedui.settings.nav.SyncSettingsDeps
import net.bible.sharedui.settings.nav.settingsNavGraph
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.search.SearchType as JSwordSearchType
import org.crosswire.jsword.versification.BookName
import org.koin.android.ext.android.inject

/**
 * The single Android host for the Compose navigation graph. Screens migrated off their own
 * Activities become destinations inside it; `ScreenLauncher` routes to it by [EXTRA_ROUTE].
 *
 * It still extends [ActivityBase] on purpose: every migrated destination inherits the base's
 * theming, locale attachment, edge-to-edge setup, `CurrentActivityHolder` registration and — the
 * one that matters for this cluster — `awaitIntent`, which the SAF flows need.
 */
class NavHostComposeActivity : ActivityBase() {
    private val documentFilterService: DocumentFilterService by inject()
    private val toolPermissionService: ToolPermissionService by inject()
    private val llmModelService: LlmModelService by inject()
    private val aiSettingsService: AiSettingsService by inject()
    private val llmProviderService: LlmProviderService by inject()
    private val promptService: PromptService by inject()
    private val rawLogService: RawLogService by inject()
    private val readingPlanControl: ReadingPlanControl by inject()
    private val speakControl: SpeakControl by inject()
    private val searchControl: SearchControl by inject()
    private val bibleSearchService: BibleSearchService by inject()
    private val searchIndexService: SearchIndexService by inject()
    private val windowControl: WindowControl by inject()
    private val pageControl: PageControl by inject()
    private val searchResultsCache: SearchResultsCache by inject()
    private val epubSearchService: EpubSearchService by inject()
    private val linkControl: LinkControl by inject()

    /**
     * Classic `ReadingProgressComposeActivity.kt:74` injects the CONCRETE impl, not the portable
     * `ReadingProgressService` seam, and this host must too: `osisIdForChapter` (the chapter result
     * intent) and the read-history/date-formatting helpers live only on the impl.
     */
    private val readingProgressService: ReadingProgressServiceImpl by inject()

    private val readingProgressSettingsService: ReadingProgressSettingsService by inject()

    /** The label editor's orphaned-bookmark counter and save/delete writes — classic
     *  `LabelEditComposeActivity`'s own injected service, unchanged. */
    private val labelEditService: LabelEditService by inject()
    private val manageLabelsService: ManageLabelsService by inject()
    private val bookmarkControl: BookmarkControl by inject()

    /**
     * Classic `BookmarksComposeActivity.kt:66` injects the CONCRETE impl, not the portable
     * `BookmarksService` seam, and this host must too: resolving a selected/assigned/deleted ROW back
     * to its Room bookmark entity (`bookmarkById`/`bookmarksByIds`/`loadedBookmarks`) is what every
     * one of the list's host-side actions needs, and none of it is on the seam.
     */
    private val bookmarksService: BookmarksServiceImpl by inject()

    /** The custom-repository list/editor pair's Room-backed service -- see `CoreModule.kt:88`. */
    private val customRepositoryService: CustomRepositoryService by inject()

    /**
     * Classic `SettingsComposeActivity`'s own `by lazy` service — NOT a Koin singleton, matching
     * classic: it caches four JSword `Books.installed()` dictionary scans for the screen's lifetime.
     * Host-held because the settings destination's composition is disposed and re-entered whenever
     * another destination sits on top of it, and a service rebuilt then would re-run those scans.
     */
    private val appSettingsService by lazy { AppSettingsServiceImpl() }

    /**
     * Classic `SyncSettingsComposeActivity`'s own `by lazy` service, constructed EXACTLY as it was:
     * `activityProvider` must yield an `ActivityBase` (`CloudSync.signIn` demands one, not a
     * `Context`), and this host is one — so `{ this }` carries over verbatim.
     */
    private val syncSettingsService by lazy {
        SyncSettingsServiceImpl(scope = lifecycleScope, activityProvider = { this })
    }

    /**
     * The AI cluster's three host-lifetime controllers and its two tool lists, LAZY for exactly the
     * reason [appSettingsController] below is (whole-branch review I2).
     *
     * In slice 1 this host served ONLY the AI cluster, so assembling `AiNavDeps` eagerly inside
     * `setContent` cost nothing that opening an AI screen would not have cost anyway. After slices
     * 3/5/6 the same host also serves Search, Reading plan, Reading progress and Settings — the
     * app's most common non-reading screens — and every one of those launches was paying the whole
     * AI cluster's construction on the main thread before its first frame:
     *
     * - [AiModelsController]'s `init` starts a permanent `scope.launch { service.models.collect {…} }`
     *   on `lifecycleScope`;
     * - [AiConnectionSettingsController] needs [buildAiConnectionLabels] (~45 `getString` calls) and
     *   its `init` starts a SECOND permanent collector that rebuilds the entire AI settings item tree
     *   on every snapshot;
     * - [AiProvidersController] is a third service-backed instance;
     * - `ToolRegistry.getAllTools()` plus two full `map`/`filter` passes build the tool-info view data.
     *
     * Behind the deps' `() -> …` getters, none of it runs until an AI DESTINATION composes — which is
     * what the arms' `remember { d.controller() }` / `remember { d.readTools() }` resolve to. Lazy
     * FIELDS rather than per-entry factories, deliberately, for [appSettingsController]'s reason: the
     * collectors and the snapshot should outlive one back-stack entry's composition.
     */
    private val aiModelsController by lazy {
        AiModelsController(service = llmModelService, scope = lifecycleScope)
    }

    private val aiConnectionSettingsController by lazy {
        AiConnectionSettingsController(
            service = aiSettingsService,
            scope = lifecycleScope,
            labels = buildAiConnectionLabels(),
            // The real navigation branching lives in aiNavGraph's AI_CONNECTION_SETTINGS arm (six of
            // its seven edges are navController.navigate(...); RESET_USAGE is the one host callback),
            // not here — see AiConnectionSettingsDeps' kdoc. This constructor param is required but
            // unused: the screen's onNavigate is wired directly in the graph, never through
            // controller::onNavigate.
            onNavigate = {},
        )
    }

    private val aiProvidersController by lazy {
        AiProvidersController(service = llmProviderService, scope = lifecycleScope)
    }

    /**
     * `ToolRegistry.getAllTools()` once, split the way `ToolInfoScreen` wants it. One `by lazy`
     * behind the other two so a host that never opens Tool info never calls into the registry at
     * all — and a host that does still walks it exactly once.
     */
    private val allAiTools by lazy { ToolRegistry.getAllTools() }
    private val aiReadTools by lazy { allAiTools.filter { !it.requiresPermission }.map { it.toToolVd() } }
    private val aiWriteTools by lazy { allAiTools.filter { it.requiresPermission }.map { it.toToolVd() } }

    /**
     * The settings cluster's two controllers, LAZY on purpose. `SettingsNavDeps` is assembled on
     * every launch of this host — which now serves four clusters — but constructing either of these
     * is expensive and settings-specific: [AppSettingsController] forces [appSettingsService]
     * (four JSword `Books.installed()` dictionary scans and ~51 `CommonUtils.settings` reads, each
     * a Room query on the main thread), builds the whole `SettingsScreenState` item tree, needs
     * ~90 `getString` calls for its labels and launches a permanent snapshot collector;
     * [SyncSettingsController] forces [syncSettingsService]'s eager `build()` (`CloudSync.signedIn`,
     * the adapter summaries) plus ~35 more `getString`s. Classic paid that only when Settings
     * itself opened, and a `by lazy` behind the deps' `() -> Controller` getter keeps it that way:
     * nothing is built until a settings DESTINATION composes.
     *
     * Lazy rather than the reading-plan/search clusters' per-entry `controllerFor` factories,
     * deliberately: the state here (the collector, the snapshot) should outlive one back-stack
     * entry's composition, so a destination re-entered after a child pops keeps what it had.
     */
    private val appSettingsController by lazy {
        AppSettingsController(
            service = appSettingsService,
            scope = lifecycleScope,
            labels = buildAppSettingsLabels(),
            // Unused by design: the screen's onNavigate is wired directly in settingsNavGraph's
            // SETTINGS arm, because two of its seven rows are routes in this host's graph. See
            // AppSettingsDeps' kdoc.
            onNavigate = {},
        )
    }

    private val syncSettingsController by lazy {
        SyncSettingsController(
            service = syncSettingsService,
            scope = lifecycleScope,
            labels = buildSyncSettingsLabels(),
            // Screen.CloudDocuments is still its own Activity, so this branch stays on the
            // controller exactly as classic had it.
            onOpenCloudDocuments = { ScreenLauncher.open(this, Screen.CloudDocuments) },
        )
    }

    /**
     * The reading-progress SETTINGS controller, `by lazy` for [appSettingsController]'s reason —
     * cheaper to build (one service snapshot, ~13 `getString`s) but still settings-specific work
     * that no other cluster's destination should pay for when the deps literal is assembled.
     *
     * There is deliberately NO lazy field for the reading-progress SCREEN's controller: that one is
     * a per-back-stack-entry factory ([ReadingProgressDeps.controllerFor], built in
     * [readingProgressControllerFor] below), because its model belongs to one entry and is reloaded
     * on entry anyway.
     */
    private val readingProgressSettingsController by lazy {
        ReadingProgressSettingsController(
            service = readingProgressSettingsService,
            scope = lifecycleScope,
            labels = buildReadingProgressSettingsLabels(),
        )
    }

    /**
     * The route `HistoryManager` should re-launch for whatever this host currently shows, or null
     * when no destination wants a history entry. Set by the reading-plan destinations (and, from
     * slice 5, the search ones) through [ReadingPlanNavDeps.setHistoryRoute] — see [setHistoryRoute].
     */
    private var historyRoute: String? = null

    /**
     * Which back-stack entry published [historyRoute] — an opaque per-entry token the destination
     * `remember`s. Identity, not value: two entries for the SAME destination publish the identical
     * route string (both read the host-global [loadedReadingDay]), so a value comparison in
     * [clearHistoryRoute] would let a disposing lower entry wipe the route the visible one had just
     * published. That is reachable since [onNewIntent] can push a second `DAILY_READING_PATTERN`
     * entry while a child destination is on top.
     */
    private var historyRouteOwner: Any? = null

    /**
     * Classic `DailyReadingComposeActivity` mutated ITS OWN intent with `ReadingPlanKeys.PLAN`/`DAY`
     * so `HistoryManager` could re-launch it on the right day (`HistoryManager.kt:173-175` ->
     * `IntentHistoryItem.revertTo()`). A nav destination has no intent of its own, so the host
     * builds one from the destination's route instead. Safe as a live getter: `HistoryManager`
     * reads this at the moment it creates the history item, not once at `onCreate`.
     */
    override val intentForHistoryList: Intent
        get() = historyRoute?.let { intentFor(this, it) } ?: super.intentForHistoryList

    /**
     * The other half of the seam, and the reason there is NO
     * `override val integrateWithHistoryManager get() = historyRoute != null` here: that `open val`
     * is read exactly once, by `ActivityBase.onCreate`'s `setNewHistoryTraversal(...)`
     * (`ActivityBase.kt:89`), which passes it BY VALUE into
     * `HistoryTraversalFactory.createHistoryTraversal(...)` (`:422`) where it is stored on the
     * `HistoryTraversal`. `HistoryManager.kt:172` then reads
     * `andBibleActivity.isIntegrateWithHistoryManager` — the `HistoryTraversal`-backed var exposed
     * at `ActivityBase.kt:275-278` — never the `open val`. An overridden getter would therefore be
     * sampled while `historyRoute` is still null and never fire again. So this drives that var
     * directly, through the same setter classic `DailyReadingComposeActivity.kt:77` used.
     *
     * [owner] is the publishing back-stack entry's token — see [historyRouteOwner].
     */
    private fun setHistoryRoute(owner: Any, route: String) {
        historyRouteOwner = owner
        historyRoute = route
        isIntegrateWithHistoryManager = true
    }

    /**
     * The compare-and-clear half of the seam — see [ReadingPlanNavDeps.clearHistoryRoute] for the
     * ordering hazard it exists for: navigation-compose runs the ENTERING destination's effects
     * before disposing the exiting one, so a leaving destination that cleared unconditionally would
     * wipe the route its successor had just published. The comparison is on the OWNER's identity
     * rather than the route's value, which is strictly stronger — see [historyRouteOwner].
     */
    private fun clearHistoryRoute(owner: Any) {
        if (historyRouteOwner !== owner) return
        historyRouteOwner = null
        historyRoute = null
        isIntegrateWithHistoryManager = false
    }

    /**
     * The graph's [NavHostController], published out of the composition so [onNewIntent] can reach
     * it. Compose owns the instance ([rememberNavController]); this field is only a handle, bound
     * and unbound by a `DisposableEffect` keyed on the controller inside [onCreate]'s `setContent`,
     * so it is null exactly when there is no live composition to navigate.
     */
    private var navController: NavHostController? = null

    /**
     * A history entry ([intentForHistoryList]) is re-launched by `IntentHistoryItem.revertTo()`
     * (`IntentHistoryItem.kt:59-65`) with `FLAG_ACTIVITY_REORDER_TO_FRONT`, which matches on the
     * COMPONENT alone. One component now serves every migrated cluster, so without this override a
     * reading-plan history entry could reorder an existing AI-cluster instance of this host to the
     * front and simply drop the stored [EXTRA_ROUTE] — showing an unrelated screen. The manifest
     * declares `android:launchMode="singleTop"` so the re-launch lands here instead of building a
     * second instance, and this navigates the live graph to the requested route.
     *
     * It works for ANY cluster's route because every cluster's graph is registered into the one
     * `NavHost` below, regardless of which route the host was started with.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The route check comes FIRST: a route-less new intent must not become this Activity's
        // intent, or a later recreate() (the ReadingPlansUpdatedViaSyncEvent handler calls one)
        // would re-run onCreate's requireNotNull(EXTRA_ROUTE) against it and throw.
        val route = intent.getStringExtra(EXTRA_ROUTE) ?: return
        setIntent(intent)

        // A daily-reading route is applied HOST-SIDE, here, BEFORE the navigate — never by leaving
        // it to the destination to notice. The destination prefers its last loaded day over its
        // route arguments (that preference is what stops a child-pop re-entry from reverting the
        // screen — see ReadingPlanNavGraph's initial-load effect), and the navigate below cannot be
        // relied on to override it: `launchSingleTop` compares DESTINATIONS, so re-navigating onto
        // an already-top daily-reading entry goes through `launchSingleTopInternal`, which rebuilds
        // the entry from the old one — same id, same saved state — and `NavHost` keys its
        // `AnimatedContent` on that id, so the arm is not even disposed. If the rebuilt entry's
        // arguments happen to equal the old ones, `NavBackStackEntry.equals` holds, the back stack's
        // StateFlow conflates the identical list and NOTHING recomposes: no effect restarts, no
        // reload happens. That is reachable whenever the entry was stamped with (plan, day) by an
        // earlier revert and the user has since drifted to another day via the day list, "Done" or
        // the date picker.
        //
        // So the host performs the load itself. It is the same call the graph's `loadDay` dep makes,
        // which means `loadedReadingDay` ends up SET to the requested day (never null) and the live
        // controller — if there is one — gets the pushed snapshot immediately. Every path then
        // converges: a composed arm is already showing the right day whether or not the navigate
        // does anything, and a fresh composition (selector/day list on top) reads the now-correct
        // `loaded` through its own `last != null` branch. The cost is one redundant reload when the
        // arguments DO change and the destination reloads too; the outcome is identical either way.
        //
        // The load's result is deliberately ignored: NO_PLAN/FAILED are the graph's business, and
        // whichever path follows re-runs the same load through the arm's own handling of them.
        if (isDailyReadingRoute(route)) {
            val (plan, day) = NavRoutes.readDailyReading(route)
            loadReadingPlanDay(plan, day)
        }

        val controller = navController
        if (controller == null) {
            // Reachable: a restored-then-reordered Activity can receive LaunchActivityItem and
            // NewIntentItem in one client transaction, i.e. before setContent's first composition
            // commits (onCreate has already captured its startRoute by then). Stash and let the
            // composition consume it — see the LaunchedEffect next to the publishing
            // DisposableEffect in onCreate.
            pendingNewIntentRoute = route
            return
        }
        navigateToRoute(controller, route)
    }

    /**
     * A route delivered by [onNewIntent] that arrived before there was a graph to navigate; the
     * composition consumes it. Null whenever nothing is pending.
     */
    private var pendingNewIntentRoute: String? = null

    /**
     * `launchSingleTop`: re-delivering a route whose destination is already on top must not stack a
     * duplicate entry. Anything already below stays put, so Back still returns where the user was —
     * the same thing a reordered-to-front Activity would have done.
     *
     * Catches `IllegalArgumentException` — and ONLY that — because it is what
     * `NavController.navigate` throws for a route no destination matches. Not externally reachable
     * (this Activity has no intent-filter and is not exported), but turning a main-thread crash
     * into the same silent no-op the two `?: return`s above already establish costs one line. A
     * broader catch would also swallow the `IllegalStateException` a controller with no graph set
     * throws, i.e. degrade a wiring bug into a logged no-op; that one must still crash.
     */
    private fun navigateToRoute(controller: NavHostController, route: String) {
        try {
            controller.navigate(route) { launchSingleTop = true }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG_NAV_HOST, "Ignoring an unroutable EXTRA_ROUTE: $route", e)
        }
    }

    /**
     * Whether [route] addresses the daily-reading destination. The `?` boundary is load-bearing:
     * [NavRoutes.READING_PLAN_DAY_LIST] (`readingPlan/dayList`) shares the argument-free
     * daily-reading route's prefix (`readingPlan/day`), so a bare `startsWith` would match it too.
     */
    private fun isDailyReadingRoute(route: String): Boolean {
        val base = NavRoutes.dailyReading()
        return route == base || route.startsWith("$base?")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startRoute = requireNotNull(intent.getStringExtra(EXTRA_ROUTE)) {
            "NavHostComposeActivity requires EXTRA_ROUTE — launch it via NavHostComposeActivity.intentFor()"
        }
        setContent {
            AbAppTheme {
                val navController = rememberNavController()
                // Publish the controller for onNewIntent (see its kdoc); unbound with the
                // composition, so the field is never a handle onto a dead graph.
                DisposableEffect(navController) {
                    this@NavHostComposeActivity.navController = navController
                    onDispose { this@NavHostComposeActivity.navController = null }
                }
                // An onNewIntent route that arrived before this composition existed — see
                // onNewIntent's null-controller branch.
                LaunchedEffect(navController) {
                    pendingNewIntentRoute?.let { route ->
                        pendingNewIntentRoute = null
                        navigateToRoute(navController, route)
                    }
                }
                val deps = remember {
                    AiNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        toolInfo = ToolInfoDeps(
                            readTools = { aiReadTools },
                            writeTools = { aiWriteTools },
                            helpBody = { getString(R.string.help_tool_info_text) },
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#ai-tools",
                        ),
                        aiDocumentFilter = AiDocumentFilterDeps(
                            // A fresh instance per back-stack entry -- see AiDocumentFilterDeps'
                            // kdoc (C1: this used to be a single remember{} at host scope, which is
                            // why "Discard changes?" did not actually discard anything).
                            controllerFor = { AiDocumentFilterController(service = documentFilterService, scope = lifecycleScope) },
                            helpBody = { getString(R.string.help_ai_document_filter_text) },
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#available-data-and-documents",
                        ),
                        globalToolPermissions = GlobalToolPermissionsDeps(
                            // Same fix, same reason -- see AiDocumentFilterDeps' kdoc (C1).
                            controllerFor = { GlobalToolPermissionsController(service = toolPermissionService, scope = lifecycleScope) },
                            helpBody = { getString(R.string.help_global_tool_permissions_text) },
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#setting-permissions",
                        ),
                        aiModels = AiModelsDeps(
                            controller = { aiModelsController },
                            providersForPicker = { llmModelService.providersForPicker() },
                            helpBody = { getString(R.string.help_ai_models_text) },
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#available-models",
                            onResume = { llmModelService.refresh() },
                        ),
                        aiConnectionSettings = AiConnectionSettingsDeps(
                            controller = { aiConnectionSettingsController },
                            languageChoices = { buildAiLanguageChoices() },
                            customLanguageTag = CUSTOM_LANGUAGE_TAG,
                            onCustomPromptSave = { key, value -> onAiConnectionCustomPromptSave(key, value) },
                            customPromptTextFor = { key -> aiConnectionCustomPromptTextFor(key) },
                            onResetUsageConfirm = { showAiConnectionResetUsageConfirm() },
                            actions = { AiConnectionHelpAction() },
                            onResume = { aiSettingsService.refresh() },
                        ),
                        aiProviders = AiProvidersDeps(
                            controller = { aiProvidersController },
                            helpBody = { getString(R.string.help_ai_providers_text) },
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#choosing-a-provider",
                            unknownErrorMessage = { getString(R.string.unknown_error) },
                            onResume = { llmProviderService.refresh() },
                        ),
                        aiPrompts = AiPromptsDeps(
                            controllerFor = { onOpenPrompt, onNewPrompt, onOpenConnectionSettings ->
                                AiPromptsController(
                                    service = promptService,
                                    scope = lifecycleScope,
                                    onOpenPrompt = onOpenPrompt,
                                    onNewPrompt = onNewPrompt,
                                    onOpenConnectionSettings = onOpenConnectionSettings,
                                )
                            },
                            helpBody = { getString(R.string.help_ai_settings_text) },
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html",
                            // Launched on THIS host's lifecycleScope, not a scope owned by the
                            // graph's composable arm -- see AiPromptsDeps' kdoc for why: a
                            // rememberCoroutineScope() in that arm would be cancelled the instant
                            // its back-stack entry stops being the top one (near-instant on
                            // Up/navigate), unlike lifecycleScope (cancelled only at
                            // onDestroy()), and interrupting installCsvAsAddon's file-copy+DB
                            // sequence mid-way is strictly worse than classic's behaviour.
                            onImportCsv = { lifecycleScope.launch { importPrompts() } },
                            onExportCsv = { lifecycleScope.launch { exportPrompts() } },
                            onResume = { promptService.refresh() },
                        ),
                        promptEdit = PromptEditDeps(
                            controllerFor = { promptId, template, defaultContext ->
                                PromptEditController(
                                    service = promptService,
                                    promptId = promptId,
                                    template = template,
                                    defaultContext = defaultContext,
                                )
                            },
                            categories = { promptService.categories() },
                            toolsByCategory = { promptService.toolsByCategory() },
                            modelChoices = { promptService.modelChoices() },
                            globalToolPermission = { toolId -> promptService.globalToolPermission(toolId) },
                            globalMaxIterationsLabel = {
                                val globalMaxIterations = CommonUtils.aiSettings.maxIterations
                                if (globalMaxIterations <= 0) getString(R.string.prompt_max_iterations_unlimited)
                                else globalMaxIterations.toString()
                            },
                            helpBody = { getString(R.string.help_prompt_edit_text) },
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#custom-prompts",
                            onPromptCopied = {
                                Toast.makeText(this@NavHostComposeActivity, R.string.prompt_copied, Toast.LENGTH_SHORT).show()
                            },
                        ),
                        rawLlmLog = RawLlmLogDeps(
                            controllerFor = { RawLlmLogController(service = rawLogService, scope = lifecycleScope) },
                            defaultTitle = { getString(R.string.raw_llm_log_title) },
                            recordTitleFor = ::rawLlmLogRecordTitle,
                            onCopy = ::copyRawLlmLog,
                            onShare = ::shareRawLlmLog,
                            onDelete = { recordId -> rawLogService.deleteByIds(setOf(recordId)) },
                            onReportBug = ::reportRawLlmLogBug,
                        ),
                        rawLogHistory = RawLogHistoryDeps(
                            controllerFor = { onOpenLog ->
                                RawLogHistoryController(service = rawLogService, scope = lifecycleScope, onOpenLog = onOpenLog)
                            },
                            helpBody = { getString(R.string.help_ai_connection_text) },
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html",
                            onResume = { rawLogService.refresh() },
                        ),
                    )
                }
                val readingPlanDeps = remember {
                    ReadingPlanNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        setHistoryRoute = { owner, route -> setHistoryRoute(owner, route) },
                        clearHistoryRoute = { owner -> clearHistoryRoute(owner) },
                        pendingSelection = pendingReadingPlanSelection,
                        dailyReading = DailyReadingDeps(
                            controllerFor = { onChangePlan, onChangeDay ->
                                buildDailyReadingController(onChangePlan, onChangeDay)
                            },
                            loadDay = { plan, day -> loadReadingPlanDay(plan, day) },
                            loaded = loadedReadingDay,
                            subscribeEvents = { subscribeDailyReadingEvents() },
                            onShowStartDatePicker = { showReadingPlanStartDatePicker() },
                            onImportPlan = { importPlanLauncher.launch("application/zip") },
                            onPlanMissing = { offerReadingPlanSelector() },
                            title = getString(R.string.rdg_plan_title),
                        ),
                        dayList = DayListDeps(
                            controllerFor = { onSelect ->
                                DailyReadingListController(
                                    loadDays = {
                                        readingPlanControl.currentPlansReadingList.map {
                                            DayEntry(it.day, readingPlanDayPrimaryText(it), it.readingsDesc)
                                        }
                                    },
                                    onSelect = onSelect,
                                )
                            },
                            subscribeEvents = { onReadingPlansChanged ->
                                subscribeReadingPlansUpdated(onReadingPlansChanged)
                            },
                            title = getString(R.string.rdg_plan_title),
                        ),
                        selector = SelectorDeps(
                            controllerFor = { onSelect ->
                                ReadingPlanSelectorController(
                                    loadPlans = {
                                        readingPlanControl.readingPlanList.map {
                                            PlanEntry(it.planCode, it.planName ?: "", it.planDescription ?: "")
                                        }
                                    },
                                    hasDuplicates = { readingPlanControl.readingPlanUserDuplicates },
                                    onSelect = { planCode ->
                                        // Classic's guard, kept host-side: the plan may have
                                        // vanished (sync) between the list load and the tap.
                                        val dto = readingPlanControl.readingPlanList
                                            .firstOrNull { it.planCode == planCode }
                                        if (dto != null) {
                                            readingPlanControl.startReadingPlan(dto)
                                            onSelect(planCode)
                                        }
                                    },
                                    onReset = { planCode -> readingPlanControl.reset(planCode) },
                                )
                            },
                            subscribeEvents = { onReadingPlansChanged ->
                                subscribeReadingPlansUpdated(onReadingPlansChanged)
                            },
                            title = getString(R.string.rdg_plan_selector_title),
                        ),
                    )
                }
                val searchDeps = remember {
                    SearchNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        setHistoryRoute = { owner, route -> setHistoryRoute(owner, route) },
                        clearHistoryRoute = { owner -> clearHistoryRoute(owner) },
                        searchForm = SearchFormDeps(
                            prepare = { restoredBibleBook -> prepareSearchForm(restoredBibleBook) },
                            loadTranslations = { loadSearchTranslations() },
                            controllerFor = { currentBookName -> buildSearchFormController(currentBookName) },
                            submit = { request -> submitSearch(request) },
                        ),
                        searchIndex = SearchIndexPromptDeps(
                            resolve = { searchDocument -> resolveIndexTarget(searchDocument) },
                            createIndex = { documentId -> searchIndexService.createIndex(documentId) },
                            title = getString(R.string.search_index),
                        ),
                        searchIndexProgress = SearchIndexProgressDeps(
                            // Classic SearchIndexProgressComposeActivity.kt:82, which is a suspend
                            // call: the host owns the scope so the graph's deps slot can stay a
                            // plain () -> Unit.
                            requestNotificationPermission = {
                                lifecycleScope.launch {
                                    CommonUtils.requestNotificationPermission(this@NavHostComposeActivity)
                                }
                            },
                            observeJobs = { onJobs, onJobFinished -> observeIndexJobs(onJobs, onJobFinished) },
                            awaitIndexed = { documentId -> awaitIndexed(documentId) },
                            title = getString(R.string.search_index),
                        ),
                        searchResults = SearchResultsDeps(
                            controllerFor = { buildSearchResultsController() },
                            isScriptureReference = { searchText -> isScriptureReference(searchText) },
                            openScriptureReference = { searchText -> openScriptureReference(searchText) },
                            title = { total, selectedCount ->
                                getString(R.string.multi_search_results, total, selectedCount)
                            },
                            showError = { showSearchError() },
                            openReference = { referenceName, translationId, selectedTranslations ->
                                openSearchResult(referenceName, translationId, selectedTranslations)
                            },
                            openResultsInWindow = { rows, selectedTranslations ->
                                openSearchResultsInAWindow(rows, selectedTranslations)
                            },
                        ),
                        epubSearch = EpubSearchFormDeps(
                            prepare = { prepareEpubSearchForm() },
                            loadMode = { loadEpubSearchMode() },
                            saveMode = { mode -> saveEpubSearchMode(mode) },
                            modeWireName = { mode -> mode.toClassicSearchTypeName() },
                            showHelp = { showEpubSearchHelp() },
                        ),
                        epubSearchResults = EpubSearchResultsDeps(
                            resolve = { searchDocument -> resolveEpubSearchTarget(searchDocument) },
                            modeFromWireName = { name -> epubSearchModeFromClassicName(name) },
                            controllerFor = { documentId -> buildEpubSearchResultsController(documentId) },
                            logSearch = { documentId, searchText, mode ->
                                Log.i(TAG_EPUB_SEARCH_RESULTS, "Searching '$searchText' ($mode) in $documentId")
                            },
                            title = { resultCount, documentAbbreviation ->
                                epubSearchResultsTitle(resultCount, documentAbbreviation)
                            },
                            showError = { showSearchError() },
                        ),
                    )
                }
                val settingsDeps = remember {
                    SettingsNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        appSettings = AppSettingsDeps(
                            // A getter over the host's `by lazy`, never an instance built here:
                            // assembling these deps runs on EVERY host launch, for every cluster,
                            // and building this controller costs four Books.installed() scans plus
                            // ~51 Room-backed settings reads on the main thread. See
                            // [appSettingsController] and AppSettingsDeps.controller's kdoc.
                            controller = { appSettingsController },
                            maybeRecreate = { key -> maybeRecreateForSettingsKey(key) },
                            onConfirmReset = { confirmResetSettings() },
                            onShowDiscreteHelp = { showDiscreteHelpDialog() },
                            onOpenTextDisplaySettings = { openGlobalTextDisplaySettings() },
                            onOpenLinksSettings = { openLinksSettings() },
                            onCrashApp = { crashApp() },
                            // One getString, unlike the ~90 the label bundle needs — cheap enough
                            // to stay eager.
                            resetContentDescription = getString(R.string.reset_settings),
                            onResume = { appSettingsService.refresh() },
                        ),
                        syncSettings = SyncSettingsDeps(
                            controller = { syncSettingsController },
                            onResume = { syncSettingsService.refresh() },
                        ),
                        readingProgress = ReadingProgressDeps(
                            // A factory, so nothing here constructs a controller or touches the
                            // read-history DAO until the destination actually composes — the same
                            // rule the two getters above follow. See readingProgressControllerFor.
                            controllerFor = { tabArg, onShowHistory, onResult ->
                                readingProgressControllerFor(tabArg, onShowHistory, onResult)
                            },
                            persistTab = { tab -> persistReadingProgressTab(tab) },
                            onApplyHistoryDeletes = { ids, cycle, onDeleted ->
                                lifecycleScope.launch {
                                    readingProgressService.deleteReadHistoryEntries(ids, cycle)
                                    onDeleted()
                                }
                            },
                            onShowHelp = { showReadingProgressHelp() },
                            unmarkConfirmMessage = { rangeName ->
                                getString(R.string.memorize_confirm_unmark, rangeName)
                            },
                            removeTargetConfirmMessage = { rangeName ->
                                getString(R.string.memorize_confirm_remove_target, rangeName)
                            },
                            // classic's android.R.string.ok / cancel — the SYSTEM strings, kept.
                            confirmText = getString(android.R.string.ok),
                            dismissText = getString(android.R.string.cancel),
                        ),
                        readingProgressResults = readingProgressResults,
                        readingProgressSettings = ReadingProgressSettingsDeps(
                            controller = { readingProgressSettingsController },
                        ),
                    )
                }
                val bookmarkDeps = remember {
                    BookmarkNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        bookmarkResults = bookmarkResults,
                        manageLabelsResults = manageLabelsResults,
                        labelEditResults = labelEditResults,
                        bookmarks = BookmarksDeps(
                            // A HOST-memoised factory, not a per-entry one -- see
                            // BookmarksDeps.controllerFor: this controller holds the user's
                            // multi-selection, and the label manager they assign it with sits on top
                            // of this destination.
                            controllerFor = { initialFilterIndex, onSelectBookmark, navigateToManageLabels ->
                                bookmarksControllerFor(initialFilterIndex, onSelectBookmark, navigateToManageLabels)
                            },
                            // Classic carried this as android:label on the host; one host now serves
                            // every cluster. Same string the screen draws in its own top bar.
                            title = getString(R.string.bookmarks_and_mynotes_title),
                            onManageLabelsResult = { result -> applyBookmarksManageLabelsResult(result) },
                            subscribeSyncEvents = { onBookmarksChanged ->
                                subscribeBookmarksUpdated(onBookmarksChanged)
                            },
                        ),
                        manageLabels = ManageLabelsDeps(
                            // A HOST-memoised factory, not a per-entry one — see
                            // ManageLabelsDeps.controllerFor: this controller must survive the label
                            // editor sitting on top of its destination.
                            controllerFor = { data, onEditLabel, onResult ->
                                manageLabelsControllerFor(data, onEditLabel, onResult)
                            },
                            // Classic's `getString(data.titleId)` (`:170`): an Android string
                            // resource id carried INSIDE the route payload, which commonMain cannot
                            // resolve. One of four, by mode.
                            titleFor = { data ->
                                getString(ManageLabelsContract.ManageLabelsData.fromJSON(data).titleId)
                            },
                            onLabelEditResult = { result -> applyLabelEditResult(result) },
                            initialSearchMode = {
                                CommonUtils.settings.getInt(
                                    MANAGE_LABELS_SEARCH_MODE_KEY,
                                    SearchMode.NAME_START.ordinal,
                                )
                            },
                            iconSlot = { customIcon, tint, studyPadMode ->
                                ManageLabelIcon(customIcon, tint, studyPadMode)
                            },
                            actions = { controller -> ManageLabelsActions(controller) },
                            searchActions = { controller ->
                                NewLabelIcon(onClick = controller::newLabel)
                            },
                        ),
                        labelEdit = LabelEditDeps(
                            // A factory, one controller per back-stack entry — and the place the
                            // controller's three outcomes become the nav result's two.
                            controllerFor = { data, onResult -> labelEditControllerFor(data, onResult) },
                            // Classic carried this as android:label="@string/edit_label" on the
                            // host; one host now serves every cluster. Same string the screen draws
                            // in its own top bar.
                            title = getString(R.string.edit_label),
                            // Classic's `remember { customIconMap.keys.toList() + null }` — a pure
                            // constant, so it is built once here instead of per composition. The
                            // trailing null is the "no custom icon" cell.
                            iconKeys = customIconMap.keys.toList() + null,
                            iconSlot = { name, tint -> AndroidLabelIcon(name, tint) },
                            actions = { data, state, onSave, onDelete ->
                                LabelEditActions(data, state, onSave, onDelete)
                            },
                            deletePromptSlot = { prompt, labelName, onConfirm, onDismiss ->
                                LabelEditDeletePrompt(prompt, labelName, onConfirm, onDismiss)
                            },
                            confirmDiscard = { onConfirm -> confirmDiscardLabelEdits(onConfirm) },
                        ),
                    )
                }
                val downloadDeps = remember {
                    DownloadNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        repositoryEditorResults = repositoryEditorResults,
                        customRepositories = CustomRepositoriesDeps(
                            // A per-entry factory, not host-memoised -- see CustomRepositoriesDeps
                            // .controllerFor: this destination is the host's start destination on
                            // every live edge, so there is no sitting child for a memoised
                            // controller to survive.
                            controllerFor = { onDuplicate ->
                                CustomRepositoryController(customRepositoryService, lifecycleScope).apply {
                                    this.onDuplicate = onDuplicate
                                }
                            },
                            title = getString(R.string.custom_repositories),
                            onDuplicate = { name ->
                                ABEventBus.post(ToastEvent(getString(R.string.duplicate_custom_repository, name)))
                            },
                        ),
                        customRepositoryEditor = CustomRepositoryEditorDeps(
                            controllerFor = { initial ->
                                CustomRepositoryEditorController(customRepositoryService, lifecycleScope, initial)
                            },
                            // Classic's manifest gives BOTH Activities in this cluster the same
                            // android:label ("Custom repositories"); one host now serves both.
                            title = getString(R.string.custom_repositories),
                            initialFor = { id -> customRepositoryEditorInitialFor(id) },
                            readClipboard = { readCustomRepositoryClipboard() },
                        ),
                        progressStatus = ProgressStatusDeps(
                            title = getString(R.string.progress_status),
                            // Classic ProgressStatusComposeActivity.kt:84, a suspend call: the host
                            // owns the scope so the graph's deps slot can stay a plain () -> Unit.
                            requestNotificationPermission = {
                                lifecycleScope.launch {
                                    CommonUtils.requestNotificationPermission(this@NavHostComposeActivity)
                                }
                            },
                            observeJobs = { onJobs -> observeProgressStatusJobs(onJobs) },
                        ),
                    )
                }
                val myDocumentsDeps = remember {
                    // Shared local vals rather than two independent copies of "launch the import
                    // picker" / "export this page" / "export these pages": both
                    // MyDocumentPagesDeps' own top-level fields AND the MyDocumentPagesController
                    // myDocumentPagesControllerFor builds are wired to the SAME instances.
                    val onImportMyDocumentPage: () -> Unit =
                        { importMyDocumentPageLauncher.launch(arrayOf("text/*")) }
                    val onExportMyDocumentPage: (Long) -> Unit = { id -> exportMyDocumentPage(id) }
                    val onExportSelectedMyDocumentPages: (List<Long>) -> Unit = { ids ->
                        pendingMyDocumentPagesExportIds = ids
                        exportMyDocumentPagesTreeLauncher.launch(null)
                    }
                    MyDocumentsNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        myDocumentPagesResults = myDocumentPagesResults,
                        myDocumentPages = MyDocumentPagesDeps(
                            controllerFor = { documentId, documentInitials, onResult ->
                                myDocumentPagesControllerFor(
                                    documentId, documentInitials, onResult,
                                    onImport = onImportMyDocumentPage,
                                    onExport = onExportMyDocumentPage,
                                    onExportSelected = onExportSelectedMyDocumentPages,
                                )
                            },
                            titleFor = { documentName -> getString(R.string.my_document_pages_title, documentName) },
                            onImport = onImportMyDocumentPage,
                            onExportSelected = onExportSelectedMyDocumentPages,
                            onExportPage = onExportMyDocumentPage,
                        ),
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    // Paint an opaque themed ground BEHIND the graph. navigation-compose's default
                    // transition is a crossfade, and while both screens are semi-transparent the
                    // window background shows through — which on a dark or e-ink theme reads as a
                    // flash of the wrong colour on every navigation. AMR hit exactly this.
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    aiNavGraph(navController, deps)
                    bookmarkNavGraph(navController, bookmarkDeps)
                    readingPlanNavGraph(navController, readingPlanDeps)
                    searchNavGraph(navController, searchDeps)
                    settingsNavGraph(navController, settingsDeps)
                    downloadNavGraph(navController, downloadDeps)
                    myDocumentsNavGraph(navController, myDocumentsDeps)
                }

                // Host-level, deliberately OUTSIDE the NavHost: `exportStudyPads` runs in
                // lifecycleScope and outlives the destination that started it, so a chooser scoped
                // to that destination's composition could be disposed mid-await and leave the
                // export hanging on a deferred nobody can complete. Verbatim from classic
                // `LabelEditComposeActivity.kt:128-153`, including the no-Cancel-row shape (round
                // 14a G2.9): dismissing IS "chose nothing", completing with null.
                // The label manager's help dialog and StudyPad-export multiselect, host-level for
                // `destinationRequest`'s reason above and for one of their own: both are raised from
                // the ManageLabels top-bar overflow, which is a `RowScope` slot inside the nav
                // graph's top app bar — a sheet composed there would be a child of the bar's own
                // layout.
                //
                // ORDER IS LOAD-BEARING, and this is the correction of a justification that used to
                // stand here claiming the opposite. The two booleans are read UNCONDITIONALLY, with
                // the plain-field [manageLabelsSession] looked up INSIDE each branch — never the
                // other way round. `manageLabelsSession` is NOT snapshot state, so a scope that can
                // only reach the booleans through a `?.let` on it registers no dependency on them
                // whenever the field is null at the moment this lambda composes. This lambda
                // composes ONCE per Activity (NavHost's own recompositions do not invalidate it),
                // and when the host is launched on `bookmarks` and the user navigates to the label
                // manager from there (`MenuCommandHandler.kt:207`), the field IS null then — so the
                // overflow's Help and Export StudyPads items would flip a boolean nobody observes
                // and open nothing. Reading the state first makes the subscription unconditional;
                // the plain-field read inside is safe because it runs only when a boolean is true,
                // and only a LIVE ManageLabels destination's overflow can make one true.
                // `ManageLabelsOverflowSubscriptionTest` proves both halves — do not re-nest these.
                if (manageLabelsHelpOpen) {
                    manageLabelsSession?.second?.let { session ->
                        ManageLabelsHelpDialog(
                            mode = session.controller.mode,
                            title = getString(session.data.titleId),
                            // Classic showed this only for WORKSPACE and HIDE, the two scoped
                            // settings (`ManageLabelsComposeActivity.kt:254-263`).
                            scopeSentence = if (
                                session.controller.mode == ManageLabelsMode.WORKSPACE ||
                                session.controller.mode == ManageLabelsMode.HIDELABELS
                            ) {
                                getString(
                                    R.string.setting_scope,
                                    getString(
                                        if (session.data.isWindow) R.string.setting_scope_window
                                        else R.string.setting_scope_workspace,
                                    ),
                                )
                            } else null,
                            // STUDYPAD keeps its own playlist; the other three modes use the
                            // Labels & Bookmarks one. Don't collapse this to one URL.
                            readMoreUrl = if (session.controller.mode == ManageLabelsMode.STUDYPAD) {
                                studyPadsVideo
                            } else {
                                labelsAndBookmarksPlaylist
                            },
                            onDismiss = { manageLabelsHelpOpen = false },
                        )
                    }
                }

                // Classic `ManageLabelsComposeActivity.kt:218-248` (the export_studypads menu
                // handler): a multiselect over every assignable label, then exportStudyPads for
                // the chosen ones. The `manageLabelsSession != null` half keeps the old gate ("only
                // while a label manager is live") without putting the session read in FRONT of the
                // boolean — see the note above; `&&` short-circuits left to right, so the state is
                // still read on every composition of this scope.
                if (manageLabelsExportOpen && manageLabelsSession != null) {
                    val exportableLabels = remember(manageLabelsExportOpen) { bookmarkControl.assignableLabels }
                    AbMultiSelectSheet(
                        open = true,
                        title = getString(R.string.export_something, getString(R.string.studypads)),
                        options = exportableLabels,
                        selectedIds = emptyList(),
                        idOf = { it.id.toString() },
                        labelOf = { it.displayName },
                        confirmText = getString(R.string.okay),
                        dismissText = getString(R.string.cancel),
                        onConfirm = { ids ->
                            manageLabelsExportOpen = false
                            val selected = exportableLabels.filter { ids.contains(it.id.toString()) }
                            if (selected.isNotEmpty()) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    exportStudyPads(
                                        this@NavHostComposeActivity,
                                        *selected.toTypedArray(),
                                        chooseDestination = ::askDestination,
                                    )
                                }
                            }
                        },
                        onDismiss = { manageLabelsExportOpen = false },
                        selectAllText = getString(R.string.select_all),
                        selectNoneText = getString(R.string.select_none),
                    )
                }

                destinationRequest?.let { req ->
                    AbActionSheet(
                        open = true,
                        title = getString(R.string.export_destination_title),
                        message = getString(R.string.export_destination_message),
                        onDismiss = { req.complete(null) },
                    ) {
                        AbActionSheetRow(
                            label = getString(R.string.share),
                            onClick = { req.complete(SaveOrShare.SHARE) },
                            icon = { Icon(painterResource(R.drawable.ic_baseline_share_24), contentDescription = null) },
                        )
                        AbActionSheetRow(
                            label = getString(R.string.backup_phone_storage),
                            onClick = { req.complete(SaveOrShare.SAVE) },
                            icon = { Icon(painterResource(R.drawable.ic_save_24dp), contentDescription = null) },
                        )
                    }
                }
            }
        }
    }

    // --- Bookmarks host baggage ----------------------------------------------------------------
    // Ported from BookmarksComposeActivity (which a later task deletes). Everything here needs a
    // `:app` type the graph cannot see -- the Room bookmark entities behind the rows, BookmarkControl,
    // SpeakControl, the workspace settings, an `Activity` for the CSV chooser, and an
    // `android.app.AlertDialog` -- which is the line that decides what is a deps slot and what is not.

    /**
     * The memo behind [BookmarksDeps.controllerFor], and the in-flight state of whichever label-manager
     * round trip is open.
     *
     * [pendingAssign] is what makes ONE result channel serve classic's TWO round trips: the assign one
     * (`BookmarksComposeActivity.kt:199-223`) has to write the returned label set onto the bookmarks the
     * user had selected when it started, and the manage one (`:257-271`) only updates the workspace. A
     * `ManageLabelsResult` carries the returned JSON and nothing else, so the DISTINCTION has to be
     * remembered at the point the payload was built -- which is here, the same shape
     * [ManageLabelsSession.pendingEdit] uses for the editor round trip one level down. The bookmark
     * ENTITIES are held rather than their ids because that is what classic held across its own
     * `awaitIntent`; re-resolving ids afterwards would depend on the list not having reloaded meanwhile.
     */
    private class BookmarksSession {
        lateinit var controller: BookmarksController
        var pendingAssign: List<BookmarkEntities.BaseBookmarkWithNotes>? = null
    }

    /**
     * The bookmark list's memoised session, keyed on the route's filter index exactly as
     * [manageLabelsSession] is keyed on its payload -- so a route naming a DIFFERENT label filter can
     * never be served the previous list's controller. (No live edge re-navigates this host to a second
     * bookmarks route today, since `BOOKMARKS_PATTERN` is a root destination reached only as the
     * host's start destination; the key costs one word and removes the trap rather than relying on
     * that staying true.)
     *
     * Unlike [manageLabelsSession] it is never DROPPED, and that is this destination's shape: being a
     * root destination, it has no re-entry for a stale session to be resumed by, and the host dies
     * with it. Dropping it at its exit would be a reload of a list that is about to disappear.
     */
    private var bookmarksSession: Pair<Int, BookmarksSession>? = null

    /**
     * Classic `BookmarksComposeActivity`'s `controller` (`:79-91`) plus its `onCreate` side effects
     * (`:95`), memoised on [bookmarksSession] -- see [BookmarksDeps.controllerFor] for why this
     * controller must survive the label manager sitting on top of its destination.
     *
     * The three lambdas that are NOT parameters here are the ones classic also kept private to the
     * host: CSV export/import (they hand `this` to `BookmarkControl`, which wants an `Activity`) and
     * the delete confirmation (an `android.app.AlertDialog`; converting platform dialogs is a
     * separate, queued port goal). Each of them ends by refreshing the controller being built, which
     * is why they close over [BookmarksSession] rather than being deps slots the arm would have to
     * hand a self-reference to.
     */
    private fun bookmarksControllerFor(
        initialFilterIndex: Int,
        onSelectBookmark: (BookmarkResult) -> Unit,
        navigateToManageLabels: (payload: String) -> Unit,
    ): BookmarksController {
        bookmarksSession?.let { (key, existing) -> if (key == initialFilterIndex) return existing.controller }

        // Classic `BookmarksComposeActivity.onCreate` (`:95`). Once per opening of the list, as there.
        CommonUtils.settings.setLong("bookmarks-last-used", System.currentTimeMillis())

        val session = BookmarksSession()
        session.controller = BookmarksController(
            service = bookmarksService,
            scope = lifecycleScope,
            initialFilterIndex = initialFilterIndex,
            onSelectBookmark = { id, listPosition ->
                bookmarkResultFor(id, listPosition, session.controller.selectedFilterIndex.value)
                    ?.let(onSelectBookmark)
            },
            onAssignLabels = { ids -> requestAssignLabels(session, ids, navigateToManageLabels) },
            onDeleteSelected = { ids -> confirmDeleteBookmarks(session, ids) },
            onExportCsv = { exportBookmarksCsv(session) },
            onImportCsv = { importBookmarksCsv(session) },
            onManageLabels = { requestManageLabels(session, navigateToManageLabels) },
        )
        bookmarksSession = initialFilterIndex to session
        return session.controller
    }

    /**
     * The half of classic `onSelectBookmark` (`BookmarksComposeActivity.kt:165-187`) that decides WHAT
     * the result says; how it LEAVES is [bookmarkResults]'. Returns null when there is nothing to
     * report -- an id with no loaded bookmark behind it (classic's `?: return`), or the failure its
     * `try` caught.
     *
     * The `try` is classic's, split with the body it wrapped: what can throw here is the speak lookup
     * and `verseRange.start.osisID`, and what can throw there is the history/`setResult` pair. Both
     * halves keep the same log-and-toast, so a user sees exactly what classic showed them either way.
     *
     * [labelNo] is passed in rather than read off [BookmarksSession] so that this stays a function of
     * what the caller saw: it is `controller.selectedFilterIndex.value` at the moment of the tap,
     * which is what classic put in the extra (`:185`).
     */
    private fun bookmarkResultFor(id: String, listPosition: Int, labelNo: Int): BookmarkResult? {
        val bookmark = bookmarksService.bookmarkById(id) ?: return null
        Log.i(TAG_BOOKMARKS, "Bookmark selected:$bookmark")
        return try {
            if (bookmark is BookmarkEntities.BibleBookmarkWithNotes && bookmarkControl.isSpeakBookmark(bookmark)) {
                speakControl.speakFromBookmark(bookmark)
            }
            // `title` is the HOST WINDOW's title, which the arm's LaunchedEffect has already set to
            // this destination's -- the same CharSequence classic read off its own Activity (`:184`).
            val description = title?.toString().orEmpty()
            when (bookmark) {
                is BookmarkEntities.BibleBookmarkWithNotes -> BookmarkResult(
                    verse = bookmark.verseRange.start.osisID,
                    description = description,
                    labelNo = labelNo,
                    listPosition = listPosition,
                )
                is BookmarkEntities.GenericBookmarkWithNotes -> BookmarkResult(
                    key = bookmark.key,
                    book = bookmark.book?.initials,
                    ordinal = bookmark.ordinalStart,
                    description = description,
                    labelNo = labelNo,
                    listPosition = listPosition,
                )
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG_BOOKMARKS, "Error on bookmarkSelected", e)
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
            null
        }
    }

    /**
     * Classic `assignLabels` (`BookmarksComposeActivity.kt:199-214`) up to the point it launched the
     * Intent: the Room read that collects every label already on the selected bookmarks, on
     * `Dispatchers.IO` exactly as classic ran it, then the ASSIGN-mode payload. The navigation itself
     * is the arm's, so it is handed back on the MAIN thread -- `navController.navigate` is not
     * thread-safe and classic's `awaitIntent` was reached from a main-dispatched continuation.
     */
    private fun requestAssignLabels(
        session: BookmarksSession,
        ids: List<String>,
        navigateToManageLabels: (payload: String) -> Unit,
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bookmarks = bookmarksService.bookmarksByIds(ids)
            val labels = mutableSetOf<IdType>()
            for (bookmark in bookmarks) {
                labels.addAll(bookmarkControl.labelsForBookmark(bookmark).map { it.id })
            }
            val payload = ManageLabelsContract.ManageLabelsData(
                mode = ManageLabelsContract.Mode.ASSIGN,
                selectedLabels = labels,
            ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON()
            withContext(Dispatchers.Main) {
                session.pendingAssign = bookmarks
                navigateToManageLabels(payload)
            }
        }
    }

    /** Classic `manageLabels` (`:257-265`): the WORKSPACE-mode payload, and no pending assignment. */
    private fun requestManageLabels(
        session: BookmarksSession,
        navigateToManageLabels: (payload: String) -> Unit,
    ) {
        lifecycleScope.launch(Dispatchers.Main) {
            val payload = ManageLabelsContract.ManageLabelsData(
                mode = ManageLabelsContract.Mode.WORKSPACE,
            ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON()
            session.pendingAssign = null
            navigateToManageLabels(payload)
        }
    }

    /**
     * Both of classic's label-manager continuations, joined at the one channel the arm consumes:
     * `assignLabels`' (`:215-222`) and `manageLabels`' (`:266-270`). Which one runs is decided by the
     * [BookmarksSession.pendingAssign] recorded when the payload was built, not by anything in the
     * result -- see that field's kdoc.
     *
     * There is no `RESULT_OK` guard, and none is missing: classic's was always true on this edge,
     * because the label manager has no cancel path at all (its Back press SAVES) and every exit it
     * has delivers a result. A user who backs out of it has, by classic's own design, saved.
     */
    private fun applyBookmarksManageLabelsResult(result: ManageLabelsResult) {
        val session = bookmarksSession?.second ?: return
        val bookmarks = session.pendingAssign
        session.pendingAssign = null

        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data)
        if (bookmarks != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                for (bookmark in bookmarks) {
                    bookmarkControl.changeLabelsForBookmark(bookmark, resultData.selectedLabels.toList())
                }
                windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
                withContext(Dispatchers.Main) { session.controller.refresh() }
            }
        } else {
            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
            session.controller.refresh()
        }
    }

    /** Classic `onDelete` (`BookmarksComposeActivity.kt:227-240`), dialog and all. */
    private fun confirmDeleteBookmarks(session: BookmarksSession, ids: List<String>) {
        val bookmarks = bookmarksService.bookmarksByIds(ids)
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_bookmarks, bookmarks.size))
            .setPositiveButton(R.string.yes) { _, _ ->
                for (bookmark in bookmarks) {
                    bookmarkControl.deleteBookmark(bookmark)
                }
                session.controller.refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(true)
            .show()
    }

    /** Classic `onExportCsv` (`:244-248`) -- `exportBookmarksToCSV` wants an `Activity`, hence host-side. */
    private fun exportBookmarksCsv(session: BookmarksSession) {
        lifecycleScope.launch {
            val bibleBookmarks = bookmarksService.loadedBookmarks()
                .filterIsInstance<BookmarkEntities.BibleBookmarkWithNotes>()
            bookmarkControl.exportBookmarksToCSV(this@NavHostComposeActivity, bibleBookmarks)
            session.controller.refresh()
        }
    }

    /** Classic `onImportCsv` (`:250-253`). */
    private fun importBookmarksCsv(session: BookmarksSession) {
        lifecycleScope.launch(Dispatchers.Main) {
            bookmarkControl.importBookmarksFromCSV(this@NavHostComposeActivity)
            session.controller.refresh()
        }
    }

    /**
     * Classic's `ABEventBus.register(this) { onMain<BookmarksUpdatedViaSyncEvent> { controller.refresh() } }`
     * (`BookmarksComposeActivity.kt:99`) and its `onDestroy` unregister (`:149`), as the per-destination
     * seam [subscribeReadingPlansUpdated] established: a fresh token per subscription, so an unsubscribe
     * can never take another cluster's listeners down with it.
     */
    private fun subscribeBookmarksUpdated(onBookmarksChanged: () -> Unit): () -> Unit {
        val token = Any()
        ABEventBus.register(token) {
            onMain<BookmarksUpdatedViaSyncEvent> { onBookmarksChanged() }
        }
        return { ABEventBus.unregister(token) }
    }

    // --- ManageLabels host baggage -------------------------------------------------------------
    // Ported from ManageLabelsComposeActivity (which a later task deletes). Everything here needs a
    // `:app` type the graph cannot see -- `BookmarkEntities.Label` and its style columns, the
    // workspace override DAO, `ManageLabelsContract.ManageLabelsData`, `CommonUtils.settings`,
    // `painterResource`, an `android.app.AlertDialog` -- which is exactly the boundary
    // `ManageLabelsDeps` draws.

    /**
     * One visit to the label manager: the parsed payload, the authoritative `Label` objects, the
     * controller built around them, and the in-flight editor context.
     *
     * [labelsById] is classic's own `labelsById` (`ManageLabelsComposeActivity.kt:109-111`, itself a
     * mirror of classic `ManageLabels.allLabels`): the shared `LabelItem` carries display fields
     * only, not the `displayStyle`/`displayStyleWholeVerse` columns `BookmarkControl
     * .insertOrUpdateLabel` needs at save time, so the real entities have to be kept beside the
     * controller. Seeded broadly (it includes the Unlabeled special label) so any label reached
     * through the editor can be looked up later.
     *
     * [pendingEdit] is the one piece of state classic did NOT need a field for: its `editLabel`
     * awaited the child Activity inside a single coroutine, so the label being edited and whether it
     * was new were simply locals that survived the suspension. The editor is a destination now and
     * the result arrives later, through the channel, so the two have to be remembered across that
     * gap. One nullable field is enough because only one editor can be open at a time -- it is a
     * child destination, not a second window. A process death loses it, exactly as it lost classic's
     * suspended coroutine.
     */
    private class ManageLabelsSession(
        val data: ManageLabelsContract.ManageLabelsData,
        val labelsById: MutableMap<String, BookmarkEntities.Label>,
    ) {
        lateinit var controller: ManageLabelsController
        var pendingEdit: PendingLabelEdit? = null
    }

    /** The label whose editor is currently open, and whether it was created by this visit. */
    private class PendingLabelEdit(val label: BookmarkEntities.Label, val isNew: Boolean)

    /**
     * The memo behind [ManageLabelsDeps.controllerFor] — see that field's kdoc for WHY the label
     * manager's controller is host-held while every other controller in these graphs is built per
     * back-stack entry. Keyed on the route's payload, and dropped at every exit
     * ([deliverManageLabelsResult]), so a later entry with a different payload — or a re-entry after
     * this one has finished — is seeded afresh rather than resuming a stale working set.
     */
    private var manageLabelsSession: Pair<String, ManageLabelsSession>? = null

    /** Raised from the ManageLabels overflow; rendered host-level in [onCreate]'s `setContent`. */
    private var manageLabelsHelpOpen by mutableStateOf(false)
    private var manageLabelsExportOpen by mutableStateOf(false)

    private fun manageLabelsControllerFor(
        data: String,
        onEditLabel: (labelEditPayload: String) -> Unit,
        onResult: (ManageLabelsResult) -> Unit,
    ): ManageLabelsController {
        manageLabelsSession?.let { (key, existing) -> if (key == data) return existing.controller }

        val parsed = ManageLabelsContract.ManageLabelsData.fromJSON(data)
        val session = ManageLabelsSession(
            data = parsed,
            labelsById = bookmarkControl.assignableLabels.associateByTo(mutableMapOf()) { it.id.toString() },
        )
        val highlightId = (windowControl.activeWindowPageManager.currentPage.key as? StudyPadKey)
            ?.takeIf { parsed.mode == ManageLabelsContract.Mode.STUDYPAD }
            ?.label?.id?.toString()
        session.controller = ManageLabelsController(
            mode = ManageLabelsMapper.toMode(parsed.mode),
            service = manageLabelsService,
            scope = lifecycleScope,
            initialSelected = ManageLabelsMapper.seedSelected(parsed),
            initialAutoAssign = ManageLabelsMapper.seedAutoAssign(parsed),
            initialAutoAssignPrimary = ManageLabelsMapper.seedAutoAssignPrimary(parsed),
            initialBookmarkPrimary = ManageLabelsMapper.seedBookmarkPrimary(parsed),
            highlightLabelId = highlightId,
            // Classic launched an Intent here; the payload is built the same way and the GRAPH turns
            // it into a destination. A label that cannot be resolved navigates nowhere, exactly as
            // classic's `?: return` did.
            onEditLabel = { id -> buildLabelEditPayload(session, id)?.let(onEditLabel) },
            onSelectStudyPad = { id, entryId -> selectStudyPad(session, id, entryId, onResult) },
            onSave = { saveManageLabelsAndExit(session, onResult) },
            onReset = { resetManageLabels(session, onResult) },
        )
        manageLabelsSession = data to session
        return session.controller
    }

    /**
     * Classic `ManageLabelsComposeActivity.onEditLabel` (`:439-497`) up to the point it built the
     * Intent: everything that decides WHAT the editor opens on. Returns the `LabelEditContract
     * .LabelData` JSON the `bookmarks/labelEdit` route carries, or null when the label cannot be
     * resolved (classic's bare `return`).
     */
    private fun buildLabelEditPayload(session: ManageLabelsSession, id: String?): String? {
        val controller = session.controller
        val isNew = id == null
        val label: BookmarkEntities.Label = if (id != null) {
            session.labelsById[id] ?: bookmarkControl.labelById(IdType(id)) ?: return null
        } else {
            BookmarkEntities.Label(new = true).apply { color = manageLabelsService.randomColorArgb() }
        }
        // A new label opened from the toolbar + with no live query must arrive with a real,
        // editable, unique name, or the return path below discards it silently (round 17b).
        val suggestedName = if (isNew) {
            controller.searchText.value.trim().ifBlank {
                defaultLabelName(
                    existing = session.labelsById.values.mapTo(mutableSetOf()) { it.displayName },
                    format = getString(R.string.new_label_default_name),
                )
            }
        } else {
            null
        }

        val workspaceId = windowControl.windowRepository.id
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        val existingOverrides = if (!isNew) workspaceDao.labelOverrides(workspaceId) else emptyList()
        val existingOverride = existingOverrides.find { it.labelId == label.id }
        val workspaceOverride = existingOverride ?: WorkspaceEntities.WorkspaceLabelOverride(
            workspaceId = workspaceId,
            labelId = label.id,
        )

        val labelData = LabelEditContract.LabelData(
            isAssigning = session.data.mode == ManageLabelsContract.Mode.ASSIGN,
            label = label,
            isAutoAssign = controller.resultAutoAssign().contains(label.id.toString()),
            isAutoAssignPrimary = controller.resultAutoAssignPrimary() == label.id.toString(),
            isThisBookmarkPrimary = controller.resultBookmarkPrimary() == label.id.toString(),
            isThisBookmarkSelected = controller.resultSelected().contains(label.id.toString()),
            suggestedName = suggestedName,
            workspaceOverride = workspaceOverride,
            hasWorkspaceContext = true,
        )
        if (isNew) {
            when (session.data.mode) {
                ManageLabelsContract.Mode.ASSIGN -> {
                    labelData.isThisBookmarkSelected = true
                    labelData.isThisBookmarkPrimary = true
                }
                ManageLabelsContract.Mode.WORKSPACE -> {
                    labelData.isAutoAssignPrimary = true
                    labelData.isAutoAssign = true
                }
                else -> {}
            }
        }

        session.pendingEdit = PendingLabelEdit(label, isNew)
        return labelData.toJSON()
    }

    /**
     * Classic's `editLabel` continuation (`ManageLabelsComposeActivity.kt:499-551`), field for
     * field, with `awaitIntent`'s `ActivityResult` replaced by the channel's [NavLabelEditResult] —
     * `Cancelled` IS classic's `RESULT_CANCELED`, and `Saved` carries the same `"data"` JSON its
     * `"data"` extra did, including a delete expressed as `delete`/`deleteOrphanedBookmarks` flags
     * on the payload.
     *
     * Called from the `MANAGE_LABELS_PATTERN` arm's `LaunchedEffect` over
     * `BookmarkNavDeps.labelEditResults`. Reads the session rather than taking the controller as a
     * parameter: the reconciliation needs `labelsById` and the in-flight [PendingLabelEdit] too, and
     * those live together or not at all.
     */
    private fun applyLabelEditResult(result: NavLabelEditResult) {
        val session = manageLabelsSession?.second ?: return
        val pending = session.pendingEdit ?: return
        session.pendingEdit = null
        val label = pending.label
        val controller = session.controller

        if (result !is NavLabelEditResult.Saved) return

        val newLabelData = LabelEditContract.LabelData.fromJSON(result.data)

        // A name the user deliberately cleared is still not a label.
        if (newLabelData.label.name.isEmpty() && pending.isNew) return

        if (newLabelData.delete) {
            session.labelsById.remove(label.id.toString())
            controller.applyLabelDeleted(label.id.toString(), newLabelData.deleteOrphanedBookmarks)
        } else {
            val updatedLabel = newLabelData.label
            session.labelsById[updatedLabel.id.toString()] = updatedLabel

            // All four applied unconditionally, as classic did (`ManageLabels.kt:614-634`): the
            // three non-ASSIGN checkboxes are hidden on the editor, so their round-tripped values
            // are unchanged from the seeds and passing them is behaviourally identical.
            controller.applyLabelChanged(
                item = updatedLabel.toLabelItem(),
                selectedFlag = newLabelData.isThisBookmarkSelected,
                autoAssignFlag = newLabelData.isAutoAssign,
                bookmarkPrimaryFlag = newLabelData.isThisBookmarkPrimary,
                autoAssignPrimaryFlag = newLabelData.isAutoAssignPrimary,
            )

            // Workspace override (classic `ManageLabels.kt:637-649`).
            val returnedOverride = newLabelData.workspaceOverride
            if (returnedOverride != null) {
                val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
                if (returnedOverride.hasOverride) {
                    dao.insertOrUpdateLabelOverride(returnedOverride)
                } else {
                    dao.deleteLabelOverride(returnedOverride.workspaceId, returnedOverride.labelId)
                }
                ABEventBus.post(LabelAddedOrUpdatedEvent(updatedLabel))
                controller.refresh() // re-derive the override (Tune icon) indicator immediately
            }
        }
    }

    /**
     * Classic `onSelectStudyPad` (`:557-569`): point the active window at the StudyPad, then leave
     * the way every other exit leaves.
     */
    private fun selectStudyPad(
        session: ManageLabelsSession,
        id: String,
        firstMatchEntryId: String?,
        onResult: (ManageLabelsResult) -> Unit,
    ) {
        val label = session.labelsById[id] ?: bookmarkControl.labelById(IdType(id)) ?: return
        try {
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(
                FakeBookFactory.journalDocument,
                StudyPadKey(label, entryId = firstMatchEntryId?.let { IdType(it) }),
            )
        } catch (e: Exception) {
            Log.e(TAG_MANAGE_LABELS, "Error on attempt to show journal", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
        saveManageLabelsAndExit(session, onResult)
    }

    /**
     * Classic `saveAndExit` (`ManageLabelsComposeActivity.kt:573-637`), with `setResult`/`finish()`
     * replaced by the channel delivery — which is the whole of the change, because every line of the
     * rest is Room work.
     *
     * That includes classic's `labels_list_search_mode` write (`:576-578`), which stays HERE rather
     * than moving into the arm: every exit this screen has routes through `controller.save()` and so
     * through this function — the up-arrow, Back, and `selectStudyPad`, which ends by calling it —
     * so there is no exit an exit-time write can miss. Only the READ half is a deps lambda, because
     * only the seeding happens inside the arm's composition.
     */
    private fun saveManageLabelsAndExit(
        session: ManageLabelsSession,
        onResult: (ManageLabelsResult) -> Unit,
    ) {
        val controller = session.controller

        // Classic `ManageLabelsComposeActivity.kt:576-578` (itself classic `ManageLabels`'
        // saveFilteringSettings): STUDYPAD only, because it is the only mode with a content search.
        if (session.data.mode == ManageLabelsContract.Mode.STUDYPAD) {
            CommonUtils.settings.setInt(MANAGE_LABELS_SEARCH_MODE_KEY, controller.searchMode.value.ordinal)
        }

        val deletedIds = controller.resultDeleted()
        val orphanedIds = controller.resultDeletedWithOrphaned()
        val withoutOrphaned = deletedIds.filterNot { orphanedIds.contains(it) }.map { IdType(it) }
        val withOrphaned = orphanedIds.map { IdType(it) }
        if (withoutOrphaned.isNotEmpty()) {
            bookmarkControl.deleteLabels(withoutOrphaned, deleteOrphanedBookmarks = false)
        }
        if (withOrphaned.isNotEmpty()) {
            bookmarkControl.deleteLabels(withOrphaned, deleteOrphanedBookmarks = true)
        }

        val changedIds = controller.resultChanged()
        val toSave = changedIds.filterNot { deletedIds.contains(it) }.mapNotNull { session.labelsById[it] }

        // The list's quick favourite-toggle only flips the controller's own LabelItem copy, so
        // re-apply the controller's current favourite onto the entity before persisting.
        val currentFavourites = controller.currentLabelItems().associate { it.id to it.favourite }
        toSave.forEach { label -> currentFavourites[label.id.toString()]?.let { label.favourite = it } }

        val newLabels = toSave.filter { it.new }
        val existingLabels = toSave.filter { !it.new }

        // New-label id remap (classic `ManageLabels.kt:695-711`): a label created via the editor only
        // gets a real, DB-assigned id here, and every set/primary tracked under its temporary id must
        // follow it.
        val idRemap = mutableMapOf<String, String>()
        for (label in newLabels) {
            val oldId = label.id.toString()
            val saved = bookmarkControl.insertOrUpdateLabel(label)
            label.id = saved.id
            label.new = false
            idRemap[oldId] = saved.id.toString()
        }
        for (label in existingLabels) {
            bookmarkControl.insertOrUpdateLabel(label)
        }

        fun remapSet(ids: Set<String>) = ids.map { idRemap[it] ?: it }.toSet()
        fun remapId(id: String?) = id?.let { idRemap[it] ?: it }

        ManageLabelsMapper.applyResult(
            data = session.data,
            selected = remapSet(controller.resultSelected()),
            autoAssign = remapSet(controller.resultAutoAssign()),
            changed = remapSet(controller.resultChanged()),
            deleted = controller.resultDeleted(),
            deletedWithOrphaned = controller.resultDeletedWithOrphaned(),
            autoAssignPrimary = remapId(controller.resultAutoAssignPrimary()),
            bookmarkPrimary = remapId(controller.resultBookmarkPrimary()),
        )

        deliverManageLabelsResult(session, onResult)
    }

    /**
     * Classic `reset` (`ManageLabelsComposeActivity.kt:656-674`), unchanged: WORKSPACE clears the
     * auto-assign set IN PLACE and stays on the list, HIDELABELS flips the payload's `reset` flag and
     * leaves — the same `"data"` extra, because [ManageLabelsMapper.applyReset] sets a FIELD rather
     * than producing a second result shape. [askConfirmation] stays an `android.app.AlertDialog`:
     * converting platform dialogs is a separate, queued port goal.
     */
    private fun resetManageLabels(
        session: ManageLabelsSession,
        onResult: (ManageLabelsResult) -> Unit,
    ) {
        lifecycleScope.launch(Dispatchers.Main) {
            when (session.data.mode) {
                ManageLabelsContract.Mode.WORKSPACE -> {
                    if (askConfirmation(getString(R.string.reset_workspace_auto_assign_labels))) {
                        session.controller.clearAutoAssign()
                    }
                }
                ManageLabelsContract.Mode.HIDELABELS -> {
                    if (askConfirmation(getString(R.string.reset_hide_labels))) {
                        ManageLabelsMapper.applyReset(session.data)
                        deliverManageLabelsResult(session, onResult)
                    }
                }
                else -> throw RuntimeException("Illegal value")
            }
        }
    }

    /**
     * The ONE place the label manager leaves. Drops the memoised session first, so that a re-entry
     * (from `Bookmarks`, once that destination exists) builds a fresh controller from a fresh
     * payload rather than resuming a finished one — see [manageLabelsSession].
     */
    private fun deliverManageLabelsResult(
        session: ManageLabelsSession,
        onResult: (ManageLabelsResult) -> Unit,
    ) {
        manageLabelsSession = null
        manageLabelsHelpOpen = false
        manageLabelsExportOpen = false
        onResult(ManageLabelsResult(session.data.toJSON()))
    }

    /** Classic `importStudyPads` (`:308-313`): the InstallZip round trip, then a controller refresh. */
    private fun importStudyPads(controller: ManageLabelsController) {
        lifecycleScope.launch(Dispatchers.Main) {
            awaitIntent(ScreenLauncher.intentFor(this@NavHostComposeActivity, Screen.InstallZip))
            controller.refresh()
        }
    }

    private suspend fun askConfirmation(message: String): Boolean = suspendCoroutine { cont ->
        android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(true)
            .setOnCancelListener { cont.resume(false) }
            .setPositiveButton(R.string.yes) { _, _ -> cont.resume(true) }
            .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(false) }
            .show()
    }

    /**
     * The label's glyph for a list row, verbatim from classic (`:691-699`). The TINT is the screen's
     * decision; the DEFAULT drawable is the mode's, which is why [studyPadMode] is passed in from the
     * arm rather than derived here.
     */
    @Composable
    private fun ManageLabelIcon(name: String?, tint: Color, studyPadMode: Boolean) {
        val defaultId = if (studyPadMode) R.drawable.ic_baseline_studypads_24 else R.drawable.ic_label_24dp
        Icon(
            painter = painterResource(customIconMap[name] ?: defaultId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }

    /**
     * The New (+) icon, in both the normal bar's [ManageLabelsActions] and the search bar's
     * `searchActions` slot — see the arm's comment at that call site for why the search bar needs its
     * own copy. Verbatim from classic (`:334-343`).
     */
    @Composable
    private fun RowScope.NewLabelIcon(onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(R.drawable.ic_add_circle_outline_white_24dp),
                contentDescription = getString(R.string.new_item),
                modifier = Modifier.size(AbActionIconSize),
            )
        }
    }

    /**
     * The label manager's top-bar actions, verbatim from classic `ManageLabelsComposeActivity
     * .ManageLabelsActions` (`:345-435`) — including every comment's worth of reasoning about which
     * rows are mode-gated and which icons deliberately differ from classic's XML menu.
     *
     * Takes the whole controller rather than five callbacks: it reads `mode` and `styleTagsVisible`
     * off it as well as commanding it, and classic read them the same way (off the Activity's own
     * `controller` field). The two rows that are NOT controller commands — help and export — flip
     * host state instead, because both raise sheets that are rendered host-level; see
     * [manageLabelsHelpOpen].
     */
    @Composable
    private fun RowScope.ManageLabelsActions(controller: ManageLabelsController) {
        IconButton(onClick = controller::openSearch) {
            Icon(
                painter = painterResource(R.drawable.ic_search_24dp),
                contentDescription = getString(R.string.search),
                modifier = Modifier.size(AbActionIconSize),
            )
        }
        NewLabelIcon(onClick = { controller.newLabel() })
        val styleTagsVisible by controller.styleTagsVisible.collectAsState()
        AbOverflowMenu(contentDescription = null) { close ->
            AbMenuItem(
                text = getString(R.string.help),
                onClick = { close(); manageLabelsHelpOpen = true },
                icon = { Icon(painterResource(R.drawable.ic_help_white_24dp), contentDescription = null) },
            )
            if (controller.mode.hasReOrderButton) {
                AbMenuItem(
                    text = getString(R.string.reorder),
                    onClick = { close(); controller.reOrder() },
                    icon = { Icon(painterResource(R.drawable.ic_baseline_refresh_24), contentDescription = null) },
                )
            }
            if (controller.mode.styleTagsShown) {
                AbMenuItem(
                    text = getString(R.string.show_style_examples),
                    onClick = { close(); controller.toggleStyleTags() },
                    checkable = true,
                    checked = styleTagsVisible,
                    icon = { Icon(painterResource(R.drawable.ic_text_format_white_24dp), contentDescription = null) },
                )
            }
            if (controller.mode.hasResetButton) {
                AbMenuItem(
                    // Two different actions, two different labels (round 17b): WORKSPACE clears the
                    // auto-assign set and stays; HIDELABELS reverts to the inherited value and leaves.
                    text = getString(
                        if (controller.mode == ManageLabelsMode.WORKSPACE) {
                            R.string.clear_auto_assign_labels
                        } else {
                            R.string.reset_generic
                        },
                    ),
                    onClick = { close(); controller.reset() },
                    icon = { Icon(painterResource(R.drawable.ic_baseline_undo_24), contentDescription = null) },
                )
            }
            // Export/import StudyPads: visible in ALL modes (classic onCreateOptionsMenu parity).
            AbMenuItem(
                text = getString(R.string.export_something, getString(R.string.studypads)),
                onClick = { close(); manageLabelsExportOpen = true },
                icon = { Icon(painterResource(R.drawable.file_export), contentDescription = null) },
            )
            AbMenuItem(
                text = getString(R.string.import_items, getString(R.string.studypads)),
                onClick = { close(); importStudyPads(controller) },
                icon = { Icon(painterResource(R.drawable.ic_file_download_24dp), contentDescription = null) },
            )
        }
    }

    // --- LabelEdit host baggage ---------------------------------------------------------------
    // Ported from LabelEditComposeActivity (which a later task deletes): the Android-resource icon
    // renderer, the top-bar actions, the delete confirmations, the discard-changes dialog and the
    // export-destination chooser. Every one of them is here rather than in `BookmarkNavGraph.kt`
    // because it ends in a platform call `commonMain` has no equivalent for — `painterResource`,
    // `getString` with format arguments, or an `android.app.AlertDialog`.

    /**
     * Non-null while the "Export to where?" chooser is awaiting an answer — rendered as an
     * [AbActionSheet] in [onCreate]'s `setContent`, so [exportStudyPads] (via `BackupControl
     * .saveOrShare`) skips its own platform `AlertDialog` and awaits this one instead. Verbatim from
     * classic `LabelEditComposeActivity.kt:89-95`, including the `finally` that clears the field and
     * so closes the sheet.
     */
    private var destinationRequest: CompletableDeferred<SaveOrShare?>? by mutableStateOf(null)

    private suspend fun askDestination(): SaveOrShare? {
        val deferred = CompletableDeferred<SaveOrShare?>()
        destinationRequest = deferred
        return try { deferred.await() } finally { destinationRequest = null }
    }

    /**
     * Builds the label editor's controller for one back-stack entry, AND collapses its three
     * outcomes onto [NavLabelEditResult]'s two.
     *
     * The collapse is classic `onFinish` (`LabelEditComposeActivity.kt:234-247`) with `setResult` +
     * `finish()` replaced by `onResult`, and nothing else changed:
     * - [ControllerLabelEditResult.Save] → [NavLabelEditResult.Saved] carrying the updated payload's
     *   JSON, which is exactly what `finishWithData` put in the `"data"` extra.
     * - [ControllerLabelEditResult.Delete] → also [NavLabelEditResult.Saved], because classic's
     *   `Delete` arm ALSO ended at `finishWithData`. It is not a third result shape: it sets
     *   `delete`/`deleteOrphanedBookmarks` on the payload first and then returns the same extra, so
     *   a completed delete IS a saved `LabelData` whose own `delete` flag is set. Collapsing it any
     *   other way would change the contract `ManageLabels` reads back.
     * - [ControllerLabelEditResult.Cancel] → [NavLabelEditResult.Cancelled], classic's bare
     *   `setResult(RESULT_CANCELED)` with no `"data"` extra at all.
     *
     * It stays here rather than in the graph because every line of it needs `LabelEditMapper` and
     * `LabelEditContract.LabelData`, `:app` types that embed Room entities and cannot cross into
     * `commonMain`. [data] is parsed ONCE per controller, not per outcome: the `Delete` arm mutates
     * that instance exactly as classic mutated its `lateinit var data` field.
     */
    private fun labelEditControllerFor(
        data: String,
        onResult: (NavLabelEditResult) -> Unit,
    ): LabelEditController {
        val labelData = LabelEditContract.LabelData.fromJSON(data)
        return LabelEditController(
            LabelEditMapper.toState(labelData),
            labelEditService,
            lifecycleScope,
        ) { result ->
            when (result) {
                is ControllerLabelEditResult.Save ->
                    onResult(NavLabelEditResult.Saved(LabelEditMapper.applyToData(labelData, result.state).toJSON()))
                is ControllerLabelEditResult.Delete -> {
                    labelData.delete = true
                    labelData.deleteOrphanedBookmarks = result.deleteOrphaned
                    onResult(NavLabelEditResult.Saved(LabelEditMapper.applyToData(labelData, result.state).toJSON()))
                }
                ControllerLabelEditResult.Cancel -> onResult(NavLabelEditResult.Cancelled)
            }
        }
    }

    /**
     * Classic `shareLabel` (`LabelEditComposeActivity.kt:227-232`): applies the pending (unsaved)
     * edits onto the payload before exporting, so the exported StudyPad matches what is on screen.
     * Takes the route's [data] and the live [state] instead of reading two Activity fields — the
     * editor's payload and controller belong to a back-stack entry now, not to this host.
     */
    private fun shareLabel(data: String, state: LabelEditState) {
        val current = LabelEditMapper.applyToData(LabelEditContract.LabelData.fromJSON(data), state)
        lifecycleScope.launch {
            exportStudyPads(this@NavHostComposeActivity, current.label, chooseDestination = ::askDestination)
        }
    }

    /**
     * Renders the current custom-icon selection: [customIconMap]`[name]` or the default bookmark
     * drawable, tinted with the caller-supplied [tint] rather than deriving one here (round-9a I1 —
     * a single internal rule cannot serve both a colour-filled disc and a neutral background).
     * Verbatim from classic `LabelEditComposeActivity.kt:285-294`.
     */
    @Composable
    private fun AndroidLabelIcon(name: String?, tint: Color) {
        val drawableId = customIconMap[name] ?: R.drawable.ic_baseline_bookmark_24
        Icon(
            painter = painterResource(drawableId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }

    /**
     * The label editor's top-bar actions, verbatim from classic `LabelEditComposeActivity
     * .LabelEditActions` (`:158-183`). [onSave] and [onDelete] are the GRAPH-owned controller's, so
     * they arrive as parameters; [data] and [state] are the two halves [shareLabel] needs.
     */
    @Composable
    private fun RowScope.LabelEditActions(
        data: String,
        state: LabelEditState,
        onSave: () -> Unit,
        onDelete: () -> Unit,
    ) {
        IconButton(onClick = onSave) {
            Icon(
                painter = painterResource(R.drawable.ic_check_24dp),
                contentDescription = getString(R.string.okay),
                modifier = Modifier.size(AbActionIconSize),
            )
        }
        if (!state.isSpecialLabel) {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = getString(R.string.delete),
                    modifier = Modifier.size(AbActionIconSize),
                )
            }
        }
        AbOverflowMenu(contentDescription = null) { close ->
            AbMenuItem(
                text = getString(R.string.export),
                onClick = { close(); shareLabel(data, state) },
                icon = { Icon(painterResource(R.drawable.ic_baseline_share_24), contentDescription = null) },
            )
        }
    }

    /**
     * Classic `DeletePromptDialog` (`LabelEditComposeActivity.kt:190-224`), with the controller's
     * two calls hoisted into [onConfirm]/[onDismiss] parameters. [labelName] comes from the LIVE
     * controller state (not the route payload's name, which `LabelEditMapper.applyToData` only syncs
     * at save/delete/share time) — otherwise a name typed but not yet saved would show stale here.
     */
    @Composable
    private fun LabelEditDeletePrompt(
        prompt: DeletePrompt,
        labelName: String,
        onConfirm: (deleteOrphaned: Boolean) -> Unit,
        onDismiss: () -> Unit,
    ) {
        when (prompt) {
            is DeletePrompt.Orphaned -> ComposeAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(getString(R.string.delete_label_confirmation, labelName)) },
                text = { Text(getString(R.string.confirm_delete_orphaned_bookmarks, prompt.count)) },
                confirmButton = {
                    TextButton(onClick = { onConfirm(true) }) {
                        Text(getString(R.string.delete_label_and_bookmarks))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { onConfirm(false) }) {
                            Text(getString(R.string.delete_label_only))
                        }
                        TextButton(onClick = onDismiss) { Text(getString(R.string.cancel)) }
                    }
                },
            )
            DeletePrompt.Confirm -> ComposeAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(getString(R.string.delete_label_confirmation, labelName)) },
                confirmButton = {
                    TextButton(onClick = { onConfirm(false) }) { Text(getString(R.string.yes)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(getString(R.string.no)) }
                },
            )
        }
    }

    /**
     * Classic `requestUp`'s discard-changes confirmation (`LabelEditComposeActivity.kt:256-266`),
     * kept as the platform `android.app.AlertDialog` it was. Converting the port's remaining
     * platform dialogs to Compose is its own queued goal; doing it inside a navigation move would
     * change behaviour under cover of a refactor. The arm decides WHEN to ask and what "yes" means;
     * this only asks.
     */
    private fun confirmDiscardLabelEdits(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(R.string.discard_changes_confirmation)
            .setPositiveButton(R.string.yes) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    // --- AiConnectionSettings host baggage ----------------------------------------------------
    // Ported from the classic AiConnectionSettingsComposeActivity (deleted in nav-graph Task 10):
    // Android-resource labels/locale arrays, the reset-usage AlertDialog, and the help
    // overflow. See AiConnectionSettingsDeps' kdoc for why each stays host-side rather than
    // moving into :sharedCore/:sharedUi.

    private fun buildAiConnectionLabels() = AiConnectionLabels(
        screenTitle = getString(R.string.ai_connection_settings),
        disclaimerWarningTitle = getString(R.string.ai_disclaimer_warning_title),
        disclaimerWarningSummary = getString(R.string.ai_disclaimer_warning_summary),
        gettingStartedTitle = getString(R.string.easy_setup_title),
        gettingStartedSummary = getString(R.string.easy_setup_pref_summary),
        providersModelsCategoryTitle = getString(R.string.ai_providers_models_category),
        providersTitle = getString(R.string.ai_providers_category),
        providersSummaryNone = getString(R.string.ai_providers_summary_none),
        modelsTitle = getString(R.string.ai_models_category),
        modelsSummaryNone = getString(R.string.ai_models_summary_none),
        behaviorCategoryTitle = getString(R.string.ai_behavior_category),
        agentPermissionModeTitle = getString(R.string.prompt_permission_mode),
        toolPermissionsTitle = getString(R.string.manage_tool_permissions_title),
        toolPermissionsSummary = getString(R.string.manage_tool_permissions_summary),
        documentsTitle = getString(R.string.ai_document_filter_title),
        documentsSummary = getString(R.string.ai_document_filter_summary),
        aiLanguageTitle = getString(R.string.ai_language_title),
        commentaryMaxResponseTitle = getString(R.string.commentary_max_response_title),
        commentaryMaxResponseNoLimit = getString(R.string.commentary_max_response_no_limit),
        commentaryMaxResponseValueFormat = getString(R.string.commentary_max_response_value),
        maxIterationsTitle = getString(R.string.agent_max_iterations_title),
        maxIterationsSummary = getString(R.string.agent_max_iterations_summary),
        maxIterationsUnlimitedSuffix = getString(R.string.prompt_max_iterations_unlimited),
        askModelBeforeRunTitle = getString(R.string.ask_model_before_run_title),
        askModelBeforeRunSummary = getString(R.string.ask_model_before_run_summary),
        autoHideAgentLogTitle = getString(R.string.auto_hide_agent_log_title),
        autoHideAgentLogSummary = getString(R.string.auto_hide_agent_log_summary),
        advancedCategoryTitle = getString(R.string.ai_advanced_category),
        customAgentSystemPromptTitle = getString(R.string.custom_agent_system_prompt_title),
        customTextTransformSystemPromptTitle = getString(R.string.custom_text_transform_system_prompt_title),
        customSystemPromptDefault = getString(R.string.custom_system_prompt_default),
        customSystemPromptCustom = getString(R.string.custom_system_prompt_custom),
        usageCategoryTitle = getString(R.string.ai_usage_category),
        usageSummaryTitle = getString(R.string.llm_usage_summary_title),
        resetUsageTitle = getString(R.string.llm_reset_usage_title),
        resetUsageSummary = getString(R.string.llm_reset_usage_summary),
        rawLogHistoryTitle = getString(R.string.raw_log_history_title),
        rawLogHistorySummary = getString(R.string.raw_log_history_summary),
        rawLogRetentionTitle = getString(R.string.raw_log_retention_title),
        rawLogRetentionSummaryDisabled = getString(R.string.raw_log_retention_summary_disabled),
        rawLogRetentionSummaryDaysFormat = getString(R.string.raw_log_retention_summary_days),
        permissionModeLabels = AgentPermissionModeIds.ordered.associateWith { id ->
            when (id) {
                "ALWAYS_ASK" -> getString(R.string.permission_always_ask)
                "ASK_ONCE_PER_RUN" -> getString(R.string.permission_ask_once_per_run)
                "ALLOW_ALL" -> getString(R.string.permission_allow_all)
                "DENY_ALL" -> getString(R.string.permission_deny_all)
                else -> id
            }
        },
    )

    /**
     * Builds the language option list from the `prefs_interface_locale_*` string-arrays — mirrors
     * classic `AiConnectionSettingsActivity.setupAiLanguage`'s option set (an "app default" entry,
     * one per non-empty locale code, and a trailing [CUSTOM_LANGUAGE_TAG] sentinel).
     */
    private fun buildAiLanguageChoices(): List<SettingsItem.Choice> {
        val descriptions = resources.getStringArray(R.array.prefs_interface_locale_descriptions)
        val codes = resources.getStringArray(R.array.prefs_interface_locale_values)
        val choices = mutableListOf<SettingsItem.Choice>()
        choices.add(
            SettingsItem.Choice(
                value = "",
                label = getString(R.string.ai_language_app_default, Locale.getDefault().displayLanguage),
            ),
        )
        for (i in codes.indices) {
            val code = codes[i]
            if (code.isNotEmpty()) choices.add(SettingsItem.Choice(value = code, label = descriptions[i]))
        }
        choices.add(SettingsItem.Choice(value = CUSTOM_LANGUAGE_TAG, label = getString(R.string.ai_language_custom)))
        return choices
    }

    /**
     * Reset-vs-blank parity with classic `showCustomSystemPromptEditor`, see
     * [resolvedCustomPromptValue]'s kdoc for why the comparison must be against the RAW built-in
     * default, never [aiConnectionCustomPromptTextFor]'s result.
     */
    private fun onAiConnectionCustomPromptSave(key: String, value: String?) {
        val builtInDefault = aiConnectionBuiltInPromptTextFor(key)
        when (key) {
            "custom_agent_prompt" ->
                aiSettingsService.setCustomAgentSystemPrompt(resolvedCustomPromptValue(value, builtInDefault))
            "custom_text_transform_prompt" ->
                aiSettingsService.setCustomTextTransformationSystemPrompt(resolvedCustomPromptValue(value, builtInDefault))
        }
    }

    /** Current custom text, or the built-in default (editor prefill only). */
    private fun aiConnectionCustomPromptTextFor(key: String): String = when (key) {
        "custom_agent_prompt" -> aiSettingsService.customAgentSystemPromptText()
        "custom_text_transform_prompt" -> aiSettingsService.customTextTransformationSystemPromptText()
        else -> ""
    }

    /** Raw built-in default text for [key] (ignores any custom override). */
    private fun aiConnectionBuiltInPromptTextFor(key: String): String = when (key) {
        "custom_agent_prompt" -> aiSettingsService.builtInAgentSystemPromptText()
        "custom_text_transform_prompt" -> aiSettingsService.builtInTextTransformationSystemPromptText()
        else -> ""
    }

    /** Per-model [LlmCostTracker.reset], ported from classic's `showResetUsageConfirm`. */
    private fun showAiConnectionResetUsageConfirm() {
        AlertDialog.Builder(this)
            .setTitle(R.string.llm_reset_usage_confirm_title)
            .setMessage(R.string.llm_reset_usage_confirm_message)
            .setPositiveButton(R.string.okay) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        for (model in DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao().all()) {
                            LlmCostTracker.reset(model.id)
                        }
                    }
                    aiSettingsService.refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Help overflow (parity with classic `ai_connection_options_menu`). */
    @Composable
    private fun RowScope.AiConnectionHelpAction() {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Text("⋮", fontSize = 24.sp) // vertical ellipsis; Material icons aren't on the app-module classpath
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(getString(R.string.help)) }, onClick = {
                expanded = false
                CommonUtils.showHelpDialog(
                    activity = this@NavHostComposeActivity,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_connection_text,
                    helpPath = "ai.html#getting-started",
                )
            })
        }
    }

    // --- RawLlmLog host baggage ---------------------------------------------------------------
    // Ported from classic RawLlmLogComposeActivity (deleted in nav-graph Task 10): none of
    // this can move into commonMain (DatabaseContainer/SimpleDateFormat are Android-JVM-only; the
    // clipboard/share/AiBugReport calls are platform APIs) — see RawLlmLogDeps' kdoc.

    /** DB-mode title text, or `null` if the record is gone (mirrors classic `onCreate`'s title logic). */
    private suspend fun rawLlmLogRecordTitle(recordId: String): String? {
        val record = withContext(Dispatchers.IO) {
            DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getById(IdType(recordId))
        } ?: return null
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "${record.modelName} — ${dateFormat.format(Date(record.timestamp))}"
    }

    /** Mirrors classic `getLogText()`: DB text (as displayed, header included) or the session `format()`. */
    private fun rawLlmLogTextFor(recordId: String?, workspaceId: String?, recordText: String?): String {
        recordId?.let { return recordText ?: "" }
        val wid = workspaceId ?: return ""
        return AgentSessionManager.getSession(IdType(wid))?.rawLlmLog?.format() ?: ""
    }

    private fun copyRawLlmLog(recordId: String?, workspaceId: String?, recordText: String?) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Raw LLM Log", rawLlmLogTextFor(recordId, workspaceId, recordText)))
        Toast.makeText(this, R.string.raw_llm_log_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareRawLlmLog(recordId: String?, workspaceId: String?, recordText: String?) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, rawLlmLogTextFor(recordId, workspaceId, recordText))
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share)))
    }

    private fun reportRawLlmLogBug(recordId: String?, workspaceId: String?) {
        lifecycleScope.launch {
            if (recordId != null) {
                AiBugReport.reportAiBug(this@NavHostComposeActivity, IdType(recordId))
            } else {
                val wid = workspaceId ?: return@launch
                val log = AgentSessionManager.getSession(IdType(wid))?.rawLlmLog ?: return@launch
                AiBugReport.reportAiBugFromRawLog(this@NavHostComposeActivity, log)
            }
        }
    }

    // --- AiPrompts host baggage — the SAF (Storage Access Framework) seam ---------------------
    // Ported VERBATIM from classic AiPromptsComposeActivity (deleted in nav-graph Task 10),
    // which itself mirrors classic AiSettingsActivity's exportPrompts/importPrompts exactly: the
    // editable-vs-addon chooser, the ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT intents, and the
    // result Toasts/error dialogs. None of `awaitIntent` (this Activity's ActivityBase suspend
    // bridge to the system file picker), AlertDialog.Builder, Toast, contentResolver or
    // SharedConstants.modulesDir has a commonMain equivalent — see AiPromptsDeps' kdoc.

    private suspend fun exportPrompts() {
        try {
            val dao = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()
            val userPrompts = withContext(Dispatchers.IO) { dao.allPrompts() }

            if (userPrompts.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_prompts_to_export), Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                putExtra(Intent.EXTRA_TITLE, "ai_prompts_$timestamp.csv")
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            PromptCsvUtils.exportPromptsToCsv(outputStream, userPrompts)
                        } ?: throw IllegalArgumentException("Could not open output stream")
                    }
                    Toast.makeText(
                        this,
                        getString(R.string.prompts_csv_export_success, userPrompts.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_AI_PROMPTS, "Error exporting prompts to CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.csv_export_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importPrompts() {
        val options = arrayOf(
            getString(R.string.import_prompts_editable),
            getString(R.string.import_prompts_addon),
        )
        val installAsAddon = suspendCancellableCoroutine<Boolean?> { cont ->
            AlertDialog.Builder(this)
                .setTitle(R.string.import_prompts_csv)
                .setItems(options) { _, which -> cont.resume(which == 1) }
                .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        } ?: return

        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values"))
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    if (installAsAddon) {
                        installCsvAsAddon(uri)
                    } else {
                        importCsvAsEditable(uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_AI_PROMPTS, "Error importing prompts from CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.csv_import_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importCsvAsEditable(uri: Uri) {
        val importResult = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                PromptCsvUtils.importPromptsFromCsv(inputStream)
            } ?: throw IllegalArgumentException("Could not open input stream")
        }

        if (importResult.errors > 0) {
            val message =
                getString(R.string.csv_import_errors, importResult.created, importResult.updated, importResult.errors) +
                    "\n\n" + importResult.errorMessages.take(5).joinToString("\n") +
                    if (importResult.errorMessages.size > 5) "\n..." else ""

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_prompts_csv))
                .setMessage(message)
                .setPositiveButton(R.string.okay, null)
                .show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.csv_import_success, importResult.created, importResult.updated),
                Toast.LENGTH_SHORT
            ).show()
        }

        promptService.refresh()
    }

    private suspend fun installCsvAsAddon(uri: Uri) {
        val displayName = contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        } ?: "prompts.csv"

        withContext(Dispatchers.IO) {
            val outDir = File(SharedConstants.modulesDir, "prompts")
            outDir.mkdirs()
            val outFile = File(outDir, displayName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            addCsvPromptBook(outFile)
            AndBibleAddons.clearCaches()
        }

        PromptRepository.clearAddonCache()
        Toast.makeText(this, R.string.install_zip_successfull, Toast.LENGTH_SHORT).show()
        promptService.refresh()
    }

    // --- Reading plan host baggage ------------------------------------------------------------
    // Ported from classic DailyReadingComposeActivity / DailyReadingListComposeActivity /
    // ReadingPlanSelectorComposeActivity (all three deleted in nav-graph Task 9). Everything here
    // needs ReadingPlanControl, SpeakControl, JSword's BookName, an Android DatePickerDialog, a SAF
    // launcher or ABEventBus with :app-module event types — none of which commonMain can reach.
    // See ReadingPlanNavGraph.kt's Deps kdocs for the seam each piece arrives through.

    /**
     * The live [DailyReadingController] the graph built for the destination currently on screen.
     * Captured here (rather than created here) because the controller needs the graph's two
     * navigation edges, while [pushReadingPlanUi]/[pushReadingPlanSpeakState] need to push INTO it
     * from host code — and because navigation-compose disposes this destination's composition while
     * a child is on top, a new instance replaces the old one on every re-entry.
     *
     * What keeps the field pointing at the instance the user can see is NOT that only one
     * `DAILY_READING_PATTERN` entry exists — [onNewIntent] can push a second one while the day list
     * or selector is on top, giving `[dailyReading, dayList, dailyReading]`. It is that
     * navigation-compose composes only the VISIBLE entries: `controllerFor` runs as an entry becomes
     * visible, so the last write always comes from the entry now on top, and an entry further down
     * (whose composition is disposed, and whose controller nothing can reach) can never write again
     * until it is composed once more — at which point it IS the visible one. During a transition
     * both are briefly composed; the entering one composes second, so it wins.
     */
    private var dailyReadingController: DailyReadingController? = null
    private var readingsDto: OneDaysReadingsDto? = null
    private var dayLoaded: Int = 0
    private var planCodeLoaded: String? = null

    /** Classic's "the selector was already offered once" memory — see [offerReadingPlanSelector]. */
    private var readingPlanSelectorOffered = false

    /** What [loadReadingPlanDay] last landed on; the graph turns it into the history route. */
    private val loadedReadingDay = MutableStateFlow<LoadedReadingDay?>(null)

    /** Child -> parent channel for the day list / selector — see [ReadingPlanSelection]. */
    private val pendingReadingPlanSelection = MutableStateFlow<ReadingPlanSelection?>(null)

    /**
     * How the reading-progress destination delivers its result, in either of the two ways it can
     * be entered — see [NavResultChannel]'s own kdoc. `exitWithResult` here is exactly what
     * `finishWithChapterResult`/`finishWithMemorizeResult` did by hand before this class existed:
     * pack the byte-identical `Intent` ([NavResultIntents.forReadingProgress]), `setResult` and
     * `finish()`.
     */
    private val readingProgressResults = NavResultChannel<ReadingProgressResult> { result ->
        setResult(RESULT_OK, NavResultIntents.forReadingProgress(result))
        finish()
    }

    // ——— The bookmark cluster's three result channels ————————————————————————————————————————
    // All three were created here together, and handed to `BookmarkNavDeps` together, before their
    // destinations existed: the alternative is a deps class that every later task has to widen.
    //
    // Until Task 6 only the live ones had a real `exitWithResult`. Fix round 1, Finding 1: the first
    // version of this block also ported the not-yet-reachable exits "so the later task inherits a
    // packing that is already right" -- and the bookmark one had ALREADY lost classic's try/catch
    // (which logs and toasts `error_occurred` around `addHistoryItem`/`setResult`), with no caller
    // and no test able to notice. That is the cost of unreachable code, and it is why each packing
    // landed with its own destination and its own red-then-green test in `NavResultIntentsTest`.

    /**
     * The bookmark LIST's channel. Its exit is classic `BookmarksComposeActivity.onSelectBookmark`
     * (`BookmarksComposeActivity.kt:165-194`), itself a mirror of `Bookmarks.bookmarkSelected`. The
     * `ActivityResultKind.Bookmarks` tag belongs in the packing rather than in [BookmarkResult], for
     * the reason [NavResultIntents.forReadingProgress] carries its own: the tag names the Intent's
     * SHAPE to `MainBibleActivity`'s dispatcher, and is not part of what the destination decided.
     *
     * The half of the port that is HERE rather than in [NavResultIntents.forBookmarks] is the
     * aliasing: classic hands ONE Intent object to `HistoryManager` and then to `setResult`, and a
     * pure packing function cannot express "and the same object again". So this lambda holds the
     * single `val`, the order of the two sinks, and classic's `try`/`catch` around them.
     *
     * Only [NavResultChannel]'s exit branch can ever run for it: nothing in any graph navigates to
     * `BOOKMARKS_PATTERN`, so there is never a parent entry to publish to.
     */
    private val bookmarkResults = NavResultChannel<BookmarkResult> { result ->
        // Classic `BookmarksComposeActivity.kt:187-194`, and the ORDER and the OBJECT are both part
        // of it: ONE Intent is built, stored in the history list, and only then set as the result.
        // `HistoryManager.createHistoryItem` (`:153-155`) keeps the very object it is handed inside
        // an `IntentHistoryItem`, so building a second, structurally-equal Intent for `setResult`
        // would silently unpick the aliasing classic relies on. Hence one `val` handed to both.
        //
        // The try/catch is classic's, kept deliberately: the first, speculative version of this
        // lambda (written before this destination existed) had already lost it, with no caller and
        // no test able to notice.
        try {
            val resultIntent = NavResultIntents.forBookmarks(result)
            historyTraversal.historyManager.addHistoryItem(null, resultIntent)
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG_BOOKMARKS, "Error on bookmarkSelected", e)
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The label MANAGER's channel. Both of classic's exits — `saveAndExit`
     * (`ManageLabelsComposeActivity.kt:573-644`) and the HIDELABELS reset path (`:663-668`) — build
     * the identical `Intent().putExtra("data", data.toJSON())`, which is why [ManageLabelsResult]
     * carries just the one string and [NavResultIntents.forManageLabels] needs no branch.
     *
     * Unlike [labelEditResults] this is a bare `Intent` and an unconditional `RESULT_OK`: classic
     * has no cancel path at all (its Back press saves, `:320-326`), so there is no result-code /
     * Intent pairing to keep together — see that function's kdoc for why [forLabelEdit] differs.
     */
    private val manageLabelsResults = NavResultChannel<ManageLabelsResult> { result ->
        setResult(RESULT_OK, NavResultIntents.forManageLabels(result))
        finish()
    }

    /**
     * The label EDITOR's exit — the one channel here with a live destination. The packing itself is
     * [NavResultIntents.forLabelEdit] (classic `LabelEditComposeActivity.finishWithData`, `:249-254`,
     * and the `Cancel` arm of its `onFinish`, `:242-245`), delegated for the same reason
     * `readingProgressResults` three fields above delegates: a lambda in this class can only be
     * exercised by launching the host, and the `"data"` extra plus the OK/CANCELED split IS the
     * contract `ManageLabelsComposeActivity`'s launcher reads back. `NavResultIntentsTest` pins both
     * cases.
     *
     * The three-outcomes-to-two collapse happened earlier, in [labelEditControllerFor]; by the time
     * a result reaches here it is already one of these two.
     */
    private val labelEditResults = NavResultChannel<NavLabelEditResult> { result ->
        val activityResult = NavResultIntents.forLabelEdit(result)
        setResult(activityResult.resultCode, activityResult.data)
        finish()
    }

    /**
     * Deliberately unreachable, and the only channel in the tree whose exit throws. Every other
     * channel's exit packs an Intent for an external caller; `CustomRepositoryEditor` has none --
     * it is registered only as a child of `CustomRepositories`, is absent from
     * `ScreenLauncher.MIGRATED`, and its route is built in exactly one place (the list arm). If
     * this ever runs, someone gave the editor an external entry without giving it a result
     * contract; fail loudly instead of packing an Intent nobody defined.
     */
    private val repositoryEditorResults = NavResultChannel<RepositoryResult> {
        error("CustomRepositoryEditor is reachable only from CustomRepositories; it has no external entry")
    }

    /**
     * [id] resolves through the same [customRepositoryService] the list uses; `null` (a NEW
     * repository, plan D9) maps to a blank [RepositoryResult] -- classic's `newItem()` -- rather
     * than throwing, and an id that no longer resolves (deleted from under the editor) falls back
     * to the same blank state instead of crashing the editor arm.
     */
    private suspend fun customRepositoryEditorInitialFor(id: Long?): RepositoryResult {
        val repo = id?.let { wanted -> customRepositoryService.list().find { it.id == wanted } }
        return RepositoryResult(repository = repo)
    }

    /** Classic `CustomRepositoryEditorComposeActivity.pasteFromClipboard()`: the clipboard's
     *  primary text clip, or null when there is none. */
    private fun readCustomRepositoryClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }

    // --- MyDocumentPages host baggage ---------------------------------------------------------
    // Ported from classic MyDocumentPagesComposeActivity (which Task 9 deletes): the pages-within-
    // a-document editor also lives in the nav graph now, entered both from CurrentGeneralBookPage
    // (outside) and, once nav-graph slice 4 Task 6 lands, from MyDocuments (inside). Every Room/
    // SWORD/SAF/EventBus side effect stays host-side, exactly as it did in the classic Activity.

    private val myDocumentDao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    /**
     * The bits of the CURRENTLY open `MyDocumentPages` session the two SAF launchers below need to
     * reach: the stable load-index-Long -> Room-entity map `myDocumentPagesControllerFor` rebuilds
     * on every entry, so imports/exports can resolve the ids the controller hands them back to a
     * real row. `registerForActivityResult` must run before `STARTED`, so the launchers themselves
     * are host fields (plan D4) -- and since only one `MyDocumentPages` destination is ever composed
     * at a time (it has no child destination of its own), this single mutable map is exactly
     * classic's per-Activity-instance `entityByLong` field, moved one level up.
     */
    private var myDocumentPagesEntityByLong: Map<Long, MyDocumentPage> = emptyMap()
    private var myDocumentPagesDocumentInitials: String = ""

    /** The current session's own `addPage`, captured so [importMyDocumentPageFile] -- which runs
     *  from a host-level launcher callback, outside the arm's own closures -- can add an imported
     *  page to the right controller. Refreshed by every [myDocumentPagesControllerFor] call. */
    private var addMyDocumentPageToList: ((title: String, contentType: MyDocumentContentType, content: String) -> Unit)? = null

    /** [net.bible.sharedcore.mydocuments.MyDocumentPagesController.onExportSelected]'s pending ids,
     *  moved host-side beside [exportMyDocumentPagesTreeLauncher] -- classic's own
     *  `MyDocumentPagesComposeActivity.pendingExportIds` field. */
    private var pendingMyDocumentPagesExportIds: List<Long> = emptyList()

    /**
     * Builds the pages-within-a-document editor's controller for one destination entry. Mirror of
     * classic `MyDocumentPagesComposeActivity`'s `onCreate` + `reload` + `applyChanges` +
     * `addPageToList`, all of which needed Room and therefore could not cross into `commonMain`.
     *
     * [onImport]/[onExport]/[onExportSelected] are threaded in from the SAME lambdas
     * [MyDocumentPagesDeps] exposes at the top level (built once, alongside this factory, when the
     * deps object is assembled below) rather than re-created here, so there is exactly one
     * definition of "launch the import picker" / "export this page" / "export these pages", not two
     * copies that could drift.
     */
    private fun myDocumentPagesControllerFor(
        documentId: String,
        documentInitials: String,
        onResult: (MyDocumentPagesResult) -> Unit,
        onImport: () -> Unit,
        onExport: (id: Long) -> Unit,
        onExportSelected: (ids: List<Long>) -> Unit,
    ): MyDocumentPagesController {
        val docId = IdType(documentId)
        myDocumentPagesDocumentInitials = documentInitials
        myDocumentPagesEntityByLong = emptyMap()

        lateinit var controller: MyDocumentPagesController

        fun nextLongId(): Long = (myDocumentPagesEntityByLong.keys.maxOrNull() ?: -1L) + 1L

        /** Mirror of classic `addPageToList`: insert, assign the next stable Long id, notify the controller. */
        fun addPageToList(title: String, contentType: MyDocumentContentType, content: String) {
            val pageId = IdType()
            val page = MyDocumentPage(
                id = pageId,
                documentId = docId,
                title = title,
                pageKey = "page_$pageId",
                contentType = contentType,
                orderNumber = controller.totalCount.value,
            )
            myDocumentDao.insertPageWithContent(page, content)
            val id = nextLongId()
            myDocumentPagesEntityByLong = myDocumentPagesEntityByLong + (id to page)
            val ct = if (contentType == MyDocumentContentType.HTML) ContentType.HTML else ContentType.MARKDOWN
            controller.addPage(MyDocPageItem(id, title, ct, isAiGenerated = false))
        }
        addMyDocumentPageToList = ::addPageToList

        /** Mirror of classic `applyChanges`: delete removed pages, persist reorders/renames, always
         *  refresh the SWORD book, and post `AiDocPagesChangedEvent` for the deletions. */
        fun applyChanges(ordered: List<MyDocPageItem>, changed: Set<Long>, deleted: Set<Long>) {
            deleted.mapNotNull { myDocumentPagesEntityByLong[it] }.forEach { p ->
                myDocumentDao.pageById(p.id)?.let { myDocumentDao.deletePageWithContent(it) }
            }
            val toUpdate = ArrayList<MyDocumentPage>()
            ordered.forEachIndexed { index, item ->
                val p = myDocumentPagesEntityByLong[item.id] ?: return@forEachIndexed
                p.orderNumber = index
                p.title = item.name
                if (item.id in changed) { p.updatedAt = System.currentTimeMillis(); toUpdate.add(p) }
            }
            if (toUpdate.isNotEmpty()) myDocumentDao.updatePages(toUpdate)
            // Classic always refreshes: new pages are inserted directly to the DB in
            // addPageToList() without going through `changed`, so the SWORD book would otherwise be
            // stale.
            MyDocumentBookManager.refreshDocument(documentInitials)
            val deletedIds = deleted.mapNotNull { myDocumentPagesEntityByLong[it]?.id }
            if (deletedIds.isNotEmpty()) ABEventBus.post(AiDocPagesChangedEvent(deletedPageIds = deletedIds))
        }

        controller = MyDocumentPagesController(
            onOpenPage = { id ->
                // Mirror of classic `openPage`/`returnWithPage`: auto-save-on-leave rather than
                // classic's save-changes prompt, then refresh the SWORD book's key map (a snapshot)
                // before the caller resolves the returned pageKey against it.
                myDocumentPagesEntityByLong[id]?.let { page ->
                    if (controller.dirty.value) controller.save() else MyDocumentBookManager.refreshDocument(documentInitials)
                    onResult(MyDocumentPagesResult.Selected(documentInitials, page.pageKey))
                }
            },
            onImport = onImport,
            onExport = onExport,
            onCreatePage = { name, type ->
                val ct = if (type == ContentType.HTML) MyDocumentContentType.HTML else MyDocumentContentType.MARKDOWN
                addPageToList(name, ct, "")
            },
            onExportSelected = onExportSelected,
            onSave = { ordered, changed, deleted -> applyChanges(ordered, changed, deleted) },
        )

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { myDocumentDao.pagesForDocument(docId) }
            myDocumentPagesEntityByLong = list.mapIndexed { i, p -> i.toLong() to p }.toMap()
            controller.setPages(
                list.mapIndexed { i, p ->
                    MyDocPageItem(
                        id = i.toLong(),
                        name = p.title,
                        contentType = if (p.contentType == MyDocumentContentType.HTML) ContentType.HTML else ContentType.MARKDOWN,
                        isAiGenerated = p.sourcePromptId != null,
                    )
                },
            )
        }

        return controller
    }

    private val importMyDocumentPageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        // One page per picked file, in filename order -- the same rule the documents-side import
        // uses (MyDocumentsComposeActivity.importFromFiles sorts by filename before numbering).
        for (uri in uris.sortedBy { getMyDocumentPageFileName(it) ?: "" }) importMyDocumentPageFile(uri)
    }

    /** Import a single text file as a new page. Ported verbatim from classic
     *  `MyDocumentPagesComposeActivity.importFile` (`:326`). */
    private fun importMyDocumentPageFile(uri: Uri) {
        try {
            val fileName = getMyDocumentPageFileName(uri) ?: getString(R.string.my_document_imported_page_name)
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return

            val contentType = when {
                fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true) ->
                    MyDocumentContentType.HTML
                else -> MyDocumentContentType.MARKDOWN
            }

            val title = fileName.substringBeforeLast(".")
            addMyDocumentPageToList?.invoke(title, contentType, content)
        } catch (e: Exception) {
            Log.e(TAG_MY_DOCUMENT_PAGES, "Failed to import file", e)
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
        }
    }

    /** Export one page via `BackupControl.saveOrShare`, with the host's own destination chooser in
     *  place of `saveOrShare`'s platform `AlertDialog` -- the seam [askDestination] already built for
     *  the reading-plan export, not a dialog conversion. Ported verbatim from classic
     *  `MyDocumentPagesComposeActivity.exportPage` (`:440`). */
    private fun exportMyDocumentPage(id: Long) {
        val page = myDocumentPagesEntityByLong[id] ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val pageWithContent = myDocumentDao.pageByIdWithContent(page.id) ?: return@launch
            val ext = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
            val sanitizedTitle = page.title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "").take(50)
                .ifEmpty { getString(R.string.my_document_export_fallback_name) }
            val fileName = "$sanitizedTitle.$ext"
            val targetDir = File(SharedConstants.internalFilesDir, "export/")
            targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)
            targetFile.writeText(pageWithContent.content ?: "")
            val mimeType = if (ext == "html") "text/html" else "text/markdown"
            BackupControl.saveOrShare(
                activity = this@NavHostComposeActivity,
                file = targetFile,
                fileName = fileName,
                shareMimeType = mimeType,
                saveMimeType = mimeType,
                chooserTitle = getString(R.string.my_document_export_page),
                chooseDestination = ::askDestination,
            )
        }
    }

    private val exportMyDocumentPagesTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val ids = pendingMyDocumentPagesExportIds
            pendingMyDocumentPagesExportIds = emptyList()
            if (uri != null && ids.isNotEmpty()) exportMyDocumentPagesToFolder(ids, uri)
        }

    /** Export several pages into one chosen folder. Ported verbatim from classic
     *  `MyDocumentPagesComposeActivity.exportPagesToFolder` (`:497`). */
    private fun exportMyDocumentPagesToFolder(ids: List<Long>, treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val treeDoc = DocumentFile.fromTreeUri(this@NavHostComposeActivity, treeUri) ?: return@launch
                for ((index, id) in ids.withIndex()) {
                    val page = myDocumentPagesEntityByLong[id] ?: continue
                    val withContent = myDocumentDao.pageByIdWithContent(page.id) ?: continue
                    val ext = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
                    val mimeType = if (ext == "html") "text/html" else "text/markdown"
                    val orderPrefix = String.format("%02d", index + 1)
                    val sanitizedTitle = page.title
                        .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                        .take(50)
                        .ifEmpty { getString(R.string.my_document_export_fallback_name) }
                    val file = treeDoc.createFile(mimeType, "$orderPrefix-$sanitizedTitle.$ext") ?: continue
                    contentResolver.openOutputStream(file.uri)?.use { out ->
                        out.write((withContent.content ?: "").toByteArray(Charsets.UTF_8))
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NavHostComposeActivity, R.string.my_document_export_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG_MY_DOCUMENT_PAGES, "Failed to export pages", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NavHostComposeActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Resolve a display file name for a content Uri. Ported verbatim from classic
     *  `MyDocumentPagesComposeActivity.getFileName` (`:345`). */
    private fun getMyDocumentPageFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    /**
     * `MyDocumentPages`'s channel -- the batch's second DUAL-ENTRY destination. Entered from
     * outside (`CurrentGeneralBookPage`, today) this exits the host with the packed
     * [NavResultIntents.forMyDocumentPages] result, exactly [labelEditResults]' shape three
     * fields below; entered from inside (`MyDocuments`, once nav-graph slice 4 Task 6 lands) it
     * publishes to `pending` and pops instead.
     */
    private val myDocumentPagesResults = NavResultChannel<MyDocumentPagesResult> { result ->
        val activityResult = NavResultIntents.forMyDocumentPages(result)
        setResult(activityResult.resultCode, activityResult.data)
        finish()
    }

    private fun buildDailyReadingController(
        onChangePlan: () -> Unit,
        onChangeDay: () -> Unit,
    ): DailyReadingController = DailyReadingController(
        onToggleRead = { readingNo ->
            val status = readingPlanControl.getReadingStatus(dayLoaded)
            if (status.isRead(readingNo)) status.setUnread(readingNo) else status.setRead(readingNo)
            pushReadingPlanUi()
        },
        onRead = { readingNo ->
            val dto = readingsDto ?: return@DailyReadingController
            val key = dto.getReadingKey(readingNo)
            // read() posts AddHistoryItem synchronously, and HistoryManager then reads
            // isIntegrateWithHistoryManager + intentForHistoryList off this host — both already
            // pointing at the day on screen, because the destination set the history route when it
            // loaded. Classic's belt-and-braces `isIntegrateWithHistoryManager = true` here is NOT
            // ported: on classic it was a no-op on an already-true val, but here it would set the
            // flag independently of `historyRoute`, and a true flag with a null route falls back to
            // super.intentForHistoryList — the argument-free route, i.e. the wrong day. The two
            // halves of the seam only ever move together, through setHistoryRoute.
            readingPlanControl.read(dayLoaded, readingNo, key)
            finish()
        },
        onSpeak = { readingNo ->
            val dto = readingsDto ?: return@DailyReadingController
            readingPlanControl.speak(dayLoaded, readingNo, dto.getReadingKey(readingNo))
            pushReadingPlanUi()
        },
        onSpeakAll = {
            val dto = readingsDto ?: return@DailyReadingController
            readingPlanControl.speak(dayLoaded, dto.getReadingKeys)
            pushReadingPlanUi()
        },
        onDone = { onReadingPlanDone() },
        onPauseSpeak = { if (speakControl.isPaused) speakControl.continueAfterPause() else speakControl.pause() },
        onStopSpeak = { speakControl.stop() },
        onChangePlan = onChangePlan,
        onChangeDay = onChangeDay,
        onSetCurrentDay = { setReadingPlanCurrentDay() },
        onReset = {
            val code = planCodeLoaded
            if (code.isNullOrEmpty()) dailyReadingController?.showError()
            else { readingPlanControl.reset(code); finish() }
        },
        onSetStartDate = { showReadingPlanStartDatePicker() },
        onImportPlan = { importPlanLauncher.launch("application/zip") },
    ).also { dailyReadingController = it }

    /**
     * Classic `DailyReadingComposeActivity.onCreate`'s plan gate and its `loadDailyReading(plan,
     * day)` in one call — the graph has no other way to reach `ReadingPlanControl`. The gate runs
     * unconditionally, exactly as classic's did: by the time a plan code arrives here (from the
     * selector, or from a history re-launch) a plan IS selected, because the selector calls
     * `startReadingPlan` before reporting the code back.
     */
    private fun loadReadingPlanDay(plan: String?, day: Int?): DailyReadingLoad {
        if (!readingPlanControl.isReadingPlanSelected || !readingPlanControl.currentPlanExists) {
            return DailyReadingLoad.NO_PLAN
        }
        return try {
            plan?.let { readingPlanControl.setReadingPlan(it) }
            dayLoaded = day ?: readingPlanControl.currentPlanDay
            planCodeLoaded = readingPlanControl.currentPlanCode
            readingsDto = readingPlanControl.getDaysReading(dayLoaded)
            pushReadingPlanUi()
            loadedReadingDay.value = LoadedReadingDay(planCodeLoaded ?: "", dayLoaded)
            DailyReadingLoad.LOADED
        } catch (e: Exception) {
            Log.e(TAG_READING_PLAN, "Error showing daily readings", e)
            dailyReadingController?.showError()
            DailyReadingLoad.FAILED
        }
    }

    /**
     * Classic's two-step "no plan" behaviour, collapsed into one host-owned decision — see
     * [DailyReadingDeps.onPlanMissing]. True = the caller should offer the selector; false = the
     * selector was already declined once and this host is finishing (classic's
     * `if (!readingPlanControl.isReadingPlanSelected) finish()`).
     */
    private fun offerReadingPlanSelector(): Boolean {
        if (readingPlanSelectorOffered) {
            finish()
            return false
        }
        readingPlanSelectorOffered = true
        return true
    }

    /** Classic's `pushUi()`: the DTO + ReadingStatus snapshot, passage names formatted host-side. */
    private fun pushReadingPlanUi() {
        val dto = readingsDto ?: return
        val status = readingPlanControl.getReadingStatus(dayLoaded)
        val readings = synchronized(BookName::class.java) {
            val save = BookName.isFullBookName()
            BookName.setFullBookName(!CommonUtils.isPortrait)
            try {
                (1..dto.numReadings).map { i ->
                    ReadingItem(i, dto.getReadingKey(i).name, status.isRead(i))
                }
            } finally {
                BookName.setFullBookName(save)
            }
        }
        dailyReadingController?.setUi(
            DailyReadingUi(
                planName = dto.readingPlanInfo.planName ?: "",
                dayDesc = dto.dayDesc,
                dateString = dto.readingDateString,
                readings = readings,
                showSpeakAll = dto.numReadings > 1,
                allRead = status.isAllRead,
                isDateBasedPlan = dto.isDateBasedPlan,
            )
        )
    }

    private fun pushReadingPlanSpeakState() {
        dailyReadingController?.pushSpeakState(
            when {
                speakControl.isPaused -> SpeakState.PAUSED
                speakControl.isSpeaking -> SpeakState.SPEAKING
                else -> SpeakState.NONE
            }
        )
    }

    private fun onReadingPlanDone() {
        val dto = readingsDto ?: return
        try {
            val nextDayToShow = readingPlanControl.done(dto.readingPlanInfo, dayLoaded, false)
            if (nextDayToShow > 0) loadReadingPlanDay(planCodeLoaded, nextDayToShow) else finish()
        } catch (e: Exception) {
            Log.e(TAG_READING_PLAN, "Error when Done daily reading", e)
            dailyReadingController?.showError()
        }
    }

    private fun setReadingPlanCurrentDay() {
        val dto = readingsDto ?: return
        try {
            val planStartDate = Calendar.getInstance()
            planStartDate.add(Calendar.DATE, -(dayLoaded - 1))
            readingPlanControl.setStartDate(dto.readingPlanInfo, planStartDate.time)
            readingPlanControl.done(dto.readingPlanInfo, dayLoaded - 1, true)
            loadReadingPlanDay(planCodeLoaded, dayLoaded)
        } catch (e: Exception) {
            Log.e(TAG_READING_PLAN, "Error setting current day", e)
            dailyReadingController?.showError()
        }
    }

    /** Platform-only (an Android `DatePickerDialog`), ported verbatim from classic `:245-256`. */
    private fun showReadingPlanStartDatePicker() {
        val dto = readingsDto ?: return
        val nowTime = Calendar.getInstance()
        val planStartDate = Calendar.getInstance()
        planStartDate.time = dto.readingPlanInfo.startDate ?: nowTime.time
        val picker = DatePickerDialog(this, { _, year, month, day ->
            planStartDate.set(year, month, day)
            readingPlanControl.setStartDate(dto.readingPlanInfo, planStartDate.time)
            loadReadingPlanDay(planCodeLoaded, dayLoaded)
        }, planStartDate.get(Calendar.YEAR), planStartDate.get(Calendar.MONTH), planStartDate.get(Calendar.DAY_OF_MONTH))
        picker.datePicker.maxDate = nowTime.timeInMillis
        picker.show()
    }

    /** Classic's day-row primary line (date for a date-based plan, otherwise the day description). */
    private fun readingPlanDayPrimaryText(dto: OneDaysReadingsDto): String =
        if (dto.isDateBasedPlan && dto.readingDate != null) dto.readingDateString else dto.dayDesc

    /**
     * Classic `DailyReadingComposeActivity`'s `ABEventBus.register(this) { ... }` pair, registered
     * per DESTINATION rather than per host (see [DailyReadingDeps.subscribeEvents]): the token is a
     * fresh object per subscription, so an unsubscribe can never take another cluster's listeners
     * down with it. `recreate()` is classic's own reaction to a plan sync — it now recreates the
     * whole host, which is the documented consequence (plan D5).
     */
    private fun subscribeDailyReadingEvents(): () -> Unit {
        val token = Any()
        ABEventBus.register(token) {
            onMain<ReadingPlansUpdatedViaSyncEvent> { recreate() }
            onMain<SpeakEvent> { pushReadingPlanSpeakState() }
        }
        // Seed the fresh controller with the CURRENT speak state. Classic never needed this: its
        // Activity (and its controller) merely paused behind the selector / day list, so the state
        // pushed by the last SpeakEvent was still there on return. Here the arm's composition is
        // disposed and the controller rebuilt, so without this seed the screen would sit at
        // SpeakState.NONE — and DailyReadingScreen.kt:90 gates the pause/play and stop buttons on
        // `speakState != NONE`, so a user who changed day or plan mid-speech would lose the
        // transport controls until the next SpeakEvent happened to fire.
        pushReadingPlanSpeakState()
        return { ABEventBus.unregister(token) }
    }

    /** The day list's / selector's `onMain<ReadingPlansUpdatedViaSyncEvent> { controller.load() }`. */
    private fun subscribeReadingPlansUpdated(onReadingPlansChanged: () -> Unit): () -> Unit {
        val token = Any()
        ABEventBus.register(token) {
            onMain<ReadingPlansUpdatedViaSyncEvent> { onReadingPlansChanged() }
        }
        return { ABEventBus.unregister(token) }
    }

    // --- Search cluster host baggage -----------------------------------------------------------
    // Ported from classic SearchComposeActivity / SearchIndexComposeActivity /
    // SearchIndexProgressComposeActivity (which stay in the tree, unreachable, until Task 9).
    // Everything here is JSword, app settings or an Android Handler -- none of it can live in
    // :sharedUi's commonMain, which is why each is a lambda on SearchNavDeps.

    /** Classic SearchComposeActivity's `windowControl.activeWindowPageManager.currentPage.currentDocument`. */
    private val currentSearchDocument get() = windowControl.activeWindowPageManager.currentPage.currentDocument

    /**
     * Classic `SearchComposeActivity.onCreate`'s preamble: the `search-last-used` stamp, the
     * "nothing to search" gate (null here == classic's immediate `finish()`) and the two strings the
     * form needs. [restoredBibleBook] is the route's own `bibleBook` argument, which classic read
     * from its history-restore extra before falling back to the current book name.
     */
    private fun prepareSearchForm(restoredBibleBook: String?): SearchFormSetup? {
        Log.i(TAG_SEARCH, "Displaying Compose search view")
        CommonUtils.settings.setLong("search-last-used", System.currentTimeMillis())
        val currentDoc = currentSearchDocument ?: return null
        return SearchFormSetup(
            title = getString(R.string.search_in, currentDoc.abbreviation),
            currentBookName = restoredBibleBook ?: searchControl.currentBookName,
        )
    }

    /**
     * Classic's `onCreate` seed AND its `onResume` re-seed in one call (they read the same two
     * things): every installed Bible as `initials to abbreviation` sorted by abbreviation, and the
     * persisted selection falling back to the current document.
     */
    private fun loadSearchTranslations(): TranslationSelection {
        val bibles = SwordDocumentFacade.bibles
            .filterIsInstance<SwordBook>()
            .sortedBy { it.abbreviation }
        val saved = loadSelectedSearchTranslations()
        val fallback = currentSearchDocument?.initials?.let { listOf(it) } ?: emptyList()
        return TranslationSelection(
            available = bibles.map { it.initials to it.abbreviation },
            selected = saved.ifEmpty { fallback },
        )
    }

    /** Classic Search.loadSelectedTranslations: only initials that still resolve to a Bible. */
    private fun loadSelectedSearchTranslations(): List<String> {
        val saved = CommonUtils.settings.getString(SEARCH_SELECTED_TRANSLATIONS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        val available = SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().map { it.initials }.toSet()
        return saved.split(",").filter { it in available }
    }

    /** Classic Search.loadRecentTerms (F22): newline-separated, since a query may contain commas. */
    private fun loadRecentSearchTerms(): List<String> {
        val saved = CommonUtils.settings.getString(SEARCH_RECENT_TERMS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        return saved.split("\n").filter { it.isNotBlank() }
    }

    /** The form's controller: host-side only because all three of its lambdas reach app settings. */
    private fun buildSearchFormController(currentBookName: String) = SearchFormController(
        currentBookName = currentBookName,
        persistTranslations = { ids ->
            CommonUtils.settings.setString(SEARCH_SELECTED_TRANSLATIONS_KEY, ids.joinToString(","))
        },
        persistRecentTerms = { terms ->
            CommonUtils.settings.setString(SEARCH_RECENT_TERMS_KEY, terms.joinToString("\n"))
        },
        loadRecentTerms = { loadRecentSearchTerms() },
    )

    /**
     * Classic `SearchComposeActivity.onSubmit`'s JSword half. It returns the DECISION rather than
     * navigating: both of classic's arms were `startActivity(...); finish()`, and in the graph both
     * are `navController.navigate(...)`, which only the graph can do -- see [SearchSubmission].
     */
    private fun submitSearch(request: SearchRequest): SearchSubmission {
        if (!bibleSearchService.validateIndex(request)) {
            // The first selected translation without a usable index (classic parity).
            val firstUnindexed = request.translationIds.firstOrNull {
                SwordDocumentFacade.getDocumentByInitials(it)?.indexStatus != IndexStatus.DONE
            }
            return SearchSubmission.NeedsIndex(firstUnindexed)
        }
        return SearchSubmission.Results(
            decoratedQuery = bibleSearchService.decorate(request),
            // Section-less highlight query (classic Search.onSearch -> SEARCH_HIGHLIGHT_TEXT).
            highlightText = searchControl.highlightSearchString(
                request.query,
                request.searchType.toClassicJSwordSearchType(),
            ),
            // Classic passed the CURRENT PAGE's document here, not one of the selected translations.
            searchDocument = currentSearchDocument?.initials,
            translationIds = request.translationIds,
        )
    }

    /** :sharedCore SearchType -> classic JSword SearchType (kept consistent with BibleSearchServiceImpl). */
    private fun SearchType.toClassicJSwordSearchType(): JSwordSearchType = when (this) {
        SearchType.ALL_WORDS -> JSwordSearchType.ALL_WORDS
        SearchType.ANY_WORDS -> JSwordSearchType.ANY_WORDS
        SearchType.PHRASE -> JSwordSearchType.PHRASE
    }

    /**
     * Classic `SearchIndexComposeActivity.documentToIndex` (`:44-49`) plus its `hasIndex` read.
     * Null == classic's "nothing to index -> finish()". The resolved initials are what the graph
     * overrides the next hop's `searchDocument` with, so an argument-free prompt still hands the
     * progress destination a concrete document.
     */
    private fun resolveIndexTarget(searchDocument: String?): IndexTarget? {
        val doc =
            if (!searchDocument.isNullOrEmpty()) SwordDocumentFacade.getDocumentByInitials(searchDocument)
            else pageControl.currentPageManager.currentPage.currentDocument
        doc ?: return null
        return IndexTarget(
            documentId = doc.initials,
            documentName = doc.name,
            isRebuild = searchIndexService.hasIndex(doc.initials),
        )
    }

    /** Classic SearchIndexProgressComposeActivity's `uiHandler`. */
    private val indexProgressHandler = Handler(Looper.getMainLooper())

    /**
     * Classic's `finishedJobs: HashSet<Progress>` -- the once-per-job de-duplication, keyed on
     * JSword `Progress` identities, which is why it cannot move into the graph.
     */
    private val finishedIndexJobs = HashSet<Progress>()

    /**
     * Classic `SearchIndexProgressComposeActivity`'s `onResume`/`onPause` PAIR (`:80-96`) as one
     * call returning its own undo: the initial `refreshJobs()`, then `JobManager.addWorkListener`,
     * and the returned lambda is `JobManager.removeWorkListener`. The graph drives it from a
     * `LifecycleResumeEffect`, so the removal happens on pause AND on disposal -- leaking the
     * listener would leak the destination it closes over.
     */
    private fun observeIndexJobs(
        onJobs: (List<ProgressJob>) -> Unit,
        onJobFinished: () -> Unit,
    ): () -> Unit {
        onJobs(indexJobSnapshot())
        val listener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) = onIndexWorkEvent(ev, onJobs, onJobFinished)
            override fun workStateChanged(ev: WorkEvent) = onIndexWorkEvent(ev, onJobs, onJobFinished)
        }
        JobManager.addWorkListener(listener)
        return { JobManager.removeWorkListener(listener) }
    }

    private fun onIndexWorkEvent(
        ev: WorkEvent,
        onJobs: (List<ProgressJob>) -> Unit,
        onJobFinished: () -> Unit,
    ) {
        indexProgressHandler.post {
            onJobs(indexJobSnapshot())
            val job = ev.job
            if (job.isFinished && finishedIndexJobs.add(job)) onJobFinished()
        }
    }

    /** Classic's `refreshJobs()` snapshot. */
    private fun indexJobSnapshot(): List<ProgressJob> {
        val snapshot = ArrayList<ProgressJob>()
        val it = JobManager.iterator()
        while (it.hasNext()) {
            val job = it.next()
            snapshot.add(
                ProgressJob(
                    id = System.identityHashCode(job).toString(),
                    label = job.jobName,
                    percent = job.work,
                    indeterminate = job.work == 0,
                )
            )
        }
        return snapshot
    }

    /**
     * `PROGRESS_STATUS_PATTERN`'s `observeJobs`: classic `ProgressStatusComposeActivity`'s
     * `onResume`/`onPause` PAIR (`:82-98`) as one call, reusing [indexJobSnapshot] -- the snapshot
     * it builds is generic over JSword's `JobManager`, not index-specific. Unlike
     * [observeIndexJobs], there is no `onJobFinished` callback and no `finishedIndexJobs`
     * de-duplication: [net.bible.sharedui.download.nav.ProgressStatusDeps] has no "indexing
     * finished" routing to drive, only a job-list refresh.
     */
    private fun observeProgressStatusJobs(onJobs: (List<ProgressJob>) -> Unit): () -> Unit {
        onJobs(indexJobSnapshot())
        val listener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) {
                indexProgressHandler.post { onJobs(indexJobSnapshot()) }
            }
            override fun workStateChanged(ev: WorkEvent) {
                indexProgressHandler.post { onJobs(indexJobSnapshot()) }
            }
        }
        JobManager.addWorkListener(listener)
        return { JobManager.removeWorkListener(listener) }
    }

    /** Classic's `isAllJobsFinished`. */
    private val isAllIndexJobsFinished: Boolean
        get() {
            val it = JobManager.iterator()
            while (it.hasNext()) if (!it.next().isFinished) return false
            return true
        }

    /**
     * Classic `jobFinished`'s wait (`:132-137`) -- "give the document up to 12 secs to reload: the
     * Progress declares itself finished before the index status has been changed" -- and nothing
     * else. It is BLOCKING (six rounds of `CommonUtils.pause(2)`), so it runs on [Dispatchers.IO]
     * and the graph awaits it: a composition must never block. What it returns is only what the
     * routing decision needs; the decision itself stays in the graph.
     */
    private suspend fun awaitIndexed(documentId: String?): IndexOutcome = withContext(Dispatchers.IO) {
        val document = SwordDocumentFacade.getDocumentByInitials(documentId)
        var attempts = 0
        while ((document == null || IndexStatus.DONE != document.indexStatus) && attempts++ < 6) {
            pause(2)
        }
        when {
            IndexStatus.DONE == document?.indexStatus -> {
                Log.i(TAG_SEARCH_INDEX, "Index created")
                if (document.isEpub) IndexOutcome.INDEXED_EPUB else IndexOutcome.INDEXED
            }
            isAllIndexJobsFinished -> {
                Log.e(TAG_SEARCH_INDEX, "Index finished but document's index is invalid")
                IndexOutcome.FAILED
            }
            else -> IndexOutcome.STILL_RUNNING
        }
    }

    // --- Search RESULTS host baggage (classic SearchResultsComposeActivity) -----------------------

    /** Classic's `SearchResultsController(bibleSearchService, lifecycleScope, searchResultsCache)`. */
    private fun buildSearchResultsController(): SearchResultsController {
        Log.i(TAG_SEARCH_RESULTS, "Displaying Compose search results view")
        return SearchResultsController(bibleSearchService, lifecycleScope, searchResultsCache)
    }

    /**
     * The DECIDING half of classic `SearchResultsComposeActivity.kt:90-95`'s ref short circuit:
     * does this search text parse as a plain scripture reference? Pure — `LinkControl.resolveRef`
     * reads `WindowControl.defaultBibleDoc` (a getter over the active window's current Bible) and
     * calls `SwordContentFacade.resolveRef`, and neither mutates anything — which is what lets the
     * graph ask it during composition, as classic asked it before `setContent`.
     */
    private fun isScriptureReference(searchText: String): Boolean =
        linkControl.resolveRef(searchText) != null

    /**
     * The ACTING half: open the reference in the reading view and undo the one history item that
     * opening it pushes.
     *
     * **Why ONE pop, where classic did two.** Classic popped `{this results screen's item, the
     * launcher's item}`, and in the graph the second of those does not exist:
     *
     *  - `ActivityBase.startActivity` (`:198-203`) posts `AddHistoryItem` only when the `open val`
     *    `integrateWithHistoryManager` is true, and this host deliberately does not override it
     *    (see [setHistoryRoute]'s kdoc for why it drives the `var` instead), so it is permanently
     *    false. And the in-graph `Search form -> results` hop is a `navigate`, not a
     *    `startActivity`, so there is nothing to gate anyway: **no launcher item is ever pushed for
     *    these screens.** Classic's second pop would therefore eat the entry BELOW — which is the
     *    `KeyHistoryItem` `MainBibleActivity` (whose `integrateWithHistoryManager` IS true,
     *    `:373`) pushed for the user's reading position when it launched the search. Every
     *    "the search text was actually a reference" jump would silently lose the way back.
     *  - The one item that IS pushed comes from this very call: `tryToOpenRef` ->
     *    `showLink(forceOpenHere = true)` -> `setCurrentDocumentAndKey` -> `setKey` ->
     *    `ABEventBus.post(AddHistoryItem)` (`CurrentPageBase.kt:112`) ->
     *    `HistoryManager.createHistoryItem`, which for this host builds an `IntentHistoryItem` from
     *    `isIntegrateWithHistoryManager` + [intentForHistoryList]. The graph publishes this
     *    destination's route immediately before calling this, precisely so that item exists and
     *    names the results screen — so exactly one pop removes exactly what classic's first pop
     *    removed, and the reading position below it survives.
     *
     * The `canGoBack()` guard is not decoration: `HistoryManager.popHistoryItem` is a bare
     * `Stack.pop()` (`:145-147`), which throws `EmptyStackException` on an empty stack — and the
     * stack it pops is the ACTIVE window's, which `showLink` can have changed under us when the
     * user has "open links in a new window" configured (`WindowMode.WINDOW_MODE_NEW` ->
     * `addNewWindow`). Classic had the same hazard, twice over.
     */
    private fun openScriptureReference(searchText: String) {
        if (!linkControl.tryToOpenRef(searchText)) return
        if (historyTraversal.historyManager.canGoBack()) {
            historyTraversal.historyManager.popHistoryItem()
        }
    }

    /** Classic's error path (`:126-136`): a transient toast, then out — never a dialog. */
    private fun showSearchError() {
        Toast.makeText(this, R.string.error_executing_search, Toast.LENGTH_SHORT).show()
    }

    /**
     * Classic `resolveBook` (`:166-168`): the row's own translation, else the first of the current
     * selection. [selectedTranslations] is passed in because the graph owns that state now (it is
     * the results controller's live selection), where classic kept a mutable field.
     */
    private fun resolveSearchResultBook(
        translationId: String?,
        selectedTranslations: List<String>,
    ): SwordBook? =
        (translationId?.let { SwordDocumentFacade.getDocumentByInitials(it) }
            ?: selectedTranslations.firstOrNull()?.let { SwordDocumentFacade.getDocumentByInitials(it) }) as? SwordBook

    /**
     * Classic `onSelect` (`:171-183`), unchanged apart from the dropped `intent.putExtra(
     * LIST_POSITION, ...)`: the list position now lives in the destination's own `rememberSaveable`,
     * so there is no intent left to write it onto. The `MainBibleActivity` start with
     * `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` stays exactly as it was — the reading
     * view is not part of this nav graph.
     */
    private fun openSearchResult(
        referenceName: String,
        translationId: String?,
        selectedTranslations: List<String>,
    ) {
        val book = resolveSearchResultBook(translationId, selectedTranslations) ?: return
        try {
            val key = book.getKey(referenceName)
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
            startActivity(
                Intent(this, MainBibleActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        } catch (e: Exception) {
            Log.e(TAG_SEARCH_RESULTS, "Could not resolve key '$referenceName' in ${book.initials}", e)
        }
    }

    /** Classic `openResultsInAWindow` (`:208-224`) minus its `finish()`, which is the graph's pop. */
    private fun openSearchResultsInAWindow(rows: List<SwordResultRow>, selectedTranslations: List<String>) {
        val list = BookAndKeyList()
        for (row in rows) {
            for (match in row.matches) {
                val book = resolveSearchResultBook(match.translationId, selectedTranslations) ?: continue
                val key = try {
                    book.getKey(row.referenceName)
                } catch (e: Exception) {
                    Log.e(TAG_SEARCH_RESULTS, "openResultsInAWindow: bad key '${row.referenceName}' in ${book.initials}", e)
                    continue
                }
                list.addAll(BookAndKey(key, book))
            }
        }
        linkControl.showLink(FakeBookFactory.multiDocument, list)
    }

    // --- EPUB search host baggage (classic EpubSearch/EpubSearchResultsComposeActivity) -----------

    /**
     * Classic `EpubSearchComposeActivity.onCreate`'s preamble. Null is the one deliberate
     * difference: classic wrote `currentPage.currentDocument!!` and would have thrown.
     */
    private fun prepareEpubSearchForm(): EpubSearchSetup? {
        Log.i(TAG_EPUB_SEARCH, "Displaying Compose EPUB search view")
        CommonUtils.settings.setLong("search-last-used", System.currentTimeMillis())
        val doc = pageControl.currentPageManager.currentPage.currentDocument
        if (doc == null) {
            Log.w(TAG_EPUB_SEARCH, "No current document to search in; leaving the EPUB search form")
            return null
        }
        return EpubSearchSetup(
            title = getString(R.string.search_in, doc.abbreviation),
            documentId = doc.initials,
        )
    }

    /** The persisted word-mode, through the shared classic wire format (`EpubSearchModeWire.kt`). */
    private fun loadEpubSearchMode(): EpubSearchMode =
        epubSearchModeFromClassicName(CommonUtils.settings.getString(EPUB_SEARCH_TYPE_KEY))

    private fun saveEpubSearchMode(mode: EpubSearchMode) {
        CommonUtils.settings.setString(EPUB_SEARCH_TYPE_KEY, mode.toClassicSearchTypeName())
    }

    /**
     * Classic `EpubSearch.help()` (`:115-132`), verbatim: an FTS5 query-syntax **platform
     * `AlertDialog`** whose message is an HTML span with a live link (hence the
     * `LinkMovementMethod`). It stays a platform dialog deliberately — the separately specified
     * platform-dialog-removal phase owns converting it, and converting it here would move a
     * `:sharedUi` golden.
     */
    private fun showEpubSearchHelp() {
        val ftsLink = "https://www.sqlite.org/fts5.html#full_text_query_syntax"
        val link = """<a href="$ftsLink">${getString(R.string.help_fts5)}</a>"""
        val span = htmlToSpan(
            """
            ${getString(R.string.help_search_epub)}<br><br>
            ${getString(R.string.help_search_details, link)}
            """.trimIndent()
        )
        val d = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            // Classic read the ACTIVITY's `title`, which for EpubSearchComposeActivity was its
            // manifest label android:label="@string/search" (AndroidManifest.xml:140) -- it never
            // called setTitle. This host's window title is whatever the CURRENT destination set
            // (here "Search in <abbrev>"), which is both a different string and shared mutable
            // state across six destinations, so the label is named explicitly instead.
            .setTitle(R.string.search)
            .setIcon(R.drawable.ic_logo)
            .setMessage(span)
            .create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * Classic `EpubSearchResultsComposeActivity.onCreate`'s document resolution and its two guards
     * (`:205-227`) as one call. Null == "not installed, or not an epub" (classic's `Log.e` +
     * `finish()`); the `!!` on the current Bible's document is a `?: return null` for the same
     * reason [prepareEpubSearchForm]'s is.
     */
    private fun resolveEpubSearchTarget(searchDocument: String?): EpubSearchTarget? {
        // Classic `EpubSearchResultsComposeActivity.onCreate`'s FIRST line (`:63`) — the one classic
        // log line slices 3/5/6 left behind (whole-branch review M7). It belongs here, at the first
        // host call the EPUB-results arm makes, for the same reason its two siblings sit where they
        // do: [prepareEpubSearchForm] opens with the EPUB search form's line, and
        // [buildSearchResultsController] with the SWORD results' one.
        Log.i(TAG_EPUB_SEARCH_RESULTS, "Displaying Compose EPUB search results view")
        val docId =
            if (searchDocument.isNullOrEmpty())
                windowControl.activeWindowPageManager.currentBible.currentDocument?.initials
                    ?: run {
                        Log.e(TAG_EPUB_SEARCH_RESULTS, "No searchDocument and no current Bible document; aborting")
                        return null
                    }
            else searchDocument
        val doc = Books.installed().getBook(docId)
        if (doc == null || !doc.isEpub) {
            Log.e(TAG_EPUB_SEARCH_RESULTS, "Document ${doc?.name} is not an epub; aborting")
            return null
        }
        return if (!epubSearchService.isIndexed(docId)) EpubSearchTarget.NeedsIndex(docId)
        else EpubSearchTarget.Ready(documentId = docId, documentAbbreviation = doc.abbreviation)
    }

    /** The EPUB results controller, with classic's `onSelect` (`:280-291`) already bound to [documentId]. */
    private fun buildEpubSearchResultsController(documentId: String) = EpubSearchResultsController(
        lifecycleScope,
        epubSearchService,
        onSelect = { keyId, ordinal -> openEpubSearchResult(documentId, keyId, ordinal) },
    )

    /** Classic `EpubSearchResults.onSelect`: re-resolve the hit's key, then open the reading view. */
    private fun openEpubSearchResult(documentId: String, keyId: String, ordinal: Int) {
        val book = Books.installed().getBook(documentId) ?: return
        try {
            val key = epubKeyFor(book, documentId, keyId, ordinal)
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
            startActivity(
                Intent(this, MainBibleActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        } catch (e: Exception) {
            Log.e(TAG_EPUB_SEARCH_RESULTS, "Could not resolve key '$keyId' in $documentId", e)
        }
    }

    /**
     * Classic's results title INCLUDING its "+" overflow affordance (`:253-257`): the service caps
     * at `MAX_SEARCH_RESULTS + 1`, so a size over the cap is detectable and shown as "5000+".
     * Host-side because `SearchControl.MAX_SEARCH_RESULTS` is an `:app` constant.
     */
    private fun epubSearchResultsTitle(resultCount: Int, documentAbbreviation: String): String {
        val resultAmount =
            if (resultCount > SearchControl.MAX_SEARCH_RESULTS) "${SearchControl.MAX_SEARCH_RESULTS}+"
            else resultCount.toString()
        return getString(R.string.search_with_results2, resultAmount, documentAbbreviation)
    }

    /** The SAF seam for plan import, ported verbatim from classic `:268-271`. */
    private val importPlanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        installZipLauncher.launch(
            ScreenLauncher.intentFor(this, Screen.InstallZip).apply {
                action = Intent.ACTION_VIEW
                data = uri
            }
        )
    }

    private val installZipLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // parity with classic: imported plan is not auto-loaded (InstallZip does not yet return the code)
    }

    // --- Settings cluster host baggage (slice 6) ------------------------------------------------
    // Ported from classic SettingsComposeActivity / SyncSettingsComposeActivity (both deleted in
    // nav-graph Task 9): Android-resource label bundles, the two platform AlertDialogs, the
    // recreate() parity rule, and the three navigation rows that have no destination in the graph.
    // See SettingsNavGraph's AppSettingsDeps kdoc for why each stays host-side.

    /**
     * Classic's `maybeRecreate` (`SettingsComposeActivity.kt:148-150`). The four keys' effects are
     * read only at Activity-creation time — the locale through `attachBaseContext`, the three theme
     * values once per composition — so nothing observes them reactively and a recreate is what makes
     * the change visible at once.
     *
     * **Plan D5, a recorded behaviour change:** on this host `recreate()` recreates EVERY
     * destination and rebuilds the whole back stack, not just the settings screen that asked for
     * it. The behaviour is kept deliberately (it is what makes a locale or theme change take
     * effect); Task 10 records it.
     */
    private fun maybeRecreateForSettingsKey(key: String) {
        if (key in RECREATE_ON_CHANGE_KEYS) recreate()
    }

    /**
     * Classic's reset confirmation (`SettingsComposeActivity.kt:245-254`), a PLATFORM AlertDialog.
     * It stays one: the separately specified platform-dialog-removal phase owns this dialog and the
     * discrete-help one below, and converting either here would move a Roborazzi golden.
     */
    private fun confirmResetSettings() {
        AlertDialog.Builder(this)
            .setMessage(R.string.reset_app_prefs)
            .setCancelable(true)
            .setPositiveButton(R.string.yes) { _, _ -> resetSettings() }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .show()
    }

    /** Classic `:264-268` verbatim, recreate() included — see [maybeRecreateForSettingsKey]. */
    private fun resetSettings() {
        SettingsReset.performReset()
        appSettingsService.refresh()
        recreate()
    }

    /**
     * Classic's persecution-help dialog (`SettingsComposeActivity.kt:189-216`), unchanged: a
     * platform AlertDialog whose HTML body carries a wiki link that needs a `LinkMovementMethod` on
     * the message TextView. Kept platform for the reason given on [confirmResetSettings].
     */
    private fun showDiscreteHelpDialog() {
        val linkUrl = "https://github.com/AndBible/and-bible/wiki/Discrete-build"
        val linkText = "<a href=\"$linkUrl\">$linkUrl</a>"

        val dPar1 = getString(R.string.discrete_mode_info_par1)
        val dPar2 = getString(R.string.discrete_mode_info_par2)
        val dLink = getString(R.string.discrete_mode_link, linkText)

        val calcPar1 = getString(R.string.calculator_par1)
        val calcPar2 = getString(R.string.calculator_par2)
        val calcPar3 = getString(R.string.calculator_par3)

        val dText = "$dPar1<br><br>$dPar2<br><br>$dLink<br><br>"
        val calcText = "$calcPar1$calcPar2<br><br>$calcPar3<br><br>$dLink"
        val htmlMessage = if (!BuildVariant.Appearance.isDiscrete) dText else calcText

        val spanned = htmlToSpan(htmlMessage)

        val d = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.prefs_persecution_cat))
            setMessage(spanned)
            setPositiveButton(R.string.okay, null)
            setCancelable(true)
        }.create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * Classic's `global_text_display_settings` row (`SettingsComposeActivity.kt:179-187`). Still an
     * Activity launch rather than a graph navigation: `Screen.TextDisplaySettings` is not migrated,
     * and it is handed a `settingsBundle` extra. GLOBAL scope comes from the ABSENT
     * `EXTRA_SCOPE_LEVEL` extra, i.e. that screen's own `scopeFromIntent` fallthrough.
     */
    private fun openGlobalTextDisplaySettings() {
        val settingsBundle = SettingsBundle(
            level = SettingsLevel.GLOBAL,
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )
        val intent = ScreenLauncher.intentFor(this, Screen.TextDisplaySettings)
        intent.putExtra("settingsBundle", settingsBundle.toJson())
        startActivity(intent)
    }

    /** Classic's `open_links` row (`:218-227`) — the Android app-links system screen, gated to S+. */
    private fun openLinksSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                Uri.parse("package:$packageName"),
            )
            startActivity(intent)
        }
    }

    /**
     * Classic's `crash_app` row (`:229-241`), beta-gated, including its fresh
     * lifecycle-INDEPENDENT [CoroutineScope]: the delayed crash must still fire if the user leaves
     * the settings screen within the ten seconds. That is now a destination change rather than an
     * Activity finish, which if anything makes the point stronger.
     */
    private fun crashApp() {
        CoroutineScope(Dispatchers.Main).launch {
            ABEventBus.post(BibleApplication.ErrorNotificationEvent("Crashing app in 10 seconds!"))
            delay(10000)
            throw RuntimeException("Crash app!")
        }
    }

    private fun buildAppSettingsLabels() = AppSettingsLabels(
        screenTitle = getString(R.string.settings),
        fontSizePercentFormat = getString(R.string.pref_font_size_multiplier_percent_format),

        dictionariesCat = getString(R.string.prefs_dictionaries_cat),
        behaviorCat = getString(R.string.prefs_behavior_customization_cat),
        displayCat = getString(R.string.prefs_display_customization_cat),
        einkCat = getString(R.string.prefs_eink_settings_cat),
        persecutionCat = getString(R.string.prefs_persecution_cat),
        featuresCat = getString(R.string.prefs_features_cat),
        advancedCat = getString(R.string.prefs_advanced_settings_cat),

        strongsGreekDictionaryTitle = getString(R.string.choose_strongs_greek_dictionary_title),
        strongsGreekDictionarySummary = getString(R.string.choose_strongs_greek_dictionary_summary),
        strongsHebrewDictionaryTitle = getString(R.string.choose_strongs_hebrew_dictionary_title),
        strongsHebrewDictionarySummary = getString(R.string.choose_strongs_hebrew_dictionary_summary),
        robinsonGreekMorphologyTitle = getString(R.string.choose_strongs_greek_morphology_title),
        robinsonGreekMorphologySummary = getString(R.string.choose_strongs_greek_morphology_summary),
        disabledWordLookupDictionariesTitle = getString(R.string.choose_word_lookup_dictionary_title),
        disabledWordLookupDictionariesSummary = getString(R.string.choose_word_lookup_dictionary_summary),

        navigateToVerseTitle = getString(R.string.prefs_navigate_to_verse_title),
        navigateToVerseSummary = getString(R.string.prefs_navigate_to_verse_summary),
        openLinksInSpecialWindowTitle = getString(R.string.prefs_open_links_in_special_window_title),
        openLinksInSpecialWindowSummary = getString(R.string.prefs_open_links_in_special_window_summary),
        screenKeepOnTitle = getString(R.string.prefs_screen_keep_on_title),
        screenKeepOnSummary = getString(R.string.prefs_screen_keep_on_summary),
        doubleTapToFullscreenTitle = getString(R.string.prefs_double_tap_to_fullscreen_title),
        doubleTapToFullscreenSummary = getString(R.string.prefs_double_tap_to_fullscreen_summary),
        autoFullscreenTitle = getString(R.string.auto_fullscreen),
        autoFullscreenSummary = getString(R.string.auto_fullscreen_summary),
        toolbarButtonActionsTitle = getString(R.string.prefs_toolbar_button_action_title),
        toolbarButtonActionsSummary = getString(R.string.prefs_toolbar_button_action_summary),
        bibleViewSwipeModeTitle = getString(R.string.prefs_bible_view_swipe_mode_title),
        bibleViewSwipeModeSummary = getString(R.string.prefs_bible_view_swipe_mode_summary),
        disableTwoStepBookmarkingTitle = getString(R.string.prefs_disable_two_step_bookmarking_title),
        disableTwoStepBookmarkingSummary = getString(R.string.prefs_disable_two_step_bookmarking_summary),
        volumeKeysScrollTitle = getString(R.string.prefs_volume_keys_scroll_title),
        volumeKeysScrollSummary = getString(R.string.prefs_volume_keys_scroll_summary),
        nightModeTitle = getString(R.string.prefs_night_mode_title),
        nightModeSummary = getString(R.string.prefs_night_mode_summary),

        localeTitle = getString(R.string.prefs_interface_locale_title),
        localeSummary = getString(R.string.prefs_interface_locale_summary),
        disableClickToEditTitle = getString(R.string.prefs_disable_click_to_edit_title),
        disableClickToEditSummary = getString(R.string.prefs_disable_click_to_edit_summary),
        notesContentTypeTitle = getString(R.string.prefs_notes_content_type_title),
        notesContentTypeSummary = getString(R.string.prefs_notes_content_type_summary),
        fontSizeMultiplierTitle = getString(R.string.pref_font_size_multiplier_title),
        hideStatusBarTitle = getString(R.string.prefs_hide_status_bar_title),
        hideStatusBarSummary = getString(R.string.prefs_hide_status_bar_summary),
        fullScreenHideButtonsTitle = getString(R.string.full_screen_hide_buttons_pref_title),
        fullScreenHideButtonsSummary = getString(R.string.full_screen_hide_buttons_pref_summary),
        hideWindowButtonsTitle = getString(R.string.hide_window_buttons_title),
        hideWindowButtonsSummary = getString(R.string.hide_window_buttons_summary),
        hideBibleReferenceOverlayTitle = getString(R.string.hide_bible_reference_overlay_title),
        hideBibleReferenceOverlaySummary = getString(R.string.hide_bible_reference_overlay_summary),
        showActiveWindowIndicatorTitle = getString(R.string.active_window_indicator_title),
        showActiveWindowIndicatorSummary = getString(R.string.active_window_indicator_summary),
        disableBibleBookmarkModalButtonsTitle = getString(R.string.prefs_in_window_bible_bookmark_modal_buttons_title),
        disableBibleBookmarkModalButtonsSummary = getString(R.string.prefs_in_window_bookmark_modal_buttons_description),
        disableGenBookmarkModalButtonsTitle = getString(R.string.prefs_in_window_gen_bookmark_modal_buttons_title),
        disableGenBookmarkModalButtonsSummary = getString(R.string.prefs_in_window_bookmark_modal_buttons_description),

        displayColorModeTitle = getString(R.string.prefs_display_color_mode_title),
        displayColorModeSummary = getString(R.string.prefs_display_color_mode_summary),
        einkModeTitle = getString(R.string.prefs_eink_display_title),
        einkModeSummary = getString(R.string.prefs_eink_display_summary),
        disableAnimationsTitle = getString(R.string.prefs_disable_animations_title),
        disableAnimationsSummary = getString(R.string.prefs_disable_animations_summary),

        discreteHelpTitle = getString(R.string.prefs_persecuted_help),
        discreteHelpSummary = getString(R.string.prefs_persecuted_summary),
        discreteModeTitle = getString(R.string.prefs_discrete_mode),
        discreteModeSummary = getString(R.string.prefs_discrete_mode_desc),
        showCalculatorTitle = getString(R.string.prefs_show_calculator),
        calculatorPinTitle = getString(R.string.prefs_calculator_pin),
        calculatorPinSummary = getString(R.string.prefs_calculator_pin_desc),

        experimentalFeaturesTitle = getString(R.string.prefs_experimental_features_title),
        experimentalFeaturesSummary = getString(R.string.prefs_experimental_features_summary),
        enableBluetoothTitle = getString(R.string.prefs_enable_bluetooth_title),
        enableBluetoothSummary = getString(R.string.prefs_enable_bluetooth_summary),
        requestSdcardPermissionTitle = getString(R.string.prefs_request_sdcard_permission_title),
        requestSdcardPermissionSummary = getString(R.string.prefs_request_sdcard_permission_summary),
        showErrorboxTitle = getString(R.string.prefs_show_error_box_title),
        showErrorboxSummary = getString(R.string.prefs_show_error_box_summary),
        openLinksTitle = getString(R.string.open_bible_links_title),
        openLinksSummary = getString(R.string.open_bible_links_summary),
        crashAppTitle = getString(R.string.crash_app),
        crashAppSummary = getString(R.string.crash_app_summary),

        syncShortcutTitle = getString(R.string.cloud_sync_title),
        syncShortcutSummary = getString(R.string.sync_settings_shortcut_summary),
        aiShortcutTitle = getString(R.string.ai_settings),
        aiShortcutSummary = getString(R.string.ai_settings_shortcut_summary),
        readingProgressShortcutTitle = getString(R.string.reading_progress_settings),
        readingProgressShortcutSummary = getString(R.string.reading_progress_settings_summary),
        textDisplayShortcutTitle = getString(R.string.global_text_display_settings_title),
        textDisplayShortcutSummary = getString(R.string.global_text_display_settings_summary),
    )

    private fun buildSyncSettingsLabels() = SyncSettingsLabels(
        screenTitle = getString(R.string.cloud_sync_title),
        generalCat = getString(R.string.sync_general_settings),
        syncCat = getString(R.string.synchronization_categories),
        documentSyncCat = getString(R.string.document_sync_category_title),
        adapterTitle = getString(R.string.sync_adapter),
        resetTitle = getString(R.string.reset_sync),
        resetSummary = getString(R.string.prefs_reset_sync_summary),
        cloudInfoTitle = getString(R.string.cloud_info),
        serverUrlTitle = getString(R.string.auth_server_uri),
        usernameTitle = getString(R.string.auth_username),
        passwordTitle = getString(R.string.auth_password),
        folderPathTitle = getString(R.string.auth_folder_path),
        folderPathSummary = getString(R.string.auth_folder_path_summary),
        bookmarksTitle = getString(R.string.bookmarks),
        workspacesTitle = getString(R.string.help_workspaces_title),
        myDocumentsTitle = getString(R.string.my_documents_title),
        aiSettingsTitle = getString(R.string.ai_settings_sync_title),
        progressTitle = getString(R.string.progress_sync_title),
        documentsTitle = getString(R.string.document_sync_title),
        documentsSummary = getString(R.string.document_sync_contents),
        autoDownloadTitle = getString(R.string.document_sync_auto_download_title),
        autoDownloadSummary = getString(R.string.document_sync_auto_download_summary),
        autoUploadTitle = getString(R.string.document_sync_auto_upload_title),
        autoUploadSummary = getString(R.string.document_sync_auto_upload_summary),
        autoDeleteTitle = getString(R.string.document_sync_auto_delete_title),
        autoDeleteSummary = getString(R.string.document_sync_auto_delete_summary),
        wifiOnlyTitle = getString(R.string.document_sync_wifi_only_title),
        wifiOnlySummary = getString(R.string.document_sync_wifi_only_summary),
        manageTitle = getString(R.string.document_sync_manage_title),
        manageSummary = getString(R.string.document_sync_manage_summary),
        resetConfirmMessage = getString(R.string.sync_confirmation),
        invalidUrlMessage = getString(R.string.invalid_url_message),
        documentsEnableDialogTitle = getString(R.string.document_sync_enable_dialog_title),
    )

    // --- Reading-progress host baggage (slice 6, Task 8) ---------------------------------------
    // Ported from classic ReadingProgressComposeActivity / ReadingProgressSettingsComposeActivity
    // (both deleted in nav-graph Task 9): the two RESULT intents that leave this batch, the
    // Android-settings-backed tab persistence, the suspending read-history loaders and their
    // resource-formatted rows, and the help dialog. See ReadingProgressDeps' kdoc.

    /**
     * The reading-progress screen's controller, built AT MOST ONCE per host instance and kept alive
     * across the destination's composition being disposed — see [readingProgressControllerFor].
     * Null until a reading-progress destination first composes, which is what keeps it lazy: the
     * deps literal (assembled on every launch of a host serving four clusters) holds only a lambda
     * and constructs nothing.
     */
    private var readingProgressController: ReadingProgressController? = null

    /**
     * Where the three read-history loaders deliver their answer: the read-history dialog state of
     * whichever COMPOSITION of the reading-progress arm is currently live. Re-pointed on every
     * [readingProgressControllerFor] call, because a surviving controller outlives the composition
     * that first supplied one — the previous arm's `historyDialog` setter writes into a disposed
     * composition's state, where nothing would ever render it.
     */
    private var readingProgressHistorySink: ((ReadHistoryRequest) -> Unit)? = null

    /**
     * The raw [NavRoutes.ARG_TAB] value that was last APPLIED to [readingProgressController], so a
     * re-entry can tell "the route names a tab I have not acted on yet" from "the same route
     * argument the surviving controller was already built with". Set on construction and on every
     * subsequent application; stays null for a route that named no tab.
     *
     * See [readingProgressControllerFor]'s re-entry paragraph — this field IS the fix for the
     * whole-branch review's I1.
     */
    private var readingProgressAppliedTabArg: Int? = null

    /**
     * [ReadingProgressDeps.controllerFor] — classic `ReadingProgressComposeActivity.kt:86-99`,
     * lambda for lambda.
     *
     * **Why the instance is CACHED on the host rather than rebuilt per back-stack entry (Task 8 fix
     * round 1).** The arm calls this from a `remember`, and navigation-compose DISPOSES a covered
     * entry's composition — the very fact Task 7's [appSettingsService] kdoc records. This screen
     * covers itself: its overflow opens reading-progress settings, now a destination in the same
     * graph. With a per-entry controller, coming back from that child rebuilt the controller and
     * lost every piece of state the model holds but nothing persists — the open book's
     * chapter-detail panel (`model.chapterDetail`), the memorize tab's chapter detail, and the
     * "show more" paging counts (`passagesShown`/`targetsShown`). Classic lost none of it: its
     * Activity merely PAUSED behind the settings Activity and its `by lazy` controller field lived
     * until `finish()`.
     *
     * Caching restores exactly that lifetime rather than approximating it. Reading progress is only
     * ever a START destination (nothing in any graph navigates TO
     * [NavRoutes.READING_PROGRESS_PATTERN]; `MenuCommandHandler` and `BibleJavascriptInterface`
     * both launch the host fresh with `startActivityForResult`, and `singleTop` cannot collapse
     * onto a host that is not already on top), so "one per host instance" and "one per back-stack
     * entry" are the SAME object here — while a host recreate or process death rebuilds it, which
     * is what classic's Activity did too.
     *
     * **The re-entry tab policy: an explicit tab is applied ONCE, when it is NEW** (whole-branch
     * review I1). The arm calls this from a `remember`, and that `remember` re-runs on every
     * re-composition of the entry — including the one caused by the reading-progress-SETTINGS child
     * popping, which is the very disposal this caching exists to survive. The route argument is
     * unchanged across that pop, so a re-entry that re-applied `tabArg` unconditionally overwrote
     * whatever tab the USER had since selected: open on Memorize via
     * `BibleJavascriptInterface.openReadingProgress(1)`, switch to Reading, open the settings child,
     * press Back, and the screen snapped back to Memorize — with `onSelectTab` having already
     * persisted "Reading", so the shown and persisted tabs then disagreed. [readingProgressAppliedTabArg]
     * gates it: an explicit tab wins only when it DIFFERS from the one last applied, which is exactly
     * the `onNewIntent` case the line was written for (a fresh delivery naming a different tab) and
     * never the pop case. The neighbouring cluster reaches the same conclusion from the other end —
     * `ReadingPlanNavGraph`'s daily-reading `LaunchedEffect` deliberately reloads the LAST LOADED day
     * rather than the entry's own arguments, for this same reason.
     *
     * Residual, stated rather than hidden: a re-delivery naming the SAME tab the controller was
     * built with is not re-applied either. That is currently unreachable — the only caller naming a
     * tab is `BibleJavascriptInterface.openReadingProgress`, which launches from `MainBibleActivity`
     * via `startActivityForResult`, so the host is never already on top and always gets a fresh
     * instance (and hence a fresh controller) — and if it ever becomes reachable, "keep the tab the
     * user is on" is the better of the two answers anyway.
     *
     * This is deliberately NOT the reading-plan/search per-entry `controllerFor` shape despite the
     * name: those clusters' destinations genuinely want fresh state per entry. It is also not a
     * `by lazy` field, because the initial tab is a ROUTE argument a `by lazy` cannot receive; a
     * nullable var built on first call is the same "construct at most once, and only on demand".
     */
    private fun readingProgressControllerFor(
        tabArg: Int?,
        onShowHistory: (ReadHistoryRequest) -> Unit,
        onResult: (ReadingProgressResult) -> Unit,
    ): ReadingProgressController {
        readingProgressHistorySink = onShowHistory

        readingProgressController?.let { existing ->
            // Re-entry. Keep the controller — that is the whole point — and apply an explicit tab
            // ONLY when it is one this controller has not already been given, i.e. a genuinely NEW
            // route argument. See the kdoc above for why an unconditional re-apply was a bug.
            if (tabArg != null && tabArg != readingProgressAppliedTabArg) {
                readingProgressAppliedTabArg = tabArg
                existing.selectTab(readingProgressInitialTab(tabArg))
            }
            return existing
        }

        // The three history loaders need the cycle the user is CURRENTLY VIEWING, not
        // service.currentCycle(): the screen's prev/next-cycle arrows move the model to an older
        // cycle and classic read `controller.model.value.cycle` off its own `by lazy` field. The
        // callbacks only ever run after construction returns, so a lateinit self-reference is the
        // faithful equivalent of that field.
        // Unlike `onShowHistory` (repointed every call, through `readingProgressHistorySink`),
        // `onResult` is captured into the controller ONLY HERE, on first construction, and lives
        // for the controller's whole host-cached lifetime with no re-pointing. That is safe only
        // because today's `onResult` closes over STABLE references — the arm's `NavHostController`
        // and the host's `readingProgressResults` channel field, both of which outlive any single
        // composition. It must stay that way: a future `onResult` that closed over per-composition
        // LOCAL state (a `remember`ed value, say) would keep reading a stale snapshot from the
        // FIRST composition across every re-entry, exactly the bug the history sink exists to avoid.
        lateinit var controller: ReadingProgressController
        controller = ReadingProgressController(
            service = readingProgressService,
            scope = lifecycleScope,
            initialTab = readingProgressInitialTab(tabArg),
            onNavigateToChapter = { bookId, chapter -> onResult(ReadingProgressResult.Chapter(bookId, chapter)) },
            // Through the sink, never through this call's `onShowHistory`: the controller outlives
            // the composition that supplied that lambda.
            onShowDayHistory = { day ->
                showDayHistory(day, controller.model.value.cycle, ::emitReadingProgressHistory)
            },
            onShowBookHistory = { bookId ->
                showBookHistory(bookId, controller.model.value.cycle, ::emitReadingProgressHistory)
            },
            onShowChapterHistory = { bookId, chapter ->
                showChapterHistory(bookId, chapter, controller.model.value.cycle, ::emitReadingProgressHistory)
            },
            initialOverviewActive = CommonUtils.settings.getBoolean("reading_progress_mem_overview", true),
            onNavigateToMemorize = { startOrdinal, endOrdinal -> onResult(ReadingProgressResult.Memorize(startOrdinal, endOrdinal)) },
            persistOverview = { CommonUtils.settings.setBoolean("reading_progress_mem_overview", it) },
        )
        readingProgressController = controller
        readingProgressAppliedTabArg = tabArg
        return controller
    }

    private fun emitReadingProgressHistory(request: ReadHistoryRequest) {
        readingProgressHistorySink?.invoke(request)
    }

    /**
     * Classic `ReadingProgressComposeActivity.kt:76-82`, expressed against a nullable route
     * argument instead of an Intent extra. `intent.getIntExtra(EXTRA_TAB, settings.getInt(PREF, 0))`
     * returns the PERSISTED tab when the extra is absent — it is the extra's default value, not a
     * fallback to 0 — so an absent [NavRoutes.ARG_TAB] must resolve the same way. The elvis below is
     * that same expression: the persisted read happens only when [tabArg] is null, and 0 is merely
     * the persisted setting's own default.
     */
    private fun readingProgressInitialTab(tabArg: Int?): ReadingTab {
        val tab = tabArg ?: CommonUtils.settings.getInt(PREF_READING_PROGRESS_LAST_TAB, 0)
        return if (tab == 1) ReadingTab.MEMORIZE else ReadingTab.READING
    }

    /** Classic's `persistTab` (`:264-266`). */
    private fun persistReadingProgressTab(tab: ReadingTab) {
        CommonUtils.settings.setInt(PREF_READING_PROGRESS_LAST_TAB, if (tab == ReadingTab.MEMORIZE) 1 else 0)
    }

    // finishWithChapterResult / finishWithMemorizeResult used to live here — classic
    // `ReadingProgressComposeActivity.navigateToChapter`/`navigateToMemorize` (`:190-196`,
    // `:200-208`), byte for byte. Both exits now go through [readingProgressResults]
    // ([NavResultChannel]), whose `exitWithResult` lambda calls
    // [NavResultIntents.forReadingProgress] to build the same `Intent` these two functions built by
    // hand; `MainBibleActivity.kt:2930-2958` still dispatches on
    // `extras.getString(ActivityResultKind.EXTRA)`, unchanged.

    // The three read-history loaders, classic `:212-239`, unchanged except that each now hands its
    // answer to the ARM's dialog state through `emit` instead of writing an Activity field. They
    // stay host-side because every line of formatting is an Android resource or a
    // ReadingProgressServiceImpl helper.

    private fun showDayHistory(dayTimestamp: Long, cycle: Int, emit: (ReadHistoryRequest) -> Unit) {
        lifecycleScope.launch {
            val entries = readingProgressService.readHistoryForDay(dayTimestamp, cycle)
            emit(
                ReadHistoryRequest(
                    title = getString(
                        R.string.reading_progress_history_for,
                        readingProgressService.dayTitle(dayTimestamp),
                    ),
                    rows = entries.map {
                        val date = readingProgressService.formatEntryDate(it.readAt)
                        val time = readingProgressService.formatEntryTime(it.readAt)
                        val version = it.bookInitials.ifEmpty {
                            getString(R.string.reading_progress_history_version_unknown)
                        }
                        ReadHistoryRow(id = it.id, primary = "$date $time", secondary = version)
                    },
                ),
            )
        }
    }

    private fun showBookHistory(bookId: String, cycle: Int, emit: (ReadHistoryRequest) -> Unit) {
        lifecycleScope.launch {
            val entries = readingProgressService.readHistoryForBook(bookId, cycle)
            emit(
                ReadHistoryRequest(
                    title = getString(
                        R.string.reading_progress_history_for,
                        readingProgressService.bookLongName(bookId),
                    ),
                    rows = entries.map { it.toReadHistoryRow() },
                ),
            )
        }
    }

    private fun showChapterHistory(
        bookId: String,
        chapter: Int,
        cycle: Int,
        emit: (ReadHistoryRequest) -> Unit,
    ) {
        lifecycleScope.launch {
            val entries = readingProgressService.readHistoryForChapter(bookId, chapter, cycle)
            emit(
                ReadHistoryRequest(
                    title = getString(
                        R.string.reading_progress_history_for,
                        "${readingProgressService.bookShortName(bookId)} $chapter",
                    ),
                    rows = entries.map { it.toReadHistoryRow() },
                ),
            )
        }
    }

    /** Classic's `ReadHistoryEntry.toRow()` (`:241-247`). */
    private fun ReadHistoryEntry.toReadHistoryRow(): ReadHistoryRow {
        val chapterRef = "${readingProgressService.bookShortName(bookId)} $chapter"
        val time = readingProgressService.formatEntryTime(readAt)
        val date = readingProgressService.formatEntryDate(readAt)
        val version = bookInitials.ifEmpty { getString(R.string.reading_progress_history_version_unknown) }
        return ReadHistoryRow(id = id, primary = "$chapterRef · $time", secondary = "$date · $version")
    }

    /** Classic's overflow help item (`:255-262`), a platform dialog left platform. */
    private fun showReadingProgressHelp() {
        CommonUtils.showHelpDialog(
            activity = this,
            titleResId = R.string.help,
            messageResId = R.string.help_reading_progress_text,
            helpPath = "reading_progress.html",
        )
    }

    /** Classic `ReadingProgressSettingsComposeActivity.buildLabels()` (`:71-85`), verbatim. */
    private fun buildReadingProgressSettingsLabels() = ReadingProgressSettingsLabels(
        screenTitle = getString(R.string.reading_progress_settings),
        autoMarkMemorizedTitle = getString(R.string.memorize_auto_mark),
        autoMarkMemorizedSummary = getString(R.string.memorize_auto_mark_summary),
        memorizeTypeFullWordsTitle = getString(R.string.memorize_type_full_words),
        memorizeTypeFullWordsSummary = getString(R.string.memorize_type_full_words_summary),
        memorizeWordVisibilityTitle = getString(R.string.memorize_word_visibility),
        memorizeWordVisibilitySummary = getString(R.string.memorize_word_visibility_summary),
        memorizeErrorHeatmapTitle = getString(R.string.memorize_error_heatmap),
        memorizeErrorHeatmapSummary = getString(R.string.memorize_error_heatmap_summary),
        memorizeScrambleHideUsedTitle = getString(R.string.memorize_scramble_hide_used),
        memorizeScrambleHideUsedSummary = getString(R.string.memorize_scramble_hide_used_summary),
        memorizeIncludeReferenceTitle = getString(R.string.memorize_include_reference),
        memorizeIncludeReferenceSummary = getString(R.string.memorize_include_reference_summary),
    )

    companion object {
        /** Classic `ReadingProgressComposeActivity.kt:54`'s file-private constant. */
        private const val PREF_READING_PROGRESS_LAST_TAB = "reading_progress_last_tab"

        private const val TAG_AI_PROMPTS = "AiPromptsCompose"
        private const val TAG_READING_PLAN = "DailyReadingNavHost"
        private const val TAG_SEARCH = "SearchCompose"
        private const val TAG_SEARCH_INDEX = "SearchIndexProgCompose"
        private const val TAG_SEARCH_RESULTS = "SearchResultsCompose"
        private const val TAG_EPUB_SEARCH = "EpubSearchCompose"
        private const val TAG_EPUB_SEARCH_RESULTS = "EpubSearchResultsCompose"
        private const val TAG_MANAGE_LABELS = "ManageLabelsNavHost"
        private const val TAG_BOOKMARKS = "BookmarksNavHost"
        private const val TAG_MY_DOCUMENT_PAGES = "MyDocPagesNavHost"

        /**
         * Classic `ManageLabels.kt`'s own key, unchanged so a user's persisted StudyPad
         * content-search mode survives the migration (`ManageLabelsComposeActivity.kt:154`, `:577`).
         */
        private const val MANAGE_LABELS_SEARCH_MODE_KEY = "labels_list_search_mode"

        /** Classic Search's settings keys, unchanged so a user's saved state survives the migration. */
        private const val SEARCH_SELECTED_TRANSLATIONS_KEY = "search_selected_translations"
        private const val SEARCH_RECENT_TERMS_KEY = "search_recent_terms"

        /** Host-wide concerns (onNewIntent routing), as opposed to one cluster's baggage. */
        private const val TAG_NAV_HOST = "NavHostCompose"

        const val EXTRA_ROUTE: String = "nav_route"

        /** Sentinel identifying the "Custom…" entry in the AI-language picker (mirrors classic). */
        private const val CUSTOM_LANGUAGE_TAG = "\u0000custom"

        /**
         * Classic `SettingsComposeActivity.kt:67-72`'s key set, moved here with the screen: a write
         * to any of these forces a recreate — see [maybeRecreateForSettingsKey].
         */
        private val RECREATE_ON_CHANGE_KEYS = setOf(
            "locale_pref",
            "night_mode_pref3",
            "display_color_mode",
            "discrete_mode",
        )

        fun intentFor(context: Context, route: String): Intent =
            Intent(context, NavHostComposeActivity::class.java).putExtra(EXTRA_ROUTE, route)
    }
}

private fun Tool.toToolVd() = ToolVd(
    id = agentTool.name,
    displayName = ToolRegistry.getDisplayName(this),
    description = description,
    requiresPermission = requiresPermission,
    categoryId = category.name,
)
