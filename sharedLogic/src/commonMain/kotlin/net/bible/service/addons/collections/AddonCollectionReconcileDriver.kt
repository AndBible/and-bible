/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.addons.collections

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.log.Log
import net.bible.sharedlogic.platform.CollectionPackSource

private const val TAG = "AddonCollectionDriver"

/**
 * Runs [AddonCollectionReconciler.reconcile] when it may be needed: at start, whenever the add-on caches are cleared
 * (a module installed, removed or found at startup, including modules installed by document sync), and after a
 * restore or a sync sign-in replaced the databases. Requests are coalesced; runs wait until the databases are ready
 * and pass the container's write gate, so they never overlap a database replace.
 */
class AddonCollectionReconcileDriver(
    private val reconciler: AddonCollectionReconciler,
    private val source: CollectionPackSource,
    private val scope: CoroutineScope,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val started = atomic(false)

    /** Subscribes to the triggers and requests the first reconciliation. Idempotent. */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        source.reloaded.subscribe { request() }
        DatabaseContainer.databaseRestored.subscribe { request() }
        scope.launch {
            for (unused in requests) {
                try {
                    while (!DatabaseContainer.ready) delay(250)
                    DatabaseContainer.writeGate.pass { reconciler.reconcile() }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Add-on collection reconciliation failed", e)
                }
            }
        }
        request()
    }

    /** Asks for a reconciliation; a request made while one is pending is merged into it. */
    fun request() {
        requests.trySend(Unit)
    }
}
