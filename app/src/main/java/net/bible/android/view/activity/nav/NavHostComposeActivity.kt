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

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
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
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.serializer
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
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.document.canDelete
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.download.LanguageGrouping
import net.bible.android.control.download.repoIdentity
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
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.ai.resolvedCustomPromptValue
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.DocumentConfiguration
import net.bible.android.view.activity.base.PseudoBook
import net.bible.android.view.activity.base.installedDocument
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.android.view.activity.bookmark.LabelEditContract
import net.bible.android.view.activity.bookmark.LabelEditMapper
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.bookmark.customIconMap
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.bookmark.toLabelItem
import net.bible.android.view.activity.download.BadDocumentAction
import net.bible.android.view.activity.download.DownloadProgressBridge
import net.bible.android.view.activity.download.RowDownloadStatus
import net.bible.android.view.activity.download.isBadDocument
import net.bible.android.view.activity.download.isInstalled
import net.bible.android.view.activity.download.isRecommended
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.search.EPUB_SEARCH_TYPE_KEY
import net.bible.android.view.activity.search.epubKeyFor
import net.bible.android.view.activity.search.epubSearchModeFromClassicName
import net.bible.android.view.activity.search.toClassicSearchTypeName
import net.bible.android.view.activity.settings.AppSettingsServiceImpl
import net.bible.android.view.activity.settings.BackgroundThumbnailResolver
import net.bible.android.view.activity.settings.SettingsReset
import net.bible.android.view.activity.settings.SyncSettingsServiceImpl
import net.bible.android.view.activity.settings.TextDisplaySettingsRouteEntry
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.android.view.activity.settings.buildBackgroundImageChooserLabels
import net.bible.android.view.activity.settings.buildColorSettingsLabels
import net.bible.android.view.activity.settings.buildTextDisplayControllerLabels
import net.bible.android.view.activity.settings.buildTextDisplayScreenLabels
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.android.view.activity.cloud.CloudSyncProgressBridge
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncService
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.documents.SyncPlan
import net.bible.sharedcore.cloud.CloudDocAction
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.CloudDocumentsController
import net.bible.service.common.CommonUtils.pause
import net.bible.service.common.displayName
import net.bible.service.common.htmlToSpan
import net.bible.service.common.labelsAndBookmarksPlaylist
import net.bible.service.common.studyPadsVideo
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.DownloadManager
import net.bible.service.download.GenericFileDownloader
import net.bible.service.download.RepoFactory
import net.bible.service.download.isPseudoBook
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
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.nav.TextSettingsResult
import net.bible.sharedcore.nav.WorkspaceResult
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedcore.mydocuments.MyDocumentPagesController
import net.bible.sharedcore.mydocuments.MyDocumentsController
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.LabelEditResult as NavLabelEditResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.progress.ReadHistoryEntry
import net.bible.sharedcore.progress.ReadingProgressController
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedcore.reading.ReadingViewHostCallbacks
import net.bible.sharedcore.reading.ReadingViewKey
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
import net.bible.sharedcore.settings.ColorSettingsController
import net.bible.sharedcore.settings.ReadingProgressSettingsController
import net.bible.sharedcore.settings.ReadingProgressSettingsLabels
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedcore.settings.SyncSettingsLabels
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.workspaces.WorkspaceSelectorController
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.NavSessionMemo
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
import net.bible.sharedui.download.nav.CloudDocumentsDeps
import net.bible.sharedui.download.nav.CustomRepositoriesDeps
import net.bible.sharedui.docCategoryOf
import net.bible.sharedui.download.nav.CustomRepositoryEditorDeps
import net.bible.sharedui.download.nav.DownloadNavDeps
import net.bible.sharedui.download.nav.DownloadDeps
import net.bible.sharedui.download.nav.ProgressStatusDeps
import net.bible.sharedui.download.nav.downloadNavGraph
import net.bible.sharedui.mydocuments.nav.MyDocumentPagesDeps
import net.bible.sharedui.mydocuments.nav.MyDocumentsDeps
import net.bible.sharedui.mydocuments.nav.MyDocumentsNavDeps
import net.bible.sharedui.mydocuments.nav.myDocumentsNavGraph
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbActionSheet
import net.bible.sharedui.components.AbActionSheetRow
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMultiSelectSheet
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.reading.nav.ReadingNavDeps
import net.bible.sharedui.reading.nav.readingNavGraph
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
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsDeps
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsNavState
import net.bible.sharedui.workspaces.nav.TextDisplaySettingsSession
import net.bible.sharedui.workspaces.nav.WorkspaceNavDeps
import net.bible.sharedui.workspaces.nav.WorkspaceSelectorDeps
import net.bible.sharedui.workspaces.nav.workspaceNavGraph
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.search.SearchType as JSwordSearchType
import org.crosswire.jsword.versification.BookName
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.android.view.activity.navigation.genbookmap.keyChooserKeys
import net.bible.android.view.activity.navigation.buildGridStep
import net.bible.android.view.activity.navigation.initialGridOptions
import net.bible.android.view.activity.navigation.persistGridOptions
import net.bible.android.view.activity.navigation.pickGridBook
import net.bible.android.view.activity.navigation.pickGridChapter
import net.bible.service.download.hideFromSelector
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade.readOsisFragment
import net.bible.service.sword.nameWithoutDocument
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.navigation.ChooseDictionaryWordController
import net.bible.sharedcore.navigation.ChooseGeneralBookKeyController
import net.bible.sharedcore.navigation.ChooseMapKeyController
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedcore.navigation.anySelectedDeletable
import net.bible.sharedui.navigation.nav.ChooseDictionaryWordDeps
import net.bible.sharedui.navigation.nav.ChooseDocumentDeps
import net.bible.sharedui.navigation.nav.ChooseGeneralBookKeyDeps
import net.bible.sharedui.navigation.nav.ChooseMapKeyDeps
import net.bible.sharedui.navigation.nav.ChooserNavDeps
import net.bible.sharedui.navigation.nav.GridChoosePassageDeps
import net.bible.sharedui.navigation.nav.chooserNavGraph
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.jdom2.Element
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

    /** The Download destination's two JSword controls, classic `DownloadComposeActivity.kt:135-136`. */
    private val downloadControl: DownloadControl by inject()
    private val documentControl: DocumentControl by inject()

    /** The passage grid's versification/book-list source, classic
     *  `GridChoosePassageComposeActivity.kt:43`. `documentControl`/`downloadControl` above are the
     *  document chooser's own two, shared with Download. */
    private val navigationControl: NavigationControl by inject()

    /** The workspace cluster's two Koin services, classic `WorkspaceSelectorComposeActivity.kt:56`
     *  and `TextDisplaySettingsComposeActivity.kt:102`. The text-display one is the CONCRETE impl,
     *  not the `TextDisplaySettingsService` interface: the HIDELABELS bridge needs
     *  `currentHideLabelsIds`, which is not part of the portable interface. */
    private val workspaceService: WorkspaceService by inject()
    private val sharedTextDisplaySettingsService: TextDisplaySettingsServiceImpl by inject()

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
            // nav-graph slice 4 Task 8: CloudDocuments is now a destination in THIS graph, so an
            // in-graph hop keeps a settings->cloud hop inside the host rather than starting a
            // second one. `navController` is only null in the sliver before the graph's first
            // composition; this callback cannot fire before then (nothing has rendered yet to call
            // it from), so the `?.let` is a defensive no-op, never a live path.
            onOpenCloudDocuments = { navController?.let { navigateToRoute(it, NavRoutes.cloudDocuments()) } },
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

    // ————————————————————————————————————————————————————————————————————————————————————————
    // The two per-destination `ActivityBase` callback families (design §4.1, nav-graph slice 7
    // Task 6). A key event and a screen-on broadcast arrive HERE, at the one host Activity, and the
    // screen that wants them is a destination — so the reading destination publishes its handlers
    // in [ReadingViewHostCallbacks] while it is composed and this host consults them. See that
    // object's kdoc for why the seam exists at all; the LOGIC below is the classic
    // `MainBibleActivity` code, ported, because every line of it is Android or `:app`.
    //
    // `MainBibleActivity` keeps its own copies of all of this until Task 13 deletes the Activity:
    // it is still the launcher, so deleting them here would take volume-key scrolling and the
    // screen-on refresh away from the live app for the rest of the batch. That is the phase's
    // standing "nothing is deleted before Task 13" rule, not an oversight.
    // ————————————————————————————————————————————————————————————————————————————————————————

    /**
     * OFF exactly while a reading view is on screen, ON otherwise — the inversion of classic
     * `MainBibleActivity.enableGenericVolumeScroll = false` (`:372`). The reading view opts OUT of
     * `ActivityBase`'s generic `VolumeButtonScroll.findScrollableView(android.R.id.content)` and
     * scrolls its own `BibleView` instead; every other classic Activity took the base default, and
     * so does every other destination of this host. Derived from the published handlers rather than
     * tracked separately, so the flag and the handler cannot disagree about which screen is up.
     */
    override val enableGenericVolumeScroll: Boolean
        get() = ReadingViewHostCallbacks.current == null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handlers = ReadingViewHostCallbacks.current
        if (handlers != null) {
            val key = readingViewKeyFor(keyCode, event)
            // A `false` from the destination means "not mine" — classic's gates (volume_keys_scroll
            // off, speaking, music playing) reached `super.onKeyDown` the same way, which with
            // `enableGenericVolumeScroll` false above is AppCompat's own handling.
            if (key != null && handlers.onKey(key)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Which of the reading view's three keys this event is, or `null` for "not the reading view's".
     * BACK counts only from an EXTERNAL KEYBOARD, exactly as classic
     * `MainBibleActivity.onKeyDown` (`:2952`) tested it: the on-screen/system back belongs to the
     * back dispatcher, and claiming it here would make back dead.
     */
    private fun readingViewKeyFor(keyCode: Int, event: KeyEvent): ReadingViewKey? = when {
        keyCode == KeyEvent.KEYCODE_VOLUME_UP -> ReadingViewKey.VolumeUp
        keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> ReadingViewKey.VolumeDown
        keyCode == KeyEvent.KEYCODE_BACK && isExternalKeyboard(event) -> ReadingViewKey.ExternalKeyboardBack
        else -> null
    }

    /** Classic's `InputDevice.getDevice(event.deviceId)?.isExternal` + `SOURCE_KEYBOARD` pair. */
    private fun isExternalKeyboard(event: KeyEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_KEYBOARD) == 0) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            InputDevice.getDevice(event.deviceId)?.isExternal ?: false
        } else {
            false
        }
    }

    /**
     * Classic `MainBibleActivity.onKeyDown`'s body (`:2934-2958`), as the reading destination's
     * handler. The gates are classic's, in classic's order, and the scroll goes to the ACTIVE
     * window's `BibleView` — `windowControl.activeWindow.bibleView`, which is the same object
     * classic reached and is null only before that window has ever been built.
     */
    private fun readingViewKeyPressed(key: ReadingViewKey): Boolean = when (key) {
        ReadingViewKey.VolumeUp, ReadingViewKey.VolumeDown -> {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager?
            val volumeKeysScroll = CommonUtils.settings.getBoolean("volume_keys_scroll", true)
            if (!speakControl.isSpeaking && audioManager?.isMusicActive != true && volumeKeysScroll) {
                val bibleView = windowControl.activeWindow.bibleView
                if (key == ReadingViewKey.VolumeDown) {
                    bibleView?.volumeDownPressed() ?: false
                } else {
                    bibleView?.volumeUpPressed() ?: false
                }
            } else {
                false
            }
        }
        // Classic closed the drawer and returned true unconditionally. The close half is NOT
        // portable yet: classic wrote `binding.drawerLayout` (the XML Task 11 removes) and then
        // `composeCloseDrawerIfOpen()`, which is `ComposeReadingViewHost`'s drawer state — the same
        // object [ReadingNavDeps.content] cannot reach from here. It belongs with the content slot
        // and lands with it; until then this reproduces classic's return value only.
        ReadingViewKey.ExternalKeyboardBack -> true
    }

    /**
     * Classic `MainBibleActivity.onScreenTurnedOn` (`:2693`), including its night-mode refresh.
     *
     * Classic forwarded to `documentViewManager.documentView`, which is
     * `bibleViewFactory.getOrCreateBibleView(windowControl.activeWindow)` — the active window's
     * `BibleView`, CREATED if it does not exist. This asks the window for the one it already has:
     * building a WebView in order to tell it the screen came on would be work for nothing, and the
     * factory itself lives on `MainBibleActivity`.
     */
    private fun readingViewScreenTurnedOn() {
        ScreenSettings.refreshNightMode()
        // Classic's `refreshIfNightModeChange()` (`:2702`), which is exactly these two calls.
        ScreenSettings.checkMonitoring()
        applyTheme()
        windowControl.activeWindow.bibleView?.onScreenTurnedOn()
    }

    /** Classic `MainBibleActivity.onScreenTurnedOff` (`:2688`). */
    private fun readingViewScreenTurnedOff() {
        windowControl.activeWindow.bibleView?.onScreenTurnedOff()
    }

    override fun onScreenTurnedOn() {
        super.onScreenTurnedOn()
        ReadingViewHostCallbacks.current?.onScreenTurnedOn?.invoke()
    }

    override fun onScreenTurnedOff() {
        super.onScreenTurnedOff()
        ReadingViewHostCallbacks.current?.onScreenTurnedOff?.invoke()
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
                            // Classic's `global_text_display_settings` row
                            // (`SettingsComposeActivity.kt:179-187`), now a graph navigation.
                            //
                            // THE FIX for design §3.2 item 2. What stood here built a classic
                            // text-settings Activity Intent (through `ScreenLauncher`, named
                            // without quoting the arm so the guard scan that hunts that launch can
                            // see the real thing is gone) and hung a detached settings bundle on
                            // it -- under the very extra key that screen uses to mean "a
                            // selector-originated edit of THIS workspace", which its scope
                            // resolution tested FIRST. So this row, which means GLOBAL, opened a
                            // detached edit of the empty workspace (a `SettingsLevel.GLOBAL`
                            // bundle's workspace id is `IdType.empty()`), and the edit's echo went
                            // nowhere because the launch was `startActivity` and read no result.
                            // The kdoc that stood above it asserted the opposite.
                            //
                            // A route argument is named, so the row now simply says which scope it
                            // means and carries no bundle at all. `TextDisplaySettingsScopeTest`
                            // pins both halves.
                            onOpenTextDisplaySettings = {
                                navController.navigate(NavRoutes.textDisplaySettings(scopeLevel = "global"))
                            },
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
                        download = DownloadDeps(
                            // Host-memoised, not per-entry: the controller is only half of this
                            // screen's state -- downloadSession holds the loaded Books, the
                            // repoIdentity maps and the DocRow mirror every seam below reads -- and
                            // CustomRepositories sits on top of this destination, disposing its
                            // composition. Same shape as myDocumentsControllerFor.
                            controllerFor = { initialTypeFilter -> downloadControllerFor(initialTypeFilter) },
                            // Read by the arm AFTER controllerFor, so it is the token of whichever
                            // session that call returned or built.
                            sessionToken = { downloadSessionToken },
                            // Classic's manifest label AND the string its screen drew are both
                            // @string/download (AndroidManifest.xml:216, AndroidStrings.kt:75).
                            title = getString(R.string.download),
                            topBarActions = { firstDownload -> DownloadOverflowMenu(navController, firstDownload) },
                            askIfWantToProceed = { askIfWantToProceedWithDownload() },
                            // Classic DownloadComposeActivity.kt:238, a suspend call: the host owns
                            // the scope so the graph's deps slot can stay a plain () -> Unit, the
                            // same shape ProgressStatusDeps.requestNotificationPermission uses.
                            requestNotificationPermission = {
                                lifecycleScope.launch {
                                    CommonUtils.requestNotificationPermission(this@NavHostComposeActivity)
                                }
                            },
                            refreshCatalogue = { refresh -> refreshDownloadCatalogue(refresh) },
                            onAutoDownload = { documentIds, downloadRecommended ->
                                handleAutoDownloadExtras(documentIds, downloadRecommended)
                            },
                            reloadCatalogueIfRequested = { reloadDownloadCatalogueIfRequested() },
                            onCancelDownload = { docId -> cancelDownload(docId) },
                            hasBible = downloadHasBible,
                            subscribeDownloadProgress = { subscribeDownloadProgress() },
                            subscribeMonitoring = { firstDownload -> subscribeDownloadMonitoring(firstDownload) },
                            persistTypeFilter = { filter ->
                                CommonUtils.settings.setInt("selected_document_filter_no", filter.ordinal)
                            },
                            initialTypeFilter = { addons -> initialDownloadTypeFilter(addons) },
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
                        cloudDocuments = CloudDocumentsDeps(
                            // PER-ENTRY factory (task-8 fix round 1) -- see
                            // cloudDocumentsEntryRef's kdoc for why a fresh instance every call
                            // (not a host-memoised singleton) is required here.
                            controllerFor = { buildCloudDocumentsController() },
                            title = getString(R.string.document_sync_manage_title),
                            topBarActions = { CloudDocumentsOverflowMenu() },
                            openOrGate = { cloudDocumentsOpenOrGate() },
                            seedItems = { cloudDocumentsSeedItems() },
                            refreshFromNetwork = { cloudDocumentsRefreshFromNetwork() },
                            subscribeProgress = { onRunning -> cloudDocumentsSubscribeProgress(onRunning) },
                            statusFilterLabels = { showRemoved -> cloudDocumentsStatusFilterLabels(showRemoved) },
                            categoryFilterLabels = { cloudDocumentsCategoryFilterLabels() },
                            confirmRemove = { initials, name, onConfirm ->
                                cloudDocumentsConfirmRemove(initials, name, onConfirm)
                            },
                            confirmPurge = { initials, name, onConfirm ->
                                cloudDocumentsConfirmPurge(initials, name, onConfirm)
                            },
                            countLabel = { count, bytes -> cloudDocumentsCountLabel(count, bytes) },
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
                    // Same shape one level up: MyDocumentsDeps' own onImport/onExport/onExportSelected
                    // AND the MyDocumentsController myDocumentsControllerFor builds are wired to the
                    // SAME instances -- see MyDocumentsDeps' own kdoc.
                    val onImportMyDocument: () -> Unit =
                        { importMyDocumentsFilesLauncher.launch(arrayOf("text/*")) }
                    val onExportMyDocument: (Long) -> Unit = { id ->
                        pendingMyDocumentExportId = id
                        exportMyDocumentTreeLauncher.launch(null)
                    }
                    val onExportSelectedMyDocuments: (List<Long>) -> Unit = { ids ->
                        pendingMyDocumentExportIds = ids
                        exportMyDocumentsBatchTreeLauncher.launch(null)
                    }
                    MyDocumentsNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        myDocumentPagesResults = myDocumentPagesResults,
                        myDocumentsResults = myDocumentsResults,
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
                        myDocuments = MyDocumentsDeps(
                            controllerFor = { onResult -> myDocumentsControllerFor(onResult) },
                            title = getString(R.string.my_documents_title),
                            onImport = onImportMyDocument,
                            onExport = onExportMyDocument,
                            onExportSelected = onExportSelectedMyDocuments,
                            importNamePrompt = myDocumentImportNamePrompt,
                            onConfirmImport = ::confirmImportMyDocument,
                            onDismissImport = ::dismissImportMyDocument,
                            routeForPages = { id ->
                                myDocumentsSession?.entityByLong?.get(id)?.let { doc ->
                                    NavRoutes.myDocumentPages(
                                        documentId = doc.id.toString(),
                                        documentInitials = doc.initials,
                                        documentName = doc.name,
                                    )
                                }
                            },
                        ),
                    )
                }
                val chooserDeps = remember {
                    ChooserNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        keyChooserResults = keyChooserResults,
                        passageResults = passageResults,
                        documentResults = documentResults,
                        chooseGeneralBookKey = ChooseGeneralBookKeyDeps(
                            // Classic's manifest android:label AND the screen's own title.
                            title = getString(R.string.general_book),
                            controllerFor = { onResult -> chooseGeneralBookKeyControllerFor(onResult) },
                            emptyResult = { generalBookKeyResult(null) },
                        ),
                        chooseMapKey = ChooseMapKeyDeps(
                            title = getString(R.string.doc_type_map),
                            controllerFor = { onResult -> chooseMapKeyControllerFor(onResult) },
                            emptyResult = { mapKeyResult(null) },
                        ),
                        chooseDictionaryWord = ChooseDictionaryWordDeps(
                            title = getString(R.string.dictionary),
                            hint = getString(R.string.search),
                            controllerFor = { onResult -> chooseDictionaryWordControllerFor(onResult) },
                            loadRows = { loadChooseDictionaryRows() },
                            loadSnippet = { keyId -> chooseDictionarySnippet(keyId) },
                        ),
                        gridChoosePassage = GridChoosePassageDeps(
                            // The ONE destination of the five whose classic Activity had no
                            // android:label, so its window title fell back to the APPLICATION label
                            // (AndroidManifest.xml:119-121). Reproduced rather than replaced by the
                            // screen's own step-dependent title.
                            windowTitle = applicationInfo.loadLabel(packageManager).toString(),
                            controllerFor = { isScripture, onResult ->
                                gridChoosePassageControllerFor(isScripture, onResult)
                            },
                        ),
                        chooseDocument = ChooseDocumentDeps(
                            title = getString(R.string.chooseBook),
                            controllerFor = { initialTypeFilter, onResult ->
                                chooseDocumentControllerFor(initialTypeFilter, onResult)
                            },
                            initialTypeFilter = { type -> chooseDocumentInitialTypeFilter(type) },
                            persistTypeFilter = { filter ->
                                CommonUtils.settings.setInt("selected_document_filter_no", filter.ordinal)
                            },
                            loadDocuments = { loadChooseDocuments() },
                            topBarActions = { ChooseDocumentOverflowMenu() },
                        ),
                    )
                }
                val workspaceDeps = remember {
                    WorkspaceNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        workspaceResults = workspaceResults,
                        textSettingsResults = textSettingsResults,
                        // The SAME instance BookmarkNavDeps gets: the label manager is registered by
                        // the bookmark graph, and the text-settings destination's Hide-labels row now
                        // navigates to it in-graph and collects the answer here.
                        manageLabelsResults = manageLabelsResults,
                        workspaceSelector = WorkspaceSelectorDeps(
                            controllerFor = { onResult, onCancel, onEditSettings ->
                                workspaceSelectorControllerFor(onResult, onCancel, onEditSettings)
                            },
                            // Classic's manifest android:label AND the string the screen draws.
                            title = getString(R.string.workspace_selector_title),
                            settingsBundleJson = { id -> workspaceService.settingsBundleJson(id) },
                            workspaceIdOf = { json -> SettingsBundle.fromJson(json).workspaceId.toString() },
                            onHelp = {
                                CommonUtils.showHelp(this, listOf(R.string.help_workspaces_title))
                            },
                        ),
                        textDisplaySettings = TextDisplaySettingsDeps(
                            sessionFor = { args -> textDisplaySettingsSessionFor(args) },
                            navStateMemo = textDisplayNavStateMemo,
                            // Classic's manifest android:label. NOT the screen's own top-bar title,
                            // which is scope-dependent and comes from the controller state.
                            windowTitle = getString(R.string.text_display_settings_activity_title),
                            activeWorkspaceId = { windowControl.windowRepository.id.toString() },
                            // Lambdas, not values: see the `by lazy` fields' own comment.
                            screenLabels = { textDisplayScreenLabels },
                            colorSettingsLabels = { textDisplayColorSettingsLabels },
                            backgroundImageChooserLabels = { textDisplayBackgroundImageChooserLabels },
                            inheritedFromWorkspace = getString(R.string.text_options_inherited_workspace),
                            inheritedFromGlobal = getString(R.string.text_options_inherited_global),
                            thumbnailFor = { token -> textDisplayThumbnailResolver.resolve(token) },
                        ),
                    )
                }
                /**
                 * The reading destination's deps (nav-graph slice 7 Task 6). Three of the four slots
                 * are real ports of `MainBibleActivity` behaviour; [ReadingNavDeps.content] is the
                 * one that cannot be, and says so loudly rather than rendering a blank screen.
                 */
                val readingNavDeps = remember {
                    ReadingNavDeps(
                        // Classic MainBibleActivity's manifest android:label (AndroidManifest.xml:103).
                        // This host's own block carries no label at all, so without this the reading
                        // view's window title would become the application label instead.
                        windowTitle = getString(R.string.app_name_short),
                        content = {
                            // NOT YET BUILDABLE, and deliberately loud (the `error(...)` shape design
                            // §1.1 uses for an unreachable branch that must never be reached quietly).
                            //
                            // The reading view's composition is `ComposeReadingViewHost
                            // .ReadingViewContent`, which this task extracted so a destination CAN
                            // render it with no ViewGroup. What it cannot do is build the ~70
                            // arguments: they come from `ComposeReadingViewHost`, whose constructor
                            // takes a `MainBibleActivity` and which reaches into it 130 times --
                            // ~25 `compose*` toolbar entry points, `binding`, `bibleViewFactory`,
                            // `documentViewManager` (itself `DocumentViewManager(MainBibleActivity)`),
                            // `handleOptionsMenuItem`, `applyChosen*` and more. Re-typing that off the
                            // Activity IS the reading view's own migration, and no task in the slice-7
                            // plan owns it (Task 13 deletes `MainBibleActivity.kt` outright).
                            //
                            // Nothing routes here yet, so this is unreachable today:
                            // `ScreenLauncher.MIGRATED` is Task 8's and `MainBibleActivity` is still
                            // the launcher. Task 8 makes `reading` the START destination and must not
                            // land before this slot is real -- see this task's report.
                            error(
                                "the reading destination has no content yet: ComposeReadingViewHost " +
                                    "still takes a MainBibleActivity. See ReadingNavDeps' kdoc.",
                            )
                        },
                        onKey = { key -> readingViewKeyPressed(key) },
                        onScreenTurnedOn = { readingViewScreenTurnedOn() },
                        onScreenTurnedOff = { readingViewScreenTurnedOff() },
                        setWindowTitle = { title -> setTitle(title) },
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
                    chooserNavGraph(navController, chooserDeps)
                    workspaceNavGraph(navController, workspaceDeps)
                    readingNavGraph(navController, readingNavDeps)
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
    // Ported from classic MyDocumentPagesComposeActivity (deleted by nav-graph slice 4 Task 9):
    // the pages-within-a-document editor also lives in the nav graph now, entered both from
    // CurrentGeneralBookPage (outside) and, once nav-graph slice 4 Task 6 lands, from MyDocuments
    // (inside). Every Room/
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
     *  `MyDocumentPagesComposeActivity.getFileName` (`:345`) -- byte-identical to classic
     *  `MyDocumentsComposeActivity.getFileName` (`:442-449`), so [importMyDocumentFromFiles] reuses
     *  this same helper rather than a second copy. */
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
     * fields below; entered from inside (`MyDocuments`) it publishes to `pending` and pops instead.
     */
    private val myDocumentPagesResults = NavResultChannel<MyDocumentPagesResult> { result ->
        val activityResult = NavResultIntents.forMyDocumentPages(result)
        setResult(activityResult.resultCode, activityResult.data)
        finish()
    }

    // --- MyDocuments host baggage ---------------------------------------------------------------
    // Ported from classic MyDocumentsComposeActivity (deleted by nav-graph slice 4 Task 9): the
    // document-list editor's Room/SAF/EventBus side effects. Every one of them is host-side (plan
    // D4/Room can't
    // cross into commonMain).

    /**
     * Classic `MyDocumentsComposeActivity`'s `entityByLong` (`:69`) plus the controller it is
     * memoised alongside -- see [MyDocumentsDeps.controllerFor]'s kdoc for why this whole session
     * must survive `MyDocumentPages` sitting on top of its destination, the same shape
     * [BookmarksSession] uses for the label manager sitting on top of `Bookmarks`.
     */
    private class MyDocumentsSession {
        lateinit var controller: MyDocumentsController
        var entityByLong: Map<Long, MyDocument> = emptyMap()
    }

    /**
     * `MyDocuments` is a root destination with no re-entry (same shape as [bookmarksSession]'s own
     * kdoc): reached only as the host's start destination, so this is built once and never dropped.
     */
    private var myDocumentsSession: MyDocumentsSession? = null

    /**
     * Classic `MyDocumentsComposeActivity`'s `controller` (`:81-90`) plus its `onCreate` `reload()`
     * (`:97`), memoised on [myDocumentsSession] -- see [MyDocumentsDeps.controllerFor] for why this
     * controller must survive the pages editor sitting on top of its destination.
     *
     * [onResult] is threaded through for shape parity with [myDocumentPagesControllerFor], but
     * nothing below actually calls it: `onOpen` is a no-op here (the arm's own `onOpen`, built from
     * [MyDocumentsDeps.routeForPages], handles the drill-down instead, since only the arm has the
     * `NavHostController`), and the `Selected` result reaches [myDocumentsResults] through the pages
     * child's own channel rather than through this controller.
     */
    private fun myDocumentsControllerFor(onResult: (MyDocumentsResult) -> Unit): MyDocumentsController {
        myDocumentsSession?.let { return it.controller }

        val session = MyDocumentsSession()

        /** Mirror of classic `applyChanges` (`MyDocumentsComposeActivity.kt:171-195`): delete
         *  removed documents (and their pages, CASCADE), persist reorders/renames/descriptions, and
         *  post `AiDocPagesChangedEvent` for the deletions. */
        fun applyMyDocumentsChanges(ordered: List<MyDocItem>, changed: Set<Long>, deleted: Set<Long>) {
            val deletedPageIds = deleted.mapNotNull { session.entityByLong[it] }.flatMap { doc ->
                myDocumentDao.pagesForDocument(doc.id).map { it.id }
            }
            deleted.mapNotNull { session.entityByLong[it] }.forEach { doc ->
                myDocumentDao.documentById(doc.id)?.let { fresh ->
                    MyDocumentBookManager.unregisterDocument(fresh.initials)
                    myDocumentDao.delete(fresh)
                }
            }
            val toUpdate = ArrayList<MyDocument>()
            ordered.forEachIndexed { index, item ->
                val doc = session.entityByLong[item.id] ?: return@forEachIndexed
                doc.orderNumber = index
                doc.name = item.name
                doc.description = item.description.ifEmpty { null }
                if (item.id in changed) { doc.updatedAt = System.currentTimeMillis(); toUpdate.add(doc) }
            }
            if (toUpdate.isNotEmpty()) myDocumentDao.updateDocuments(toUpdate)
            if (deletedPageIds.isNotEmpty()) ABEventBus.post(AiDocPagesChangedEvent(deletedPageIds = deletedPageIds))
        }

        session.controller = MyDocumentsController(
            // See this function's own kdoc: the real "open" handling lives in the arm, not here.
            onOpen = {},
            onImport = { importMyDocumentsFilesLauncher.launch(arrayOf("text/*")) },
            onExport = { id -> pendingMyDocumentExportId = id; exportMyDocumentTreeLauncher.launch(null) },
            onCreate = { name -> createMyDocument(session, name) },
            onExportSelected = { ids ->
                pendingMyDocumentExportIds = ids
                exportMyDocumentsBatchTreeLauncher.launch(null)
            },
            onSave = { ordered, changed, deleted -> applyMyDocumentsChanges(ordered, changed, deleted) },
        )
        myDocumentsSession = session

        lifecycleScope.launch {
            val docs = withContext(Dispatchers.IO) { myDocumentDao.allDocuments() }
            session.entityByLong = docs.mapIndexed { i, d -> i.toLong() to d }.toMap()
            session.controller.setDocuments(
                docs.mapIndexed { i, d ->
                    MyDocItem(
                        id = i.toLong(),
                        initials = d.initials,
                        name = d.name,
                        description = d.description ?: "",
                        isAiGenerated = d.sourcePromptId != null,
                        canDelete = MyDocumentBookManager.canDeleteDocument(d),
                    )
                },
            )
        }

        return session.controller
    }

    /** Mirror of classic `createNewDocument` (`MyDocumentsComposeActivity.kt:198-208`): insert +
     *  register a fresh empty document, then add it to the session's list. */
    private fun createMyDocument(session: MyDocumentsSession, name: String) {
        val initials = MyDocumentBookManager.generateInitials(name)
        val newDoc = MyDocument(name = name, initials = initials, orderNumber = session.controller.totalCount.value)
        myDocumentDao.insert(newDoc)
        MyDocumentBookManager.registerDocument(newDoc)
        val id = (session.entityByLong.keys.maxOrNull() ?: -1L) + 1L
        session.entityByLong = session.entityByLong + (id to newDoc)
        session.controller.addDocument(MyDocItem(id, initials, name, "", isAiGenerated = false, canDelete = true))
    }

    private var pendingMyDocumentExportId: Long? = null
    private var pendingMyDocumentExportIds: List<Long> = emptyList()

    /** SAF-picked URIs awaiting a user-entered name; [myDocumentImportNamePrompt] is shown while
     *  non-null -- classic's `pendingImportUris` (`MyDocumentsComposeActivity.kt:76`). */
    private var pendingMyDocumentImportUris: List<Uri>? = null

    /** Classic's `importNamePrompt` Compose state (`MyDocumentsComposeActivity.kt:79`) as a host
     *  flow -- see [MyDocumentsDeps.importNamePrompt]'s kdoc for why it cannot be `remember`ed in
     *  the arm. */
    private val myDocumentImportNamePrompt = MutableStateFlow<String?>(null)

    private val importMyDocumentsFilesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            // Parity with classic showImportNameDialog: don't import immediately -- stash the URIs
            // and open the import name dialog pre-filled with the same default name; import runs on
            // the user's confirm.
            if (!uris.isNullOrEmpty()) {
                pendingMyDocumentImportUris = uris
                myDocumentImportNamePrompt.value =
                    getString(R.string.my_document_new_name, (myDocumentsSession?.entityByLong?.size ?: 0) + 1)
            }
        }

    /** Import name dialog confirmed: run the verbatim import with the user's chosen name, then
     *  clear pending state. Mirror of classic `confirmImport` (`MyDocumentsComposeActivity.kt:247-252`). */
    private fun confirmImportMyDocument(name: String) {
        val uris = pendingMyDocumentImportUris
        myDocumentImportNamePrompt.value = null
        pendingMyDocumentImportUris = null
        if (uris != null) importMyDocumentFromFiles(name, uris)
    }

    /** Import name dialog dismissed: drop the pending URIs without importing. Mirror of classic
     *  `dismissImport` (`MyDocumentsComposeActivity.kt:255-258`). */
    private fun dismissImportMyDocument() {
        myDocumentImportNamePrompt.value = null
        pendingMyDocumentImportUris = null
    }

    private val exportMyDocumentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val id = pendingMyDocumentExportId
            pendingMyDocumentExportId = null
            val session = myDocumentsSession
            if (uri != null && id != null && session != null) {
                session.entityByLong[id]?.let { exportMyDocumentToFolder(it, uri) }
            }
        }

    private val exportMyDocumentsBatchTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val ids = pendingMyDocumentExportIds
            pendingMyDocumentExportIds = emptyList()
            if (uri != null && ids.isNotEmpty()) exportMyDocumentsToFolder(ids, uri)
        }

    /**
     * Import selected text files as a new document under the user-entered [documentName]. Ported
     * from classic `MyDocumentsComposeActivity.importFromFiles` (`:337-409`, `Dispatchers.IO` body).
     */
    private fun importMyDocumentFromFiles(documentName: String, uris: List<Uri>) {
        val session = myDocumentsSession ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                data class FileEntry(val fileName: String, val content: String)

                val entries = uris.mapNotNull { uri ->
                    val fileName = getMyDocumentPageFileName(uri) ?: return@mapNotNull null
                    val content = contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: return@mapNotNull null
                    FileEntry(fileName, content)
                }.sortedBy { it.fileName }

                if (entries.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@NavHostComposeActivity,
                            R.string.my_document_import_empty_selection,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }

                val initials = MyDocumentBookManager.generateInitials(documentName)
                val newDocument = MyDocument(
                    name = documentName,
                    initials = initials,
                    orderNumber = session.controller.totalCount.value,
                )
                myDocumentDao.insert(newDocument)

                for ((index, entry) in entries.withIndex()) {
                    val contentType = when {
                        entry.fileName.endsWith(".html", true)
                            || entry.fileName.endsWith(".htm", true) -> MyDocumentContentType.HTML
                        else -> MyDocumentContentType.MARKDOWN
                    }
                    val rawName = entry.fileName.substringBeforeLast(".")
                    val title = rawName.replace(Regex("^\\d+-"), "").trim()
                        .ifEmpty { getString(R.string.my_document_new_page_name, index + 1) }

                    val pageId = IdType()
                    val page = MyDocumentPage(
                        id = pageId,
                        documentId = newDocument.id,
                        title = title,
                        pageKey = "page_$pageId",
                        contentType = contentType,
                        orderNumber = index,
                    )
                    myDocumentDao.insertPageWithContent(page, entry.content)
                }

                MyDocumentBookManager.registerDocument(newDocument)

                withContext(Dispatchers.Main) {
                    val id = (session.entityByLong.keys.maxOrNull() ?: -1L) + 1L
                    session.entityByLong = session.entityByLong + (id to newDocument)
                    session.controller.addDocument(
                        MyDocItem(id, initials, documentName, "", isAiGenerated = false, canDelete = true),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG_MY_DOCUMENTS, "Failed to import files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NavHostComposeActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Export one document's pages to a chosen tree folder. Ported verbatim from classic
     *  `MyDocumentsComposeActivity.exportDocumentToFolder` (`:411-439`); the per-page write loop is
     *  shared with the batch path via [writeMyDocumentPagesInto]. */
    private fun exportMyDocumentToFolder(document: MyDocument, treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pages = myDocumentDao.pagesWithContentForDocument(document.id)
                if (pages.isEmpty()) return@launch

                val treeDoc = DocumentFile.fromTreeUri(this@NavHostComposeActivity, treeUri) ?: return@launch
                writeMyDocumentPagesInto(treeDoc, pages)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NavHostComposeActivity,
                        R.string.my_document_export_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG_MY_DOCUMENTS, "Failed to export document", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NavHostComposeActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Export several documents into one chosen folder, each into its OWN subdirectory -- two
     * documents can hold same-named pages, and a flat batch export would collide. Ported verbatim
     * from classic `MyDocumentsComposeActivity.exportDocumentsToFolder` (`:276-308`).
     */
    private fun exportMyDocumentsToFolder(ids: List<Long>, treeUri: Uri) {
        val session = myDocumentsSession ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val treeDoc = DocumentFile.fromTreeUri(this@NavHostComposeActivity, treeUri) ?: return@launch
                for (id in ids) {
                    val document = session.entityByLong[id] ?: continue
                    // Fetch first, and skip a page-less document BEFORE creating its subdirectory --
                    // otherwise a batch containing an empty document leaves a stray empty folder.
                    val pages = myDocumentDao.pagesWithContentForDocument(document.id)
                    if (pages.isEmpty()) continue
                    val folderName = document.name
                        .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                        .take(50)
                        .ifEmpty { document.initials }
                    val subDir = treeDoc.createDirectory(folderName) ?: continue
                    writeMyDocumentPagesInto(subDir, pages)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NavHostComposeActivity,
                        R.string.my_document_export_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG_MY_DOCUMENTS, "Failed to export documents", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NavHostComposeActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** The per-page write loop, lifted verbatim out of classic
     *  `MyDocumentsComposeActivity.exportDocumentToFolder` (`:314-329`) so the batch path reuses it.
     *  Runs on the caller's (IO) dispatcher. */
    private fun writeMyDocumentPagesInto(dir: DocumentFile, pages: List<MyDocumentPageWithContent>) {
        for ((index, page) in pages.withIndex()) {
            val ext = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
            val mimeType = if (ext == "html") "text/html" else "text/markdown"
            val orderPrefix = String.format("%02d", index + 1)
            val sanitizedTitle = page.title
                .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                .take(50)
                .ifEmpty { getString(R.string.my_document_export_fallback_name) }
            val entryName = "$orderPrefix-$sanitizedTitle.$ext"
            val file = dir.createFile(mimeType, entryName) ?: continue
            contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write((page.content ?: "").toByteArray(Charsets.UTF_8))
            }
        }
    }

    /**
     * `MyDocuments`' channel. Unlike [myDocumentPagesResults] this destination has only ONE entry
     * mode -- see [MyDocumentsNavDeps.myDocumentsResults]' kdoc -- so [deliver] always takes this
     * exit branch: pack the result exactly as [NavResultIntents.forMyDocuments] did before this
     * channel existed, `setResult` and `finish`.
     */
    private val myDocumentsResults = NavResultChannel<MyDocumentsResult> { result ->
        val activityResult = NavResultIntents.forMyDocuments(result)
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

    // --- Download host baggage ------------------------------------------------------------------
    // Ported from classic DownloadComposeActivity (deleted by nav-graph slice 4 Task 9): the
    // download screen's network JSON fetches, its JSword load/delete/unlock seams, its six
    // platform AlertDialogs and
    // its live per-row progress bridge. None of it can cross into commonMain -- every line names a
    // JSword type, an Android dialog or a `settings` key -- so it reaches the destination as the
    // lambdas of DownloadDeps.

    private val swordDocumentInfoDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val downloadBookmarksDao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    /**
     * Classic `DownloadComposeActivity`'s own fields (`:139-166`) plus the controller they are
     * memoised alongside -- see [DownloadDeps.controllerFor] for why this whole session must survive
     * `CustomRepositories` sitting on top of its destination, the same shape [MyDocumentsSession]
     * uses for the pages editor.
     */
    private class DownloadSession {
        lateinit var controller: DocumentSelectionController
        lateinit var downloadManager: DownloadManager
        lateinit var repoFactory: RepoFactory
        lateinit var genericFileDownloader: GenericFileDownloader
        val bridge = DownloadProgressBridge()

        /** Network-fetched configs (parity with classic `Ref<…>` fields). */
        var recommendedDocuments: DocumentConfiguration? = null
        var defaultDocuments: DocumentConfiguration? = null
        var badDocuments: DocumentConfiguration? = null
        var pseudoBooks: List<PseudoBook>? = null

        /** docId (`Book.repoIdentity`) -> Book, rebuilt on every (re)load. */
        var booksById: Map<String, Book> = emptyMap()
        /** The full loaded book list (for `findBookByInitials` in the auto-download extras). */
        var allBooks: List<Book> = emptyList()
        /** Current `DocRow`s host-side so progress updates can rebuild only the affected rows. */
        var currentRows: List<DocRow> = emptyList()
        /** groupingKey -> sort rank from `downloadControl.sortLanguages` (RelevantLanguageSorter). */
        var langRank: Map<String, Int> = emptyMap()
        val booksNotFound = ArrayList<String>()

        val hasErrors get() =
            genericFileDownloader.errors.isNotEmpty() || downloadManager.failedRepos.isNotEmpty()
    }

    /**
     * `Download` has no re-entry of its own (its only child destination is `CustomRepositories`), so
     * this is built once and never dropped -- [myDocumentsSession]'s own reasoning.
     *
     * It is a plain field, so it does NOT survive an Activity recreation, while the arm's one-shot
     * guards are `rememberSaveable` and DO -- see [downloadSessionToken], which is what keeps the
     * two from disagreeing.
     */
    private var downloadSession: DownloadSession? = null

    /**
     * Identity of the CURRENT [downloadSession], handed to the graph as [DownloadDeps.sessionToken]
     * and regenerated ONLY by [downloadControllerFor] when it actually builds a new session.
     *
     * Slice 4 final-review fix (findings I1+I2). The arm's `seededRouteStateFor`/`ranEntrySetupFor`
     * one-shots are keyed on this token combined with the route's own argument signature, so that
     * "this entry has been set up" becomes "this entry has been set up AGAINST THIS SESSION, WITH
     * THESE ARGUMENTS" -- see [DownloadDeps.sessionToken]'s kdoc for the two symptoms that closes.
     * The value must be unique across Activity INSTANCES, not merely within one, or a restored
     * saveable key would match a token a rebuilt session happened to reuse; a counter restarting at
     * zero on every recreation would do exactly that, which is why this is a random UUID rather
     * than a sequence number.
     */
    private var downloadSessionToken: String = UUID.randomUUID().toString()

    /**
     * Drives the `firstDownload` OK gate's enabled state: true once >=1 Bible is installed. Latches
     * (classic `okayButtonEnabled` never flips back off), fed by [downloadCompletionListener].
     *
     * A HOST field rather than a session one because [DownloadDeps] is built before any destination
     * has asked for a controller, so the flow has to exist ahead of the session.
     */
    private val downloadHasBible = MutableStateFlow(false)

    /** Mirror of classic FirstDownload's `JobManager` WorkListener: enable OK once a Bible lands. */
    private val downloadCompletionListener = object : WorkListener {
        override fun workProgressed(workEvent: WorkEvent) {
            if (workEvent.job.isFinished) updateHasBible()
        }
        // Never called by JSword in practice, so all the work is done in workProgressed (classic note).
        override fun workStateChanged(workEvent: WorkEvent) {}
    }

    /** Latching check: once a Bible is installed, OK stays enabled (classic `enableOkayButtonIfBibles`). */
    private fun updateHasBible() {
        if (!downloadHasBible.value) {
            downloadHasBible.value = Books.installed().books.any { it.bookCategory == BookCategory.BIBLE }
        }
    }

    /**
     * Classic `DownloadComposeActivity`'s `controller` (`:196-227`) and the `onCreate` setup around
     * it (`:229-231`), memoised on [downloadSession].
     */
    private fun downloadControllerFor(initialTypeFilter: DocTypeFilter): DocumentSelectionController {
        downloadSession?.let { return it.controller }

        val session = DownloadSession()
        session.downloadManager = DownloadManager { }
        session.repoFactory = RepoFactory(session.downloadManager)
        session.genericFileDownloader = GenericFileDownloader(this) { }

        session.controller = DocumentSelectionController(
            // classic sortLanguages order (RelevantLanguageSorter): rank by the precomputed index.
            langComparator = Comparator { a, b ->
                (session.langRank[a.groupingKey] ?: Int.MAX_VALUE)
                    .compareTo(session.langRank[b.groupingKey] ?: Int.MAX_VALUE)
            },
            onSelect = { docId -> handleDownloadSelection(docId) },
            onDelete = { ids -> handleDownloadDelete(ids) },
            onDeleteIndex = { ids -> handleDownloadDeleteIndex(ids) },
            onAbout = { docId -> handleDownloadAbout(docId) },
            onUnlock = { docId -> handleDownloadUnlock(docId) },
            onStickyLanguage = { lang -> CommonUtils.settings.setString("selected_language_code", lang?.code) },
            // Every key applies here: this list is the only one with an install size, and the only
            // one that loads the recommended-documents config.
            applicableSortKeys = DocSortKey.entries.toSet(),
            applicableGroupKeys = listOf(DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.LANGUAGE, DocGroupBy.REPOSITORY),
            storedArrangement = if (CommonUtils.settings.getBoolean(ARRANGEMENT_REMEMBER_KEY, true))
                CommonUtils.settings.getString(ARRANGEMENT_KEY, null) else null,
            rememberArrangementInitially = CommonUtils.settings.getBoolean(ARRANGEMENT_REMEMBER_KEY, true),
            onArrangementChange = { encoded, remember ->
                CommonUtils.settings.setBoolean(ARRANGEMENT_REMEMBER_KEY, remember)
                CommonUtils.settings.setString(ARRANGEMENT_KEY, encoded)
            },
            // Round 17e-1 final-review fix (I3): debounces the drag-reorder commit.
            scope = lifecycleScope,
        )
        session.controller.setTypeFilter(initialTypeFilter)
        downloadSession = session
        // A NEW session, so a new identity: every one-shot the arm stamped against the previous one
        // (in this Activity instance or in the one a recreation replaced) is now stale by
        // construction. See [downloadSessionToken].
        downloadSessionToken = UUID.randomUUID().toString()
        return session.controller
    }

    /**
     * Classic `initialTypeFilter()` (`:909-916`) minus its `"type"` extra (plan D1): the tree's only
     * `putExtra("type"` is gone, so only the `addons` branch and the persisted fallback remain.
     */
    private fun initialDownloadTypeFilter(addons: Boolean): DocTypeFilter =
        if (addons) DocTypeFilter.ADDON
        else DocTypeFilter.entries.getOrElse(
            CommonUtils.settings.getInt("selected_document_filter_no", 0),
        ) { DocTypeFilter.ALL }

    /** Classic `askIfWantToProceed()` (`:460-478`), a platform AlertDialog that can answer NO. */
    private suspend fun askIfWantToProceedWithDownload(): Boolean = withContext(Dispatchers.Main) {
        if (CommonUtils.settings.getBoolean("download_do_not_ask", false)) {
            true
        } else {
            suspendCoroutine { cont ->
                AlertDialog.Builder(this@NavHostComposeActivity)
                    .setTitle(R.string.download_question_title)
                    .setMessage(getString(R.string.download_question_message))
                    .setPositiveButton(R.string.yes) { _, _ -> cont.resume(true) }
                    .setNegativeButton(R.string.do_not_ask_again) { _, _ ->
                        CommonUtils.settings.setBoolean("download_do_not_ask", true)
                        cont.resume(true)
                    }
                    .setNeutralButton(R.string.cancel) { _, _ -> cont.resume(false) }
                    .setOnCancelListener { cont.resume(false) }
                    .show()
            }
        }
    }

    /**
     * Classic's `downloadDocJson()` + `loadDocuments()` + `updateLastRepoRefreshDate()` block
     * (`:239-243` on entry, `:286-297` on pull-to-refresh) as one call.
     *
     * [refresh] means FORCE a repository re-fetch (the gesture). The staleness cache stays here,
     * where `settings` lives: a non-forced call still refreshes when the repo list is old, which is
     * exactly what classic's `val refresh = isRepoBookListOld` did on entry.
     */
    private suspend fun refreshDownloadCatalogue(refresh: Boolean) {
        val session = downloadSession ?: return
        downloadDocJson(session)
        val doRefresh = refresh || isRepoBookListOld
        loadDownloadDocuments(session, doRefresh)
        if (doRefresh) CommonUtils.settings.setLong(REPO_REFRESH_DATE, Date().time)
    }

    /**
     * Armed when the overflow menu navigates to `CustomRepositories`, disarmed by
     * [reloadDownloadCatalogueIfRequested]. Classic awaited that Activity's result and then reloaded
     * (`DownloadComposeActivity.kt:899-905`); an in-graph hop has no result to await, so the
     * "and then reloaded" half is carried here instead of being lost.
     */
    private var pendingDownloadCatalogueReload = false

    /**
     * Classic `onCustomRepositories()`'s `loadDocuments(true)` (`:904`), verbatim: a FORCED reload,
     * not [refreshDownloadCatalogue]'s staleness-cached one -- adding or editing a repository is
     * exactly what changes the downloadable set, and a `refresh = false` load may serve the day-old
     * cached catalogue. No `downloadDocJson()` either: classic did not re-fetch the JSON configs on
     * this path.
     */
    private suspend fun reloadDownloadCatalogueIfRequested() {
        if (!pendingDownloadCatalogueReload) return
        pendingDownloadCatalogueReload = false
        val session = downloadSession ?: return
        loadDownloadDocuments(session, refresh = true)
    }

    /** Classic `downloadDocJson()` (`:406-414`), minus the defaults list -- see [handleAutoDownloadExtras]. */
    private suspend fun downloadDocJson(session: DownloadSession) = coroutineScope {
        awaitAll(
            async { loadRecommendedDocuments(session) },
            async { loadPseudoBooks(session) },
            async { loadBadDocuments(session) },
        )
    }

    private suspend fun loadRecommendedDocuments(session: DownloadSession) = withContext(Dispatchers.IO) {
        val source = java.net.URL("https://andbible.github.io/data/${SharedConstants.RECOMMENDED_JSON}")
        val target = File(SharedConstants.modulesDir, SharedConstants.RECOMMENDED_JSON)
        session.genericFileDownloader.downloadFile(source, target, "Recommendations", reportError = !target.canRead())
        if (target.canRead()) {
            session.recommendedDocuments =
                CommonUtils.json.decodeFromString(DocumentConfiguration.serializer(), String(target.readBytes()))
        } else {
            Log.e(TAG_DOWNLOAD, "Could not load recommendations")
        }
    }

    private suspend fun loadBadDocuments(session: DownloadSession) = withContext(Dispatchers.IO) {
        val source = java.net.URL("https://andbible.github.io/data/${SharedConstants.BAD_DOCS_JSON}")
        val target = File(SharedConstants.modulesDir, SharedConstants.BAD_DOCS_JSON)
        session.genericFileDownloader.downloadFile(source, target, "Bad documents list", reportError = !target.canRead())
        if (target.canRead()) {
            session.badDocuments =
                CommonUtils.json.decodeFromString(DocumentConfiguration.serializer(), String(target.readBytes()))
        } else {
            Log.e(TAG_DOWNLOAD, "Could not load bad documents list")
        }
    }

    private suspend fun loadDefaultDocuments(session: DownloadSession) = withContext(Dispatchers.IO) {
        val source = java.net.URL("https://andbible.github.io/data/${SharedConstants.DEFAULT_JSON}")
        val target = File(SharedConstants.modulesDir, SharedConstants.DEFAULT_JSON)
        session.genericFileDownloader.downloadFile(source, target, "Defaults", reportError = !target.canRead())
        if (target.canRead()) {
            session.defaultDocuments =
                CommonUtils.json.decodeFromString(DocumentConfiguration.serializer(), String(target.readBytes()))
        } else {
            Log.e(TAG_DOWNLOAD, "Could not load default document list")
        }
    }

    private suspend fun loadPseudoBooks(session: DownloadSession) = withContext(Dispatchers.IO) {
        val source = java.net.URL("https://andbible.github.io/data/${SharedConstants.PSEUDO_BOOKS}")
        val target = File(SharedConstants.modulesDir, SharedConstants.PSEUDO_BOOKS)
        session.genericFileDownloader.downloadFile(source, target, "Pseudo books", reportError = !target.canRead())
        if (target.canRead()) {
            session.pseudoBooks = CommonUtils.json.decodeFromString(serializer(), String(target.readBytes()))
        } else {
            Log.e(TAG_DOWNLOAD, "Could not load pseudo book list")
        }
    }

    /**
     * (Re)load downloadable Books off-main, rebuild the docId->Book map and the `DocRow` list, and
     * push them into the controller. Classic `loadDocuments()` (`:485-528`).
     */
    private suspend fun loadDownloadDocuments(session: DownloadSession, refresh: Boolean) {
        try {
            val books = withContext(Dispatchers.Default) {
                session.downloadManager.refreshInstallManager()
                val docs = downloadControl.getDownloadableDocuments(session.repoFactory, refresh)
                if (docs.isNotEmpty()) docs + FakeBookFactory.pseudoDocuments(session.pseudoBooks) else docs
            }
            val rows = withContext(Dispatchers.Default) {
                val grouping = LanguageGrouping(books.mapNotNull { it.language })
                val langByKey: Map<String, LangOption> = grouping.representatives.mapNotNull { lang ->
                    val key = grouping.key(lang) ?: return@mapNotNull null
                    key to LangOption(lang.code ?: "", lang.name, key)
                }.toMap()
                // classic sortLanguages(representatives) order -> groupingKey -> rank index.
                session.langRank = downloadControl.sortLanguages(grouping.representatives)
                    .mapIndexedNotNull { i, lang -> grouping.key(lang)?.let { it to i } }
                    .toMap()
                // Bad documents flagged HIDE are excluded (classic filterDocuments); WARN -> badWarn.
                books.filterNot { it.isBadDocument(session.badDocuments, BadDocumentAction.HIDE) }
                    .map { it.toDocRow(session, grouping, langByKey) }
            }
            session.allBooks = books
            // docId is the opaque repoIdentity ("repo--initials"), unique per repo+initials.
            session.booksById = books.associateBy { it.repoIdentity }
            // Progress events already carry repoIdentity as DocumentStatus.id, and docId ==
            // repoIdentity now, so the bridge's repoIdentity -> docId map is the identity.
            session.bridge.setRepoIdentityMap(books.associate { it.repoIdentity to it.repoIdentity })
            session.currentRows = rows
            session.controller.setDocuments(rows)
        } catch (e: Exception) {
            Log.e(TAG_DOWNLOAD, "Error loading download documents", e)
            session.controller.showError()
        }
    }

    private fun Book.toDocRow(
        session: DownloadSession,
        grouping: LanguageGrouping,
        langByKey: Map<String, LangOption>,
    ): DocRow {
        val status = downloadControl.getDocumentStatus(this)
        val key = grouping.key(language) ?: (language.code ?: "")
        val sizeMb = bookMetaData.getProperty(SwordBookMetaData.KEY_INSTALL_SIZE)
            ?.toDoubleOrNull()?.let { it / 1e6 }
        return DocRow(
            docId = repoIdentity,
            osisId = osisID,
            abbreviation = abbreviation,
            name = name,
            language = langByKey[key] ?: LangOption(language.code ?: "", language.name, key),
            repository = getProperty(DownloadManager.REPOSITORY_KEY) ?: "",
            category = docCategoryOf(bookCategory),
            installStatus = status.documentInstallStatus.toDocInstallStatus(),
            percentDone = status.percentDone,
            recommended = isRecommended(session.recommendedDocuments),
            badWarn = isBadDocument(session.badDocuments, BadDocumentAction.WARN),
            locked = isLocked,
            enciphered = isEnciphered,
            canDelete = canDeleteNow(this),
            installSizeMb = sizeMb,
        )
    }

    /**
     * From the INSTALLED copy, not the repository catalogue entry: a repo Book's driver is an
     * installer driver and is never deletable. Re-resolved on every in-place row update, because the
     * installed copy does not exist until the download finishes (classic `canDeleteNow`, `:559-560`).
     */
    private fun canDeleteNow(book: Book?): Boolean =
        runCatching { book?.installedDocument?.canDelete ?: false }.getOrDefault(false)

    /**
     * Classic's `lifecycleScope.launch { bridge.statuses.collect { applyProgress(it) } }`
     * (`:253-255`), started and stopped by the destination instead of by an Activity -- see
     * [DownloadDeps.subscribeDownloadProgress] for why no payload crosses into the graph.
     */
    private fun subscribeDownloadProgress(): () -> Unit {
        val session = downloadSession ?: return {}
        val job = lifecycleScope.launch {
            session.bridge.statuses.collect { statusMap -> applyDownloadProgress(session, statusMap) }
        }
        return { job.cancel() }
    }

    /** Classic `onStart`/`onStop` (`:376-394`) as one subscribe/unsubscribe pair. */
    private fun subscribeDownloadMonitoring(firstDownload: Boolean): () -> Unit {
        val session = downloadSession ?: return {}
        session.bridge.register()
        downloadControl.startMonitoringDownloads()
        if (firstDownload) {
            updateHasBible()
            JobManager.addWorkListener(downloadCompletionListener)
        }
        return {
            session.bridge.unregister()
            downloadControl.stopMonitoringDownloads()
            if (firstDownload) JobManager.removeWorkListener(downloadCompletionListener)
        }
    }

    /** Classic `applyProgress` (`:565-590`), verbatim apart from the session indirection. */
    private fun applyDownloadProgress(session: DownloadSession, statusMap: Map<String, RowDownloadStatus>) {
        if (statusMap.isEmpty()) return
        // Push each row's live status straight into the controller, which updates it IN PLACE -- no
        // re-sort, no clearSelection() -- unlike setDocuments()/refilter(). Keep the host-side
        // currentRows mirror in sync so a later refreshRowStatus()/setDocuments() doesn't revert the
        // in-progress status.
        var mirror = session.currentRows
        for ((docId, s) in statusMap) {
            // A completed install arrives HERE, not through refreshRowStatus, so this is the path on
            // which canDelete actually flips, and it has to be re-resolved.
            val canDelete = canDeleteNow(session.booksById[docId])
            session.controller.updateDownloadStatus(docId, s.status, s.percentDone, canDelete)
            mirror = mirror.map { row ->
                if (row.docId == docId &&
                    (row.installStatus != s.status || row.percentDone != s.percentDone || row.canDelete != canDelete)
                ) {
                    row.copy(installStatus = s.status, percentDone = s.percentDone, canDelete = canDelete)
                } else row
            }
        }
        session.currentRows = mirror
    }

    /** Refresh one row's status directly from getDocumentStatus (classic `refreshRowStatus`, `:593-617`). */
    private fun refreshDownloadRowStatus(session: DownloadSession, book: Book) {
        val status = downloadControl.getDocumentStatus(book)
        val canDelete = canDeleteNow(book)
        val updated = session.currentRows.map { row ->
            if (row.docId == book.repoIdentity) {
                row.copy(
                    installStatus = status.documentInstallStatus.toDocInstallStatus(),
                    percentDone = status.percentDone,
                    canDelete = canDelete,
                )
            } else row
        }
        if (updated != session.currentRows) {
            session.currentRows = updated
            // updateDownloadStatus() updates the row IN PLACE, never re-sorting/clearing selection.
            session.controller.updateDownloadStatus(
                book.repoIdentity,
                status.documentInstallStatus.toDocInstallStatus(),
                status.percentDone,
                canDelete,
            )
        }
    }

    /** Classic `handleDocumentSelection` (`:622-631`): a selection starts a download, in place. */
    private fun handleDownloadSelection(docId: String) {
        val session = downloadSession ?: return
        val book = session.booksById[docId] ?: return
        Log.i(TAG_DOWNLOAD, "Document selected:" + book.initials)
        try {
            manageDownload(session, book)
        } catch (e: Exception) {
            Log.e(TAG_DOWNLOAD, "Error on attempt to download", e)
            Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show()
        }
    }

    /** Classic `manageDownload` (`:633-654`), including its confirm-before-download AlertDialog. */
    private fun manageDownload(session: DownloadSession, documentToDownload: Book?) {
        if (documentToDownload != null &&
            downloadControl.getDocumentStatus(documentToDownload).documentInstallStatus != DocumentInstallStatus.BEING_INSTALLED &&
            !documentToDownload.isPseudoBook
        ) {
            if (documentToDownload.isInstalled && DatabaseContainer.ready &&
                downloadBookmarksDao.genericBookmarkCountFor(documentToDownload) > 0
            ) {
                lifecycleScope.launch {
                    if (CommonUtils.documentUpgradeConfirmation(this@NavHostComposeActivity)) {
                        doDownload(session, documentToDownload)
                    }
                }
            } else {
                AlertDialog.Builder(this)
                    .setMessage(getText(R.string.download_document_confirm_prefix).toString() + " " + documentToDownload.name)
                    .setCancelable(false)
                    .setPositiveButton(R.string.okay) { _, _ -> doDownload(session, documentToDownload) }
                    .setNegativeButton(R.string.cancel) { _, _ -> }.create().show()
            }
        }
    }

    /**
     * Classic's own `downloadScope` (`DownloadComposeActivity.kt:167`), deliberately NOT
     * [lifecycleScope]: `downloadControl.downloadDocument` is a SUSPEND call, so a cancellation
     * between "user tapped" and "job enqueued" would silently drop the download -- and leaving the
     * screen is exactly when that happens.
     */
    private val downloadScope = CoroutineScope(Dispatchers.Default)

    /** Classic `doDownload` (`:656-665`). */
    private fun doDownload(session: DownloadSession, document: Book) = downloadScope.launch(Dispatchers.Main) {
        try {
            downloadControl.downloadDocument(session.repoFactory, document)
            refreshDownloadRowStatus(session, document)
            ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        } catch (e: Exception) {
            Log.e(TAG_DOWNLOAD, "Error on attempt to download", e)
            Toast.makeText(this@NavHostComposeActivity, R.string.error_downloading, Toast.LENGTH_SHORT).show()
        }
    }

    /** Classic `onCancel` (`:344`): the per-row cancel button, resolved through the session's map. */
    private fun cancelDownload(docId: String) {
        val session = downloadSession ?: return
        session.booksById[docId]?.let { downloadControl.cancelDownload(it) }
    }

    /**
     * Classic `handleDelete` (`:673-707`): ONE dialog listing every document that will be deleted,
     * with the non-deletable remainder reported once rather than once per document.
     */
    private fun handleDownloadDelete(ids: Set<String>) {
        val session = downloadSession ?: return
        val selected = ids.mapNotNull { session.booksById[it] }
        val (deletable, rest) = selected.partition { documentControl.canDelete(it.installedDocument) }
        if (rest.isNotEmpty()) ABEventBus.post(ToastEvent(R.string.cant_delete_document))
        if (deletable.isEmpty()) return
        val msg: CharSequence = if (deletable.size == 1) {
            getString(R.string.delete_doc, deletable.single().name)
        } else {
            getString(R.string.delete_docs_confirm) + "\n\n" + deletable.joinToString("\n") { it.name }
        }
        AlertDialog.Builder(this)
            .setMessage(msg).setCancelable(true)
            .setPositiveButton(R.string.yes) { _, _ ->
                // Re-checked per document INSIDE the loop: Book.canDelete is `!lastBible && ...`, so
                // with exactly two Bibles installed both pass the partition, and deleting them both
                // would leave zero Bibles. Deleting one flips the other's flag.
                var skipped = false
                for (document in deletable) {
                    if (!documentControl.canDelete(document.installedDocument)) { skipped = true; continue }
                    try {
                        Log.i(TAG_DOWNLOAD, "Deleting:$document")
                        documentControl.deleteDocument(document.installedDocument)
                    } catch (e: Exception) {
                        Log.e(TAG_DOWNLOAD, "Deleting document crashed", e)
                        Dialogs.showErrorMsg(R.string.error_occurred, e)
                    }
                }
                if (skipped) ABEventBus.post(ToastEvent(R.string.cant_delete_document))
                lifecycleScope.launch { loadDownloadDocuments(session, false) }
                ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
            }
            .setNegativeButton(R.string.no, null)
            .create().show()
    }

    /** Classic `handleDeleteIndex` (`:710-727`). */
    private fun handleDownloadDeleteIndex(ids: Set<String>) {
        val session = downloadSession ?: return
        for (document in ids.mapNotNull { session.booksById[it] }) {
            val msg: CharSequence = getString(R.string.delete_search_index_doc, document.name)
            AlertDialog.Builder(this)
                .setMessage(msg).setCancelable(true)
                .setPositiveButton(R.string.okay) { _, _ ->
                    try {
                        Log.i(TAG_DOWNLOAD, "Deleting index:$document")
                        SwordDocumentFacade.deleteDocumentIndex(document.installedDocument)
                    } catch (e: Exception) {
                        Log.e(TAG_DOWNLOAD, "Deleting index crashed", e)
                        Dialogs.showErrorMsg(R.string.error_occurred, e)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .create().show()
        }
    }

    /** Classic `handleAbout` (`:730-746`): reload the SBMD (retaining repo/BadDocument), then showAbout. */
    private fun handleDownloadAbout(docId: String) {
        val session = downloadSession ?: return
        val document = session.booksById[docId] ?: return
        try {
            val sbmd = document.bookMetaData as SwordBookMetaData
            val repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY)
            val badDocument = sbmd.getProperty("BadDocument")
            sbmd.reload()
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
            sbmd.putProperty("BadDocument", badDocument)
            lifecycleScope.launch(Dispatchers.Main) {
                CommonUtils.showAbout(this@NavHostComposeActivity, document)
            }
        } catch (e: Exception) {
            Log.e(TAG_DOWNLOAD, "Error expanding SwordBookMetaData for $document", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    /** Classic `handleUnlock` (`:748-756`). */
    private fun handleDownloadUnlock(docId: String) {
        val session = downloadSession ?: return
        val document = session.booksById[docId] ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            CommonUtils.unlockDocument(this@NavHostComposeActivity, document)
            loadDownloadDocuments(session, false)
        }
    }

    /**
     * Classic `handleAutoDownloadExtras` (`:759-786`), with its two Intent extras now route
     * arguments. The defaults list is fetched HERE rather than in [downloadDocJson]: classic fetched
     * it in parallel with the other three but gated on the same `download-recommended` extra this
     * takes, and nothing else reads `defaultDocuments` -- so the fetch follows its one consumer
     * instead of needing a flag [DownloadDeps.refreshCatalogue] does not carry.
     */
    private suspend fun handleAutoDownloadExtras(documentIds: String?, downloadRecommended: Boolean) {
        val session = downloadSession ?: return
        // Cleared per run: the session outlives one visit to this destination (a second entry into
        // the singleTop host reuses it), and a stale list would report books that were not found on
        // a PREVIOUS visit in this visit's "books not downloaded" dialog.
        session.booksNotFound.clear()
        withContext(Dispatchers.Main) {
            if (documentIds != null) {
                val booksToDownload: List<SwordDocumentInfo> =
                    CommonUtils.json.decodeFromString(serializer(), documentIds)
                downloadRequestedBooks(session, booksToDownload)
                if (session.booksNotFound.size > 0) {
                    warnUserBooksNotDownloaded(session)
                }
            }
            if (!downloadRecommended) return@withContext
            loadDefaultDocuments(session)
            val defaults = session.defaultDocuments ?: return@withContext
            for (l in listOf(
                defaults.bibles["en"], defaults.commentaries["en"], defaults.addons["en"],
                defaults.books["en"], defaults.dictionaries["en"], defaults.maps["en"],
            )) {
                val l2 = l?.map {
                    if (it.contains("::")) {
                        val (initials, repository) = it.split("::")
                        SwordDocumentInfo(initials = initials, repository = repository, language = "en", abbreviation = "", name = "")
                    } else {
                        SwordDocumentInfo(initials = it, repository = "", language = "en", abbreviation = "", name = "")
                    }
                }
                downloadRequestedBooks(session, l2)
            }
        }
    }

    /** Classic `downloadRequestedBooks` (`:789-800`). */
    private fun downloadRequestedBooks(session: DownloadSession, osisIds: List<SwordDocumentInfo>?) {
        osisIds ?: return
        for (it in osisIds) {
            Log.i(TAG_DOWNLOAD, "User request to download $it")
            val book: Book? = findBookByInitials(session, it.initials, if (it.repository == "") null else it.repository)
            if (book != null) {
                doDownload(session, book)
            } else {
                session.booksNotFound.add(it.initials)
            }
        }
    }

    private fun findBookByInitials(session: DownloadSession, initials: String, repository: String?): Book? =
        session.allBooks.find {
            if (repository != null) {
                it.initials == initials && it.getProperty(DownloadManager.REPOSITORY_KEY) == repository
            } else {
                it.initials == initials
            }
        }

    /**
     * Classic `warnUserBooksNotDownloaded` (`:803-821`) -- the one dialog in this cluster that
     * inflates a LAYOUT (`R.layout.books_not_downloaded_dialog`). Converting the six platform
     * dialogs in this section to Compose is a separate, queued port goal; both layouts are pinned as
     * survivors by `ClassicDocumentSelectionRemovalGuardTest`.
     */
    private fun warnUserBooksNotDownloaded(session: DownloadSession) {
        val books = session.booksNotFound.toTypedArray()
        lifecycleScope.launch {
            val notInstalled: Array<String> = books.mapNotNull { swordDocumentInfoDao.getBook(it)?.name }.toTypedArray()
            withContext(Dispatchers.Main) {
                val v = layoutInflater.inflate(R.layout.books_not_downloaded_dialog, null)
                val adapter = ArrayAdapter(this@NavHostComposeActivity, R.layout.books_not_downloaded_list_item, notInstalled)
                v.findViewById<ListView>(R.id.bookListView).adapter = adapter
                AlertDialog.Builder(this@NavHostComposeActivity)
                    .setView(v)
                    .setPositiveButton(R.string.okay, null)
                    .show()
            }
        }
    }

    /** Classic `isRepoBookListOld` (`:826-831`): not refreshed in a day means old. */
    private val isRepoBookListOld: Boolean
        get() {
            val repoRefreshDate = CommonUtils.settings.getLong(REPO_REFRESH_DATE, 0)
            return (Date().time - repoRefreshDate) / MILLISECS_IN_DAY > REPO_LIST_STALE_AFTER_DAYS
        }

    /**
     * Classic's overflow menu (`:839-876`). `AbMenuItem`, never `DropdownMenuItem`: this file is
     * scanned by `MenuSeamGuardTest`.
     */
    @Composable
    private fun DownloadOverflowMenu(navController: NavHostController, firstDownload: Boolean) {
        AbOverflowMenu(contentDescription = null) { close ->
            if (downloadSession?.hasErrors == true) {
                AbMenuItem(
                    text = getString(R.string.download_errors),
                    onClick = { close(); showDownloadErrors() },
                    icon = { Icon(painterResource(R.drawable.ic_error_outline_black_24dp), contentDescription = null) },
                )
            }
            if (!firstDownload) { // classic FirstDownload hides installZip
                AbMenuItem(
                    text = getString(R.string.install_zip),
                    onClick = { close(); onInstallZip() },
                    icon = { Icon(painterResource(R.drawable.ic_unarchive_white_24dp), contentDescription = null) },
                )
            }
            AbMenuItem(
                text = getString(R.string.custom_repositories),
                // An in-graph hop, not classic's awaitIntent(Screen.CustomRepositories): that
                // destination lives in THIS graph (Task 3), so an Intent would launch this host at
                // itself. An in-graph hop has no result to await, so classic's follow-up
                // `loadDocuments(true)` (:904) is ARMED here and performed by
                // [reloadDownloadCatalogueIfRequested] when the destination composes again.
                onClick = {
                    close()
                    pendingDownloadCatalogueReload = true
                    navController.navigate(NavRoutes.customRepositories())
                },
                // Icons.Filled.Dns (a stack of servers) rather than the "Install zip" unarchive
                // glyph this row used to share.
                icon = { Icon(Icons.Filled.Dns, contentDescription = null) },
            )
            if (DocumentSyncSettings.enabled) {
                AbMenuItem(
                    text = getString(R.string.document_sync_manage_title),
                    // An in-graph hop, not classic's awaitIntent(Screen.CloudDocuments): nav-graph
                    // slice 4 Task 8 registered CloudDocuments as a destination in THIS graph, so an
                    // Intent would launch this host at itself.
                    onClick = { close(); navController.navigate(NavRoutes.cloudDocuments()) },
                    icon = { Icon(painterResource(R.drawable.ic_syncdb_24dp), contentDescription = null) },
                )
            }
        }
    }

    /** Classic `showErrors` (`:878-890`). */
    private fun showDownloadErrors() {
        val session = downloadSession ?: return
        var message = ""
        if (session.downloadManager.failedRepos.isNotEmpty()) {
            message += getString(R.string.failed_repositories_message, session.downloadManager.failedRepos.joinToString(",\n"))
        }
        if (session.genericFileDownloader.errors.isNotEmpty()) {
            message += getString(R.string.failed_downloads_message, session.genericFileDownloader.errors.joinToString(",\n"))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.download_errors))
            .setMessage(message)
            .setPositiveButton(R.string.okay, null)
            .create().show()
    }

    /** Classic `onInstallZip` (`:891-897`): InstallZip stays an Activity (design section 6). */
    private fun onInstallZip() {
        val intent = ScreenLauncher.intentFor(this, Screen.InstallZip)
        lifecycleScope.launch {
            awaitIntent(intent)
            ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        }
    }

    private fun DocumentInstallStatus.toDocInstallStatus(): DocInstallStatus = when (this) {
        DocumentInstallStatus.INSTALLED -> DocInstallStatus.INSTALLED
        DocumentInstallStatus.NOT_INSTALLED -> DocInstallStatus.NOT_INSTALLED
        DocumentInstallStatus.BEING_INSTALLED -> DocInstallStatus.BEING_INSTALLED
        DocumentInstallStatus.UPGRADE_AVAILABLE -> DocInstallStatus.UPGRADE_AVAILABLE
        DocumentInstallStatus.ERROR_DOWNLOADING -> DocInstallStatus.ERROR_DOWNLOADING
        DocumentInstallStatus.INSTALL_CANCELLED -> DocInstallStatus.INSTALL_CANCELLED
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // CLOUD DOCUMENTS -- nav-graph slice 4, Task 8. Ported from classic
    // CloudDocumentsComposeActivity, whose line numbers the comments below cite.
    // ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * One CloudDocuments back-stack entry's host-side state: the controller the arm renders, plus
     * every piece of host scratch state whose lifetime is EXACTLY that controller's -- currently
     * just [lastPlan]. This is classic's own shape restored: a `CloudDocumentsComposeActivity`
     * instance WAS one entry, so its `controller by lazy` (`:80-100`) and its `lastPlan` field
     * (`:78`) were born and died together and neither ever needed pruning. In the nav host ONE
     * Activity hosts MANY entries, which pulled "per Activity" and "per entry" apart; this class
     * puts them back together (task-8 fix round 4).
     *
     * **Why this, and not the host-lifetime `Map<CloudDocumentsController, SyncPlan>` fix rounds 2
     * and 3 had:** a map keyed by controller is a container whose lifetime is the HOST's, so every
     * way an entry can end needs its own pruning path -- and each round closed one and left the next
     * open (confirm, then explicit dismiss, then "the destination was popped while the Sync-now
     * sheet was still up", where neither of those ever fires, round 3's one-shot
     * `syncNowDialog.first { it == null }` watcher waits forever, and the watcher itself -- a
     * suspended `lifecycleScope` coroutine holding a hard reference -- pins the very controller it
     * was meant to release). A field on a per-entry object needs no pruning path at all: when the
     * entry is abandoned the whole object, plan included, becomes garbage as soon as the arm's
     * composition and any coroutine still working for that entry let go of it.
     */
    private class CloudDocumentsEntry {
        /** Assigned once, immediately after construction, before any of its callbacks can fire. */
        lateinit var controller: CloudDocumentsController

        /**
         * Classic's `lastPlan` field (`:78`): the last resolved Sync-now plan, retained so
         * confirming it dispatches without a second resolve. Set by [cloudDocumentsShowSyncNow],
         * consumed AND cleared by [handleCloudDocumentsSyncNowConfirm], overwritten by the next
         * open. Deliberately NOT cleared on dismiss -- classic did not clear it either, the screen
         * only offers a confirm button while `syncNowDialog` is non-null (so a dismissed sheet has
         * no path to a confirm without a fresh `showSyncNow` resolving a new plan first), and the
         * value dies with this entry regardless.
         */
        var lastPlan: SyncPlan? = null
    }

    /**
     * The CURRENT entry's state -- task-8 fix round 1 (the controller), round 4 (everything else
     * that is per-entry). [buildCloudDocumentsController] REASSIGNS this every time
     * [CloudDocumentsDeps.controllerFor] is called, one per genuine composition entry, the
     * [CustomRepositoriesDeps.controllerFor] idiom rather than a `by lazy` singleton: this
     * destination has TWO distinct entry points (Download's overflow row, Settings' sync row) and no
     * covering child, so a genuine leave-and-reopen within one host session is a real, reachable
     * path, and classic reset every filter/selection/search/arrangement on each such open by getting
     * a fresh Activity and a fresh controller. A singleton would have let all of that silently
     * survive a round trip instead.
     *
     * **Read ONLY from [cloudDocumentsOpenOrGate]/[cloudDocumentsRefreshFromNetwork] (through the
     * [cloudDocumentsController] accessor below), and only ONCE, synchronously, before either
     * function's first suspension point** -- both capture it into a local `val controller`
     * immediately and use ONLY that local afterward, INCLUDING inside a `finally` block (fix round
     * 3: `finally` runs even when the surrounding coroutine is being CANCELLED, e.g. because the
     * arm's composition was just disposed by a leave-and-reopen, so a `finally {
     * cloudDocumentsController.pushBusy(false) }` that re-read the property there could clear busy
     * on a SECOND entry's controller instead of the one that set it true -- capturing once up front
     * removes the second read entirely, not just delays it). **Composable functions** get their own
     * composition-time snapshot instead (see [CloudDocumentsOverflowMenu]). Every other function
     * below that touches an entry does so through an EXPLICIT `controller: CloudDocumentsController`
     * (or `entry: CloudDocumentsEntry`) parameter, captured once at the point the operation started,
     * never by re-reading this property after a suspension point or from a callback that can fire
     * later than its origin (task-8 fix round 2, finding A) -- `lifecycleScope.launch { ... }` is
     * host-Activity-scoped, not cancelled when the arm's composition is disposed, so a coroutine
     * started by one entry (or an `AlertDialog` positive-button callback armed by one entry, which
     * can fire arbitrarily later) can easily resume or fire AFTER a leave-and-reopen has already
     * rebuilt this ref onto a second, DIFFERENT entry; reading the property at that point would
     * silently misdirect the first entry's result onto the second entry's live controller. Binding
     * every such callback to the specific entry it was built for -- the `entry` capture in
     * [buildCloudDocumentsController] -- makes that misdirection structurally impossible instead of
     * merely unlikely.
     */
    private var cloudDocumentsEntryRef: CloudDocumentsEntry? = null
    private val cloudDocumentsEntry: CloudDocumentsEntry
        get() = requireNotNull(cloudDocumentsEntryRef) {
            "CloudDocuments controller read before its destination composed"
        }
    private val cloudDocumentsController: CloudDocumentsController
        get() = cloudDocumentsEntry.controller

    /**
     * Classic's own `controller by lazy` field (`CloudDocumentsComposeActivity.kt:80-100`), ported as
     * a PER-ENTRY factory -- see [cloudDocumentsEntryRef]'s kdoc for both halves of why: the
     * per-entry rebuild itself, and why every constructor callback below is a LAMBDA closing over the
     * local `entry` (whose `controller` is assigned before any of them can fire) rather than a bare
     * method reference -- a method reference is the SAME function for every instance and cannot tell
     * "its own" entry from the ref's current one, which is exactly the bug fix round 2 closes.
     */
    private fun buildCloudDocumentsController(): CloudDocumentsController {
        val entry = CloudDocumentsEntry()
        entry.controller = CloudDocumentsController(
            syncEnabled = { DocumentSyncSettings.enabled },
            onAction = { action, initials -> handleCloudDocumentsAction(entry.controller, action, initials) },
            onBulkAction = { action, initials -> handleCloudDocumentsBulkAction(entry.controller, action, initials) },
            onSyncNow = { download, upload, delete -> handleCloudDocumentsSyncNowConfirm(entry, download, upload, delete) },
            onRescan = { cloudDocumentsRunSyncAction(entry.controller) { DocumentSync.resetListingCache() } },
            onShowRemovedChange = { show -> handleCloudDocumentsShowRemovedChange(entry.controller, show) },
            storedArrangement = if (CommonUtils.settings.getBoolean(CLOUD_ARRANGEMENT_REMEMBER_KEY, true))
                CommonUtils.settings.getString(CLOUD_ARRANGEMENT_KEY, null) else null,
            rememberArrangementInitially = CommonUtils.settings.getBoolean(CLOUD_ARRANGEMENT_REMEMBER_KEY, true),
            onArrangementChange = { encoded, remember ->
                CommonUtils.settings.setBoolean(CLOUD_ARRANGEMENT_REMEMBER_KEY, remember)
                CommonUtils.settings.setString(CLOUD_ARRANGEMENT_KEY, encoded)
            },
            scope = lifecycleScope,
        )
        cloudDocumentsEntryRef = entry
        return entry.controller
    }

    /**
     * Classic `openOrGate()` in full (`:193-208`) -- see [CloudDocumentsDeps.openOrGate]'s kdoc.
     * A single cache scan serves both the sign-in-gate's emptiness check and the seed, exactly as
     * classic's own `cached` local did.
     *
     * `controller` is captured on the FIRST LINE, ahead of the `CloudSync.signIn` and
     * [cloudDocumentsSeedItems] suspension points -- see [cloudDocumentsEntryRef]'s kdoc (fix round
     * 3). Slice 4's final-review finding M1: the capture used to sit BELOW both of them, which was
     * still safe (the caller is the arm's own `LaunchedEffect`, so a disposal cancels this
     * coroutine and neither call below runs at all) but did not match what this kdoc claimed, and
     * the guard protecting the invariant -- `CloudDocumentsControllerRebuildIsolationTest` --
     * counted occurrences without checking position, so it could not have caught the drift its own
     * message described. The capture is now genuinely first, and that guard now asserts the position
     * as well as the count.
     */
    private suspend fun cloudDocumentsOpenOrGate(): Boolean {
        val controller = cloudDocumentsController
        var signedIn = CloudSync.signedIn
        if (!signedIn) signedIn = CloudSync.signIn(this@NavHostComposeActivity) == true
        val items = cloudDocumentsSeedItems()
        if (!signedIn && items.isEmpty()) {
            Toast.makeText(this, R.string.document_sync_signin_required, Toast.LENGTH_LONG).show()
            return false
        }
        controller.setShowRemoved(DocumentSyncSettings.showRemovedDocuments)
        controller.setItems(items)
        if (signedIn && (!DocumentSyncSettings.enabled || items.isEmpty())) cloudDocumentsRefreshFromNetwork()
        return true
    }

    /**
     * Classic's shared cache-only scan+flatten, the middle of both `openOrGate()` (`:198`, `:205`)
     * and `renderFromCache()` (`:229`) -- see [CloudDocumentsDeps.seedItems]'s kdoc for why this
     * stays PURE (no controller mutation): it is reached from [handleCloudDocumentsShowRemovedChange],
     * itself reached from [CloudDocumentsController.setShowRemoved], which calls its
     * `onShowRemovedChange` callback UNCONDITIONALLY on every invocation -- a `setShowRemoved` call
     * inside this function would recurse through that callback forever.
     */
    private suspend fun cloudDocumentsSeedItems(): List<CloudDocItem> = withContext(Dispatchers.IO) {
        DocumentSync.scanCached(includeDeleted = DocumentSyncSettings.showRemovedDocuments).map { it.toCloudDocItem() }
    }

    /**
     * Classic `refreshFromNetwork()` (`:212-218`): a network scan, flattened and pushed with a busy
     * pair around it. `controller` is captured ONCE, before the `finally` even exists to run --
     * fix round 3: the property was read a second time inside `finally { cloudDocumentsController
     * .pushBusy(false) }`, and `finally` blocks run even when this coroutine is CANCELLED (e.g. a
     * leave-and-reopen disposed the arm's composition while the scan above was suspended), so that
     * second read could resolve to a SECOND entry's controller and clear ITS busy flag instead of
     * this call's own. Capturing once removes the second read entirely.
     */
    private suspend fun cloudDocumentsRefreshFromNetwork() {
        val controller = cloudDocumentsController
        controller.pushBusy(true)
        try {
            val items = withContext(Dispatchers.IO) {
                DocumentSync.scan(includeDeleted = DocumentSyncSettings.showRemovedDocuments).map { it.toCloudDocItem() }
            }
            controller.setItems(items)
        } finally {
            controller.pushBusy(false)
        }
    }

    /**
     * Classic's `renderFromCache()` (`:226-232`), reached only from
     * [CloudDocumentsController.setShowRemoved]'s `onShowRemovedChange` callback -- persist the
     * setting, then re-seed from the cache with the new `includeDeleted` value. [controller] is the
     * SPECIFIC instance [buildCloudDocumentsController] bound this callback to, not
     * [cloudDocumentsController]'s current value -- see that property's kdoc (fix round 2, finding A):
     * this launches on `lifecycleScope`, which outlives the arm's composition, so by the time it
     * resumes past the suspending seed a leave-and-reopen may already have rebuilt the ref onto a
     * different controller.
     */
    private fun handleCloudDocumentsShowRemovedChange(controller: CloudDocumentsController, show: Boolean) {
        DocumentSyncSettings.showRemovedDocuments = show
        lifecycleScope.launch {
            controller.pushBusy(true)
            try {
                controller.setItems(cloudDocumentsSeedItems())
            } finally {
                controller.pushBusy(false)
            }
        }
    }

    /**
     * Classic `bridge.register()`/`unregister()` plus the `bridge.running.drop(1).collect { ... }`
     * body (`:105`, `:180`, `:111-116`) as one subscribe/stop pair -- see
     * [CloudDocumentsDeps.subscribeProgress]'s kdoc. A fresh [CloudSyncProgressBridge] per call,
     * mirroring classic's per-Activity-instance field: this destination can be entered and left
     * multiple times within one host instance, and each entry needs its own register/unregister
     * pair, not a single one shared for the host's whole lifetime.
     */
    private fun cloudDocumentsSubscribeProgress(onRunning: (Boolean) -> Unit): () -> Unit {
        val bridge = CloudSyncProgressBridge()
        bridge.register()
        val job = lifecycleScope.launch {
            bridge.running.drop(1).collect { running -> onRunning(running) }
        }
        return {
            job.cancel()
            bridge.unregister()
        }
    }

    /**
     * Classic `runSyncAction` (`:234-241`), used by [buildCloudDocumentsController]'s `onRescan`.
     * [controller] is the SPECIFIC instance the callback was bound to -- see
     * [cloudDocumentsEntryRef]'s kdoc (fix round 2, finding A); this also launches on
     * `lifecycleScope`, so the same "may resume after a leave-and-reopen" hazard applies.
     */
    private fun cloudDocumentsRunSyncAction(controller: CloudDocumentsController, block: suspend () -> Unit): kotlinx.coroutines.Job = lifecycleScope.launch {
        controller.pushBusy(true)
        try {
            withContext(Dispatchers.IO) { block() }
            val items = withContext(Dispatchers.IO) {
                DocumentSync.scan(includeDeleted = DocumentSyncSettings.showRemovedDocuments).map { it.toCloudDocItem() }
            }
            controller.setItems(items)
        } finally {
            controller.pushBusy(false)
        }
    }

    /**
     * Classic `handleAction` (`:258-268`). [controller] is the instance
     * [buildCloudDocumentsController] bound `onAction` to; [cloudDocumentsConfirmRemove]/
     * [cloudDocumentsConfirmPurge]'s `onConfirm` closures below capture it too, so an `AlertDialog`
     * positive-button tap -- which can fire arbitrarily later than this call, long past any
     * leave-and-reopen -- still lands on the RIGHT controller (fix round 2, finding A).
     */
    private fun handleCloudDocumentsAction(controller: CloudDocumentsController, action: CloudDocAction, initials: String) {
        val item = controller.items.value.firstOrNull { it.initials == initials } ?: return
        when (action) {
            CloudDocAction.DOWNLOAD -> DocumentSyncService.start(this, emptyList(), listOf(initials))
            CloudDocAction.PUSH, CloudDocAction.RESTORE -> DocumentSyncService.start(this, listOf(initials), emptyList())
            CloudDocAction.BLOCK -> {
                DocumentSyncSettings.blockList.block(initials)
                controller.setBlocked(initials, true)
            }
            CloudDocAction.UNBLOCK -> {
                DocumentSyncSettings.blockList.unblock(initials)
                controller.setBlocked(initials, false)
            }
            CloudDocAction.REMOVE_CLOUD -> cloudDocumentsConfirmRemove(listOf(initials), item.name) {
                DocumentSyncService.start(this, emptyList(), emptyList(), removeInitials = listOf(initials))
                controller.applyRemoval(initials)
                controller.clearSelection()
            }
            CloudDocAction.PURGE -> cloudDocumentsConfirmPurge(listOf(initials), item.name) {
                DocumentSyncService.start(this, emptyList(), emptyList(), purgeInitials = listOf(initials))
                controller.applyPurge(initials)
                controller.clearSelection()
            }
        }
    }

    /** Classic `handleBulkAction` (`:270-279`) -- [controller] as [handleCloudDocumentsAction]'s own kdoc explains. */
    private fun handleCloudDocumentsBulkAction(controller: CloudDocumentsController, action: CloudDocAction, initials: List<String>) {
        when (action) {
            CloudDocAction.DOWNLOAD -> {
                DocumentSyncService.start(this, emptyList(), initials)
                controller.clearSelection()
            }
            CloudDocAction.PUSH, CloudDocAction.RESTORE -> {
                DocumentSyncService.start(this, initials, emptyList())
                controller.clearSelection()
            }
            CloudDocAction.BLOCK -> {
                initials.forEach { DocumentSyncSettings.blockList.block(it); controller.setBlocked(it, true) }
                controller.clearSelection()
            }
            CloudDocAction.UNBLOCK -> {
                initials.forEach { DocumentSyncSettings.blockList.unblock(it); controller.setBlocked(it, false) }
                controller.clearSelection()
            }
            CloudDocAction.REMOVE_CLOUD -> cloudDocumentsConfirmRemove(initials, null) {
                DocumentSyncService.start(this, emptyList(), emptyList(), removeInitials = initials)
                initials.forEach { controller.applyRemoval(it) }
                controller.clearSelection()
            }
            CloudDocAction.PURGE -> cloudDocumentsConfirmPurge(initials, null) {
                DocumentSyncService.start(this, emptyList(), emptyList(), purgeInitials = initials)
                initials.forEach { controller.applyPurge(it) }
                controller.clearSelection()
            }
        }
    }

    /**
     * Classic `confirmRemove` (`:281-293`) -- one of the batch's four plural call sites (design
     * §2.4 counted three). Owns both the platform `AlertDialog` and the plural it resolves;
     * [onConfirm] is what the positive button did inline, supplied by whichever caller is asking.
     */
    private fun cloudDocumentsConfirmRemove(initials: List<String>, name: String?, onConfirm: () -> Unit) {
        val enabled = DocumentSyncSettings.enabled
        val title = if (enabled) R.string.cloud_doc_action_remove_all_devices else R.string.cloud_doc_action_remove_cloud
        val message = if (name != null) {
            getString(if (enabled) R.string.cloud_doc_remove_all_confirm else R.string.cloud_doc_remove_cloud_confirm, name)
        } else {
            resources.getQuantityString(
                if (enabled) R.plurals.cloud_doc_bulk_remove_all_confirm else R.plurals.cloud_doc_bulk_remove_cloud_confirm,
                initials.size, initials.size,
            )
        }
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** Classic `confirmPurge` (`:295-305`) -- the second of the batch's four plural call sites. */
    private fun cloudDocumentsConfirmPurge(initials: List<String>, name: String?, onConfirm: () -> Unit) {
        val message = if (name != null) {
            getString(R.string.cloud_doc_purge_confirm, name)
        } else {
            resources.getQuantityString(R.plurals.cloud_doc_bulk_purge_confirm, initials.size, initials.size)
        }
        AlertDialog.Builder(this).setTitle(R.string.cloud_doc_action_purge).setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /**
     * Classic `showSyncNow` (`:308-324`), triggered from [CloudDocumentsOverflowMenu]'s "Sync now"
     * row. [entry] is the OverflowMenu's own composition-time snapshot (see that composable's
     * kdoc), not [cloudDocumentsEntry]'s current value -- this launches on `lifecycleScope` and
     * awaits a network round trip, so a leave-and-reopen could otherwise land the resolved plan and
     * dialog on the WRONG (second) entry's controller (fix round 2, finding A).
     *
     * The resolved plan is parked on THAT entry ([CloudDocumentsEntry.lastPlan]), exactly where
     * classic parked it (a field of the one Activity that was the one entry), so it needs no
     * cleanup of its own on any of the ways this sheet can end -- confirm, cancel, or the
     * destination being popped with the sheet still up (task-8 fix round 4; see
     * [cloudDocumentsEntryRef]'s kdoc for the map-shaped predecessor this replaced and why).
     */
    private fun cloudDocumentsShowSyncNow(entry: CloudDocumentsEntry) {
        val controller = entry.controller
        lifecycleScope.launch {
            controller.pushBusy(true)
            val plan = try {
                withContext(Dispatchers.IO) { DocumentSync.computeSyncPlan(download = true, upload = true, delete = true) }
            } catch (e: Exception) {
                Toast.makeText(this@NavHostComposeActivity, R.string.sync_error, Toast.LENGTH_SHORT).show()
                return@launch
            } finally {
                controller.pushBusy(false)
            }
            cloudDocumentsPresentSyncNow(entry, plan)
        }
    }

    /**
     * The tail of classic's `showSyncNow` (`:316-324`): park the resolved plan on the entry it was
     * resolved FOR, label it, and put the sheet up. Split out of [cloudDocumentsShowSyncNow] so that
     * parking decision -- the whole subject of task-8 fix round 4 -- is reachable without the IO
     * round trip that resolves the plan; `CloudDocumentsControllerRebuildIsolationTest` drives this
     * directly rather than writing [CloudDocumentsEntry.lastPlan] reflectively, so its "an abandoned
     * entry pins nothing on the host" assertion is made against the REAL parking path.
     */
    private fun cloudDocumentsPresentSyncNow(entry: CloudDocumentsEntry, plan: SyncPlan) {
        entry.lastPlan = plan
        val labels = listOf(
            getString(R.string.cloud_doc_sync_now_download) + "\n" +
                cloudDocumentsCountLabel(plan.toDownload.size, plan.downloadBytes),
            getString(R.string.cloud_doc_sync_now_upload) + "\n" +
                cloudDocumentsCountLabel(plan.toUpload.size, plan.uploadBytes),
            getString(R.string.cloud_doc_sync_now_delete) + "\n" +
                cloudDocumentsCountLabel(plan.toUninstall.size, null),
        )
        val checked = listOf(
            DocumentSyncSettings.syncNowDownload, DocumentSyncSettings.syncNowUpload, DocumentSyncSettings.syncNowDelete,
        )
        entry.controller.showSyncNow(labels, checked)
    }

    /**
     * Classic `handleSyncNowConfirm` (`:326-337`). [entry] is the one
     * [buildCloudDocumentsController] bound `onSyncNow` to, and the resolved plan is read (and
     * cleared) from THAT entry's own [CloudDocumentsEntry.lastPlan], so a plan resolved for one
     * entry can never answer another entry's confirm.
     */
    private fun handleCloudDocumentsSyncNowConfirm(entry: CloudDocumentsEntry, download: Boolean, upload: Boolean, delete: Boolean) {
        val plan = entry.lastPlan ?: return
        entry.lastPlan = null
        DocumentSyncSettings.syncNowDownload = download
        DocumentSyncSettings.syncNowUpload = upload
        DocumentSyncSettings.syncNowDelete = delete
        DocumentSyncService.start(
            this,
            pushInitials = if (upload) plan.toUpload else emptyList(),
            downloadInitials = if (download) plan.toDownload else emptyList(),
            uninstallInitials = if (delete) plan.toUninstall else emptyList(),
        )
    }

    /** Classic `countLabel` (`:339-343`) -- the third/fourth of the batch's four plural call sites. */
    private fun cloudDocumentsCountLabel(count: Int, bytes: Long?): String = when {
        count == 0 -> getString(R.string.cloud_doc_sync_now_count_none)
        bytes != null && bytes > 0 ->
            resources.getQuantityString(R.plurals.cloud_doc_sync_now_count_size, count, count, Formatter.formatShortFileSize(this, bytes))
        else -> resources.getQuantityString(R.plurals.cloud_doc_sync_now_count, count, count)
    }

    /**
     * Classic `OverflowMenu()` (`:346-375`) -- the `topBarActions` slot, host-side because every row
     * is host work (`R.drawable` icons, `CloudSync.signedIn`, `CommonUtils.showHelpDialog`).
     * `AbMenuItem`, never `DropdownMenuItem`: this file is scanned by `MenuSeamGuardTest`.
     *
     * The [CloudDocumentsEntry] is read ONCE, as a composition-time snapshot of
     * [cloudDocumentsEntry] -- this composable recomposes fresh for every genuine entry (it is
     * nested inside the arm's own composition via the `topBarActions` slot), so the snapshot always
     * names the CURRENT entry. Its `onClick` rows close over that local val, not the live property,
     * so a tap (which can fire after the menu has been open for a moment) still targets the entry it
     * was opened from rather than whatever the ref happens to hold at tap time (fix round 2,
     * finding A) -- and "Sync now" parks its resolved plan on that same snapshot, so the plan cannot
     * outlive the entry it was resolved for (fix round 4).
     */
    @Composable
    private fun CloudDocumentsOverflowMenu() {
        val entry = cloudDocumentsEntry
        AbOverflowMenu(contentDescription = null) { close ->
            if (CloudSync.signedIn) {
                AbMenuItem(
                    text = getString(R.string.cloud_doc_sync_now),
                    onClick = { close(); cloudDocumentsShowSyncNow(entry) },
                    icon = { Icon(painterResource(R.drawable.ic_sync_white_24dp), contentDescription = null) },
                )
                AbMenuItem(
                    text = getString(R.string.cloud_doc_rescan),
                    onClick = { close(); entry.controller.rescan() },
                    icon = { Icon(painterResource(R.drawable.ic_baseline_refresh_24), contentDescription = null) },
                )
            }
            AbMenuItem(
                text = getString(R.string.help),
                onClick = {
                    close()
                    CommonUtils.showHelpDialog(
                        activity = this@NavHostComposeActivity,
                        titleResId = R.string.help,
                        messageResId = R.string.help_document_sync_text,
                        helpPath = "document_sync.html",
                    )
                },
                icon = { Icon(painterResource(R.drawable.ic_help_white_24dp), contentDescription = null) },
            )
        }
    }

    /** Classic's view-data flatten (`:378-385`). */
    private fun DocumentSync.DocumentStatusItem.toCloudDocItem(): CloudDocItem = CloudDocItem(
        initials = initials, name = name, category = category?.let { docCategoryOf(it) },
        cloudVersion = cloudVersion, localVersion = localVersion,
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = updateAvailable, localNewer = localNewer,
        blocked = blocked, canDeleteLocal = canDeleteLocal, cloudDeleted = cloudDeleted,
        sizeLabel = if (sizeBytes > 0) Formatter.formatShortFileSize(this@NavHostComposeActivity, sizeBytes) else null,
        sizeBytes = sizeBytes.takeIf { it > 0 },
    )

    /** Classic `statusFilterLabels` (`:387-396`), `getString` only -- see [CloudDocumentsDeps.statusFilterLabels]'s kdoc. */
    private fun cloudDocumentsStatusFilterLabels(showRemoved: Boolean): List<String> = buildList {
        add(getString(R.string.cloud_doc_filter_all))
        add(getString(R.string.cloud_doc_filter_installed))
        add(getString(R.string.cloud_doc_filter_cloud))
        add(getString(R.string.cloud_doc_filter_updates))
        add(getString(R.string.cloud_doc_filter_blocked))
        add(getString(R.string.cloud_doc_filter_device_only))
        add(getString(R.string.cloud_doc_filter_cloud_only))
        if (showRemoved) add(getString(R.string.cloud_doc_filter_removed))
    }

    /** Classic `categoryFilterLabels` (`:398-406`), `getString` only. */
    private fun cloudDocumentsCategoryFilterLabels(): List<String> = listOf(
        getString(R.string.doc_type_all),
        getString(R.string.doc_type_bible),
        getString(R.string.doc_type_commentary),
        getString(R.string.doc_type_dictionary),
        getString(R.string.doc_type_book),
        getString(R.string.doc_type_map),
        getString(R.string.doc_type_addons),
    )

    // --- Chooser host baggage (nav-graph slice 7, Task 4) ----------------------------------------
    // Ported from the five classic chooser Activities: ChooseGeneralBookKeyComposeActivity,
    // ChooseMapKeyComposeActivity, ChooseDictionaryWordComposeActivity,
    // GridChoosePassageComposeActivity and ChooseDocumentComposeActivity. Every one of them is STILL
    // IN THE TREE and still reachable through its own `Screen.X` arm -- the phase's standing rule is
    // that no Activity is deleted before Task 13 -- so each of these seams has two copies for now:
    // the Activity's own, and this one behind the graph's deps. Task 13 deletes the Activity half.
    //
    // None of it can cross into `commonMain`: every line names a JSword type, an Android dialog, an
    // `R.string` or a `settings` key.

    // ——— The chooser cluster's three result channels ————————————————————————————————————————————
    // THREE channels for FIVE destinations (design §6.2) -- see `ChooserNavDeps`' own kdoc: the three
    // key choosers already produce an identical payload consumed by a single arm.
    //
    // All three `exitWithResult` lambdas are a hard `error(...)`, which in this file only
    // [repositoryEditorResults] does otherwise. Design §1.1 is the reason and it is stronger here
    // than it was there: slice 7 migrates the producing destinations TOGETHER WITH their consumer
    // (the reading view, Task 9), so every one of these results is delivered in-graph by
    // construction and slice 2's host-side `exitWithResult` shim is deliberately not used for any of
    // them. If one of these ever runs, a destination has been given an external entry without being
    // given a result contract; fail loudly rather than pack an Intent nobody defined.

    private val keyChooserResults = NavResultChannel<KeyChooserResult> {
        error("slice 7 destinations are only entered in-graph")
    }

    private val passageResults = NavResultChannel<PassageResult> {
        error("slice 7 destinations are only entered in-graph")
    }

    private val documentResults = NavResultChannel<DocumentResult> {
        error("slice 7 destinations are only entered in-graph")
    }

    // ——— ChooseGeneralBookKey ————————————————————————————————————————————————————————————————————

    /** Classic `ChooseGeneralBookKeyComposeActivity.page` (`:43-44`) -- a GETTER, not a captured
     *  value, so every read sees the currently active window's general book exactly as classic did. */
    private val chooseGeneralBookPage get() = windowControl.activeWindowPageManager.currentGeneralBook

    /**
     * Classic `buildResult` (`:49-61`) with the `Intent` removed: the same two shapes, the same
     * fallback (`doc!!.globalKeyList.first()` when no key was chosen, double-bang included -- it is
     * classic's, and this task is a port, not a fix), minus the `ActivityResultKind.GenBookKey` tag,
     * which named the INTENT's shape to `MainBibleActivity`'s dispatcher and has no meaning in-graph.
     */
    private fun generalBookKeyResult(key: Key?): KeyChooserResult {
        val doc = chooseGeneralBookPage.currentDocument
        return if (key is BookAndKey) {
            KeyChooserResult(bookAndKeyJson = key.serialized)
        } else {
            KeyChooserResult(
                key = key?.osisRef ?: doc!!.globalKeyList.first().osisRef,
                book = doc?.initials,
            )
        }
    }

    /**
     * Null exactly where classic's `onCreate` returned early on an EMPTY key list (`:77-83`); the
     * arm then delivers [generalBookKeyResult] with a null key, which is classic's own fallback.
     *
     * The key list is a LOCAL closed over by the controller this call returns, never a host field,
     * for the reason [ChooseDocumentSession] exists: the destination is per-entry, so two entries of
     * this route alive at once (one pushed over the other, or one paused under a child) would share
     * a single host field and the popped-back controller would resolve its `KeyRow.keyId` -- a plain
     * INDEX, see `KeyRow.keyId` -- against the other entry's list and publish the wrong `osisRef`.
     * A local cannot be cross-wired: each controller keeps the list that was resolved when IT was
     * built.
     */
    private fun chooseGeneralBookKeyControllerFor(
        onResult: (KeyChooserResult) -> Unit,
    ): ChooseGeneralBookKeyController? {
        val keys = chooseGeneralBookPage.keyChooserKeys()
        if (keys.isEmpty()) return null
        return ChooseGeneralBookKeyController(
            loadRows = {
                keys.mapIndexed { i, k -> KeyRow(i.toString(), k.nameWithoutDocument) }
            },
            currentRow = {
                chooseGeneralBookPage.key?.let { cur ->
                    keys.indexOf(cur).takeIf { it >= 0 }?.toString()
                }
            },
            onSelect = { keyId ->
                onResult(generalBookKeyResult(keys.getOrNull(keyId.toIntOrNull() ?: -1)))
            },
        )
    }

    // ——— ChooseMapKey ————————————————————————————————————————————————————————————————————————————

    /** Classic `ChooseMapKeyComposeActivity.page` (`:42-43`). */
    private val chooseMapPage get() = windowControl.activeWindowPageManager.currentMap

    /** Classic `buildResult` (`:47-53`): `key`+`book` unconditionally -- the map chooser never
     *  produces a `bookAndKey`, unlike [generalBookKeyResult]. */
    private fun mapKeyResult(key: Key?): KeyChooserResult = KeyChooserResult(
        key = key?.osisRef,
        book = chooseMapPage.currentDocument?.initials,
    )

    /** Null on an empty key list, classic `:69-74` -- see [chooseGeneralBookKeyControllerFor], whose
     *  kdoc also covers why the key list is a local of this call rather than a host field. */
    private fun chooseMapKeyControllerFor(onResult: (KeyChooserResult) -> Unit): ChooseMapKeyController? {
        val keys = chooseMapPage.keyChooserKeys()
        if (keys.isEmpty()) return null
        return ChooseMapKeyController(
            loadRows = { keys.mapIndexed { i, k -> KeyRow(i.toString(), k.nameWithoutDocument) } },
            currentRow = {
                chooseMapPage.key?.let { cur -> keys.indexOf(cur).takeIf { it >= 0 }?.toString() }
            },
            onSelect = { keyId ->
                onResult(mapKeyResult(keys.getOrNull(keyId.toIntOrNull() ?: -1)))
            },
        )
    }

    // ——— ChooseDictionaryWord ————————————————————————————————————————————————————————————————————

    /** Classic `ChooseDictionaryWordComposeActivity.page` (`:49`). */
    private val chooseDictionaryPage get() = windowControl.activeWindowPageManager.currentDictionary

    /**
     * One dictionary-chooser ENTRY's baggage: the global key list [loadChooseDictionaryRows] resolves
     * off-main, whose index is the `DictRow.keyId` (classic `:51-52`).
     *
     * A session rather than three host fields, for [ChooseDocumentSession]'s reason: the destination
     * is per-entry, so a host field shared by two live entries would let a popped-back controller
     * resolve its `keyId` -- a plain INDEX -- against the other entry's list and publish the wrong
     * `osisRef`. The two key choosers above close their list over the controller instead; this one
     * cannot, because [loadChooseDictionaryRows] WRITES the list and [chooseDictionarySnippet] reads
     * it, and both are deps slots with frozen signatures that carry no controller.
     */
    private class ChooseDictionarySession {
        var keys: List<Key> = emptyList()
    }

    /** The CURRENT entry's session, the `ChooseDocument`/`CloudDocuments` idiom: reassigned by
     *  [chooseDictionaryWordControllerFor], read by the two seams below. */
    private var chooseDictionarySession: ChooseDictionarySession? = null

    /** Null where classic finished immediately with NO result -- `page.currentDocument == null`
     *  (`:72`). Unlike the two key choosers above there is no fallback key to hand back. */
    private fun chooseDictionaryWordControllerFor(
        onResult: (KeyChooserResult) -> Unit,
    ): ChooseDictionaryWordController? {
        if (chooseDictionaryPage.currentDocument == null) return null
        val session = ChooseDictionarySession()
        chooseDictionarySession = session
        return ChooseDictionaryWordController(
            onSelect = { keyId ->
                val key = session.keys.getOrNull(keyId.toIntOrNull() ?: -1)
                    ?: return@ChooseDictionaryWordController
                // Classic `:58-63`: the dictionary shares ChooseGeneralBookKey's result shape.
                onResult(
                    KeyChooserResult(
                        key = key.osisRef,
                        book = chooseDictionaryPage.currentDocument?.initials,
                    ),
                )
            },
        )
    }

    /**
     * Classic's `lifecycleScope.launch { withContext(Dispatchers.IO) { page.cachedGlobalKeyList } }`
     * (`:77-85`). NULL means the load failed -- the `Log.e` is classic's, and it stays here because
     * `commonMain` has no logger; the arm turns a null into the `controller.showError()` classic's
     * own `catch` called.
     */
    private suspend fun loadChooseDictionaryRows(): List<DictRow>? {
        // No session means no controller was built for this entry, i.e. the arm never reached its
        // load effect -- unreachable by construction, and a null here is the arm's "failed" signal.
        val session = chooseDictionarySession ?: return null
        return try {
            session.keys = withContext(Dispatchers.IO) {
                chooseDictionaryPage.cachedGlobalKeyList ?: emptyList()
            }
            session.keys.mapIndexed { i, k -> DictRow(i.toString(), k.name) }
        } catch (e: CancellationException) {
            // Classic ran this in `lifecycleScope`, where leaving the screen simply cancelled the
            // job. In-graph it runs in the arm's `LaunchedEffect`, whose job is cancelled on every
            // navigate-away -- and `CancellationException` IS an `Exception`, so without this
            // rethrow the generic catch below would swallow the cancellation, log a bogus error and
            // hand the arm a null, which is its "the load FAILED" signal and calls the
            // NON-suspending `controller.showError()`. That runs even in a cancelled coroutine.
            throw e
        } catch (e: Exception) {
            Log.e(TAG_CHOOSERS, "Error creating dictionary key list", e)
            null
        }
    }

    /** Classic `snippetFor` (`:110-116`) plus its `withContext(Dispatchers.IO)` call-site wrapper
     *  (`:100`): JSword `readOsisFragment` and jdom2 walking, neither of which can leave `:app`. */
    private suspend fun chooseDictionarySnippet(keyId: String): String = withContext(Dispatchers.IO) {
        val keys = chooseDictionarySession?.keys ?: return@withContext ""
        val key = keys.getOrNull(keyId.toIntOrNull() ?: -1) ?: return@withContext ""
        val book: Book = chooseDictionaryPage.currentDocument ?: return@withContext ""
        val text = try { readOsisFragment(book, key) } catch (e: OsisError) { e.xml }
        dictionaryEntrySnippet(text, key.toString())
    }

    /** Classic `getEntrySnippet` (`:118-124`). */
    private fun dictionaryEntrySnippet(text: Element, key: String): String {
        text.removeChild("title")
        val entry = text.getChild("entryFree") ?: return cleanUpDictionarySnippet(text.value, key)
        val greekOrHebrew = entry.getChildren("orth")?.map { it.text }?.filter { it != "" }?.joinToString(" - ") ?: ""
        if (greekOrHebrew != "") return greekOrHebrew
        return cleanUpDictionarySnippet(entry.value, key)
    }

    /** Classic `cleanUpSnippet` (`:126-130`). */
    private fun cleanUpDictionarySnippet(snippet: String, key: String): String {
        var noNewLines = snippet.replace('\n', ' ')
        if (noNewLines.startsWith(key)) noNewLines = noNewLines.substring(key.length)
        return maxDictionaryLettersWholeWords(noNewLines)
    }

    /** Classic `maxLettersWholeWords` (`:132-138`). */
    private fun maxDictionaryLettersWholeWords(text: String, max: Int = 50): String {
        val words = text.split(' ').toMutableList()
        var result = ""
        while (result.length < max && words.isNotEmpty()) { result += words[0] + ' '; words.removeAt(0) }
        val append = if (words.isNotEmpty()) "..." else ""
        return "$result$append"
    }

    // ——— GridChoosePassage ———————————————————————————————————————————————————————————————————————

    /**
     * Classic `GridChoosePassageComposeActivity.onCreate` (`:52-82`) in one factory. The two step
     * fields classic held as Activity state (`selectedBookNo`, `selectedChapter`, `:47-48`) are local
     * `var`s of this call, so they belong to the entry that asked for the controller rather than to
     * the host -- which is what makes a second, later entry start from a clean step state the way a
     * fresh Activity did.
     *
     * **`navigateToVerse` is read from the PREF, not from an argument** (design §6.1.1). Classic read
     * `intent.getBooleanExtra("navigateToVerse", CommonUtils.settings.getBoolean("navigate_to_verse_pref",
     * false))` (`:56`); the extra's single producer (`BibleJavascriptInterface.refChooserDialog`)
     * moved to the ref-chooser sheet in Task 10, so what survives is exactly the fallback both
     * remaining callers already got. That extra now has NO producer anywhere in `app/src/main`.
     *
     * Task 10 re-homed the JS caller's two forcings onto the quick sheet: `navigateToVerse = true`
     * became a field of `ReadingQuickSheet.KeyChooser`, and `isScripture = true` needed nothing at
     * all -- the sheet's Grid arm already hard-codes it. So as of Task 10 the two `CurrentPage`
     * classes ARE `isScripture`'s only remaining producers; the warning this paragraph used to
     * carry (that a third one was still out there) no longer applies.
     *
     * The `"title"` extra classic also read (`:55`) is deliberately dropped: it has no producer
     * anywhere in the tree (design §6.1, re-verified against test sources for this task), so the base
     * title is unconditionally `R.string.choosePassageBookName`.
     */
    private fun gridChoosePassageControllerFor(
        isScripture: Boolean,
        onResult: (PassageResult) -> Unit,
    ): GridChoosePassageController {
        val navigateToVerse = CommonUtils.settings.getBoolean("navigate_to_verse_pref", false)
        var selectedBookNo = 0
        var selectedChapter = 1
        val baseTitle = getString(R.string.choosePassageBookName)
        val workspaceName = SharedActivityState.currentWorkspaceName
        return GridChoosePassageController(
            initialOptions = initialGridOptions(navigationControl, isScripture),
            buildStep = { step, opts ->
                buildGridStep(
                    step, opts, baseTitle, workspaceName, selectedBookNo, selectedChapter,
                    navigationControl, windowControl,
                )
            },
            onPersistOptions = { persistGridOptions(it, navigationControl) },
            onPickBook = { bookNo ->
                selectedBookNo = bookNo
                pickGridBook(bookNo, navigateToVerse, navigationControl) { selectedChapter = it }
            },
            onPickChapter = { chapter ->
                selectedChapter = chapter
                pickGridChapter(chapter, selectedBookNo, navigateToVerse, navigationControl, windowControl)
            },
            onPickVerse = { verse ->
                Verse(
                    navigationControl.versification,
                    BibleBook.values()[selectedBookNo],
                    selectedChapter,
                    verse,
                ).osisID
            },
            // Classic `finishWithVerse` (`:101-107`) with the Intent removed.
            onFinish = { osisId -> onResult(PassageResult(verse = osisId)) },
        )
    }

    // ——— ChooseDocument ——————————————————————————————————————————————————————————————————————————

    /**
     * Classic `ChooseDocumentComposeActivity`'s `booksById` field (`:89-90`) plus the controller it is
     * rebuilt alongside. A SESSION per entry, not a host-memoised one (see `ChooseDocumentDeps
     * .controllerFor`): the picker is opened over and over from the reading view and classic gave
     * every open a fresh Activity, so a memoised controller would carry the previous open's filter,
     * search and selection into the next.
     */
    private class ChooseDocumentSession {
        lateinit var controller: DocumentSelectionController

        /** docId (`Book.initials`) -> Book, rebuilt on every (re)load. */
        var booksById: Map<String, Book> = emptyMap()
    }

    /** The CURRENT entry's session. Reassigned by [chooseDocumentControllerFor], read by every seam
     *  below -- the `CloudDocuments` idiom for a per-entry controller whose host-side seams have
     *  frozen signatures of their own. */
    private var chooseDocumentSession: ChooseDocumentSession? = null

    /** Classic's controller construction (`:92-124`), with `onSelect` routed into the graph's channel
     *  instead of `setResult`+`finish`. */
    private fun chooseDocumentControllerFor(
        initialTypeFilter: DocTypeFilter,
        onResult: (DocumentResult) -> Unit,
    ): DocumentSelectionController {
        val session = ChooseDocumentSession()
        session.controller = DocumentSelectionController(
            // Classic ChooseDocument.sortLanguages: alphabetical by display name.
            langComparator = compareBy { it.displayName },
            onSelect = { docId -> handleChooseDocumentSelection(docId, onResult) },
            onDelete = { ids -> handleChooseDocumentDelete(ids) },
            onDeleteIndex = { ids -> handleChooseDocumentDeleteIndex(ids) },
            onAbout = { docId -> handleChooseDocumentAbout(docId) },
            onUnlock = { docId -> handleChooseDocumentUnlock(docId) },
            onStickyLanguage = { lang ->
                // Classic `:101-106`: a sticky record like classic's lastSelectedLanguage, not read
                // back on launch here (this screen starts with no language filter).
                CommonUtils.settings.setString("selected_language_code", lang?.code)
            },
            // No SIZE and no RECOMMENDED: this screen has neither datum (classic `:107-112`).
            applicableSortKeys = setOf(
                DocSortKey.STATUS, DocSortKey.TYPE, DocSortKey.NAME,
                DocSortKey.LANGUAGE, DocSortKey.REPOSITORY,
            ),
            applicableGroupKeys = listOf(
                DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.LANGUAGE, DocGroupBy.REPOSITORY,
            ),
            storedArrangement = if (CommonUtils.settings.getBoolean(CHOOSE_DOC_ARRANGEMENT_REMEMBER_KEY, true))
                CommonUtils.settings.getString(CHOOSE_DOC_ARRANGEMENT_KEY, null) else null,
            rememberArrangementInitially = CommonUtils.settings.getBoolean(CHOOSE_DOC_ARRANGEMENT_REMEMBER_KEY, true),
            onArrangementChange = { encoded, remember ->
                CommonUtils.settings.setBoolean(CHOOSE_DOC_ARRANGEMENT_REMEMBER_KEY, remember)
                CommonUtils.settings.setString(CHOOSE_DOC_ARRANGEMENT_KEY, encoded)
            },
            scope = lifecycleScope,
        )
        chooseDocumentSession = session
        // Classic `onCreate:130`, immediately after construction.
        session.controller.setTypeFilter(initialTypeFilter)
        return session.controller
    }

    /**
     * Classic `initialTypeFilter()` (`:425-432`) minus its `addons` branch: nothing in the tree puts
     * an `addons` extra on an Intent any more (design §6.1), and
     * `ClassicDocumentSelectionRemovalGuardTest.noCallSiteStillPutsADownloadExtraOnAnIntent` already
     * asserts as much -- by a plain CONTAINMENT scan over every shipping source, which is why this
     * comment spells the extra's name out rather than quoting the `putExtra` call it forbids. The
     * branch would be unreachable by construction.
     */
    private fun chooseDocumentInitialTypeFilter(type: String?): DocTypeFilter = when (type) {
        "BIBLE" -> DocTypeFilter.BIBLE
        "COMMENTARY" -> DocTypeFilter.COMMENTARY
        else -> DocTypeFilter.entries.getOrElse(
            CommonUtils.settings.getInt("selected_document_filter_no", 0),
        ) { DocTypeFilter.ALL }
    }

    /** Classic's `loadDocuments()` (`:236-258`). */
    private suspend fun loadChooseDocuments() {
        val session = chooseDocumentSession ?: return
        try {
            val books = withContext(Dispatchers.Default) {
                SwordDocumentFacade.documents + FakeBookFactory.pseudoDocuments.filterNot { it.hideFromSelector }
            }
            val rows = withContext(Dispatchers.Default) {
                // The Book -> DocRow mapping (language grouping included) lives in DocRowMapper,
                // shared with the reading view's document quick sheet. One mapper per book list.
                val mapper = DocRowMapper(downloadControl, books)
                books.map { mapper.toDocRow(it) }
            }
            session.booksById = books.associateBy { it.initials }
            session.controller.setDocuments(rows)
        } catch (e: CancellationException) {
            // See [loadChooseDictionaryRows]: classic's `lifecycleScope` job simply died on leaving,
            // but this one runs in the arm's `LaunchedEffect` and is cancelled on every
            // navigate-away. `showError()` does not suspend, so swallowing the cancellation here
            // would paint an error over a screen the user has already left.
            throw e
        } catch (e: Exception) {
            Log.e(TAG_CHOOSE_DOCUMENT, "Error loading documents", e)
            session.controller.showError()
        }
    }

    /** Classic `handleDocumentSelection` (`:262-278`) with `setResult`+`finish` replaced by the
     *  channel: the unlock gate and its reload are unchanged. */
    private fun handleChooseDocumentSelection(docId: String, onResult: (DocumentResult) -> Unit) {
        val book = chooseDocumentSession?.booksById?.get(docId) ?: return
        if (book.bookCategory == BookCategory.AND_BIBLE) return
        lifecycleScope.launch(Dispatchers.Main) {
            if (book.isLocked && !CommonUtils.unlockDocument(this@NavHostComposeActivity, book)) {
                loadChooseDocuments()
                return@launch
            }
            Log.i(TAG_CHOOSE_DOCUMENT, "Book selected:" + book.initials)
            onResult(DocumentResult(book = book.initials))
        }
    }

    /** Classic `handleDelete` (`:287-321`), including its one-dialog-for-the-whole-selection shape
     *  and the per-document `canDelete` re-check inside the loop (the lastBible guard). */
    private fun handleChooseDocumentDelete(ids: Set<String>) {
        val booksById = chooseDocumentSession?.booksById ?: return
        val selected = ids.mapNotNull { booksById[it] }
        val (deletable, rest) = selected.partition { documentControl.canDelete(it.installedDocument) }
        if (rest.isNotEmpty()) ABEventBus.post(ToastEvent(R.string.cant_delete_document))
        if (deletable.isEmpty()) return
        val msg: CharSequence = if (deletable.size == 1) {
            getString(R.string.delete_doc, deletable.single().name)
        } else {
            getString(R.string.delete_docs_confirm) + "\n\n" + deletable.joinToString("\n") { it.name }
        }
        AlertDialog.Builder(this)
            .setMessage(msg).setCancelable(true)
            .setPositiveButton(R.string.yes) { _, _ ->
                var skipped = false
                for (document in deletable) {
                    if (!documentControl.canDelete(document.installedDocument)) { skipped = true; continue }
                    try {
                        Log.i(TAG_CHOOSE_DOCUMENT, "Deleting:$document")
                        documentControl.deleteDocument(document.installedDocument)
                    } catch (e: Exception) {
                        Log.e(TAG_CHOOSE_DOCUMENT, "Deleting document crashed", e)
                        Dialogs.showErrorMsg(R.string.error_occurred, e)
                    }
                }
                if (skipped) ABEventBus.post(ToastEvent(R.string.cant_delete_document))
                lifecycleScope.launch { loadChooseDocuments() }
                ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
            }
            .setNegativeButton(R.string.no, null)
            .create().show()
    }

    /** Classic `handleDeleteIndex` (`:324-341`). */
    private fun handleChooseDocumentDeleteIndex(ids: Set<String>) {
        val booksById = chooseDocumentSession?.booksById ?: return
        for (document in ids.mapNotNull { booksById[it] }) {
            val msg: CharSequence = getString(R.string.delete_search_index_doc, document.name)
            AlertDialog.Builder(this)
                .setMessage(msg).setCancelable(true)
                .setPositiveButton(R.string.okay) { _, _ ->
                    try {
                        Log.i(TAG_CHOOSE_DOCUMENT, "Deleting index:$document")
                        SwordDocumentFacade.deleteDocumentIndex(document.installedDocument)
                    } catch (e: Exception) {
                        Log.e(TAG_CHOOSE_DOCUMENT, "Deleting index crashed", e)
                        Dialogs.showErrorMsg(R.string.error_occurred, e)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .create().show()
        }
    }

    /** Classic `handleAbout` (`:344-360`): reload the SBMD (retaining repo/BadDocument), then show. */
    private fun handleChooseDocumentAbout(docId: String) {
        val document = chooseDocumentSession?.booksById?.get(docId) ?: return
        try {
            val sbmd = document.bookMetaData as SwordBookMetaData
            val repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY)
            val badDocument = sbmd.getProperty("BadDocument")
            sbmd.reload()
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
            sbmd.putProperty("BadDocument", badDocument)
            lifecycleScope.launch(Dispatchers.Main) {
                CommonUtils.showAbout(this@NavHostComposeActivity, document)
            }
        } catch (e: Exception) {
            Log.e(TAG_CHOOSE_DOCUMENT, "Error expanding SwordBookMetaData for $document", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    /** Classic `handleUnlock` (`:363-369`). */
    private fun handleChooseDocumentUnlock(docId: String) {
        val document = chooseDocumentSession?.booksById?.get(docId) ?: return
        lifecycleScope.launch(Dispatchers.Main) {
            CommonUtils.unlockDocument(this@NavHostComposeActivity, document)
            loadChooseDocuments()
        }
    }

    /**
     * Classic `OverflowMenu()` (`:373-392`) -- Download / Backup modules / Install zip. Host-composed
     * in full: `R.drawable` painters, `getString`s, and two `awaitIntent` round trips that need an
     * `ActivityBase`.
     *
     * **Both `awaitIntent` consumers are kept exactly as classic wrote them, by this task's
     * instruction.** `InstallZip` is still an Activity and belongs to slice 8; `Download` is already
     * a destination of this very graph, and THAT is worth a second look before anything routes to
     * this destination (Task 8): the host is `launchMode="singleTop"`, so
     * `awaitIntent(NavHostComposeActivity.intentFor(this, NavRoutes.download()))` issued FROM the
     * host aims an Intent at the host itself. Slice 4 already met this shape and converted its
     * equivalent hop to an in-graph `navigate` for exactly that reason -- see
     * [DownloadDeps.reloadCatalogueIfRequested]'s kdoc, which says so in as many words. Left as-is
     * here deliberately, and reported rather than silently fixed.
     */
    @Composable
    private fun ChooseDocumentOverflowMenu() {
        AbOverflowMenu(contentDescription = null) { close ->
            AbMenuItem(
                text = getString(R.string.download),
                onClick = { close(); onChooseDocumentDownload() },
                icon = { Icon(painterResource(R.drawable.ic_file_download_24dp), contentDescription = null) },
            )
            AbMenuItem(
                text = getString(R.string.backup_modules2),
                onClick = { close(); onChooseDocumentBackup() },
                icon = { Icon(painterResource(R.drawable.ic_backup_black_24dp), contentDescription = null) },
            )
            AbMenuItem(
                text = getString(R.string.install_zip),
                onClick = { close(); onChooseDocumentInstallZip() },
                icon = { Icon(painterResource(R.drawable.ic_unarchive_white_24dp), contentDescription = null) },
            )
        }
    }

    /** Classic `onDownload()` (`:394-408`), unchanged -- see [ChooseDocumentOverflowMenu]'s kdoc. */
    private fun onChooseDocumentDownload() {
        try {
            if (downloadControl.checkDownloadOkay()) {
                val handlerIntent = intentFor(this, NavRoutes.download())
                lifecycleScope.launch {
                    awaitIntent(handlerIntent)
                    ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
                    loadChooseDocuments()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_CHOOSE_DOCUMENT, "Error opening download", e)
            Dialogs.showErrorMsg(R.string.error_occurred, e)
        }
    }

    /** Classic `onBackup()` (`:410-412`). */
    private fun onChooseDocumentBackup() {
        lifecycleScope.launch { BackupControl.backupModulesViaIntent(this@NavHostComposeActivity) }
    }

    /** Classic `onInstallZip()` (`:414-421`) -- still an Activity hop, slice 8's to migrate. */
    private fun onChooseDocumentInstallZip() {
        val intent = ScreenLauncher.intentFor(this, Screen.InstallZip)
        lifecycleScope.launch {
            awaitIntent(intent)
            ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
            loadChooseDocuments()
        }
    }

    // --- Workspace-cluster host baggage (nav-graph slice 7, Task 5) -------------------------------
    // Ported from classic WorkspaceSelectorComposeActivity and TextDisplaySettingsComposeActivity.
    // Both are STILL IN THE TREE and still reachable through their own `Screen.X` arms -- no Activity
    // is deleted before Task 13 -- so each seam below has two copies for now: the Activity's own, and
    // this one behind the graph's deps. Task 13 deletes the Activity half.
    //
    // None of it can cross into `commonMain`: every line names a Room-backed `SettingsBundle`, an
    // `R.string`, an `ActivityResultLauncher` or `awaitIntent`.

    // ——— The cluster's two result channels ———————————————————————————————————————————————————————
    // Both `exitWithResult` lambdas are a hard `error(...)`, the shape the chooser cluster already
    // uses. Design §1.1: slice 7 migrates these destinations together with their consumers -- the
    // selector's result is the reading view's (Task 9), and the settings editor's is consumed by the
    // selector arm INSIDE this very graph -- so every result here is delivered in-graph by
    // construction. If one of these ever runs, a destination has been given an external entry
    // without being given a result contract; fail loudly rather than pack an Intent nobody defined.

    private val workspaceResults = NavResultChannel<WorkspaceResult> {
        error("slice 7 destinations are only entered in-graph")
    }

    private val textSettingsResults = NavResultChannel<TextSettingsResult> {
        error("slice 7 destinations are only entered in-graph")
    }

    // ——— WorkspaceSelector ———————————————————————————————————————————————————————————————————————

    /**
     * The selector's memoised controller -- see [WorkspaceSelectorDeps.controllerFor] for why this
     * one must be host-held rather than built per back-stack entry (it navigates to the settings
     * editor, and navigation-compose disposes its arm's composition while that child is on top).
     *
     * Dropped at every exit, like [manageLabelsSession], so a re-entry after this visit has finished
     * is seeded afresh rather than resuming a finished working set. It is keyed on nothing because
     * the route carries no arguments -- there is only one selector.
     */
    private var workspaceSelectorController: WorkspaceSelectorController? = null

    /**
     * Classic `WorkspaceSelectorComposeActivity`'s `controller` (`:59-83`) plus its `onCreate` side
     * effects (`:89-90`). Both side effects run HERE, at construction, rather than in an arm effect:
     * classic ran them once per Activity, and the in-graph equivalent of that is once per memoised
     * controller -- a `LaunchedEffect` in the arm would re-run on every return from the settings
     * editor and `load()` would wipe the user's unsaved working set.
     *
     * **The memo is assigned LAST** (fix round 1). It used to be written before the two side effects
     * ran, so a `saveCurrentIntoDb()`/`load()` that threw left a half-initialised controller memoised
     * for the rest of the host's life, and every later entry to the selector would be handed that
     * controller with an empty working set instead of retrying. The order below means a throwing
     * side effect leaves the memo untouched and the next entry starts over.
     *
     * This runs from inside the arm's `remember { }`, i.e. it can run for a composition that is then
     * discarded, and that is deliberate rather than merely tolerated: what it leaves behind is a
     * valid, freshly-loaded controller keyed to the one selector route, which the next real entry
     * reuses (the side effects are what "once per visit" means, and they have happened). If no entry
     * follows, [onDestroy] discards it along with anything it cloned.
     */
    private fun workspaceSelectorControllerFor(
        onResult: (workspaceId: String?, changed: Boolean) -> Unit,
        onCancel: () -> Unit,
        onEditSettings: (workspaceId: String) -> Unit,
    ): WorkspaceSelectorController {
        workspaceSelectorController?.let { return it }
        val controller = WorkspaceSelectorController(
            service = workspaceService,
            scope = lifecycleScope,
            onResult = { workspaceId, changed ->
                workspaceSelectorController = null
                onResult(workspaceId, changed)
            },
            onCancel = {
                workspaceSelectorController = null
                onCancel()
            },
            onEditSettings = onEditSettings,
        )
        workspaceService.saveCurrentIntoDb()
        controller.load()
        workspaceSelectorController = controller
        return controller
    }

    /**
     * The consequence of NOT porting classic `WorkspaceSelectorComposeActivity.onDetachedFromWindow`'s
     * `if (!finished) controller.cancel()` -- see `WorkspaceSelectorDeps`' kdoc for why the arm is the
     * wrong place for that guard and this is the right one.
     *
     * `WorkspaceSelectorController.clone` inserts the cloned workspace into the database immediately
     * and stages it in `created` so a Cancel can hard-delete it again; that staging lives only in
     * [workspaceSelectorController], i.e. only as long as this Activity. This host's manifest entry
     * does not list `uiMode`/`fontScale`/`density` in `configChanges`, so a dark-mode flip or a
     * font-scale change destroys and recreates it mid-visit -- and before this hook the clone simply
     * became a permanent workspace that nothing tracked any more, which not even an explicit Cancel
     * afterwards could remove.
     *
     * A non-null memo here means the visit ended without a result: every real exit
     * (`save`/`selectWorkspace`/`cancel`) nulls it first. [WorkspaceSelectorController.discardCreated]
     * rather than `cancel()`, because `cancel()`'s exit is a `popBackStack()`/`finish()` on a host
     * that is already being destroyed.
     */
    override fun onDestroy() {
        workspaceSelectorController?.discardCreated()
        workspaceSelectorController = null
        super.onDestroy()
    }

    // ——— TextDisplaySettings —————————————————————————————————————————————————————————————————————

    /**
     * The text-display-settings visit's state, held for the HOST's lifetime rather than the back-stack
     * entry's composition -- [manageLabelsSession]'s shape, in the reusable form the graph can also
     * key and drop. See `TextDisplaySettingsDeps.navStateMemo`: without it, the destination's whole
     * working set (drill-up stack, controller cache, and the detached `SettingsBundle` edit) is
     * rebuilt from the route the moment anything is pushed on top -- which, since fix round 1 made
     * the Hide-labels row a real child destination, happens on an ordinary tap.
     *
     * Dropped by the arm on every exit, both branches. Nothing here resumes it.
     */
    private val textDisplayNavStateMemo =
        NavSessionMemo<TextDisplaySettingsArgs, TextDisplaySettingsNavState>()

    /** Classic's four `by lazy` label bundles (`:112-115`) -- ~90 `getString` calls between them, so
     *  they stay lazy here too. Fix round 1: they are handed to the graph as `() -> Labels` slots,
     *  because a plain value is read where the deps object is CONSTRUCTED (inside one `remember { }`
     *  that runs on every host launch), which forced all ~90 whatever route the host was opened for --
     *  the exact opposite of what this comment used to claim. */
    private val textDisplayControllerLabels by lazy { buildTextDisplayControllerLabels(this) }
    private val textDisplayScreenLabels by lazy { buildTextDisplayScreenLabels(this) }
    private val textDisplayColorSettingsLabels by lazy { buildColorSettingsLabels(this) }
    private val textDisplayBackgroundImageChooserLabels by lazy { buildBackgroundImageChooserLabels(this) }

    /** Classic's `thumbnailResolver` (`:377`), cache and all. */
    private val textDisplayThumbnailResolver = BackgroundThumbnailResolver()

    /**
     * Classic's photo picker (`:144-165`), unchanged by instruction: a constructor-time
     * `registerForActivityResult` (it must be registered before RESUMED) bridged to a suspend
     * function through a [CancellableContinuation], and handed to every [ColorSettingsController]
     * this host builds. It is a PARAMETER of that controller rather than a property of the Koin
     * service, for the reason `TextDisplaySettingsService.importBackgroundImage`'s kdoc gives.
     */
    private var pendingTextDisplayPick: CancellableContinuation<String?>? = null
    private val textDisplayPhotoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingTextDisplayPick?.resume(uri?.toString())
        pendingTextDisplayPick = null
    }
    private val textDisplayImagePicker: suspend () -> String? = {
        suspendCancellableCoroutine { cont ->
            pendingTextDisplayPick = cont
            textDisplayPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            cont.invokeOnCancellation { pendingTextDisplayPick = null }
        }
    }

    /**
     * Everything one entry into the text-display-settings destination needs, resolved from the
     * route's own arguments -- classic's three coupled `by lazy` fields (`detachedEdit`, `service`
     * and the `finish()` override that reads them) as one object. See [TextDisplaySettingsSession].
     *
     * The DETACHED branch builds its own service instance rather than the Koin singleton, exactly as
     * classic did and for [DetachedWorkspaceEdit]'s stated reason: a selector-originated edit must
     * touch neither the active workspace nor the shared service.
     */
    private fun textDisplaySettingsSessionFor(args: TextDisplaySettingsArgs): TextDisplaySettingsSession {
        val entry = TextDisplaySettingsRouteEntry(args, sharedTextDisplaySettingsService)
        val service = entry.service
        return TextDisplaySettingsSession(
            initialScope = entry.scope,
            controllerFor = { scope, onNavigate ->
                TextDisplaySettingsController(
                    service = service,
                    settingsScope = scope,
                    labels = textDisplayControllerLabels,
                    onNavigateCallback = onNavigate,
                )
            },
            colorControllerFor = { scope ->
                ColorSettingsController(
                    service = service,
                    scope = scope,
                    coroutineScope = lifecycleScope,
                    imagePicker = textDisplayImagePicker,
                )
            },
            hideLabelsPayload = { scope -> textDisplayHideLabelsPayload(service, scope) },
            applyHideLabelsResult = { json, controller -> applyTextDisplayHideLabels(json, controller) },
            // Classic's `finish()` override (`:282-293`), condition included -- see
            // TextDisplaySettingsRouteEntry.resultOnLeave, which is where it lives now so that a test
            // can reach it without launching this host.
            resultOnLeave = entry::resultOnLeave,
        )
    }

    /**
     * The payload half of classic's BOOKMARKS_HIDELABELS bridge (`:381-406`) -- classic
     * `HideLabelsPreference.openDialog`'s `ManageLabelsData`, verbatim. The graph navigates to
     * `NavRoutes.manageLabels(...)` with it; see [applyTextDisplayHideLabels] for the answer.
     *
     * **Fix round 1 replaced the `awaitIntent` this used to be**, and it was live breakage rather
     * than a latent hazard. `awaitIntent(intentFor(this, NavRoutes.manageLabels(data)))` is a
     * `startActivityForResult` aimed at THIS activity, which is `android:launchMode="singleTop"` and
     * is the activity on top: the system answers with [onNewIntent], which navigates the live graph
     * to the label manager, while the pending request resolves CANCELED (or never). The chosen labels
     * were dropped, and the label manager -- now sitting ABOVE `settings/textDisplay` in the same
     * graph -- published its answer into [manageLabelsResults]'s pending slot, which no text-settings
     * code was collecting. This is the shape `DownloadDeps.reloadCatalogueIfRequested` had to stop
     * using, and unlike `ChooseDocument`'s Download row (dormant until Task 8) this one was reachable
     * the day Task 5 landed, through the live `settings` destination's global-text-settings row.
     */
    private fun textDisplayHideLabelsPayload(
        service: TextDisplaySettingsServiceImpl,
        scope: SettingsScope,
    ): String = ManageLabelsContract.ManageLabelsData(
        mode = ManageLabelsContract.Mode.HIDELABELS,
        selectedLabels = service.currentHideLabelsIds(scope).toMutableSet(),
        isWindow = scope is SettingsScope.Window,
    ).applyFrom(windowControl.windowRepository.workspaceSettings).toJSON()

    /**
     * Classic's `RESULT_OK` branch of the same bridge, line for line: a `reset` answer reverts the
     * row, anything else applies the `workspaceSettings.updateFrom(data)` recent-labels side effect
     * and hands the chosen ids to the controller.
     *
     * There is no result-code check any more and none is missing: `ManageLabelsComposeActivity` had
     * no cancel path at all (its back press saves -- see [manageLabelsResults]'s kdoc), so every exit
     * built the identical `RESULT_OK` Intent, and the in-graph destination likewise delivers on every
     * exit. "A result arrived" IS "the user answered".
     */
    private fun applyTextDisplayHideLabels(resultJson: String, controller: TextDisplaySettingsController) {
        val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(resultJson)
        if (resultData.reset) {
            controller.onRevert(TextSettingType.BOOKMARKS_HIDELABELS.name)
        } else {
            windowControl.windowRepository.workspaceSettings.updateFrom(resultData)
            controller.onHideLabelsChange(resultData.selectedLabels.map { it.toString() })
        }
    }

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
        private const val TAG_MY_DOCUMENTS = "MyDocumentsNavHost"
        private const val TAG_DOWNLOAD = "DownloadNavHost"

        /**
         * Per-SCREEN arrangement preference keys, classic `DownloadComposeActivity.kt:113-114`:
         * sorting a download list by size is a different intent from ordering the reading view's
         * document picker, so they are deliberately NOT the chooser's keys.
         */
        private const val ARRANGEMENT_KEY = "download.arrangement"
        private const val ARRANGEMENT_REMEMBER_KEY = "download.arrangement.remember"

        private const val TAG_CHOOSERS = "ChoosersNavHost"
        private const val TAG_CHOOSE_DOCUMENT = "ChooseDocumentNavHost"

        /**
         * The document CHOOSER's own arrangement keys, classic
         * `ChooseDocumentComposeActivity.kt:73-74`, reused VERBATIM so a user's persisted picker
         * arrangement survives while the classic Activity and this arm coexist. Deliberately NOT
         * [ARRANGEMENT_KEY]'s pair: sorting a download list by size is a different intent from
         * ordering the reading view's document picker, and changing one must not reorder the other.
         */
        private const val CHOOSE_DOC_ARRANGEMENT_KEY = "chooseDoc.arrangement"
        private const val CHOOSE_DOC_ARRANGEMENT_REMEMBER_KEY = "chooseDoc.arrangement.remember"

        /**
         * Classic `CloudDocumentsComposeActivity`'s own key prefix (round 17e-2), reused VERBATIM
         * (not the Download cluster's [ARRANGEMENT_KEY] pair) so a user's persisted sync-list
         * arrangement survives while the classic Activity and this arm coexist -- both read/write
         * the same settings key.
         */
        private const val CLOUD_ARRANGEMENT_KEY = "cloudDocs.arrangement"
        private const val CLOUD_ARRANGEMENT_REMEMBER_KEY = "cloudDocs.arrangement.remember"

        /** Classic's repository-staleness cache (`DownloadComposeActivity.kt:938-940`). */
        private const val REPO_REFRESH_DATE = "repoRefreshDate"
        private const val REPO_LIST_STALE_AFTER_DAYS: Long = 1
        private const val MILLISECS_IN_DAY = 1000 * 60 * 60 * 24.toLong()

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
