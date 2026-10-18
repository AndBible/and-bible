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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host.slots

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

/**
 * What the Speak settings sheet needs from the platform: a way into the system text-to-speech
 * settings. Android starts the TTS settings intent (falling back to the general settings); a
 * platform without such a screen can no-op.
 */
@kotlin.native.HiddenFromObjC
interface SpeakSettingsPlatform {
    /** Opens the platform's text-to-speech settings screen. */
    fun openSystemTtsSettings()
}

/**
 * What the quick sheets ask the platform to do once the user has picked something. Each member is
 * the body the matching full-screen chooser result runs, so the sheet and the screen cannot drift
 * apart.
 */
@kotlin.native.HiddenFromObjC
interface ReadingQuickActions {
    /**
     * Switches to workspace [id] from the quick sheet. Saves the outgoing workspace first (the quick
     * sheet never pauses the activity, so nothing else flushes it), then switches.
     */
    fun quickSwitchToWorkspace(id: String)

    /** Applies the document [bookStr] (an initials string, or null) chosen in the Documents sheet to the active window. */
    fun applyChosenDocument(bookStr: String?)

    /**
     * Applies a general-book, map or dictionary [key] chosen in a key-chooser sheet to the active
     * window. [book] is explicit because an EPUB table-of-contents key carries its own document.
     */
    fun applyChosenGenBookKey(book: Book?, key: Key)
}
