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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens of the redesigned dialogs (spec 2026-11-20-dialog-redesign, sections 4 and 5). */
object AbDialogTokens {
    val LeadSize = 18.sp
    val LeadLineHeight = 25.sp
    val BodySize = 16.sp
    val BodyLineHeight = 23.sp
    val DetailSize = 14.sp
    val DetailLineHeight = 20.sp

    val Corner = 20.dp
    val PaddingSide = 24.dp
    val PaddingTop = 22.dp
    val PaddingBottom = 14.dp
    val GapLeadToBody = 10.dp
    val GapAboveButtons = 18.dp
    val MinWidth = 280.dp
    val MaxWidth = 560.dp

    val CardCorner = 14.dp
    val CardPaddingV = 11.dp
    val CardPaddingH = 14.dp
    val CardLabelSize = 15.sp
    val CardConsequenceSize = 13.sp

    /** The lead line may take at most this fraction of the available height. */
    const val LeadMaxHeightFraction = 0.4f
}

/** The lead line: the question or headline of a dialog. */
@Composable
fun abDialogLeadStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontSize = AbDialogTokens.LeadSize,
    lineHeight = AbDialogTokens.LeadLineHeight,
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.onSurface,
)

/** Body text of a dialog. */
@Composable
fun abDialogBodyStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontSize = AbDialogTokens.BodySize,
    lineHeight = AbDialogTokens.BodyLineHeight,
    color = MaterialTheme.colorScheme.onSurface,
)

/** The secondary consequence/detail line of a dialog. */
@Composable
fun abDialogDetailStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontSize = AbDialogTokens.DetailSize,
    lineHeight = AbDialogTokens.DetailLineHeight,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
