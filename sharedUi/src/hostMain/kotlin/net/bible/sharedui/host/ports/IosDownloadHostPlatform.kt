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

package net.bible.sharedui.host.ports

import androidx.compose.runtime.Composable
import net.bible.sharedui.app.download.DownloadHostPlatform
import net.bible.sharedui.app.download.DownloadOverflowItem
import androidx.compose.material.icons.filled.Dns
import kotlin.coroutines.cancellation.CancellationException
import net.bible.sharedcore.log.nsLog
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.crosswire.jsword.book.Book

/**
 * Copies [from] to [to] like Android's `FileManager.copyFile`: false when the source is missing or the target
 * volume lacks room for it, an existing target is replaced atomically (copy to `<name>.tmp`, then rename), missing parent directories are created. Never throws.
 */
@kotlin.native.HiddenFromObjC
internal fun copyFileReplacing(
    from: Path,
    to: Path,
    files: FileSystem = FileSystem.SYSTEM,
    freeBytes: (String) -> Long?,
): Boolean = try {
    val size = files.metadataOrNull(from)?.takeIf { it.isRegularFile }?.size
    if (size == null) {
        nsLog("copyFile: source missing: $from")
        false
    } else {
        to.parent?.let { files.createDirectories(it) }
        val free = freeBytes((to.parent ?: to).toString())
        if (free != null && size > free) {
            nsLog("copyFile: not enough room ($size > $free) for $to")
            false
        } else {
            // Copy beside the target, then replace it in one atomic rename: a failure part-way leaves the existing
            // file (an upgraded module) untouched.
            val partial = to.parent?.let { it / (to.name + ".tmp") } ?: (to.name + ".tmp").toPath()
            try {
                files.delete(partial, mustExist = false)
                files.copy(from, partial)
                files.atomicMove(partial, to)
                true
            } catch (e: Throwable) {
                runCatching { files.delete(partial, mustExist = false) }
                throw e
            }
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    nsLog("copyFile $from -> $to failed: ${e.message}")
    false
}

/** Device file operations and shared document dialogs for iOS downloads. */
@kotlin.native.HiddenFromObjC
class IosDownloadHostPlatform(
    private val documents: IosDocumentDialogs,
    private val freeBytes: (String) -> Long?,
) : DownloadHostPlatform {
    override fun copyFile(from: Path, to: Path): Boolean = copyFileReplacing(from, to, freeBytes = freeBytes)

    override suspend fun showAbout(book: Book) = documents.showAboutBook(book)
    override suspend fun unlock(book: Book): Boolean = documents.unlockBook(book)
    override suspend fun confirmUpgradeWithBookmarks(): Boolean = documents.confirmUpgrade()

    @Composable
    override fun OverflowIcon(item: DownloadOverflowItem) {
        if (item == DownloadOverflowItem.CUSTOM_REPOSITORIES) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Dns, contentDescription = null)
            return
        }
        val key = when (item) {
            DownloadOverflowItem.ERRORS -> "ic_error_outline_black_24dp"
            DownloadOverflowItem.INSTALL_ZIP -> "ic_unarchive_white_24dp"
            DownloadOverflowItem.CUSTOM_REPOSITORIES -> "ic_baseline_cloud_download_24"
            DownloadOverflowItem.CLOUD_DOCUMENTS -> "ic_syncdb_24dp"
        }
        androidx.compose.material3.Icon(org.jetbrains.compose.resources.painterResource(net.bible.sharedui.icons.AbIcons.all.getValue(key)), contentDescription = null)
    }
}
