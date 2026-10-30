package net.bible.android.view.activity.nav

import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.database.DbDate
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.readingplan.ReadingPlanHost
import net.bible.sharedui.app.readingplan.ReadingPlanHostPlatform
import net.bible.sharedui.strings.AndroidStrings
import net.bible.test.DatabaseResetter.resetDatabase
import net.bible.test.awaitHostUntil
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingPlanSyncStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var unsubscribe: (() -> Unit)? = null
    private val dao get() = DatabaseContainer.instance.readingPlanDb.readingPlanDao()
    // The global test reset replaces AppCoroutineScope; do not retain a Koin repository's old write scope.
    private val control by lazy {
        val koin = GlobalContext.get()
        ReadingPlanControl(
            koin.get(), koin.get(),
            net.bible.service.db.readingplan.ReadingPlanRepository(
                { dao }, net.bible.sharedcore.platform.AppCoroutineScope(scope.coroutineContext),
                net.bible.android.platform.AndroidLocalCalendar(),
            ),
            koin.get(), net.bible.service.common.CommonUtils.settings,
            net.bible.android.platform.AndroidLocalCalendar(),
        )
    }

    @After fun tearDown() {
        unsubscribe?.invoke()
        scope.cancel()
        resetDatabase()
    }

    private suspend fun syncCompleted() {
        val accessor = DatabaseContainer.getDatabaseAccessorFactories(DatabaseContainer.instance)
            .map { it() }.single { it.category == SyncableDatabaseDefinition.READINGPLANS }
        accessor._reactToUpdates!!.invoke(emptyList())
    }

    private suspend fun start(code: String = PLAN) {
        dao.updatePlan(ReadingPlan(code, DbDate(1_800_000_000_000L), 1))
        control.setReadingPlan(code)
    }

    private fun host(): ReadingPlanHost = ReadingPlanHost(
        shell = object : HostShell {
            override val hostScope = scope
            override fun exitHost() = Unit
            override fun setWindowTitle(title: String) = Unit
            // Like the desktop/iOS shell, this does not reconstruct the navigation destination.
            override fun recreateHost() = Unit
            override fun openReading() = Unit
            override fun setHistoryRoute(owner: Any, route: String) = Unit
            override fun clearHistoryRoute(owner: Any) = Unit
            override fun goBackInHistory() = false
            override fun navigate(route: String) = Unit
        },
        strings = AndroidStrings(ApplicationProvider.getApplicationContext()),
        platform = object : ReadingPlanHostPlatform {
            override val isPortrait = true
            override fun localYmd(epochMs: Long) = Triple(2026, 1, 1)
            override fun withLocalYmd(epochMs: Long, year: Int, month1to12: Int, day: Int) = epochMs
        },
        openInstallZip = { _, _, _ -> },
        documentPicker = object : DocumentPicker {
            override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean) = emptyList<DocumentRef>()
            override suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef? = null
            override suspend fun openDocumentTree(): DocumentRef? = null
        },
        readingPlanControlProvider = { control },
        speakControlProvider = { GlobalContext.get().get() },
        appScopeProvider = { net.bible.sharedcore.platform.AppCoroutineScope(scope.coroutineContext) },
    )

    @Test fun syncCompletionRepushesTheOpenDayBeforeTheNextTick() {
        runBlocking { start() }
        val host = host()
        val deps = host.navDeps(NavHostController(ApplicationProvider.getApplicationContext())).dailyReading
        val controller = deps.controllerFor({}, {})
        runBlocking { host.loadReadingPlanDay(PLAN, 1) }
        unsubscribe = deps.subscribeEvents()
        assertFalse(controller.ui.value.readings.first().isRead)

        // A patch from another device marks the first passage read while this day stays open.
        runBlocking {
            dao.addPlanStatus(ReadingPlanStatus(PLAN, 1,
                """{"chapterReadArray":[{"readingNumber":1,"isRead":true}]}"""))
            syncCompleted()
        }
        awaitHostUntil("the open day must show the synced tick without reloading or tapping") {
            controller.ui.value.readings.first().isRead
        }
        controller.toggleRead(1)
        awaitHostUntil("the displayed read tick is now unticked") { !controller.ui.value.readings.first().isRead }
        assertFalse(runBlocking { control.getReadingStatus(1).isRead(1) })
    }

    @Test fun syncedResetWhileTheSelectorCoversTheDayForgetsTheLoadedPlan() {
        runBlocking { start() }
        val host = host()
        val deps = host.navDeps(NavHostController(ApplicationProvider.getApplicationContext()))
        deps.dailyReading.controllerFor({}, {})
        runBlocking { host.loadReadingPlanDay(PLAN, 1) }
        unsubscribe = deps.selector.subscribeEvents {}
        runBlocking {
            dao.deletePlanInfo(PLAN)
            syncCompleted()
        }
        awaitHostUntil("Back must not reload the synced-reset plan from its old route") { host.planCodeLoaded == null }
        assertEquals(setOf(PLAN), host.resetReadingPlanCodes)
    }

    @Test fun syncedResetClearsCurrentPlanWithoutAnOpenScreen() = runBlocking {
        start()
        dao.deleteStatusesForPlan(PLAN)
        dao.deletePlanInfo(PLAN)
        syncCompleted()
        assertEquals("a synced reset must behave like a local reset", "", control.currentPlanCode)
    }

    @Test fun resetAndRestartInTheSameSyncKeepsTheCurrentPlan() = runBlocking {
        start()
        dao.deletePlanInfo(PLAN)
        dao.updatePlan(ReadingPlan(PLAN, DbDate(1_900_000_000_000L), 1))
        syncCompleted()
        assertEquals(PLAN, control.currentPlanCode)
    }

    @Test fun resettingAnotherPlanKeepsTheCurrentSelection() = runBlocking {
        start("other")
        start()
        dao.deletePlanInfo("other")
        syncCompleted()
        assertEquals(PLAN, control.currentPlanCode)
    }

    @Test fun syncDuringPlanStartDoesNotDeselectThePickedPlan() = runBlocking {
        // runBlocking owns the Robolectric main thread, so nothing here may need Dispatchers.Main.
        val bg = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val real = dao
        val inserting = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : net.bible.android.database.readingplan.ReadingPlanDao by real {
            override suspend fun updatePlan(plan: ReadingPlan) {
                inserting.complete(Unit)
                gate.await()
                real.updatePlan(plan)
            }
        }
        val koin = GlobalContext.get()
        val slowControl = ReadingPlanControl(
            koin.get(), koin.get(),
            net.bible.service.db.readingplan.ReadingPlanRepository(
                { slow }, net.bible.sharedcore.platform.AppCoroutineScope(bg.coroutineContext),
                net.bible.android.platform.AndroidLocalCalendar(),
            ),
            koin.get(), net.bible.service.common.CommonUtils.settings,
            net.bible.android.platform.AndroidLocalCalendar(),
        )
        val starting = bg.launch { slowControl.startReadingPlan(net.bible.service.readingplan.ReadingPlanInfoDto(PLAN)) }
        inserting.await()
        // An incoming sync lands after the user picked the plan but before its row exists.
        syncCompleted()
        gate.complete(Unit)
        starting.join()
        assertNotNull(real.getPlan(PLAN))
        assertEquals("the sync must not deselect the plan the user just started", PLAN, slowControl.currentPlanCode)
        bg.cancel()
    }

    private lateinit var openController: net.bible.sharedcore.readingplan.DailyReadingController

    private fun openDay(): ReadingPlanHost {
        runBlocking { start() }
        val host = host()
        val deps = host.navDeps(NavHostController(ApplicationProvider.getApplicationContext())).dailyReading
        openController = deps.controllerFor({}, {})
        runBlocking { host.loadReadingPlanDay(PLAN, null) }
        unsubscribe = deps.subscribeEvents()
        return host
    }

    @Test fun syncedCurrentDayMovesTheScreenThatWasOnTheCurrentDay() {
        val host = openDay()
        assertEquals(1, host.dayLoaded)
        runBlocking {
            dao.updatePlan(dao.getPlan(PLAN)!!.apply { planCurrentDay = 2 })
            syncCompleted()
        }
        awaitHostUntil("another device finished day 1, so the open current day must follow") { host.dayLoaded == 2 }
    }

    @Test fun syncedCurrentDayKeepsADayTheUserOpenedExplicitly() {
        val host = openDay()
        runBlocking { host.loadReadingPlanDay(PLAN, 5) }
        runBlocking {
            dao.updatePlan(dao.getPlan(PLAN)!!.apply { planCurrentDay = 2 })
            syncCompleted()
            dao.addPlanStatus(ReadingPlanStatus(PLAN, 5,
                """{"chapterReadArray":[{"readingNumber":1,"isRead":true}]}"""))
            syncCompleted()
        }
        // The tick on day 5 arrives only if the day-5 screen was refreshed, not replaced by the current day.
        awaitHostUntil("the explicitly opened day 5 shows the synced tick") {
            openController.ui.value.readings.first().isRead
        }
        assertEquals(5, host.dayLoaded)
    }

    companion object { private const val PLAN = "y1ot1nt1_OTandNT" }
}
