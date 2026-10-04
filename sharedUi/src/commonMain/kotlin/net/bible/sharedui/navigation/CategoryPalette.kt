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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedcore.theme.categoryBaseArgb
import net.bible.sharedcore.theme.categoryChipArgb
import net.bible.sharedui.theme.LocalDisplayColorMode

/** Category color for a [GridButton.colorGroup]; grays out in BW, stays colored in COLOR_EINK.
 *  The palette itself lives in `:sharedCore` ([categoryBaseArgb]) so the tint maths can be swept
 *  over the real table in a unit test rather than over a copy that could drift from it. */
@Composable
fun categoryColor(colorGroup: Int): Color =
    Color(accentArgbFor(categoryBaseArgb(colorGroup), LocalDisplayColorMode.current))

/**
 * The "Choose passage" grid's inactive chip fill: [base] — the caller passes
 * `MaterialTheme.colorScheme.surfaceVariant` — tinted towards the book's category colour by
 * [categoryChipArgb], which shades that colour to [base]'s own luminance first.
 *
 * This restores classic's category signal, which the port had lost. Classic paints category-coloured
 * *text* on a dark chip; that cannot be reproduced literally, because its palette is light pastels
 * (`#CCCCFE`, `#FFFF31`) which are near-invisible as text on a light `surfaceVariant`. Tinting the
 * chip instead keeps the label on M3's `surfaceVariant`/`onSurfaceVariant` pair — see
 * [categoryChipArgb] for why the luminance match, not the fraction, is what makes that safe.
 *
 * BW needs no branch here: [categoryColor] already routes the palette through `accentArgbFor`, so the
 * tint is grey in BW and the chip stays within a hair of the plain surface.
 *
 * `remember`ed because the bisection in `luminanceMatchedArgb` runs 24 luminance evaluations and a
 * grid recomposes per scroll: the keys are everything the result depends on, so a cell computes this
 * once.
 */
@Composable
fun categoryChipColor(colorGroup: Int, base: Color): Color {
    val category = categoryColor(colorGroup)
    return remember(category, base) { Color(categoryChipArgb(base.toArgb(), category.toArgb())) }
}

// Progress-bar hues (classic green/gold), also e-ink-aware.
@Composable
fun readingColor(): Color = Color(accentArgbFor(0xFF4CAF50.toInt(), LocalDisplayColorMode.current))

@Composable
fun memorizationColor(): Color = Color(accentArgbFor(0xFFFFD700.toInt(), LocalDisplayColorMode.current))
