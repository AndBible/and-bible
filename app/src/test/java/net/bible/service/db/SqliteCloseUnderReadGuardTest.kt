package net.bible.service.db

import androidx.room3.useReaderConnection
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.SettingsDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.concurrent.thread

/**
 * #3947: the app's own Room databases (built by [buildAppDatabase] in the test application's data directory) go
 * through [TestSqliteUseGuard]. Closing one while another thread steps a query on it is the JVM-SIGSEGV mechanism;
 * here it must instead be recorded with Room's real close path and the stepping thread, and fail the fixture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SqliteCloseUnderReadGuardTest {
    @Before fun setUp() { TestSqliteUseGuard.takeViolations() }
    @After fun tearDown() { TestSqliteUseGuard.takeViolations() }

    @Test fun closingAnAppDatabaseUnderARunningQueryIsReportedNotACrash() {
        val db = buildAppDatabase<SettingsDatabase>("close-under-read-guard.sqlite3")
        runBlocking { db.stringSettingDao().set("k", "v") }
        var readerFailure: Throwable? = null
        val reader = thread(name = "issue-3947-app-reader") {
            try {
                runBlocking {
                    db.useReaderConnection { connection ->
                        connection.usePrepared(LONG_SINGLE_STEP_QUERY) { it.step() }
                    }
                }
            } catch (t: Throwable) {
                readerFailure = t
            }
        }
        // Room runs the query on its query context (Dispatchers.IO), not on the calling thread.
        val stepper = awaitStepper(reader) { "query ended before the close could start: $readerFailure" }
        db.close()
        reader.join(60_000)
        assertTrue("reader must finish", !reader.isAlive)
        val violations = TestSqliteUseGuard.takeViolations()
        assertTrue("the close under the running step must be recorded", violations.isNotEmpty())
        val first = violations.first()
        // The debug-mode coroutine name is appended to the worker's thread name only while the coroutine runs.
        val stepperName = stepper.name.substringBefore(" @coroutine")
        assertTrue("stepper=$stepperName\n$first", Regex("while thread '${Regex.escape(stepperName)}( @coroutine#\\d+)?' was inside SQLite \\(step\\)").containsMatchIn(first))
        assertTrue(first, first.contains("close-under-read-guard.sqlite3"))
        assertTrue("closer stack is Room's real close path:\n$first", first.contains("androidx.room3.RoomDatabase.close"))
    }

    @Test fun theFixtureFailsOnARecordedViolation() {
        val db = buildAppDatabase<SettingsDatabase>("close-under-read-guard-2.sqlite3")
        runBlocking { db.stringSettingDao().set("k", "v") }
        val reader = thread(name = "issue-3947-app-reader-2") {
            runCatching { runBlocking { db.useReaderConnection { c -> c.usePrepared(LONG_SINGLE_STEP_QUERY) { it.step() } } } }
        }
        awaitStepper(reader) { "query ended before the close could start" }
        db.close()
        reader.join(60_000)
        try {
            TestSqliteUseGuard.requireNoViolations()
            fail("a recorded violation must fail the fixture")
        } catch (e: AssertionError) {
            assertTrue(e.message, e.message!!.contains("HARNESS FAILURE"))
        }
    }

    /** The thread inside SQLite's step for the guarded long query. */
    private fun awaitStepper(reader: Thread, ended: () -> String): Thread {
        val deadline = System.nanoTime() + 60_000_000_000L
        while (true) {
            Thread.getAllStackTraces().entries.firstOrNull { (_, stack) ->
                stack.any { it.methodName == "nativeStep" } &&
                    stack.any { it.className.contains("TestSqliteUseGuard") } &&
                    stack.any { it.className.startsWith(SqliteCloseUnderReadGuardTest::class.java.name) }
            }?.let { return it.key }
            if (!reader.isAlive) { reader.join(); fail(ended()) }
            if (System.nanoTime() > deadline) fail("nothing stepped the query")
            Thread.sleep(1)
        }
    }

    private companion object {
        const val LONG_SINGLE_STEP_QUERY =
            "WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c WHERE x < 10000000) SELECT count(*) FROM c"
    }
}
