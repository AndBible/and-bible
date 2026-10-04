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

package net.bible.sharedui.nav

/**
 * One keyed value held for the HOST's lifetime rather than a back-stack entry's composition —
 * the shape `NavHostComposeActivity.manageLabelsSession` (`Pair<key, session>`, looked up by key
 * and set to null on every exit) writes by hand, extracted so it can be tested and so the graphs
 * that need it do not each grow their own copy of the same three lines.
 *
 * **Why a host-lifetime memo is needed at all.** navigation-compose disposes a destination's
 * composition while a child destination is on top of it. Anything an arm holds in a plain
 * `remember` therefore dies the moment that arm pushes a child, and is rebuilt from the route
 * arguments on the way back — which silently discards every unsaved edit the destination was
 * holding. A classic Activity was merely PAUSED behind its child and never had that problem, so
 * this is a hazard the migration introduces rather than one it inherits, and it is invisible until
 * a destination actually acquires a child.
 *
 * **Why it is keyed.** A route with different arguments is a different piece of work — a detached
 * edit of workspace A is not an edit of workspace B — so a memo that ignored its key would hand
 * the second entry the first one's working set. [getOrPut] therefore rebuilds whenever the key
 * differs, exactly as `manageLabelsSession` compares its `data` payload before reusing a session.
 *
 * **Why exactly ONE entry.** The lifetime being managed is "the visit the user is in the middle
 * of", not a cache: the owner is expected to [drop] the memo on every exit, so at most one entry
 * can be live. Holding more would keep a finished visit's working set alive until the host died,
 * and a re-entry would silently RESUME it instead of starting fresh.
 *
 * **This is not snapshot state and must not be read from a composition as if it were.** It is a
 * plain field, read inside a `remember` and written from an exit callback; nothing recomposes when
 * it changes, which is the point (the value it holds is what recomposes).
 *
 * Not thread-safe, and deliberately so: every caller is on the main thread, inside composition or
 * an exit callback.
 */
class NavSessionMemo<K : Any, V : Any> {
    private var entry: Pair<K, V>? = null

    /** What is memoised right now, if anything — for callers that must act on an unfinished visit. */
    val current: V? get() = entry?.second

    /**
     * The memoised value for [key], building it with [factory] when nothing is memoised for that
     * key yet. The new value is stored only AFTER [factory] returns, so a factory that throws
     * leaves no half-built value memoised for the host's whole lifetime.
     */
    fun getOrPut(key: K, factory: () -> V): V {
        entry?.let { (memoisedKey, value) -> if (memoisedKey == key) return value }
        val value = factory()
        entry = key to value
        return value
    }

    /** Forget the memoised value, so the next [getOrPut] starts a fresh visit. */
    fun drop() {
        entry = null
    }
}
