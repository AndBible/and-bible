/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.installzip

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events

object InstallZipProgress {
    private var source = EventSource<String>()

    /** A one-line, user-visible install progress message (replaces `InstallZipEvent`). Emitted from service and IO threads. */
    val messages: Events<String> get() = source

    fun report(message: String) = source.emit(message)

    @VisibleForTesting
    fun resetSubscribersForTest() { source = EventSource() }
}
