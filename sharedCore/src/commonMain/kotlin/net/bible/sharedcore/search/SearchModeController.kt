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

package net.bible.sharedcore.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a screen's top bar is currently showing its inline search field, plus the one policy
 * decision that goes with it: does leaving search mode clear the query.
 *
 * Deliberately owns the MODE ONLY, never the query. The screens that use this each store their
 * query in their own controller because their setQuery pipelines differ — one only stores (the host
 * runs FTS), one triggers a Room-backed reload, one refilters in memory and drops the selection —
 * so a shared query holder would have to reproduce three different pipelines to gain nothing.
 *
 * [close] is unconditional: it clears per policy whether or not search mode was active. A caller
 * that must leave search mode WITHOUT touching the query — a load or refresh path that has just
 * seeded the query from an intent, for instance — calls [reset] instead. Getting these two mixed up
 * silently wipes a seeded query, so they are separate names rather than a boolean argument.
 */
class SearchModeController(
    private val onClearQuery: () -> Unit,
    private val clearOnClose: Boolean = true,
) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun open() { _active.value = true }

    fun close() {
        _active.value = false
        if (clearOnClose) onClearQuery()
    }

    fun reset() { _active.value = false }
}
