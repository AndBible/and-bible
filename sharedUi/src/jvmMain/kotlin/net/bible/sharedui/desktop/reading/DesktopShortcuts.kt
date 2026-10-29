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
    fun apply(current: Int): Int = when (this) {
        Larger -> current + FONT_STEP
        Smaller -> current - FONT_STEP
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

    /** Back: a Compose back handler first; when none takes it and [historyFallback], the reading history. */
    fun back(historyFallback: Boolean)
    fun bookmarkSelection(windowId: String, selectionJson: String)
    fun changeFontSize(change: FontSizeChange)
    fun focusNextWindow()
    fun focusPreviousWindow()
    fun toggleFullScreen()
    fun openSettings()

    /** Whether an Esc from [windowId]'s page may go back: no page modal open and no text being edited in the page. */
    fun escapeAllowed(windowId: String): Boolean
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
    /** Handles a mouse or key navigation; forward is consumed without effect. Returns true (always consumed). */
    fun onNavigation(navigation: Navigation): Boolean {
        if (navigation == Navigation.Back) actions.back(historyFallback = true)
        return true
    }

    /** Handles [chord] for [windowId] (the pane's window, null for a Compose key: the active window). True = consumed. */
    fun onChord(chord: Chord, windowId: String?): Boolean {
        if (chord.composing) return false
        val key = if (chord.key.length == 1) chord.key.lowercase() else chord.key
        if (chord.alt && !chord.ctrl) {
            val nav = DesktopBackDispatcher.navigationForDomKey(key, alt = true, isRtl = isRtl()) ?: return false
            return onNavigation(nav)
        }
        if (!chord.ctrl) return when (key) {
            "Escape" -> {
                val id = windowId
                if (!chord.fromPane || chord.editable || id == null || !actions.escapeAllowed(id)) return false
                actions.back(historyFallback = false)
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

    private inline fun reading(block: () -> Unit): Boolean {
        if (!actions.isOnReadingScreen()) return false
        block()
        return true
    }
}
