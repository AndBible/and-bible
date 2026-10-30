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

/**
 * Staging trees and set-aside old trees of an EPUB install, next to (not inside) the scanned `epub` folder. An aside
 * tree `<modulesRoot>/.epub-old-<id>/<module folder>` keeps the module's folder name, so a crash leftover can be put
 * back where it belongs.
 */
internal const val EPUB_STAGING_PREFIX = ".epub-install-"
internal const val EPUB_ASIDE_PREFIX = ".epub-old-"

/**
 * Folder names of staging and aside trees of installs in progress; [sweepStaleEpubStagingDirs] leaves them alone.
 * Names, not paths: the archive install roots at the SWORD download dir and the direct install at the modules dir,
 * which may spell the same folder differently.
 */
private val activeStagingDirs = mutableSetOf<String>()

private fun newProtectedDir(modulesRoot: Path, prefix: String): Path =
    (modulesRoot / "$prefix${IdType()}").also { dir -> withMonitor(activeStagingDirs) { activeStagingDirs.add(dir.name) } }

/**
 * A fresh, not yet created staging directory below [modulesRoot] (outside the scanned `epub` folder), protected from
 * [sweepStaleEpubStagingDirs] until [releaseEpubStagingDir].
 */
internal fun newEpubStagingDir(modulesRoot: Path): Path = newProtectedDir(modulesRoot, EPUB_STAGING_PREFIX)

/** Like [newEpubStagingDir], for the old module set aside during a replacement (moved to `<aside>/<module folder>`). */
internal fun newEpubAsideDir(modulesRoot: Path): Path = newProtectedDir(modulesRoot, EPUB_ASIDE_PREFIX)

/** Deletes the staging or aside tree [dir] (best effort) and drops its protection. */
internal fun releaseEpubStagingDir(dir: Path) {
    runCatching { FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    withMonitor(activeStagingDirs) { activeStagingDirs.remove(dir.name) }
}

/**
 * Cleans up after installs that crashed below [modulesRoot]; trees of installs still running are kept. Staging trees
 * are removed. An aside tree holds an old module from a replacement that has not committed: restore it even when
 * failed new files remain in its module folder. Keep the aside if recovery fails, so a later sweep can retry.
 */
internal fun sweepStaleEpubStagingDirs(modulesRoot: Path) {
    val fileSystem = FileSystem.SYSTEM
    val active = withMonitor(activeStagingDirs) { activeStagingDirs.toSet() }
    val stale = fileSystem.listOrNull(modulesRoot)?.filter { it.name !in active } ?: return
    for (aside in stale.filter { it.name.startsWith(EPUB_ASIDE_PREFIX) }) {
        val oldTrees = fileSystem.listOrNull(aside) ?: continue
        var restored = true
        for (old in oldTrees) {
            val dir = modulesRoot / "epub" / old.name
            runCatching {
                fileSystem.createDirectories(dir.parent!!)
                restoreEpubAside(dir, aside, modulesRoot)
                Log.w("EpubReplace", "Restored the EPUB ${old.name} set aside by an interrupted replacement")
            }.onFailure {
                restored = false
                Log.e("EpubReplace", "Could not restore the EPUB ${old.name} from $aside", it)
            }
        }
        if (restored) runCatching { fileSystem.deleteRecursively(aside, mustExist = false) }
    }
    stale.filter { it.name.startsWith(EPUB_STAGING_PREFIX) }
        .forEach { runCatching { fileSystem.deleteRecursively(it, mustExist = false) } }
}

/**
 * Moves the old module set aside in [aside] back to [dir]. Whatever is still in [dir] (new files a failed delete left
 * behind) is first moved into a staging-named tree, which is deleted here or by a later sweep.
 */
internal fun restoreEpubAside(dir: Path, aside: Path, modulesRoot: Path) {
    val fileSystem = FileSystem.SYSTEM
    if (fileSystem.exists(dir)) {
        val trash = modulesRoot / "$EPUB_STAGING_PREFIX${IdType()}"
        fileSystem.atomicMove(dir, trash)
        runCatching { fileSystem.deleteRecursively(trash, mustExist = false) }
    }
    fileSystem.atomicMove(aside / dir.name, dir)
}

/**
 * Replaces (or installs) the live EPUB module [dir] by the staged tree [newTree] through the EPUB install lifecycle:
 * drain readers, drop the internal metadata cache and publish a fresh owner, so render caches cannot reuse the old
 * generation. The old module is only set aside (below [modulesRoot], outside the scanned `epub` folder) until the
 * new one is registered; if that fails, the new files are removed, the old module is moved back and registered
 * again, and the failure is rethrown. A crash in between leaves the aside tree for [sweepStaleEpubStagingDirs].
 *
 * The app database (`epub-<initials>.sqlite3`) is dropped before the swap, as the new module would otherwise reuse
 * it. A restored old module loses nothing by that: its optimized database (`optimized.sqlite3.gz`) and search index
 * live in the module folder, and the app database is unpacked from the former when it is registered again.
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
    val aside = newEpubAsideDir(modulesRoot)
    var setAside = false
    var asideRestoreFailed = false
    try {
        installEpubModule(dir) {
            retireOwnedEpubModule(dir)
            var replaced = false
            try {
                fileSystem.createDirectories(dir.parent!!)
                if (fileSystem.exists(dir)) {
                    fileSystem.createDirectories(aside)
                    fileSystem.atomicMove(dir, aside / dir.name)
                    setAside = true
                }
                fileSystem.atomicMove(newTree, dir)
                replaced = true
                addOwnedEpubBook(dir, strings, environment)
                check(Books.installed().getBook(epubInitials(dir.name)) != null) { "EPUB registration failed: ${dir.name}" }
                if (setAside) {
                    val trash = newEpubStagingDir(modulesRoot)
                    try {
                        // Commit before cleanup: only staging trees may be discarded without recovery at startup.
                        fileSystem.atomicMove(aside, trash)
                    } finally {
                        releaseEpubStagingDir(trash)
                    }
                }
                true
            } catch (e: Exception) {
                // Only the new files are removed; a failure before they were moved in leaves the old module in place.
                if (replaced) runCatching { deleteOwnedEpubModule(dir) }
                    .onFailure { Log.e("EpubReplace", "Could not remove the new EPUB ${dir.name}", it) }
                runCatching {
                    if (setAside) restoreEpubAside(dir, aside, modulesRoot)
                    addOwnedEpubBook(dir, strings, environment) // the old module was retired above; no-op when absent
                }.onFailure {
                    asideRestoreFailed = setAside && fileSystem.exists(aside / dir.name)
                    Log.e("EpubReplace", "Could not restore the previous EPUB ${dir.name}", it)
                }
                throw e
            }
        }
    } finally {
        // A failed restore keeps the old tree (without active-install protection) for the next sweep to retry.
        if (asideRestoreFailed) withMonitor(activeStagingDirs) { activeStagingDirs.remove(aside.name) }
        else releaseEpubStagingDir(aside)
    }
}
