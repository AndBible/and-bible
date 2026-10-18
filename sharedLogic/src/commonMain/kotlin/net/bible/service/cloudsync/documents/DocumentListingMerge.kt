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

package net.bible.service.cloudsync.documents

/** Outcome of merging an incremental cloud listing into the cache. */
data class MergeResult(val cache: List<DocumentSyncMeta>, val watermark: Long)

/**
 * Merges an incremental cloud listing into the existing cache (pure; no Android/network).
 *
 * - Drops cache entries whose folder is no longer present ([currentInitials] = all folders now).
 * - Upserts every successfully-read [changed] meta (keyed by initials), keeping unchanged entries.
 * - Advances the watermark to the max matched createdTime, but **never past the earliest failure**:
 *   when something failed, the watermark stops at `min(failedCreatedTimes) - 1` so the failed meta
 *   (and anything newer) is re-fetched next cycle, while not regressing below [oldWatermark].
 */
fun mergeCloudListing(
    oldCache: List<DocumentSyncMeta>,
    changed: List<DocumentSyncMeta>,
    currentInitials: Set<String>,
    oldWatermark: Long,
    matchedCreatedTimes: List<Long>,
    failedCreatedTimes: List<Long>,
): MergeResult {
    val byInitials = oldCache.associateBy { it.initials }.toMutableMap()
    byInitials.keys.retainAll(currentInitials)          // purge folders that vanished
    for (m in changed) byInitials[m.initials] = m       // upsert changed/new

    val minFailed = failedCreatedTimes.minOrNull()
    val advanced = if (minFailed != null) {
        minFailed - 1                                   // stop just below the earliest failure
    } else {
        matchedCreatedTimes.maxOrNull() ?: oldWatermark
    }
    val newWatermark = maxOf(oldWatermark, advanced)    // never regress

    return MergeResult(byInitials.values.toList(), newWatermark)
}
