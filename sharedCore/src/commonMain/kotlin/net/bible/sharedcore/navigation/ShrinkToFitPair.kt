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
package net.bible.sharedcore.navigation

/**
 * The result of [shrinkToFitPair]: the two chips' assigned widths, plus the gap actually applied
 * between them. [gap] is clamped down to fit within [available] just like the widths are — it is
 * never larger than `available` on its own — so `languageWidth + typeWidth + gap <= available`
 * holds BY CONSTRUCTION for every input, not by an after-the-fact guard clause, however narrow
 * `available` is (including narrower than the nominal gap, which cannot happen on a real device
 * but must not violate the invariant a layout depends on).
 */
data class ShrinkToFitWidths(val languageWidth: Int, val typeWidth: Int, val gap: Int)

/**
 * Pure sizing decision behind [net.bible.sharedui.navigation.DocumentFilterBar]'s two filter
 * chips: given both chips' natural (intrinsic, unconstrained) widths and the width actually
 * available, decide how much width each gets.
 *
 * Both chips get their full natural width whenever they fit together (with [gap] between them).
 * Only when they don't does either shrink — and then only by a share of the shortfall (the
 * amount by which the combined natural width overshoots [available]), not by a share of the
 * whole row. [languageShareOfShortfall] biases which chip gives up more of that shortfall
 * (defaults to 2/3 to language, matching [net.bible.sharedui.navigation.DocumentFilterBar]:
 * language display names are the more variable, often-longer values; the type filter's label set
 * is small, bounded and mostly short). Neither width is ever cut below zero; if one chip's
 * assigned cut would take it below zero, the other chip absorbs the leftover shortfall instead of
 * that shortfall being silently dropped.
 *
 * This deliberately is NOT `Modifier.weight`: weight caps every weighted child's max width to a
 * fixed proportion of the row regardless of what its sibling actually needs, which either lets
 * one child's full, unconditional width starve the other to nothing, or truncates a child that
 * would otherwise fit with room to spare. See `DocumentFilterBar.kt`'s call-site kdoc for the two
 * rejected `Modifier.weight` attempts this replaced.
 *
 * [minWidth] is a per-chip floor applied only while a chip is actively being cut (a chip whose
 * natural width is already narrower than [minWidth] — e.g. a short "All" label — is never padded
 * up to it). Without a floor, a sufficiently long language name can shrink the type chip to a
 * single ambiguous character ("A…" for both "All types" and "Add-ons") — a filter that hides its
 * own state, which is exactly the defect this control exists to fix. The floor is honored by
 * taking the shortfall from the OTHER chip first, same direction as the existing
 * natural-width-cap redistribution below; it is relaxed back to the floor-free behaviour only
 * when honoring both floors together genuinely would not fit in [available] — a floor that cannot
 * be met is not a bound this function can enforce without breaking the stronger
 * `languageWidth + typeWidth + gap <= available` invariant.
 *
 * Extracted as a pure function (no Compose types) so its branching arithmetic — in particular the
 * clamp-and-redistribute logic — can be host-tested directly: [ShrinkToFitPairTest] covers each
 * branch plus a swept-input invariant check, none of which a golden image (which renders exactly
 * one state) can exercise.
 */
fun shrinkToFitPair(
    available: Int,
    gap: Int,
    languageNatural: Int,
    typeNatural: Int,
    languageShareOfShortfall: Float = 2f / 3f,
    minWidth: Int = 0,
): ShrinkToFitWidths {
    // Delegates to the n-ary form (round 17e) so there is exactly ONE implementation of the
    // shrink-and-redistribute arithmetic. This signature and ShrinkToFitPairTest are kept as the
    // regression anchor for the tuned 2/3 : 1/3 asymmetry.
    val out = shrinkToFitChips(
        available = available,
        gap = gap,
        naturals = listOf(languageNatural, typeNatural),
        shortfallWeights = listOf(languageShareOfShortfall, 1f - languageShareOfShortfall),
        minWidth = minWidth,
    )
    return ShrinkToFitWidths(out.widths[0], out.widths[1], out.gap)
}

/** [shrinkToFitChips]'s result: each chip's assigned width, plus the gap actually applied. */
data class ChipWidths(val widths: List<Int>, val gap: Int)

