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

import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.theme.accentArgbFor
import kotlin.math.roundToInt

/**
 * Classic's `defaultWorkspaceColor` (`app/.../database/WorkspaceEntities.kt:41`), duplicated here
 * because `:sharedCore` cannot see the Android database module. It doubles as the **"not set"
 * sentinel** — see [isWorkspaceColorSet].
 */
const val DEFAULT_WORKSPACE_COLOR_ARGB: Int = 0xFF444444.toInt()

/** How far a set workspace colour is blended into the dark surface in night mode (A/B batch 3, F3). */
const val NIGHT_WORKSPACE_TINT_FRACTION: Float = 0.30f

/**
 * Whether the user actually chose a workspace colour.
 *
 * The column default is `NULL` but the Kotlin field default — and what "reset to default" writes
 * (`WorkspaceServiceImpl.kt:125-126`, `WorkspaceSelectorActivity.kt:566`) — is
 * [DEFAULT_WORKSPACE_COLOR_ARGB], so BOTH count as not set. Accepted side effect: a user who
 * deliberately picks `#444444` gets the untinted default look; in exchange "reset to default"
 * behaves correctly with no migration.
 */
fun isWorkspaceColorSet(workspaceArgb: Int?): Boolean =
    workspaceArgb != null && workspaceArgb != DEFAULT_WORKSPACE_COLOR_ARGB

/** Per-channel linear interpolation between two ARGB ints; [fraction] is clamped to `0f..1f`. */
fun lerpArgb(fromArgb: Int, toArgb: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val a = (fromArgb shr shift) and 0xFF
        val b = (toArgb shr shift) and 0xFF
        return (a + (b - a) * f).roundToInt().coerceIn(0, 255)
    }
    return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/**
 * The Compose reading toolbar's container colour (A/B batch 3, F3).
 *
 * - **Not set** → [surfaceArgb] unchanged, i.e. today's plain Material 3 bar. `accentArgbFor` is
 *   deliberately NOT applied on this path: `AbTheme` already greyscales the whole `ColorScheme` in
 *   BW and COLOR_EINK, and wrapping a scheme role a second time is the mistake palette B banked.
 * - **Set, day** → the colour, degraded for the display mode by [accentArgbFor] (grey in BW,
 *   coloured in COLOR_EINK).
 * - **Set, night** → the same accent blended [NIGHT_WORKSPACE_TINT_FRACTION] of the way from the
 *   dark surface, so the hue is recognisable without glaring. A dark chosen colour therefore reads
 *   as a very subtle tint — accepted: the user picked a dark colour.
 *
 * The content (text/icon) colour is NOT decided here — it is a luminance call over the returned
 * container, made in `:sharedUi` where Compose's `Color.luminance()` exists.
 */
fun readingToolbarContainerArgb(
    workspaceArgb: Int?,
    surfaceArgb: Int,
    nightMode: Boolean,
    colorMode: DisplayColorMode,
): Int {
    if (!isWorkspaceColorSet(workspaceArgb)) return surfaceArgb
    val accent = accentArgbFor(workspaceArgb!!, colorMode)
    return if (nightMode) lerpArgb(surfaceArgb, accent, NIGHT_WORKSPACE_TINT_FRACTION) else accent
}
