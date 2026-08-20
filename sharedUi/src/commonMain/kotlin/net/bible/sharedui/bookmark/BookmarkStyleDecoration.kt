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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.sharedui.theme.LocalIsDarkTheme

/** The band drawn under UNDERLINE text. Odd-looking as a constant, but the underline has to read as
 *  a band rather than a hairline at every font scale, and 3dp is what the reading view's underline
 *  gradient occupies at default text size. */
private val UnderlineBandHeight = 3.dp

/**
 * How a bookmark style decorates text, and whether it also wants the label's marker glyph.
 *
 * One renderer, two call sites: [BookmarkStylePreview] (the editor's "what will this look like"
 * card) and [LabelStyleTag] (the list row's small tag). Duplicating the rules is what the
 * [BookmarkStylePreview] KDoc warns against.
 */
data class BookmarkStyleDecoration(val textModifier: Modifier, val showsMarkerIcon: Boolean)

/**
 * Resolves [style] into a decoration for text painted in [colorArgb].
 *
 * Deliberately an approximation of the reader: the real renderer paints gradients whose darkness
 * depends on how many bookmarks overlap (`bibleview-js/src/composables/bookmarks.ts:116-190`), and
 * reproducing that would be a second renderer to keep in step for no gain. What must be exact is
 * which of the four styles decorates the text and which does not.
 *
 * The monochrome and alpha treatments are NOT part of that approximation — they follow the reader's
 * own rules (`bookmarks.ts:140`, `:164-165`, `:193-199`), so anyone changing the JS should find and
 * update this too. One deliberate deviation: the monochrome underline uses the theme's `onSurface`
 * rather than a hardcoded black/white, staying consistent with the rest of the theme.
 *
 * HIDDEN and MARKER return an undecorated modifier, because that is what they look like: HIDDEN
 * draws nothing, MARKER draws the glyph instead. Their callers tell them apart — the preview by
 * drawing the glyph, the tag by its own text.
 */
@Composable
fun bookmarkStyleDecoration(style: BookmarkDisplayStyle, colorArgb: Int): BookmarkStyleDecoration {
    // The reader treats monochrome as a SUBSTITUTION, not a desaturation: fixed greys for the
    // highlight and plain black/white for the underline. accentArgbFor is for workspace accents and
    // would grey the label colour into the container tone here, making highlight, underline and
    // hidden indistinguishable in BW.
    val monochrome = LocalDisplayColorMode.current == DisplayColorMode.BW
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
    return when (style) {
        BookmarkDisplayStyle.HIGHLIGHT -> BookmarkStyleDecoration(
            Modifier.background(highlightFill, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp),
            showsMarkerIcon = false,
        )
        BookmarkDisplayStyle.UNDERLINE -> BookmarkStyleDecoration(
            Modifier.drawBehind {
                val band = UnderlineBandHeight.toPx()
                drawRect(
                    color = underlineColor,
                    topLeft = Offset(0f, size.height - band),
                    size = Size(size.width, band),
                )
            },
            showsMarkerIcon = false,
        )
        BookmarkDisplayStyle.MARKER -> BookmarkStyleDecoration(Modifier, showsMarkerIcon = true)
        BookmarkDisplayStyle.HIDDEN -> BookmarkStyleDecoration(Modifier, showsMarkerIcon = false)
    }
}

/**
 * A label's style, small enough to sit on a list row: **the style's own localised name, drawn with
 * that style**, in the label's colour.
 *
 * The name is the sample. That is what makes it work in a list — the text differs per row so the
 * column stays scannable (a repeated verse sample would be identical noise on every row), it is
 * self-documenting so there is no shape convention to learn, it is short enough for two axes side
 * by side on a narrow screen, and the four names are already translated in 50 locales
 * (`displayModeHighlight` … `displayModeHidden`), so this needs no new string.
 *
 * HIDDEN is additionally muted to `onSurfaceVariant` — here, unlike in [BookmarkStylePreview],
 * that is right: the preview shows what the reader draws (plain text), while this tag is a label
 * *about* the style, and "not drawn" is the thing it has to say.
 */
@Composable
fun LabelStyleTag(
    style: BookmarkDisplayStyle,
    colorArgb: Int,
    modifier: Modifier = Modifier,
    iconSlot: @Composable () -> Unit,
) {
    val strings = LocalStrings.current
    val decoration = bookmarkStyleDecoration(style, colorArgb)
    val text = when (style) {
        BookmarkDisplayStyle.HIGHLIGHT -> strings.displayModeHighlight
        BookmarkDisplayStyle.UNDERLINE -> strings.displayModeUnderline
        BookmarkDisplayStyle.MARKER -> strings.displayModeMarker
        BookmarkDisplayStyle.HIDDEN -> strings.displayModeHidden
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (style == BookmarkDisplayStyle.HIDDEN) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            // A tag is a short label, not a paragraph -- it must never wrap. Wrapping also
            // silently inflates the row past its 48dp target.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = decoration.textModifier,
        )
        if (decoration.showsMarkerIcon) {
            Spacer(Modifier.width(4.dp))
            iconSlot()
        }
    }
}
