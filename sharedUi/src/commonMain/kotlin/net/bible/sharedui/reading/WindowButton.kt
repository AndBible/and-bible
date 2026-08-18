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

package net.bible.sharedui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.reading.railLabelFontScale

/**
 * The two call sites of classic Android's single `WindowButtonWidget(isRestoreButton = true/false)`:
 * [Rail] is the restore rail's per-window button (Plan A Task 5, `isRestoreButton = true`); [Pane]
 * is the floating per-pane "☰" button hosted on each split pane (Plan B Task 5,
 * `isRestoreButton = false`). [mode] switches the label layout: Rail restores classic's two-row
 * `topButtonText`/`buttonText` stack (bottom-start, `window_button.xml` + `WindowButtonWidget.kt:127,148`,
 * see [topLabel]); Pane keeps a single centred glyph, larger, like classic's own `windowButton`
 * text (`isRestoreButton = false` hides both `topButtonText`/`buttonText` and centres `windowButton`
 * itself, `WindowButtonWidget.kt:133-137,149-150`) — every other look (tint / minimised / sync /
 * links badges) is driven purely by the boolean/int state parameters below, not gated by [mode].
 * **Exception (fix-round-1):** [isPinned]'s indicator IS gated by [mode] — Pane only, never Rail —
 * because classic's `pinMode.visibility` itself requires `!isRestoreButton`
 * (`WindowButtonWidget.kt:86-95`): the pin indicator never coexists with the rail's
 * `topButtonText`/`buttonText` in classic, which is exactly why classic's tight rail-label geometry
 * never has to avoid it. See [isPinned].
 */
enum class WindowButtonMode { Rail, Pane }

private val WindowButtonSize = 40.dp
private val WindowButtonCorner = 8.dp
private val BadgeIconSize = 14.dp
/** Shared inset for the top-end/top-start badges' `Modifier.padding(...)` — also the basis of [RailBadgeRowHeight]. */
private val BadgeInset = 2.dp
/** The sync badge's glyph size (classic `synchronize` ImageView is 12dip; 9.dp reads the same beside an 8.dp digit). */
private val SyncBadgeIconSize = 9.dp
/**
 * The sync-group digit's size, declared in **dp** and converted to sp at use.
 *
 * A badge digit inside a FIXED 40.dp button cannot honour the system font scale: the button will
 * not grow, so a scaling glyph can only overflow — and it would overflow into precisely the two
 * things positioned relative to [RailBadgeRowHeight] (the rail label pair, and the Pane pin icon).
 * Declaring it in dp is what makes [RailBadgeRowHeight] an honest bound instead of an optimistic
 * one, by construction rather than by luck.
 *
 * [SyncGroupBadge] pins BOTH this digit `Text`'s `fontSize` (this constant, dp-derived, so the
 * glyph itself is scale-invariant) AND its `lineHeight` (to the same dp-derived size) — a `Text`
 * with `fontSize` set but `lineHeight` left unspecified still takes its line height from the
 * ambient `LocalTextStyle` (Material3's `typography.bodyLarge`, 24.sp), which has no relationship
 * to an 8.dp digit. That inflates the digit's measured line box to ~24.dp, and because the
 * containing `Row` uses `verticalAlignment = CenterVertically`, the much smaller sync [Icon] gets
 * centred inside that oversized box and pushed roughly halfway down it — past where the rail
 * label region starts. That push-down, present even at font scale 1.0, was the ORIGINAL F46
 * defect ("the sync icon overlaps the bible reference"); pinning `lineHeight` here removes it.
 * [SyncGroupBadge]'s `Row` additionally pins its own height to [SyncBadgeIconSize] for the same
 * reason, so nothing (a future style change, an ambient theme tweak) can reintroduce the
 * inflated box by another route — that pin is what finally makes [RailBadgeRowHeight] an HONEST
 * bound on what this badge draws, rather than a bound that merely happened to hold.
 */
