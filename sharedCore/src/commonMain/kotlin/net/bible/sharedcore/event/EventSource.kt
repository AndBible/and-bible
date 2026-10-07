/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedcore.event

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** A handle that ends one subscription. Cancelling twice is harmless. */
fun interface Subscription { fun cancel() }

/**
 * The read side of a domain owner's change stream: subscribe only, never emit. It replaces a
 * global `ABEventBus` event. The subscriber names the owner, so the dependency is visible in the
 * code (spec `2026-10-07-abeventbus-removal-program-design.md` §3.1).
 */
interface Events<out T : Any> {
    /** Runs [handler] synchronously on the emitter's thread, before `emit` returns (the old `on { }`). */
    fun subscribe(handler: (T) -> Unit): Subscription

    /** Runs [handler] on `Dispatchers.Main.immediate` (the old `onMain { }`). */
    fun subscribeOnMain(handler: (T) -> Unit): Subscription

    /** For holders and Compose code that own a scope. Never drops an emission, however slow the collector. */
    fun asFlow(): Flow<T>
}

// Lazy, so code that only uses subscribe() never touches Dispatchers.Main (absent on a plain JVM).
private val mainScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

/**
 * A typed, per-owner event stream with `ABEventBus`'s delivery semantics:
 * - Handlers are snapshotted under the lock and invoked outside it, so a re-entrant [emit] and a
 *   `cancel()` during dispatch are safe. A cancel does not affect the snapshot already taken.
 * - A throwing handler is caught and printed; the other handlers still run.
 *
 * The owner keeps it private and exposes it as [Events].
 */
class EventSource<T : Any> : Events<T> {
    private class Handler<T>(val onMain: Boolean, val block: (T) -> Unit)

    private val lock = SynchronizedObject()
    private var handlers: List<Handler<T>> = emptyList() // copy-on-write

    fun emit(event: T) {
        val snapshot = synchronized(lock) { handlers }
        for (handler in snapshot) {
            if (handler.onMain) {
                mainScope.launch { invoke(handler, event) }
            } else {
                invoke(handler, event)
            }
        }
    }

    override fun subscribe(handler: (T) -> Unit): Subscription = add(Handler(onMain = false, block = handler))

    override fun subscribeOnMain(handler: (T) -> Unit): Subscription = add(Handler(onMain = true, block = handler))

    override fun asFlow(): Flow<T> = callbackFlow {
        val subscription = subscribe { trySend(it) }
        awaitClose { subscription.cancel() }
    }.buffer(Channel.UNLIMITED)

    private fun add(handler: Handler<T>): Subscription {
        synchronized(lock) { handlers = handlers + handler }
        return Subscription { synchronized(lock) { handlers = handlers - handler } }
    }

    private fun invoke(handler: Handler<T>, event: T) {
        try { handler.block(event) } catch (e: Throwable) { e.printStackTrace() }
    }
}

/** One handle for a View or service that holds several subscriptions: add on register, cancelAll on teardown. */
class Subscriptions {
    private val lock = SynchronizedObject()
    private val items = ArrayList<Subscription>()

    fun add(subscription: Subscription) {
        synchronized(lock) { items.add(subscription) }
    }

    fun cancelAll() {
        val all = synchronized(lock) { items.toList().also { items.clear() } }
        all.forEach { it.cancel() }
    }
}
