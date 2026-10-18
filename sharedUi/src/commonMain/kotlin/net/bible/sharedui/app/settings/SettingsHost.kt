package net.bible.sharedui.app.settings

import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.progress.ReadHistoryEntry
import net.bible.sharedcore.progress.ReadingProgressController
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedcore.settings.AppSettingsController
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedcore.settings.ReadingProgressSettingsController
import net.bible.sharedcore.settings.ReadingProgressSettingsService
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedcore.settings.SyncSettingsService
import net.bible.service.common.THEME_PALETTE_STYLE_KEY
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.app.ports.SystemScreens
import net.bible.sharedui.settings.nav.AppSettingsDeps
import net.bible.sharedui.settings.nav.ReadHistoryRequest
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.settings.nav.ReadingProgressDeps
import net.bible.sharedui.settings.nav.ReadingProgressSettingsDeps
import net.bible.sharedui.settings.nav.SettingsNavDeps
import net.bible.sharedui.settings.nav.SyncSettingsDeps
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.nav.NavResultChannel

/**
 * Classic `SettingsComposeActivity.kt:67-72`'s key set: a write to any of these forces a recreate (see
 * [SettingsHost.maybeRecreateForSettingsKey]).
 */
internal val RECREATE_ON_CHANGE_KEYS = setOf(
    "locale_pref",
    "night_mode_pref3",
    "display_color_mode",
    THEME_PALETTE_STYLE_KEY,
    "discrete_mode",
)

/** The persisted tab of the reading-progress screen (0 = reading, 1 = memorize). */
internal const val PREF_READING_PROGRESS_LAST_TAB = "reading_progress_last_tab"

private const val PREF_READING_PROGRESS_MEM_OVERVIEW = "reading_progress_mem_overview"

/**
 * The settings cluster's deps builder and host baggage: app settings, sync settings, reading progress and
 * reading-progress settings.
 *
 * Laziness is load-bearing. [navDeps] runs on every host launch, so every service and controller sits behind a
 * provider or a `by lazy`: [AppSettingsController] forces its service (four JSword dictionary scans and ~51
 * settings reads), [SyncSettingsController] starts the service's asynchronous snapshot load. Nothing here may
 * touch the database while the host is constructed.
 *
 * The three settings controllers are `by lazy` fields rather than per-entry factories on purpose: their state (the
 * collector, the snapshot) should outlive one back-stack entry's composition. The reading-progress SCREEN's
 * controller is cached too, see [readingProgressControllerFor].
 */
class SettingsHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val appSettingsProvider: () -> AppSettings,
    private val platform: SettingsPlatform,
    private val links: ExternalLinks,
    private val debug: DebugActions,
    private val systemScreens: SystemScreens,
    private val helpDialogs: HelpDialogs,
    private val appSettingsServiceProvider: () -> AppSettingsService,
    private val syncSettingsServiceProvider: () -> SyncSettingsService,
    private val readingProgressSettingsServiceProvider: () -> ReadingProgressSettingsService,
    private val readingProgressServiceProvider: () -> ReadingProgressService,
) {
    /**
     * Classic `SettingsComposeActivity`'s own `by lazy` service, NOT a Koin singleton: it caches four JSword
     * dictionary scans for the screen's lifetime. Host-held because the settings destination's composition is
     * disposed and re-entered whenever another destination sits on top of it.
     */
    /** Preference reads, resolved on first use (Welcome starts uninitialised). */
    private val appSettings: AppSettings by lazy(appSettingsProvider)
    private val appSettingsService: AppSettingsService by lazy(appSettingsServiceProvider)
    private val syncSettingsService: SyncSettingsService by lazy(syncSettingsServiceProvider)
    private val readingProgressSettingsService: ReadingProgressSettingsService by lazy(readingProgressSettingsServiceProvider)
    private val readingProgressService: ReadingProgressService by lazy(readingProgressServiceProvider)

    internal val appSettingsController: AppSettingsController by lazy {
        AppSettingsController(
            service = appSettingsService,
            scope = shell.hostScope,
            labels = strings.appSettingsLabels(),
            // Unused by design: the screen's onNavigate is wired directly in settingsNavGraph's SETTINGS arm,
            // because two of its seven rows are routes in the host's graph. See AppSettingsDeps' kdoc.
            onNavigate = {},
            // The reset question is the controller's own dialog state; confirmDialog() runs this callback.
            onConfirmReset = { resetSettings() },
        )
    }

    internal val syncSettingsController: SyncSettingsController by lazy {
        SyncSettingsController(
            service = syncSettingsService,
            scope = shell.hostScope,
            labels = strings.syncSettingsLabels(),
            // CloudDocuments is a destination in the same graph: an in-graph hop keeps a settings->cloud hop
            // inside the host rather than starting a second one.
            onOpenCloudDocuments = { shell.navigate(NavRoutes.cloudDocuments()) },
        )
    }

    internal val readingProgressSettingsController: ReadingProgressSettingsController by lazy {
        ReadingProgressSettingsController(
            service = readingProgressSettingsService,
            scope = shell.hostScope,
            labels = strings.readingProgressSettingsLabels(),
        )
    }

    /**
     * The settings cluster's deps. [readingProgressResults] is the host-created channel the reading destination
     * collects; the activity still owns it until the result channels move.
     */
    fun navDeps(
        navController: NavHostController,
        readingProgressResults: NavResultChannel<ReadingProgressResult>,
    ): SettingsNavDeps = SettingsNavDeps(
        exitHost = { shell.exitHost() },
        setWindowTitle = { title -> shell.setWindowTitle(title) },
        appSettings = AppSettingsDeps(
            // A getter over the `by lazy`, never an instance built here: assembling these deps runs on EVERY
            // host launch, for every cluster. See [appSettingsController] and AppSettingsDeps.controller's kdoc.
            controller = { appSettingsController },
            maybeRecreate = { key -> maybeRecreateForSettingsKey(key) },
            discreteHelpHtml = { buildDiscreteHelpHtml() },
            // The `global_text_display_settings` row: a graph navigation that names its scope and carries no
            // detached bundle at all. `TextDisplaySettingsScopeTest` pins it.
            onOpenTextDisplaySettings = {
                navController.navigate(NavRoutes.textDisplaySettings(scopeLevel = "global"))
            },
            onOpenLinksSettings = { systemScreens.openAppLinkSettings() },
            onCrashApp = { debug.crashAppDelayed() },
            onTestErrorDialog = { debug.showTestError("Test error (debug build)") },
            onTestErrorDialogQueue = {
                debug.showTestError("Test error 1 of 2")
                debug.showTestError("Test error 2 of 2")
            },
            // One string, unlike the ~90 the label bundle needs; cheap enough to stay eager.
            resetContentDescription = strings.resetSettings,
            onResume = { appSettingsService.refresh() },
            askBeforeOpeningLink = { links.askBeforeOpening },
            onOpenExternal = { links.open(it) },
        ),
        syncSettings = SyncSettingsDeps(
            controller = { syncSettingsController },
            onResume = { syncSettingsService.refresh() },
        ),
        readingProgress = ReadingProgressDeps(
            // A factory, so nothing here constructs a controller or touches the read-history DAO until the
            // destination actually composes. See readingProgressControllerFor.
            controllerFor = { tabArg, onShowHistory, onResult ->
                readingProgressControllerFor(tabArg, onShowHistory, onResult)
            },
            persistTab = { tab -> persistReadingProgressTab(tab) },
            onApplyHistoryDeletes = { ids, cycle, onDeleted ->
                shell.hostScope.launch {
                    readingProgressService.deleteReadHistoryEntries(ids, cycle)
                    onDeleted()
                }
            },
            onShowHelp = { helpDialogs.show(HelpTopic.READING_PROGRESS) },
            unmarkConfirmMessage = { rangeName -> strings.memorizeConfirmUnmark(rangeName) },
            removeTargetConfirmMessage = { rangeName -> strings.memorizeConfirmRemoveTarget(rangeName) },
            // Classic's android.R.string.ok / cancel: the SYSTEM strings, kept.
            confirmText = platform.confirmText,
            dismissText = platform.dismissText,
        ),
        readingProgressResults = readingProgressResults,
        readingProgressSettings = ReadingProgressSettingsDeps(
            controller = { readingProgressSettingsController },
        ),
    )

    /**
     * Classic's `maybeRecreate`. These keys' effects are read only at host-creation time (the locale through
     * `attachBaseContext`, the theme values once per composition), so nothing observes them reactively and a
     * recreate is what makes the change visible at once.
     *
     * A recorded behaviour change: the recreate rebuilds EVERY destination and the whole back stack, not just
     * the settings screen that asked for it. Kept deliberately.
     */
    internal fun maybeRecreateForSettingsKey(key: String) {
        if (key in RECREATE_ON_CHANGE_KEYS) shell.recreateHost()
    }

    /** Wired into [AppSettingsController]'s `onConfirmReset`; runs only from its `confirmDialog()`. */
    private fun resetSettings() {
        platform.resetAllSettings()
        appSettingsService.refresh()
        shell.recreateHost()
    }

    /**
     * The persecution-help HTML body. It feeds [AppSettingsController.showDiscreteHelp], rendered as a message
     * dialog whose inline wiki link goes through the `askBeforeOpeningLink` / `onOpenExternal` pair.
     */
    internal fun buildDiscreteHelpHtml(): String {
        val linkUrl = "https://github.com/AndBible/and-bible/wiki/Discrete-build"
        val linkText = "<a href=\"$linkUrl\">$linkUrl</a>"

        val dPar1 = strings.discreteModeInfoPar1
        val dPar2 = strings.discreteModeInfoPar2
        val dLink = strings.discreteModeLink(linkText)

        val calcPar1 = strings.calculatorPar1
        val calcPar2 = strings.calculatorPar2
        val calcPar3 = strings.calculatorPar3

        val dText = "$dPar1<br><br>$dPar2<br><br>$dLink<br><br>"
        val calcText = "$calcPar1$calcPar2<br><br>$calcPar3<br><br>$dLink"
        return if (!platform.isDiscreteBuild) dText else calcText
    }

    // --- Reading progress ---------------------------------------------------------------------

    /**
     * The reading-progress screen's controller, built AT MOST ONCE per host instance and kept alive across the
     * destination's composition being disposed (its overflow opens reading-progress settings, a child
     * destination of the same graph; a per-entry controller would lose the open chapter-detail panel and the
     * "show more" paging counts on return). Null until a reading-progress destination first composes.
     */
    private var readingProgressController: ReadingProgressController? = null

    /**
     * Where the three read-history loaders deliver their answer: the dialog state of whichever COMPOSITION of
     * the reading-progress arm is live. Re-pointed on every [readingProgressControllerFor] call, because a
     * surviving controller outlives the composition that first supplied one.
     */
    private var readingProgressHistorySink: ((ReadHistoryRequest) -> Unit)? = null

    /**
     * The raw tab route argument last APPLIED to [readingProgressController], so a re-entry can tell "the route
     * names a tab I have not acted on yet" from "the argument the surviving controller was built with". Null
     * for a route that named no tab.
     */
    private var readingProgressAppliedTabArg: Int? = null

    /**
     * [ReadingProgressDeps.controllerFor]. The re-entry tab policy: an explicit tab is applied ONCE, when it is
     * NEW. The arm calls this from a `remember` that re-runs on every re-composition of the entry, including the
     * one caused by the reading-progress-settings child popping; re-applying an unchanged argument there would
     * overwrite the tab the user had since selected. [readingProgressAppliedTabArg] gates it.
     *
     * `onResult` is captured into the controller ONLY on first construction and lives for its whole cached
     * lifetime, so it must close over stable references only (the arm's `NavHostController` and the host's
     * result channel), never per-composition local state.
     */
    internal fun readingProgressControllerFor(
        tabArg: Int?,
        onShowHistory: (ReadHistoryRequest) -> Unit,
        onResult: (ReadingProgressResult) -> Unit,
    ): ReadingProgressController {
        readingProgressHistorySink = onShowHistory

        readingProgressController?.let { existing ->
            if (tabArg != null && tabArg != readingProgressAppliedTabArg) {
                readingProgressAppliedTabArg = tabArg
                existing.selectTab(readingProgressInitialTab(tabArg))
            }
            return existing
        }

        // The history loaders need the cycle the user is CURRENTLY VIEWING (the screen's prev/next-cycle arrows
        // move the model to an older cycle), not service.currentCycle(). The callbacks only run after
        // construction returns, so a lateinit self-reference is the faithful equivalent.
        lateinit var controller: ReadingProgressController
        controller = ReadingProgressController(
            service = readingProgressService,
            scope = shell.hostScope,
            initialTab = readingProgressInitialTab(tabArg),
            onNavigateToChapter = { bookId, chapter -> onResult(ReadingProgressResult.Chapter(bookId, chapter)) },
            // Through the sink, never through this call's `onShowHistory`: the controller outlives the
            // composition that supplied that lambda.
            onShowDayHistory = { day ->
                showDayHistory(day, controller.model.value.cycle, ::emitReadingProgressHistory)
            },
            onShowBookHistory = { bookId ->
                showBookHistory(bookId, controller.model.value.cycle, ::emitReadingProgressHistory)
            },
            onShowChapterHistory = { bookId, chapter ->
                showChapterHistory(bookId, chapter, controller.model.value.cycle, ::emitReadingProgressHistory)
            },
            initialOverviewActive = appSettings.getBoolean(PREF_READING_PROGRESS_MEM_OVERVIEW, true),
            onNavigateToMemorize = { startOrdinal, endOrdinal ->
                onResult(ReadingProgressResult.Memorize(startOrdinal, endOrdinal))
            },
            persistOverview = { appSettings.setBoolean(PREF_READING_PROGRESS_MEM_OVERVIEW, it) },
        )
        readingProgressController = controller
        readingProgressAppliedTabArg = tabArg
        return controller
    }

    private fun emitReadingProgressHistory(request: ReadHistoryRequest) {
        readingProgressHistorySink?.invoke(request)
    }

    /**
     * Classic `ReadingProgressComposeActivity`'s initial tab against a nullable route argument. An absent
     * argument resolves to the PERSISTED tab (the argument's default value, not a fallback to 0).
     */
    internal fun readingProgressInitialTab(tabArg: Int?): ReadingTab {
        val tab = tabArg ?: appSettings.getInt(PREF_READING_PROGRESS_LAST_TAB, 0)
        return if (tab == 1) ReadingTab.MEMORIZE else ReadingTab.READING
    }

    private fun persistReadingProgressTab(tab: ReadingTab) {
        appSettings.setInt(PREF_READING_PROGRESS_LAST_TAB, if (tab == ReadingTab.MEMORIZE) 1 else 0)
    }

    // The three read-history loaders hand their answer to the ARM's dialog state through `emit`.

    private fun showDayHistory(dayTimestamp: Long, cycle: Int, emit: (ReadHistoryRequest) -> Unit) {
        shell.hostScope.launch {
            val entries = readingProgressService.readHistoryForDay(dayTimestamp, cycle)
            emit(
                ReadHistoryRequest(
                    title = strings.readingProgressHistoryFor(readingProgressService.dayTitle(dayTimestamp)),
                    rows = entries.map {
                        val date = readingProgressService.formatEntryDate(it.readAt)
                        val time = readingProgressService.formatEntryTime(it.readAt)
                        val version = it.bookInitials.ifEmpty { strings.readingProgressHistoryVersionUnknown }
                        ReadHistoryRow(id = it.id, primary = "$date $time", secondary = version)
                    },
                ),
            )
        }
    }

    private fun showBookHistory(bookId: String, cycle: Int, emit: (ReadHistoryRequest) -> Unit) {
        shell.hostScope.launch {
            val entries = readingProgressService.readHistoryForBook(bookId, cycle)
            emit(
                ReadHistoryRequest(
                    title = strings.readingProgressHistoryFor(readingProgressService.bookLongName(bookId)),
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
        shell.hostScope.launch {
            val entries = readingProgressService.readHistoryForChapter(bookId, chapter, cycle)
            emit(
                ReadHistoryRequest(
                    title = strings.readingProgressHistoryFor(
                        "${readingProgressService.bookShortName(bookId)} $chapter",
                    ),
                    rows = entries.map { it.toReadHistoryRow() },
                ),
            )
        }
    }

    private fun ReadHistoryEntry.toReadHistoryRow(): ReadHistoryRow {
        val chapterRef = "${readingProgressService.bookShortName(bookId)} $chapter"
        val time = readingProgressService.formatEntryTime(readAt)
        val date = readingProgressService.formatEntryDate(readAt)
        val version = bookInitials.ifEmpty { strings.readingProgressHistoryVersionUnknown }
        return ReadHistoryRow(id = id, primary = "$chapterRef · $time", secondary = "$date · $version")
    }
}
