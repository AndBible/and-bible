/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import androidx.room3.PooledConnection
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.LogEntry
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.sharedcore.platform.AppSettings
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exec
import net.bible.service.db.queryLong
import net.bible.service.db.useReaderConnectionMarked
import net.bible.service.db.useWriterConnectionMarked

const val TRIGGERS_DISABLED_KEY = "triggersDisabled"
const val LAST_SYNCHRONIZED_KEY = "lastSynchronized"

enum class SyncableDatabaseDefinition {
    BOOKMARKS, WORKSPACES, READINGPLANS, MYDOCUMENTS, AI_SETTINGS, PROGRESS;
    class Table(
        val tableName: String,
        val idField1: String = "id",
        val idField2: String? = null,
    )
    val filename get() = when(this) {
        BOOKMARKS -> BookmarkDatabase.dbFileName
        READINGPLANS ->ReadingPlanDatabase.dbFileName
        WORKSPACES -> WorkspaceDatabase.dbFileName
        MYDOCUMENTS -> MyDocumentDatabase.dbFileName
        AI_SETTINGS -> AiSettingsDatabase.dbFileName
        PROGRESS -> ProgressDatabase.dbFileName
    }

    val tables get() = when(this) {
        BOOKMARKS -> listOf(
            Table(
                tableName = "Label"
            ),
            Table(
                tableName = "BibleBookmark"
            ),
            Table(
                tableName = "BibleBookmarkNotes",
                idField1 = "bookmarkId"
            ),
            Table(
                tableName = "BibleBookmarkToLabel",
                idField1 = "bookmarkId",
                idField2 = "labelId"
            ),
            Table(
                tableName = "GenericBookmark"
            ),
            Table(
                tableName = "GenericBookmarkNotes",
                idField1 = "bookmarkId"
            ),
            Table(
                tableName = "GenericBookmarkToLabel",
                idField1 = "bookmarkId",
                idField2 = "labelId"
            ),
            Table(
                tableName = "StudyPadTextEntry"
            ),
            Table(
                tableName = "StudyPadTextEntryText",
                idField1 = "studyPadTextEntryId"
            ),
        )
        WORKSPACES -> listOf(
            Table(tableName = "Workspace"),
            Table(tableName = "Window"),
            Table(tableName = "PageManager", idField1 = "windowId"),
            Table(tableName = "WorkspaceLabelOverride", idField1 = "workspaceId", idField2 = "labelId"),
            Table(tableName = "GlobalTextDisplaySettings"),
        )
        READINGPLANS -> listOf(
            Table(tableName = "ReadingPlan"),
            Table(tableName = "ReadingPlanStatus"),
        )
        MYDOCUMENTS -> listOf(
            Table(tableName = "MyDocument"),
            Table(tableName = "MyDocumentPage"),
            Table(tableName = "MyDocumentPageContent", idField1 = "pageId"),
            Table(tableName = "AiPageCacheEntry", idField1 = "pageId"),
        )
        AI_SETTINGS -> listOf(
            Table(tableName = "LlmProviderConfig"),
            Table(tableName = "LlmConfiguredModel"),
            Table(tableName = "AgentPrompt"),
            Table(tableName = "GlobalAiSettings"),
            Table(tableName = "LlmUsageRecord"),
            Table(tableName = "PromptCategory"),
            Table(tableName = "BuiltinPromptOverride"),
        )
        PROGRESS -> listOf(
            Table(tableName = "MemorizedVerse"),
            Table(tableName = "ChapterReadHistory"),
            Table(tableName = "MemorizationTarget"),
            Table(tableName = "GlobalReadingProgressSettings"),
        )
    }

    fun isSyncEnabled(settings: AppSettings) = settings.getBoolean("sync_enable_" + name.lowercase(), false)
    fun setSyncEnabled(settings: AppSettings, value: Boolean) = settings.setBoolean("sync_enable_" + name.lowercase(), value)
    val accessor get() = DatabaseContainer.databaseAccessorsByCategory[this]!!
    suspend fun lastSynchronized(settings: AppSettings): Long? = if (!isSyncEnabled(settings)) null else accessor.dao.getLong(LAST_SYNCHRONIZED_KEY)

    companion object {
        val ALL = arrayOf(BOOKMARKS, WORKSPACES, READINGPLANS, MYDOCUMENTS, AI_SETTINGS, PROGRESS)
        val nameToCategory = ALL.associateBy { it.name }
        val filenameToCategory = ALL.associateBy { it.filename }
    }
}

