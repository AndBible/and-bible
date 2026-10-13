package net.bible.service.llm

import androidx.room3.useWriterConnection
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.migrations.workspacesMigrations
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Diagnostic only: native finalization is deferred on the retained query's exact handles. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WorkspaceNativeLifetimeWitnessTest {
    private companion object {
        val ids = AtomicInteger()
        val pending = mutableListOf<CleanupOwner>()
        val uncertainBoundaries = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Boundary, Boolean>())
    }

    internal class Boundary(val generation: Int, val held: Boolean, val probe: Probe? = null) : SQLiteDriver {
        private val driver = BundledSQLiteDriver()
        private val connections = mutableListOf<SQLiteConnection>()
        private val deferredStatements = mutableListOf<SQLiteStatement>()
        private val deferredConnections = mutableListOf<SQLiteConnection>()
        private val events = mutableListOf<String>()
        private val uncertain = java.util.IdentityHashMap<Any, Throwable>()
        private val released = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())

        @Synchronized private fun closeOnce(handle: Any, close: () -> Unit) {
            uncertain[handle]?.let { throw it }
            if (released.contains(handle)) return
            try { close(); released.add(handle) } catch (e: Throwable) {
                uncertain[handle] = e
                synchronized(uncertainBoundaries) { uncertainBoundaries.add(this) }
                throw e
            }
        }
        val armed = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val completed = AtomicBoolean()
        var container: DatabaseContainer? = null
        var database: WorkspaceDatabase? = null
        @Volatile var selectedConnection: SQLiteConnection? = null
        @Volatile var selectedStatement: SQLiteStatement? = null
        @Volatile var selectedConnectionId = 0
        @Volatile var selectedStatementId = 0

        @Synchronized fun event(name: String, stack: Boolean = false) {
            val value = "${events.size + 1} $name generation=$generation thread=${Thread.currentThread().name}/${Thread.currentThread().id} completed=${completed.get()}"
            events.add(value)
            println("WORKSPACE-NATIVE $value")
            if (stack) Throwable("WORKSPACE-NATIVE stack: $name").printStackTrace(System.out)
        }

        @Synchronized fun index(name: String) = events.indexOfFirst { it.contains(" $name") }

        override fun open(fileName: String): SQLiteConnection {
            val connection = driver.open(fileName)
            val cid = ids.incrementAndGet()
            synchronized(this) { connections.add(connection) }
            event("connection-open C=$cid file=$fileName container=${System.identityHashCode(container)} db=${System.identityHashCode(database)}")
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    val sid = ids.incrementAndGet()
                    return object : SQLiteStatement by statement {
                        override fun step(): Boolean {
                            if (sql == "SELECT * FROM GlobalTextDisplaySettings LIMIT 1" && armed.compareAndSet(true, false)) {
                                selectedConnection = connection
                                selectedStatement = statement
                                selectedConnectionId = cid
                                selectedStatementId = sid
                                event("query-entry C=$cid S=$sid sql=$sql", stack = true)
                                if (held) {
                                    event("hold-entry C=$cid S=$sid")
                                    entered.countDown()
                                    if (!release.await(10, TimeUnit.SECONDS)) {
                                        event("HARNESS-FAILURE hold-timeout C=$cid S=$sid")
                                        error("HARNESS FAILURE: hold timeout")
                                    }
                                    event("hold-release C=$cid S=$sid")
                                }
                                event("delegate-step-enter C=$cid S=$sid")
                                return try { statement.step().also { event("delegate-step-exit C=$cid S=$sid result=$it") } }
                                catch (e: Throwable) { event("delegate-step-failure C=$cid S=$sid type=${e.javaClass.name}"); throw e }
                            }
                            return statement.step()
                        }

                        override fun reset() {
                            if (selectedStatement === statement) event("statement-reset-enter C=$cid S=$sid")
                            statement.reset()
                            if (selectedStatement === statement) event("statement-reset-exit C=$cid S=$sid")
                        }

                        override fun clearBindings() {
                            if (selectedStatement === statement) event("clear-bindings-enter C=$cid S=$sid")
                            statement.clearBindings()
                            if (selectedStatement === statement) event("clear-bindings-exit C=$cid S=$sid")
                        }

                        override fun close() {
                            if (selectedStatement === statement) {
                                event("statement-finalize-attempt C=$cid S=$sid", stack = true)
                                if (!completed.get()) {
                                    synchronized(this@Boundary) { deferredStatements.add(statement) }
                                    event("statement-finalize-suppressed C=$cid S=$sid")
                                    return
                                }
                            }
                            closeOnce(statement) { statement.close() }
                            if (selectedStatement === statement) event("statement-finalize-native-exit C=$cid S=$sid")
                        }
                    }
                }

                override fun close() {
                    if (selectedConnection === connection) {
                        event("connection-close-attempt C=$cid S=${selectedStatementId}", stack = true)
                        if (!completed.get()) {
                            synchronized(this@Boundary) { deferredConnections.add(connection) }
                            event("connection-close-suppressed C=$cid")
                            return
                        }
                    }
                    closeOnce(connection) { connection.close() }
                    if (selectedConnection === connection) event("connection-close-native-exit C=$cid")
                }
            }
        }

        fun retainAdapters(connection: SQLiteConnection, statement: SQLiteStatement?) {
            selectedConnection = connection
            connections.add(connection)
            deferredConnections.add(connection)
            if (statement != null) {
                selectedStatement = statement
                deferredStatements.add(statement)
            }
        }

        /** Uncertain delegate outcomes are permanent handoff state, never retried as Java no-ops. */
        @Synchronized fun finishNativeCleanup() {
            var failure: Throwable? = null
            fun record(e: Throwable) {
                if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e)
            }
            fun unique(resources: List<SQLiteConnection>): List<SQLiteConnection> {
                val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SQLiteConnection, Boolean>())
                return resources.filter { seen.add(it) }
            }
            val statements = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SQLiteStatement, Boolean>())
            deferredStatements.toList().filter { statements.add(it) }.forEach { statement ->
                if (!uncertain.containsKey(statement)) {
                    try {
                        closeOnce(statement) { statement.close() }
                        deferredStatements.removeAll { it === statement }
                    } catch (e: Throwable) {
                        uncertain[statement] = e
                        event("UNCERTAIN-NATIVE-CLOSE statement retained-exact-handle")
                    }
                    // Known injected failure is AFTER successful release, not an uncertain native result.
                    if (!uncertain.containsKey(statement)) try { probe?.nativeClose() } catch (e: Throwable) { record(e) }
                }
            }
            unique(deferredConnections + connections).forEach { connection ->
                if (!(connection === selectedConnection && deferredStatements.isNotEmpty()) && !uncertain.containsKey(connection)) {
                    try {
                        closeOnce(connection) { connection.close() }
                        deferredConnections.removeAll { it === connection }
                        connections.removeAll { it === connection }
                    } catch (e: Throwable) {
                        uncertain[connection] = e
                        event("UNCERTAIN-NATIVE-CLOSE connection retained-exact-handle")
                    }
                    if (!uncertain.containsKey(connection)) try { probe?.nativeClose() } catch (e: Throwable) { record(e) }
                }
            }
            if (uncertain.isNotEmpty()) {
                synchronized(uncertainBoundaries) { uncertainBoundaries.add(this) }
                // Fresh wrapper avoids adding a Throwable as suppressed onto itself on later handoff.
                val handoff = IllegalStateException("HARNESS FAILURE: uncertain native close requires handoff")
                uncertain.values.forEach { handoff.addSuppressed(it) }
                if (failure == null && uncertain.size == 1) failure = uncertain.values.first()
                else record(handoff)
                event("uncertain-native-handoff count=${uncertain.size} no-retry-no-retirement")
            }
            failure?.let { event("native-cleanup-failure retainedStatements=${deferredStatements.size} retainedConnections=${deferredConnections.size + connections.size}"); throw it }
            event("native-cleanup-complete")
        }
    }

    /** One exact experiment owner, retained before any setup mutation or worker starts. */
    internal class CleanupOwner(val boundary: Boundary, val container: DatabaseContainer, val probe: Probe?) {
        var database: WorkspaceDatabase? = null
        var reader: Thread? = null
        var closer: Thread? = null
        var published = true
        private var exactContainerClosed = false
        private var exactContainerFailure: Throwable? = null
        var producer: kotlinx.coroutines.Job? = null
        var restored = false
        var finished = false

        fun finish() {
            if (finished) return
            boundary.release.countDown()
            var failure: Throwable? = null
            fun record(e: Throwable) { if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e) }
            try {
                reader?.join(if (probe != null) 20 else 5_000)
                closer?.join(if (probe != null) 20 else 5_000)
                producer?.let { kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(5_000) { it.join() } } }
            } catch (e: Throwable) { record(e); if (e is InterruptedException) Thread.interrupted() }
            if (reader?.isAlive == true || closer?.isAlive == true || producer?.isCompleted == false) {
                boundary.event("HARNESS-FAILURE pending-exact-owner reader=${reader?.isAlive} closer=${closer?.isAlive}")
                record(IllegalStateException("HARNESS FAILURE: retained incomplete cleanup owner"))
                throw failure!!
            }
            fun attempt(block: () -> Unit) { try { block() } catch (e: Throwable) { record(e) } }
            if (!published && !exactContainerClosed) attempt {
                // closeAll flushes this exact store BEFORE cancelling its own writer and closing its DBs.
                exactContainerFailure?.let { throw it }
                try { container.closeAll() } catch (e: Throwable) {
                    // An actual close failure cannot be confirmed by idempotent no-op retries.
                    exactContainerFailure = e
                    throw e
                }
                val writer = checkNotNull(container.settingsScope.coroutineContext[kotlinx.coroutines.Job])
                kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(5_000) { writer.join() } }
                check(writer.isCompleted)
                exactContainerClosed = true
                boundary.event("exact-unpublished-container-close-confirmed")
            }
            attempt { database?.close() }
            attempt { boundary.finishNativeCleanup() }
            // Native cleanup failure must not skip restoration after all exact workers complete.
            if (!restored) attempt {
                DatabaseResetter.resetDatabase()
                assertFalse(DatabaseContainer.replacing)
                assertEquals(0L, DatabaseContainer.replaceEpoch)
                assertNotSame(container, DatabaseContainer.instance)
                restored = true
                probe?.restored = true
                boundary.event("cleanup-restoration-verified workers-joined ai-admission-unmodified")
            }
            if (failure == null) {
                finished = true
                synchronized(pending) { pending.remove(this) }
            }
            failure?.let { throw it }
        }
    }

    internal fun retainCleanupOwner(boundary: Boundary, container: DatabaseContainer): CleanupOwner =
        CleanupOwner(boundary, container, null).also { it.published = false; synchronized(pending) { pending.add(it) } }

    @Test fun returnedUnpublishedContainerIsClosedBeforeOwnerRetirement() {
        DatabaseResetter.resetDatabase()
        val previousFactory = DatabaseContainer.containerFactory
        val boundary = Boundary(30, false)
        var owner: CleanupOwner? = null
        val injected = IllegalStateException("after factory return before publication")
        val wrapper: () -> DatabaseContainer = {
            previousFactory().also { container ->
                owner = retainCleanupOwner(boundary, container)
                boundary.container = container
                throw injected
            }
        }
        DatabaseContainer.containerFactory = wrapper
        try {
            assertSame(injected, runCatching { DatabaseContainer.instance }.exceptionOrNull())
            DatabaseContainer.containerFactory = previousFactory
            owner!!.finish()
            val exact = owner!!.container
            assertTrue("exact unpublished settings writer completed", exact.settingsScope.coroutineContext[kotlinx.coroutines.Job]!!.isCompleted)
            assertTrue("exact unpublished Workspace DAO closed", runCatching { CommonUtils.globalTextDisplaySettings; kotlinx.coroutines.runBlocking { exact.workspaceDb.globalTextDisplaySettingsDao().get() } }.isFailure)
            val all = exact.javaClass.getDeclaredMethod("getAllDatabases").apply { isAccessible = true }.invoke(exact) as Array<*>
            all.forEach { resource ->
                val db = resource as androidx.room3.RoomDatabase
                assertTrue("every exact unpublished database rejects use after close", runCatching {
                    kotlinx.coroutines.runBlocking { db.useWriterConnection { } }
                }.isFailure)
            }
            assertTrue(owner!!.finished)
        } finally {
            if (DatabaseContainer.containerFactory === wrapper) DatabaseContainer.containerFactory = previousFactory
            owner?.container?.closeAll()
            owner?.container?.settingsScope?.coroutineContext?.get(kotlinx.coroutines.Job)?.let { kotlinx.coroutines.runBlocking { it.join() } }
            DatabaseResetter.resetDatabase()
        }
    }

    @Test fun uncertainStatementCloseCannotBeRetiredByNoOpRetry() = uncertainClose(true)
    @Test fun uncertainConnectionCloseCannotBeRetiredByDuplicateNoOp() = uncertainClose(false)

    private fun uncertainClose(statementFailure: Boolean) {
        val realConnection = BundledSQLiteDriver().open(":memory:")
        val realStatement = realConnection.prepare("SELECT 1")
        val injected = IllegalStateException("adapter flag-before-throw")
        val calls = AtomicInteger()
        val connectionCalls = AtomicInteger()
        val statement = object : SQLiteStatement by realStatement {
            override fun close() {
                if (calls.incrementAndGet() == 1) throw injected
                // Emulate bundled isClosed=true: a later close returns without native release.
            }
        }
        val connection = object : SQLiteConnection by realConnection {
            override fun close() {
                connectionCalls.incrementAndGet()
                if (!statementFailure && calls.incrementAndGet() == 1) throw injected
            }
        }
        val boundary = Boundary(6, false)
        boundary.retainAdapters(connection, if (statementFailure) statement else null)
        try {
            assertSame(injected, runCatching { boundary.finishNativeCleanup() }.exceptionOrNull())
            assertNotNull("uncertain native outcome must remain a handoff, not cleanup success",
                runCatching { boundary.finishNativeCleanup() }.exceptionOrNull())
            assertEquals("never retry uncertain delegate", 1, calls.get())
            assertEquals("statement uncertainty blocks its connection", if (statementFailure) 0 else 1, connectionCalls.get())
            assertEquals(-1, boundary.index("native-cleanup-complete"))
            assertTrue("permanent exact uncertain owner retained", synchronized(uncertainBoundaries) { uncertainBoundaries.contains(boundary) })
        } finally {
            // Adapter never invokes native close; exact real handles are safely freed here once.
            realStatement.close()
            realConnection.close()
        }
    }

    @Test fun controlledResetWitnessAndPostCompletionControl() {
        exercise(1, held = true)
        exercise(2, held = false)
    }

    internal class Probe(val mode: String) {
        val injected = IllegalStateException("injected-$mode")
        val parked = CountDownLatch(1)
        val unpark = CountDownLatch(1)
        val closeAttempts = AtomicInteger()
        var restored = false
        var boundary: Boundary? = null
        var reader: Thread? = null
        var retry: (() -> Unit)? = null
        fun nativeClose() {
            if (mode == "close" && closeAttempts.incrementAndGet() == 1) throw injected
        }
    }

    @Test fun setupFailureRestoresClosedOriginalDatabase() {
        val probe = Probe("setup")
        try {
            assertSame(probe.injected, runCatching { exercise(3, true, probe) }.exceptionOrNull())
            assertTrue("mandatory restoration after owned setup failure", probe.restored)
            CommonUtils.globalTextDisplaySettings
        } finally { DatabaseResetter.resetDatabase() }
    }

    @Test fun failedNativeCloseDoesNotSkipOtherResourcesOrRestoration() {
        val probe = Probe("close")
        try {
            assertSame(probe.injected, runCatching { exercise(4, true, probe) }.exceptionOrNull())
            assertTrue("remaining resource close attempted", probe.closeAttempts.get() >= 2)
            assertTrue("mandatory restoration independent of close failure", probe.restored)
        } finally {
            probe.retry?.invoke()
            DatabaseResetter.resetDatabase()
        }
    }

    @Test fun incompleteWorkerIsRetainedUntilRetryCompletes() = incompleteWorker("drain")
    @Test fun interruptedDrainRetainsExactWorkerUntilRetryCompletes() = incompleteWorker("interrupt")

    private fun incompleteWorker(mode: String) {
        val probe = Probe(mode)
        try {
            val failure = runCatching { exercise(5, true, probe) }.exceptionOrNull()
            assertSame("preserve original error", probe.injected, failure)
            assertTrue("exact real reader still incomplete", probe.reader!!.isAlive)
            assertFalse("never restore under live reader", probe.restored)
            assertEquals("no native cleanup under live reader", -1, probe.boundary!!.index("native-cleanup-complete"))
            assertNotNull("retain explicit pending cleanup owner", probe.retry)
            assertTrue("owner survives exercise stack", synchronized(pending) { pending.any { it.reader === probe.reader && it.boundary === probe.boundary } })
            probe.unpark.countDown()
            probe.reader!!.join(5_000)
            probe.retry!!.invoke()
            assertFalse(probe.reader!!.isAlive)
            assertTrue(probe.restored)
        } finally {
            Thread.interrupted()
            probe.unpark.countDown()
            probe.reader?.join(5_000)
            probe.retry?.invoke()
            DatabaseResetter.resetDatabase()
        }
    }

    private fun exercise(generation: Int, held: Boolean, probe: Probe? = null) {
        DatabaseResetter.resetDatabase()
        val boundary = Boundary(generation, held, probe)
        probe?.boundary = boundary
        val container = DatabaseContainer.instance
        boundary.container = container
        val owner = CleanupOwner(boundary, container, probe)
        synchronized(pending) { pending.add(owner) }
        probe?.retry = { owner.finish() }
        val queryDone = CountDownLatch(1)
        val resetDone = CountDownLatch(1)
        var original: Throwable? = null
        try {
            container.workspaceDb.close()
            if (probe?.mode == "setup") throw probe.injected
            val database = Room.databaseBuilder<WorkspaceDatabase>(BibleApplication.application, WorkspaceDatabase.dbFileName)
                .setDriver(boundary)
                .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(*workspacesMigrations)
                .build()
            owner.database = database
            boundary.database = database
            container.workspaceDb = database
            // Finish opening/schema SQL before the held read; no competing SQL trigger is needed.
            CommonUtils.globalTextDisplaySettings
            boundary.armed.set(true)
            val reader = Thread({
                boundary.event("reader-entry owner=CommonUtils.globalTextDisplaySettings", stack = true)
                try {
                    val result = CommonUtils.globalTextDisplaySettings
                    boundary.event("dao-result type=${result.javaClass.name}")
                } catch (e: Throwable) {
                    boundary.failure.set(e)
                    boundary.event("dao-failure type=${e.javaClass.name}", stack = true)
                } finally {
                    if (probe?.mode == "drain" || probe?.mode == "interrupt") {
                        probe.parked.countDown()
                        probe.unpark.await()
                    }
                    boundary.completed.set(true)
                    boundary.event("dao-completion")
                    queryDone.countDown()
                }
            }, "workspace-witness-reader-$generation")
            owner.reader = reader
            probe?.reader = reader
            reader.start()
            if (held) assertTrue("HARNESS FAILURE: SELECT hold not entered", boundary.entered.await(5, TimeUnit.SECONDS))
            else assertTrue("HARNESS FAILURE: control reader incomplete", queryDone.await(5, TimeUnit.SECONDS))
            val closer = Thread({
                boundary.event("reset-entry", stack = true)
                try { DatabaseContainer.reset(); boundary.event("reset-commit") }
                catch (e: Throwable) { boundary.failure.compareAndSet(null, e); boundary.event("reset-failure type=${e.javaClass.name}") }
                finally { resetDone.countDown() }
            }, "workspace-witness-reset-$generation")
            owner.closer = closer
            closer.start()
            assertTrue("HARNESS FAILURE: reset did not complete within bounded handshake", resetDone.await(5, TimeUnit.SECONDS))
            boundary.release.countDown()
            if (probe?.mode == "drain" || probe?.mode == "interrupt") {
                check(probe.parked.await(5, TimeUnit.SECONDS))
                if (probe.mode == "interrupt") Thread.currentThread().interrupt()
                throw probe.injected
            }
            assertTrue("HARNESS FAILURE: reader did not complete", queryDone.await(5, TimeUnit.SECONDS))
            reader.join(5_000)
            closer.join(5_000)
            assertFalse("HARNESS FAILURE: retained reader alive", reader.isAlive)
            assertFalse("HARNESS FAILURE: retained reset alive", closer.isAlive)
            assertNull("native/DAO/reset failure is not a successful witness", boundary.failure.get())
            assertNotNull("retain actual connection object", boundary.selectedConnection)
            assertNotNull("retain actual statement object", boundary.selectedStatement)
            fun index(name: String) = boundary.index(name).also { assertTrue("missing event $name", it >= 0) }
            if (held) {
                assertTrue(index("hold-entry") < index("reset-entry"))
                assertTrue(index("statement-finalize-attempt") < index("hold-release"))
                assertTrue(index("connection-close-attempt") < index("hold-release"))
                assertTrue(index("connection-close-suppressed") < index("delegate-step-enter"))
                assertTrue(index("delegate-step-exit") < index("dao-completion"))
                boundary.event("CONTROLLED-SAME-HANDLE-HAZARD-PROVEN not-historical-attribution")
            } else {
                assertTrue(index("dao-completion") < index("reset-entry"))
                assertTrue(index("dao-completion") < index("statement-finalize-attempt"))
                assertTrue(index("dao-completion") < index("connection-close-native-exit"))
                assertEquals(-1, boundary.index("connection-close-suppressed"))
                boundary.event("POST-COMPLETION-SAFE-CLOSE-CONTROL")
            }
        } catch (e: Throwable) {
            original = e
            throw e
        } finally {
            try { owner.finish() } catch (e: Throwable) {
                if (original == null) throw e
                if (original !== e) original.addSuppressed(e)
            }
        }
    }
}