private val SyncBadgeDigitSize = 8.dp
/** Classic `pinMode` ImageView size (`window_button.xml:97-107`, 12dip). */
private val PinIconSize = 12.dp
private val BorderWidth = 1.dp
private val MinimisedBorderWidth = 1.5.dp
private const val MinimisedAlpha = 0.62f
private val DashPattern = floatArrayOf(4f, 3f)

/** Classic rail `buttonText` size, set at runtime in `WindowButtonWidget.kt:127`. */
private val RailLabelSize = 13.sp
/** Classic rail `topButtonText` size (`window_button.xml`, `android:textSize="8.6sp"`). */
private val RailTopLabelSize = 9.sp
/** Classic `buttonText`/`topButtonText` start padding (`window_button.xml`, `paddingStart="1dip"` + the badge column). */
private val RailTextStartPadding = 2.dp
/** Classic `buttonText` bottom padding — subtracted from the label box before scaling the lines. */
private val RailLabelBottomPadding = 1.dp
/**
 * The tallest top-badge extent this file actually draws — the top-end `docType`/link [Icon]'s
 * `Modifier.align(Alignment.TopEnd).padding(BadgeInset).size(BadgeIconSize)` below — **derived**
 * from [BadgeIconSize]/[BadgeInset] rather than restated as a literal, so a later touch-target bump
 * to either can't silently drift out of sync with this reservation and reintroduce fix-round-1's
 * overlap (a hand-picked `16.dp` here would have "happened" to match today's constants without
 * anything keeping them equal). This total (currently 16dp) mirrors classic's `docType` bottom edge
 * — `layout_marginTop="2dip"` + 14dp height (`window_button.xml:123-133`), i.e. also 2dp+14dp there
 * — the y-offset classic's `topButtonText` sits directly below (`Top_toBottomOf="@id/docType"`,
 * `window_button.xml:52`). See the `WindowButtonMode.Rail` branch below for why this is reserved as
 * a *fixed* height rather than left as padding on a bottom-anchored column.
 *
 * See also: this constant is not Rail-only despite the name — the Pane pin [Icon]'s `top` offset
 * (below, the `isPinned && mode == WindowButtonMode.Pane` block) derives from it too, as the bound
 * the top-start sync badge must stay within for that offset to be correct.
 */
private val RailBadgeRowHeight = BadgeIconSize + BadgeInset

