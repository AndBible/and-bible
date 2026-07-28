package net.bible.android.view.compose.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.inspectionMode
import com.github.takahirom.roborazzi.size
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
 * Shared capture core: render [content] wrapped as the Compose hosts wrap it
 * (LayoutDirection > ProvideAppLocals > AbTheme), then, inside a `Box` that mimics what a real
 * `Scaffold` would paint (theme background + matching content colour — see the inline comment
 * below for why a `Box` and not an actual `Scaffold`/`Surface`), capture it to [path].
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun capture(
    path: String,
    dark: Boolean,
    colorMode: DisplayColorMode,
    rtl: Boolean,
    heightDp: Int = 0,
    content: @Composable () -> Unit,
) {
    // inspectionMode=true sets LocalInspectionMode, which makes Compose's InfiniteTransition
    // skip its animation LaunchedEffect and hold values at their deterministic initial state.
    // Without this, animating widgets (e.g. Material3's indeterminate LinearProgressIndicator)
    // are captured at an arbitrary animation phase, making the golden flaky (record != verify).
    // heightDp>0 overrides only the device HEIGHT (width stays the default), so a long-list golden
    // (e.g. the 8-status matrix) can render every row instead of clipping at the default viewport.
    captureRoboImage(
        path,
        roborazziComposeOptions = RoborazziComposeOptions {
            inspectionMode(true)
            if (heightDp > 0) size(0, heightDp)
        },
    ) {
        val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = colorMode, disableAnimations = true) {
                    // The hosts always render inside AbScaffold -> Scaffold, which paints
                    // containerColor = colorScheme.background AND provides a matching
                    // LocalContentColor. MaterialTheme alone does neither, so without this a
                    // content-level capture (a bar, a menu, a drawer body, a tab body) rendered on
                    // the Robolectric window's WHITE background with M3's Color.Black content
                    // default -- making every dark/BW/eink variant of such a golden unrepresentative
                    // (see ReadingDrawer_items_dark.png before this fix: white-on-white).
                    // A Box+background is used rather than Surface(fillMaxSize) on purpose: Surface
                    // wraps its content in Box(propagateMinConstraints = true), which would force
                    // small components to fill the whole viewport and change their layout.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.background),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Render [content] for [mode] and capture to <screen>_<state>_<tag>.png.
 * [heightDp] > 0 overrides only the device height so a long list renders in full (default: clip
 * at the standard viewport, keeping every existing golden byte-identical).
 */
fun captureGolden(screen: String, state: String, mode: GoldenMode, heightDp: Int = 0, content: @Composable () -> Unit) =
    capture("src/test/roborazzi/${screen}_${state}_${mode.tag}.png", mode.dark, mode.colorMode, mode.rtl, heightDp, content)

/**
 * Render [content] in the light theme with RTL layout, capturing to <screen>_<state>_light_rtl.png.
 * The RTL *language* comes from the calling test method's @Config(qualifiers = "ar"); this only
 * forces the layout direction. [heightDp] > 0 overrides the device height (default 0 keeps the
 * standard viewport clip, same as [captureGolden]).
 */
fun captureRtl(screen: String, state: String, heightDp: Int = 0, content: @Composable () -> Unit) =
    capture("src/test/roborazzi/${screen}_${state}_light_rtl.png", dark = false, colorMode = DisplayColorMode.NORMAL, rtl = true, heightDp = heightDp, content = content)

/**
 * Capture [content] across the full four-mode LTR matrix. [heightDp] > 0 overrides the device
 * height for every mode (default 0 keeps the standard viewport clip, same as [captureGolden]).
 */
fun captureMatrix(screen: String, state: String, heightDp: Int = 0, content: @Composable () -> Unit) {
    ALL_MODES.forEach { mode -> captureGolden(screen, state, mode, heightDp = heightDp, content = content) }
}
