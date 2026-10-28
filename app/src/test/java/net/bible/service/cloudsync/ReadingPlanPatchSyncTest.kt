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
import net.bible.android.database.DbDate
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.migrations.readingPlanMigrations
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.service.common.CommonUtils
import net.bible.service.db.useWriterConnectionMarked
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.DatabaseResetter
import okio.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Reading-plan changes travelling between two devices through normal patches (#3902): A and B are two devices
 * that already share one cloud folder (B started as a copy of A's database), writing through the same DAO calls
 * as `ReadingPlanRepository`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingPlanPatchSyncTest {
    private val opened = mutableListOf<SyncableDatabaseAccessor<ReadingPlanDatabase>>()
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

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(60_000) { block() } }

    private suspend fun device(copyOf: File? = null): SyncableDatabaseAccessor<ReadingPlanDatabase> {
        val file = File.createTempFile("rp-sync-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        copyOf?.copyTo(file, overwrite = true)
        fun open(path: String): ReadingPlanDatabase = Room.databaseBuilder(application, ReadingPlanDatabase::class.java, path)
            .addMigrations(*readingPlanMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()
        var db = open(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(db, { open(it) }, { db.close(); db = open(file.absolutePath); db },
            file.path, SyncableDatabaseDefinition.READINGPLANS, deviceId = UUID.randomUUID().toString())
        // As DatabaseContainer does at every open: a copied file carries the other device's triggers (and id).
        dropTriggers(dbDef)
        createTriggers(dbDef)
        dbDef.localDb.useWriterConnectionMarked { }
        return dbDef.also { opened += it }
    }

    /** B joins A's folder: a copy of A's file (as fetched), both with their log cleared and the watermark at now. */
    private suspend fun join(a: SyncableDatabaseAccessor<ReadingPlanDatabase>): SyncableDatabaseAccessor<ReadingPlanDatabase> {
        a.dao.clearLog()
        val initial = File.createTempFile("rp-initial-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        File(a.localDbPath).copyTo(initial, overwrite = true)
        val b = device(initial)
        val now = System.currentTimeMillis()
        a.dao.setConfig(LAST_PATCH_WRITTEN_KEY, now)
        b.dao.setConfig(LAST_PATCH_WRITTEN_KEY, now)
        tick()
        return b
    }

    /** The triggers stamp with millisecond precision; keep writes of separate steps apart. */
    private fun tick() = Thread.sleep(5)

    private suspend fun sync(from: SyncableDatabaseAccessor<ReadingPlanDatabase>, to: SyncableDatabaseAccessor<ReadingPlanDatabase>) {
        tick()
        val patch: Path? = createPatchForDatabase(from, env)
        if (patch != null) applyPatchesForDatabase(to, env, patch)
        tick()
    }

    private val SyncableDatabaseAccessor<ReadingPlanDatabase>.rp get() = localDb.readingPlanDao()

    private suspend fun SyncableDatabaseAccessor<ReadingPlanDatabase>.start(planCode: String, startDate: Long = 1_000_000_000L) =
        rp.updatePlan(rp.getPlan(planCode)?.apply { planStartDate = DbDate(startDate) } ?: ReadingPlan(planCode, DbDate(startDate)))

    private suspend fun SyncableDatabaseAccessor<ReadingPlanDatabase>.markRead(planCode: String, day: Int, status: String = READ) =
        rp.addPlanStatus(ReadingPlanStatus(planCode, day, status))

    private suspend fun SyncableDatabaseAccessor<ReadingPlanDatabase>.reset(planCode: String) {
        rp.deleteStatusesForPlan(planCode)
        rp.deletePlanInfo(planCode)
    }

    @Test fun startedPlanAndReadDaysReachTheOtherDevice() = run {
        val a = device()
        val b = join(a)
        a.start(PLAN)
        a.markRead(PLAN, 1)
        a.markRead(PLAN, 2)

        sync(a, b)

        assertEquals(1_000_000_000L, b.rp.getPlan(PLAN)?.planStartDate?.getTime())
        assertEquals(READ, b.rp.getStatus(PLAN, 1)?.readingStatus)
        assertEquals(READ, b.rp.getStatus(PLAN, 2)?.readingStatus)
    }

    @Test fun aDayReadOnTheOtherDeviceComesBack() = run {
        val a = device()
        a.start(PLAN)
        a.markRead(PLAN, 1)
        val b = join(a)

        b.markRead(PLAN, 1, PARTIAL)
        b.markRead(PLAN, 2)
        sync(b, a)

        assertEquals(PARTIAL, a.rp.getStatus(PLAN, 1)?.readingStatus)
        assertEquals(READ, a.rp.getStatus(PLAN, 2)?.readingStatus)
    }

    @Test fun aResetReachesTheOtherDevice() = run {
        val a = device()
        a.start(PLAN)
        a.markRead(PLAN, 1)
        val b = join(a)

        a.reset(PLAN)
        sync(a, b)

        assertNull(b.rp.getPlan(PLAN))
        assertNull(b.rp.getStatus(PLAN, 1))
    }

    /**
     * The reset deletes the rows by id and the restart creates new ones under the same natural keys (planCode,
     * (planCode, planDay)), all in one patch. The upserts must not land on the other device's old rows only for
     * the deletes to remove them afterwards.
     */
    @Test fun aResetAndRestartInOnePatchKeepsThePlanOnTheOtherDevice() = run {
        val a = device()
        a.start(PLAN)
        a.markRead(PLAN, 1)
        a.markRead(PLAN, 2)
        val b = join(a)

        a.reset(PLAN)
        tick()
        a.start(PLAN, startDate = 2_000_000_000L)
        a.markRead(PLAN, 1)
        sync(a, b)

        assertEquals(2_000_000_000L, b.rp.getPlan(PLAN)?.planStartDate?.getTime())
        assertEquals(READ, b.rp.getStatus(PLAN, 1)?.readingStatus)
        assertNull(b.rp.getStatus(PLAN, 2))
        // Both devices hold the rows under the same ids, so a later change by id reaches the other side.
        assertEquals(a.rp.getPlan(PLAN)?.id, b.rp.getPlan(PLAN)?.id)
        assertEquals(a.rp.getStatus(PLAN, 1)?.id, b.rp.getStatus(PLAN, 1)?.id)

        a.reset(PLAN)
        sync(a, b)
        assertNull(b.rp.getPlan(PLAN))
        assertNull(b.rp.getStatus(PLAN, 1))
    }

    companion object {
        const val PLAN = "y1ot1nt1_OTthenNT"
        const val READ = """{"1":"READ","2":"READ"}"""
        const val PARTIAL = """{"1":"READ","2":"UNREAD"}"""
    }
}
