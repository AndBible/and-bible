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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.filterBarLayout
import net.bible.sharedui.strings.LocalStrings

/**
 * One chip in an [AbFilterChipBar]. [shortfallWeight] is this chip's share of any width shortfall
 * — the weights across a bar should sum to 1. The document bar keeps the tuned 2/3 : 1/3 split
 * (language names are the long, variable ones; the type label set is short and bounded).
 */
data class AbFilterChip(
    val label: String,
    val contentDescription: String?,
    val leadingIcon: (@Composable () -> Unit)?,
    val shortfallWeight: Float,
    val onClick: () -> Unit,
)

/**
 * A filter bar: N chips, a result count, and an optional "more filters" ICON button (no label —
 * a text button would eat the width the chips need on a narrow phone).
 *
 * The count is responsive: it sits on the chip row when everything fits at natural width, and
 * drops to a second line under the chips when keeping it there would truncate a chip. The decision
 * is [filterBarLayout], a pure function with no Compose types; this composable only measures the
 * children at their intrinsic widths, calls it, and places what it returns. That split is what
 * makes the branch space testable at all — a golden renders exactly one state.
 *
 * [moreFiltersActive] draws a dot on the icon button, so a filter hidden inside the sheet is never
 * silently in effect.
 */
@Composable
fun AbFilterChipBar(
    chips: List<AbFilterChip>,
    resultCount: String,
    modifier: Modifier = Modifier,
    onMoreFilters: (() -> Unit)? = null,
    moreFiltersActive: Boolean = false,
) {
    val gap = 8.dp
    val minChipWidth = ChipMinWidth
    Layout(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        content = {
            chips.forEach { chip ->
                AssistChip(
                    onClick = chip.onClick,
                    modifier = if (chip.contentDescription != null) {
                        Modifier.semantics { contentDescription = chip.contentDescription }
                    } else Modifier,
                    label = { Text(chip.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = chip.leadingIcon,
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize))
                    },
                )
            }
            Text(
                text = resultCount,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (onMoreFilters != null) {
                Box {
                    IconButton(onClick = onMoreFilters) {
                        Icon(Icons.Filled.Tune, contentDescription = LocalStrings.current.docArrangeMoreFilters)
                    }
                    if (moreFiltersActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val chipMeasurables = measurables.take(chips.size)
        val countMeasurable = measurables[chips.size]
        val tuneMeasurable = measurables.getOrNull(chips.size + 1)

        val naturals = chipMeasurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val countWidth = countMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val tunePlaceable = tuneMeasurable?.measure(Constraints())
        val gapPx = gap.roundToPx()

        val layoutOut = filterBarLayout(
            available = constraints.maxWidth,
            gap = gapPx,
            chipNaturals = naturals,
            shortfallWeights = chips.map { it.shortfallWeight },
            countWidth = countWidth,
            tuneWidth = tunePlaceable?.width ?: 0,
            minChipWidth = minChipWidth.roundToPx(),
        )

        val chipPlaceables = chipMeasurables.mapIndexed { i, m ->
            m.measure(Constraints(maxWidth = layoutOut.chipWidths[i], maxHeight = constraints.maxHeight))
        }
        val countPlaceable = countMeasurable.measure(Constraints(maxWidth = constraints.maxWidth))
        val firstRowHeight = maxOf(
            chipPlaceables.maxOfOrNull { it.height } ?: 0,
            tunePlaceable?.height ?: 0,
            if (layoutOut.countOnSecondRow) 0 else countPlaceable.height,
        )
        val secondRowHeight = if (layoutOut.countOnSecondRow) countPlaceable.height + gapPx else 0

        layout(constraints.maxWidth, firstRowHeight + secondRowHeight) {
            var x = 0
            chipPlaceables.forEach { p ->
                p.placeRelative(x, (firstRowHeight - p.height) / 2)
                x += p.width + layoutOut.gap
            }
            if (!layoutOut.countOnSecondRow) {
                countPlaceable.placeRelative(x, (firstRowHeight - countPlaceable.height) / 2)
            }
            tunePlaceable?.placeRelative(
                constraints.maxWidth - tunePlaceable.width, (firstRowHeight - tunePlaceable.height) / 2,
            )
            if (layoutOut.countOnSecondRow) countPlaceable.placeRelative(0, firstRowHeight + gapPx)
        }
    }
}

/**
 * Per-chip floor passed to [filterBarLayout]: chip chrome (leading icon, trailing arrow, their gaps
 * and the chip's horizontal content padding) plus roughly four characters of label. 80dp, carried
 * over verbatim from the value `DocumentFilterBar` tuned against the 320dp
 * `Download_filtersLongLanguageName` golden — a larger "safer" floor makes `2 * minWidth` exceed
 * that device's real budget, at which point the sizing function relaxes the floor entirely and
 * stops protecting the very case it exists for.
 */
private val ChipMinWidth = 80.dp
