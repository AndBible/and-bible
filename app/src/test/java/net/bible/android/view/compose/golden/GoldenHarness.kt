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
import com.github.takahirom.roborazzi.RoborazziComposeOption
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.inspectionMode
import com.github.takahirom.roborazzi.size
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.LocalSystemBarSync
import net.bible.test.resetComposeUiDispatcher
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import java.io.File
import javax.imageio.ImageIO

/** Reference and audit-only LTR modes. RTL is a separate Arabic-locale capture. */
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
    MONO(false, DisplayColorMode.MONOCHROME, false, "mono"),
    MONO_DARK(true, DisplayColorMode.MONOCHROME, false, "mono_dark"),
}

/** Four reference LTR modes; monochrome renders are audited without reference PNGs. */
val ALL_MODES: List<GoldenMode> = GoldenMode.entries.filter { it.colorMode != DisplayColorMode.MONOCHROME }
val MONO_MODES: List<GoldenMode> = listOf(GoldenMode.MONO, GoldenMode.MONO_DARK)

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
    captureOptions: List<RoborazziComposeOption> = emptyList(),
    roborazziOptions: RoborazziOptions? = null,
    content: @Composable () -> Unit,
) {
    // inspectionMode=true sets LocalInspectionMode, which makes Compose's InfiniteTransition
    // skip its animation LaunchedEffect and hold values at their deterministic initial state.
    // Without this, animating widgets (e.g. Material3's indeterminate LinearProgressIndicator)
    // are captured at an arbitrary animation phase, making the golden flaky (record != verify).
    // heightDp>0 overrides only the device HEIGHT (width stays the default), so a long-list golden
    // (e.g. the 8-status matrix) can render every row instead of clipping at the default viewport.
    // The WIDTH is never overridden here or anywhere in this harness -- every golden in this repo
    // is captured at the harness's default 320dp, the narrowest mainstream phone width. A capture
    // already exercises the tightest layout case; a per-test `qualifiers = "w320dp"` changes
    // nothing and only looks like it does (round-10a Task 6, fix round 5 -- a whole extra golden
    // was recorded on that false premise before this comment existed).
    //
    // Re-arm Compose's JVM-static UI dispatcher first. An earlier test in the same JVM that ended
    // with a trampoline/frame dispatch pending leaves it stuck (see resetComposeUiDispatcher's kdoc),
    // and then the capture happens before anything that needs a post-first-composition pass --
    // a LaunchedEffect load, an onTextLayout-driven chevron -- so the image comes out poorer than the
    // golden. That was the "first-capture drift" seen only in full-suite runs: bisected 2026-09-26 to
    // ReadingHostResumeReconciliationTest / ReadingHostSyncAndRestoreEventsTest, each of which alone
    // turned the next golden class red.
    resetComposeUiDispatcher()
    val composeOptions = RoborazziComposeOptions {
        inspectionMode(true)
        if (heightDp > 0) size(0, heightDp)
        // captureOptions defaults to empty for every existing caller (byte-identical
        // behaviour, no re-record needed); a caller that passes a
        // RoborazziComposeCaptureOption (e.g. to settle an async effect before the
        // screenshot -- see AbDatePickerDialogGoldenTest) gets its beforeCapture()/
        // afterCapture() wired into this specific capture only.
        captureOptions.forEach { addOption(it) }
    }
    val renderedContent: @Composable () -> Unit = {
        val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            ProvideAppLocals {
                // A/B batch 3 review fix (Important 2): `captureRoboImage` launches a REAL
                // `ComponentActivity` (`launchRoborazziActivity` -> `ActivityScenario`), so
                // `ProvideAppLocals`'s `LocalSystemBarSync` above resolves a real Activity and would
                // otherwise run `applySystemBarColor` for real on every capture (mutating
                // `window.statusBarColor` / the content-root background / icon appearance). Goldens
                // have so far stayed pixel-identical only because AbScaffold's own Scaffold container
                // happens to paint over the content-root write — a coincidence, not a guarantee.
                // Override the seam back to an explicit no-op here so captures are inert by
                // construction instead of by that coincidence.
                CompositionLocalProvider(LocalSystemBarSync provides { _, _ -> }) {
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
    // Reference captures retain the library's context-dependent default, not RoborazziOptions().
    if (roborazziOptions == null) {
        captureRoboImage(path, roborazziComposeOptions = composeOptions, content = renderedContent)
    } else {
        captureRoboImage(path, roborazziOptions = roborazziOptions, roborazziComposeOptions = composeOptions, content = renderedContent)
    }
}

/**
 * Render [content] for [mode] and capture to <screen>_<state>_<tag>.png.
 * [heightDp] > 0 overrides only the device height so a long list renders in full (default: clip
 * at the standard viewport, keeping every existing golden byte-identical).
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureGolden(
    screen: String,
    state: String,
    mode: GoldenMode,
    heightDp: Int = 0,
    captureOptions: List<RoborazziComposeOption> = emptyList(),
    content: @Composable () -> Unit,
) = capture("src/test/roborazzi/${screen}_${state}_${mode.tag}.png", mode.dark, mode.colorMode, mode.rtl, heightDp, captureOptions, content = content)

/**
 * Render [content] in the light theme with RTL layout, capturing to <screen>_<state>_light_rtl.png.
 * The RTL *language* comes from the calling test method's @Config(qualifiers = "ar"); this only
 * forces the layout direction. [heightDp] > 0 overrides the device height (default 0 keeps the
 * standard viewport clip, same as [captureGolden]).
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureRtl(
    screen: String,
    state: String,
    heightDp: Int = 0,
    captureOptions: List<RoborazziComposeOption> = emptyList(),
    content: @Composable () -> Unit,
) = capture(
    "src/test/roborazzi/${screen}_${state}_light_rtl.png",
    dark = false, colorMode = DisplayColorMode.NORMAL, rtl = true, heightDp = heightDp,
    captureOptions = captureOptions, content = content,
)

/**
 * Capture [content] across the full four-mode LTR matrix. [heightDp] > 0 overrides the device
 * height for every mode (default 0 keeps the standard viewport clip, same as [captureGolden]).
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureMatrix(
    screen: String,
    state: String,
    heightDp: Int = 0,
    captureOptions: List<RoborazziComposeOption> = emptyList(),
    content: @Composable () -> Unit,
) {
    ALL_MODES.forEach { mode -> captureGolden(screen, state, mode, heightDp = heightDp, captureOptions = captureOptions, content = content) }
    auditMono(screen, state, heightDp, captureOptions, content)
}

/** Force audit captures into build output, even when the enclosing task does not record goldens. */
@OptIn(ExperimentalRoborazziApi::class)
internal fun auditMono(
    screen: String,
    state: String,
    heightDp: Int,
    captureOptions: List<RoborazziComposeOption>,
    content: @Composable () -> Unit,
) {
    val key = "${screen}_${state}"
    val policy = MonochromeAuditPolicy.fromResources()
    if (!policy.shouldAudit(key)) return
    val renders = MONO_MODES.map { mode ->
        val file = File("build/mono-audit/${key}_${mode.tag}.png")
        file.parentFile?.mkdirs()
        capture(file.path, mode.dark, mode.colorMode, mode.rtl, heightDp, captureOptions,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record), content = content)
        val image = checkNotNull(ImageIO.read(file)) { "Cannot read monochrome audit capture: $file" }
        MonochromeAuditPolicy.Render(mode.tag, MonochromePaletteAudit.audit(image, mode.dark), file)
    }
    policy.check(key, renders, System.getProperty("mono.audit.report"))
}
