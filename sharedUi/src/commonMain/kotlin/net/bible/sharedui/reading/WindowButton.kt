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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The two call sites of classic Android's single `WindowButtonWidget(isRestoreButton = true/false)`:
 * [Rail] is the restore rail's per-window button (Plan A Task 5, `isRestoreButton = true`); [Pane]
 * is the floating per-pane "☰" button hosted on each split pane (Plan B Task 5,
 * `isRestoreButton = false`). [mode] switches the label layout: Rail restores classic's two-row
 * `topButtonText`/`buttonText` stack (bottom-start, `window_button.xml` + `WindowButtonWidget.kt:126,145`,
 * see [topLabel]); Pane keeps a single centred glyph, larger, like classic's own `windowButton`
 * text (`isRestoreButton = false` hides both `topButtonText`/`buttonText` and centres `windowButton`
 * itself, `WindowButtonWidget.kt:133-137,149-150`) — every other look (tint / minimised / badges) is
 * driven purely by the boolean/int state parameters below, not gated by [mode], so a host can show
 * e.g. a pin badge on a Pane button too if it ever needs to (classic gated pin/sync visibility by
 * `isRestoreButton` imperatively in `updateSettings()`; this port leaves that decision to the
 * caller, which already knows which state applies where).
 */
enum class WindowButtonMode { Rail, Pane }

private val WindowButtonSize = 40.dp
private val WindowButtonCorner = 8.dp
private val BadgeIconSize = 14.dp
private val PinDotSize = 6.dp
private val BorderWidth = 1.dp
private val MinimisedBorderWidth = 1.5.dp
private const val MinimisedAlpha = 0.62f
private val DashPattern = floatArrayOf(4f, 3f)

/** Classic rail `buttonText` size, set at runtime in `WindowButtonWidget.kt:126`. */
private val RailLabelSize = 13.sp
/** Classic rail `topButtonText` size (`window_button.xml`, `android:textSize="8.6sp"`). */
private val RailTopLabelSize = 9.sp
/** Classic `buttonText`/`topButtonText` start padding (`window_button.xml`, `paddingStart="1dip"` + the badge column). */
private val RailTextStartPadding = 2.dp

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
 * - [isPinned] → a small dot badge (classic `pinMode` `ic_pin`, start edge under the sync badge —
 *   `window_button.xml:97-107` `Top_toBottomOf="@id/synchronize"`).
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
            // the document abbreviation bottom-start (window_button.xml + WindowButtonWidget.kt:126,145).
            WindowButtonMode.Rail -> Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = RailTextStartPadding, end = 1.dp, bottom = 1.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                if (topLabel != null) {
                    Text(
                        text = topLabel,
                        fontSize = RailTopLabelSize,
                        lineHeight = RailTopLabelSize,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = label,
                    fontSize = RailLabelSize,
                    lineHeight = RailLabelSize,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(BadgeIconSize),
            )
            leadingIcon != null -> Icon(
                painter = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(BadgeIconSize),
            )
        }

        // Top-start corner: sync-group badge (classic `synchronize` + `syncGroup` text).
        if (syncGroup > 0) {
            SyncGroupBadge(
                group = syncGroup,
                color = contentColor,
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
            )
        }
        // Start edge, directly under the sync badge (classic `pinMode`,
        // window_button.xml:97-107 `Top_toBottomOf="@id/synchronize"`) — NOT bottom-start, which
        // would collide with the rail's bottom label row.
        if (isPinned) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 15.dp).size(PinDotSize)
                    .clip(CircleShape)
                    .background(contentColor),
            )
        }
    }
}

@Composable
private fun SyncGroupBadge(group: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Sync,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(9.dp),
        )
        Text(text = group.toString(), color = color, fontSize = 8.sp, maxLines = 1)
    }
}
