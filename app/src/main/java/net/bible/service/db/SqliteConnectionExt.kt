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

package net.bible.service.db

import android.util.Log
import androidx.room.PooledConnection
import androidx.room.TransactionScope
import androidx.room.Transactor
import androidx.room.immediateTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.database.migrations.joinColumnNames

/** Prepares and runs [sql] to completion with [args] bound to its `?` placeholders (1-based, in order). */
fun SQLiteConnection.exec(sql: String, vararg args: Any?) =
    prepare(sql).use { st -> st.bindAll(args); while (st.step()) {} }

/**
 * Binds [args] positionally. Supports null, Long, Int, Boolean, Double, Float, String and ByteArray;
 * anything else is a programming error and throws.
 */
fun SQLiteStatement.bindAll(args: Array<out Any?>) = args.forEachIndexed { i, a ->
    val n = i + 1
    when (a) {
        null -> bindNull(n)
        is Long -> bindLong(n, a)
        is Int -> bindLong(n, a.toLong())
        is Boolean -> bindBoolean(n, a)
        is Double -> bindDouble(n, a)
        is Float -> bindDouble(n, a.toDouble())
        is String -> bindText(n, a)
        is ByteArray -> bindBlob(n, a)
        else -> error("unsupported bind type ${a::class}")
    }
}

/** Runs [sql] and maps every result row with [row]; the statement is only valid inside the lambda. */
fun <T> SQLiteConnection.queryRows(sql: String, vararg args: Any?, row: (SQLiteStatement) -> T): List<T> =
    prepare(sql).use { st -> st.bindAll(args); buildList { while (st.step()) add(row(st)) } }

/**
 * Maps only the first row of [sql] with [row], or returns null when there is no row. Unlike
 * `queryRows(..).firstOrNull()` it stops stepping after the first row.
 */
fun <T> SQLiteConnection.queryFirst(sql: String, vararg args: Any?, row: (SQLiteStatement) -> T): T? =
    prepare(sql).use { st -> st.bindAll(args); if (st.step()) row(st) else null }

/** First column of the first row as a Long, or null when there is no row or the value is SQL NULL. */
fun SQLiteConnection.queryLong(sql: String, vararg args: Any?): Long? =
    queryRows(sql, *args) { if (it.isNull(0)) null else it.getLong(0) }.firstOrNull()

/** Index of the result column called [name]; throws when the statement has no such column. */
fun SQLiteStatement.columnIndex(name: String): Int =
    getColumnNames().indexOf(name).also { require(it >= 0) { "no column $name in ${getColumnNames()}" } }

/** Column [i] as text, or null when it is SQL NULL (`getText` alone would return "" for NULL). */
fun SQLiteStatement.textOrNull(i: Int): String? = if (isNull(i)) null else getText(i)

/**
 * Runs `INSERT OR [conflict] INTO [table] (cols) VALUES (?, ...)` with the values of [values] in the given
 * order, replacing requery's `insert(table, CONFLICT_x, ContentValues)`. [conflict] is one of
 * `IGNORE`, `FAIL`, `ABORT`, `REPLACE`, `ROLLBACK`. Returns the new row id, or -1 when no row was
 * inserted. Like `SQLiteDatabase.insertWithOnConflict`, it logs and returns -1 on any SQLite error
 * (a constraint violation under FAIL/ABORT, say) instead of throwing.
 */
fun SQLiteConnection.insertOr(conflict: String, table: String, vararg values: Pair<String, Any?>): Long {
    require(conflict in setOf("IGNORE", "FAIL", "ABORT", "REPLACE", "ROLLBACK")) { "bad conflict resolution: $conflict" }
    require(values.isNotEmpty()) { "insertOr needs at least one column" }
    val cols = values.joinToString(",") { "`${it.first}`" }
    val marks = values.joinToString(",") { "?" }
    try {
        exec("INSERT OR $conflict INTO $table ($cols) VALUES ($marks)", *values.map { it.second }.toTypedArray())
    } catch (e: SQLiteException) {
        Log.e("SqliteConnectionExt", "Error inserting into $table", e)
        return -1L
    }
    return if ((queryLong("SELECT changes()") ?: 0L) > 0L) queryLong("SELECT last_insert_rowid()") ?: -1L else -1L
}

/**
 * `BEGIN IMMEDIATE` ... `COMMIT` on a raw (non-Room) connection, `ROLLBACK` when [block] throws (the exception
 * is rethrown; a failing ROLLBACK is attached to it as suppressed). The coroutine is marked as in a transaction,
 * so [blockingDb] refuses to run inside [block].
 */
