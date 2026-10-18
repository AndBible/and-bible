package net.bible.sharedui.theme

import androidx.compose.runtime.Composable
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.resolveDisableAnimations
import net.bible.sharedcore.platform.resolveDisplayColorMode
import net.bible.sharedcore.platform.resolveThemePaletteStyle

/**
 * The common host theme: reads the app-global theme inputs (colour mode, animations, palette style)
 * from [settings] and wraps [content] in [AbTheme]. This and `AbAppTheme` (`:app`, which also
 * provides the Android locals) are the only two `AbTheme(` call sites (`AbThemeHostGuardTest`).
 *
 * The settings are read during composition, like the Android wrapper always did, so a host that
 * recomposes on a settings change picks the new values up. [darkTheme] and [workspaceSeed] come from
 * the host because their live sources (night-mode state, workspace-change events) are platform
 * specific; [workspaceSeed] is invoked during composition, so back it with snapshot state when it
 * must trigger recomposition.
 *
 * @param isOnyx whether the device is an Onyx e-ink device (changes the colour-mode and animation defaults).
 */
@Composable
fun AbHostTheme(
    darkTheme: Boolean,
    settings: AppSettings,
    isOnyx: Boolean,
    workspaceSeed: () -> Int?,
    content: @Composable () -> Unit,
) {
    AbTheme(
        seedArgb = workspaceSeed(),
        darkTheme = darkTheme,
        colorMode = settings.resolveDisplayColorMode(isOnyx),
        disableAnimations = settings.resolveDisableAnimations(isOnyx),
        paletteStyle = settings.resolveThemePaletteStyle(),
        content = content,
    )
}
