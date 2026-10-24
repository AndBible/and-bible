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
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.CommonUtils
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import net.bible.service.db.useWriterConnectionMarked
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [rekeyNaturalKeys] on two independent real databases (the snapshot file and the "cloud" file, both created by
 * Room with the bundled driver, rows inserted with raw SQL). Ids are 16-byte blobs, written here as `X'..'`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class NaturalKeysTest {
    private val files = mutableListOf<File>()

    @After fun tearDown() {
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    /** A 16-byte id literal; [n] is the last byte, [side] the first, so ids of the two databases never match by accident. */
    private fun id(side: Char, n: Int) = "X'" + side.code.toString(16).padStart(2, '0') + "0".repeat(28) + n.toString(16).padStart(2, '0') + "'"

    private fun <T : SyncableRoomDatabase> create(klass: Class<T>, init: (androidx.sqlite.SQLiteConnection) -> Unit): File {
        val file = File.createTempFile("natkey-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        val db = Room.databaseBuilder(application, klass, file.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()
        runBlocking { db.useWriterConnectionMarked { } }
        db.close()
        openSqlite(file.path).use(init)
        return file
    }

    private fun doc(c: androidx.sqlite.SQLiteConnection, id: String, initials: String) = c.exec(
        "INSERT INTO MyDocument (id, name, description, initials, orderNumber, createdAt, updatedAt, sourcePromptId) " +
            "VALUES ($id, 'n', NULL, ?, 0, 1, 1, NULL)", initials)

    private fun page(c: androidx.sqlite.SQLiteConnection, id: String, doc: String, key: String) = c.exec(
        "INSERT INTO MyDocumentPage (id, documentId, title, pageKey, contentType, orderNumber, createdAt, updatedAt, " +
            "sourcePromptId, languageCode) VALUES ($id, $doc, 't', ?, 'MARKDOWN', 0, 1, 1, NULL, NULL)", key)

    private fun log(c: androidx.sqlite.SQLiteConnection, table: String, id1: String, id2: String = "''") = c.exec(
        "INSERT INTO LogEntry (tableName, entityId1, entityId2, type, lastUpdated, sourceDevice) " +
            "VALUES (?, $id1, $id2, 'UPSERT', 5, 'dev')", table)

    private fun plan(c: androidx.sqlite.SQLiteConnection, id: String, code: String) = c.exec(
        "INSERT INTO ReadingPlan (planCode, planStartDate, planCurrentDay, id) VALUES (?, 0, 1, $id)", code)

    private fun rekey(category: SyncableDatabaseDefinition, snapshot: File, cloud: File) = run {
        openSqlite(cloud.path).use { rekeyNaturalKeys(category, snapshot.toOkioPath(), it) }
    }

    private fun query(file: File, sql: String): List<String> =
        openSqlite(file.path).use { c -> c.queryRows(sql) { it.getText(0) } }

    @Test fun samePlanCodeBecomesOneId() {
        val snapshot = create(ReadingPlanDatabase::class.java) { plan(it, id('s', 1), "bible1y"); plan(it, id('s', 2), "other"); log(it, "ReadingPlan", id('s', 1)); log(it, "ReadingPlan", id('s', 2)) }
        val cloud = create(ReadingPlanDatabase::class.java) { plan(it, id('c', 1), "bible1y") }

        rekey(SyncableDatabaseDefinition.READINGPLANS, snapshot, cloud)

        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM ReadingPlan WHERE planCode = 'bible1y' AND id = ${id('c', 1)}"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 = ${id('c', 1)}"))
        assertEquals(listOf("0"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 = ${id('s', 1)}"))
        // The row that is new to the cloud keeps its id, and its log entry.
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 = ${id('s', 2)}"))
    }

    @Test fun myDocumentSameInitialsKeepsPagesAndContent() {
        val snapshot = create(MyDocumentDatabase::class.java) {
            doc(it, id('s', 1), "MyDoc_a")
            page(it, id('s', 11), id('s', 1), "p1")
            it.exec("INSERT INTO MyDocumentPageContent (pageId, content) VALUES (${id('s', 11)}, 'body')")
        }
        val cloud = create(MyDocumentDatabase::class.java) { doc(it, id('c', 1), "MyDoc_a") }

        rekey(SyncableDatabaseDefinition.MYDOCUMENTS, snapshot, cloud)

        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM MyDocument WHERE id = ${id('c', 1)}"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM MyDocumentPage WHERE documentId = ${id('c', 1)}"))
        assertEquals(listOf("body"), query(snapshot, "SELECT c.content FROM MyDocumentPageContent c JOIN MyDocumentPage p ON p.id = c.pageId JOIN MyDocument d ON d.id = p.documentId"))
        assertEquals(listOf("0"), query(snapshot, "SELECT count(*) FROM MyDocumentPage WHERE documentId NOT IN (SELECT id FROM MyDocument)"))
        assertEquals(listOf("0"), query(snapshot, "SELECT count(*) FROM MyDocumentPageContent WHERE pageId NOT IN (SELECT id FROM MyDocumentPage)"))
    }

    @Test fun pageKeyCollisionUnderReKeyedDocument() {
        val snapshot = create(MyDocumentDatabase::class.java) {
            doc(it, id('s', 1), "MyDoc_a")
            page(it, id('s', 11), id('s', 1), "same")
            page(it, id('s', 12), id('s', 1), "mine")
            it.exec("INSERT INTO MyDocumentPageContent (pageId, content) VALUES (${id('s', 11)}, 'device')")
            it.exec(
                "INSERT INTO AiPageCacheEntry (pageId, sourcePromptId, sourceContext, kjvOrdinalStart, kjvOrdinalEnd, contextHash, " +
                    "usedWriteTools, sourceModelName, sourceBookInitials, sourceBookKey) " +
                    "VALUES (${id('s', 11)}, ${id('s', 99)}, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL)"
            )
            log(it, "MyDocumentPageContent", id('s', 11))
            log(it, "AiPageCacheEntry", id('s', 11))
        }
        val cloud = create(MyDocumentDatabase::class.java) {
            doc(it, id('c', 1), "MyDoc_a")
            page(it, id('c', 11), id('c', 1), "same")
        }

        rekey(SyncableDatabaseDefinition.MYDOCUMENTS, snapshot, cloud)

        assertEquals(listOf("mine", "same"), query(snapshot, "SELECT pageKey FROM MyDocumentPage ORDER BY pageKey"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM MyDocumentPage WHERE id = ${id('c', 11)} AND pageKey = 'same' AND documentId = ${id('c', 1)}"))
        assertEquals(listOf("device"), query(snapshot, "SELECT content FROM MyDocumentPageContent WHERE pageId = ${id('c', 11)}"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM AiPageCacheEntry WHERE pageId = ${id('c', 11)}"))
        // Content and cache log entries (keyed by pageId) follow the chained re-key.
        assertEquals(listOf("AiPageCacheEntry", "MyDocumentPageContent"), query(snapshot, "SELECT tableName FROM LogEntry WHERE entityId1 = ${id('c', 11)} ORDER BY tableName"))
        // The page the cloud does not have keeps its id but sits under the cloud's document.
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM MyDocumentPage WHERE id = ${id('s', 12)} AND documentId = ${id('c', 1)}"))
    }

    @Test fun logEntriesFollowTheNewId() {
        val snapshot = create(MyDocumentDatabase::class.java) {
            doc(it, id('s', 1), "MyDoc_a"); page(it, id('s', 11), id('s', 1), "p")
            log(it, "MyDocument", id('s', 1)); log(it, "MyDocumentPage", id('s', 11))
        }
        val cloud = create(MyDocumentDatabase::class.java) { doc(it, id('c', 1), "MyDoc_a"); page(it, id('c', 11), id('c', 1), "p") }

        rekey(SyncableDatabaseDefinition.MYDOCUMENTS, snapshot, cloud)

        assertEquals(listOf("0"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 IN (${id('s', 1)}, ${id('s', 11)})"))
        assertEquals(listOf("MyDocument"), query(snapshot, "SELECT tableName FROM LogEntry WHERE entityId1 = ${id('c', 1)}"))
        assertEquals(listOf("MyDocumentPage"), query(snapshot, "SELECT tableName FROM LogEntry WHERE entityId1 = ${id('c', 11)}"))
    }

    @Test fun noCollisionNoChange() {
        val snapshot = create(MyDocumentDatabase::class.java) {
            doc(it, id('s', 1), "MyDoc_mine"); page(it, id('s', 11), id('s', 1), "p"); log(it, "MyDocument", id('s', 1))
        }
        val cloud = create(MyDocumentDatabase::class.java) { doc(it, id('c', 1), "MyDoc_theirs") }
        val before = query(snapshot, "SELECT hex(id) || hex(documentId) FROM MyDocumentPage") + query(snapshot, "SELECT hex(entityId1) FROM LogEntry")

        rekey(SyncableDatabaseDefinition.MYDOCUMENTS, snapshot, cloud)

        assertEquals(before, query(snapshot, "SELECT hex(id) || hex(documentId) FROM MyDocumentPage") + query(snapshot, "SELECT hex(entityId1) FROM LogEntry"))
        assertEquals(listOf("MyDoc_mine"), query(snapshot, "SELECT initials FROM MyDocument"))
    }

    private fun planIds(file: File) = query(file, "SELECT planCode || '=' || hex(id) FROM ReadingPlan ORDER BY planCode")
    private fun hex(literal: String) = literal.removePrefix("X'").removeSuffix("'").uppercase()

    @Test fun swappedIdsAreExchanged() {
        val snapshot = create(ReadingPlanDatabase::class.java) {
            plan(it, id('c', 1), "a"); plan(it, id('c', 2), "b"); log(it, "ReadingPlan", id('c', 1)); log(it, "ReadingPlan", id('c', 2))
        }
        val cloud = create(ReadingPlanDatabase::class.java) { plan(it, id('c', 2), "a"); plan(it, id('c', 1), "b") }

        rekey(SyncableDatabaseDefinition.READINGPLANS, snapshot, cloud)

        assertEquals(listOf("a=${hex(id('c', 2))}", "b=${hex(id('c', 1))}"), planIds(snapshot))
        assertEquals(listOf("2"), query(snapshot, "SELECT count(*) FROM LogEntry"))
        assertEquals(listOf("a"), query(snapshot, "SELECT p.planCode FROM LogEntry l JOIN ReadingPlan p ON p.id = l.entityId1 WHERE l.entityId1 = ${id('c', 2)}"))
    }

    @Test fun chainIsRenamedWhicheverRowComesFirst() {
        // A (id c1) goes to c2, which B holds; B goes to c3.
        val snapshot = create(ReadingPlanDatabase::class.java) { plan(it, id('c', 1), "a"); plan(it, id('c', 2), "b") }
        val cloud = create(ReadingPlanDatabase::class.java) { plan(it, id('c', 2), "a"); plan(it, id('c', 3), "b") }

        rekey(SyncableDatabaseDefinition.READINGPLANS, snapshot, cloud)

        assertEquals(listOf("a=${hex(id('c', 2))}", "b=${hex(id('c', 3))}"), planIds(snapshot))
    }

    @Test fun targetIdHeldByANonRekeyedRowThrowsAndLeavesTheSnapshotUntouched() {
        // The device holds a row under the cloud's id for another key; a re-key would break the primary key.
        val snapshot = create(ReadingPlanDatabase::class.java) { plan(it, id('c', 1), "x"); plan(it, id('s', 1), "y"); log(it, "ReadingPlan", id('s', 1)) }
        val cloud = create(ReadingPlanDatabase::class.java) { plan(it, id('c', 1), "y") }
        val before = planIds(snapshot)

        val failure = runCatching { rekey(SyncableDatabaseDefinition.READINGPLANS, snapshot, cloud) }.exceptionOrNull()

        assertEquals(true, failure is IllegalStateException)
        assertEquals(before, planIds(snapshot))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 = ${id('s', 1)}"))
    }

    private fun usage(c: androidx.sqlite.SQLiteConnection, id: String, model: String, device: String, tokens: Int) = c.exec(
        "INSERT INTO LlmUsageRecord (id, configuredModelId, deviceId, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, estimatedCostUsd) " +
            "VALUES ($id, $model, ?, $tokens, 0, 0, 0, 0.0)", device)

    private fun aiSettings(c: androidx.sqlite.SQLiteConnection, side: Char, model: Int) {
        c.exec("INSERT INTO LlmProviderConfig (id, providerType, displayName, endpoint, apiFormat, orderNumber) VALUES (${id('p', 1)}, 'T', 'P', NULL, NULL, 0)")
        c.exec("INSERT INTO LlmConfiguredModel (id, providerConfigId, modelId, orderNumber, inputPricePerMillion, outputPricePerMillion, cacheCreationPricePerMillion, cacheReadPricePerMillion) VALUES (${id(side, model)}, ${id('p', 1)}, 'gpt', 0, 0, 0, 0, 0)")
    }

    @Test fun aiSettingsModelChildrenFollowTheReKey() {
        val snapshot = create(AiSettingsDatabase::class.java) {
            aiSettings(it, 's', 1)
            it.exec("INSERT INTO AgentPrompt (id, name, promptTemplate, showIn, configuredModelId) VALUES (${id('s', 5)}, 'n', 't', 'ALL', ${id('s', 1)})")
            it.exec("INSERT INTO BuiltinPromptOverride (id, configuredModelId) VALUES (${id('s', 6)}, ${id('s', 1)})")
            it.exec("INSERT INTO GlobalAiSettings (id, aiExcludedDocuments, hiddenBuiltInPrompts, hiddenBuiltInCategories, favoritePrompts, commentaryDeselected, defaultModelId) VALUES (${id('s', 7)}, '', '', '', '', '', ${id('s', 1)})")
            usage(it, id('s', 8), id('s', 1), "dev", 10)
            log(it, "LlmConfiguredModel", id('s', 1))
        }
        val cloud = create(AiSettingsDatabase::class.java) { aiSettings(it, 'c', 1) }

        rekey(SyncableDatabaseDefinition.AI_SETTINGS, snapshot, cloud)

        val model = id('c', 1)
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LlmConfiguredModel WHERE id = $model"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM AgentPrompt WHERE configuredModelId = $model"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM BuiltinPromptOverride WHERE configuredModelId = $model"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM GlobalAiSettings WHERE defaultModelId = $model"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LlmUsageRecord WHERE configuredModelId = $model"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE tableName = 'LlmConfiguredModel' AND entityId1 = $model"))
    }

    @Test fun staleUsageRowForTheCloudModelIsDroppedNotACollision() {
        val snapshot = create(AiSettingsDatabase::class.java) {
            aiSettings(it, 's', 1)
            usage(it, id('s', 8), id('s', 1), "dev", 10)
            // Stale: the snapshot still holds a usage row for the cloud's model id, for the same device.
            usage(it, id('s', 9), id('c', 1), "dev", 99)
            log(it, "LlmUsageRecord", id('s', 8)); log(it, "LlmUsageRecord", id('s', 9))
        }
        val cloud = create(AiSettingsDatabase::class.java) { aiSettings(it, 'c', 1) }

        rekey(SyncableDatabaseDefinition.AI_SETTINGS, snapshot, cloud)

        assertEquals(listOf("10"), query(snapshot, "SELECT inputTokens FROM LlmUsageRecord WHERE configuredModelId = ${id('c', 1)}"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LlmUsageRecord"))
        assertEquals(listOf("0"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 = ${id('s', 9)}"))
    }

    private fun status(c: androidx.sqlite.SQLiteConnection, id: String, code: String, day: Int, text: String) = c.exec(
        "INSERT INTO ReadingPlanStatus (planCode, planDay, readingStatus, id) VALUES (?, ?, ?, $id)", code, day.toLong(), text)

    @Test fun readingPlanStatusSameCodeAndDayTakesTheCloudId() {
        val snapshot = create(ReadingPlanDatabase::class.java) {
            status(it, id('s', 1), "p", 1, "S1"); status(it, id('s', 2), "p", 2, "S2")
            log(it, "ReadingPlanStatus", id('s', 1)); log(it, "ReadingPlanStatus", id('s', 2))
        }
        val cloud = create(ReadingPlanDatabase::class.java) { status(it, id('c', 1), "p", 1, "C1") }

        rekey(SyncableDatabaseDefinition.READINGPLANS, snapshot, cloud)

        assertEquals(listOf("p|1|S1|1"), query(snapshot, "SELECT planCode || '|' || planDay || '|' || readingStatus || '|' || (id = ${id('c', 1)}) FROM ReadingPlanStatus WHERE planDay = 1"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE tableName = 'ReadingPlanStatus' AND entityId1 = ${id('c', 1)}"))
        assertEquals(listOf("0"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 = ${id('s', 1)}"))
        // Day 2 is new to the cloud: unchanged, with its log entry.
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM ReadingPlanStatus WHERE id = ${id('s', 2)}"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 = ${id('s', 2)}"))
    }

    private fun memorized(c: androidx.sqlite.SQLiteConnection, id: String, ordinal: Int) = c.exec(
        "INSERT INTO MemorizedVerse (id, kjvOrdinal, memorizedAt) VALUES ($id, ?, 5)", ordinal.toLong())

    @Test fun memorizedVerseSameOrdinalTakesTheCloudId() {
        val snapshot = create(ProgressDatabase::class.java) {
            memorized(it, id('s', 1), 100); memorized(it, id('s', 2), 200)
            log(it, "MemorizedVerse", id('s', 1)); log(it, "MemorizedVerse", id('s', 2))
        }
        val cloud = create(ProgressDatabase::class.java) { memorized(it, id('c', 1), 100) }

        rekey(SyncableDatabaseDefinition.PROGRESS, snapshot, cloud)

        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM MemorizedVerse WHERE kjvOrdinal = 100 AND id = ${id('c', 1)}"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE tableName = 'MemorizedVerse' AND entityId1 = ${id('c', 1)}"))
        assertEquals(listOf("0"), query(snapshot, "SELECT count(*) FROM LogEntry WHERE entityId1 = ${id('s', 1)}"))
        assertEquals(listOf("1"), query(snapshot, "SELECT count(*) FROM MemorizedVerse WHERE kjvOrdinal = 200 AND id = ${id('s', 2)}"))
    }
}
