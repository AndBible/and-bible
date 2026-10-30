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
package net.bible.sharedcore.platform.desktop.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import net.bible.sharedcore.platform.sync.IosSyncRunner
import net.bible.sharedcore.platform.sync.SyncScheduler

/**
 * The desktop [SyncScheduler]: the hoisted, application-owned [IosSyncRunner] (one coalesced pass at a time) with no
 * OS execution-allowance hooks, because a desktop process needs none. Mirrors the iOS scheduler's surface.
 */
class DesktopSyncScheduler(scope: CoroutineScope, sync: suspend () -> Boolean) : SyncScheduler {
    private val runner = IosSyncRunner(scope, sync, begin = { 0uL }, end = {})

    @Volatile private var last: Deferred<Boolean>? = null

    /** The latest pass started through this scheduler while it is still running, whoever started it; null when idle. */
    val runningPass: Deferred<Boolean>? get() = last?.takeIf { it.isActive }

    override fun startDatabaseSync(): Deferred<Boolean> = runner.startDatabaseSync().also { last = it }

    /** The creator fixes the interaction policy of the pass; a coalescing caller cannot change it. */
    fun startBackgroundDatabaseSync(action: suspend () -> Boolean): Deferred<Boolean> = runner.startDatabaseSync(action).also { last = it }

    fun stop() = runner.stop()

    /** Stops only the pass this caller joined; a completed pass cannot cancel its replacement. */
    fun stop(result: Deferred<Boolean>) = runner.stop(result)
}
