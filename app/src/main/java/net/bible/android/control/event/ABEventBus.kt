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
package net.bible.android.control.event

import de.greenrobot.event.EventBus
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

object ABEventBus {
    interface Subscriptions {
        fun <T : Any> on(type: KClass<T>, handler: (T) -> Unit)
        fun <T : Any> onMain(type: KClass<T>, handler: (T) -> Unit)
    }

    private class Registration(val type: KClass<*>, val onMain: Boolean, val handler: (Any) -> Unit)

    private val lock = SynchronizedObject()
    // owner identity -> its registrations
    private val registrations = LinkedHashMap<Any, MutableList<Registration>>()
    // Lazy so tests that use only synchronous on{} never touch Dispatchers.Main
    // (accessing it on a plain JVM without an installed main dispatcher throws).
    private val mainScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    private val _events = MutableSharedFlow<Any>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Coroutine-native stream for new (Compose) subscribers that own a CoroutineScope. */
    val events: Flow<Any> = _events.asSharedFlow()

    // ---- greenrobot back-compat surface (transient; removed once all sites migrate) ----
    private val greenrobotSubscribers = ArrayList<Any>()

    fun register(subscriber: Any) {
        EventBus.getDefault().register(subscriber)
        synchronized(lock) { greenrobotSubscribers.add(subscriber) }
    }

    fun safelyRegister(subscriber: Any) {
        val bus = EventBus.getDefault()
        if (!bus.isRegistered(subscriber)) {
            bus.register(subscriber)
            synchronized(lock) { greenrobotSubscribers.add(subscriber) }
        }
    }

    // ---- new KMP-native DSL surface ----
    fun register(owner: Any, block: Subscriptions.() -> Unit) {
        val subs = ArrayList<Registration>()
        val collector = object : Subscriptions {
            override fun <T : Any> on(type: KClass<T>, handler: (T) -> Unit) {
                @Suppress("UNCHECKED_CAST")
                subs.add(Registration(type, onMain = false, handler = handler as (Any) -> Unit))
            }
            override fun <T : Any> onMain(type: KClass<T>, handler: (T) -> Unit) {
                @Suppress("UNCHECKED_CAST")
                subs.add(Registration(type, onMain = true, handler = handler as (Any) -> Unit))
            }
        }
        collector.block()
        synchronized(lock) { registrations[owner] = subs }
    }

    fun safelyRegister(owner: Any, block: Subscriptions.() -> Unit) {
        synchronized(lock) { if (registrations.containsKey(owner)) return }
        register(owner, block)
    }

    fun unregister(subscriber: Any) {
        EventBus.getDefault().unregister(subscriber) // harmless if not a greenrobot subscriber
        synchronized(lock) {
            greenrobotSubscribers.remove(subscriber)
            registrations.remove(subscriber)
        }
    }

    /** Between tests we need to clean up. */
    fun unregisterAll() {
        val greenrobot: List<Any> = synchronized(lock) {
            val snapshot = ArrayList(greenrobotSubscribers)
            greenrobotSubscribers.clear()
            registrations.clear()
            snapshot
        }
        for (s in greenrobot) EventBus.getDefault().unregister(s)
    }

    fun post(event: Any) {
        // 1) greenrobot subscribers (un-migrated onEvent* methods)
        EventBus.getDefault().post(event)
        // 2) new DSL handlers — snapshot under lock, invoke outside the lock (re-entrant-safe)
        val matching = synchronized(lock) {
            registrations.values.flatten().filter { it.type.isInstance(event) }
        }
        for (reg in matching) {
            if (reg.onMain) mainScope.launch { reg.handler(event) } else reg.handler(event)
        }
        // 3) coroutine-native stream
        _events.tryEmit(event)
    }
}

// reified sugar so call sites read `on<SomeEvent> { e -> ... }`
inline fun <reified T : Any> ABEventBus.Subscriptions.on(noinline handler: (T) -> Unit) =
    on(T::class, handler)
inline fun <reified T : Any> ABEventBus.Subscriptions.onMain(noinline handler: (T) -> Unit) =
    onMain(T::class, handler)
