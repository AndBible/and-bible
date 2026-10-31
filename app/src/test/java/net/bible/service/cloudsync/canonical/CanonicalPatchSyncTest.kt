/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.cloudsync.canonical

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.DbDate
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.migrations.aiSettingsMigrations
import net.bible.android.database.migrations.myDocumentMigrations
import net.bible.android.database.migrations.readingPlanMigrations
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageContent
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.service.cloudsync.LAST_PATCH_WRITTEN_KEY
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.applyPatchesForDatabase
import net.bible.service.cloudsync.clearCanonicalMarkerPending
import net.bible.service.cloudsync.createPatchForDatabase
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.dropTriggers
import net.bible.service.common.ArchiveUtils
import net.bible.service.common.CommonUtils
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.db.queryLong
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.DatabaseResetter
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Patches between devices of different heal states (#3906): the healed marker, the re-key of an incoming patch to
 * canonical ids and the one-time re-send when a peer heals. A device that has not healed (an old app version) is
 * simulated with `canonical = false`, which reproduces the previous patch code exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CanonicalPatchSyncTest {
    private val opened = mutableListOf<SyncableDatabaseAccessor<*>>()
    private val files = mutableListOf<File>()
    private val env get() = KoinJavaComponent.get<AppEnvironment>(AppEnvironment::class.java)

    @Before fun setUp() { DatabaseResetter.resetDatabase() }

    @After fun tearDown() {
        opened.forEach { it.localDb.close() }
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
        DatabaseResetter.resetDatabase()
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(120_000) { block() } }

    private fun tick() = Thread.sleep(5)

    private suspend fun <T : SyncableRoomDatabase> newDevice(
        category: SyncableDatabaseDefinition,
        copyOf: File? = null,
        open: (String) -> T,
    ): SyncableDatabaseAccessor<T> {
        val file = File.createTempFile("${category.name.lowercase()}-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        copyOf?.copyTo(file, overwrite = true)
        var db = open(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(db, open, { db.close(); db = open(file.absolutePath); db },
            file.path, category, deviceId = UUID.randomUUID().toString())
        dropTriggers(dbDef)
        createTriggers(dbDef)
        dbDef.localDb.syncDao().getLong("x") // opens the database
        return dbDef.also { opened += it }
    }

    private fun <T : RoomDatabase> build(klass: Class<T>, path: String, migrations: Array<out androidx.room3.migration.Migration>): T =
        Room.databaseBuilder(application, klass, path)
            .addMigrations(*migrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()

    private suspend fun docDevice(copyOf: File? = null) = newDevice(SyncableDatabaseDefinition.MYDOCUMENTS, copyOf) {
        build(MyDocumentDatabase::class.java, it, myDocumentMigrations)
    }

    private suspend fun aiDevice() = newDevice(SyncableDatabaseDefinition.AI_SETTINGS) {
        build(AiSettingsDatabase::class.java, it, aiSettingsMigrations)
    }

    private suspend fun planDevice() = newDevice(SyncableDatabaseDefinition.READINGPLANS) {
        build(ReadingPlanDatabase::class.java, it, readingPlanMigrations)
    }

    /** [from]'s patch applied to [to]; [fromCanonical] / [toCanonical] false stand for an old app version. */
    private suspend fun <T : SyncableRoomDatabase> sync(
        from: SyncableDatabaseAccessor<T>, to: SyncableDatabaseAccessor<T>,
        fromCanonical: Boolean = true, toCanonical: Boolean = true,
    ) {
        tick()
        val patch: Path? = createPatchForDatabase(from, env, canonical = fromCanonical)
        if (patch != null) applyPatchesForDatabase(to, env, patch, canonical = toCanonical)
        tick()
    }

    private suspend fun <T : SyncableRoomDatabase> syncBoth(a: SyncableDatabaseAccessor<T>, b: SyncableDatabaseAccessor<T>) {
        sync(a, b)
        sync(b, a)
    }

    private val SyncableDatabaseAccessor<MyDocumentDatabase>.md get() = localDb.myDocumentDao()
    private val SyncableDatabaseAccessor<ReadingPlanDatabase>.rp get() = localDb.readingPlanDao()

    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.addBook(): MyDocument =
        MyDocument(name = "AI", initials = "AIDocuments").also { md.insert(it) }

    /** A book as the new code creates it: with its canonical id from the start. */
    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.addCanonicalBook(): MyDocument =
        MyDocument(id = canonicalMyDocumentId("AIDocuments"), name = "AI", initials = "AIDocuments").also { md.insert(it) }

    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.addPage(book: MyDocument, key: String): MyDocumentPage {
        val page = MyDocumentPage(documentId = book.id, title = key, pageKey = key, orderNumber = 1)
        md.insert(page)
        md.insertOrUpdateContent(MyDocumentPageContent(page.id, "content $key"))
        return page
    }

    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.pageKeysWithContent(): Set<String> {
        val book = md.allDocuments().single()
        return md.pagesForDocument(book.id).map { "${it.pageKey}=${md.getContent(it.id)}" }.toSet()
    }

    /** Also guards the Task 3 heal itself: after the bug exchange only the heal and a re-send can bring the pages back. */
    @Test fun bothHealedAiPagesReachBothDevices() = run {
        val a = docDevice()
        val b = docDevice()
        val bookA = a.addBook()
        a.addPage(bookA, "a")
        val bookB = b.addBook()
        b.addPage(bookB, "b")
        // The bug: random ids, so the pages are dropped on the way.
        sync(a, b, fromCanonical = false, toCanonical = false)
        sync(b, a, fromCanonical = false, toCanonical = false)
        // The fixture reproduces the bug: each device dropped the other's page.
        assertEquals(setOf("a=content a"), a.pageKeysWithContent())
        assertEquals(setOf("b=content b"), b.pageKeysWithContent())

        healCanonicalIds(a)
        healCanonicalIds(b)
        syncBoth(a, b)

        val expected = setOf("a=content a", "b=content b")
        assertEquals(expected, a.pageKeysWithContent())
        assertEquals(expected, b.pageKeysWithContent())
        assertEquals(canonicalMyDocumentId("AIDocuments"), a.md.allDocuments().single().id)
        assertEquals(canonicalMyDocumentId("AIDocuments"), b.md.allDocuments().single().id)
    }

    @Test fun bothHealedResetReachesTheOtherDevice() = run {
        val a = planDevice()
        val b = planDevice()
        for (d in listOf(a, b)) {
            d.rp.updatePlan(ReadingPlan("y1ot", DbDate(1000L)))
            d.rp.addPlanStatus(ReadingPlanStatus("y1ot", 1, "READ"))
            tick()
        }
        healCanonicalIds(a)
        healCanonicalIds(b)
        tick()

        a.rp.deleteStatusesForPlan("y1ot")
        a.rp.deletePlanInfo("y1ot")
        sync(a, b)

        assertNull(b.rp.getPlan("y1ot"))
        assertNull(b.rp.getStatus("y1ot", 1))
    }

    @Test fun mixedVersionsDroppedPagesComeBack() = run {
        val a = docDevice()
        val b = docDevice()
        a.addPage(a.addBook(), "a")
        b.addBook()
        healCanonicalIds(a)

        // B is an old version: it ignores the marker and the canonical book id, A's page has no book to join.
        sync(a, b, toCanonical = false)
        assertEquals(emptySet<String>(), b.md.pagesForDocument(b.md.allDocuments().single().id).map { it.pageKey }.toSet())

        healCanonicalIds(b)
        sync(b, a) // carries B's marker; A moved rows, so it must send everything again
        assertEquals(0L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        assertEquals(1L, a.dao.getLong("canonicalPeerSeen:${b.deviceId}"))
        sync(a, b)

        assertEquals(setOf("a=content a"), b.pageKeysWithContent())
        assertEquals(setOf("a=content a"), a.pageKeysWithContent())
    }

    @Test fun patchWrittenBeforeHealAppliesAfterIt() = run {
        val a = docDevice()
        val b = docDevice()
        a.addBook()
        val bookB = b.addBook()
        // B (old) writes its patch before A has healed.
        b.addPage(bookB, "b")
        tick()
        val patch = createPatchForDatabase(b, env, canonical = false)!!
        healCanonicalIds(a)

        applyPatchesForDatabase(a, env, patch)

        assertEquals(canonicalMyDocumentId("AIDocuments"), a.md.allDocuments().single().id)
        assertEquals(setOf("b=content b"), a.pageKeysWithContent())
    }

    @Test fun earlierPeerEditIsNotOverwritten() = run {
        val a = docDevice()
        val book = a.addBook()
        val page = a.addPage(book, "p")
        a.dao.clearLog()
        val b = docDevice(copyOf = File(a.localDbPath))
        // Both devices hold the same rows with random ids; the log is empty on both, as after a join.
        a.dao.setConfig(LAST_PATCH_WRITTEN_KEY, System.currentTimeMillis())
        b.dao.setConfig(LAST_PATCH_WRITTEN_KEY, System.currentTimeMillis())
        tick()

        b.md.insertOrUpdateContent(MyDocumentPageContent(page.id, "edited on B")) // t1, old version
        tick(); tick()
        a.md.update(a.md.documentById(book.id)!!.apply { name = "renamed on A" }) // t2: A has a newer edit of the book
        healCanonicalIds(a) // after t1; A keeps its own log timestamps
        healCanonicalIds(b)
        tick()
        syncBoth(a, b)

        assertEquals(setOf("p=edited on B"), a.pageKeysWithContent())
        assertEquals(setOf("p=edited on B"), b.pageKeysWithContent())
        assertEquals("renamed on A", a.md.allDocuments().single().name)
        assertEquals("renamed on A", b.md.allDocuments().single().name)
    }

    private suspend fun configOfPatch(gz: Path, key: String): Long? {
        val raw = File.createTempFile("patch-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        ArchiveUtils.gunzipFile(gz, raw.absolutePath.toPath())
        return openSqlite(raw.absolutePath).use { it.queryLong("SELECT longValue FROM SyncConfiguration WHERE keyName = '$key'") }
    }

    private suspend fun logCountOfPatch(gz: Path): Long {
        val raw = File.createTempFile("patch-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        ArchiveUtils.gunzipFile(gz, raw.absolutePath.toPath())
        return openSqlite(raw.absolutePath).use { it.queryLong("SELECT COUNT(*) FROM LogEntry")!! }
    }

    @Test fun markerOnlyPatchWhenNoOwnChanges() = run {
        val a = docDevice()
        healCanonicalIds(a)
        assertEquals(true, a.dao.getBoolean("canonicalMarkerPending"))

        val patch = createPatchForDatabase(a, env)
        assertNotNull(patch)
        assertEquals(1L, configOfPatch(patch!!, "canonicalIdsHealedDevice:${a.deviceId}"))
        assertEquals(0L, logCountOfPatch(patch))
        // The marker stays pending until the upload succeeded.
        assertEquals(true, a.dao.getBoolean("canonicalMarkerPending"))
        // The local configuration does not leak into the patch.
        assertNull(configOfPatch(patch, "canonicalIdsVersion"))
        assertNull(configOfPatch(patch, LAST_PATCH_WRITTEN_KEY))

        clearCanonicalMarkerPending(a) // what CloudSync does after the upload
        assertEquals(false, a.dao.getBoolean("canonicalMarkerPending"))
        assertNull(createPatchForDatabase(a, env))
        // An old version never writes a marker, and a marker alone is no reason to write a patch for it.
        a.dao.setConfig("canonicalMarkerPending", true)
        assertNull(createPatchForDatabase(a, env, canonical = false))
    }

    @Test fun markerOnlyPatchIsAValidPatchForAnOldDevice() = run {
        val a = docDevice()
        val b = docDevice()
        b.addBook()
        healCanonicalIds(a)
        val patch = createPatchForDatabase(a, env)!!

        applyPatchesForDatabase(b, env, patch, canonical = false)

        assertEquals(1, b.md.allDocuments().size)
        assertNull(b.dao.getLong("canonicalPeerSeen:${a.deviceId}"))
        assertNull(b.dao.getLong("canonicalIdsHealedDevice:${a.deviceId}"))
    }

    @Test fun nonDivergedDeviceDoesNotResend() = run {
        val a = docDevice()
        val b = docDevice()
        healCanonicalIds(a) // nothing to move
        assertNull(a.dao.getBoolean("canonicalHealMovedRows"))
        b.addPage(b.addCanonicalBook(), "b")
        healCanonicalIds(b) // nothing to move either: its book was canonical from creation
        assertNull(b.dao.getBoolean("canonicalHealMovedRows"))
        a.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 12345L)

        sync(b, a)

        assertEquals(12345L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        assertEquals(1L, a.dao.getLong("canonicalPeerSeen:${b.deviceId}"))
    }

    @Test fun aPeerCountsOnlyOnceAndOwnMarkerNever() = run {
        val a = docDevice()
        val b = docDevice()
        a.addBook()
        b.addBook()
        healCanonicalIds(a)
        healCanonicalIds(b)

        sync(b, a)
        assertEquals(0L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        a.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 777L)
        b.addPage(b.md.allDocuments().single(), "later")
        sync(b, a)
        assertEquals(777L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))

        // A's own patch applied on A itself (a re-read of its own upload) is not a peer.
        a.dao.removeConfig("canonicalPeerSeen:${b.deviceId}")
        val own = createPatchForDatabase(a, env, updateTimestamp = false)!!
        a.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 888L)
        applyPatchesForDatabase(a, env, own)
        assertNull(a.dao.getLong("canonicalPeerSeen:${a.deviceId}"))
        assertEquals(888L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
    }

    @Test fun unhealedReceiverKeepsItsOwnIds() = run {
        val a = docDevice()
        val b = docDevice()
        a.addBook()
        val bookB = b.addBook()
        healCanonicalIds(a)

        sync(a, b)

        // B has not healed: no re-key of the incoming patch, B's rows keep their random ids until B heals.
        assertEquals(bookB.id, b.md.allDocuments().single().id)
    }

    @Test fun canonicalFromCreationDeviceResendsWhenAnOldPeerHeals() = run {
        val a = docDevice()
        val b = docDevice()
        a.addPage(a.addCanonicalBook(), "a")
        b.addBook()
        healCanonicalIds(a) // moves nothing
        assertNull(a.dao.getBoolean("canonicalHealMovedRows"))

        sync(a, b, toCanonical = false) // B (old) drops A's page: its book has a random id
        assertEquals(emptySet<String>(), b.md.pagesForDocument(b.md.allDocuments().single().id).map { it.pageKey }.toSet())
        healCanonicalIds(b) // moves rows
        sync(b, a) // B's marker says its heal moved rows
        assertEquals(0L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        sync(a, b)

        assertEquals(setOf("a=content a"), b.pageKeysWithContent())
    }

    @Test fun peerThatMovedNothingDoesNotTriggerAResend() = run {
        val a = docDevice()
        val b = docDevice()
        a.addCanonicalBook()
        b.addPage(b.addCanonicalBook(), "b")
        healCanonicalIds(a)
        healCanonicalIds(b)
        a.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 4242L)

        sync(b, a)

        assertEquals(4242L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        assertEquals(1L, a.dao.getLong("canonicalPeerSeen:${b.deviceId}"))
    }

    @Test fun rowsAnOldReceiverDroppedAreRestoredWithoutParents() = run {
        val a = aiDevice()
        val b = aiDevice()
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        a.localDb.llmProviderConfigDao().insert(provider)
        sync(a, b)
        val modelA = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m")
        a.localDb.llmConfiguredModelDao().insert(modelA)
        b.localDb.llmConfiguredModelDao().insert(LlmConfiguredModel(providerConfigId = provider.id, modelId = "m"))
        val prompt = AgentPrompt(name = "p").also { it.configuredModelId = modelA.id }
        a.localDb.agentPromptDao().insert(prompt)

        // B is old: it keeps its own model id, drops the prompt (foreign key) but keeps the prompt's log entry.
        sync(a, b, toCanonical = false)
        assertNull(b.localDb.agentPromptDao().promptById(prompt.id))
        healCanonicalIds(a)
        healCanonicalIds(b)
        sync(a, b)

        val canonical = canonicalLlmConfiguredModelId(provider.id, "m")
        assertEquals(canonical, b.localDb.agentPromptDao().promptById(prompt.id)?.configuredModelId)
        assertEquals(canonical, b.localDb.llmConfiguredModelDao().getByProvider(provider.id).single().id)
    }

    /**
     * Lowering the watermark for a re-send is not enough when this device has no entries of its own: only the pending
     * marker makes it write a patch, and the patch then carries the whole log, entries of other devices included.
     */
    @Test fun resendWithoutOwnLogEntriesStillProducesAPatch() = run {
        val a = docDevice()
        val b = docDevice()
        val c = docDevice()
        // A holds only C's entries (C is an old version): the book and page arrive with random ids and C's source device.
        c.addPage(c.addBook(), "c")
        sync(c, a, fromCanonical = false, toCanonical = false)
        assertEquals(0L, a.dao.countNewLogEntries(0L, a.deviceId))
        assertEquals(1, healCanonicalIds(a)) // rows moved: re-send is owed
        assertNotNull(createPatchForDatabase(a, env))
        clearCanonicalMarkerPending(a) // the first announcement was uploaded
        assertNull(createPatchForDatabase(a, env))

        b.addBook()
        healCanonicalIds(b)
        sync(b, a) // a new healed peer: A lowers its watermark

        assertEquals(0L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        val patch = createPatchForDatabase(a, env)
        assertNotNull("a re-send needs a patch even with no own log entries", patch)
        assertTrue("the patch carries the entries of the other device", logCountOfPatch(patch!!) > 0)
    }

    /** The generic restore only brings back rows whose local entry is an UPSERT: a local delete stays a delete. */
    @Test fun genericRestoreDoesNotResurrectALocallyDeletedRow() = run {
        val a = docDevice()
        val b = docDevice()
        val book = a.addCanonicalBook()
        val page = a.addPage(book, "p")
        healCanonicalIds(a)
        healCanonicalIds(b)
        sync(a, b)
        assertEquals(setOf("p=content p"), b.pageKeysWithContent())

        b.md.delete(b.md.pagesForDocument(book.id).single { it.id == page.id })
        // The same timestamp as the incoming UPSERT: a tie must not undo the local delete.
        val upsertTime = a.dao.allLogEntries().single { it.tableName == "MyDocumentPage" && it.type.name == "UPSERT" }.lastUpdated
        b.localDb.useWriterConnectionMarked {
            it.exec("UPDATE LogEntry SET lastUpdated = ?, sourceDevice = ? WHERE tableName = 'MyDocumentPage' AND type = 'DELETE'", upsertTime, a.deviceId)
        }
        a.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 0L)
        a.dao.setConfig("canonicalMarkerPending", true)

        sync(a, b)

        assertEquals(emptySet<String>(), b.md.pagesForDocument(book.id).map { it.pageKey }.toSet())
    }

    @Test fun canonicalFalseReproducesTheOldBehaviour() = run {
        val a = docDevice()
        a.addBook()
        healCanonicalIds(a)
        val patch = createPatchForDatabase(a, env, canonical = false)!!
        assertNull(configOfPatch(patch, "canonicalIdsHealedDevice:${a.deviceId}"))
        assertTrue(a.dao.getBoolean("canonicalMarkerPending") == true)
    }
}
