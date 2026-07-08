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

package net.bible.sharedui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedcore.theme.toGrayscaleArgb

private fun grayed(c: Color, mode: DisplayColorMode): Color =
    if (mode == DisplayColorMode.NORMAL) c else Color(toGrayscaleArgb(c.toArgb()))

// Base accents (colored). Adjust hues later; e-ink polish is not chased in Phase 0.
private val BookmarkBase = Color(0xFFFFC107)
private val ActiveWindowBase = Color(0xFF2196F3)
private val HelperLineBase = Color(0xFF4CAF50)

@Composable
fun AbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorMode: DisplayColorMode = DisplayColorMode.NORMAL,
    disableAnimations: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    // In BW/COLOR_EINK the base scheme is grayscale; accents follow accentArgbFor (colored in COLOR_EINK).
    val scheme = if (colorMode == DisplayColorMode.NORMAL) baseScheme else baseScheme.copy(
        primary = grayed(baseScheme.primary, colorMode),
        secondary = grayed(baseScheme.secondary, colorMode),
        tertiary = grayed(baseScheme.tertiary, colorMode),
        primaryContainer = grayed(baseScheme.primaryContainer, colorMode),
        secondaryContainer = grayed(baseScheme.secondaryContainer, colorMode),
        tertiaryContainer = grayed(baseScheme.tertiaryContainer, colorMode),
    )
    val accents = AbColors(
        bookmark = Color(accentArgbFor(BookmarkBase.toArgb(), colorMode)),
        activeWindow = Color(accentArgbFor(ActiveWindowBase.toArgb(), colorMode)),
        helperLine = Color(accentArgbFor(HelperLineBase.toArgb(), colorMode)),
    )
    CompositionLocalProvider(
        LocalAbColors provides accents,
        LocalDisableAnimations provides disableAnimations,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
