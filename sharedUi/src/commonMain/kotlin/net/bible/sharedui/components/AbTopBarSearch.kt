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

package net.bible.sharedui.components

/**
 * A one-shot instruction to an inline top-bar search field about focus and the software keyboard.
 *
 * It is an INSTRUCTION, not a level: the bar acts on it and then acknowledges via
 * [AbTopBarSearchCallbacks.onImeRequestHandled], which returns it to `null`. That acknowledgement is
 * what arms the next edge — a boolean level would not fire twice for two consecutive requests. Same
 * shape as the reading toolbar's `SearchFieldImeRequest`, deliberately duplicated rather than shared
 * because that one lives in `:sharedCore`'s reading package and carries reading-search semantics.
 */
enum class AbSearchImeRequest { Focus, Release }

/**
 * Everything an inline top-bar search mode renders, as plain data.
 *
 * A `null` instance — the default on [AbTopAppBar] and [AbScaffold] — means "not in search mode", so
 * the normal bar and every one of its goldens are untouched by construction.
 */
data class AbTopBarSearchState(
    val query: String,
    val imeRequest: AbSearchImeRequest? = null,
)

/**
 * Callbacks out of an inline top-bar search mode. A class rather than a lambda bundle so a caller
 * cannot silently omit one and so the parameter list of [AbTopAppBar] stays short.
 *
 * [onClose] is expected to leave search mode AND clear the query (classic parity: collapsing the
 * SearchView clears the filter). The bar does not clear anything itself — it is stateless.
 *
 * The component has no way to intercept hardware back itself — a host that wants "back leaves
 * search mode before it leaves the screen" is responsible for routing hardware back to [onClose]
 * on its own (e.g. an `onBackPressed`/`OnBackPressedCallback` override that checks search-mode
 * state first).
 */
class AbTopBarSearchCallbacks(
    val onQueryChange: (String) -> Unit,
    val onClose: () -> Unit,
    val onImeRequestHandled: () -> Unit,
)
