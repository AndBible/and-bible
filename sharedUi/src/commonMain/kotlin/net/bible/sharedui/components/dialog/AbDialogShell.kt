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
import androidx.compose.foundation.layout.Spacer
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

/**
 * The shell of the redesigned dialogs: icon, lead line, a scrolling middle area and an end-aligned button row.
 *
 * Height safety, in order of priority:
 *  - the button row never scrolls away: it is outside the scrolling middle area (it is bounded to 40% of the
 *    height like the lead, and scrolls only past that, at absurd font scales);
 *  - the lead is capped at [AbDialogTokens.LeadMaxHeightFraction] of the available height and scrolls inside that cap.
 *    The cap is a `heightIn` on an ANCESTOR of the `verticalScroll` (a bound set on the scrolling node itself leaves
 *    its content unbounded and the cap does nothing);
 *  - [content] (detail + body) shares a weighted middle area that takes whatever height remains and scrolls.
 *    The surface itself is bounded to the window's height, so the weight has a finite bound to work against.
 *
 * Hosted on a plain [Dialog] with `usePlatformDefaultWidth = false`, so the width is ours:
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
            val available: Dp = if (maxHeight.value.isFinite()) (maxHeight - ScreenMarginVertical * 2).coerceAtLeast(120.dp) else 600.dp
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
                Column(
                    Modifier.padding(
                        start = AbDialogTokens.PaddingSide,
                        end = AbDialogTokens.PaddingSide,
                        top = AbDialogTokens.PaddingTop,
                        bottom = AbDialogTokens.PaddingBottom,
                    ),
                ) {
                    if (icon != null) {
                        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = AbDialogTokens.GapLeadToBody)) { icon() }
                    }
                    if (lead != null) {
                        Box(Modifier.heightIn(max = available * AbDialogTokens.LeadMaxHeightFraction).testTag("dialog-lead").semantics { heading() }) {
                            Box(Modifier.verticalScroll(rememberScrollState())) {
                                ProvideTextStyle(abDialogLeadStyle()) { lead() }
                            }
                        }
                    }
                    if (content != null) {
                        if (lead != null) Spacer(Modifier.height(AbDialogTokens.GapLeadToBody))
                        val scrollState = rememberScrollState()
                        Column(Modifier.weight(1f, fill = false)) {
                            if (scrollState.canScrollBackward) ScrollDivider("dialog-divider-top")
                            Column(
                                Modifier.weight(1f, fill = false).testTag("dialog-middle").verticalScroll(scrollState),
                            ) {
                                ProvideTextStyle(abDialogBodyStyle()) { content() }
                            }
                            if (scrollState.canScrollForward) ScrollDivider("dialog-divider-bottom")
                        }
                    }
                    Spacer(Modifier.height(AbDialogTokens.GapAboveButtons))
                    // Last-resort bound: lead (40%) + buttons (40%) + paddings always leave the middle area a
                    // share of the window, so a huge font scale cannot push the buttons out of the surface.
                    Box(Modifier.heightIn(max = available * AbDialogTokens.LeadMaxHeightFraction)) {
                        Box(Modifier.verticalScroll(rememberScrollState())) {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) { buttons() }
                        }
                    }
                }
            }
        }
    }
}

/** A 1dp rule shown at an edge of the middle area while more content can be scrolled to in that direction. */
@Composable
private fun ScrollDivider(tag: String) {
    val color = if (isPureMonochrome()) monoInk(LocalIsDarkTheme.current) else MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxWidth().height(1.dp).background(color).testTag(tag))
}

/** The detail line of a dialog: a consequence, in [abDialogDetailStyle]. */
@Composable
fun AbDialogDetail(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.testTag("dialog-detail"), style = abDialogDetailStyle())
}