class SyncableDatabaseAccessor<T: SyncableRoomDatabase>(
    var localDb: T,
    val dbFactory: (filename: String) -> T,
    private val _resetLocalDb: () -> T,
    val localDbPath: String,
    val category: SyncableDatabaseDefinition,
    val _reactToUpdates: ((entries: List<LogEntry>) -> Unit)? = null,
    val deviceId: String,
    private val resetOwned: ((net.bible.service.llm.AiDatabaseLifetime.Replacement) -> T)? = null,
) {
    fun resetLocalDb() {
        localDb = _resetLocalDb()
    }

    internal fun resetLocalDbOwned(token: net.bible.service.llm.AiDatabaseLifetime.Replacement) {
        localDb = resetOwned?.invoke(token) ?: _resetLocalDb()
    }

    suspend fun reactToUpdates(lastSynchronized: Long) {
        val newEntries = dao.newLogEntries(lastSynchronized, deviceId)
        if(newEntries.isNotEmpty()) {
            _reactToUpdates?.invoke(newEntries)
        }
    }

    suspend fun bytesUsed(): Long {
        return dao.totalBytesUsed()
    }
    suspend fun hasChanges(): Boolean {
        val lastSynchronized = dao.getLong(LAST_SYNCHRONIZED_KEY)?: 0
        return dao.countNewLogEntries(lastSynchronized, deviceId) > 0
    }

    val categoryName get() = category.name.lowercase()
    val dao get() = localDb.syncDao()
    val tableDefinitions get() = category.tables
    /** `PRAGMA user_version` of the local database. */
    suspend fun version(): Int = localDb.useReaderConnectionMarked { it.queryLong("PRAGMA user_version")!!.toInt() }
}

private suspend fun createTriggersForTable(
    db: PooledConnection,
    dbDef: SyncableDatabaseAccessor<*>,
    tableDef: SyncableDatabaseDefinition.Table,
) = tableDef.run {
    fun where(prefix: String): String =
        if(idField2 == null) {
            "entityId1 = $prefix.$idField1"
        } else {
            "entityId1 = $prefix.$idField1 AND entityId2 = $prefix.$idField2"
        }
    fun insert(prefix: String): String =
        if(idField2 == null) {
            "$prefix.$idField1,''"
        } else {
            "$prefix.$idField1,$prefix.$idField2"
        }
    val timeStampFunc = "CAST(UNIXEPOCH('subsec') * 1000 AS INTEGER)"

    val deviceId = dbDef.deviceId
    val whenCondition = """
            WHEN (SELECT count(*) FROM SyncConfiguration WHERE keyName='${TRIGGERS_DISABLED_KEY}' AND booleanValue = 1 LIMIT 1) = 0
            """.trimIndent()

    db.exec("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_inserts AFTER INSERT ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("NEW")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("NEW")}, 'UPSERT', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
    db.exec("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_updates AFTER UPDATE ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("OLD")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("OLD")}, 'UPSERT', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
    db.exec("""
            CREATE TRIGGER IF NOT EXISTS ${tableName}_deletes AFTER DELETE ON $tableName $whenCondition 
            BEGIN DELETE FROM LogEntry WHERE ${where("OLD")} AND tableName = '$tableName';
            INSERT INTO LogEntry VALUES ('$tableName', ${insert("OLD")}, 'DELETE', $timeStampFunc, '$deviceId'); 
            END;
        """.trimIndent()
    )
}

private suspend fun dropTriggersForTable(
    db: PooledConnection,
    tableDef: SyncableDatabaseDefinition.Table
) = db.run {
    exec("DROP TRIGGER IF EXISTS ${tableDef.tableName}_inserts")
    exec("DROP TRIGGER IF EXISTS ${tableDef.tableName}_updates")
    exec("DROP TRIGGER IF EXISTS ${tableDef.tableName}_deletes")
}


suspend fun createTriggers(dbDef: SyncableDatabaseAccessor<*>) = dbDef.localDb.useWriterConnectionMarked { db ->
    for(tableDef in dbDef.tableDefinitions) {
        createTriggersForTable(db, dbDef, tableDef)
    }
}

suspend fun dropTriggers(dbDef: SyncableDatabaseAccessor<*>) = dbDef.localDb.useWriterConnectionMarked { db ->
    for(tableDef in dbDef.tableDefinitions) {
        dropTriggersForTable(db, tableDef)
    }
}
