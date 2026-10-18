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

package net.bible.sharedui.reading.bridge

import net.bible.service.sword.ShareSelection
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.platform.AppSettings

/**
 * The plain text the app shares or copies for [selection], formatted by the user's share options
 * (the `share_*` preferences). The same options feed the share sheet and the clipboard copy.
 */
@kotlin.native.HiddenFromObjC
fun shareableSelectionText(selection: ShareSelection, facade: SwordContentFacade, settings: AppSettings): String =
    facade.getSelectionText(
        selection,
        showVerseNumbers = settings.getBoolean("share_verse_numbers", true),
        advertiseApp = settings.getBoolean("share_show_add", true),
        abbreviateReference = settings.getBoolean("share_abbreviate_reference", true),
        showNotes = settings.getBoolean("show_notes", true),
        showVersion = settings.getBoolean("share_show_version", true),
        showReference = settings.getBoolean("share_show_reference", true),
        showReferenceAtFront = settings.getBoolean("share_show_reference_at_front", true),
        showSelectionOnly = settings.getBoolean("show_selection_only", true),
        showEllipsis = settings.getBoolean("show_ellipsis", true),
        showQuotes = settings.getBoolean("share_show_quotes", false),
        separateVersesWithNewlines = settings.getBoolean("share_separate_verses_newlines", false),
    )
