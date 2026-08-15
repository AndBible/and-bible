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
package net.bible.sharedui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.navigation.iconCategory
import net.bible.sharedcore.navigation.shrinkToFitPair
import net.bible.sharedui.components.AbSearchableOptionSheet
import net.bible.sharedui.strings.LocalStrings

/**
 * The document-selection filter row: two chips that each open their own bottom sheet, plus the
 * result count. Replaces the previous tappable "Finnish · All types" summary row and the two
 * full-width controls it expanded to; the chips carry the current value, so nothing is hidden.
 *
 * Exactly one sheet can be open at a time (a single [openSheet] state), which preserves the
 * screen's no-nested-bottom-sheets invariant.
 *
 * Deliberately search-agnostic: it takes no query state. Moving the search field into the
 * toolbar is a separate, later change that must not have to touch this file.
 */
@Composable
fun DocumentFilterBar(
    languages: List<LangOption>,
    selectedLanguage: LangOption?,
    onLanguageChange: (LangOption?) -> Unit,
    typeFilters: List<Pair<DocTypeFilter, String>>,
    selectedTypeFilter: DocTypeFilter,
    onTypeFilterChange: (DocTypeFilter) -> Unit,
    resultCount: String,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var openSheet by remember { mutableStateOf(FilterSheet.None) }

    val languageOptions: List<LangOption?> = remember(languages) { listOf<LangOption?>(null) + languages }
    val selectedTypePair = typeFilters.firstOrNull { it.first == selectedTypeFilter }
        ?: (selectedTypeFilter to selectedTypeFilter.name)

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The chips live in their own shrink-to-fit pair so THEY absorb the overflow. A trailing
        // weighted Spacer would instead make the LAST child — the result count — the casualty.
        //
        // Round-1 review history worth keeping: a plain `Modifier.weight` split was tried twice
        // here and rejected both times. A single weighted language chip (unweighted type) let an
        // unconditionally-full-width type chip starve the language chip to LITERAL ZERO width
        // under a long language name — its label vanished, not even an ellipsis. Weighting BOTH
        // chips fixed that, but a flat weight ratio (tried 2:1, then 1:1) caps each chip's max
        // width to a FIXED proportion of the row regardless of what its sibling actually needs —
        // so on an ordinary "All" + "All types" row, `Modifier.weight` truncated "All types" to
        // "All ty…" even though "All" left visible slack unused (Row weight never redistributes
        // a sibling's unused share). [ShrinkingChipPair] instead measures both chips at their
        // natural (intrinsic) width first and only imposes a tighter constraint — splitting the
        // shortfall, not the whole row — when the combined natural width would not fit.
        ShrinkingChipPair(
            modifier = Modifier.weight(1f),
            language = {
                AssistChip(
                    onClick = { openSheet = FilterSheet.Language },
                    label = {
                        Text(
                            selectedLanguage?.displayName ?: strings.all,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Language,
                            contentDescription = strings.languageLabel,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                    },
                )
            },
            type = {
                AssistChip(
                    onClick = { openSheet = FilterSheet.Type },
                    // The content description lives on the chip itself, not on the leading icon
                    // slot: that slot disappears entirely when the filter is ALL (no reserved-slot
                    // spacer here, unlike the sheet's list), so a description hung off it went
                    // silent in the chip's default state -- the type chip announced only its value,
                    // never that it WAS a type filter. Attaching it here means the chip's
                    // accessible label doesn't depend on which of its slots happen to be present.
                    modifier = Modifier.semantics { contentDescription = strings.documentTypeLabel },
                    label = { Text(selectedTypePair.second, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = if (selectedTypeFilter.iconCategory == null) {
                        // No reserved-slot spacer here: that's only needed in the sheet's LIST, to
                        // keep every row's label aligned. A single chip has nothing to align
                        // against, so dropping the slot entirely reclaims real width for the label.
                        null
                    } else {
                        // Chip-sized icon (AssistChipDefaults.IconSize), NOT TypeFilterIconSize:
                        // that larger size is only right for the sheet's ListItem leading slot.
                        // contentDescription = null: the chip-level semantics above already carries
                        // the label, so the icon doesn't need (and shouldn't duplicate) its own.
                        { TypeFilterIcon(selectedTypeFilter, contentDescription = null, size = AssistChipDefaults.IconSize) }
                    },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                    },
                )
            },
        )
        Text(
            text = resultCount,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }

    when (openSheet) {
        FilterSheet.None -> Unit
        FilterSheet.Language -> AbSearchableOptionSheet(
            options = languageOptions,
            selected = selectedLanguage,
            optionLabel = { it?.displayName ?: strings.all },
            onSelect = { onLanguageChange(it); openSheet = FilterSheet.None },
            onDismiss = { openSheet = FilterSheet.None },
            searchPlaceholder = strings.search,
        )
        FilterSheet.Type -> AbSearchableOptionSheet(
            options = typeFilters,
            selected = selectedTypePair,
            optionLabel = { it.second },
            onSelect = { onTypeFilterChange(it.first); openSheet = FilterSheet.None },
            onDismiss = { openSheet = FilterSheet.None },
            searchPlaceholder = null, // seven items; a search field would be noise
            leadingIcon = { TypeFilterIcon(it.first, contentDescription = null) },
        )
    }
}

private enum class FilterSheet { None, Language, Type }

/**
 * Lays [language] and [type] out side by side at their natural (intrinsic) width whenever both
 * fit; only when the combined natural width exceeds the available width does either shrink —
 * and then only by its share of the SHORTFALL, not of the whole row. This is deliberately not a
 * plain `Row` with `Modifier.weight` on each child: weight caps every weighted child's width to a
 * fixed proportion of the row regardless of what its sibling needs, so it either lets one child's
 * full, unconditional width starve the other to nothing (a single weighted child) or truncates a
 * child that would otherwise fit with room to spare (both weighted equally) — see the call site's
 * kdoc for the two rejected attempts this replaced.
 *
 * The actual sizing decision is [shrinkToFitPair], a pure function with no Compose types — this
 * composable does nothing but measure the two children at their natural width, call it, then
 * measure and place them at the widths (and gap) it returns. Keeping the arithmetic out of the
 * `Layout` block is what makes it host-testable at all: a golden image renders exactly one state,
 * not the branch/clamp logic behind it.
 *
 * [minWidth] is [shrinkToFitPair]'s per-chip floor, converted to px here since that function is
 * pure Compose-free arithmetic. Without it, a long language name can shrink the type chip down to
 * a single ambiguous character ("A…" for both "All types" and "Add-ons") — a filter that hides its
 * own state.
 */
@Composable
private fun ShrinkingChipPair(
    language: @Composable () -> Unit,
    type: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    languageShareOfShortfall: Float = 2f / 3f,
    minWidth: Dp = ChipMinWidth,
) {
    Layout(modifier = modifier, content = {
        language()
        type()
    }) { measurables, constraints ->
        val (languageMeasurable, typeMeasurable) = measurables
        val languageNatural = languageMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val typeNatural = typeMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val widths = shrinkToFitPair(
            available = constraints.maxWidth,
            gap = gap.roundToPx(),
            languageNatural = languageNatural,
            typeNatural = typeNatural,
            languageShareOfShortfall = languageShareOfShortfall,
            minWidth = minWidth.roundToPx(),
        )

        val languagePlaceable = languageMeasurable.measure(
            Constraints(maxWidth = widths.languageWidth, maxHeight = constraints.maxHeight),
        )
        val typePlaceable = typeMeasurable.measure(
            Constraints(maxWidth = widths.typeWidth, maxHeight = constraints.maxHeight),
        )
        val height = maxOf(languagePlaceable.height, typePlaceable.height)
        layout(constraints.maxWidth, height) {
            languagePlaceable.placeRelative(0, (height - languagePlaceable.height) / 2)
            typePlaceable.placeRelative(languagePlaceable.width + widths.gap, (height - typePlaceable.height) / 2)
        }
    }
}

/**
 * The leading icon for a document-type filter. [DocTypeFilter.ALL] spans every category and so
 * has no icon of its own — it renders an EMPTY slot of exactly [size], so the labels in the type
 * sheet stay aligned instead of shifting left on one row.
 *
 * [size] is caller-supplied rather than fixed: the sheet's `ListItem` leading slot and the chip's
 * `AssistChip` leading slot want different sizes ([TypeFilterIconSize] vs
 * [AssistChipDefaults.IconSize]), and using the larger, list-appropriate size inside the chip was
 * exactly what starved the chip of width for its own label.
 *
 * Public (not `private`) so [net.bible.android.view.compose.golden.DocumentFilterBarGoldenTest] —
 * in the separate `:app` module — can capture the type sheet through the SAME leading-icon lambda
 * production uses, rather than a hand-copied re-implementation of this reserved-slot rule that
 * could silently drift from it. `internal` is not enough here: Kotlin `internal` visibility is
 * scoped to the compilation module, and `:app` is a different Gradle module from `:sharedUi`.
 */
@Composable
fun TypeFilterIcon(filter: DocTypeFilter, contentDescription: String?, size: Dp = TypeFilterIconSize) {
    val category = filter.iconCategory
    if (category == null) {
        Spacer(Modifier.size(size))
    } else {
        Icon(
            painter = LocalCategoryIcon.current(category),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
        )
    }
}

private val TypeFilterIconSize = 24.dp

/**
 * Floor passed to [shrinkToFitPair] via [ShrinkingChipPair]: chip chrome (leading icon, trailing
 * arrow, their gaps to the label, and the chip's own horizontal content padding) plus roughly four
 * characters of label at [AssistChip]'s default text style. Prevents the type chip in particular
 * from shrinking to a single ambiguous character ("A…" reads as both "All types" and "Add-ons") —
 * see finding Important-3 in the round-6 fix wave.
 *
 * 80dp, not a larger "safe" number: measured against `Download_filtersLongLanguageName`'s actual
 * inputs (a 320dp-wide device, "Portuguese (Brazil)" selected), the two chips only have ~195px of
 * combined budget once the result count and screen padding are subtracted. A floor much above 80dp
 * would make `2 * minWidth` exceed that budget, and [shrinkToFitPair] relaxes the floor back to
 * floor-free behaviour entirely rather than violate the stronger "never claim more than available"
 * invariant — so an over-generous floor constant would silently stop protecting the exact golden
 * this fix exists for. At 80dp the type chip still lands with ~4-5 characters of legible label
 * ("All t…"/"Add-…") instead of one.
 */
private val ChipMinWidth = 80.dp
