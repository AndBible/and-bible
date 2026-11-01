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
package net.bible.service.cloudsync

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
import net.bible.android.database.IdType
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
import net.bible.service.cloudsync.canonical.canonicalMyDocumentId
import net.bible.service.cloudsync.canonical.newMyDocumentId
import net.bible.service.cloudsync.merge.mergeSnapshotIntoLocal
import net.bible.service.cloudsync.merge.prepareMergeSnapshot
import net.bible.service.common.CommonUtils
import net.bible.service.db.exec
import net.bible.service.db.queryLong
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.GlobalAiSettings
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.LlmUsageRecord
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import okio.Path.Companion.toOkioPath
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Receive-side id aliases of #3906 between real two-device patch exchanges (`createPatchForDatabase` /
 * `applyPatchesForDatabase`).
 *
 * Two topologies:
 * - **Upgrade**: devices that already share a random id (they synced before), one updated and one on an older version.
 *   Nothing may be lost in either direction, whatever children are edited. An old version is simulated by applying
 *   patches with `mapIncomingIds = false`, which is the previous apply code exactly (the patch writer is unchanged).
 * - **Diverged**: each device created the same natural-key row under its own id before syncing (the original #3906
 *   AI Documents collision). Two updated devices converge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class IncomingIdAliasSyncTest {
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
        deviceId: String = UUID.randomUUID().toString(),
        open: (String) -> T,
    ): SyncableDatabaseAccessor<T> {
        val file = File.createTempFile("${category.name.lowercase()}-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        copyOf?.copyTo(file, overwrite = true)
        var db = open(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(db, open, { db.close(); db = open(file.absolutePath); db },
            file.path, category, deviceId = deviceId)
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

    private suspend fun docDevice(copyOf: File? = null, deviceId: String = UUID.randomUUID().toString()) =
        newDevice(SyncableDatabaseDefinition.MYDOCUMENTS, copyOf, deviceId) {
        build(MyDocumentDatabase::class.java, it, myDocumentMigrations)
    }

    private suspend fun aiDevice() = newDevice(SyncableDatabaseDefinition.AI_SETTINGS) {
        build(AiSettingsDatabase::class.java, it, aiSettingsMigrations)
    }

    private suspend fun planDevice() = newDevice(SyncableDatabaseDefinition.READINGPLANS) {
        build(ReadingPlanDatabase::class.java, it, readingPlanMigrations)
    }

    /** [from]'s patch applied to [to]; [toNew] false: [to] runs an older app version (no receive-side mapping). */
    private suspend fun <T : SyncableRoomDatabase> sync(
        from: SyncableDatabaseAccessor<T>, to: SyncableDatabaseAccessor<T>, toNew: Boolean = true,
    ) {
        tick()
        val patch = createPatchForDatabase(from, env)
        if (patch != null) applyPatchesForDatabase(to, env, patch, mapIncomingIds = toNew)
        tick()
    }

    private suspend fun <T : SyncableRoomDatabase> syncBoth(a: SyncableDatabaseAccessor<T>, b: SyncableDatabaseAccessor<T>) {
        sync(a, b)
        sync(b, a)
    }

    private val SyncableDatabaseAccessor<MyDocumentDatabase>.md get() = localDb.myDocumentDao()
    private val SyncableDatabaseAccessor<ReadingPlanDatabase>.rp get() = localDb.readingPlanDao()

    /** A book with a random id, as versions before #3906 created it. */
    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.addBook(): MyDocument =
        MyDocument(name = "AI", initials = "AIDocuments").also { md.insert(it) }

    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.addPage(book: IdType, key: String): MyDocumentPage {
        val page = MyDocumentPage(documentId = book, title = key, pageKey = key, orderNumber = 1)
        md.insert(page)
        md.insertOrUpdateContent(MyDocumentPageContent(page.id, "content $key"))
        return page
    }

    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.book(): MyDocument = md.allDocuments().single()

    /** "pageKey/title=content" of every page of the single book. */
    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.pages(): Set<String> {
        val book = book()
        return md.pagesForDocument(book.id).map { "${it.pageKey}/${it.title}=${md.getContent(it.id)}" }.toSet()
    }

    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.editPage(key: String, title: String, content: String) {
        val page = md.pagesForDocument(book().id).single { it.pageKey == key }
        md.update(page.apply { this.title = title })
        md.insertOrUpdateContent(MyDocumentPageContent(page.id, content))
    }

    private suspend fun SyncableDatabaseAccessor<*>.sql(sql: String) = localDb.useWriterConnectionMarked { it.exec(sql) }

    private suspend fun SyncableDatabaseAccessor<*>.count(sql: String): Long = localDb.useWriterConnectionMarked { it.queryLong(sql) }!!

    private suspend fun SyncableDatabaseAccessor<*>.aliasCount(): Long =
        localDb.useWriterConnectionMarked { it.queryLong("SELECT count(*) FROM SyncConfiguration WHERE keyName LIKE '$ID_ALIAS_PREFIX%'") }!!

    // ---------------------------------------------------------------- upgrade topology: ids already shared

    @Test fun sharedBookOldPeerChildEditsReachUpdatedDeviceAndBack() = run {
        val a = docDevice()
        val b = docDevice()
        val book = a.addBook()
        a.addPage(book.id, "p1")
        a.addPage(book.id, "p2")
        sync(a, b, toNew = false) // both old: B now shares the book id
        assertEquals(book.id, b.book().id)

        // A updates; B stays old. B edits existing pages and adds one: a child-only patch naming the shared id.
        b.editPage("p1", "p1b", "edited on b")
        b.addPage(book.id, "b1")
        sync(b, a)
        // A edits an existing page and adds one; B applies with the old code.
        a.editPage("p2", "p2a", "edited on a")
        a.addPage(book.id, "a1")
        sync(a, b, toNew = false)
        sync(b, a)

        val expected = setOf("p1/p1b=edited on b", "p2/p2a=edited on a", "b1/b1=content b1", "a1/a1=content a1")
        assertEquals(expected, a.pages())
        assertEquals(expected, b.pages())
        assertEquals(book.id, a.book().id)
        assertEquals(0L, a.aliasCount())
    }

    @Test fun sharedBookUpdatedBothSidesNothingMoves() = run {
        val a = docDevice()
        val b = docDevice()
        val book = a.addBook()
        a.addPage(book.id, "p1")
        sync(a, b, toNew = false)
        b.editPage("p1", "t", "edited on b")
        b.addPage(book.id, "b1")
        sync(b, a)
        a.addPage(book.id, "a1")
        sync(a, b)

        val expected = setOf("p1/t=edited on b", "b1/b1=content b1", "a1/a1=content a1")
        assertEquals(expected, a.pages())
        assertEquals(expected, b.pages())
        assertEquals(book.id, b.book().id)
    }

    @Test fun sharedReadingPlanStatusesBothDirections() = run {
        val a = planDevice()
        val b = planDevice()
        val plan = ReadingPlan("y1ot", DbDate(1000L))
        a.rp.updatePlan(plan)
        a.rp.addPlanStatus(ReadingPlanStatus("y1ot", 1, "READ1"))
        sync(a, b, toNew = false)

        b.rp.addPlanStatus(ReadingPlanStatus("y1ot", 2, "READ2"))
        b.rp.addPlanStatus(b.rp.getStatus("y1ot", 1)!!.copy(readingStatus = "READ1b"))
        sync(b, a)
        a.rp.addPlanStatus(ReadingPlanStatus("y1ot", 3, "READ3"))
        a.rp.updatePlan(a.rp.getPlan("y1ot")!!.apply { planCurrentDay = 3 })
        sync(a, b, toNew = false)

        for (d in listOf(a, b)) {
            assertEquals(plan.id, d.rp.getPlan("y1ot")!!.id)
            assertEquals(3, d.rp.getPlan("y1ot")!!.planCurrentDay)
            assertEquals(listOf("READ1b", "READ2", "READ3"), (1..3).map { d.rp.getStatus("y1ot", it)!!.readingStatus })
        }
    }

    @Test fun sharedModelPromptsAndDefaultModelBothDirections() = run {
        val a = aiDevice()
        val b = aiDevice()
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        a.localDb.llmProviderConfigDao().insert(provider)
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m")
        a.localDb.llmConfiguredModelDao().insert(model)
        val prompt = AgentPrompt(name = "p").also { it.configuredModelId = model.id }
        a.localDb.agentPromptDao().insert(prompt)
        a.localDb.globalAiSettingsDao().set(GlobalAiSettings(defaultModelId = model.id))
        sync(a, b, toNew = false)

        // B (old) edits the existing prompt and adds one; A (updated) adds one and edits the settings.
        b.localDb.agentPromptDao().update(b.localDb.agentPromptDao().promptById(prompt.id)!!.also { it.name = "pb" })
        val promptB = AgentPrompt(name = "b").also { it.configuredModelId = model.id }
        b.localDb.agentPromptDao().insert(promptB)
        sync(b, a)
        val promptA = AgentPrompt(name = "a").also { it.configuredModelId = model.id }
        a.localDb.agentPromptDao().insert(promptA)
        a.localDb.globalAiSettingsDao().set(a.localDb.globalAiSettingsDao().get()!!.copy(maxIterations = 7))
        sync(a, b, toNew = false)
        sync(b, a)

        for (d in listOf(a, b)) {
            val dao = d.localDb.agentPromptDao()
            assertEquals("pb", dao.promptById(prompt.id)?.name)
            assertEquals(model.id, dao.promptById(promptB.id)?.configuredModelId)
            assertEquals(model.id, dao.promptById(promptA.id)?.configuredModelId)
            assertEquals(model.id, d.localDb.globalAiSettingsDao().get()?.defaultModelId)
            assertEquals(7, d.localDb.globalAiSettingsDao().get()?.maxIterations)
            assertEquals(listOf(model.id), d.localDb.llmConfiguredModelDao().all().map { it.id })
        }
    }

    // ---------------------------------------------------------------- diverged ids

    /** The #3906 bug: each device created its own AI Documents book. */
    private suspend fun divergedBooks(): Pair<SyncableDatabaseAccessor<MyDocumentDatabase>, SyncableDatabaseAccessor<MyDocumentDatabase>> {
        val a = docDevice()
        val b = docDevice()
        a.addPage(a.addBook().id, "a")
        b.addPage(b.addBook().id, "b")
        return a to b
    }

    @Test fun divergedBooksConvergeOnTwoUpdatedDevices() = run {
        val (a, b) = divergedBooks()
        val bookA = a.book().id
        val bookB = b.book().id
        syncBoth(a, b)

        val expected = setOf("a/a=content a", "b/b=content b")
        assertEquals(expected, a.pages())
        assertEquals(expected, b.pages())
        // Local ids never change.
        assertEquals(bookA, a.book().id)
        assertEquals(bookB, b.book().id)
        assertEquals(1L, a.aliasCount())
        assertEquals(1L, b.aliasCount())
    }

    @Test fun laterChildOnlyPatchesFollowTheStoredAlias() = run {
        val (a, b) = divergedBooks()
        syncBoth(a, b)

        // A child-only patch (no book row): a new page on B, an edit of A's page on B, an edit of B's page on A.
        b.addPage(b.book().id, "b2")
        b.editPage("a", "a-b", "a edited on b")
        a.editPage("b", "b-a", "b edited on a")
        syncBoth(b, a)

        val expected = setOf("a/a-b=a edited on b", "b/b-a=b edited on a", "b2/b2=content b2")
        assertEquals(expected, a.pages())
        assertEquals(expected, b.pages())
    }

    @Test fun divergedUpdatedDeviceGainsOldPeersPagesAndOldPeerIsNoWorse() = run {
        val (a, b) = divergedBooks()
        sync(b, a) // A updated
        sync(a, b, toNew = false) // B old: A's page has no book there, as before #3906
        sync(b, a)

        assertEquals(setOf("a/a=content a", "b/b=content b"), a.pages())
        assertEquals(setOf("b/b=content b"), b.pages())
        // B's later pages keep arriving under A's book.
        b.addPage(b.book().id, "b2")
        sync(b, a)
        assertEquals(setOf("a/a=content a", "b/b=content b", "b2/b2=content b2"), a.pages())
    }

    @Test fun threeDivergedBooksChainToOneLocalBook() = run {
        val (a, b) = divergedBooks()
        val c = docDevice()
        c.addPage(c.addBook().id, "c")
        sync(b, a)
        sync(c, a)
        // One patch of A, read by both peers (as every device reads every other device's patches).
        tick()
        val patchA = createPatchForDatabase(a, env)!!
        applyPatchesForDatabase(b, env, patchA)
        applyPatchesForDatabase(c, env, patchA)

        val expected = setOf("a/a=content a", "b/b=content b", "c/c=content c")
        assertEquals(expected, a.pages())
        assertEquals(expected, b.pages())
        assertEquals(expected, c.pages())
        assertEquals(2L, a.aliasCount())
    }

    @Test fun divergedReadingPlanResetReachesTheOtherDevice() = run {
        val a = planDevice()
        val b = planDevice()
        for (d in listOf(a, b)) {
            d.rp.updatePlan(ReadingPlan("y1ot", DbDate(1000L)))
            d.rp.addPlanStatus(ReadingPlanStatus("y1ot", 1, "READ"))
            tick()
        }
        a.rp.addPlanStatus(ReadingPlanStatus("y1ot", 2, "READ"))
        syncBoth(a, b)
        assertNotNull(b.rp.getStatus("y1ot", 2))

        // Reset on B: deletes of B's own ids, which A holds under other ids.
        b.rp.deleteStatusesForPlan("y1ot")
        b.rp.deletePlanInfo("y1ot")
        sync(b, a)

        assertNull(a.rp.getPlan("y1ot"))
        assertNull(a.rp.getStatus("y1ot", 1))
        assertNull(a.rp.getStatus("y1ot", 2))
    }

    /** #3902's reset-and-restart in one patch: DELETE of B's old id and UPSERT of its new id both map to A's row. */
    @Test fun divergedReadingPlanResetAndRestartInOnePatchKeepsTheRestart() = run {
        val a = planDevice()
        val b = planDevice()
        for (d in listOf(a, b)) {
            d.rp.updatePlan(ReadingPlan("y1ot", DbDate(1000L)))
            d.rp.addPlanStatus(ReadingPlanStatus("y1ot", 1, "READ"))
            tick()
        }
        syncBoth(a, b)

        b.rp.deleteStatusesForPlan("y1ot")
        b.rp.deletePlanInfo("y1ot")
        tick()
        b.rp.updatePlan(ReadingPlan("y1ot", DbDate(2000L)))
        b.rp.addPlanStatus(ReadingPlanStatus("y1ot", 1, "RESTARTED"))
        sync(b, a)

        assertEquals(DbDate(2000L), a.rp.getPlan("y1ot")?.planStartDate)
        assertEquals("RESTARTED", a.rp.getStatus("y1ot", 1)?.readingStatus)
    }

    @Test fun divergedBookDeletedAndRecreatedOnPeerKeepsLocalPages() = run {
        val (a, b) = divergedBooks()
        syncBoth(a, b)
        b.md.delete(b.book())
        tick()
        b.addPage(b.addBook().id, "b2")
        sync(b, a)

        // The re-created book is newer than the delete, so A's book stays with its pages; B's old page went with B's book by
        // cascade, which is never logged, so A keeps its copy (as the merged row it is).
        assertEquals(setOf("a/a=content a", "b/b=content b", "b2/b2=content b2"), a.pages())
    }

    @Test fun divergedModelDefaultAndPromptsMapToTheLocalModel() = run {
        val a = aiDevice()
        val b = aiDevice()
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        a.localDb.llmProviderConfigDao().insert(provider)
        sync(a, b)
        val modelA = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m").also { a.localDb.llmConfiguredModelDao().insert(it) }
        val modelB = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m").also { b.localDb.llmConfiguredModelDao().insert(it) }
        val promptB = AgentPrompt(name = "b").also { it.configuredModelId = modelB.id }
        b.localDb.agentPromptDao().insert(promptB)
        b.localDb.globalAiSettingsDao().set(GlobalAiSettings(defaultModelId = modelB.id, maxIterations = 9))
        sync(b, a)

        assertEquals(listOf(modelA.id), a.localDb.llmConfiguredModelDao().all().map { it.id })
        assertEquals(modelA.id, a.localDb.agentPromptDao().promptById(promptB.id)?.configuredModelId)
        assertEquals(modelA.id, a.localDb.globalAiSettingsDao().get()?.defaultModelId)
        assertEquals(9, a.localDb.globalAiSettingsDao().get()?.maxIterations)

        // Later a settings-only patch from B still names B's model id.
        b.localDb.globalAiSettingsDao().set(b.localDb.globalAiSettingsDao().get()!!.copy(maxIterations = 4))
        sync(b, a)
        assertEquals(modelA.id, a.localDb.globalAiSettingsDao().get()?.defaultModelId)
        assertEquals(4, a.localDb.globalAiSettingsDao().get()?.maxIterations)
    }

    // ---------------------------------------------------------------- edge cases of the alias

    @Test fun aliasTargetDeletedLocallyDropsLateChildrenAsBeforeAndANewerParentEditRestoresIt() = run {
        val (a, b) = divergedBooks()
        syncBoth(a, b)
        a.md.delete(a.book())
        assertEquals(emptyList<MyDocument>(), a.md.allDocuments())

        b.addPage(b.book().id, "b2") // child-only: the parent is gone on A, the page has nowhere to go
        sync(b, a)
        assertEquals(emptyList<MyDocument>(), a.md.allDocuments())
        assertNull(a.md.pageByKey(b.book().id, "b2"))

        // An edit of the book on B, newer than A's delete, wins as it would for a shared id: the book comes back under
        // A's id with the pages B carries.
        b.md.update(b.book().apply { name = "AI renamed" })
        sync(b, a)
        assertEquals("AI renamed", a.book().name)
        assertEquals(setOf("a/a=content a", "b/b=content b", "b2/b2=content b2"), a.pages())
        assertEquals(setOf("a/a=content a", "b/b=content b", "b2/b2=content b2"), b.pages())
    }

    @Test fun naturalKeyChangeOnTheLocalRowEndsTheAlias() = run {
        val (a, b) = divergedBooks()
        syncBoth(a, b)
        val bookA = a.book()
        a.md.update(bookA.apply { initials = "Renamed" })
        // Not synced to B yet. B edits its book under the old key: no longer the same row as A's.
        b.md.update(b.book().apply { name = "B book" })
        sync(b, a)

        val books = a.md.allDocuments().associateBy { it.initials }
        assertEquals(setOf("Renamed", "AIDocuments"), books.keys)
        assertEquals(bookA.id, books.getValue("Renamed").id)
        assertEquals("B book", books.getValue("AIDocuments").name)
        assertEquals(0L, a.aliasCount())
        // Nothing lost: A keeps both pages under its renamed book, B's book arrived with its pages.
        assertEquals(setOf("a", "b"), a.md.pagesForDocument(bookA.id).map { it.pageKey }.toSet())
    }

    @Test fun patchCarryingTheLocalIdItselfIsNotAliased() = run {
        // B shares A's book id, then deletes it and creates its own book with the same initials before syncing.
        val a = docDevice()
        val b = docDevice()
        val shared = a.addBook()
        a.addPage(shared.id, "a")
        sync(a, b)
        b.md.delete(b.book())
        b.addPage(b.addBook().id, "b")
        sync(b, a)

        // As before #3906 (#3902): the delete of the shared id wins and B's new book replaces it.
        val book = a.book()
        assertEquals(b.book().id, book.id)
        assertEquals(setOf("b/b=content b"), a.pages())
        assertEquals(0L, a.aliasCount())
    }

    @Test fun deleteOfAMappedForeignBookReachesTheLocalBook() = run {
        val (a, b) = divergedBooks()
        syncBoth(a, b)
        b.md.delete(b.book())
        sync(b, a)
        assertEquals(emptyList<MyDocument>(), a.md.allDocuments())
    }

    // ---------------------------------------------------------------- canonical id re-created after a delete

    /** A shared canonical AI Documents book with a page from each device, on two devices. */
    private suspend fun sharedCanonicalBook(): Pair<SyncableDatabaseAccessor<MyDocumentDatabase>, SyncableDatabaseAccessor<MyDocumentDatabase>> {
        val a = docDevice()
        val b = docDevice()
        val book = MyDocument(id = a.md.newMyDocumentId("AIDocuments"), name = "AI", initials = "AIDocuments").also { a.md.insert(it) }
        assertEquals(canonicalMyDocumentId("AIDocuments"), book.id)
        a.addPage(book.id, "a")
        sync(a, b)
        b.addPage(b.book().id, "b")
        syncBoth(b, a)
        assertEquals(setOf("a/a=content a", "b/b=content b"), a.pages())
        assertEquals(setOf("a/a=content a", "b/b=content b"), b.pages())
        return a to b
    }

    /** As the app does: a document deleted on [this] device is re-created (AI Documents on the next AI action). */
    private suspend fun SyncableDatabaseAccessor<MyDocumentDatabase>.deleteAndRecreateBook(page: String) {
        md.delete(book())
        tick()
        val book = MyDocument(id = md.newMyDocumentId("AIDocuments"), name = "AI", initials = "AIDocuments").also { md.insert(it) }
        addPage(book.id, page)
    }

    /**
     * Review finding 1: a canonical id re-created before the next upload must not replace the delete in the log, or the
     * peers keep the old pages (cascaded deletes are not logged) and send them back later.
     */
    @Test fun canonicalBookDeletedAndRecreatedBeforeUploadDoesNotKeepOldPages() = run {
        val (a, b) = sharedCanonicalBook()
        a.deleteAndRecreateBook("a2")
        sync(a, b)
        assertEquals(setOf("a2/a2=content a2"), b.pages())
        assertEquals(setOf("a2/a2=content a2"), a.pages())

        // B edits the book (carries its children) and re-sends its whole log (R1): nothing old comes back to A.
        b.md.update(b.book().apply { name = "AI renamed" })
        b.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 0L)
        sync(b, a)
        assertEquals(setOf("a2/a2=content a2"), a.pages())
        assertEquals("AI renamed", a.book().name)
    }

    @Test fun canonicalBookDeletedAndRecreatedBeforeUploadReachesAnOldPeer() = run {
        val (a, b) = sharedCanonicalBook()
        a.deleteAndRecreateBook("a2")
        sync(a, b, toNew = false)
        assertEquals(setOf("a2/a2=content a2"), b.pages())
        b.md.update(b.book().apply { name = "AI renamed" })
        sync(b, a)
        assertEquals(setOf("a2/a2=content a2"), a.pages())
    }

    /** The other direction: B deleted the book; A, having applied that delete, re-creates it, and so does B later. */
    @Test fun bookRecreatedAfterAReceivedDeleteGetsAFreshId() = run {
        val (a, b) = sharedCanonicalBook()
        b.md.delete(b.book())
        sync(b, a)
        assertEquals(emptyList<MyDocument>(), a.md.allDocuments())
        assert(a.md.newMyDocumentId("AIDocuments") != canonicalMyDocumentId("AIDocuments"))
        a.addPage(MyDocument(id = a.md.newMyDocumentId("AIDocuments"), name = "AI", initials = "AIDocuments").also { a.md.insert(it) }.id, "a2")
        syncBoth(a, b)
        assertEquals(setOf("a2/a2=content a2"), a.pages())
        assertEquals(setOf("a2/a2=content a2"), b.pages())
    }

    /** Without a logged delete the canonical id is used, so two devices that create the same document still share its id. */
    @Test fun canonicalIdOfANeverDeletedDocumentIsReused() = run {
        val a = docDevice()
        assertEquals(canonicalMyDocumentId("Foo"), a.md.newMyDocumentId("Foo"))
    }

    // ---------------------------------------------------------------- review minors (stale alias, usage clash, R2 marker)

    /**
     * Review minor 3: an alias whose foreign id has since become a real local row (here: injected for A's own book, as
     * left behind when a patch carrying the local id let the foreign row in) can never apply again and must not block
     * a later alias that targets that row.
     */
    @Test fun aliasWhoseForeignIdIsALocalRowDoesNotBlockALaterAlias() = run {
        val (a, b) = divergedBooks()
        a.sql("INSERT INTO SyncConfiguration (keyName, stringValue) SELECT '${ID_ALIAS_PREFIX}MyDocument:' || hex(id), hex(randomblob(16)) FROM MyDocument")
        sync(b, a) // learns B's book -> A's book
        b.addPage(b.book().id, "b2") // child-only patch: needs the stored alias
        sync(b, a)
        assertEquals(setOf("a/a=content a", "b/b=content b", "b2/b2=content b2"), a.pages())
        // The stale alias is gone; the live one stays.
        assertEquals(1L, a.aliasCount())
    }

    /**
     * Review minor 4: B's patch holds usage rows of the same device under both B's model and A's model id. After the
     * mapping both name A's model; one row stays, and the dropped row's log entry must not land without its row.
     */
    @Test fun clashingUsageRowIsDroppedWithItsLogEntry() = run {
        val a = aiDevice()
        val b = aiDevice()
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        a.localDb.llmProviderConfigDao().insert(provider)
        sync(a, b)
        val modelA = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m").also { a.localDb.llmConfiguredModelDao().insert(it) }
        val modelB = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m").also { b.localDb.llmConfiguredModelDao().insert(it) }
        b.localDb.llmUsageRecordDao().upsert(LlmUsageRecord(configuredModelId = modelB.id, deviceId = "x", inputTokens = 1))
        b.localDb.llmUsageRecordDao().upsert(LlmUsageRecord(configuredModelId = modelA.id, deviceId = "x", inputTokens = 2))
        sync(b, a)

        assertEquals(listOf(2L), a.localDb.llmUsageRecordDao().getByModel(modelA.id).map { it.inputTokens })
        assertEquals(0L, a.count("SELECT count(*) FROM LogEntry WHERE tableName = 'LlmUsageRecord' AND type = 'UPSERT' AND entityId1 NOT IN (SELECT id FROM LlmUsageRecord)"))
    }

    /**
     * Review minor 5: the re-send for a newly learned pair (R2) must survive an upload that finishes after the apply and
     * writes its own watermark.
     */
    @Test fun learnedPairResendSurvivesAnOverlappingUploadWatermark() = run {
        val (a, b) = divergedBooks()
        startIdAliasResendOnce(a)
        createPatchForDatabase(a, env)
        sync(b, a) // A learns B's book id: R2
        a.dao.setConfig(LAST_PATCH_WRITTEN_KEY, System.currentTimeMillis()) // an overlapping upload finishes
        tick()
        assert(a.hasChanges())
        startIdAliasResendOnce(a) // the next upload
        assertEquals(0L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        startIdAliasResendOnce(a) // consumed: once per learned pair
        createPatchForDatabase(a, env)
        startIdAliasResendOnce(a)
        assert(a.dao.getLong(LAST_PATCH_WRITTEN_KEY)!! > 0L)
    }

    /**
     * Review finding 6: the merge (join) path applies the snapshot through `applyPatchesForDatabase`, so the aliasing, R2
     * and R3 run there too. B leaves sync and joins again: its database becomes a copy of the cloud (A's, with A's stored
     * alias of B's book), and B's own data (its diverged book, re-keyed by the merge) is merged in.
     */
    @Test fun mergeOfADivergedSnapshotLandsUnderTheCloudBookAndLearnsNothing() = run {
        val (a, b) = divergedBooks()
        syncBoth(a, b)
        b.addPage(b.book().id, "b2")
        b.editPage("a", "a-b", "a edited on b")
        tick()
        val m = docDevice(copyOf = File(a.localDbPath), deviceId = b.deviceId)
        m.dao.setConfig(LAST_PATCH_WRITTEN_KEY, System.currentTimeMillis())
        m.dao.removeConfig(ID_ALIAS_RESEND_PENDING_KEY) // A's own pending R2 came with the copy
        val snapshot = File.createTempFile("snapshot-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        prepareMergeSnapshot(b, snapshot.toOkioPath())
        tick()
        mergeSnapshotIntoLocal(m, env, snapshot.toOkioPath(), System.currentTimeMillis() + 10_000)

        val expected = setOf("a/a-b=a edited on b", "b/b=content b", "b2/b2=content b2")
        assertEquals(a.book().id, m.book().id)
        assertEquals(expected, m.pages())
        assertEquals(1L, m.aliasCount()) // A's alias, nothing new learned
        assertNull(m.dao.getBoolean(ID_ALIAS_RESEND_PENDING_KEY))
        sync(m, a)
        assertEquals(expected, a.pages())
    }

    // ---------------------------------------------------------------- historic convergence (R1-R3) and known limits

    /** A sync of the new version: the one-time re-send (R1) runs before the patch is created, as in CloudSync. */
    private suspend fun <T : SyncableRoomDatabase> syncNew(from: SyncableDatabaseAccessor<T>, to: SyncableDatabaseAccessor<T>) {
        startIdAliasResendOnce(from)
        sync(from, to)
    }

    @Test fun historicDivergedPagesConvergeAfterBothUpdate() = run {
        val (a, b) = divergedBooks()
        // Before the update: each device dropped the other's page.
        sync(a, b, toNew = false)
        sync(b, a, toNew = false)
        assertEquals(setOf("a/a=content a"), a.pages())
        assertEquals(setOf("b/b=content b"), b.pages())

        // A updates first; B is still old when A's re-send arrives, and drops A's page again.
        startIdAliasResendOnce(a)
        sync(a, b, toNew = false)
        assertEquals(setOf("b/b=content b"), b.pages())
        // B updates: its re-send teaches A the alias (A restores B's page and re-sends once more, R2).
        startIdAliasResendOnce(b)
        sync(b, a)
        assertEquals(setOf("a/a=content a", "b/b=content b"), a.pages())
        assert(a.hasChanges()) // R2 pending: the next upload (syncNew) re-sends the whole log
        syncNew(a, b)
        assertEquals(setOf("a/a=content a", "b/b=content b"), b.pages())

        // Bounded: a couple more rounds and nobody has anything left to send.
        repeat(2) { syncBoth(a, b) }
        assertNull(createPatchForDatabase(a, env))
        assertNull(createPatchForDatabase(b, env))
        assertEquals(setOf("a/a=content a", "b/b=content b"), a.pages())
        assertEquals(setOf("a/a=content a", "b/b=content b"), b.pages())
    }

    /**
     * Review finding 6: the one-time re-send (R1) reaching an old peer whose log lacks entries. The log is never pruned,
     * except that the first device clears it before uploading the cloud's initial file, so a peer that joined from that
     * file holds rows without entries. The re-send must not overwrite its newer edits, resurrect its deletes or move rows.
     */
    @Test fun resendToAnOldPeerThatJoinedFromTheInitialFileChangesNothing() = run {
        val u = docDevice()
        val book = u.addBook()
        for (k in listOf("p1", "p2", "p3")) u.addPage(book.id, k)
        u.dao.clearLog() // createAndUploadInitial
        val initial = File(u.localDbPath)
        val a = docDevice(copyOf = initial)
        val c = docDevice(copyOf = initial)
        a.editPage("p1", "p1-a", "p1 edited on a")
        a.md.deletePageWithContent(a.md.pagesForDocument(book.id).single { it.pageKey == "p2" })
        sync(a, c, toNew = false)
        c.editPage("p1", "p1-c", "p1 edited on c")
        c.md.deletePageWithContent(c.md.pagesForDocument(book.id).single { it.pageKey == "p3" })
        val before = c.pages()
        assertEquals(setOf("p1/p1-c=p1 edited on c"), before)

        startIdAliasResendOnce(a)
        assertEquals(0L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        sync(a, c, toNew = false)
        assertEquals(before, c.pages())
    }

    @Test fun resendOnceIsOncePerDevice() = run {
        val a = docDevice()
        a.addPage(a.addBook().id, "a")
        createPatchForDatabase(a, env)
        val watermark = a.dao.getLong(LAST_PATCH_WRITTEN_KEY)!!
        startIdAliasResendOnce(a)
        assertEquals(0L, a.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        createPatchForDatabase(a, env)
        startIdAliasResendOnce(a)
        assert(a.dao.getLong(LAST_PATCH_WRITTEN_KEY)!! >= watermark)
    }

    /** R3 for a table without `parents`: a prompt of the other device's model, dropped before the update, comes back. */
    @Test fun droppedRowWithoutParentsIsRestoredFromTheResend() = run {
        val a = aiDevice()
        val b = aiDevice()
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        a.localDb.llmProviderConfigDao().insert(provider)
        sync(a, b, toNew = false)
        val modelA = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m").also { a.localDb.llmConfiguredModelDao().insert(it) }
        val modelB = LlmConfiguredModel(providerConfigId = provider.id, modelId = "m").also { b.localDb.llmConfiguredModelDao().insert(it) }
        val promptB = AgentPrompt(name = "b").also { it.configuredModelId = modelB.id }
        b.localDb.agentPromptDao().insert(promptB)
        sync(b, a, toNew = false)
        assertNull(a.localDb.agentPromptDao().promptById(promptB.id)) // dropped as before #3906

        syncNew(b, a)
        assertEquals(modelA.id, a.localDb.agentPromptDao().promptById(promptB.id)?.configuredModelId)
    }

    @Test fun resendDoesNotRestoreARowDeletedLocallyLater() = run {
        val a = docDevice()
        val b = docDevice()
        val book = a.addBook()
        a.addPage(book.id, "p")
        sync(a, b, toNew = false)
        sync(b, a, toNew = false)
        a.md.delete(a.md.pagesForDocument(book.id).single())

        syncNew(b, a) // B's whole log again, with the page's older UPSERT entry
        assertEquals(emptySet<String>(), a.pages())
    }

    /** Pre-#3906, B merged A's status onto its own row in place; B then deleted it. A's re-send must not bring it back. */
    @Test fun resendDoesNotRestoreARowThatWasMergedInPlaceAndDeleted() = run {
        val a = planDevice()
        val b = planDevice()
        for (d in listOf(a, b)) {
            d.rp.updatePlan(ReadingPlan("y1ot", DbDate(1000L)))
            d.rp.addPlanStatus(ReadingPlanStatus("y1ot", 1, "READ"))
            tick()
        }
        sync(a, b, toNew = false)
        b.rp.deleteStatusesForPlan("y1ot")

        syncNew(a, b)
        assertNull(b.rp.getStatus("y1ot", 1))
    }

    /**
     * Known limit (spec §5, accepted 2026-10-31): diverged ids, B old, A updated. A maps B's page under its own book; when A
     * then edits that page, its patch names A's book id, which B does not have, and old B's clean-up drops its own page.
     * Pinned so that a change here is deliberate.
     */
    @Test fun knownLimitOldPeerLosesItsPageWhenAnUpdatedDeviceEditsIt() = run {
        val (a, b) = divergedBooks()
        sync(b, a)
        assertEquals(setOf("a/a=content a", "b/b=content b"), a.pages())
        a.editPage("b", "b-a", "edited on a")
        sync(a, b, toNew = false)
        assertEquals(emptySet<String>(), b.pages())
    }
}
