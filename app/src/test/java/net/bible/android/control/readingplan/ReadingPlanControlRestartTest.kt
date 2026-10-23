package net.bible.android.control.readingplan

import androidx.room3.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TestBibleApplication
import net.bible.android.database.DbDate
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.android.platform.AndroidLocalCalendar
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.db.sqliteDriverFactory
import net.bible.service.readingplan.ReadingPlanInfoDto
import net.bible.service.readingplan.ReadingPlanTextFileDao
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import okio.Path.Companion.toPath
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class ReadingPlanControlRestartTest {
    @Test fun resetThenRestartIgnoresTheCallersStaleStartDate() = runTest {
        val db = Room.inMemoryDatabaseBuilder(application, ReadingPlanDatabase::class.java)
            .setDriver(sqliteDriverFactory()).build()
        val settings = testAppSettings()
        val originalPlan = settings.getString("reading_plan", null)
        val real = db.readingPlanDao()
        val deleteEntered = CompletableDeferred<Unit>()
        val deleteGate = CompletableDeferred<Unit>()
        val slow = object : ReadingPlanDao by real {
            override suspend fun deletePlanInfo(planCode: String) {
                deleteEntered.complete(Unit)
                deleteGate.await()
                real.deletePlanInfo(planCode)
            }
        }
        val oldDate = DbDate(1_700_000_000_000L)
        val date = DbDate(1_800_000_000_000L)
        val calendar = AndroidLocalCalendar()
        val repo = ReadingPlanRepository({ slow }, AppCoroutineScope(coroutineContext), calendar, today = { date })
        val source = object : ReadingPlanSource {
            override fun builtInPlanCodes() = emptyList<String>()
            override fun openBuiltInPlan(code: String): String = error("No plan text needed for restart")
        }
        val textDao = ReadingPlanTextFileDao(source, repo, testCoreStrings(), calendar,
            userPlanFolder = { "/nonexistent".toPath() }, providedPlans = { emptyMap() })
        val koin = GlobalContext.get()
        val control = ReadingPlanControl(koin.get(), koin.get(), repo, textDao, settings, calendar)
        try {
            real.updatePlan(ReadingPlan("plan", oldDate, 9))
            real.addPlanStatus(ReadingPlanStatus("plan", 9, "1"))
            val stalePlan = ReadingPlanInfoDto("plan").apply { startDate = oldDate }
            control.setReadingPlan("plan")

            control.reset("plan")
            deleteEntered.await()
            val restart = launch { control.startReadingPlan(stalePlan) }
            runCurrent()
            deleteGate.complete(Unit)
            // Room runs on its own executor, outside the virtual-time test scheduler.
            restart.join()
            advanceUntilIdle()

            val restarted = assertNotNull(real.getPlan("plan"), "Control must restart despite the stale DTO start date")
            assertEquals(date, restarted.planStartDate)
            assertEquals(1, restarted.planCurrentDay)
            assertNull(real.getStatus("plan", 9))
            assertEquals("plan", control.currentPlanCode)
        } finally {
            deleteGate.complete(Unit)
            advanceUntilIdle()
            settings.setString("reading_plan", originalPlan)
            db.close()
        }
    }
}
