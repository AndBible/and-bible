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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.sharedui.theme.LocalIsDarkTheme

/** The band drawn under UNDERLINE text. Odd-looking as a constant, but the underline has to read as
 *  a band rather than a hairline at every font scale, and 3dp is what the reading view's underline
 *  gradient occupies at default text size. */
private val UnderlineBandHeight = 3.dp

/**
 * A one-line illustration of what a bookmark carrying this label looks like in the reading view.
 *
 * Deliberately an approximation: the real renderer paints gradients whose darkness depends on how
 * many bookmarks overlap (`bibleview-js/src/composables/bookmarks.ts:116-190`), and reproducing
 * that here would be a second renderer to keep in step for no gain. What must be exact is which of
 * the four styles decorates the text and which does not — that is the thing the user is choosing.
 *
 * The monochrome and alpha treatments below are NOT part of that approximation — they follow the
 * reader's own rules (`bibleview-js/src/composables/bookmarks.ts:140`, `:164-165`, `:193-199`), so
 * anyone changing the JS should find and update this too. One deliberate deviation: the monochrome
 * underline uses the theme's `onSurface` rather than a hardcoded black/white, staying consistent
 * with the rest of the theme instead of matching the JS literally.
 *
 * HIDDEN draws plain text and nothing else, because that is what a hidden bookmark looks like. It
 * is told apart from MARKER by MARKER drawing [iconSlot], and by the option label next to it.
 *
 * [iconSlot] is a slot rather than a parameter because the label's icon is an Android drawable that
 * only the host can resolve.
 */
@Composable
fun BookmarkStylePreview(
    style: BookmarkDisplayStyle,
    colorArgb: Int,
    sampleText: String,
    modifier: Modifier = Modifier,
    iconSlot: @Composable () -> Unit,
) {
    val colorMode = LocalDisplayColorMode.current
    // The reader treats monochrome as a SUBSTITUTION, not a desaturation: fixed greys for the
    // highlight and plain black/white for the underline (bookmarks.ts:140, :164-165, :193-199).
    // accentArgbFor is for workspace accents and would grey the label colour into the container
    // tone here, making highlight, underline and hidden indistinguishable in BW.
    val monochrome = colorMode == DisplayColorMode.BW
    // The theme's own resolved night state, not re-derived from a colour (that is exactly what
    // LocalIsDarkTheme exists to prevent) — this mirrors the reader's nightMode flag.
    val night = LocalIsDarkTheme.current
    val labelColor = Color(colorArgb)
    val highlightFill = when {
        monochrome -> if (night) Color(0xFFB4B4B4) else Color(0xFFD2D2D2)   // 180 / 210 grey
        else -> labelColor.copy(alpha = if (night) 0.4f else 0.3f)          // bookmarks.ts:198
    }
    val underlineColor = when {
        monochrome -> MaterialTheme.colorScheme.onSurface                    // black day / white night
        else -> labelColor
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val decoration = when (style) {
            BookmarkDisplayStyle.HIGHLIGHT ->
                Modifier.background(highlightFill, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp)
            BookmarkDisplayStyle.UNDERLINE ->
                Modifier.drawBehind {
                    val band = UnderlineBandHeight.toPx()
                    drawRect(
                        color = underlineColor,
                        topLeft = Offset(0f, size.height - band),
                        size = Size(size.width, band),
                    )
                }
            BookmarkDisplayStyle.MARKER, BookmarkDisplayStyle.HIDDEN -> Modifier
        }
        Text(
            sampleText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = decoration,
        )
        if (style == BookmarkDisplayStyle.MARKER) {
            Spacer(Modifier.width(6.dp))
            iconSlot()
        }
    }
}
