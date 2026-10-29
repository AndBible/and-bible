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
package net.bible.sharedui.host.ports

import net.bible.service.installzip.JobId
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.desktop.assembly.DesktopNoDebugArgs
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.IOException
import okio.source
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

internal actual fun installPipelineIo(): InstallPipelineIo = JvmInstallPipelineIo()

/**
 * The `java.nio` implementation of [InstallPipelineIo] (the Foundation one's counterpart). [tempDir] is injectable so
 * tests keep their files in a temporary directory.
 */
internal class JvmInstallPipelineIo(
    override val debugArgs: HostDebugArgs = DesktopNoDebugArgs,
    private val temp: () -> java.nio.file.Path = { Paths.get(System.getProperty("java.io.tmpdir")) },
) : InstallPipelineIo {
    override fun tempDir(): Path = temp().toOkioPath()

    override fun newJobId(): JobId = UUID.randomUUID().toString()

    override fun filePath(uri: String): String = path(uri).toString()

    override fun openSource(uri: String): Source {
        val path = path(uri)
        try {
            return Files.newInputStream(path).source()
        } catch (e: java.io.IOException) {
            throw IOException("Cannot open file", e)
        }
    }

    override fun sleepMs(ms: Long) {
        Thread.sleep(ms)
    }

    /**
     * Only an absolute `file:` URL names a file; anything else (a relative path, another scheme) is refused. The path is
     * percent-decoded by hand, so a literal backslash or other character that `java.net.URI` rejects stays as it is.
     */
    private fun path(uri: String): java.nio.file.Path {
        val rest = listOf("file:///", "file://localhost/").firstOrNull { uri.startsWith(it, ignoreCase = true) }
            ?.let { uri.substring(it.length - 1) }
            ?: throw IOException("Invalid file URL")
        val decoded = percentDecode(rest.substringBefore('#').substringBefore('?')) ?: throw IOException("Invalid file URL")
        // A Windows drive path arrives as /C:/dir/file.
        val native = if (java.io.File.separatorChar == '\\' && Regex("^/[A-Za-z]:[/\\\\]").containsMatchIn(decoded)) decoded.substring(1) else decoded
        return try { Paths.get(native) } catch (e: java.nio.file.InvalidPathException) { throw IOException("Invalid file URL", e) }
    }

    private fun percentDecode(text: String): String? {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%') {
                val value = text.substring(i + 1, minOf(i + 3, text.length)).takeIf { it.length == 2 }?.toIntOrNull(16) ?: return null
                out.write(value)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toString(Charsets.UTF_8)
    }
}
