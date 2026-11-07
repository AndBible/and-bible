package net.bible.shareddata

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import net.bible.service.db.TestSqliteUseGuard
import net.bible.service.db.openSqlite
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The JVM SIGSEGVs of #3947 (`sqlite3VdbeExec`, `sqlite3VdbeDeleteAuxData`, `pcache1FetchStage2`, `syncJournal`
 * under `nativeStep`/`nativeClose`) have one mechanism: Room 3's `Pool.close()` closes every connection, also one
 * another thread is using, and finalizes its cached statements while that thread is inside `sqlite3_step`.
 * [TestSqliteUseGuard] turns that into a reported harness failure that names the stepping thread.
 */
class TestSqliteUseGuardTest {
    @BeforeTest fun clear() { TestSqliteUseGuard.takeViolations() }
    @AfterTest fun clearAfter() { TestSqliteUseGuard.takeViolations() }

    @Test fun closingWhileAnotherThreadStepsWaitsForTheStepAndNamesTheStepper() {
        val connection = TestSqliteUseGuard.guard(":memory:", BundledSQLiteDriver().open(":memory:"))
        val statement = connection.prepare(LONG_SINGLE_STEP_QUERY)
        var stepped: Boolean? = null
        var afterClose: Throwable? = null
        val reader = thread(name = "guard-test-reader") {
            try {
                stepped = statement.step()
                statement.getLong(0)
            } catch (t: Throwable) {
                afterClose = t
            }
        }
        awaitInsideNative(connection, reader)
        // Room's order in Pool.close -> ConnectionWrapper.close: evict (finalize) cached statements, then close.
        statement.close()
        connection.close()
        reader.join(60_000)
        assertTrue(!reader.isAlive, "reader must finish")
        // The close waited for the step to return (instead of finalizing under it); the reader's next call is
        // after the close and gets the driver's ordinary error, as in production after a sequential close.
        assertEquals(true, stepped, "step must complete; reader failure: $afterClose")
        assertIs<SQLiteException>(afterClose)
        assertContains(afterClose!!.message!!, "statement is closed")
        // The statement close found the reader inside step; the connection close may also find it inside its
        // (failing) getLong, a second real violation.
        val violations = TestSqliteUseGuard.takeViolations()
        assertTrue(violations.size in 1..2, violations.joinToString("\n\n"))
        val violation = violations.first()
        assertContains(violation, "statement close")
        assertContains(violation, "guard-test-reader")
        assertContains(violation, "step")
        assertContains(violation, "closingWhileAnotherThreadStepsWaitsForTheStepAndNamesTheStepper")
    }

    /**
     * The stepping thread releases the lock and takes it again for its next call within nanoseconds; the closer
     * waiting on the non-fair lock needs microseconds to wake. Without the close taking precedence the reader barged
     * in, its next call succeeded and the close came late (47 of 60 rounds of this loop before the fix; the
     * once-flaky test above is the same scenario with one slow round).
     */
    @Test fun aCloseWaitingForAStepRunsBeforeTheSteppersNextCall() {
        val rounds = 20
        val readsAfterClose = (1..rounds).count {
            val connection = TestSqliteUseGuard.guard(":memory:", BundledSQLiteDriver().open(":memory:"))
            val statement = connection.prepare(SHORT_SINGLE_STEP_QUERY)
            var nextCall: Result<Long>? = null
            val reader = thread(name = "guard-test-barging-reader") {
                val stepped = runCatching { statement.step() }
                nextCall = if (stepped.isSuccess) runCatching { statement.getLong(0) } else Result.failure(stepped.exceptionOrNull()!!)
            }
            awaitInsideNative(connection, reader)
            statement.close()
            connection.close()
            reader.join(60_000)
            assertTrue(!reader.isAlive, "reader must finish")
            nextCall!!.isSuccess
        }
        TestSqliteUseGuard.takeViolations()
        assertEquals(0, readsAfterClose, "rounds in which the reader's next call ran before the waiting close")
    }

