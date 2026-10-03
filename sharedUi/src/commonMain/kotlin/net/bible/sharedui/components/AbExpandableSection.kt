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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.bible.sharedui.theme.LocalDisableAnimations

/**
 * A titled section that collapses, for a form whose later groups are usually irrelevant.
 *
 * The header carries [indicators] rather than a summary sentence: a collapsed section has to be
 * able to say whether anything inside it is set, and small marks (the same ones the screen uses
 * elsewhere) do that with no new translated string. Callers hoist [expanded] so a golden can
 * photograph both states and so the screen can decide the initial value.
 *
 * The whole header row is the toggle — a chevron-only hit target on a form row is too small, and
 * there is nothing else on the row to compete with the tap. It carries [Role.Button] plus the
 * semantics `expand`/`collapse` actions (fix round 1, Finding 3): TalkBack then announces it as a
 * disclosure control in the current expand/collapsed state, entirely in the platform's own
 * announcement language -- unlike a `stateDescription`, this needs no new app string.
 *
 * Header padding matches `LabelEditScreen`'s other top-level headings at 16dp (round 17b, replacing
 * the earlier "matches AbSwitchRow" rule from fix round 1 Finding 2) -- the header sits OUTSIDE the
 * indent of the [AbSwitchRow]s it discloses, at 32dp, so the nesting reads as nesting instead of the
 * header looking like just another row in the list.
 *
 * The chevron is at the TRAILING edge (round 17b): a disclosure control belongs where the eye
 * finishes the title, and the leading edge is where `LabelEditScreen`'s other headings start, which
 * is what made a leading chevron read as an extra indent.
 *
 * The reveal reads [LocalDisableAnimations] (fix round 1, Finding 1), the same local
 * [net.bible.sharedui.reading.BibleReferenceOverlay] reads, and collapses to a 0ms transition when
 * the user has turned animations off, instead of the normal 220ms -- same duration family as that
 * precedent.
 */
@Composable
fun AbExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    indicators: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val disableAnim = LocalDisableAnimations.current
    val animDurationMillis = if (disableAnim) 0 else 220
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics {
                    if (expanded) collapse { onToggle(); true } else expand { onToggle(); true }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            indicators()
            // Round 17b: the chevron sits at the TRAILING edge. Weight on this spacer, not on any
            // child: an unweighted child measures first, so the title and the indicators keep their
            // intrinsic widths and the chevron stays pinned right. It is not the last child by
            // accident either — a weighted TRAILING child would make the chevron the overflow
            // casualty, and a clipped icon still takes hit-test area.
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                // 20dp: both production call sites (LabelEditScreen.kt) pass 16dp [indicators]
                // icons, so the chevron is the larger icon on this row. It reads as a disclosure
                // affordance without competing with the smaller marks beside it.
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(durationMillis = animDurationMillis)) +
                fadeIn(animationSpec = tween(durationMillis = animDurationMillis)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = animDurationMillis)) +
                fadeOut(animationSpec = tween(durationMillis = animDurationMillis)),
        ) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}
