/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.addons.collections

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.bookmarks.AddonCollectionDao
import net.bible.android.database.bookmarks.AddonCollectionEntities.AddonCollectionRow
import net.bible.sharedcore.log.Log
import net.bible.sharedlogic.platform.CollectionPackRef
import net.bible.sharedlogic.platform.CollectionPackSource
import okio.FileSystem
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.use

private const val TAG = "AddonCollectionReconciler"

/**
 * Brings the bookmark database's add-on collection rows in line with the installed collection modules
 * ([CollectionPackSource]): a new module is imported, a changed one (other `Version`, or the same version with another
 * file content hash) is replaced, an unchanged one is left alone and a module that is gone is removed. Afterwards add-on
 * rows that no module owns are swept (a restored backup, an imported file). Runs are serialised. User rows are never
 * written (see [AddonCollectionImporter]): a module that is missing for a while costs the user nothing.
 *
 * A module whose file cannot be imported (nothing but special labels, a newer schema, a damaged file) is tried once per
 * version and content hash in this process, not on every run; the next app start tries again (an app update may read
 * it). A file whose size and modification time are unchanged is not hashed again.
 *
 * The rows that changed are reported to [onChanged] as synthetic log entries (UPSERT for every row of an imported
 * module, DELETE for rows that are gone), the same shape a sync applies, so open views refresh the same way. These
 * entries are never written to `LogEntry`.
 *
 * [dao] is a provider: the container replaces the bookmark database on restore and sync.
 */
class AddonCollectionReconciler(
    private val source: CollectionPackSource,
    private val importer: AddonCollectionImporter,
    private val dao: () -> AddonCollectionDao,
    private val onChanged: suspend (List<LogEntry>) -> Unit = {},
) {
    private val mutex = Mutex()

    /** What a file looked like when it was hashed: path, size, modification time. */
    private data class FileStamp(val path: String, val size: Long?, val modified: Long?)

    /** Last hash per module, valid while the file's [FileStamp] is unchanged. Guarded by [mutex]. */
    private val hashes = mutableMapOf<String, Pair<FileStamp, String>>()

    /** Version and content hash per module whose import failed in this process. Guarded by [mutex]. */
    private val failedImports = mutableMapOf<String, Pair<String, String>>()

    /** Reconciles once; returns whether any add-on row changed. */
    suspend fun reconcile(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) { reconcileLocked() }
    }

    private suspend fun reconcileLocked(): Boolean {
        val refs = try { source.collections() } catch (e: Exception) {
            Log.e(TAG, "Could not list add-on collections", e); return false
        }.distinctBy { it.moduleInitials }
        val imported = dao().allModules().associateBy { it.moduleInitials }
        val changes = mutableListOf<LogEntry>()
        var changed = false
        // A module with imported content that could not be read or re-imported now: its rows stay, and the sweep
        // waits (it would only remove add-on rows, but there is no need to churn them).
        var keptStale = false

        for (ref in refs) {
            val previous = imported[ref.moduleInitials]
            val hash = try { contentHash(ref) } catch (e: Exception) {
                // Installed but unreadable now (e.g. storage not mounted): keep what was imported, as is.
                Log.e(TAG, "Could not read add-on collection file of ${ref.moduleInitials}", e)
                if (previous != null) keptStale = true
                continue
            }
            if (previous != null && previous.version == ref.version && previous.contentHash == hash) continue
            if (failedImports[ref.moduleInitials] == (ref.version to hash)) {
                if (previous != null) keptStale = true
                continue
            }
            val before = dao().rowsOf(ref.moduleInitials)
            try {
                importer.replace(ref, hash)
                failedImports.remove(ref.moduleInitials)
            } catch (e: Exception) {
                // The previous content (if any) stays: replace is one transaction. Not tried again for this content.
                Log.e(TAG, "Could not import add-on collection ${ref.moduleInitials} ${ref.version}", e)
                failedImports[ref.moduleInitials] = ref.version to hash
                if (previous != null) keptStale = true
                continue
            }
            changed = true
            changes += diff(before, dao().rowsOf(ref.moduleInitials))
        }

        val installed = refs.map { it.moduleInitials }.toSet()
        failedImports.keys.retainAll(installed)
        hashes.keys.retainAll(installed)
        for (gone in imported.keys - installed) {
            val before = dao().rowsOf(gone)
            try {
                importer.remove(gone)
            } catch (e: Exception) {
                Log.e(TAG, "Could not remove add-on collection $gone", e)
                keptStale = true
                continue
            }
            changed = true
            changes += diff(before, emptyList())
        }

        if (!keptStale && importer.needsSweep() && importer.sweep() > 0) changed = true
        if (changed) {
            try { onChanged(changes) } catch (e: Exception) { Log.e(TAG, "Change notification failed", e) }
        }
        return changed
    }

    private fun contentHash(ref: CollectionPackRef): String {
        val metadata = FileSystem.SYSTEM.metadata(ref.file)
        val stamp = FileStamp(ref.file.toString(), metadata.size, metadata.lastModifiedAtMillis)
        hashes[ref.moduleInitials]?.let { (known, hash) -> if (known == stamp && stamp.size != null && stamp.modified != null) return hash }
        val hash = HashingSource.sha256(FileSystem.SYSTEM.source(ref.file)).use { hashing ->
            hashing.buffer().readAll(blackholeSink())
            hashing.hash.hex()
        }
        hashes[ref.moduleInitials] = stamp to hash
        return hash
    }

    private fun diff(before: List<AddonCollectionRow>, after: List<AddonCollectionRow>): List<LogEntry> {
        fun AddonCollectionRow.key() = Triple(tableName, id1, id2)
        val afterKeys = after.map { it.key() }.toSet()
        fun entry(r: AddonCollectionRow, type: LogEntryTypes) =
            LogEntry(r.tableName, r.id1, r.syncId2, type, 0, "addon:${r.moduleInitials}")
        return before.filter { it.key() !in afterKeys }.map { entry(it, LogEntryTypes.DELETE) } +
            after.map { entry(it, LogEntryTypes.UPSERT) }
    }
}
