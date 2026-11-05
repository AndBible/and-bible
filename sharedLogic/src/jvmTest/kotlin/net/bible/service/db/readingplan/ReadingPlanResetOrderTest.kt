package net.bible.service.db.readingplan

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.database.DbDate
import net.bible.android.control.readingplan.ReadingStatus
import net.bible.android.database.IdType
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedlogic.platform.LocalCalendar
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReadingPlanResetOrderTest {
    @Test fun passageTicksSurviveStatusRecreation() = runTest {
        val dao = FakeReadingPlanDao()
        val repo = ReadingPlanRepository({ dao }, AppCoroutineScope(coroutineContext), unusedCalendar())
        val status = ReadingStatus("plan", 1, 2, repo)
        status.reloadStatus()
        assertTrue(!status.isAllRead)
        status.setRead(1)

        val reopened = ReadingStatus("plan", 1, 2, repo)
        reopened.reloadStatus()
        assertTrue(reopened.isRead(1))
        assertTrue(!reopened.isRead(2))
        assertTrue(!reopened.isAllRead)
        reopened.setRead(2)
        val again = ReadingStatus("plan", 1, 2, repo)
        again.reloadStatus()
        assertTrue(again.isAllRead)
        val nextDay = ReadingStatus("plan", 2, 2, repo)
        nextDay.reloadStatus()
        assertTrue(!nextDay.isRead(1) && !nextDay.isRead(2))
    }

    @Test fun pastStartDateSurvivesRepositoryRecreationWithoutResettingDay() = runTest {
        val dao = FakeReadingPlanDao()
        val repo = ReadingPlanRepository({ dao }, AppCoroutineScope(coroutineContext), unusedCalendar())
        val original = DbDate(1_800_000_000_000L)
        val past = DbDate(original.getTime() - 7L * 24 * 60 * 60 * 1000)
        repo.startPlan("plan", original)
        repo.setCurrentDay("plan", 3).join()
        repo.startPlan("plan", past)
        val reopened = ReadingPlanRepository({ dao }, AppCoroutineScope(coroutineContext), unusedCalendar())
        assertEquals(past, reopened.getStartDate("plan"))
        assertEquals(3, reopened.getCurrentDay("plan"))
    }

    @Test fun resetThenRestartKeepsTheStartRow() = runTest {
        val dao = FakeReadingPlanDao()
        val oldDate = DbDate(1_700_000_000_000L)
        val date = DbDate(1_800_000_000_000L)
        dao.updatePlan(ReadingPlan("plan", oldDate, 9))
        dao.addPlanStatus(ReadingPlanStatus("plan", 9, "1"))
        val repo = ReadingPlanRepository({ dao }, AppCoroutineScope(coroutineContext), unusedCalendar())
        val deleteGate = CompletableDeferred<Unit>()
        dao.deleteGate = deleteGate

        repo.resetPlan("plan")
        runCurrent()
        assertTrue(dao.deleteEntered.isCompleted, "Reset must be suspended at the plan-row deletion")
        launch { repo.startPlan("plan", date) }
        runCurrent()
        deleteGate.complete(Unit)
        advanceUntilIdle()

        val restarted = assertNotNull(dao.getPlan("plan"), "Restart row must survive the queued reset")
        assertEquals(date, restarted.planStartDate)
        assertEquals(1, restarted.planCurrentDay)
        assertNull(dao.getStatus("plan", 9))
    }

    @Test fun statusWrittenBeforeResetDoesNotSurvive() = runTest {
        val dao = FakeReadingPlanDao()
        val repo = ReadingPlanRepository({ dao }, AppCoroutineScope(coroutineContext), unusedCalendar())

        repo.setReadingStatus("plan", 3, "1")
        repo.resetPlan("plan")
        advanceUntilIdle()

        assertNull(dao.getStatus("plan", 3))
        assertNull(dao.getPlan("plan"))
    }

    private fun unusedCalendar(): LocalCalendar = Proxy.newProxyInstance(
        LocalCalendar::class.java.classLoader, arrayOf(LocalCalendar::class.java),
    ) { _, method, _ -> error("Unexpected calendar call: ${method.name}") } as LocalCalendar

    /** Copies rows at the DAO boundary, like Room, rather than sharing mutable entity instances. */
    private class FakeReadingPlanDao : ReadingPlanDao {
        private val plans = mutableMapOf<String, ReadingPlan>()
        private val statuses = mutableMapOf<Pair<String, Int>, ReadingPlanStatus>()
        var deleteGate: CompletableDeferred<Unit>? = null
        val deleteEntered = CompletableDeferred<Unit>()

        override suspend fun getPlan(planCode: String) = plans[planCode]?.copy()
        override suspend fun getStatus(planCode: String, planDay: Int) = statuses[planCode to planDay]?.copy()

        override suspend fun updatePlan(planCode: String, planStartDate: DbDate, planCurrentDay: Int, id: IdType) {
            plans[planCode] = ReadingPlan(planCode, planStartDate, planCurrentDay, id)
        }

        override suspend fun addPlanStatus(planCode: String, planDay: Int, readingStatus: String, id: IdType) {
            statuses[planCode to planDay] = ReadingPlanStatus(planCode, planDay, readingStatus, id)
        }

        override suspend fun deleteStatusesBeforeDay(planCode: String, planDay: Int) {
            statuses.keys.removeAll { it.first == planCode && it.second < planDay }
        }

        override suspend fun deleteStatusesForPlan(planCode: String) {
            statuses.keys.removeAll { it.first == planCode }
        }

        override suspend fun deletePlanInfo(planCode: String) {
            deleteEntered.complete(Unit)
            deleteGate?.await()
            plans.remove(planCode)
        }
    }
}
