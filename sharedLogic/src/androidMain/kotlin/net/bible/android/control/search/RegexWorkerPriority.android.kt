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

import android.os.Process

private class AndroidHandle(val tid: Int, val original: Int)

internal actual fun platformRegexWorkerPriority(): RegexWorkerPriority = object : RegexWorkerPriority {
    override fun begin(): Any? {
        val tid = Process.myTid()
        return AndroidHandle(tid, Process.getThreadPriority(tid))
    }

    override fun lower(handle: Any?) {
        (handle as? AndroidHandle)?.let { runCatching { Process.setThreadPriority(it.tid, Process.THREAD_PRIORITY_LOWEST) } }
    }

    override fun restore(handle: Any?) {
        (handle as? AndroidHandle)?.let { runCatching { Process.setThreadPriority(it.tid, it.original) } }
    }
}
