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

package net.bible.sharedui.ios

import platform.Foundation.NSLock
import net.bible.sharedcore.log.nsLog

/**
 * The I2 phase 2 pending convention: a host member that is not implemented on iOS yet calls [log] with
 * `"<Interface>.<member>"` and answers the cancelled / no-op value. Each member is logged once (NSLog tag
 * `I2P2-pending`), so a pending button costs one line, never a crash.
 */
@kotlin.native.HiddenFromObjC
object IosPending {
    private val lock = NSLock()
    private var seen: Set<String> = emptySet()

    /** The members reported so far. */
    val reported: Set<String> get() = locked { seen }

    fun log(member: String) {
        val first = locked {
            if (member in seen) false else { seen = seen + member; true }
        }
        if (first) {
            nsLog("I2P2-pending: $member")
            onFirstReport?.invoke(member)
        }
    }

    /** Test hook: called once per member, on its first report only. */
    internal var onFirstReport: ((String) -> Unit)? = null

    internal fun resetForTest() = locked { seen = emptySet(); onFirstReport = null }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }
}
