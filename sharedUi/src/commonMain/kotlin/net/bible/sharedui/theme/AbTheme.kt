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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.theme.accentArgbFor

// Base accents (colored). Adjust hues later; e-ink polish is not chased in Phase 0.
private val BookmarkBase = Color(0xFFFFC107)
private val ActiveWindowBase = Color(0xFF2196F3)
private val HelperLineBase = Color(0xFF4CAF50)

/** The active [DisplayColorMode], exposed so content (e.g. the passage-grid category palette) can
 *  grayscale its own non-scheme colors in BW/COLOR_EINK. Defaults to NORMAL outside an [AbTheme]. */
val LocalDisplayColorMode = staticCompositionLocalOf { DisplayColorMode.NORMAL }

/** Whether [AbTheme] resolved to the dark scheme. Exposed so content can branch on night mode
 *  without re-deriving it from a colour. Defaults to `false` outside an [AbTheme]. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * AndBible's Material3 theme for the Compose UI path.
 *
 * @param darkTheme whether to use the dark color scheme. **Hosts MUST supply this from
 *   [net.bible.service.device.ScreenSettings.nightMode]** (the resolved app night boolean —
 *   honours AndBible's auto/sunset/manual/system night setting, the same source the classic
 *   screens read). The [isSystemInDarkTheme] default is only a fallback for `@Preview`s and
 *   tests that have no `ScreenSettings`; a real host that lets it default would wrongly follow
 *   the OS theme instead of AndBible's own night setting. This is the contract every Compose host
 *   follows — thread it exactly like [colorMode]/[disableAnimations] come from `CommonUtils`.
 * @param colorMode NORMAL keeps full color; BW/COLOR_EINK grayscale the whole base scheme
 *   (accents in [LocalAbColors] stay colored in COLOR_EINK via [accentArgbFor]).
 * @param disableAnimations exposed to content via [LocalDisableAnimations].
 * @param seedArgb the workspace colour used as the Material 3 seed (A/B batch 4b). `null` — the
 *   default — is the untinted path and returns today's stock scheme, which is why previews, tests
 *   and the Roborazzi harness need no change. `:app` hosts do not pass this by hand; `AbAppTheme`
 *   supplies it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbTheme(
    seedArgb: Int? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorMode: DisplayColorMode = DisplayColorMode.NORMAL,
    disableAnimations: Boolean = false,
    content: @Composable () -> Unit,
) {
    // abColorScheme returns the FINAL scheme: seeding and the BW/COLOR_EINK greyscale pass both
    // happen inside it, so this host cannot fall out of step with the BibleView payload builder,
    // which calls the same function. Accents follow accentArgbFor (still coloured in COLOR_EINK).
    val scheme = abColorScheme(seedArgb, darkTheme, colorMode)
    val mono = colorMode == DisplayColorMode.MONOCHROME
    val accents = if (mono) {
        val ink = monoInk(darkTheme)
        AbColors(bookmark = ink, activeWindow = ink, helperLine = ink, monoDisabled = MonoDisabled)
    } else AbColors(
        bookmark = Color(accentArgbFor(BookmarkBase.toArgb(), colorMode)),
        activeWindow = Color(accentArgbFor(ActiveWindowBase.toArgb(), colorMode)),
        helperLine = Color(accentArgbFor(HelperLineBase.toArgb(), colorMode)),
        monoDisabled = scheme.onSurface.copy(alpha = 0.38f),
    )
    CompositionLocalProvider(
        LocalAbColors provides accents,
        LocalDisableAnimations provides disableAnimations,
        LocalDisplayColorMode provides colorMode,
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(colorScheme = scheme) {
            if (mono) {
                CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
            } else content()
        }
    }
}
