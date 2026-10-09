package net.bible.service.db.readingplan

import androidx.room3.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TestBibleApplication
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.service.db.sqliteDriverFactory
import net.bible.sharedcore.platform.AppCoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The repository's fire-and-forget writes run in the injected application scope and land in call order: a
 * slow first status write must not be overtaken by (and then overwrite) a later one, and a failing write must
 * neither crash nor stop the writes queued after it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // 35: ON CONFLICT DO UPDATE needs the newer SQLite (see ReadingPlanDaoTest)
class ReadingPlanRepositoryWritesTest {
    private lateinit var db: ReadingPlanDatabase
    private val appScope = AppCoroutineScope()
    private var statusWrites = 0

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ReadingPlanDatabase::class.java)
            .setDriver(sqliteDriverFactory()).build()
    }

    @After fun tearDown() {
        appScope.coroutineContext[Job]?.cancel()
        db.close()
    }

    /** A DAO whose FIRST status write is slow ([firstFails]: throws instead). */
    private fun repo(firstFails: Boolean = false): ReadingPlanRepository {
        val real = db.readingPlanDao()
        val dao = object : ReadingPlanDao by real {
            override suspend fun addPlanStatus(status: ReadingPlanStatus) {
                if (statusWrites++ == 0) {
                    if (firstFails) throw IllegalStateException("planted write failure")
                    delay(300)
                }
                real.addPlanStatus(status)
            }
        }
        return ReadingPlanRepository(daoProvider = { dao }, appScope = appScope)
    }

    @Test fun statusWritesLandInCallOrder() = runBlocking {
        val repo = repo()
        val first = repo.setReadingStatus("p", 1, "first tick")
        val second = repo.setReadingStatus("p", 1, "both ticks")
        withTimeout(10_000) { first.join(); second.join() }
        assertEquals("both ticks", repo.getReadingStatus("p", 1))
    }

    @Test fun aFailingWriteDoesNotStopTheWritesQueuedAfterIt() = runBlocking {
        val repo = repo(firstFails = true)
        val failed = repo.setReadingStatus("p", 1, "lost")
        val next = repo.setReadingStatus("p", 2, "kept")
        withTimeout(10_000) { failed.join(); next.join() }
        assertEquals(null, repo.getReadingStatus("p", 1))
        assertEquals("kept", repo.getReadingStatus("p", 2))
    }
}
