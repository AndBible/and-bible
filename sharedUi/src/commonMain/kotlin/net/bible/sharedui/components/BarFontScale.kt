@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.min

/** No cap unless the host explicitly supplies one; Android keeps its system font scaling. */
@kotlin.native.HiddenFromObjC
val LocalBarFontScaleCap: ProvidableCompositionLocal<Float> = staticCompositionLocalOf { Float.POSITIVE_INFINITY }

/**
 * The density [CappedBarFontScale] replaced, so popups launched from a capped bar (menus) can restore
 * it with [UncappedFontScale]. `null` outside a capped bar.
 */
@kotlin.native.HiddenFromObjC
val LocalUncappedDensity: ProvidableCompositionLocal<Density?> = staticCompositionLocalOf { null }

/** Apply only around fixed-height bars, never around scrollable content or Bible text. */
@Composable
@kotlin.native.HiddenFromObjC
fun CappedBarFontScale(content: @Composable () -> Unit) {
    val current = LocalDensity.current
    val capped = min(current.fontScale, LocalBarFontScaleCap.current)
    CompositionLocalProvider(
        LocalDensity provides Density(current.density, fontScale = capped),
        // Keep the first (outermost) uncapped density if bars nest.
        LocalUncappedDensity provides (LocalUncappedDensity.current ?: current),
        content = content,
    )
}

/**
 * Restores the system font scale inside a menu popup that is declared within a capped bar. A no-op
 * when no bar capped the scale (Android, or outside a bar).
 */
@Composable
@kotlin.native.HiddenFromObjC
fun UncappedFontScale(content: @Composable () -> Unit) {
    val original = LocalUncappedDensity.current
    if (original == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides original,
            LocalUncappedDensity provides null,
            content = content,
        )
    }
}
