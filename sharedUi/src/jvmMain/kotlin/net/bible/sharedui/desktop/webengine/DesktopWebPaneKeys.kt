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
package net.bible.sharedui.desktop.webengine

import java.util.concurrent.atomic.AtomicReference

/**
 * Who owns the keyboard at window level. An off-screen web pane forwards every key to the page, whose own script implements
 * the shortcuts (D3a). The window-level Compose key handlers run root-first in the preview pass, before the pane, so they
 * would steal chords such as Ctrl+B or Ctrl+L while the pane has focus; they ask [windowKey] and stand down while a pane
 * [ownsKeys].
 *
 * Panes are tracked by owner token: a pane that loses focus clears only its own claim, so the blur of one pane never
 * clears the focus another pane just gained.
 */
object DesktopWebPaneKeys {
    private val owner = AtomicReference<Any?>(null)

    /** True while a web pane holds Compose focus and takes every key itself. */
    val ownsKeys: Boolean get() = owner.get() != null

    /** [pane] gained keyboard focus; it replaces any earlier owner. */
    fun focusGained(pane: Any) = owner.set(pane)

    /** [pane] lost focus or left the composition; clears the claim only when [pane] is still the owner. */
    fun focusLost(pane: Any) {
        owner.compareAndSet(pane, null)
    }

    /**
     * Runs a window-level key [handle] unless a pane owns the keys, in which case it returns false (not handled) without
     * calling it.
     */
    inline fun windowKey(handle: () -> Boolean): Boolean = if (ownsKeys) false else handle()
}
