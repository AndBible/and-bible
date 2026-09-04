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

package net.bible.sharedui.ai

import net.bible.sharedui.strings.Strings

/**
 * Maps a `PromptContext.name` to its localized label. Extracted from `PromptEditScreen`'s private
 * `contextLabel` in 17f, when three call sites appeared: the editor's chips, the manager row's
 * target list, and the filter sheet's target chips. An unknown id falls through to itself rather
 * than crashing — the ids come from `PromptContextIds.ordered`, but a future enum member added on
 * the host side must not take the screen down.
 */
internal fun promptContextLabel(contextId: String, strings: Strings): String = when (contextId) {
    "VERSE_SELECTION" -> strings.promptContextVerseSelection
    "TEXT_SELECTION" -> strings.promptContextTextSelection
    "WINDOW_MENU" -> strings.promptContextWindowMenu
    "WORKSPACE_MENU" -> strings.promptContextWorkspaceMenu
    "NOTE_EDITOR" -> strings.promptContextNoteEditor
    else -> contextId
}
