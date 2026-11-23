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

package net.bible.sharedui.components

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings
import kotlin.math.roundToInt

/**
 * The share of the window a bottom sheet may take (#4207, spec `2026-11-22-compact-bottom-sheets-design.md`,
 * decision 1): half, hard. [AbModalBottomSheet] bounds its whole content by this share of the height the sheet's
 * window offers (so tablets and desktop windows scale, and the IME is already excluded the way the sheet itself
 * is), and the reading view stays at least half visible above any sheet. Content that does not fit scrolls inside
 * the sheet; a short sheet keeps its natural height.
 */
const val AbSheetMaxHeightFraction: Float = 0.5f

/**
 * The floor of the half cap (#4207): a sheet is never capped below `min(offered height, AbSheetMinCapHeight)`. It only
 * bites when the window offers less than twice this, i.e. under 320dp: a phone in landscape with the soft keyboard up
 * offers ~120dp, half of which (60dp) would clip the sheet's own header and search field. Portrait and landscape
 * without a keyboard are unchanged.
 */
val AbSheetMinCapHeight: Dp = 160.dp

/**
 * The label assign sheet's floor (#4215, spec amendment 2026-11-23, item 2): a phone in landscape without the keyboard
 * offers ~330-390dp, half of which showed the header, the chip row and barely one label. This floor keeps the header
 * (48dp), the chip row (48dp) and two label rows (2 x 48dp) visible even when each sits under its own category header
 * (2 x 36dp: a ticked label lists under "Selected labels", the next under "Other labels"), plus the sheet's 16dp bottom
 * padding. The iPhone simulator run showed the 248dp first try cutting the second row in that case. Like [AbSheetMinCapHeight] it never exceeds the offered height, so a window shorter than this
 * (the keyboard up) gets the whole offered height, as before. Other sheets keep the plain cap.
 */
val AbLabelAssignSheetMinCapHeight: Dp = 280.dp

/**
 * The upper limit of a new sheet's scrolling region where nothing else bounds it: content-only frames (goldens,
 * UI tests) that render without a sheet window, and bodies that are not given the flexible height. 400dp is
 * round 12c's shipped cap (`SettingsEditorSheet.kt`) and round 13a's (`SpeakSettingsSheet.kt`).
 *
 * Inside an [AbModalBottomSheet] the half-window cap ([AbSheetMaxHeightFraction]) decides, with this as an
 * additional upper limit for the sheets that always had it: on a tall window half the window is more than 400dp and
 * a short list should not grow past what it always had. The four older 480dp bounds (prompt and model selectors,
 * read history, the colour editor) and the 70 % fractions of the grid and label sheets are now only that upper
 * limit, or gone.
 */
val AbSheetContentMaxHeight: Dp = 400.dp

/**
 * True inside an [AbModalBottomSheet], whose content column is bounded by [AbSheetMaxHeightFraction] of the window.
 * There a scroll region takes `weight(1f, fill = false)` so the rows after it (footer, confirm row) are measured
 * first instead of being squeezed to nothing; outside a sheet (content-only frames) the column is unbounded and a
 * weight would collapse to zero, so the old fixed bound applies.
 */
val LocalAbSheetHeightCapped = staticCompositionLocalOf { false }

/**
 * [AbModalBottomSheet]'s bound: measures its content with at most [fraction] of the height it is offered, but never
 * less than `min(offered, minCapHeight)` (the floor, [AbSheetMinCapHeight] unless a sheet asks for more). Unbounded
 * constraints (a content-only frame) pass through untouched.
 */
