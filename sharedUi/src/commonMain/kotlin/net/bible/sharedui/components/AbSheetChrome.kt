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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings

/**
 * How tall a new sheet's scrolling region may grow. 400dp is round 12c's shipped cap
 * (`SettingsEditorSheet.kt:252`) and round 13a's (`SpeakSettingsSheet.kt:122`) — the only value in
 * this port with device evidence behind it. A larger cap overflows the sheet in landscape (window
 * height ~360dp) and clips a scrolling list's bottom out of reach.
 *
 * The three bodies this round converts IN PLACE (`PromptSelectorSheetContent`,
 * `ModelSelectionSheetContent`, `AbReadHistorySheetContent`) deliberately keep their own existing
 * 480dp bound instead: the spec's brief for them is "same content, same bound", so the only thing
 * that changes for those three is the container.
 */
val AbSheetContentMaxHeight: Dp = 400.dp

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
        if (showClose) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = strings.settingsEditorClose)
            }
        }
    }
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
fun AbSheetScrollBound(
    canScrollForward: () -> Boolean,
    maxHeight: Dp = AbSheetContentMaxHeight,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .abBottomFade(color = MaterialTheme.colorScheme.surfaceContainerLow, visible = canScrollForward),
    ) {
        content()
    }
}
