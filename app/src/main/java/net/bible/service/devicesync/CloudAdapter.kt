/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.devicesync

import com.google.api.client.util.DateTime
import net.bible.android.view.activity.base.ActivityBase
import java.io.File
import java.io.OutputStream

interface CloudAdapter {
    val signedIn: Boolean
    suspend fun signIn(activity: ActivityBase): Boolean
    suspend fun signOut()
    fun get(id: String): SyncFile
    fun listFiles(
        parentsIds: List<String>? = null,
        name: String? = null,
        mimeType: String? = null,
        createdTimeAtLeast: DateTime? = null
    ): List<SyncFile>
    fun getFolders(parentId: String): List<SyncFile>
    fun download(id: String, outputStream: OutputStream)
    fun createNewFolder(name: String, parentId: String? = null): SyncFile
    fun upload(name: String, file: File, parentId: String? = null): SyncFile
    fun delete(id: String)
}