/**
 * The n-chip generalisation of [shrinkToFitPair]. Every chip gets its natural width whenever they
 * all fit; only when they do not does any chip shrink, and then by its [shortfallWeights] share of
 * the SHORTFALL rather than of the whole row.
 *
 * The two-chip case must remain byte-identical to [shrinkToFitPair]'s tuned behaviour, which is
 * why the cut is computed the same way: chips 0..n-2 take `floor(shortfall * weight)`, the LAST
 * chip absorbs the exact remainder, each is clamped to its own room above the floor, and any still
 * unassigned shortfall is handed back in index order. `ShrinkToFitPairTest` runs unmodified against
 * the delegate below and is the anchor for that equivalence.
 */
fun shrinkToFitChips(
    available: Int,
    gap: Int,
    naturals: List<Int>,
    shortfallWeights: List<Float>,
    minWidth: Int = 0,
): ChipWidths {
    require(naturals.size == shortfallWeights.size) { "one weight per chip" }
    if (naturals.isEmpty()) return ChipWidths(emptyList(), 0)
    val safeAvailable = available.coerceAtLeast(0)
    val gaps = naturals.size - 1
    // The gaps themselves can never exceed what is available: if they did, no non-negative widths
    // could keep the total within `available` at all. Clamping here is what makes the
    // sum(widths) + gap*gaps <= available invariant hold by construction.
    val effectiveGap = if (gaps == 0) 0 else gap.coerceIn(0, safeAvailable / gaps)
    val widthBudget = safeAvailable - effectiveGap * gaps
    val needed = naturals.sum()
    if (needed <= widthBudget) return ChipWidths(naturals, effectiveGap)

    val safeMin = minWidth.coerceAtLeast(0)
    val floors = naturals.map { safeMin.coerceAtMost(it) }
    // Honouring every floor is only possible when the floors themselves fit; when they do not,
    // honouring them would leave part of the shortfall uncut and break the stronger
    // "never claim more than available" invariant, so the floor is dropped entirely.
    val canHonorFloors = widthBudget >= floors.sum()
    val mins = if (canHonorFloors) floors else naturals.map { 0 }

    val shortfall = (needed - widthBudget).coerceAtMost(needed)
    val cuts = IntArray(naturals.size)
    var assigned = 0
    for (i in naturals.indices) {
        val want = if (i == naturals.lastIndex) shortfall - assigned else (shortfall * shortfallWeights[i]).toInt()
        cuts[i] = want.coerceIn(0, naturals[i] - mins[i])
        assigned += cuts[i]
    }
    // Whatever a clamp refused is handed back in index order, so a shortfall is never silently
    // dropped just because one chip had no room for its proportional share.
    var owed = shortfall - assigned
    var i = 0
    while (owed > 0 && i < naturals.size) {
        val room = naturals[i] - mins[i] - cuts[i]
        val take = owed.coerceAtMost(room.coerceAtLeast(0))
        cuts[i] += take
        owed -= take
        i++
    }
    return ChipWidths(naturals.mapIndexed { idx, n -> (n - cuts[idx]).coerceAtLeast(0) }, effectiveGap)
}

/**
 * How a filter bar lays out: each chip's width, the gap, and whether the result count had to move
 * to a second row underneath the chips.
 */
data class FilterBarLayout(val chipWidths: List<Int>, val gap: Int, val countOnSecondRow: Boolean)

/**
 * Decide a filter bar's layout: chips, a trailing result count and a trailing "more filters" icon
 * button.
 *
 * The count moves to a second row ONLY when keeping it on the first row would force a chip to
 * truncate — on a roomy phone everything stays on one line, on a narrow one the count drops below
 * and the chips keep their full labels. A chip that hides its own current value is a filter the
 * user cannot read, which is worse than one extra line of chrome; the count is the one element
 * here that reads identically wherever it sits.
 *
 * The icon button never moves: it is fixed-width and is the row's affordance.
 */
fun filterBarLayout(
    available: Int,
    gap: Int,
    chipNaturals: List<Int>,
    shortfallWeights: List<Float>,
    countWidth: Int,
    tuneWidth: Int,
    minChipWidth: Int,
): FilterBarLayout {
    val oneRowBudget = available - tuneWidth - countWidth - gap * 2
    val needed = chipNaturals.sum() + gap * (chipNaturals.size - 1).coerceAtLeast(0)
    if (needed <= oneRowBudget) {
        return FilterBarLayout(chipNaturals, gap, countOnSecondRow = false)
    }
    val twoRowBudget = available - tuneWidth - gap
    val widths = shrinkToFitChips(
        available = twoRowBudget,
        gap = gap,
        naturals = chipNaturals,
        shortfallWeights = shortfallWeights,
        minWidth = minChipWidth,
    )
    return FilterBarLayout(widths.widths, widths.gap, countOnSecondRow = true)
}
