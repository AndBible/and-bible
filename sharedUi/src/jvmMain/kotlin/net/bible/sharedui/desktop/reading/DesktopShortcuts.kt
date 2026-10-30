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
package net.bible.sharedui.desktop.reading

import net.bible.sharedui.desktop.reading.DesktopBackDispatcher.Navigation

/** A change of the font size setting from the keyboard. */
enum class FontSizeChange {
    Larger, Smaller, Reset;

    /** The new `font_size_multiplier` percent for [current], within the settings slider's 10..500. */
    fun apply(current: Int, steps: Int = 1): Int = when (this) {
        Larger -> current + FONT_STEP * steps
        Smaller -> current - FONT_STEP * steps
        Reset -> FONT_DEFAULT
    }.coerceIn(FONT_MIN, FONT_MAX)

    companion object {
        const val FONT_STEP = 10
        const val FONT_DEFAULT = 100
        const val FONT_MIN = 10
        const val FONT_MAX = 500
    }
}

/**
 * A key as the shortcuts see it: DOM-style [key] ("f", "+", "Tab", "ArrowLeft", "F11", "Escape"), the modifiers, and where
 * it came from. [fromPane] = the page script reported it (the CEF pane had the focus), otherwise a Compose window key.
 * [editable] = the page focus is a text field; [composing] = an input method composition is running; [selection] = the
 * JSON of `bibleView.querySelection()` the page attached to a Ctrl+B.
 */
data class Chord(
    val key: String,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val fromPane: Boolean = false,
    val editable: Boolean = false,
    val composing: Boolean = false,
    val selection: String? = null,
)

/** The side effects of the shortcuts. [DesktopShortcutActionsImpl] is the production one. */
interface DesktopShortcutActions {
    /** Whether the reading screen is the current destination. */
    fun isOnReadingScreen(): Boolean

    /** The active window's id, null when there is none yet. */
    fun activeWindowId(): String?
    fun search(windowId: String)
    fun goToReference(windowId: String)

    /** Esc from a pane: a Compose back handler; when none takes it and [historyFallback], the reading history. */
    fun back(historyFallback: Boolean)

    /**
     * Back from Alt+Left or the mouse button: a Compose back handler first (an overlay on top owns Back), then the
     * page modals of [windowId] (null = the active window), then the reading history.
     */
    fun navigateBack(windowId: String?)
    fun bookmarkSelection(windowId: String, selectionJson: String)
    /** Applies [change] [steps] times at once (one setting write, one config resend); the result stays within 10..500. */
    fun changeFontSize(change: FontSizeChange, steps: Int = 1)
    fun focusNextWindow()
    fun focusPreviousWindow()
    fun toggleFullScreen()
    fun openSettings()

    /** Whether an Esc from [windowId]'s page may be taken: false while the page edits text (its editors handle Escape). */
    fun escapeAllowed(windowId: String): Boolean

    /**
     * Closes the page modals of [windowId] (null = the active window) when one is open, as Android's back does, and
     * returns true; false when none is open (nothing was done).
     */
    fun closePageModals(windowId: String?): Boolean = false
}

/**
 * The desktop shortcut table as decision logic: [onChord] decides what a [Chord] means, acts through [actions] and
 * returns whether the key was consumed. Pure apart from [actions]; no Compose or AWT types.
 *
 * Rules: nothing is acted on during IME composition; a text field in the page keeps plain keys, Esc and Ctrl+C/V/X/A/Z;
 * the reading shortcuts need the reading screen, back and Ctrl+, do not; chords the page handles itself (Ctrl+F, Ctrl+C,
 * a Ctrl+B without selection) are left to it when they come from the pane; Esc is only taken from the pane, Compose
 * delivers its own Esc to its back handlers.
 *
 * @param isRtl the layout direction, which swaps Alt+Left and Alt+Right
 */
class DesktopShortcuts(
    private val actions: DesktopShortcutActions,
    private val isRtl: () -> Boolean = { DesktopLayoutDirection.isRtl() },
) {
    /**
     * Handles a mouse or key navigation; forward is consumed without effect. Back goes through
     * [DesktopShortcutActions.navigateBack] (Compose back handler, page modals, history). Returns true (always consumed).
     */
    fun onNavigation(navigation: Navigation, windowId: String? = null): Boolean {
        if (navigation == Navigation.Back) actions.navigateBack(windowId)
        return true
    }

    /** [steps] Ctrl+wheel steps in a pane: [deltaY] < 0 (wheel up) = larger text. Same action as Ctrl+Plus/Minus. */
    fun onZoom(deltaY: Double, steps: Int = 1): Boolean {
        if (deltaY == 0.0 || steps < 1) return false
        return reading {
            actions.changeFontSize(if (deltaY < 0) FontSizeChange.Larger else FontSizeChange.Smaller, steps.coerceAtMost(MAX_ZOOM_STEPS))
        }
    }

    /** Handles [chord] for [windowId] (the pane's window, null for a Compose key: the active window). True = consumed. */
    fun onChord(chord: Chord, windowId: String?): Boolean {
        if (chord.composing) return false
        val key = if (chord.key.length == 1) chord.key.lowercase() else chord.key
        // Ctrl+Alt is AltGr on Windows (AltGr+L = ł, AltGr+0 = }): never a shortcut, its character is typed.
        if (chord.ctrl && chord.alt) return false
        if (chord.alt) {
            if (chord.editable) return false
            val nav = DesktopBackDispatcher.navigationForDomKey(key, alt = true, isRtl = isRtl()) ?: return false
            return onNavigation(nav, windowId)
        }
        if (!chord.ctrl) return when (key) {
            "Escape" -> {
                val id = windowId
                if (!chord.fromPane || chord.editable || id == null || !actions.escapeAllowed(id)) return false
                if (!actions.closePageModals(id)) actions.back(historyFallback = false)
                true
            }
            "F11" -> reading { actions.toggleFullScreen() }
            else -> false
        }
        // Ctrl chords. Copy, paste, cut, select all and undo are never ours.
        if (key.length == 1 && key in "cvxaz") return false
        if (key == ",") {
            actions.openSettings()
            return true
        }
        val id = windowId ?: actions.activeWindowId()
        return when (key) {
            "f" -> !chord.fromPane && id != null && reading { actions.search(id) }
            "l" -> id != null && reading { actions.goToReference(id) }
            "b" -> when {
                chord.editable -> false
                chord.fromPane -> chord.selection != null && id != null && reading { actions.bookmarkSelection(id, chord.selection) }
                else -> id != null && reading { actions.goToReference(id) }
            }
            "+", "=" -> reading { actions.changeFontSize(FontSizeChange.Larger) }
            "-", "_" -> reading { actions.changeFontSize(FontSizeChange.Smaller) }
            "0" -> reading { actions.changeFontSize(FontSizeChange.Reset) }
            "Tab" -> reading { if (chord.shift) actions.focusPreviousWindow() else actions.focusNextWindow() }
            else -> false
        }
    }

    private companion object {
        const val MAX_ZOOM_STEPS = 10
    }

    private inline fun reading(block: () -> Unit): Boolean {
        if (!actions.isOnReadingScreen()) return false
        block()
        return true
    }
}
