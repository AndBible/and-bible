package net.bible.android.view.widget

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders

/**
 * Colour roles of the home-screen widgets (spec "Visual specification", direction A).
 *
 * [light] and [dark] are the fixed tonal palette used below API 31 (and as the golden reference);
 * [mono] and [monoDark] are grey-only schemes for AndBible's monochrome (e-ink) setting. Each is a
 * [ColorProviders] pinned to one scheme, so it does not depend on the system night mode. Use
 * [system] to follow the system night mode.
 */
object WidgetColors {
    private val lightScheme: ColorScheme = lightColorScheme(
        surface = Color(0xFFDFE8F6), surfaceVariant = Color(0xFFCBD9EE),
        onSurface = Color(0xFF14243A), onSurfaceVariant = Color(0xFF46566C),
        primary = Color(0xFF2F5D9A), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC4D9FB), onPrimaryContainer = Color(0xFF14243A),
        background = Color(0xFFDFE8F6), onBackground = Color(0xFF14243A),
        secondaryContainer = Color(0xFFCBD9EE), onSecondaryContainer = Color(0xFF14243A),
    )
    private val darkScheme: ColorScheme = darkColorScheme(
        surface = Color(0xFF1B2430), surfaceVariant = Color(0xFF26313F),
        onSurface = Color(0xFFE1E8F2), onSurfaceVariant = Color(0xFFA4B0C1),
        primary = Color(0xFFA6C8FF), onPrimary = Color(0xFF0B2F5C),
        primaryContainer = Color(0xFF2C4568), onPrimaryContainer = Color(0xFFE1E8F2),
        background = Color(0xFF1B2430), onBackground = Color(0xFFE1E8F2),
        secondaryContainer = Color(0xFF26313F), onSecondaryContainer = Color(0xFFE1E8F2),
    )
    private val monoLightScheme: ColorScheme = lightColorScheme(
        surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFD9D9D9),
        onSurface = Color(0xFF000000), onSurfaceVariant = Color(0xFF444444),
        primary = Color(0xFF000000), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9D9D9), onPrimaryContainer = Color(0xFF000000),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF000000),
        secondaryContainer = Color(0xFFD9D9D9), onSecondaryContainer = Color(0xFF000000),
    )
    private val monoDarkScheme: ColorScheme = darkColorScheme(
        surface = Color(0xFF000000), surfaceVariant = Color(0xFF3A3A3A),
        onSurface = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFFBBBBBB),
        primary = Color(0xFFFFFFFF), onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF3A3A3A), onPrimaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFF000000), onBackground = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF3A3A3A), onSecondaryContainer = Color(0xFFFFFFFF),
    )

    val light: ColorProviders = ColorProviders(lightScheme, lightScheme)
    val dark: ColorProviders = ColorProviders(darkScheme, darkScheme)
    val mono: ColorProviders = ColorProviders(monoLightScheme, monoLightScheme)
    val monoDark: ColorProviders = ColorProviders(monoDarkScheme, monoDarkScheme)

    /** Fixed palette following the system night mode. */
    val system: ColorProviders = ColorProviders(lightScheme, darkScheme)

    /** Grey-only palette following the system night mode. */
    val monoSystem: ColorProviders = ColorProviders(monoLightScheme, monoDarkScheme)
}

/**
 * Applies the widget theme. On API 31+ the dynamic system colours are used unless [mono] is set;
 * below that (and in mono) the fixed tonal palette applies. Light/dark follows the system night mode.
 */
@Composable
fun AbWidgetTheme(mono: Boolean, content: @Composable () -> Unit) {
    val colors = when {
        mono -> WidgetColors.monoSystem
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlanceTheme.colors
        else -> WidgetColors.system
    }
    GlanceTheme(colors = colors, content = content)
}

/** Applies a fixed [colors] palette, ignoring dynamic colours and night mode (previews, goldens). */
@Composable
fun AbWidgetTheme(colors: ColorProviders, content: @Composable () -> Unit) {
    GlanceTheme(colors = colors, content = content)
}