/**
 * Stateless Compose port of classic `WindowButtonWidget`
 * (`app/src/main/java/net/bible/android/view/util/widget/WindowButtonWidget.kt:75-151`), shared by
 * the restore rail and the floating per-pane button — see [WindowButtonMode]'s kdoc for how the two
 * classic use sites map onto this one composable.
 *
 * Look (classic → Compose), all colours drawn ONLY from [MaterialTheme.colorScheme] (no hard-coded
 * hues) so BW/e-ink monochrome (`AbTheme(colorMode = ...)`, which grayscales every scheme role)
 * degrades this button automatically, with no special-casing here:
 * - [isActive] → [androidx.compose.material3.ColorScheme.primaryContainer] fill (classic
 *   `*_active` drawable); inactive → [androidx.compose.material3.ColorScheme.surfaceVariant]
 *   (classic base drawable).
 * - [isMinimised] → the whole button is drawn at [MinimisedAlpha] (~0.62) alpha plus a dashed
 *   outline in [androidx.compose.material3.ColorScheme.outline] — signalling "known window, not
 *   currently shown", distinct from a merely-inactive button. There's no 1:1 classic analogue (the
 *   classic widget has no such "minimised" concept); this look was chosen for the new Compose split
 *   to read as "temporarily set aside" rather than plain "not selected".
 * - [isPinned] → **Pane mode only** (`WindowButtonMode.Pane`): classic's `pinMode` `ic_pin` glyph,
 *   start edge under the sync badge — `window_button.xml:97-107`
 *   `Top_toBottomOf="@id/synchronize"`, matching classic's `pinMode.visibility` requiring
 *   `!isRestoreButton` (`WindowButtonWidget.kt:86-95`). Rail mode never draws it — classic instead
 *   conveys a pinned rail window via a different background drawable (`WindowButtonWidget.kt:106-116`,
 *   not yet replicated by this composable) — so a caller passing `isPinned = true` with
 *   `mode = Rail` renders no visible indicator (fix-round-1: an earlier version drew it regardless of
 *   [mode] and it collided with the rail's two-row label).
 * - [isLinks] → a link glyph (classic `docType` force-swapped to `ic_link_black_24dp`); takes the
 *   SAME top-end corner as [leadingIcon] and always wins over it, exactly like classic always
 *   overwriting `docType`'s image when `window.isLinksWindow`.
 * - [syncGroup] > 0 → a small sync glyph + the group number in the top-start corner (classic
 *   `synchronize` + `syncGroup` text). The number is shown as given (already 1-based, matching
 *   classic's `(window.syncGroup + 1).toString()` — a caller passing the raw 0-based
 *   `Window.syncGroup` must add 1 itself).
 *
 * The whole box is the tap target: [onClick] fires on a tap, [onLongPress] on a long-press (classic
 * `setOnClickListener`/`setMyContextClickListener`), recognized by [detectTapGestures] directly
 * (no [androidx.compose.foundation.combinedClickable], per this composable's exact contract).
 *
 * @param label Rail: doc initials / ordinal (host-supplied); Pane: "☰" — plain text, not a resource
 *   id, so this file stays iOS-clean.
 * @param leadingIcon doc-type icon (host-supplied [Painter], never `R.drawable`/`ImageVector`
 *   resource ids); `null` → none shown. Ignored (visually) when [isLinks] is true.
 * @param topLabel Rail only: classic's tiny `topButtonText` row — `window.pageManager.titleText`
 *   (e.g. "Gen 1"), host-supplied like [label] so this file stays iOS-clean. `null` (the default,
 *   and what the Pane call site passes) renders no top row, matching classic hiding
 *   `topButtonText` for non-rail buttons (`WindowButtonWidget.kt:149`).
 */
