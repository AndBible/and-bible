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

import net.bible.sharedui.components.OverlayPresence
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/** A pane that can be handed the keyboard focus again (see [DesktopWebPaneKeys.restoreAfterOverlay]). */
interface FocusRestorable {
    /** Moves Compose focus into the pane; runs on the UI thread. */
    fun requestFocus()
}

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

    /**
     * A pane whose focus a primary press outside it just took (see [pressTookFocus]); the press that opens an overlay
     * clears the pane's focus before the overlay is composed. Dropped by the next press elsewhere ([pressElsewhere]).
     */
    private val pressCleared = AtomicReference<Any?>(null)

    /** The pane that had the keys when the first overlay opened: the only one [restoreAfterOverlay] gives them back to. */
    private val beforeOverlay = AtomicReference<Any?>(null)

    init {
        installOverlayHooks()
    }

    /**
     * An overlay (sheet, dialog, drawer) takes Compose focus from the pane and nothing gives it back. The pane that held
     * it when the first overlay opens is remembered; when the last one closes it gets it back, posted to the event thread
     * so the overlay's window is gone by then. A user who was in a Compose text field before the overlay opened has no
     * pane to return to, so that focus is never overridden.
     */
    private fun installOverlayHooks() {
        OverlayPresence.onFirstOpened = { beforeOverlay.set(owner.get() ?: pressCleared.get()) }
        OverlayPresence.onAllClosed = { SwingUtilities.invokeLater { restoreAfterOverlay() } }
    }

    /** True while a web pane holds Compose focus and takes every key itself. */
    val ownsKeys: Boolean get() = owner.get() != null

    /** [pane] gained keyboard focus; it replaces any earlier owner. */
    fun focusGained(pane: Any) {
        owner.set(pane)
        pressCleared.set(null)
    }

    /** A primary press outside [pane] is about to take its focus away. */
    fun pressTookFocus(pane: Any) = pressCleared.set(pane)

    /** A primary press outside every pane that took no pane's focus (the focus is, or goes, somewhere else). */
    fun pressElsewhere() = pressCleared.set(null)

    /** [pane] lost focus or left the composition; clears the claim only when [pane] is still the owner. */
    fun focusLost(pane: Any) {
        owner.compareAndSet(pane, null)
    }

    /** Test hook: no owner, no remembered pane, no pending press; reinstalls the overlay listener. */
    fun resetForTests() {
        owner.set(null)
        pressCleared.set(null)
        beforeOverlay.set(null)
        OverlayPresence.resetForTests()
        installOverlayHooks()
    }

    /** [pane] left the composition for good: it is never given the focus back. */
    fun forget(pane: Any) {
        pressCleared.compareAndSet(pane, null)
        beforeOverlay.compareAndSet(pane, null)
    }

    /**
     * The last Compose overlay closed: gives the focus back to the pane that had it before, unless some pane has it
     * already. Returns whether a pane was asked.
     */
    fun restoreAfterOverlay(): Boolean {
        // Posted to the event thread: a menu item may have opened a sheet in the meantime.
        if (OverlayPresence.count > 0) return false
        val pane = beforeOverlay.getAndSet(null) as? FocusRestorable ?: return false
        if (owner.get() != null) return false
        pane.requestFocus()
        return true
    }

    /**
     * Runs a window-level key [handle] unless a pane owns the keys, in which case it returns false (not handled) without
     * calling it.
     */
    inline fun windowKey(handle: () -> Boolean): Boolean = if (ownsKeys) false else handle()
}
