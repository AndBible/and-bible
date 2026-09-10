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

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.owncloud.android.lib.common.OwnCloudClient
import org.apache.jackrabbit.webdav.client.methods.DavMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.SocketException

/**
 * #3442: a dropped connection (flight mode, backgrounding, a flaky network) made
 * `client.executeMethod()` throw instead of returning an error status. `RemoteOperation.run()`
 * (in the nextcloud/owncloud library) does not catch exceptions from its subclass's `run()`, so
 * the exception previously escaped the worker thread and crashed the app.
 */
class GenericRemoteOperationTest {
    @Test
    fun `dropped connection during executeMethod is returned as a failed result instead of escaping`() {
        val davMethod = mock<DavMethod>()
        val client = mock<OwnCloudClient>()
        val connectionReset = SocketException("Connection reset")
        whenever(client.executeMethod(davMethod)).thenThrow(connectionReset)

        // execute() is RemoteOperation's own public entry point; it just calls the protected
        // run(client) our class overrides, so this exercises the real production path.
        val result = GenericRemoteOperation(davMethod).execute(client)

        assertFalse(result.isSuccess)
        assertSame(connectionReset, result.exception)
        verify(davMethod).releaseConnection()
    }
}
