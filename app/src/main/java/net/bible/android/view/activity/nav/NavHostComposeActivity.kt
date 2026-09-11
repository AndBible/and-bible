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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.report.AiBugReport
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.ai.resolvedCustomPromptValue
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.pause
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.ReadingPlansUpdatedViaSyncEvent
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.PromptCsvUtils
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.readingplan.OneDaysReadingsDto
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.isEpub
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
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.readingplan.DailyReadingController
import net.bible.sharedcore.readingplan.DailyReadingListController
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.DayEntry
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.ReadingPlanSelectorController
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchFormController
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.AbAppTheme
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
import net.bible.sharedui.readingplan.nav.DailyReadingDeps
import net.bible.sharedui.readingplan.nav.DailyReadingLoad
import net.bible.sharedui.readingplan.nav.DayListDeps
import net.bible.sharedui.readingplan.nav.LoadedReadingDay
import net.bible.sharedui.readingplan.nav.ReadingPlanNavDeps
import net.bible.sharedui.readingplan.nav.ReadingPlanSelection
import net.bible.sharedui.readingplan.nav.SelectorDeps
import net.bible.sharedui.readingplan.nav.readingPlanNavGraph
import net.bible.sharedui.search.nav.IndexOutcome
import net.bible.sharedui.search.nav.IndexTarget
import net.bible.sharedui.search.nav.SearchFormDeps
import net.bible.sharedui.search.nav.SearchFormSetup
import net.bible.sharedui.search.nav.SearchIndexPromptDeps
import net.bible.sharedui.search.nav.SearchIndexProgressDeps
import net.bible.sharedui.search.nav.SearchNavDeps
import net.bible.sharedui.search.nav.SearchSubmission
import net.bible.sharedui.search.nav.TranslationSelection
import net.bible.sharedui.search.nav.searchNavGraph
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
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
            val (plan, day) = readingPlanArgsOf(route)
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

    /**
     * [NavRoutes.ARG_PLAN] and [NavRoutes.ARG_DAY] read off a daily-reading route string, for
     * [onNewIntent]'s host-side load — which has to reach those values BEFORE the destination that
     * would normally read them off its `NavBackStackEntry` exists (or, in the no-op-navigate case,
     * without it ever re-reading them).
     *
     * **[NavRoutes.ARG_PLAN] must be decoded here**, and that is not a redundant belt on top of the
     * arm's read — it is the step this parser is missing and the arm gets for free. `NavRoutes
     * .dailyReading` percent-encodes every value (`RouteBuilder.optional` -> `NavRoutes.encodeArg`),
     * and the arm reaches its copy through the navigation library, whose
     * `NavDeepLink.getMatchingQueryArguments` reads query values with `Uri.getQueryParameters` —
     * which returns them ALREADY `Uri.decode`-ed. So the arm sees `My Plan` where a plain string
     * split sees `My%20Plan`; "the arm does not decode" only ever meant "it does not need a SECOND,
     * `NavRoutes.decodeArg` pass". Feeding the encoded form to `ReadingPlanControl.setReadingPlan`
     * would write a non-existent plan code into the `READING_PLAN` preference and then throw — and
     * plan codes are filenames (`ReadingPlanTextFileDao.userPlanCodes`, `AndBibleAddons
     * .providedReadingPlans`), so a space or a non-ASCII character in one is ordinary, not exotic.
     * `decodeArg`'s malformed-escape `require` cannot fire: every daily-reading route reaching
     * [EXTRA_ROUTE] was built by `NavRoutes.dailyReading`. [NavRoutes.ARG_DAY] is digits, so it
     * needs no decode.
     *
     * An EMPTY value counts as absent, matching the library: its query-parameter regex is `(.+?)`,
     * so `plan=` does not match and the argument falls back to its `null` default — whereas `""`
     * here would reach `setReadingPlan("")` and wipe the preference.
     */
    private fun readingPlanArgsOf(route: String): Pair<String?, Int?> {
        val query = route.substringAfter('?', "")
        if (query.isEmpty()) return null to null
        val arguments = query.split("&")
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val plan = arguments[NavRoutes.ARG_PLAN]?.takeIf { it.isNotEmpty() }?.let(NavRoutes::decodeArg)
        return plan to arguments[NavRoutes.ARG_DAY]?.toIntOrNull()
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
                val allTools = remember { ToolRegistry.getAllTools() }
                val aiModelsController = remember {
                    AiModelsController(service = llmModelService, scope = lifecycleScope)
                }
                val aiConnectionSettingsController = remember {
                    AiConnectionSettingsController(
                        service = aiSettingsService,
                        scope = lifecycleScope,
                        labels = buildAiConnectionLabels(),
                        // The real navigation branching lives in aiNavGraph's
                        // AI_CONNECTION_SETTINGS arm (six of its seven edges are
                        // navController.navigate(...); RESET_USAGE is the one host callback),
                        // not here — see AiConnectionSettingsDeps' kdoc. This constructor param is
                        // required but unused: the screen's onNavigate is wired directly in the
                        // graph, never through controller::onNavigate.
                        onNavigate = {},
                    )
                }
                val aiProvidersController = remember {
                    AiProvidersController(service = llmProviderService, scope = lifecycleScope)
                }
                val deps = remember(allTools) {
                    AiNavDeps(
                        exitHost = { finish() },
                        setWindowTitle = { title -> setTitle(title) },
                        toolInfo = ToolInfoDeps(
                            readTools = allTools.filter { !it.requiresPermission }.map { it.toToolVd() },
                            writeTools = allTools.filter { it.requiresPermission }.map { it.toToolVd() },
                            helpBody = getString(R.string.help_tool_info_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#ai-tools",
                        ),
                        aiDocumentFilter = AiDocumentFilterDeps(
                            // A fresh instance per back-stack entry -- see AiDocumentFilterDeps'
                            // kdoc (C1: this used to be a single remember{} at host scope, which is
                            // why "Discard changes?" did not actually discard anything).
                            controllerFor = { AiDocumentFilterController(service = documentFilterService, scope = lifecycleScope) },
                            helpBody = getString(R.string.help_ai_document_filter_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#available-data-and-documents",
                        ),
                        globalToolPermissions = GlobalToolPermissionsDeps(
                            // Same fix, same reason -- see AiDocumentFilterDeps' kdoc (C1).
                            controllerFor = { GlobalToolPermissionsController(service = toolPermissionService, scope = lifecycleScope) },
                            helpBody = getString(R.string.help_global_tool_permissions_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#setting-permissions",
                        ),
                        aiModels = AiModelsDeps(
                            controller = aiModelsController,
                            providersForPicker = { llmModelService.providersForPicker() },
                            helpBody = getString(R.string.help_ai_models_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#available-models",
                            onResume = { llmModelService.refresh() },
                        ),
                        aiConnectionSettings = AiConnectionSettingsDeps(
                            controller = aiConnectionSettingsController,
                            languageChoices = buildAiLanguageChoices(),
                            customLanguageTag = CUSTOM_LANGUAGE_TAG,
                            onCustomPromptSave = { key, value -> onAiConnectionCustomPromptSave(key, value) },
                            customPromptTextFor = { key -> aiConnectionCustomPromptTextFor(key) },
                            onResetUsageConfirm = { showAiConnectionResetUsageConfirm() },
                            actions = { AiConnectionHelpAction() },
                            onResume = { aiSettingsService.refresh() },
                        ),
                        aiProviders = AiProvidersDeps(
                            controller = aiProvidersController,
                            helpBody = getString(R.string.help_ai_providers_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#choosing-a-provider",
                            unknownErrorMessage = getString(R.string.unknown_error),
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
                            helpBody = getString(R.string.help_ai_settings_text),
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
                            helpBody = getString(R.string.help_prompt_edit_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#custom-prompts",
                            onPromptCopied = {
                                Toast.makeText(this@NavHostComposeActivity, R.string.prompt_copied, Toast.LENGTH_SHORT).show()
                            },
                        ),
                        rawLlmLog = RawLlmLogDeps(
                            controllerFor = { RawLlmLogController(service = rawLogService, scope = lifecycleScope) },
                            defaultTitle = getString(R.string.raw_llm_log_title),
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
                            helpBody = getString(R.string.help_ai_connection_text),
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
                    readingPlanNavGraph(navController, readingPlanDeps)
                    searchNavGraph(navController, searchDeps)
                }
            }
        }
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

    companion object {
        private const val TAG_AI_PROMPTS = "AiPromptsCompose"
        private const val TAG_READING_PLAN = "DailyReadingNavHost"
        private const val TAG_SEARCH = "SearchCompose"
        private const val TAG_SEARCH_INDEX = "SearchIndexProgCompose"

        /** Classic Search's settings keys, unchanged so a user's saved state survives the migration. */
        private const val SEARCH_SELECTED_TRANSLATIONS_KEY = "search_selected_translations"
        private const val SEARCH_RECENT_TERMS_KEY = "search_recent_terms"

        /** Host-wide concerns (onNewIntent routing), as opposed to one cluster's baggage. */
        private const val TAG_NAV_HOST = "NavHostCompose"

        const val EXTRA_ROUTE: String = "nav_route"

        /** Sentinel identifying the "Custom…" entry in the AI-language picker (mirrors classic). */
        private const val CUSTOM_LANGUAGE_TAG = "\u0000custom"

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
