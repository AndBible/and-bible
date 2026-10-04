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
 * Case-insensitive "label contains query" filter backing the searchable picker
 * (`AbSearchablePicker`). Framework-free so it can be unit-tested and shared with iOS.
 *
 * - A blank/whitespace-only [query] returns [options] unchanged (order + identity preserved).
 * - Otherwise keeps options whose [label] contains the trimmed query, matched
 *   case-insensitively. Relative order is preserved.
 */
fun <T> filterPickerOptions(options: List<T>, query: String, label: (T) -> String): List<T> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return options
    val needle = trimmed.lowercase()
    return options.filter { label(it).lowercase().contains(needle) }
}
