package net.bible.android.control.readingplan

import androidx.room3.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TestBibleApplication
import net.bible.android.database.ReadingPlanDatabase
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.db.sqliteDriverFactory
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The writes of the daily-reading screen must finish in the app scope even when the screen's own scope is
 * cancelled while the first write is in flight (Done then Back, recreation).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // 35: in-memory Room, see ReadingPlanRepositoryStartDateTest
class ReadingPlanDayWritesTest {
    private lateinit var db: ReadingPlanDatabase
    private lateinit var control: ReadingPlanControl
    private val settings = testAppSettings()
    private var originalPlan: String? = null
    private val entered = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ReadingPlanDatabase::class.java)
            .setDriver(sqliteDriverFactory()).build()
        val real = db.readingPlanDao()
        // the FIRST plan-row write parks until the test has cancelled the caller's scope
        var first = true
        val gated = object : ReadingPlanDao by real {
            override suspend fun updatePlan(plan: ReadingPlanEntities.ReadingPlan) {
                if (first) { first = false; entered.complete(Unit); gate.await() }
                real.updatePlan(plan)
            }
        }
        val repo = ReadingPlanRepository(daoProvider = { gated })
        val source = object : ReadingPlanSource {
            override fun builtInPlanCodes() = listOf("three")
            override fun openBuiltInPlan(code: String) = "# Three\n1=Gen.1\n2=Gen.2\n3=Gen.3\n"
        }
        val dao = ReadingPlanTextFileDao(source, repo, testCoreStrings(), userPlanFolder = { File("/nonexistent") }, providedPlans = { emptyMap() })
        val koin = GlobalContext.get()
        control = ReadingPlanControl(koin.get(), koin.get(), repo, dao, settings)
        originalPlan = settings.getString("reading_plan", null)
        control.setReadingPlan("three")
    }

    @After fun tearDown() {
        appScope.coroutineContext[Job]?.cancel()
        settings.setString("reading_plan", originalPlan)
        db.close()
    }

    private val writes get() = ReadingPlanDayWrites(control, appScope.coroutineContext)

    /** Starts [block] in a caller scope, cancels that scope once the first write is parked, then lets the write go. */
    private fun cancelCallerMidWay(block: suspend () -> Unit) = runBlocking {
        val caller = CoroutineScope(Job() + Dispatchers.Default)
        caller.launch { block() }
        entered.await()
        caller.coroutineContext[Job]!!.cancel()
        gate.complete(Unit)
    }

    private suspend fun eventually(check: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(3000) { while (!check()) delay(20); true } ?: false

    @Test fun setCurrentDayIsNotLeftHalfAppliedWhenTheCallerIsCancelledMidWay() {
        val info = runBlocking { control.getDaysReading(1).readingPlanInfo }
        cancelCallerMidWay { writes.setCurrentDay(info, 3) }
        // day 2 stored then advanced to 3: both steps of the sequence landed
        assertEquals(true, runBlocking { eventually { control.currentPlanDay() == 3 } })
    }

    @Test fun startDateIsStoredWhenTheCallerIsCancelledMidWay() {
        val info = runBlocking { control.getDaysReading(1).readingPlanInfo }
        val start = java.util.Date(86_400_000L * 10_000)
        cancelCallerMidWay { writes.setStartDate(info, start) }
        assertEquals(true, runBlocking { eventually { control.getDaysReading(1).readingPlanInfo.startDate == start } })
    }
}
