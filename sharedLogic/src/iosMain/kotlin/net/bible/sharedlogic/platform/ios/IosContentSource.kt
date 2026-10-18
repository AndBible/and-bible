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
package net.bible.sharedlogic.platform.ios

import net.bible.service.sword.ContentSource
import okio.FileSystem
import okio.ForwardingSource
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import platform.Foundation.NSURL
import platform.UniformTypeIdentifiers.UTType

/**
 * iOS [ContentSource]. [uri] is a `file://` URL (a document-picker result) or a plain path. A picked file outside the
 * sandbox needs its security scope opened while it is read; the scope is released when the returned source closes.
 * [displayName] is the last path component, [mimeType] the preferred MIME type of the extension (null when unknown).
 */
class IosContentSource(private val files: FileSystem = FileSystem.SYSTEM) : ContentSource {
    private fun fileUrl(uri: String): NSURL? =
        if (!uri.startsWith("file:")) null
        // A file URI with unescaped characters (spaces) does not parse as a URL: take what follows "file://" as a raw path.
        else NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))

    private fun pathOf(uri: String): Path? = (if (uri.startsWith("file:")) fileUrl(uri)?.path else uri)?.toPath()

    override fun openSource(uri: String): Source? {
        val path = pathOf(uri) ?: return null
        val url = fileUrl(uri)
        val scoped = url?.startAccessingSecurityScopedResource() == true
        return try {
            object : ForwardingSource(files.source(path)) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        if (scoped) url?.stopAccessingSecurityScopedResource()
                    }
                }
            }.buffer()
        } catch (e: IOException) {
            if (scoped) url?.stopAccessingSecurityScopedResource()
            null
        }
    }

    override fun displayName(uri: String): String? = pathOf(uri)?.name?.takeIf { it.isNotEmpty() }

    override fun mimeType(uri: String): String? {
        val name = displayName(uri) ?: return null
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() } ?: return null
        return UTType.typeWithFilenameExtension(extension)?.preferredMIMEType
    }
}