fun Modifier.abSheetHeightCap(
    fraction: Float = AbSheetMaxHeightFraction,
    minCapHeight: Dp = AbSheetMinCapHeight,
): Modifier = layout { measurable, constraints ->
    val cap = if (constraints.hasBoundedHeight) {
        // cap = max(fraction * offered, min(offered, minCapHeight)): see [AbSheetMinCapHeight].
        maxOf((constraints.maxHeight * fraction).roundToInt(), minOf(constraints.maxHeight, minCapHeight.roundToPx()))
    } else constraints.maxHeight
    val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight, cap), maxHeight = cap))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * Makes a sheet Column child flexible: it takes the height left after its siblings that must stay visible (header,
 * footer, confirm row) have been measured, and no more than it needs. Inside an [AbModalBottomSheet] only
 * ([LocalAbSheetHeightCapped]); a plain [Modifier] elsewhere. Order matters: a Column measures its non-weighted
 * children first, then divides the rest among the weighted ones.
 */
@Composable
fun ColumnScope.sheetFlexible(): Modifier =
    if (LocalAbSheetHeightCapped.current) Modifier.weight(1f, fill = false) else Modifier

/**
 * A sheet page's header: an optional leading back arrow, the title, optional trailing [actions], and
 * a ✕ as the explicit close affordance (round 13a's idiom — swipe, scrim tap and back all reach
 * `onDismissRequest`, and a sheet has no dialog button row to put a "Cancel" in).
 *
 * Shaped after `SpeakSettingsSheet.kt:93-115` and `SettingsEditorSheet.kt:93-110`, both of which
 * hand-roll this Row. Unifying those two onto this function is deliberately NOT done here: both
 * files belong to the sibling container `compose-14b` this round (spec §12), so touching them would
 * manufacture the one merge conflict the fork exists to avoid. Fold them in after the merge.
 *
 * [canGoBack]/[onBack]/[actions] were added in round 15b (fix round 2 of `AbQuickSheet`'s task) to
 * absorb what used to be a private `QuickSheetHeader` copy of this exact Row in `AbQuickSheet.kt` —
 * two insertions (a leading back arrow, a trailing actions slot) were the entire diff between the
 * two, so the copy was folded back into this function instead of kept alongside it. All defaults
 * reproduce the original two-argument call exactly: [canGoBack] defaulting to `{ false }` never
 * renders the back arrow and [actions] defaulting to `null` never renders anything extra, so every
 * existing call site (`AbChoiceSheet.kt`, `AbMultiSelectSheet.kt`, `AbActionSheet.kt`,
 * `AbReadHistorySheet.kt`, `ReadingLlmDialogs.kt`, both still calling `AbSheetHeader(title =
 * ..., onClose = ...)` with nothing else) renders byte-identically to before.
 *
 * [showClose] (run 3 final-review fix wave, I2): an uncancellable action sheet
 * (`AppDialogRequest.Options.cancellable = false`) hides the ✕ — it is a fourth way to dismiss,
 * alongside swipe/scrim/back, all of which a non-cancellable sheet must refuse. The sheet's own
 * [AbActionSheetRow] built from `dismissText` stays the one explicit way out. Defaults to `true`,
 * so every other existing caller renders byte-identically to before.
 */
@Composable
fun AbSheetHeader(
    title: String,
    onClose: () -> Unit,
    canGoBack: () -> Boolean = { false },
    onBack: () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
    showClose: Boolean = true,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack()) {
            IconButton(onClick = onBack) {
                // F84: same label as AbTopAppBar's back arrow.
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.settingsEditorBack)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // I3 (whole-branch review fix wave): weight(1f) on the TITLE, not a trailing Spacer.
            // In a Row, unweighted children measure first against the remaining space -- so
            // without this, a long title (round 15b's grid-step title, or History's
            // "History (%1$s: Window %2$d)" with a user-chosen workspace name) starves the
            // trailing actions/close IconButton down to zero width. A clipped IconButton still
            // takes hit-test area (this port has been bitten by exactly this shape before). Short
            // titles render identically: the text is left-aligned in its (now weighted) slot and
            // the icons stay pinned right, so every pre-existing caller stays byte-identical.
            modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
        )
        if (actions != null) actions()
        if (showClose) AbSheetCloseButton(onClose)
    }
}

