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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import net.bible.service.installzip.JobId
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import okio.Buffer
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.Timeout
import platform.Foundation.NSInputStream
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.inputStreamWithURL

internal actual fun installPipelineIo(): InstallPipelineIo = IosInstallPipelineIo()

/** The Foundation implementation of [InstallPipelineIo]. */
@kotlin.native.HiddenFromObjC
internal class IosInstallPipelineIo(override val debugArgs: HostDebugArgs = IosDebugLaunchArgs) : InstallPipelineIo {
    override fun tempDir(): Path = NSTemporaryDirectory().toPath()

    override fun newJobId(): JobId = NSUUID().UUIDString

    override fun filePath(uri: String): String = url(uri).path!!

    override fun sleepMs(ms: Long) {
        platform.posix.usleep((ms * 1000).toUInt())
    }

    private fun url(uri: String): NSURL = NSURL.URLWithString(uri)
        ?.takeIf { it.isFileURL() && it.path?.startsWith("/") == true }
        ?: throw IOException("Invalid file URL")

    /** Stream the exact URL, preserving literal backslashes and holding its security scope until close. */
    override fun openSource(uri: String): Source {
        val url = url(uri)
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val stream = NSInputStream.inputStreamWithURL(url) ?: throw IOException("Cannot open file")
            stream.open()
            return object : Source {
                private var closed = false
                override fun read(sink: Buffer, byteCount: Long): Long {
                    check(!closed)
                    require(byteCount >= 0)
                    if (byteCount == 0L) return 0L
                    val bytes = ByteArray(minOf(byteCount, 8192L).toInt())
                    val count = bytes.usePinned { stream.read(it.addressOf(0).reinterpret(), bytes.size.toULong()) }
                    if (count < 0) throw IOException("Cannot read file")
                    if (count == 0L) return -1L
                    sink.write(bytes, 0, count.toInt())
                    return count
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {
                    if (!closed) {
                        closed = true
                        try { stream.close() } finally { if (scoped) url.stopAccessingSecurityScopedResource() }
                    }
                }
            }
        } catch (e: Throwable) {
            if (scoped) url.stopAccessingSecurityScopedResource()
            throw e
        }
    }
}
