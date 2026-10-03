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
package net.bible.service.cloudsync.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DocumentSyncOpsTest {
    @Test
    fun buildsPushesThenDownloadsThenRemovalsInOrder() {
        val ops = buildDocumentSyncOps(listOf("KJV", "FinRK"), listOf("ESV"), listOf("NIV"))
        assertEquals(
            listOf(
                DocumentSyncOp.Push("KJV"),
                DocumentSyncOp.Push("FinRK"),
                DocumentSyncOp.Download("ESV"),
                DocumentSyncOp.Remove("NIV"),
            ),
            ops,
        )
    }

    @Test
    fun buildsPurgesLastAfterRemovals() {
        val ops = buildDocumentSyncOps(listOf("KJV"), listOf("ESV"), listOf("NIV"), listOf("GONE"))
        assertEquals(
            listOf(
                DocumentSyncOp.Push("KJV"),
                DocumentSyncOp.Download("ESV"),
                DocumentSyncOp.Remove("NIV"),
                DocumentSyncOp.Purge("GONE"),
            ),
            ops,
        )
    }

    @Test
    fun buildsUninstallsLastAfterPurges() {
        val ops = buildDocumentSyncOps(listOf("KJV"), listOf("ESV"), listOf("NIV"), listOf("GONE"), listOf("OLD"))
        assertEquals(
            listOf(
                DocumentSyncOp.Push("KJV"),
                DocumentSyncOp.Download("ESV"),
                DocumentSyncOp.Remove("NIV"),
                DocumentSyncOp.Purge("GONE"),
                DocumentSyncOp.Uninstall("OLD"),
            ),
            ops,
        )
    }

    @Test
    fun buildsEmptyWhenNoInitials() {
        assertEquals(emptyList<DocumentSyncOp>(), buildDocumentSyncOps(emptyList(), emptyList()))
    }

    @Test
    fun shouldAutoUploadOnlyWhenAllConditionsMet() {
        assertTrue(shouldAutoUpload(enabled = true, autoUpload = true, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = false, autoUpload = true, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, autoUpload = false, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, autoUpload = true, blocked = true, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, autoUpload = true, blocked = false, autoTransferAllowed = false))
    }

    @Test
    fun transientNetworkErrorRecognisesIOExceptions() {
        // The exact failure from ticket 3355: a download timeout from Google Drive.
        assertTrue(isTransientNetworkError(SocketTimeoutException("timeout")))
        assertTrue(isTransientNetworkError(UnknownHostException("no network")))
        assertTrue(isTransientNetworkError(IOException("connection reset")))
    }

    @Test
    fun transientNetworkErrorWalksCauseChain() {
        val wrapped = RuntimeException("download failed", SocketTimeoutException("timeout"))
        assertTrue(isTransientNetworkError(wrapped))
    }

    @Test
    fun transientNetworkErrorIsFalseForAppErrors() {
        assertFalse(isTransientNetworkError(null))
        assertFalse(isTransientNetworkError(IllegalStateException("bad state")))
        assertFalse(isTransientNetworkError(RuntimeException("oops", IllegalArgumentException("nested"))))
    }

    @Test
    fun transientNetworkErrorTerminatesOnCyclicCauseChain() {
        // A cause chain that loops back must not iterate forever.
        val a = RuntimeException("a")
        val b = RuntimeException("b")
        a.initCause(b)
        b.initCause(a)
        assertFalse(isTransientNetworkError(a))
    }
}
