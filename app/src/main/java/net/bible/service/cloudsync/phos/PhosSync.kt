/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync.phos

import android.os.Build
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import net.bible.service.db.BookmarksUpdatedViaSyncEvent
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.ReadingPlansUpdatedViaSyncEvent
import net.bible.service.db.WorkspacesUpdatedViaSyncEvent
import java.io.File

private const val TAG = "PhosSync"

/** Settings of the Phos Nextcloud sync (stored in the app's plain shared preferences). */
object PhosSyncSettings {
    const val ENABLED = "phos_sync_enabled"
    const val SERVER = "phos_sync_server_url"
    const val USER = "phos_sync_username"
    const val PASSWORD = "phos_sync_password"
    const val BOOKMARKS = "phos_sync_bookmarks"
    const val WORKSPACES = "phos_sync_workspaces"
    const val READING_PLANS = "phos_sync_readingplans"
    const val LAST_RUN = "phos_sync_last_run"
    const val LAST_RESULT = "phos_sync_last_result"

    private val prefs get() = CommonUtils.realSharedPreferences

    var enabled: Boolean
        get() = prefs.getBoolean(ENABLED, false)
        set(v) = prefs.edit().putBoolean(ENABLED, v).apply()
    val server: String get() = prefs.getString(SERVER, null).orEmpty()
    val user: String get() = prefs.getString(USER, null).orEmpty()
    var password: String
        get() = prefs.getString(PASSWORD, null).orEmpty()
        set(v) = prefs.edit().putString(PASSWORD, v).apply()
    val bookmarks: Boolean get() = prefs.getBoolean(BOOKMARKS, true)
    val workspaces: Boolean get() = prefs.getBoolean(WORKSPACES, true)
    val readingPlans: Boolean get() = prefs.getBoolean(READING_PLANS, true)
    var lastRun: Long
        get() = prefs.getLong(LAST_RUN, 0L)
        set(v) = prefs.edit().putLong(LAST_RUN, v).apply()
    var lastResult: String
        get() = prefs.getString(LAST_RESULT, null).orEmpty()
        set(v) = prefs.edit().putString(LAST_RESULT, v).apply()

    val configured: Boolean get() = enabled && server.isNotEmpty() && user.isNotEmpty() && password.isNotEmpty()
}

private class FileStateStore(private val file: File) : PhosStateStore {
    override fun load(): String? = try { if (file.exists()) file.readText() else null } catch (e: Exception) { null }
    override fun save(text: String) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

/**
 * Synchronizes bookmarks, workspaces and reading plans with the "Phos" app on a Nextcloud server,
 * independently of (and alongside) the file based cloud sync.
 */
object PhosSync {
    private val mutex = Mutex()

    val enabled: Boolean get() = PhosSyncSettings.configured

    private val stateFile get() = File(BibleApplication.application.filesDir, "phos-sync-state.json")

    private val userAgent get() = "AndBible (${Build.MODEL})"

    /** Newest change any synced database recorded (its sync triggers keep the log). */
    private fun newestChange(): Long {
        var newest = 0L
        for (db in databases()) {
            db.query("SELECT MAX(lastUpdated) FROM LogEntry").use { c -> if (c.moveToFirst() && !c.isNull(0)) newest = maxOf(newest, c.getLong(0)) }
        }
        return newest
    }

    private fun databases(): List<SupportSQLiteDatabase> = with(DatabaseContainer.instance) {
        buildList {
            if (PhosSyncSettings.bookmarks) add(bookmarkDb.openHelper.writableDatabase)
            if (PhosSyncSettings.workspaces) add(workspaceDb.openHelper.writableDatabase)
            if (PhosSyncSettings.readingPlans) add(readingPlanDb.openHelper.writableDatabase)
        }
    }

    /** True when something changed here since the last run. */
    fun hasChanges(): Boolean = enabled && newestChange() > PhosSyncSettings.lastRun

