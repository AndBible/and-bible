package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.inspectionMode
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.theme.AbTheme

/** The five golden renders: four theme modes (LTR) + one RTL check (spec §4). */
enum class GoldenMode(
    val dark: Boolean,
    val colorMode: DisplayColorMode,
    val rtl: Boolean,
    val tag: String,
) {
    DARK(true, DisplayColorMode.NORMAL, false, "dark"),
    LIGHT(false, DisplayColorMode.NORMAL, false, "light"),
    BW(false, DisplayColorMode.BW, false, "bw"),
    EINK(false, DisplayColorMode.COLOR_EINK, false, "eink"),
    LIGHT_RTL(false, DisplayColorMode.NORMAL, true, "light_rtl"),
}

/** Full theme/RTL matrix, for a screen's primary state. */
val ALL_MODES: List<GoldenMode> = GoldenMode.entries.toList()

/** Single mode used for edge states (empty/error) — theming is already covered by the primary state. */
val EDGE_MODE: GoldenMode = GoldenMode.LIGHT

/**
 * Render [content] wrapped exactly as the Compose hosts wrap it (ProvideAppLocals > AbTheme),
 * for the given [mode], and capture it to app/src/test/roborazzi/<screen>_<state>_<tag>.png.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureGolden(screen: String, state: String, mode: GoldenMode, content: @Composable () -> Unit) {
    // inspectionMode=true sets LocalInspectionMode, which makes Compose's InfiniteTransition
    // skip its animation LaunchedEffect and hold values at their deterministic initial state.
    // Without this, animating widgets (e.g. Material3's indeterminate LinearProgressIndicator)
    // are captured at an arbitrary animation phase, making the golden flaky (record != verify).
    captureRoboImage(
        "src/test/roborazzi/${screen}_${state}_${mode.tag}.png",
        roborazziComposeOptions = RoborazziComposeOptions { inspectionMode(true) },
    ) {
        val dir = if (mode.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = mode.dark,
                    colorMode = mode.colorMode,
                    disableAnimations = true,
                    content = content,
                )
            }
        }
    }
}

/** Capture [content] across the full five-render matrix. */
fun captureMatrix(screen: String, state: String, content: @Composable () -> Unit) {
    ALL_MODES.forEach { mode -> captureGolden(screen, state, mode, content) }
}
