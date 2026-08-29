/*
 * Copyright (c) 2025-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.utils.WebDavFileUtils
import com.owncloud.android.lib.resources.files.model.RemoteFile
import org.apache.commons.httpclient.HttpStatus
import org.apache.jackrabbit.webdav.client.methods.DavMethod

class GenericRemoteOperation(
    val davMethod: DavMethod
) : RemoteOperation<List<RemoteFile>>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<List<RemoteFile>> {
        var result: RemoteOperationResult<List<RemoteFile>>
        try {
            val status = client.executeMethod(davMethod)

            val isSuccess = (status == HttpStatus.SC_MULTI_STATUS || status == HttpStatus.SC_OK)

            if (isSuccess) {
                val dataInServer = davMethod.responseBodyAsMultiStatus
                val webDavFileUtils = WebDavFileUtils()
                val mFolderAndFiles = webDavFileUtils.readData(
                    dataInServer,
                    client,
                    false,
                    true
                )

                result = RemoteOperationResult<List<RemoteFile>>(
                    true,
                    status,
                    davMethod.responseHeaders
                )
                if (result.isSuccess) {
                    result.resultData = mFolderAndFiles
                }
            } else {
                client.exhaustResponse(davMethod.responseBodyAsStream)
                result = RemoteOperationResult<List<RemoteFile>>(
                    false,
                    status,
                    davMethod.responseHeaders
                )
            }
        } catch (e: Exception) {
            // client.executeMethod() throws on a dropped connection (flight mode, backgrounding,
            // flaky network) rather than reporting it via the return value. RemoteOperation.run()
            // (in the nextcloud/owncloud library) only catches IOException while creating the
            // client, not while running this subclass, so an uncaught exception here escaped the
            // worker thread and crashed the app (#3442). Library ops such as
            // ReadFolderRemoteOperation catch it themselves and return RemoteOperationResult(e);
            // mirror that so the failure reaches NextCloudAdapter.execute() as a normal result and
            // is treated as a transient, retryable network error.
            result = RemoteOperationResult<List<RemoteFile>>(e)
        } finally {
            davMethod.releaseConnection()
        }

        return result
    }
}
