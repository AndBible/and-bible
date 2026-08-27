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

package net.bible.sharedui.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle

/**
 * A one-line illustration of what a bookmark carrying this label looks like in the reading view.
 *
 * The decoration rules (which style paints what, and the monochrome substitutions) live in
 * [bookmarkStyleDecoration] — shared with [LabelStyleTag] so the two cannot drift apart.
 *
 * HIDDEN draws plain text and nothing else, because that is what a hidden bookmark looks like. It
 * is told apart from MARKER by MARKER drawing [iconSlot], and by the option label next to it.
 *
 * [iconSlot] is a slot rather than a parameter because the label's icon is an Android drawable that
 * only the host can resolve.
 *
 * [decoratePartially] decorates only the first half of [sampleText] (via [splitSelectionSample]),
 * for the selection axis — a text-selection bookmark covers part of a verse, a whole-verse one
 * covers all of it, and a fully decorated sample cannot show that difference. MARKER's glyph is
 * drawn as a superscript by [SuperscriptMarker], matching how the reader actually draws it, and
 * when [decoratePartially] is set the glyph's position follows the selection — right after the
 * decorated half, not after the whole sample.
 */
@Composable
fun BookmarkStylePreview(
    style: BookmarkDisplayStyle,
    colorArgb: Int,
    sampleText: String,
    modifier: Modifier = Modifier,
    decoratePartially: Boolean = false,
    iconSlot: @Composable () -> Unit,
) {
    BookmarkStyleSample(
        style = style,
        colorArgb = colorArgb,
        text = sampleText,
        textStyle = MaterialTheme.typography.bodyLarge,
        color = LocalContentColor.current,
        modifier = modifier
            .fillMaxWidth()
            // `surface`, not `surfaceVariant`: the BW highlight fill is a fixed light/dark grey
            // close in tone to surfaceVariant, so HIGHLIGHT read as barely different from HIDDEN
            // here even though the reader shows a clear contrast on its white page (round-9a
            // whole-branch review M4).
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decoratePartially = decoratePartially,
        ellipsizeTail = false,
        iconSlot = iconSlot,
    )
}
