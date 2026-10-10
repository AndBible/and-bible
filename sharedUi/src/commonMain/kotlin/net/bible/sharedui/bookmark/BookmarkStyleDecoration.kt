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

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoInk

/** Marker previews share the editor's tint: pure ink only in MONO, legacy accent in other modes. */
@Composable
fun bookmarkMarkerTint(colorArgb: Int): Color =
    if (isPureMonochrome()) monoInk(LocalIsDarkTheme.current)
    else Color(net.bible.sharedcore.theme.accentArgbFor(colorArgb, LocalDisplayColorMode.current))

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
 *
 * When [showsMarkerIcon] is true, the glyph is drawn by [SuperscriptMarker] — a shared 60%-scale,
 * raised placement in the label's colour — rather than each call site drawing its icon slot at its
 * own full size.
 */
data class BookmarkStyleDecoration(val textModifier: Modifier, val showsMarkerIcon: Boolean, val frame: Boolean = false)

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
 * Pure monochrome replaces the highlight band with a transparent, square 1dp ink frame.
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
    val monochrome = LocalDisplayColorMode.current.isGreyBase
    // The theme's own resolved night state, not re-derived from a colour (that is exactly what
    // LocalIsDarkTheme exists to prevent) — this mirrors the reader's nightMode flag.
    val night = LocalIsDarkTheme.current
    val labelColor = Color(colorArgb)
    val frame = isPureMonochrome()
    val highlightFill = when {
        frame -> Color.Transparent
        monochrome -> if (night) Color(0xFFB4B4B4) else Color(0xFFD2D2D2)   // 180 / 210 grey
        else -> labelColor.copy(alpha = if (night) 0.4f else 0.3f)          // bookmarks.ts:198
    }
    val underlineColor = when {
        monochrome -> MaterialTheme.colorScheme.onSurface                    // black day / white night
        else -> labelColor
    }
    return when (style) {
        BookmarkDisplayStyle.HIGHLIGHT -> BookmarkStyleDecoration(
            (if (frame) Modifier.background(highlightFill).border(1.dp, monoInk(night))
            else Modifier.background(highlightFill, RoundedCornerShape(2.dp))).padding(horizontal = 2.dp),
            showsMarkerIcon = false,
            frame = frame,
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
 * Splits [text] into the part a text-selection bookmark decorates and the part it does not.
 *
 * Why half of anything at all: a fully decorated sample cannot be told from a whole-verse one, and
 * the two axes are precisely what the tag and the preview have to distinguish. The cut is the space
 * NEAREST the midpoint, searched in both directions (ties broken towards the earlier one) — a
 * sentence breaks at whichever nearby word boundary is closest to half ("For God so| loved the
 * world", length 26, midpoint index 13: the space three characters after the midpoint (index 16)
 * is exactly as close as the one three characters before it (index 10), so the earlier one wins),
 * while a single word — every translated style name — has no space to find at all and falls
 * through to the midpoint character ("High|light"), which is what
 * makes a list tag read as partial. Scripts without word spaces take the same character-split
 * fallback, which is correct rather than merely tolerable: the decoration is illustrative, not
 * linguistic. A space sitting at index 0 is never used as the cut (that would decorate nothing at
 * all), so a leading space cannot make the decorated half empty.
 *
 * The space itself stays with the UNdecorated tail, so a highlight band never ends on a trailing
 * space.
 */
fun splitSelectionSample(text: String): Pair<String, String> {
    if (text.length < 2) return text to ""
    val mid = text.length / 2
    val before = text.lastIndexOf(' ', mid)
    val after = text.indexOf(' ', mid)
    val cut = when {
        before <= 0 && after < 0 -> mid
        before <= 0 -> after
        after < 0 -> before
        (mid - before) <= (after - mid) -> before
        else -> after
    }
    return text.substring(0, cut) to text.substring(cut)
}

/** The glyph size the hosts draw a label icon at (`ManageLabelIcon` / `AndroidLabelIcon`), which is
 *  fixed there — so scaling for the superscript has to happen here. */
private val HostGlyphSize = 24.dp

/**
 * The label's marker glyph as the reader draws it: a superscript.
 *
 * The reader's MARKER is a `<span class="bookmark-marker">` at `font-size: 60%` raised `top: -0.8em`
 * with `vertical-align: top`, in the label's colour, immediately after the verse text
 * (`bibleview-js/src/components/BibleView.vue:683-694`, `common.scss:86-91`,
 * `composables/bookmarks.ts:646-659`). Compose drew it at the host's full 24dp, vertically centred —
 * which is why it read as a large icon beside the text rather than a mark on it.
 *
 * The host slot's size is not ours to set, so the glyph is scaled by a `graphicsLayer` inside a box
 * of the intended size: layout gets the small size, drawing gets the shrunken glyph. 8dp is a floor —
 * below that the mark is not identifiable at any font scale.
 */
@Composable
fun SuperscriptMarker(textSizeDp: Dp, iconSlot: @Composable () -> Unit) {
    val size = max(textSizeDp * 0.6f, 8.dp)
    val rise = textSizeDp * 0.35f
    val scale = size / HostGlyphSize
    Box(modifier = Modifier.size(size).offset(y = -rise), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .requiredSize(HostGlyphSize)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            contentAlignment = Alignment.Center,
        ) { iconSlot() }
    }
}

/**
 * The ONE renderer for a style sample: the text (whole, or split into a decorated selection half
 * and an undecorated tail) plus MARKER's superscript glyph in the right place. Both surfaces that
 * show a sample — [LabelStyleTag] in the list row and [BookmarkStylePreview] in the editor — go
 * through this, and neither may re-implement any part of it.
 *
 * This exists because they DID re-implement it: each carried its own copy of the split condition,
 * both copies excluded MARKER from the split, and so the glyph was appended after the WHOLE sample
 * on both surfaces. The reader puts it after the last element of the selection for a partial range
 * (`bibleview-js/src/composables/bookmarks.ts:768`) and after the last verse element for a whole
 * one (`:735`), which is exactly what "split, glyph at the cut" reproduces.
 *
 * HIDDEN still never splits: it decorates nothing, so the seam would be invisible AND meaningless,
 * and splitting one Text into two only risks a different line break.
 *
 * Every branch caps at `maxLines = 1`, on both surfaces: [BookmarkStylePreview] documents itself
 * as "a one-line illustration", and [LabelStyleTag] is a short label in a fixed-height row — on
 * neither surface may a sample wrap to a second line.
 *
 * The decorated half never ellipsizes — it is the part that demonstrates "selection", so it must
 * render whole or the demonstration is lost. [ellipsizeTail] instead governs the UNdecorated tail
 * and the whole-text (non-split) branch, on both surfaces: one line ending in `…` is legible, a
 * mid-glyph clip is not, so both call sites pass `true`. The 110dp call-site cap
 * (`ManageLabelsScreen.kt`'s `tagMaxWidth`) is why [LabelStyleTag] needs the ellipsis at all —
 * `ManageLabels_styles_longname_light.png` (WORKSPACE, qualifiers = "fr", whose tag text is the
 * axis word and falls back to English because `bookmark_style_tag_*` isn't translated yet) shows
 * the row's NAME ellipsising with a two-tag line still present, not a tag itself ellipsising —
 * no tag string reaches 110dp until those keys are translated (see
 * `ManageLabelsGoldenTest.manageLabels_styles_longName`'s own KDoc for the current column
 * arithmetic). The cap and this ellipsis path stay in place against the day one does.
 */
@Composable
fun BookmarkStyleSample(
    style: BookmarkDisplayStyle,
    colorArgb: Int,
    text: String,
    textStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    decoratePartially: Boolean = false,
    ellipsizeTail: Boolean = false,
    iconSlot: @Composable () -> Unit,
) {
    val decoration = bookmarkStyleDecoration(style, colorArgb)
    val marker: @Composable () -> Unit = {
        if (decoration.showsMarkerIcon) {
            Spacer(Modifier.width(1.dp))
            SuperscriptMarker(with(LocalDensity.current) { textStyle.fontSize.toDp() }, iconSlot)
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (decoratePartially && style != BookmarkDisplayStyle.HIDDEN) {
            val (decorated, rest) = splitSelectionSample(text)
            Text(decorated, style = textStyle, color = color, maxLines = 1, modifier = decoration.textModifier)
            marker()
            Text(
                rest,
                style = textStyle,
                color = color,
                maxLines = 1,
                overflow = if (ellipsizeTail) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
        } else {
            Text(
                text,
                style = textStyle,
                color = color,
                maxLines = 1,
                overflow = if (ellipsizeTail) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = decoration.textModifier,
            )
            marker()
        }
    }
}

/**
 * One style tag on a label row: [text] drawn with [style]'s decoration in the label's colour, at
 * `labelSmall`.
 *
 * The text names the AXIS ("Selection", "Whole verse", "Workspace"), not the style — the decoration
 * is what says which style, and repeating it in words next to a live example was the redundant half
 * of the pair (round-15a device feedback). Round 10a chose the style's own name for the good reason
 * that it differs per row and was already translated; the axis word costs three new strings and
 * makes each tag say something the decoration cannot.
 *
 * The caller supplies the text so this composable has no opinion about which axis it is drawing.
 *
 * HIDDEN is muted to `onSurfaceVariant` and carries no decoration, so the muting is the ONLY signal
 * that the style is "not drawn". That is a deliberate, accepted cost of the axis wording; the style
 * vocabulary is taught by the editor's radio options and by the help dialog's style row.
 */
@Composable
fun LabelStyleTag(
    text: String,
    style: BookmarkDisplayStyle,
    colorArgb: Int,
    modifier: Modifier = Modifier,
    decoratePartially: Boolean = false,
    iconSlot: @Composable () -> Unit,
) {
    val textStyle = MaterialTheme.typography.labelSmall
    val color = if (style == BookmarkDisplayStyle.HIDDEN) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    BookmarkStyleSample(
        style = style,
        colorArgb = colorArgb,
        text = text,
        textStyle = textStyle,
        color = color,
        modifier = modifier,
        decoratePartially = decoratePartially,
        ellipsizeTail = true,
        iconSlot = iconSlot,
    )
}
