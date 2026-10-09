package net.bible.android.control.readingplan

import kotlinx.coroutines.Job
import net.bible.sharedcore.platform.AppCoroutineScope
import androidx.room3.Room
import kotlinx.coroutines.delay
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

/** `done` moves the stored current day on and reads it back, so the write must have landed by then. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // 35: in-memory Room, see ReadingPlanRepositoryStartDateTest
class ReadingPlanControlDoneTest {
    private lateinit var db: ReadingPlanDatabase
    private lateinit var control: ReadingPlanControl
    private val settings = testAppSettings()
    private var originalPlan: String? = null
    private val appScope = AppCoroutineScope()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ReadingPlanDatabase::class.java)
            .setDriver(sqliteDriverFactory()).build()
        // a slow plan-row write: a caller that reads the day back without waiting for its write sees the old day
        val real = db.readingPlanDao()
        val slow = object : ReadingPlanDao by real {
            override suspend fun updatePlan(plan: ReadingPlanEntities.ReadingPlan) { delay(300); real.updatePlan(plan) }
        }
        val repo = ReadingPlanRepository(daoProvider = { slow }, appScope = appScope)
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

    @Test fun doneOnTheCurrentDayMovesToTheNextDayAndTheNewDayIsReadBack() = runBlocking {
        assertEquals(1, control.currentPlanDay())
        val info = control.getDaysReading(1).readingPlanInfo
        control.done(info, 1, false)
        assertEquals(2, control.currentPlanDay())
        control.done(info, 2, false)
        assertEquals(3, control.currentPlanDay())
    }

    @Test fun forcedDoneOnALaterDayStoresThatDayFirst() = runBlocking {
        val info = control.getDaysReading(1).readingPlanInfo
        control.done(info, 2, true)
        assertEquals(3, control.currentPlanDay()) // day 2 stored, found current, then advanced
    }
}
