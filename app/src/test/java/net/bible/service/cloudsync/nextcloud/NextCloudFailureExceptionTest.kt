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
package net.bible.service.cloudsync.nextcloud

import net.bible.service.cloudsync.documents.isTransientNetworkError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

/**
 * A failed NextCloud operation carries no exception of its own when the server merely answered with
 * an error status, so the adapter builds one. Which exception it picks decides whether the sync
 * layer retries quietly or alarms the user, so the mapping is pinned down here.
 */
class NextCloudFailureExceptionTest {
    /**
     * OSTicket 3376: a NextCloud server returning HTTP 503 produced a repeated user-facing "An
     * error has occurred". A server that is temporarily unavailable is a connectivity problem, so
     * the exception must be one [isTransientNetworkError] recognises — then the op is simply retried
     * on the next sync.
     */
    @Test
    fun serverErrorIsTransientAndNotReportedToUser() {
        val e = nextCloudFailureException(isNotFound = false, httpCode = 503, description = "UNKNOWN_ERROR")
        assertTrue("HTTP 503 must be treated as a transient network failure", isTransientNetworkError(e))
    }

    /**
     * A genuinely missing resource must stay distinguishable: `isSyncFolderKnown` catches
     * FileNotFoundException to forget a stale sync-folder marker. Were a 503 to arrive as
     * FileNotFoundException too, an unreachable server would silently look like deleted data.
     */
    @Test
    fun missingResourceIsFileNotFoundButServerErrorIsNot() {
        val notFound = nextCloudFailureException(isNotFound = true, httpCode = 404, description = "FILE_NOT_FOUND")
        assertTrue(notFound is FileNotFoundException)

        val serverError = nextCloudFailureException(isNotFound = false, httpCode = 503, description = "UNKNOWN_ERROR")
        assertFalse(
            "a server error must not masquerade as a missing file",
            serverError is FileNotFoundException,
        )
    }

    /** A FileNotFoundException is still an IOException, so a 404 also survives a retry-or-report split. */
    @Test
    fun missingResourceIsAlsoAnIOException() {
        val notFound = nextCloudFailureException(isNotFound = true, httpCode = 404, description = "FILE_NOT_FOUND")
        assertTrue(isTransientNetworkError(notFound))
    }

    /**
     * The caller derives `isNotFound` from either the raw HTTP status or the library's ResultCode,
     * so a 404 must map to FileNotFoundException even when only one of the two says "not found" —
     * how the library classifies a WebDAV 404 into a ResultCode is not something we can rely on.
     */
    @Test
    fun notFoundHoldsWhicheverSignalReportsIt() {
        // Reached via httpCode == 404 alone (ResultCode said something else).
        assertTrue(
            nextCloudFailureException(isNotFound = true, httpCode = 404, description = "UNKNOWN_ERROR")
                is FileNotFoundException
        )
        // Reached via ResultCode alone (no usable HTTP status, e.g. 0).
        assertTrue(
            nextCloudFailureException(isNotFound = true, httpCode = 0, description = "FILE_NOT_FOUND")
                is FileNotFoundException
        )
    }

    @Test
    fun messageCarriesStatusForDiagnosis() {
        val e = nextCloudFailureException(isNotFound = false, httpCode = 502, description = "BAD_GATEWAY")
        assertTrue(e.message!!.contains("502"))
        assertTrue(e.message!!.contains("BAD_GATEWAY"))
    }
}
