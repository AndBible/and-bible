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
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.db

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val inTransactionOnThread = ThreadLocal<Boolean?>()

/**
 * Marks the threads a transaction body runs on, so [blockingDb] can refuse to run there.
 * Being a [ThreadContextElement] it is re-applied on every resumption, so the mark follows the
 * body across `withContext` thread hops.
 */
object DbTransactionMarker : AbstractCoroutineContextElement(Key), ThreadContextElement<Boolean?> {
    object Key : CoroutineContext.Key<DbTransactionMarker>
    override fun updateThreadContext(context: CoroutineContext): Boolean? =
        inTransactionOnThread.get().also { inTransactionOnThread.set(true) }
    override fun restoreThreadContext(context: CoroutineContext, oldState: Boolean?) = inTransactionOnThread.set(oldState)
}

class BlockingDbInTransaction : IllegalStateException(
    "blockingDb called inside a database transaction: a nested runBlocking cannot use the transaction's " +
        "connection and would wait for it forever. Make the caller suspend instead.")

/**
 * The one sanctioned bridge from blocking code to suspend DAO functions while callers migrate to
 * coroutines (D1 spec §3 D1.2; removed in L1). Throws [BlockingDbInTransaction] inside a marked transaction.
 */
fun <T> blockingDb(block: suspend CoroutineScope.() -> T): T {
    if (inTransactionOnThread.get() == true) throw BlockingDbInTransaction()
    return runBlocking(block = block)
}

/** Every Room transaction in app code goes through this (Room 2.8 `withTransaction`; Task 17 renames the inner call). */
suspend fun <R> RoomDatabase.roomTransaction(block: suspend () -> R): R =
    withContext(DbTransactionMarker) { withTransaction(block) }
