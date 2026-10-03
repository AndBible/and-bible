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
package net.bible.sharedcore.navigation

/**
 * The most-recently-used document list behind round 15b's document quick sheet (spec §4.5).
 *
 * Round 15b's ONLY new persisted state. JSword's own `Book.setLastAccess` is not usable here:
 * nothing in this app ever writes it, so it is uniformly zero.
 *
 * Pure list arithmetic; the host owns the storage (one JSON string key in `CommonUtils.settings`,
 * which is a key/value Room store, so this costs no schema migration).
 */
object RecentDocuments {
    /** How many documents the Recent tab remembers. */
    const val MAX = 8

    /** [initials] becomes the most recent entry; duplicates collapse, the oldest falls off the end. */
    fun touched(current: List<String>, initials: String): List<String> {
        if (initials.isBlank()) return current
        return (listOf(initials) + current.filterNot { it == initials }).take(MAX)
    }
}
