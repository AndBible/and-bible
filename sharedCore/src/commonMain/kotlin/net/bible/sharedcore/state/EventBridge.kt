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
package net.bible.sharedcore.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.bible.android.control.event.ABEventBus

/** The [ABEventBus] stream narrowed to events of type [T]. */
inline fun <reified T : Any> eventsOf(): Flow<T> = ABEventBus.events.filterIsInstance<T>()

/**
 * Folds bus events of type [T] into a [StateFlow], starting from [initial]. The collector runs for
 * the lifetime of [scope]. This is the canonical "subscribe the event bus into UI state" bridge for
 * shared state holders (legacy code keeps the owner-DSL `register()`).
 *
 * Because [ABEventBus]'s SharedFlow has `replay=0`, only events posted after [scope] launches the
 * collector are folded in; the returned StateFlow always starts at [initial].
 */
inline fun <reified T : Any, S> stateFromEvents(
    scope: CoroutineScope,
    initial: S,
    crossinline fold: (S, T) -> S,
): StateFlow<S> {
    val flow = MutableStateFlow(initial)
    scope.launch { eventsOf<T>().collect { ev -> flow.value = fold(flow.value, ev) } }
    return flow.asStateFlow()
}
