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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import androidx.compose.runtime.Composable
import net.bible.sharedui.app.download.DownloadHostPlatform
import net.bible.sharedui.app.download.DownloadOverflowItem
import net.bible.sharedui.ios.IosPending
import net.bible.sharedcore.log.nsLog
import okio.FileSystem
import okio.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import org.crosswire.jsword.book.Book

/**
 * Copies [from] to [to] like Android's `FileManager.copyFile`: false when the source is missing or the target
 * volume lacks room for it, an existing target is replaced, missing parent directories are created. Never throws.
 */
@kotlin.native.HiddenFromObjC
internal fun copyFileReplacing(from: Path, to: Path, files: FileSystem = FileSystem.SYSTEM): Boolean = try {
    val size = files.metadataOrNull(from)?.takeIf { it.isRegularFile }?.size
    if (size == null) {
        nsLog("copyFile: source missing: $from")
        false
    } else {
        to.parent?.let { files.createDirectories(it) }
        val free = freeSpaceBytes((to.parent ?: to).toString())
        if (free != null && size > free) {
            nsLog("copyFile: not enough room ($size > $free) for $to")
            false
        } else {
            files.delete(to, mustExist = false)
            files.copy(from, to)
            true
        }
    }
} catch (e: Throwable) {
    nsLog("copyFile $from -> $to failed: ${e.message}")
    false
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun freeSpaceBytes(path: String): Long? =
    (NSFileManager.defaultManager.attributesOfFileSystemForPath(path, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue

/** Download on iOS: the overflow icon renders nothing; the rest is pending (stream H). */
@kotlin.native.HiddenFromObjC
class IosDownloadHostPlatform : DownloadHostPlatform {
    override fun copyFile(from: Path, to: Path): Boolean = copyFileReplacing(from, to)

    override suspend fun showAbout(book: Book) {
        IosPending.log("DownloadHostPlatform.showAbout")
    }

    override suspend fun unlock(book: Book): Boolean {
        IosPending.log("DownloadHostPlatform.unlock")
        return false
    }

    override suspend fun confirmUpgradeWithBookmarks(): Boolean {
        IosPending.log("DownloadHostPlatform.confirmUpgradeWithBookmarks")
        return false
    }

    @Composable
    override fun OverflowIcon(item: DownloadOverflowItem) {
        // Nothing to draw: the overflow items carry no platform-specific icon on iOS.
    }
}
