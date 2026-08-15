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
    val safeAvailable = available.coerceAtLeast(0)
    // The gap itself can never exceed what's available: if it did, no non-negative widths could
    // keep `languageWidth + typeWidth + gap` within `available` at all (both widths are already
    // floored at zero, so cutting them further cannot make room for an over-large gap). Clamping
    // it here — rather than trusting the caller's fixed nominal gap — is what makes the invariant
    // hold by construction rather than by hoping this case never comes up.
    val effectiveGap = gap.coerceIn(0, safeAvailable)
    val widthBudget = safeAvailable - effectiveGap
    val neededWidths = languageNatural + typeNatural

    val languageWidth: Int
    val typeWidth: Int
    if (neededWidths <= widthBudget) {
        // Both fit as-is -- nothing shrinks, nothing is truncated that doesn't need to be.
        languageWidth = languageNatural
        typeWidth = typeNatural
    } else {
        // A floor above a chip's own natural width would ask this function to GROW that chip,
        // which is not its job -- cap each chip's floor to what it naturally has.
        val safeMinWidth = minWidth.coerceAtLeast(0)
        val languageFloor = safeMinWidth.coerceAtMost(languageNatural)
        val typeFloor = safeMinWidth.coerceAtMost(typeNatural)
        // Both floors fit inside the budget together <=> there is enough room to cut every bit of
        // shortfall while respecting both floors (neededWidths - (languageFloor + typeFloor) is
        // exactly the max cut achievable while honoring both, and that is >= shortfall exactly
        // when this holds) -- so when it holds the floors are fully honorable, and when it does
        // not, honoring them at all would leave part of the shortfall uncut and violate the
        // top-level invariant, so the floor is dropped instead.
        val canHonorFloors = widthBudget >= languageFloor + typeFloor
        val languageMin = if (canHonorFloors) languageFloor else 0
        val typeMin = if (canHonorFloors) typeFloor else 0

        // Never ask the pair to give up more than they jointly have -- a shortfall bigger than
        // languageNatural + typeNatural is only satisfiable by cutting both to zero, not by
        // manufacturing negative width.
        val shortfall = (neededWidths - widthBudget).coerceAtMost(languageNatural + typeNatural)
        var languageCut = (shortfall * languageShareOfShortfall).toInt().coerceIn(0, languageNatural - languageMin)
        var typeCut = (shortfall - languageCut).coerceIn(0, typeNatural - typeMin)
        // If type's own room (down to its floor) couldn't cover what language's clamp left over,
        // the remaining shortfall goes BACK to language (which may still have room down to ITS
        // floor after its first cut) -- a one-directional handoff (language's leftover -> type
        // only) would silently drop this remainder whenever BOTH chips clamp, which is exactly the
        // shape of shortfall that a redistribution mechanism exists to handle in the first place.
        val stillOwed = shortfall - languageCut - typeCut
        if (stillOwed > 0) {
            languageCut += stillOwed.coerceAtMost(languageNatural - languageMin - languageCut)
        }
        languageWidth = (languageNatural - languageCut).coerceAtLeast(0)
        typeWidth = (typeNatural - typeCut).coerceAtLeast(0)
    }
    return ShrinkToFitWidths(languageWidth, typeWidth, effectiveGap)
}