    @Test fun sequentialUseAfterCloseIsLeftToTheDriverAndIsNoViolation() {
        fun stepAfterClose(connection: SQLiteConnection): Throwable {
            val statement = connection.prepare("SELECT 1")
            statement.close()
            connection.close()
            return assertFailsWith<Throwable> { statement.step() }
        }
        val raw = stepAfterClose(BundledSQLiteDriver().open(":memory:"))
        val guarded = stepAfterClose(TestSqliteUseGuard.guard(":memory:", BundledSQLiteDriver().open(":memory:")))
        assertEquals(raw::class, guarded::class)
        assertEquals(raw.message, guarded.message)
        assertTrue(TestSqliteUseGuard.takeViolations().isEmpty())
    }

    @Test fun requireNoViolationsReportsAndClearsWhatWasRecorded() {
        val connection = TestSqliteUseGuard.guard(":memory:", BundledSQLiteDriver().open(":memory:"))
        val statement = connection.prepare(LONG_SINGLE_STEP_QUERY)
        val reader = thread(name = "guard-test-reader-2") { runCatching { statement.step() } }
        awaitInsideNative(connection, reader)
        statement.close()
        connection.close()
        reader.join(60_000)
        val failure = assertFailsWith<AssertionError> { TestSqliteUseGuard.requireNoViolations() }
        assertContains(failure.message!!, "HARNESS FAILURE")
        assertContains(failure.message!!, "guard-test-reader-2")
        TestSqliteUseGuard.requireNoViolations()
    }

    @Test fun testDirectoryConnectionsAreGuarded() {
        val path = "${testDatabaseDir()}/guarded.sqlite3"
        openSqlite(path).use { assertTrue(TestSqliteUseGuard.isGuarded(it)) }
    }

    @Test fun anUnusedConnectionClosesWithoutAViolation() {
        val connection = TestSqliteUseGuard.guard(":memory:", BundledSQLiteDriver().open(":memory:"))
        connection.prepare("SELECT 1").use { assertTrue(it.step()) }
        connection.close()
        assertNull(TestSqliteUseGuard.threadInsideNative(connection))
        assertTrue(TestSqliteUseGuard.takeViolations().isEmpty())
        assertFailsWith<SQLiteException> { connection.prepare("SELECT 1") }
    }

    /** An interrupted closer (cancelled coroutine, interrupted thread) must still close and keep its interrupt flag. */
    @Test fun anInterruptedCloserStillClosesRecordsTheViolationAndKeepsItsInterruptFlag() {
        val connection = TestSqliteUseGuard.guard(":memory:", BundledSQLiteDriver().open(":memory:"))
        val statement = connection.prepare(LONG_SINGLE_STEP_QUERY)
        val reader = thread(name = "guard-test-interrupt-reader") { runCatching { statement.step() } }
        awaitInsideNative(connection, reader)
        var closeFailure: Throwable? = null
        var flagAfterClose = false
        val closer = thread(name = "guard-test-interrupted-closer") {
            Thread.currentThread().interrupt()
            try { connection.close() } catch (t: Throwable) { closeFailure = t }
            flagAfterClose = Thread.currentThread().isInterrupted
        }
        closer.join(120_000)
        reader.join(60_000)
        assertTrue(!closer.isAlive && !reader.isAlive, "both threads must finish")
        assertNull(closeFailure, "close must not throw: $closeFailure")
        assertTrue(flagAfterClose, "the interrupt status must be restored")
        assertTrue(TestSqliteUseGuard.takeViolations().isNotEmpty(), "the violation must still be recorded")
        assertFailsWith<SQLiteException> { connection.prepare("SELECT 1") }
    }

    private fun awaitInsideNative(connection: SQLiteConnection, reader: Thread) {
        val deadline = System.nanoTime() + 60_000_000_000L
        while (TestSqliteUseGuard.threadInsideNative(connection) !== reader) {
            if (!reader.isAlive) fail("the query finished before the close could start; make it longer")
            if (System.nanoTime() > deadline) fail("reader never entered SQLite")
            Thread.sleep(1)
        }
    }

    private companion object {
        /** One `sqlite3_step` that runs for seconds: the window in which the close lands. */
        const val LONG_SINGLE_STEP_QUERY =
            "WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c WHERE x < 10000000) SELECT count(*) FROM c"

        /** Long enough for the close to start inside the step, short enough to repeat. */
        const val SHORT_SINGLE_STEP_QUERY =
            "WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM c WHERE x < 300000) SELECT count(*) FROM c"
    }
}
