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
 * category colour.
 *
 * A quarter step is the measured maximum that keeps every category's chip at ≥ 4.5:1 against
 * `onSurfaceVariant` in both stock M3 schemes (asserted in `GridCategoryTintContrastTest`) while
 * still reading as a distinguishable hue family. It is an output of that measurement, not a
 * preference: if the scheme or the palette changes and the assertion fails, lower this value.
 */
const val CATEGORY_TINT_FRACTION: Float = 0.25f