suspend fun <T> SQLiteConnection.inTransaction(block: suspend SQLiteConnection.() -> T): T = withContext(DbTransactionMarker) {
    execSQL("BEGIN IMMEDIATE")
    try {
        block().also { execSQL("COMMIT") }
    } catch (t: Throwable) {
        try { execSQL("ROLLBACK") } catch (r: Throwable) { t.addSuppressed(r) }
        throw t
    }
}

/**
 * Room-connection counterpart of [SQLiteConnection.inTransaction], for a [Transactor] from `useWriterConnection`:
 * an IMMEDIATE transaction through Room (which also handles compatibility mode), rolled back when [block] throws.
 * Suspend DAO calls made inside [block] join this transaction: Room finds the connection in the coroutine context.
 * Marked like the raw one, so [blockingDb] refuses inside. Not to be confused with the member `inTransaction()`,
 * which only asks whether a transaction is open.
 */
suspend fun <T> Transactor.inTransaction(block: suspend TransactionScope<T>.() -> T): T =
    withContext(DbTransactionMarker) { immediateTransaction(block) }

/** Suspend [exec] for a Room connection (`useWriterConnection` / a transaction scope). */
suspend fun PooledConnection.exec(sql: String, vararg args: Any?) =
    usePrepared(sql) { st -> st.bindAll(args); while (st.step()) {} }

/** Suspend [queryRows] for a Room connection; the statement is only valid inside [row]. */
suspend fun <T> PooledConnection.queryRows(sql: String, vararg args: Any?, row: (SQLiteStatement) -> T): List<T> =
    usePrepared(sql) { st -> st.bindAll(args); buildList { while (st.step()) add(row(st)) } }

/** Suspend [queryLong] for a Room connection: first column of the first row, null for no row or SQL NULL. */
suspend fun PooledConnection.queryLong(sql: String, vararg args: Any?): Long? =
    queryRows(sql, *args) { if (it.isNull(0)) null else it.getLong(0) }.firstOrNull()

/** Column names of [tableName] (in [schema] when given, e.g. an attached `patch`), from `PRAGMA table_info`. */
suspend fun PooledConnection.columnNames(tableName: String, schema: String? = null): List<String> {
    val schemaString = schema?.let { "$it." } ?: ""
    return queryRows("PRAGMA ${schemaString}table_info($tableName)") { it.getText(it.columnIndex("name")) }
}

/** [columnNames] as a backquoted, comma-separated list. */
suspend fun PooledConnection.columnNamesJoined(tableName: String, schema: String? = null): String =
    joinColumnNames(columnNames(tableName, schema))

/** Bound for one cleanup statement (see [withCleanup]). */
internal const val CLEANUP_TIMEOUT_MS = 5_000L

/** [withCleanup] on a Room writer connection. */
suspend fun <T> PooledConnection.withCleanup(vararg cleanupSql: String, body: suspend () -> T): T =
    runWithCleanup(cleanupSql.asList(), { exec(it) }, body)

/**
 * Runs [body], then every statement of [cleanupSql] through [execute] (each separately), also when [body] throws
 * or its coroutine is cancelled: the cleanup runs in [NonCancellable], so a cancelled caller cannot skip it
 * (`foreign_keys` left OFF, a schema left ATTACHed). A failing cleanup statement is logged and does not stop the
 * next one; it never replaces an exception from [body] (it is attached as suppressed). When [body] succeeded, the
 * first cleanup failure is thrown after all statements ran. Each statement is bounded by [CLEANUP_TIMEOUT_MS]:
 * a connection that died with the cancellation must not hang the caller.
 */
internal suspend fun <T> runWithCleanup(
    cleanupSql: List<String>,
    execute: suspend (String) -> Unit,
    body: suspend () -> T,
): T {
    var primary: Throwable? = null
    var cleanupFailure: Throwable? = null
    try {
        return body()
    } catch (t: Throwable) {
        primary = t
        throw t
    } finally {
        withContext(NonCancellable) {
            for (sql in cleanupSql) {
                try {
                    withTimeout(CLEANUP_TIMEOUT_MS) { execute(sql) }
                } catch (e: Throwable) {
                    Log.e("SqliteConnectionExt", "Cleanup statement failed: $sql", e)
                    if (primary != null) primary.addSuppressed(e) else if (cleanupFailure == null) cleanupFailure = e
                }
            }
        }
        cleanupFailure?.let { throw it }
    }
}
