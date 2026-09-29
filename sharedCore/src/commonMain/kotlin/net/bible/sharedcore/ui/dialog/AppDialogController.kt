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

package net.bible.sharedcore.ui.dialog

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app-wide queue of owner-less dialogs (spec §5.1, D4, D6). App-scoped (a Koin single) so a
 * request survives a host swap — `StartupActivity` → `NavHostComposeActivity` — and a service can
 * raise one without knowing any Activity.
 *
 * Invariants, each pinned by `AppDialogControllerTest`:
 * - FIFO among answerable requests; only the head is [pending]; a new request never replaces the
 *   one showing. [AppDialogRequest.Progress] is drawn underneath, via [progress]; it never blocks
 *   answerable requests -- [pending] is the first non-`Progress` entry in the queue.
 * - Every answerable request reaches its caller EXACTLY once: [respond], [cancelAll], or — for
 *   [await] — the caller's own cancellation (which removes the request without answering it).
 *   A dropped request is a hung caller, and that failure is silent.
 * - [respond] for an unknown / already-answered id, or for a [AppDialogRequest.Progress], is a no-op.
 * - Thread-safe: callers post from background threads (`DownloadQueue`, `LinkControl`, TTS).
 *   `onResult` runs on whichever thread calls [respond] — the host's main thread.
 *
 * Unlike `AgentPermissionController` (which answers a second concurrent prompt DENY because only one
 * agent tool call can be outstanding), this one queues: any number of error messages can arrive at
 * once. The two stay separate on purpose.
 */
class AppDialogController {

    private class Entry(val id: Long, val request: AppDialogRequest, val onResult: ((AppDialogResult) -> Unit)?)

    private val lock = SynchronizedObject()
    private val queue = ArrayList<Entry>()
    private var nextId = 1L
    private val mutablePending = MutableStateFlow<ShownDialog?>(null)
    private val mutableProgress = MutableStateFlow<ShownDialog?>(null)

    /** The first answerable (non-[AppDialogRequest.Progress]) queue entry, or null. */
    val pending: StateFlow<ShownDialog?> = mutablePending.asStateFlow()

    /** The most recently shown [AppDialogRequest.Progress] still in the queue, or null. */
    val progress: StateFlow<ShownDialog?> = mutableProgress.asStateFlow()

    /**
     * Fix batch 1 §2.9 (F82/F104): a text request's half-typed value, outliving the composition that
     * showed it. `AppDialogOverlay` releases rendering on `onStop`, which disposes the field's
     * `remember`; a recreate even withdraws the request and raises a new one. Keyed by the request's
     * [AppDialogRequest.TextInput.draftKey] when it has one (so it survives the recreate's NEW
     * request), else by its id. Cleared by an answer and by [cancelAll], never by a withdrawal of a
     * keyed request. Guarded by [lock].
     */
    private val drafts = HashMap<String, String>()

    private fun draftKeyOf(entry: Entry): String? =
        (entry.request as? AppDialogRequest.TextInput)?.let { it.draftKey ?: "id:${entry.id}" }

    /** The half-typed text saved for the queued request [id], or null. */
    fun draft(id: Long): String? = synchronized(lock) {
        queue.firstOrNull { it.id == id }?.let(::draftKeyOf)?.let(drafts::get)
    }

    /** Remembers [text] as the half-typed value of the queued request [id] (no-op if it is not queued). */
    fun saveDraft(id: Long, text: String) {
        synchronized(lock) {
            val key = queue.firstOrNull { it.id == id }?.let(::draftKeyOf) ?: return
            drafts[key] = text
        }
    }

    /** Raises [request] and suspends until it is answered. Cancelling the caller withdraws it. */
    suspend fun await(request: AppDialogRequest): AppDialogResult {
        val answer = CompletableDeferred<AppDialogResult>()
        val id = enqueue(request) { answer.complete(it) }
        try {
            return answer.await()
        } finally {
            remove(id)   // no-op when respond/cancelAll already removed it
        }
    }

    /** Fire-and-forget: raises [request]; [onResult] runs once when it is answered. Returns its id. */
    fun post(request: AppDialogRequest, onResult: (AppDialogResult) -> Unit = {}): Long =
        enqueue(request, onResult)

    /** Shows a modal progress; it stays until [dismiss] with the returned id. */
    fun show(progress: AppDialogRequest.Progress): Long = enqueue(progress, null)

    fun dismiss(id: Long) {
        remove(id)
    }

    fun respond(id: Long, result: AppDialogResult) {
        val entry = synchronized(lock) {
            val e = queue.firstOrNull { it.id == id } ?: return
            if (e.request is AppDialogRequest.Progress) return
            queue.remove(e)
            draftKeyOf(e)?.let(drafts::remove)
            publish()
            e
        }
        entry.onResult?.invoke(result)
    }

    /** Answers every queued request [AppDialogResult.Cancel] and removes every progress. */
    fun cancelAll() {
        val dropped = synchronized(lock) {
            val all = queue.toList()
            queue.clear()
            drafts.clear()
            publish()
            all
        }
        dropped.forEach { it.onResult?.invoke(AppDialogResult.Cancel) }
    }

    private fun enqueue(request: AppDialogRequest, onResult: ((AppDialogResult) -> Unit)?): Long =
        synchronized(lock) {
            val id = nextId++
            queue.add(Entry(id, request, onResult))
            publish()
            id
        }

    private fun remove(id: Long) {
        synchronized(lock) {
            // A withdrawal keeps a keyed draft for the re-raised request; a key-less one is unreachable.
            queue.firstOrNull { it.id == id }?.let { e ->
                if ((e.request as? AppDialogRequest.TextInput)?.draftKey == null) drafts.remove("id:$id")
            }
            if (queue.removeAll { it.id == id }) publish()
        }
    }

    /** Called with [lock] held. Republishes both [pending] (first answerable) and [progress] (last Progress). */
    private fun publish() {
        mutablePending.value = queue.firstOrNull { it.request !is AppDialogRequest.Progress }
            ?.let { ShownDialog(it.id, it.request) }
        mutableProgress.value = queue.lastOrNull { it.request is AppDialogRequest.Progress }
            ?.let { ShownDialog(it.id, it.request) }
    }
}
