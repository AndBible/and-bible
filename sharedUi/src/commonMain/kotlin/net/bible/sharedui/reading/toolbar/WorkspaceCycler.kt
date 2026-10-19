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

package net.bible.sharedui.reading.toolbar

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android's `cycleWorkspace` made safe against a double fling: the current position is read and the switch finishes
 * inside one mutex, so a second cycle starts from the workspace the first one landed on instead of the one it left.
 */
@kotlin.native.HiddenFromObjC
class WorkspaceCycler(
    private val workspaceIds: suspend () -> List<String>,
    private val currentId: () -> String,
    private val switchTo: suspend (String) -> Unit,
) {
    private val mutex = Mutex()

    /** Switches to the next or previous workspace in list order, wrapping; nothing with fewer than two. */
    suspend fun cycle(forward: Boolean) = mutex.withLock {
        val ids = workspaceIds()
        if (ids.size < 2) return@withLock
        val current = ids.indexOf(currentId())
        val next = if (forward) (if (current < ids.size - 1) current + 1 else 0)
        else (if (current > 0) current - 1 else ids.size - 1)
        switchTo(ids[next])
    }
}
