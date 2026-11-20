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
import net.bible.android.database.IdType
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
 * rows that no module owns are swept (a restored backup, an imported file). Runs are serialised.
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

        for (ref in refs) {
            val hash = try { contentHash(ref) } catch (e: Exception) {
                Log.e(TAG, "Could not read add-on collection file of ${ref.moduleInitials}", e); continue
            }
            val previous = imported[ref.moduleInitials]
            if (previous != null && previous.version == ref.version && previous.contentHash == hash) continue
            val before = dao().rowsOf(ref.moduleInitials)
            try {
                importer.replace(ref, hash)
            } catch (e: Exception) {
                // The previous content (if any) stays: replace is one transaction.
                Log.e(TAG, "Could not import add-on collection ${ref.moduleInitials} ${ref.version}", e)
                continue
            }
            changed = true
            changes += diff(before, dao().rowsOf(ref.moduleInitials))
        }

        val installed = refs.map { it.moduleInitials }.toSet()
        for (gone in imported.keys - installed) {
            val before = dao().rowsOf(gone)
            try {
                importer.remove(gone)
            } catch (e: Exception) {
                Log.e(TAG, "Could not remove add-on collection $gone", e)
                continue
            }
            changed = true
            changes += diff(before, emptyList())
        }

        if (!changed) {
            // Only when nothing ran: every import and removal sweeps too.
            if (importer.needsSweep() && importer.sweep() > 0) changed = true
        }
        if (changed) {
            try { onChanged(changes) } catch (e: Exception) { Log.e(TAG, "Change notification failed", e) }
        }
        return changed
    }

    private fun contentHash(ref: CollectionPackRef): String =
        HashingSource.sha256(FileSystem.SYSTEM.source(ref.file)).use { hashing ->
            hashing.buffer().readAll(blackholeSink())
            hashing.hash.hex()
        }

    private fun diff(before: List<AddonCollectionRow>, after: List<AddonCollectionRow>): List<LogEntry> {
        fun AddonCollectionRow.key() = Triple(tableName, id1, id2)
        val afterKeys = after.map { it.key() }.toSet()
        fun entry(r: AddonCollectionRow, type: LogEntryTypes) =
            LogEntry(r.tableName, r.id1, r.id2 ?: IdType.empty(), type, 0, "addon:${r.moduleInitials}")
        return before.filter { it.key() !in afterKeys }.map { entry(it, LogEntryTypes.DELETE) } +
            after.map { entry(it, LogEntryTypes.UPSERT) }
    }
}