/** The same localized close affordance in headers and compact, title-less sheet rows. */
@Composable
fun AbSheetCloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    // Inside a modal sheet the X slides it out first (like scrim/back); elsewhere it reports directly.
    val animatedClose = LocalAbSheetAnimatedClose.current
    IconButton(onClick = { if (animatedClose != null) animatedClose(onClose) else onClose() }, modifier = modifier) {
        Icon(Icons.Filled.Close, contentDescription = LocalStrings.current.settingsEditorClose)
    }
}

/**
 * Title-less sheets share their first content row with the close button. Reserve the trailing
 * touch target rather than covering a selectable row; start/end padding and alignment follow RTL.
 */
@Composable
fun AbSheetCloseOverlay(onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 56.dp)) { content() }
        AbSheetCloseButton(onClose, Modifier.align(Alignment.TopEnd).padding(end = 8.dp))
    }
}

/**
 * Scrolls vertically inside a capped sheet ([LocalAbSheetHeightCapped]); a no-op elsewhere, where the column is
 * unbounded (content-only frames render at their natural height). For a sheet body that is not a list: the content
 * must not contain lazy lists or flexible scroll regions ([AbSheetScrollBound], [sheetFlexible]); inside a scrolling
 * column they collapse to zero height or throw.
 */
@Composable
fun Modifier.sheetVerticalScroll(): Modifier {
    val state = rememberScrollState()
    return if (LocalAbSheetHeightCapped.current) verticalScroll(state) else this
}

/**
 * A form-like sheet body (fields, switches, explanatory text) between a header and a pinned confirm row: flexible
 * ([sheetFlexible]) and scrolling, so a body taller than the half-window cap scrolls while the rows after it stay
 * on screen. A plain [Column] outside a capped sheet. Same restriction as [sheetVerticalScroll]: no lazy lists or
 * flexible scroll regions inside, and any nested scroller must be unbounded-height-safe (no own same-axis scroll).
 */
@Composable
fun ColumnScope.SheetScrollBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.then(sheetFlexible()).sheetVerticalScroll(), content = content)
}

/**
 * The bounded scroll viewport every sheet body puts its list in, with the bottom fade over it.
 *
 * The bound lives HERE, on an ancestor of the scrolling content, and never in the content's own
 * `modifier` — `AbListChoiceDialog.kt:47-55` records why: a `heightIn` that lands inside
 * `verticalScroll`'s child clamps the inner column instead of the viewport, collapsing the scroll
 * range to 0 and making everything past the bound permanently unreachable rather than merely
 * scroll-capped.
 *
 * [canScrollForward] is a LAMBDA and that is load-bearing, not style: `abBottomFade` reads it in the
 * DRAW phase. A `Boolean` parameter would be evaluated during composition, before the content had
 * measured, so `canScrollForward` would still be false and the fade would be missing from exactly
 * the first frame that has to show it (`AbBottomFade.kt:26-30`).
 *
 * The fade's colour is `surfaceContainerLow` because that is what `BottomSheetDefaults.ContainerColor`
 * resolves to — a Material ROLE, not a hue, so it greys correctly in the BW and e-ink display modes
 * with no special-casing.
 */
@Composable
fun ColumnScope.AbSheetScrollBound(
    canScrollForward: () -> Boolean,
    maxHeight: Dp = AbSheetContentMaxHeight,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Flexible inside a capped sheet: rows after this region (a footer) are measured first.
            .then(sheetFlexible())
            .heightIn(max = maxHeight)
            .abBottomFade(color = MaterialTheme.colorScheme.surfaceContainerLow, visible = canScrollForward),
    ) {
        content()
    }
}

/**
 * Content of a dialog window opened from inside a sheet: a dialog is not under the sheet's half-window cap, so it
 * resets [LocalAbSheetHeightCapped] (inherited through the composition) to false. Otherwise its [sheetFlexible] and
 * [sheetVerticalScroll] children would act as if in a capped sheet column (#4207 review). Every dialog root wraps
 * its window content in this.
 */
@Composable
fun OutsideSheetCap(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAbSheetHeightCapped provides false, content = content)
}