@Composable
fun WindowButton(
    label: String,
    isActive: Boolean,
    isMinimised: Boolean,
    isPinned: Boolean,
    isLinks: Boolean,
    syncGroup: Int,
    mode: WindowButtonMode,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Painter? = null,
    topLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isActive) colors.primaryContainer else colors.surfaceVariant
    val contentColor = if (isActive) colors.onPrimaryContainer else colors.onSurfaceVariant
    val outlineColor = colors.outline
    val cornerShape = RoundedCornerShape(WindowButtonCorner)

    Box(
        modifier = modifier
            .size(WindowButtonSize)
            .alpha(if (isMinimised) MinimisedAlpha else 1f)
            .clip(cornerShape)
            .background(containerColor)
            .then(
                if (isMinimised) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = outlineColor,
                            cornerRadius = CornerRadius(WindowButtonCorner.toPx(), WindowButtonCorner.toPx()),
                            style = Stroke(
                                width = MinimisedBorderWidth.toPx(),
                                pathEffect = PathEffect.dashPathEffect(DashPattern),
                            ),
                        )
                    }
                } else {
                    Modifier.border(BorderWidth, colors.outlineVariant, cornerShape)
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            },
    ) {
        when (mode) {
            WindowButtonMode.Pane -> Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 2.dp),
            )
            // Classic's rail button is two-row: the tiny page title under the top badge row, and
            // the document abbreviation bottom-start (window_button.xml + WindowButtonWidget.kt:127,148).
            // `.height(RailBadgeRowHeight-reserved)` fixes this Column's OWN box to the space below
            // the badge row — unlike a bare bottom-anchored Column (whose top edge floats upward as
            // content grows), this guarantees the label pair's top edge can never rise above the
            // badge row, regardless of actual font-metric variance (see [RailBadgeRowHeight]).
            // verticalArrangement=Bottom then still hugs the two lines to the bottom, mirroring
            // classic's `buttonText` `Bottom_toBottomOf="@id/windowButton"`.
            WindowButtonMode.Rail -> {
                // The box is dp, the lines are sp: above ~1.05 font scale the bottom-anchored
                // Column would overflow upward into the badge row. One shared factor keeps the two
                // lines proportional to each other and inside the box; at scale 1.0 it is exactly
                // 1f, so nothing moves. See railLabelFontScale's kdoc.
                val density = LocalDensity.current
                val labelBoxHeight = WindowButtonSize - RailBadgeRowHeight
                val availablePx = with(density) { (labelBoxHeight - RailLabelBottomPadding).toPx() }
                val requiredPx = with(density) {
                    RailLabelSize.toPx() + if (topLabel != null) RailTopLabelSize.toPx() else 0f
                }
                val scale = railLabelFontScale(availablePx, requiredPx)
                val topLabelSize = RailTopLabelSize * scale
                val labelSize = RailLabelSize * scale
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .height(labelBoxHeight)
                        .padding(start = RailTextStartPadding, end = 1.dp, bottom = RailLabelBottomPadding),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (topLabel != null) {
                        Text(
                            text = topLabel,
                            fontSize = topLabelSize,
                            lineHeight = topLabelSize,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = label,
                        fontSize = labelSize,
                        lineHeight = labelSize,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Top-end corner: the link glyph always wins over a doc-type leadingIcon (mirrors classic
        // docType being force-swapped to ic_link when isLinksWindow).
        when {
            isLinks -> Icon(
                imageVector = Icons.Filled.Link,
                // TODO: no LocalStrings field for this yet — keep literal/null until one is added.
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(BadgeInset).size(BadgeIconSize),
            )
            leadingIcon != null -> Icon(
                painter = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(BadgeInset).size(BadgeIconSize),
            )
        }

        // Top-start corner: sync-group badge (classic `synchronize` + `syncGroup` text).
        if (syncGroup > 0) {
            SyncGroupBadge(
                group = syncGroup,
                color = contentColor,
                modifier = Modifier.align(Alignment.TopStart).padding(BadgeInset),
            )
        }
        // Pane only, fix-round-1: classic's `pinMode.visibility` requires `!isRestoreButton`
        // (`WindowButtonWidget.kt:86-95`) — the pin indicator is a Pane-only badge in classic; the
        // rail instead conveys pinned-ness through a different BACKGROUND drawable
        // (`bar_window_button*` vs `bar_window_unpinned_button*`, `WindowButtonWidget.kt:106-116`,
        // not replicated by this composable yet — tracked separately, not part of this task).
        // Position (Pane): start edge, directly under the sync badge — `top = RailBadgeRowHeight`
        // DERIVES that from the badge reservation rather than restating it as a literal, which is
        // the Compose equivalent of classic's `Top_toBottomOf="@id/synchronize"`. This is only a
        // true bound because `SyncGroupBadge`'s digit is declared in dp (see [SyncBadgeDigitSize]);
        // while it was `8.sp` the badge could grow past any dp offset written here.
        if (isPinned && mode == WindowButtonMode.Pane) {
            Icon(
                painter = LocalPinIcon.current(),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = BadgeInset, top = RailBadgeRowHeight)
                    .size(PinIconSize),
            )
        }
    }
}

@Composable
private fun SyncGroupBadge(group: Int, color: Color, modifier: Modifier = Modifier) {
    val digitSize = with(LocalDensity.current) { SyncBadgeDigitSize.toSp() }
    // .height(SyncBadgeIconSize) pins this Row's own cross-axis size so CenterVertically can never
    // centre the icon inside a taller-than-intended box again (see SyncBadgeDigitSize's kdoc).
    // Chained AFTER the caller's modifier (align + padding), matching the top-end badge's own
    // align -> padding -> size order, so the padding still surrounds the fixed-height content.
    Row(
        modifier = modifier.height(SyncBadgeIconSize),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Sync,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(SyncBadgeIconSize),
        )
        Text(
            text = group.toString(),
            color = color,
            fontSize = digitSize,
            lineHeight = digitSize,
            maxLines = 1,
        )
    }
}
