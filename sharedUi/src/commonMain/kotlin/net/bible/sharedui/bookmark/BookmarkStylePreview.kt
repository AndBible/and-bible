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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
 * drawn as a superscript by [SuperscriptMarker], matching how the reader actually draws it.
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
    val decoration = bookmarkStyleDecoration(style, colorArgb)
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            // `surface`, not `surfaceVariant`: the BW highlight fill is a fixed light/dark grey
            // close in tone to surfaceVariant, so HIGHLIGHT read as barely different from HIDDEN
            // here even though the reader shows a clear contrast on its white page (round-9a
            // whole-branch review M4).
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // MARKER and HIDDEN decorate nothing, so a partial split would show as an invisible seam in
        // the middle of the sentence -- skip it and keep one Text (same rule as LabelStyleTag).
        val split = decoratePartially && style != BookmarkDisplayStyle.MARKER && style != BookmarkDisplayStyle.HIDDEN
        if (split) {
            val (decorated, rest) = splitSelectionSample(sampleText)
            Text(decorated, style = textStyle, modifier = decoration.textModifier)
            Text(rest, style = textStyle)
        } else {
            Text(sampleText, style = textStyle, modifier = decoration.textModifier)
        }
        if (decoration.showsMarkerIcon) {
            Spacer(Modifier.width(1.dp))
            SuperscriptMarker(with(LocalDensity.current) { textStyle.fontSize.toDp() }, iconSlot)
        }
    }
}
