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

package net.bible.sharedcore.reading

// Z-late epilogue: the classic resources this file cites by name no longer exist -- they were
// deleted once the Compose reading view replaced what used them. The citations stay as the
// provenance of the numbers below, which is the whole reason they are recorded.
/**
 * How much to shrink the restore-rail button's two label lines so they fit the space left below
 * its badge row.
 *
 * The rail button is a FIXED 40.dp box (classic `window_button.xml` likewise): it reserves a
 * dp-sized strip at the top for the sync/doc-type badges and gives the rest to the label pair.
 * The labels, though, are sized in `sp`, so they grow with the user's system font scale while the
 * box does not — and because the label `Column` is bottom-anchored, the overflow goes UPWARD, into
 * the badge. That is the reported "sync icon overlaps the bible reference" collision. Clipping
 * would hide the reference instead; freezing the labels in dp would ignore the font-scale setting
 * entirely. So the labels honour the setting until they fill the button, and then stop.
 *
 * Returns 1.0 whenever the labels already fit (so at font scale 1.0 nothing moves and no golden
 * shifts for this reason alone), otherwise the proportional factor that makes them fit, clamped to
 * [0, 1]. [requiredPx] must count only the lines actually drawn — a single-line button must not be
 * shrunk for a line it never renders. Both arguments are pixels, so the caller's `sp → px`
 * conversion is what carries the font scale in.
 */
fun railLabelFontScale(availablePx: Float, requiredPx: Float): Float {
    if (requiredPx <= 0f) return 1f
    val scale = availablePx / requiredPx
    return when {
        scale > 1f -> 1f
        scale < 0f -> 0f
        else -> scale
    }
}
