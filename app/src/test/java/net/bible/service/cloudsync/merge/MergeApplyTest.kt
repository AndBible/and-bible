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

package net.bible.service.cloudsync.merge

import androidx.room3.Room
import androidx.room3.RoomDatabase
import net.bible.android.database.migrations.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.GlobalTextDisplaySettings
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.android.database.migrations.readingPlanMigrations
import net.bible.android.database.migrations.workspacesMigrations
import net.bible.service.cloudsync.LAST_PATCH_WRITTEN_KEY
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.applyPatchesForDatabase
import net.bible.service.cloudsync.createPatchForDatabase
import net.bible.service.cloudsync.createTriggers
import net.bible.service.common.CommonUtils
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import net.bible.service.db.useWriterConnectionMarked
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.DatabaseResetter
import okio.Path.Companion.toOkioPath
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
 * [mergeSnapshotIntoLocal] on real databases (bundled driver). Roles: the cloud is device A (its file, copied after
 * `clearLog`, is the "initial file"; later edits travel as patches), the joining device is B (a copy of the initial
 * file plus its own edits, snapshotted), and M is B's database after the swap: a copy of the initial file with A's
 * patches applied, carrying B's device id. C is a third device that only ever sees the cloud. Log timestamps are
 * set by hand where the outcome depends on them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MergeApplyTest {
    private val opened = mutableListOf<SyncableDatabaseAccessor<*>>()
    private val files = mutableListOf<File>()
    private val env get() = KoinJavaComponent.get<AppEnvironment>(AppEnvironment::class.java)

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
    }

    @After fun tearDown() {
        opened.forEach { it.localDb.close() }
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
        DatabaseResetter.resetDatabase()
    }

    private suspend fun <T : SyncableRoomDatabase> accessor(
        klass: Class<T>, category: SyncableDatabaseDefinition, migrations: Array<Migration>,
        copyOf: File? = null, deviceId: String = UUID.randomUUID().toString(),
    ): SyncableDatabaseAccessor<T> {
        val file = File.createTempFile("merge-apply-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        copyOf?.copyTo(file, overwrite = true)
        fun open(path: String): T = Room.databaseBuilder(application, klass, path)
            .addMigrations(*migrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()
        var db = open(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(db, { open(it) }, { db.close(); db = open(file.absolutePath); db },
            file.path, category, deviceId = deviceId)
        createTriggers(dbDef)
        dbDef.localDb.useWriterConnectionMarked { }
        return dbDef.also { opened += it }
    }

    private suspend fun bookmarks(copyOf: File? = null, deviceId: String = UUID.randomUUID().toString()) =
        accessor(BookmarkDatabase::class.java, SyncableDatabaseDefinition.BOOKMARKS, bookmarkMigrations, copyOf, deviceId)

    /** Production-like watermark: `afterReset` stores NOW, so every pre-merge entry sits below it. */
    private fun now() = System.currentTimeMillis()
    private fun later() = now() + 10_000L

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(60_000) { block() } }

    /** The cloud's initial file: A's database with an empty log, as `createAndUploadInitial` leaves it. */
    private suspend fun initialOf(a: SyncableDatabaseAccessor<*>): File {
        a.dao.clearLog()
        return File.createTempFile("initial-", ".sqlite3", CommonUtils.tmpDir).also {
            files += it
            File(a.localDbPath).copyTo(it, overwrite = true)
        }
    }

    private suspend fun SyncableDatabaseAccessor<*>.sql(sql: String, vararg args: Any?) =
        localDb.useWriterConnectionMarked { it.exec(sql, *args) }

    private suspend fun SyncableDatabaseAccessor<*>.setLogTime(table: String, time: Long, type: String = "UPSERT", id: ByteArray? = null) =
        if (id == null) sql("UPDATE LogEntry SET lastUpdated = ? WHERE tableName = ? AND type = ?", time, table, type)
        else sql("UPDATE LogEntry SET lastUpdated = ? WHERE tableName = ? AND type = ? AND entityId1 = ?", time, table, type, id)

    private suspend fun SyncableDatabaseAccessor<BookmarkDatabase>.names(): Set<String> =
        localDb.bookmarkDao().allLabelsSortedByName().map { it.name }.filter { it.startsWith("t-") }.toSet()

    /** The merging device: [initial] plus the cloud's patch (when [cloud] edited after the initial upload), carrying B's id. */
    private suspend fun swapIn(initial: File, cloud: SyncableDatabaseAccessor<BookmarkDatabase>, b: SyncableDatabaseAccessor<BookmarkDatabase>): SyncableDatabaseAccessor<BookmarkDatabase> {
        val m = bookmarks(initial, b.deviceId)
        createPatchForDatabase(cloud, env)?.let { applyPatchesForDatabase(m, env, it) }
        m.dao.setConfig(LAST_PATCH_WRITTEN_KEY, now())
        return m
    }

    private suspend fun merge(m: SyncableDatabaseAccessor<BookmarkDatabase>, b: SyncableDatabaseAccessor<BookmarkDatabase>, mergeTime: Long = later()) {
        val snapshot = File.createTempFile("snapshot-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        prepareMergeSnapshot(b, snapshot.toOkioPath())
        mergeSnapshotIntoLocal(m, env, snapshot.toOkioPath(), mergeTime)
    }

    private suspend fun label(db: SyncableDatabaseAccessor<BookmarkDatabase>, name: String): BookmarkEntities.Label {
        val l = BookmarkEntities.Label(name = name)
        db.localDb.bookmarkDao().insert(l)
        return l
    }

    private suspend fun SyncableDatabaseAccessor<BookmarkDatabase>.nameOf(l: BookmarkEntities.Label) =
        localDb.bookmarkDao().labelById(l.id)?.name

    private suspend fun rename(db: SyncableDatabaseAccessor<BookmarkDatabase>, l: BookmarkEntities.Label, name: String, logTime: Long) {
        db.sql("UPDATE Label SET name = ? WHERE id = ?", name, l.id.toString().hexBytes())
        db.setLogTime("Label", logTime, id = l.id.toString().hexBytes())
    }

    private fun String.hexBytes() = replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun unionWithoutLoss() = run {
        val a = bookmarks()
        label(a, "t-a1")
        val ba = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 5, kjvOrdinalEnd = 6)
        a.localDb.bookmarkDao().insert(ba.bookmarkEntity)
        val initial = initialOf(a)
        val b = bookmarks()
        label(b, "t-b1")
        val bb = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 7, kjvOrdinalEnd = 8)
        b.localDb.bookmarkDao().insert(bb.bookmarkEntity)
        val m = swapIn(initial, a, b)

        merge(m, b)

        assertEquals(setOf("t-a1", "t-b1"), m.names())
        assertNotNull(m.localDb.bookmarkDao().bibleBookmarkById(ba.id))
        assertNotNull(m.localDb.bookmarkDao().bibleBookmarkById(bb.id))
    }

    private fun column(db: SyncableDatabaseAccessor<*>, sql: String): List<String> =
        openSqlite(db.localDbPath, readOnly = true).use { c -> c.queryRows(sql) { it.getText(0) } }

    @Test fun unionOfReadingPlans() = run {
        suspend fun plans() = accessor(ReadingPlanDatabase::class.java, SyncableDatabaseDefinition.READINGPLANS, readingPlanMigrations)
        val a = plans()
        a.sql("INSERT INTO ReadingPlan (planCode, planStartDate, planCurrentDay, id) VALUES ('cloud-plan', 0, 1, randomblob(16))")
        val initial = initialOf(a)
        val b = plans()
        b.sql("INSERT INTO ReadingPlan (planCode, planStartDate, planCurrentDay, id) VALUES ('device-plan', 0, 1, randomblob(16))")
        val m = accessor(ReadingPlanDatabase::class.java, SyncableDatabaseDefinition.READINGPLANS, readingPlanMigrations, initial, b.deviceId)
        m.dao.setConfig(LAST_PATCH_WRITTEN_KEY, now())
        val snapshot = File.createTempFile("snapshot-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        prepareMergeSnapshot(b, snapshot.toOkioPath())

        mergeSnapshotIntoLocal(m, env, snapshot.toOkioPath(), later())

        assertEquals(listOf("cloud-plan", "device-plan"), column(m, "SELECT planCode FROM ReadingPlan ORDER BY planCode"))
    }

    @Test fun deviceNewerWins() = run {
        val a = bookmarks()
        val l = label(a, "t-orig")
        val initial = initialOf(a)
        val b = bookmarks(initial)
        rename(b, l, "t-device", 3_000)
        rename(a, l, "t-cloud", 2_000)
        val m = swapIn(initial, a, b)

        merge(m, b)

        assertEquals("t-device", m.nameOf(l))
    }

    @Test fun cloudNewerWins() = run {
        val a = bookmarks()
        val l = label(a, "t-orig")
        val initial = initialOf(a)
        val b = bookmarks(initial)
        rename(b, l, "t-device", 2_000)
        rename(a, l, "t-cloud", 3_000)
        val m = swapIn(initial, a, b)

        merge(m, b)

        assertEquals("t-cloud", m.nameOf(l))
    }

    /** A bookmark's only date is `lastUpdatedOn`; without a log entry it dates the device's row. */
    @Test fun bookmarkDatedByLastUpdatedOnAgainstCloudLog() = run {
        suspend fun scenario(cloudLogTime: Long): Long? {
            val a = bookmarks()
            val bm = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 5, kjvOrdinalEnd = 6)
            a.localDb.bookmarkDao().insert(bm.bookmarkEntity)
            val initial = initialOf(a)
            val b = bookmarks(initial)
            b.sql("UPDATE BibleBookmark SET kjvOrdinalEnd = 60, lastUpdatedOn = 3000")
            b.dao.clearLog()
            a.sql("UPDATE BibleBookmark SET kjvOrdinalEnd = 61, lastUpdatedOn = 1")
            a.setLogTime("BibleBookmark", cloudLogTime)
            val m = swapIn(initial, a, b)
            merge(m, b)
            return m.localDb.bookmarkDao().bibleBookmarkById(bm.id)?.bookmarkEntity?.kjvOrdinalEnd?.toLong()
        }
        assertEquals(60L, scenario(cloudLogTime = 2_000))
        assertEquals(61L, scenario(cloudLogTime = 4_000))
    }

    @Test fun tieKeepsCloud() = run {
        val a = bookmarks()
        val l = label(a, "t-orig")
        val initial = initialOf(a)
        val b = bookmarks(initial)
        rename(b, l, "t-device", 2_500)
        rename(a, l, "t-cloud", 2_500)
        val m = swapIn(initial, a, b)

        merge(m, b)

        assertEquals("t-cloud", m.nameOf(l))
    }

    @Test fun cloudBaseRowWithoutLogLosesToDevice() = run {
        val a = bookmarks()
        val l = label(a, "t-orig")
        val initial = initialOf(a)
        val b = bookmarks(initial)
        b.sql("UPDATE Label SET name = 't-device' WHERE id = ?", l.id.toString().hexBytes())
        b.dao.clearLog() // the device's row has no entry (and no date) either
        val m = swapIn(initial, a, b)

        merge(m, b)

        assertEquals("t-device", m.nameOf(l))
    }

    private suspend fun workspaces(copyOf: File? = null, deviceId: String = UUID.randomUUID().toString()) =
        accessor(WorkspaceDatabase::class.java, SyncableDatabaseDefinition.WORKSPACES, workspacesMigrations, copyOf, deviceId)

    private suspend fun SyncableDatabaseAccessor<WorkspaceDatabase>.setFontSize(size: Int, logTime: Long?) {
        localDb.globalTextDisplaySettingsDao().set(
            GlobalTextDisplaySettings().also { it.textDisplaySettings.fontSize = size })
        if (logTime != null) setLogTime("GlobalTextDisplaySettings", logTime)
    }

    private suspend fun SyncableDatabaseAccessor<WorkspaceDatabase>.fontSize() =
        localDb.globalTextDisplaySettingsDao().get()?.textDisplaySettings?.fontSize

    private suspend fun workspaceScenario(cloudEditTime: Long?): Int? {
        val a = workspaces()
        a.setFontSize(10, null)
        val initial = initialOf(a)
        val b = workspaces(initial)
        b.setFontSize(15, 1)
        if (cloudEditTime != null) a.setFontSize(20, cloudEditTime)
        val m = workspaces(initial, b.deviceId)
        createPatchForDatabase(a, env)?.let { applyPatchesForDatabase(m, env, it) }
        m.dao.setConfig(LAST_PATCH_WRITTEN_KEY, now())
        val snapshot = File.createTempFile("snapshot-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        prepareMergeSnapshot(b, snapshot.toOkioPath())
        mergeSnapshotIntoLocal(m, env, snapshot.toOkioPath(), later())
        return m.fontSize()
    }

    @Test fun singletonEditedOnCloudAfterInitialKeepsCloud() = run {
        assertEquals(20, workspaceScenario(cloudEditTime = 5_000))
    }

    @Test fun singletonUntouchedTakesDevice() = run {
        assertEquals(15, workspaceScenario(cloudEditTime = null))
    }

    private suspend fun delete(db: SyncableDatabaseAccessor<BookmarkDatabase>, l: BookmarkEntities.Label, logTime: Long?) {
        db.sql("DELETE FROM Label WHERE id = ?", l.id.toString().hexBytes())
        if (logTime != null) db.setLogTime("Label", logTime, "DELETE", l.id.toString().hexBytes())
    }

    @Test fun deviceDeleteNewerRemovesCloudRow() = run {
        val a = bookmarks()
        val l = label(a, "t-orig")
        val initial = initialOf(a)
        val b = bookmarks(initial)
        delete(b, l, null) // the delete is logged now: newer than a cloud base row (no entry)
        val m = swapIn(initial, a, b)

        merge(m, b)

        assertNull(m.nameOf(l))
    }

    @Test fun cloudEditNewerThanDeviceDeleteKeepsRow() = run {
        val a = bookmarks()
        val l = label(a, "t-orig")
        val initial = initialOf(a)
        val b = bookmarks(initial)
        delete(b, l, 2_000)
        rename(a, l, "t-cloud", 3_000)
        val m = swapIn(initial, a, b)

        merge(m, b)

        assertEquals("t-cloud", m.nameOf(l))
    }

    /** Accepted resurrection: a row the cloud deleted before its initial upload leaves no tombstone. */
    @Test fun cloudDeletedBeforeInitialComesBack() = run {
        val a = bookmarks()
        val l = label(a, "t-orig")
        val b = bookmarks(File(a.localDbPath).copyTo(File.createTempFile("b-copy-", ".sqlite3", CommonUtils.tmpDir).also { files += it }, overwrite = true))
        delete(a, l, null)
        val initial = initialOf(a)
        assertNull(a.nameOf(l))
        val m = swapIn(initial, a, b)

        merge(m, b)

        assertEquals("t-orig", m.nameOf(l))
    }

    @Test fun thirdDeviceReceivesMergedRows() = run {
        val a = bookmarks()
        val won = label(a, "t-won")
        val lost = label(a, "t-lost")
        val initial = initialOf(a)
        val b = bookmarks(initial)
        label(b, "t-b-new")
        rename(b, won, "t-b-won", 3_000)
        rename(b, lost, "t-b-lost", 1_000)
        rename(a, won, "t-a-old", 2_000)
        rename(a, lost, "t-a-newer", 2_000)
        val cloudPatch = createPatchForDatabase(a, env)!!
        val m = bookmarks(initial, b.deviceId)
        applyPatchesForDatabase(m, env, cloudPatch)
        m.dao.setConfig(LAST_PATCH_WRITTEN_KEY, now())

        val mergeTime = later()
        merge(m, b, mergeTime)

        assertEquals(setOf("t-b-new", "t-b-won", "t-a-newer"), m.names())
        val patch = createPatchForDatabase(m, env)!!
        val c = bookmarks(initial)
        applyPatchesForDatabase(c, env, cloudPatch)
        applyPatchesForDatabase(c, env, patch)
        assertEquals(setOf("t-b-new", "t-b-won", "t-a-newer"), c.names())
        // C holds the winners' re-stamped entries (without the re-stamp no patch would carry them).
        assertEquals("$mergeTime|${b.deviceId}", column(c,
            "SELECT lastUpdated || '|' || sourceDevice FROM LogEntry WHERE tableName = 'Label' AND entityId1 = X'${won.id.toString().replace("-", "")}'").single())
        // Only the winners are uploaded: the lost row's entry is still the cloud's, not re-stamped.
        fun entry(l: BookmarkEntities.Label) = column(m,
            "SELECT lastUpdated || '|' || sourceDevice FROM LogEntry WHERE tableName = 'Label' AND entityId1 = X'${l.id.toString().replace("-", "")}'").single()
        assertEquals("2000|${a.deviceId}", entry(lost))
        assertEquals("$mergeTime|${b.deviceId}", entry(won))
    }

    @Test fun lastPatchWrittenNotBelowMergeTimeThrows() = run {
        val a = bookmarks()
        label(a, "t-a")
        val initial = initialOf(a)
        val b = bookmarks(initial)
        label(b, "t-b")
        val m = swapIn(initial, a, b)
        m.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 10_000L)

        val e = runCatching { merge(m, b, mergeTime = 10_000L) }.exceptionOrNull()

        assertTrue("got $e", e is IllegalStateException)
        assertEquals(setOf("t-a"), m.names())
    }
}
