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

package net.bible.service.sword.epub

import net.bible.android.database.IdType
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.sync.withMonitor
import okio.FileSystem
import okio.Path
import org.crosswire.jsword.book.Books

/** Staging trees and set-aside old trees of an EPUB install, next to (not inside) the scanned `epub` folder. */
internal const val EPUB_STAGING_PREFIX = ".epub-install-"
private const val EPUB_ASIDE_PREFIX = ".epub-old-"

/** Staging trees of installs in progress; [sweepStaleEpubStagingDirs] leaves them alone. */
private val activeStagingDirs = mutableSetOf<Path>()

/**
 * A fresh, not yet created staging directory below [modulesRoot] (outside the scanned `epub` folder), protected from
 * [sweepStaleEpubStagingDirs] until [releaseEpubStagingDir].
 */
internal fun newEpubStagingDir(modulesRoot: Path): Path =
    (modulesRoot / "$EPUB_STAGING_PREFIX${IdType()}").also { dir -> withMonitor(activeStagingDirs) { activeStagingDirs.add(dir) } }

/** Deletes the staging tree [dir] (best effort) and drops its protection. */
internal fun releaseEpubStagingDir(dir: Path) {
    runCatching { FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    withMonitor(activeStagingDirs) { activeStagingDirs.remove(dir) }
}

/** Removes staging trees below [modulesRoot] left by a crashed install; those of installs still running are kept. */
internal fun sweepStaleEpubStagingDirs(modulesRoot: Path) {
    val active = withMonitor(activeStagingDirs) { activeStagingDirs.toSet() }
    FileSystem.SYSTEM.listOrNull(modulesRoot)
        ?.filter { it.name.startsWith(EPUB_STAGING_PREFIX) && it !in active }
        ?.forEach { runCatching { FileSystem.SYSTEM.deleteRecursively(it, mustExist = false) } }
}

/**
 * Replaces (or installs) the live EPUB module [dir] by the staged tree [newTree] through the EPUB install lifecycle:
 * drain readers, drop the internal metadata cache and publish a fresh owner, so render caches cannot reuse the old
 * generation. The old module is only set aside (below [modulesRoot], outside the scanned `epub` folder) until the
 * new one is registered; if that fails, the new files are removed, the old module is moved back and registered
 * again, and the failure is rethrown.
 *
 * [newTree] must be on the same file system as [dir] (it is moved, not copied).
 */
internal suspend fun publishStagedEpub(
    dir: Path,
    newTree: Path,
    modulesRoot: Path,
    strings: CoreStrings,
    environment: AppEnvironment,
) {
    val fileSystem = FileSystem.SYSTEM
    installEpubModule(dir) {
        retireOwnedEpubModule(dir)
        var aside: Path? = null
        var replaced = false
        try {
            fileSystem.createDirectories(dir.parent!!)
            if (fileSystem.exists(dir)) {
                val target = modulesRoot / "$EPUB_ASIDE_PREFIX${IdType()}"
                fileSystem.atomicMove(dir, target)
                aside = target
            }
            fileSystem.atomicMove(newTree, dir)
            replaced = true
            addOwnedEpubBook(dir, strings, environment)
            check(Books.installed().getBook(epubInitials(dir.name)) != null) { "EPUB registration failed: ${dir.name}" }
            aside?.let { runCatching { fileSystem.deleteRecursively(it, mustExist = false) } }
            true
        } catch (e: Exception) {
            // Only the new files are removed; a failure before they were moved in leaves the old module in place.
            if (replaced) runCatching { deleteOwnedEpubModule(dir) }
            runCatching {
                aside?.let { fileSystem.atomicMove(it, dir) }
                addOwnedEpubBook(dir, strings, environment) // the old module was retired above; no-op when absent
            }.onFailure { Log.e("EpubReplace", "Could not restore the previous EPUB ${dir.name}", it) }
            throw e
        }
    }
}
