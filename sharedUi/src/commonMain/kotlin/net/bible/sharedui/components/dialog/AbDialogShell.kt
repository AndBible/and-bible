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

package net.bible.sharedui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedui.components.NoDialogDim
import net.bible.sharedui.components.TrackOverlay
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoBorder
import net.bible.sharedui.theme.monoInk

/** Margin kept between the dialog and the screen's left and right edges. */
private val ScreenMargin = 16.dp

/** Margin kept above and below the dialog, so its corners and border stay clear of system bars and cutouts. */
private val ScreenMarginVertical = 24.dp

/** Windows shorter than this use the compact frame (see [AbDialogShell]). */
private val CompactWindowHeight = 420.dp
private val CompactMarginVertical = 8.dp
private val CompactPaddingTop = 12.dp
private val CompactPaddingBottom = 8.dp

/** Smallest height reserved for the middle area (detail + content) when there is content. */
private val MiddleMinHeight = 56.dp

private enum class ShellSlot { Icon, Lead, Middle, Buttons }

/**
 * The shell of the redesigned dialogs: icon, lead line, a scrolling middle area and an end-aligned button row.
 *
 * Parameters:
 *  - [onDismissRequest]: called for a scrim click or Back, when [properties] allow them;
 *  - [buttons]: the button row content, laid out in an end-aligned `FlowRow` that wraps on overflow;
 *  - [icon]: centred above the lead (the notice dialog's logo);
 *  - [lead]: the question or headline; it is shown in [abDialogLeadStyle];
 *  - [content]: the middle area (detail + body), shown in [abDialogBodyStyle]; it scrolls;
 *  - [properties]: only `dismissOnBackPress` and `dismissOnClickOutside` are forwarded to the platform dialog.
 *    The shell always sets `usePlatformDefaultWidth = false` (the width is ours), and ignores the other fields.
 *
 * Height safety, in order of priority (one custom layout pass decides all of it):
 *  1. the button row is measured first and is never capped and never scrolls;
 *  2. the middle area ([content]) keeps at least [MiddleMinHeight];
 *  3. the lead yields first: its cap is `min(40% of the available height, what remains after the button row, the
 *     middle minimum, the icon and the gaps)`, and it scrolls inside that cap;
 *  4. windows shorter than 420dp use a compact frame (8dp margin, 12dp top and 8dp bottom padding);
 *  5. the middle area takes all remaining height and scrolls, with dividers at the edges that can scroll further.
 * The surface is bounded to the window's height, so the layout always has a finite bound to divide.
 *
 * Hosted on a plain [Dialog] with `usePlatformDefaultWidth = false`, so the width is
 * [AbDialogTokens.MinWidth]..[AbDialogTokens.MaxWidth], never wider than the screen minus a margin.
 * In pure monochrome the surface is paper with an ink border, no tonal elevation and no platform dim.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbDialogShell(
    onDismissRequest: () -> Unit,
    buttons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    lead: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    TrackOverlay()
    val mono = isPureMonochrome()
    val shape = RoundedCornerShape(AbDialogTokens.Corner)
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside,
        usePlatformDefaultWidth = false,
    )) {
        if (mono) NoDialogDim()
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // Short windows (landscape phones) use a compact frame: smaller margin above/below and tighter padding,
            // so that three AX5 buttons plus the middle minimum still fit.
            val compact = maxHeight.value.isFinite() && maxHeight < CompactWindowHeight
            val marginV = if (compact) CompactMarginVertical else ScreenMarginVertical
            val padTop = if (compact) CompactPaddingTop else AbDialogTokens.PaddingTop
            val padBottom = if (compact) CompactPaddingBottom else AbDialogTokens.PaddingBottom
            val available: Dp = if (maxHeight.value.isFinite()) (maxHeight - marginV * 2).coerceAtLeast(120.dp) else 600.dp
            val maxDialogWidth = (maxWidth - ScreenMargin * 2).coerceAtMost(AbDialogTokens.MaxWidth)
            val minDialogWidth = AbDialogTokens.MinWidth.coerceAtMost(maxDialogWidth)
            Surface(
                modifier = modifier
                    .widthIn(min = minDialogWidth, max = maxDialogWidth)
                    .heightIn(max = available)
                    .monoBorder(shape),
                shape = shape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (mono) 0.dp else 6.dp,
            ) {
                val leadCapFraction = available * AbDialogTokens.LeadMaxHeightFraction
                Layout(
                    modifier = Modifier.padding(
                        start = AbDialogTokens.PaddingSide,
                        end = AbDialogTokens.PaddingSide,
                        top = padTop,
                        bottom = padBottom,
                    ),
                    content = {
                        if (icon != null) Box(Modifier.layoutId(ShellSlot.Icon)) { icon() }
                        if (lead != null) {
                            Box(Modifier.layoutId(ShellSlot.Lead).testTag("dialog-lead").semantics { heading() }) {
                                Box(Modifier.verticalScroll(rememberScrollState())) {
                                    ProvideTextStyle(abDialogLeadStyle()) { lead() }
                                }
                            }
                        }
                        if (content != null) {
                            val scrollState = rememberScrollState()
                            Column(Modifier.layoutId(ShellSlot.Middle)) {
                                if (scrollState.canScrollBackward) ScrollDivider("dialog-divider-top")
                                Column(
                                    Modifier.weight(1f, fill = false).testTag("dialog-middle").verticalScroll(scrollState),
                                ) {
                                    ProvideTextStyle(abDialogBodyStyle()) { content() }
                                }
                                if (scrollState.canScrollForward) ScrollDivider("dialog-divider-bottom")
                            }
                        }
                        Box(Modifier.layoutId(ShellSlot.Buttons)) {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) { buttons() }
                        }
                    },
                ) { measurables, constraints ->
                    fun slot(s: ShellSlot) = measurables.firstOrNull { it.layoutId == s }
                    val loose = constraints.copy(minWidth = 0, minHeight = 0)
                    val width = constraints.maxWidth
                    val total = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE / 2
                    val gapLead = AbDialogTokens.GapLeadToBody.roundToPx()
                    val gapButtons = AbDialogTokens.GapAboveButtons.roundToPx()

                    // 1. Buttons: never capped, never scrolling.
                    val buttonsP = slot(ShellSlot.Buttons)!!.measure(loose.copy(maxHeight = total))
                    var used = buttonsP.height + gapButtons
                    // Icon: fixed.
                    val iconP = slot(ShellSlot.Icon)?.measure(loose.copy(maxHeight = (total - used).coerceAtLeast(0)))
                    if (iconP != null) used += iconP.height + gapLead
                    // 2. Middle minimum: the lead must leave this much (or the middle's natural height if smaller).
                    val middleM = slot(ShellSlot.Middle)
                    // A fixed reserve: intrinsic measurement is not allowed for content that uses
                    // SubcomposeLayout (BoxWithConstraints, lazy lists), which dialog content often does.
                    val middleReserve = if (middleM == null) 0 else MiddleMinHeight.roundToPx()
                    // 3. Lead yields first.
                    val leadM = slot(ShellSlot.Lead)
                    val gapMiddle = if (leadM != null && middleM != null) gapLead else 0
                    val leadCap = minOf(leadCapFraction.roundToPx(), total - used - middleReserve - gapMiddle).coerceAtLeast(0)
                    val leadP = leadM?.measure(loose.copy(maxHeight = leadCap))
                    if (leadP != null) used += leadP.height + gapMiddle
                    // 4. The middle area takes the rest.
                    val middleP = middleM?.measure(loose.copy(maxHeight = (total - used).coerceAtLeast(0)))
                    val h = used + (middleP?.height ?: 0)
                    layout(width, h.coerceAtMost(total)) {
                        var y = 0
                        iconP?.let { it.placeRelative((width - it.width) / 2, y); y += it.height + gapLead }
                        leadP?.let { it.placeRelative(0, y); y += it.height + gapMiddle }
                        middleP?.let { it.placeRelative(0, y); y += it.height }
                        buttonsP.placeRelative(0, y + gapButtons)
                    }
                }
            }
        }
    }
}

/** A 1dp rule shown at an edge of the middle area while more content can be scrolled to in that direction. */
@Composable
private fun ScrollDivider(tag: String) {
    val color = if (isPureMonochrome()) monoInk(LocalIsDarkTheme.current) else MaterialTheme.colorScheme.outline
    Box(Modifier.fillMaxWidth().height(1.dp).background(color).testTag(tag))
}

/** The detail line of a dialog: a consequence, in [abDialogDetailStyle]. */
@Composable
fun AbDialogDetail(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.testTag("dialog-detail"), style = abDialogDetailStyle())
}
