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

/** Apply only around fixed-height bars, never around scrollable content or Bible text. */
@Composable
@kotlin.native.HiddenFromObjC
fun CappedBarFontScale(content: @Composable () -> Unit) {
    val current = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(current.density, fontScale = min(current.fontScale, LocalBarFontScaleCap.current)),
        content = content,
    )
}
