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

/** The four LTR golden renders (theme modes). The RTL check is a separate Arabic-locale capture. */
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
}

/** Full theme matrix (four LTR modes), for a screen's primary state. */
val ALL_MODES: List<GoldenMode> = GoldenMode.entries.toList()

/** Single mode used for edge states (empty/error) — theming is already covered by the primary state. */
val EDGE_MODE: GoldenMode = GoldenMode.LIGHT

/**
 * Shared capture core: render [content] wrapped exactly as the Compose hosts wrap it
 * (LayoutDirection > ProvideAppLocals > AbTheme) and capture it to [path].
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun capture(
    path: String,
    dark: Boolean,
    colorMode: DisplayColorMode,
    rtl: Boolean,
    content: @Composable () -> Unit,
) {
    // inspectionMode=true sets LocalInspectionMode, which makes Compose's InfiniteTransition
    // skip its animation LaunchedEffect and hold values at their deterministic initial state.
    // Without this, animating widgets (e.g. Material3's indeterminate LinearProgressIndicator)
    // are captured at an arbitrary animation phase, making the golden flaky (record != verify).
    captureRoboImage(
        path,
        roborazziComposeOptions = RoborazziComposeOptions { inspectionMode(true) },
    ) {
        val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = colorMode, disableAnimations = true, content = content)
            }
        }
    }
}

/** Render [content] for [mode] and capture to <screen>_<state>_<tag>.png. */
fun captureGolden(screen: String, state: String, mode: GoldenMode, content: @Composable () -> Unit) =
    capture("src/test/roborazzi/${screen}_${state}_${mode.tag}.png", mode.dark, mode.colorMode, mode.rtl, content)

/**
 * Render [content] in the light theme with RTL layout, capturing to <screen>_<state>_light_rtl.png.
 * The RTL *language* comes from the calling test method's @Config(qualifiers = "ar"); this only
 * forces the layout direction.
 */
fun captureRtl(screen: String, state: String, content: @Composable () -> Unit) =
    capture("src/test/roborazzi/${screen}_${state}_light_rtl.png", dark = false, colorMode = DisplayColorMode.NORMAL, rtl = true, content)

/** Capture [content] across the full four-mode LTR matrix. */
fun captureMatrix(screen: String, state: String, content: @Composable () -> Unit) {
    ALL_MODES.forEach { mode -> captureGolden(screen, state, mode, content) }
}
