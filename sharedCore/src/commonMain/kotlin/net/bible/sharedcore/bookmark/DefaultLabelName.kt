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

package net.bible.sharedcore.bookmark

/**
 * The name a brand-new label is born with (round 17b): [format] filled with the smallest `n >= 1`
 * whose result is not already in [existing].
 *
 * [format] contains one `%d` and comes from the host as a translated string, so nothing here may
 * assume English or that the number comes last. `String.replace` rather than `String.format`,
 * which does not exist in `commonMain`.
 *
 * Collisions are compared trimmed and lower-cased, because a user reads "label 1" and "Label 1" as
 * the same name. This is a courtesy for the GENERATED default only — nothing stops a user from
 * typing a duplicate afterwards, exactly as before, and uniqueness is not an invariant of the
 * label table.
 */
fun defaultLabelName(existing: Set<String>, format: String): String {
    val taken = existing.mapTo(mutableSetOf()) { it.trim().lowercase() }
    var n = 1
    while (true) {
        val candidate = format.replace("%d", n.toString())
        if (candidate.trim().lowercase() !in taken) return candidate
        n++
    }
}
