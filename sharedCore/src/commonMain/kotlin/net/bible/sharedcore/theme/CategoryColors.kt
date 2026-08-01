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

package net.bible.sharedcore.theme

/** The classic `getBookColorAndGroup` palette (ARGB), one per Bible-book category `0..9`. */
private val CATEGORY_BASE = intArrayOf(
    0xFFCCCCFE.toInt(), // 0 Pentateuch
    0xFFFECC9B.toInt(), // 1 History
    0xFF99FF99.toInt(), // 2 Wisdom
    0xFFFF99FF.toInt(), // 3 Major prophets
    0xFFFFFECD.toInt(), // 4 Minor prophets
    0xFFFF9703.toInt(), // 5 Gospel
    0xFF0099FF.toInt(), // 6 Acts
    0xFFFFFF31.toInt(), // 7 Pauline
    0xFF67CC66.toInt(), // 8 General epistles
    0xFFFE33FF.toInt(), // 9 Revelation
)

/** Classic's `OTHER_COLOR` — the Acts blue, reused for deuterocanonical/unknown books. */
private const val OTHER_BASE = 0xFF0099FF.toInt()

/**
 * The raw category colour for a `GridButton.colorGroup`; any out-of-range group (notably `-1`, which
 * the host uses for deuterocanonical books) falls back to [OTHER_BASE].
 *
 * This lives in `:sharedCore` rather than beside the composable that consumes it so the tint maths
 * is unit-testable: `ColorMathTest` sweeps every category through [blendArgb] to prove the bounded
 * luminance shift the tinted chip depends on. A copy of the table in the test could drift from the
 * one being rendered, which is exactly the bug the sweep is meant to catch.
 *
 * Display-colour-mode handling is NOT applied here — the caller wraps this in
 * [accentArgbFor] so BW greys the tint and COLOR_EINK keeps it.
 */
fun categoryBaseArgb(colorGroup: Int): Int =
    if (colorGroup in CATEGORY_BASE.indices) CATEGORY_BASE[colorGroup] else OTHER_BASE

/**
 * How far the "Choose passage" grid's inactive chip is blended from `surfaceVariant` towards its
 * luminance-matched category colour. Purely a strength dial for the look — unlike a blend towards
 * the *raw* palette, it does not trade against text contrast, because [categoryChipArgb] matches
 * luminance first.
 */
const val CATEGORY_TINT_FRACTION: Float = 0.5f

/**
 * The "Choose passage" grid's inactive chip: [surfaceArgb] blended [CATEGORY_TINT_FRACTION] of the
 * way towards [categoryArgb] **after** that category colour has been shaded to [surfaceArgb]'s own
 * luminance.
 *
 * The luminance match is what makes this safe, and it was arrived at by measurement rather than
 * taste. Blending straight towards the raw palette does not work: M3 leaves only 5.44:1 between
 * `surfaceVariant` and `onSurfaceVariant` at its worst across seeded schemes, and the categories
 * include near-white creams (`#FFFECD`) that lighten a dark chip fast — the largest fixed fraction
 * still clearing WCAG AA (4.5:1) everywhere measured **0.06**, far too faint to signal anything.
 * Shading the category colour to the surface's luminance first removes that trade entirely: the chip
 * changes hue without materially changing lightness, so the scheme's own text pairing survives (worst
 * measured 5.45:1 at this fraction, against 5.44:1 untinted) and the fraction is free to be chosen
 * for looks.
 *
 * Known cost of the match, recorded rather than worked around: it collapses saturation differences at
 * extreme target luminances, so in a light theme Major prophets (`#FF99FF`) and Revelation
 * (`#FE33FF`) — two magentas that classic distinguishes only by saturation — both land on `#FFD6FF`
 * and become indistinguishable. They stay distinct in a dark theme.
 */
fun categoryChipArgb(surfaceArgb: Int, categoryArgb: Int): Int {
    val matched = luminanceMatchedArgb(categoryArgb, relativeLuminance(surfaceArgb))
    return blendArgb(surfaceArgb, matched, CATEGORY_TINT_FRACTION)
}
