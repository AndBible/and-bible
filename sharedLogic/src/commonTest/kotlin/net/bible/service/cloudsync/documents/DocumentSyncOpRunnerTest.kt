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
package net.bible.service.cloudsync.documents

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.CloudSyncUserFacingException
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentSyncOpRunnerTest {
    private val calls = mutableListOf<String>()
    private var failure: Throwable? = null

    private val actions = object : DocumentSyncActions {
        private fun record(name: String, initials: String) {
            calls += "$name:$initials"
            failure?.let { throw it }
        }
        override suspend fun push(initials: String) = record("push", initials)
        override suspend fun download(initials: String, onProgress: (Long, Long) -> Unit) {
            onProgress(5L, 10L)
            record("download", initials)
        }
        override suspend fun remove(initials: String) = record("remove", initials)
        override suspend fun purge(initials: String) = record("purge", initials)
        override suspend fun uninstall(initials: String) = record("uninstall", initials)
    }

    @Test
    fun eachOpCallsMatchingActionAndIsDone() = runTest {
        val progress = mutableListOf<Pair<Long, Long>>()
        val ops = listOf(
            DocumentSyncOp.Push("A"), DocumentSyncOp.Download("B"), DocumentSyncOp.Remove("C"),
            DocumentSyncOp.Purge("D"), DocumentSyncOp.Uninstall("E"),
        )
        for (op in ops) {
            assertEquals(DocumentSyncOpOutcome.Done, runDocumentSyncOp(op, actions) { d, t -> progress += d to t })
        }
        assertEquals(listOf("push:A", "download:B", "remove:C", "purge:D", "uninstall:E"), calls)
        assertEquals(listOf(5L to 10L), progress)
    }

    @Test
    fun ioExceptionIsRetryLater() = runTest {
        failure = IOException("timeout")
        assertEquals(DocumentSyncOpOutcome.RetryLater, runDocumentSyncOp(DocumentSyncOp.Push("A"), actions) { _, _ -> })
    }

    @Test
    fun wrappedIoExceptionIsRetryLater() = runTest {
        failure = IllegalStateException("wrapper", IOException("dropped"))
        assertEquals(DocumentSyncOpOutcome.RetryLater, runDocumentSyncOp(DocumentSyncOp.Remove("A"), actions) { _, _ -> })
    }

    @Test
    fun userFacingExceptionKeepsMessageAndReconnectFlag() = runTest {
        failure = CloudSyncUserFacingException("full", false)
        assertEquals(
            DocumentSyncOpOutcome.UserFacing("full", false),
            runDocumentSyncOp(DocumentSyncOp.Download("A"), actions) { _, _ -> },
        )
        failure = CloudSyncUserFacingException("bad password", true)
        assertEquals(
            DocumentSyncOpOutcome.UserFacing("bad password", true),
            runDocumentSyncOp(DocumentSyncOp.Download("A"), actions) { _, _ -> },
        )
    }

    @Test
    fun otherExceptionIsFailed() = runTest {
        failure = IllegalArgumentException("bug")
        assertEquals(DocumentSyncOpOutcome.Failed, runDocumentSyncOp(DocumentSyncOp.Purge("A"), actions) { _, _ -> })
    }

    @Test
    fun cancellationPropagates() = runTest {
        failure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { runDocumentSyncOp(DocumentSyncOp.Uninstall("A"), actions) { _, _ -> } }
    }
}
