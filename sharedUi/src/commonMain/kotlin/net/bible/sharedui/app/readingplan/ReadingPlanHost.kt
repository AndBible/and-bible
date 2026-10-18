package net.bible.sharedui.app.readingplan

import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.control.readingplan.ReadingPlanDayWrites
import net.bible.android.control.readingplan.ReadingStatus
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.DbDate
import net.bible.service.db.DatabaseContainer
import net.bible.service.readingplan.OneDaysReadingsDto
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.readingplan.DailyReadingController
import net.bible.sharedcore.readingplan.DailyReadingListController
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.DayEntry
import net.bible.sharedcore.readingplan.PlanEntry
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.ReadingPlanSelectorController
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedlogic.sync.bookNameMonitor
import net.bible.sharedlogic.sync.withMonitor
import net.bible.sharedlogic.util.currentTimeMillis
import net.bible.sharedui.app.HostShell
import net.bible.android.view.activity.installzip.INSTALL_ZIP_ACTION_VIEW
import net.bible.sharedui.app.installzip.InstallZipOpener
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.components.ymdToUtcMidnightMillis
import net.bible.sharedui.readingplan.nav.DailyReadingDeps
import net.bible.sharedui.readingplan.nav.DailyReadingLoad
import net.bible.sharedui.readingplan.nav.DayListDeps
import net.bible.sharedui.readingplan.nav.LoadedReadingDay
import net.bible.sharedui.readingplan.nav.ReadingPlanNavDeps
import net.bible.sharedui.readingplan.nav.ReadingPlanSelection
import net.bible.sharedui.readingplan.nav.SelectorDeps
import net.bible.sharedui.strings.Strings
import org.crosswire.jsword.versification.BookName
import kotlin.coroutines.cancellation.CancellationException

private const val TAG_READING_PLAN = "DailyReadingNavHost"

/**
 * What of the reading plan host survives `recreate()` and process death: the plans reset since they were last
 * chosen. The loaded day is not saved; it is rebuilt from the destination's route arguments.
 */
@Serializable
data class ReadingPlanRecreationState(val resetPlanCodes: Set<String> = emptySet())

/**
 * The reading plan cluster's deps builder and host baggage: the daily reading view, the day list and the plan
 * selector. Ported from classic DailyReadingComposeActivity / DailyReadingListComposeActivity /
 * ReadingPlanSelectorComposeActivity.
 *
 * Every controller sits behind a provider, so constructing the host touches no database.
 */
class ReadingPlanHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val platform: ReadingPlanHostPlatform,
    private val openInstallZip: InstallZipOpener,
    private val documentPicker: DocumentPicker,
    private val readingPlanControlProvider: () -> ReadingPlanControl,
    private val speakControlProvider: () -> SpeakControl,
    private val appScopeProvider: () -> AppCoroutineScope,
    restored: ReadingPlanRecreationState? = null,
    /** Seam for tests: clears a plan's progress. Defaults to [ReadingPlanControl.reset]. */
    private val resetPlan: (String) -> Unit = { readingPlanControlProvider().reset(it) },
) {
    private val readingPlanControl: ReadingPlanControl by lazy(readingPlanControlProvider)
    private val speakControl: SpeakControl by lazy(speakControlProvider)

    /** Reading-plan writes that must land even if this screen goes away mid-way (app scope). */
    private val readingPlanWrites by lazy { ReadingPlanDayWrites(readingPlanControl, appScopeProvider().coroutineContext) }

    private var navController: NavHostController? = null

    /**
     * The live [DailyReadingController] the graph built for the destination currently on screen.
     * Captured here (rather than created here) because the controller needs the graph's two
     * navigation edges, while [pushReadingPlanUi]/[pushReadingPlanSpeakState] need to push INTO it
     * from host code, and because navigation-compose disposes this destination's composition while
     * a child is on top, a new instance replaces the old one on every re-entry.
     *
     * What keeps the field pointing at the instance the user can see is NOT that only one
     * daily-reading entry exists: a new intent can push a second one while the day list
     * or selector is on top, giving `[dailyReading, dayList, dailyReading]`. It is that
     * navigation-compose composes only the VISIBLE entries: `controllerFor` runs as an entry becomes
     * visible, so the last write always comes from the entry now on top, and an entry further down
     * (whose composition is disposed, and whose controller nothing can reach) can never write again
     * until it is composed once more, at which point it IS the visible one. During a transition
     * both are briefly composed; the entering one composes second, so it wins.
     */
    var dailyReadingController: DailyReadingController? = null
        private set
    private var readingsDto: OneDaysReadingsDto? = null
    var dayLoaded: Int = 0
        private set
    var planCodeLoaded: String? = null
        internal set

    /** Classic's "the selector was already offered once" memory, see [offerReadingPlanSelector]. */
    private var readingPlanSelectorOffered = false

    /** What [loadReadingPlanDay] last landed on; the graph turns it into the history route. */
    val loadedReadingDay = MutableStateFlow<LoadedReadingDay?>(null)

    /** Child -> parent channel for the day list / selector, see [ReadingPlanSelection]. */
    private val pendingReadingPlanSelection = MutableStateFlow<ReadingPlanSelection?>(null)

    /**
     * Plans reset since they were last chosen in the selector. `reset()` only clears progress, so the plan still
     * "exists": without this, popping back onto a daily-reading entry whose route arguments name the reset plan
     * reloads it and selects it again. Such a load falls to the selector like a missing plan.
     */
    val resetReadingPlanCodes: MutableSet<String> = restored?.resetPlanCodes.orEmpty().toMutableSet()

    /** The state to persist across recreation. The empty snapshot means "nothing to restore". */
    fun recreationSnapshot() = ReadingPlanRecreationState(resetReadingPlanCodes.toSet())

    /** Replaces the reset codes with a restored snapshot. */
    fun restore(state: ReadingPlanRecreationState?) {
        resetReadingPlanCodes.clear()
        resetReadingPlanCodes += state?.resetPlanCodes.orEmpty()
    }

    fun navDeps(navController: NavHostController): ReadingPlanNavDeps {
        this.navController = navController
        return ReadingPlanNavDeps(
            exitHost = { shell.exitHost() },
            setWindowTitle = { title -> shell.setWindowTitle(title) },
            setHistoryRoute = { owner, route -> shell.setHistoryRoute(owner, route) },
            clearHistoryRoute = { owner -> shell.clearHistoryRoute(owner) },
            goBackInHistory = { shell.goBackInHistory() },
            pendingSelection = pendingReadingPlanSelection,
            dailyReading = DailyReadingDeps(
                controllerFor = { onChangePlan, onChangeDay -> buildDailyReadingController(onChangePlan, onChangeDay) },
                loadDay = { plan, day -> loadReadingPlanDay(plan, day) },
                loaded = loadedReadingDay,
                subscribeEvents = { subscribeDailyReadingEvents() },
                onImportPlan = { importPlan() },
                onPlanMissing = { offerReadingPlanSelector() },
                title = strings.readingPlanTitle,
            ),
            dayList = DayListDeps(
                controllerFor = { scope, onSelect ->
                    DailyReadingListController(
                        scope = scope,
                        loadDays = {
                            readingPlanControl.currentPlansReadingList().map {
                                DayEntry(it.day, readingPlanDayPrimaryText(it), it.readingsDesc)
                            }
                        },
                        onSelect = onSelect,
                    )
                },
                subscribeEvents = { onReadingPlansChanged -> subscribeReadingPlansUpdated(onReadingPlansChanged) },
                title = strings.readingPlanTitle,
            ),
            selector = SelectorDeps(
                controllerFor = { scope, onSelect ->
                    ReadingPlanSelectorController(
                        scope = scope,
                        loadPlans = {
                            readingPlanControl.readingPlanList().map {
                                PlanEntry(it.planCode, it.planName ?: "", it.planDescription ?: "")
                            }
                        },
                        hasDuplicates = { readingPlanControl.readingPlanUserDuplicates },
                        onSelect = { planCode ->
                            // Classic's guard, kept host-side: the plan may have
                            // vanished (sync) between the list load and the tap.
                            shell.hostScope.launch {
                                val dto = readingPlanControl.readingPlanList().firstOrNull { it.planCode == planCode }
                                if (dto != null) {
                                    // the start write must land even if the screen goes away; the next
                                    // screen reads it, so report the selection only after it
                                    resetReadingPlanCodes.remove(planCode) // chosen again: loadable again
                                    withContext(appScopeProvider().coroutineContext) { readingPlanControl.startReadingPlan(dto) }
                                    onSelect(planCode)
                                }
                            }
                        },
                        onReset = { planCode -> onSelectorReset(planCode) },
                    )
                },
                subscribeEvents = { onReadingPlansChanged -> subscribeReadingPlansUpdated(onReadingPlansChanged) },
                title = strings.readingPlanSelectorTitle,
            ),
        )
    }

    /** The selector's Reset of [planCode]: clears its progress, then forgets it (see [forgetResetReadingPlan]). */
    internal fun onSelectorReset(planCode: String) {
        resetPlan(planCode)
        // the selector is already on screen: the day view that Back reveals, now
        // plan-less, goes to the reader instead of offering the selector again
        if (forgetResetReadingPlan(planCode)) readingPlanSelectorOffered = true
    }

    /**
     * Back to the reading destination when it is on the back stack; finish only when this host was started on the
     * day itself (nothing beneath to return to). Daily reading's Passage/Reset/Done were classic's `finish()`, which
     * would close the reading view's own host now that it is pushed above `reading` in the same host.
     */
    private fun returnToReadingOrFinish() {
        val controller = navController
        val readingOnStack = controller?.currentBackStack?.value
            ?.any { it.destination.route?.substringBefore('?') == NavRoutes.READING } == true
        if (controller != null && readingOnStack) controller.popBackStack(NavRoutes.READING, inclusive = false)
        else shell.exitHost()
    }

    private fun buildDailyReadingController(
        onChangePlan: () -> Unit,
        onChangeDay: () -> Unit,
    ): DailyReadingController = DailyReadingController(
        onToggleRead = { readingNo ->
            shell.hostScope.launch {
                readingPlanWrites.toggleRead(dayLoaded, readingNo)
                pushReadingPlanUi()
            }
        },
        onRead = { readingNo ->
            val dto = readingsDto ?: return@DailyReadingController
            val key = dto.getReadingKey(readingNo)
            // read() makes a direct `HistoryManager.recordIfCreated` call synchronously, and HistoryManager then reads
            // isIntegrateWithHistoryManager + intentForHistoryList off the host, both already
            // pointing at the day on screen, because the destination set the history route when it
            // loaded. Classic's belt-and-braces `isIntegrateWithHistoryManager = true` here is NOT
            // ported: on classic it was a no-op on an already-true val, but here it would set the
            // flag independently of `historyRoute`, and a true flag with a null route falls back to
            // super.intentForHistoryList, i.e. the argument-free route, the wrong day. The two
            // halves of the seam only ever move together, through setHistoryRoute.
            shell.hostScope.launch {
                readingPlanControl.read(dayLoaded, readingNo, key)
                returnToReadingOrFinish()
            }
        },
        onSpeak = { readingNo ->
            val dto = readingsDto ?: return@DailyReadingController
            shell.hostScope.launch {
                readingPlanControl.speak(dayLoaded, readingNo, dto.getReadingKey(readingNo))
                pushReadingPlanUi()
            }
        },
        onSpeakAll = {
            val dto = readingsDto ?: return@DailyReadingController
            shell.hostScope.launch {
                readingPlanControl.speak(dayLoaded, dto.getReadingKeys)
                pushReadingPlanUi()
            }
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
            else { readingPlanControl.reset(code); forgetResetReadingPlan(code); returnToReadingOrFinish() }
        },
        onSetStartDate = { showReadingPlanStartDatePicker() },
        onConfirmStartDate = { year, month1to12, day -> confirmReadingPlanStartDate(year, month1to12, day) },
        onImportPlan = { importPlan() },
    ).also { dailyReadingController = it }

    private var readingDayLoadGeneration = 0L

    /**
     * Classic `DailyReadingComposeActivity.onCreate`'s plan gate and its `loadDailyReading(plan,
     * day)` in one call, the graph has no other way to reach `ReadingPlanControl`. The gate runs
     * unconditionally, exactly as classic's did: by the time a plan code arrives here (from the
     * selector, or from a history re-launch) a plan IS selected, because the selector calls
     * `startReadingPlan` before reporting the code back.
     */
    suspend fun loadReadingPlanDay(
        plan: String?, day: Int?, applicable: () -> Boolean = { true },
    ): DailyReadingLoad {
        val generation = ++readingDayLoadGeneration
        fun current() = generation == readingDayLoadGeneration && applicable()
        val code = plan ?: readingPlanControl.currentPlanCode
        if (code.isEmpty() || code in resetReadingPlanCodes) return DailyReadingLoad.NO_PLAN
        return try {
            if (!readingPlanControl.currentPlanExists(code)) {
                if (!current()) throw CancellationException("Superseded reading day")
                return DailyReadingLoad.NO_PLAN
            }
            val loadedDay = day ?: readingPlanControl.currentPlanDay(code)
            val dto = readingPlanControl.getDaysReading(loadedDay, code)
            val status = readingPlanControl.getReadingStatus(loadedDay, code)
            val ui = readingPlanUi(dto, status)
            // No host or selected-plan mutation until all suspend reads have finished.
            if (!current()) throw CancellationException("Superseded reading day")
            readingPlanControl.setReadingPlan(code)
            dayLoaded = loadedDay
            planCodeLoaded = code
            readingsDto = dto
            dailyReadingController?.setUi(ui)
            loadedReadingDay.value = LoadedReadingDay(code, loadedDay)
            readingPlanSelectorOffered = false // a plan is in use: a later removal must offer the selector again
            DailyReadingLoad.LOADED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!current()) throw CancellationException("Superseded reading day", e)
            Log.e(TAG_READING_PLAN, "Error showing daily readings", e)
            dailyReadingController?.showError()
            DailyReadingLoad.FAILED
        }
    }

    /**
     * Classic's two-step "no plan" behaviour, collapsed into one host-owned decision, see
     * [DailyReadingDeps.onPlanMissing]. True = the caller should offer the selector; false = the
     * selector was already declined once, so the flag is reset and the user is returned to the reader
     * (classic's `if (!readingPlanControl.isReadingPlanSelected) finish()`; the host only finishes when
     * it started on the day itself). The reset lets a later "Reading plan" open offer the selector again.
     */
    private fun offerReadingPlanSelector(): Boolean {
        if (readingPlanSelectorOffered) {
            readingPlanSelectorOffered = false
            returnToReadingOrFinish() // not finish(): in the single host that would close the reader too
            return false
        }
        readingPlanSelectorOffered = true
        return true
    }

    /** Classic's `pushUi()`: the DTO + ReadingStatus snapshot, passage names formatted host-side. */
    private suspend fun pushReadingPlanUi() {
        val dto = readingsDto ?: return
        val status = readingPlanControl.getReadingStatus(dayLoaded)
        dailyReadingController?.setUi(readingPlanUi(dto, status))
    }

    private fun readingPlanUi(dto: OneDaysReadingsDto, status: ReadingStatus): DailyReadingUi {
        val readings = withMonitor(bookNameMonitor) {
            val save = BookName.isFullBookName()
            BookName.setFullBookName(!platform.isPortrait)
            try {
                (1..dto.numReadings).map { i ->
                    ReadingItem(i, dto.getReadingKey(i).getName(), status.isRead(i))
                }
            } finally {
                BookName.setFullBookName(save)
            }
        }
        return DailyReadingUi(
            planName = dto.readingPlanInfo.planName ?: "",
            dayDesc = dto.dayDesc,
            dateString = dto.readingDateString,
            readings = readings,
            showSpeakAll = dto.numReadings > 1,
            allRead = status.isAllRead,
            isDateBasedPlan = dto.isDateBasedPlan,
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

    /**
     * After a reset of [code] (selector or day view): never reload it, and drop it if it is the loaded one.
     * Returns whether it was the loaded one.
     */
    private fun forgetResetReadingPlan(code: String): Boolean {
        resetReadingPlanCodes += code
        val wasLoaded = planCodeLoaded == code
        if (wasLoaded) forgetLoadedReadingPlan()
        return wasLoaded
    }

    /** SU-15g I1: drops the host-wide last-loaded (plan, day) and readings once their plan was reset/removed. */
    private fun forgetLoadedReadingPlan() {
        readingDayLoadGeneration++ // an in-flight load of the removed plan must not re-select it when it lands
        readingsDto = null
        planCodeLoaded = null
        loadedReadingDay.value = null
    }

    private fun onReadingPlanDone() {
        val dto = readingsDto ?: return
        val donePlanCode = planCodeLoaded
        shell.hostScope.launch {
            try {
                val nextDayToShow = readingPlanWrites.done(dto.readingPlanInfo, dayLoaded)
                if (nextDayToShow > 0) loadReadingPlanDay(planCodeLoaded, nextDayToShow)
                // -1: plan finished or user up to date. Classic finish()ed its own Activity back to the
                // reader; here that closes the reader's host (SU-15g D1), so pop to it instead.
                else {
                    // done() reset the plan on its last day: forget it, or the next open reloads it
                    if (readingPlanControl.currentPlanCode.isEmpty()) {
                        if (donePlanCode != null) forgetResetReadingPlan(donePlanCode) else forgetLoadedReadingPlan()
                    }
                    returnToReadingOrFinish()
                }
            } catch (e: Exception) {
                Log.e(TAG_READING_PLAN, "Error when Done daily reading", e)
                dailyReadingController?.showError()
            }
        }
    }

    private fun setReadingPlanCurrentDay() {
        val dto = readingsDto ?: return
        shell.hostScope.launch {
            try {
                readingPlanWrites.setCurrentDay(dto.readingPlanInfo, dayLoaded)
                loadReadingPlanDay(planCodeLoaded, dayLoaded)
            } catch (e: Exception) {
                Log.e(TAG_READING_PLAN, "Error setting current day", e)
                dailyReadingController?.showError()
            }
        }
    }

    /**
     * Classic's `android.app.DatePickerDialog` (`:245-256`) is ported to
     * [net.bible.sharedui.components.AbDatePickerDialog] via [DailyReadingController.showStartDatePicker]. This
     * function keeps only computing the initial/max dates from [readingsDto] and hands them over as UTC-midnight
     * millis ([ymdToUtcMidnightMillis]). Both are read off the LOCAL calendar fields (never a raw instant)
     * so the round trip through [confirmReadingPlanStartDate] reproduces exactly the same
     * year/month/day classic's `Calendar.get(YEAR/MONTH/DAY_OF_MONTH)` did, see
     * [ymdToUtcMidnightMillis]'s kdoc for why this sidesteps any real timezone conversion.
     */
    private fun showReadingPlanStartDatePicker() {
        val dto = readingsDto ?: return
        val nowTime = currentTimeMillis()
        val (startYear, startMonth, startDay) = platform.localYmd(dto.readingPlanInfo.startDate?.getTime() ?: nowTime)
        val (nowYear, nowMonth, nowDay) = platform.localYmd(nowTime)
        val initialUtcMillis = ymdToUtcMidnightMillis(startYear, startMonth, startDay)
        val maxUtcMillis = ymdToUtcMidnightMillis(nowYear, nowMonth, nowDay)
        dailyReadingController?.showStartDatePicker(initialUtcMillis, maxUtcMillis)
    }

    /**
     * [DailyReadingController.confirmStartDate]'s host callback: classic's picker-listener body
     * verbatim (`:245-256`). Re-derives the SAME starting instant [showReadingPlanStartDatePicker] built (existing
     * start date, or now's time-of-day) before moving it to the picked local date: classic's listener closed over
     * that exact `planStartDate` instance, so its time-of-day fields (untouched by `.set(y, m, d)`) came from
     * there, not from "now" at confirm time.
     */
    private fun confirmReadingPlanStartDate(year: Int, month1to12: Int, day: Int) {
        val dto = readingsDto ?: return
        val start = dto.readingPlanInfo.startDate?.getTime() ?: currentTimeMillis()
        val planStartDate = platform.withLocalYmd(start, year, month1to12, day)
        shell.hostScope.launch {
            readingPlanWrites.setStartDate(dto.readingPlanInfo, DbDate(planStartDate))
            loadReadingPlanDay(planCodeLoaded, dayLoaded)
        }
    }

    /** Classic's day-row primary line (date for a date-based plan, otherwise the day description). */
    private fun readingPlanDayPrimaryText(dto: OneDaysReadingsDto): String =
        if (dto.isDateBasedPlan && dto.readingDate != null) dto.readingDateString else dto.dayDesc

    /**
     * Classic `DailyReadingComposeActivity`'s bus `register(this) { ... }` pair (now the `SpeakChanges` state
     * subscription plus `DatabaseContainer.readingPlansSynced`), registered per DESTINATION rather than per host
     * (see [DailyReadingDeps.subscribeEvents]). `recreate()` is classic's own reaction to a plan sync, it now
     * recreates the whole host, which is the documented consequence (plan D5).
     */
    private fun subscribeDailyReadingEvents(): () -> Unit {
        val speak = SpeakChanges.changes.subscribeOnMain { if (it is SpeakChange.State) pushReadingPlanSpeakState() }
        val unsubscribePlans = subscribeToReadingPlanSync { shell.recreateHost() }
        // Seed the fresh controller with the CURRENT speak state. Classic never needed this: its
        // Activity (and its controller) merely paused behind the selector / day list, so the state
        // pushed by the last SpeakChanges notification was still there on return. Here the arm's composition is
        // disposed and the controller rebuilt, so without this seed the screen would sit at
        // SpeakState.NONE, and the screen gates the pause/play and stop buttons on
        // `speakState != NONE`, so a user who changed day or plan mid-speech would lose the
        // transport controls until the next SpeakChanges notification.
        pushReadingPlanSpeakState()
        return { speak.cancel(); unsubscribePlans() }
    }

    /** The day list's / selector's reaction to `DatabaseContainer.readingPlansSynced` (`controller.load()`). */
    private fun subscribeReadingPlansUpdated(onReadingPlansChanged: () -> Unit): () -> Unit =
        subscribeToReadingPlanSync(onReadingPlansChanged)

    /** Subscribes [onChanged] to reading-plan cloud syncs on main; each destination holds its own subscription. */
    private fun subscribeToReadingPlanSync(onChanged: () -> Unit): () -> Unit {
        val subscription = DatabaseContainer.readingPlansSynced.subscribeOnMain { onChanged() }
        return { subscription.cancel() }
    }

    /**
     * The plan import seam, ported from classic `:268-271`. The picked file goes straight to the InstallZip
     * destination as an ACTION_VIEW entry (confirm first), exactly the Intent classic built. Its result was ignored
     * (the imported plan is not auto-loaded), so no onResult.
     */
    private fun importPlan() {
        shell.hostScope.launch {
            val refs = documentPicker.openDocuments(listOf("application/zip"), multiple = false)
            if (refs.isNotEmpty()) openInstallZip(INSTALL_ZIP_ACTION_VIEW, refs) {}
        }
    }
}
