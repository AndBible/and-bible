/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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


package net.bible.android.control.page.window

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities.WorkspaceSettings

/**
 * Guards read/modify/publish of the [WorkspaceSettings] collection snapshots (recent labels, study pad
 * cursors, hidden compare documents). This explicitly owned lock avoids the old Native monitor's
 * process-wide document-load contention (#3978). It is per process, only held for tiny critical
 * sections, and must never be held while taking another lock or emitting events.
 */
private val workspaceSettingsLock = reentrantLock()

/**
 * Run [block] (copy, modify, publish a new immutable snapshot) while holding the settings lock.
 * Keep it short and do not notify listeners inside it.
 */
fun <T> WorkspaceSettings.update(block: WorkspaceSettings.() -> T): T =
    workspaceSettingsLock.withLock { block() }

/** Replace the cursor snapshot under the settings lock for UI and bookmark-service writers. */
fun WorkspaceSettings.setStudyPadCursor(labelId: IdType, orderNumber: Int) = update {
    studyPadCursors = studyPadCursors + (labelId to orderNumber)
}

/**
 * Atomically read the cursor of [labelId] and publish the result of [transform]. [transform] returns
 * the new cursor, or null to leave it unchanged. Returns true if a new value was published.
 */
fun WorkspaceSettings.updateStudyPadCursor(labelId: IdType, transform: (Int?) -> Int?): Boolean = update {
    val new = transform(studyPadCursors[labelId]) ?: return@update false
    studyPadCursors = studyPadCursors + (labelId to new)
    true
}