    /**
     * One run: push local changes, pull the server's. With [force] false it only runs when
     * something changed locally or [intervalMs] has passed (to see what other devices did).
     */
    suspend fun synchronize(force: Boolean = false, intervalMs: Long = 5 * 60 * 1000L): PhosRunResult? {
        if (!enabled) return null
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val now = System.currentTimeMillis()
                if (!force && newestChange() <= PhosSyncSettings.lastRun && now - PhosSyncSettings.lastRun < intervalMs) {
                    return@withLock null
                }
                try {
                    runOnce()
                } catch (e: Exception) {
                    Log.w(TAG, "Phos sync failed", e)
                    PhosSyncSettings.lastResult = e.message ?: e.javaClass.simpleName
                    null
                }
            }
        }
    }

    private fun runOnce(): PhosRunResult {
        if (!looksLikeAppPassword(PhosSyncSettings.password)) {
            PhosSyncSettings.password = PhosHttpClient.appPassword(PhosSyncSettings.server, PhosSyncSettings.user,
                PhosSyncSettings.password, userAgent)
        }
        val http = PhosHttpClient(PhosSyncSettings.server, PhosSyncSettings.user, PhosSyncSettings.password, userAgent)
        val container = DatabaseContainer.instance
        val bookmarkDb = { container.bookmarkDb.openHelper.writableDatabase }
        val workspaceDb = { container.workspaceDb.openHelper.writableDatabase }
        val readingPlanDb = { container.readingPlanDb.openHelper.writableDatabase }
        val adapters = buildList<PhosAdapter> {
            if (PhosSyncSettings.bookmarks) {
                add(TransactionalAdapter(PhosLabelAdapter(bookmarkDb), bookmarkDb))
                add(TransactionalAdapter(PhosBookmarkAdapter(bookmarkDb), bookmarkDb))
            }
            if (PhosSyncSettings.workspaces) add(TransactionalAdapter(PhosWorkspaceAdapter(workspaceDb), workspaceDb))
            if (PhosSyncSettings.readingPlans) add(TransactionalAdapter(PhosReadingPlanAdapter(readingPlanDb), readingPlanDb))
        }
        val started = System.currentTimeMillis()
        val engine = PhosSyncEngine(http, FileStateStore(stateFile), adapters, deviceName = Build.MODEL ?: "Android",
            appVersion = CommonUtils.applicationVersionName, platform = "andbible", log = { Log.i(TAG, it) })
        val result = engine.run()
        PhosSyncSettings.lastRun = System.currentTimeMillis()
        PhosSyncSettings.lastResult = "pushed ${result.pushed}, pulled ${result.pulled}" +
            (if (result.conflicts > 0) ", conflicts ${result.conflicts}" else "") +
            (if (result.heldDeletes > 0) ", ${result.heldDeletes} deletions held back" else "") +
            (if (result.problems.isNotEmpty()) ", problems: " + result.problems.take(3).joinToString("; ") else "")
        Log.i(TAG, PhosSyncSettings.lastResult)
        if (result.appliedKeys.isNotEmpty()) announce(result.appliedKeys.keys, started - 1)
        return result
    }

    /** Lets the UI reload what the run changed, the same way it does after the file based sync. */
    private fun announce(types: Set<String>, since: Long) {
        val container = DatabaseContainer.instance
        if ("label" in types || "bookmark" in types) {
            ABEventBus.post(BookmarksUpdatedViaSyncEvent(logEntries(container.bookmarkDb.openHelper.writableDatabase, since)))
        }
        if ("workspace" in types) {
            ABEventBus.post(WorkspacesUpdatedViaSyncEvent(logEntries(container.workspaceDb.openHelper.writableDatabase, since)))
        }
        if ("planProgress" in types) {
            ABEventBus.post(ReadingPlansUpdatedViaSyncEvent(logEntries(container.readingPlanDb.openHelper.writableDatabase, since)))
        }
    }

    private fun idOf(c: android.database.Cursor, i: Int): IdType =
        if (c.isNull(i)) IdType.empty() else c.getBlob(i)?.takeIf { it.size == 16 }?.let { IdType.fromByteArray(it) } ?: IdType.empty()

    private fun logEntries(db: SupportSQLiteDatabase, since: Long): List<LogEntry> {
        val out = mutableListOf<LogEntry>()
        db.rows("SELECT tableName, entityId1, entityId2, type, lastUpdated, sourceDevice FROM LogEntry WHERE lastUpdated >= ?",
            arrayOf<Any?>(since)) { c ->
            val type = try { LogEntryTypes.valueOf(c.getString(3)) } catch (e: Exception) { return@rows }
            out += LogEntry(c.getString(0), idOf(c, 1), idOf(c, 2), type, c.getLong(4), c.getString(5).orEmpty())
        }
        return out
    }

    /** Forget the server state (other server or account). Local data stays. */
    fun reset() {
        stateFile.delete()
        PhosSyncSettings.lastRun = 0L
        PhosSyncSettings.lastResult = ""
    }
}

/** Applies a whole batch of records in one database transaction. */
@Suppress("FunctionName")
private fun TransactionalAdapter(inner: PhosAdapter, db: () -> SupportSQLiteDatabase): PhosAdapter =
    if (inner is PhosKeyMapper) MappingTransactionalAdapter(inner, db) else PlainTransactionalAdapter(inner, db)

private class MappingTransactionalAdapter(inner: PhosAdapter, db: () -> SupportSQLiteDatabase) :
    PlainTransactionalAdapter(inner, db), PhosKeyMapper by (inner as PhosKeyMapper)

private open class PlainTransactionalAdapter(private val inner: PhosAdapter, private val db: () -> SupportSQLiteDatabase) : PhosAdapter by inner {
    private var inTx = false

    override fun beginApply() {
        db().beginTransaction()
        inTx = true
        inner.beginApply()
    }

    override fun endApply() {
        try {
            inner.endApply()
            if (inTx) db().setTransactionSuccessful()
        } finally {
            if (inTx) db().endTransaction()
            inTx = false
        }
    }
}
