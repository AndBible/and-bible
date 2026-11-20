/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.search

/**
 * Lets an abandoned regex worker be demoted so that a runaway match, which cannot be interrupted, competes less with the
 * app. [begin] runs on the worker's own thread and returns a handle that names the thread and its original priority;
 * [lower] and [restore] may then be called from any thread.
 */
internal interface RegexWorkerPriority {
    fun begin(): Any?
    fun lower(handle: Any?)
    fun restore(handle: Any?)
}

/** The platform's implementation: lowers the thread priority on Android, does nothing elsewhere. */
internal expect fun platformRegexWorkerPriority(): RegexWorkerPriority
