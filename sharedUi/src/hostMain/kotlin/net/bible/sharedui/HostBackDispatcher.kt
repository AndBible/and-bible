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
package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import net.bible.sharedcore.log.nsLog

/**
 * The shared iOS/desktop scene back route. Every scene with composed back handlers registers its
 * NavigationEventDispatcher here (see [Attach]); [back] completes a back event in the most recently attached scene that
 * has an enabled handler, and [passThrough] hands an event a pass-through handler declined to the handlers below it.
 *
 * Threading: UI thread only (the main thread on iOS, the AWT event thread on desktop). [back], [passThrough] and the
 * composition callbacks must run there: the `unhandled` flag is not synchronized, and the dispatcher's handlers run
 * synchronously on the calling thread. The lock guards only the attachment list.
 */
internal object HostBackDispatcher {
    private class Tracking : NavigationEventInput() {
        var hasEnabled = false
            private set
        override fun onHasEnabledHandlersChanged(enabled: Boolean) { hasEnabled = enabled }
        fun complete() = dispatchOnBackCompleted()
    }

    private class Attachment(val dispatcher: NavigationEventDispatcher, val input: Tracking, var refs: Int)
    private val lock = SynchronizedObject()
    private val attachments = mutableListOf<Attachment>()
    private var unhandled = false

    private fun attach(dispatcher: NavigationEventDispatcher) = synchronized(lock) {
        val existing = attachments.firstOrNull { it.dispatcher === dispatcher }
        if (existing != null) existing.refs++ else {
            val input = Tracking()
            dispatcher.addInput(input)
            attachments += Attachment(dispatcher, input, 1)
        }
    }

    private fun detach(dispatcher: NavigationEventDispatcher) = synchronized(lock) {
        val attachment = attachments.firstOrNull { it.dispatcher === dispatcher } ?: return@synchronized
        if (--attachment.refs <= 0) {
            attachments.remove(attachment)
            // The scene may have disposed its dispatcher before its composition is torn down.
            runCatching { dispatcher.removeInput(attachment.input) }
                .onFailure { nsLog("host back dispatcher: removeInput failed (scene already disposed?): $it") }
        }
    }

    @Composable
    fun Attach() {
        val dispatcher = currentDispatcher()
        DisposableEffect(dispatcher) {
            if (dispatcher != null) attach(dispatcher)
            onDispose { if (dispatcher != null) detach(dispatcher) }
        }
    }

    @OptIn(InternalComposeUiApi::class)
    @Composable
    fun currentDispatcher(): NavigationEventDispatcher? =
        LocalCompatNavigationEventDispatcherOwner.current?.navigationEventDispatcher

    private fun dispatchTopmost(): Boolean {
        val top = synchronized(lock) { attachments.lastOrNull { it.input.hasEnabled } } ?: return false
        top.input.complete()
        return true
    }

    fun back(): Boolean {
        unhandled = false
        return dispatchTopmost() && !unhandled
    }

    /** The caller disables itself synchronously before forwarding to the next enabled handler. */
    fun passThrough() {
        if (!dispatchTopmost()) unhandled = true
    }
}
