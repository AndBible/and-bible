package net.bible.sharedui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp

/** What the host window's system bars are doing now, so a sheet's own dialog window can do the same (F106). */
data class HostSystemBars(val statusVisible: Boolean, val navVisible: Boolean) {
    val transientBySwipe: Boolean get() = !statusVisible || !navVisible
}

/** Provided by the nav host only. Null everywhere else (goldens, previews, iOS), which keeps the mirror inert. */
val LocalHostSystemBars = compositionLocalOf<HostSystemBars?> { null }

enum class BarWrite { ShowStatus, HideStatus, ShowNav, HideNav, TransientBySwipe }

/** The pure decision behind [MirrorHostSystemBars]. Public (not internal) so `:app` tests can reach it. */
fun barWrites(bars: HostSystemBars?): List<BarWrite> = if (bars == null) emptyList() else buildList {
    add(if (bars.statusVisible) BarWrite.ShowStatus else BarWrite.HideStatus)
    add(if (bars.navVisible) BarWrite.ShowNav else BarWrite.HideNav)
    if (bars.transientBySwipe) add(BarWrite.TransientBySwipe)
}

/** Applies [LocalHostSystemBars] to the enclosing dialog window. Call inside a sheet's content. */
@Composable
expect fun MirrorHostSystemBars()

/**
 * The insets a top bar pads itself by: Material's default (system bars union display cutout, top and
 * horizontal), except that while the host hides the status bar ([HostSystemBars.statusVisible] false,
 * `hide_status_bar`) the cutout contributes only its horizontal sides. The point of that preference is
 * to win back the status-bar strip, and the legacy app drew content up into the cutout there
 * (ActivityBase padded `systemBars()`, which excludes `displayCutout`); a top cutout over an edge
 * button is cleared sideways instead ([topBarCutoutSides], #3900). With no host state (goldens,
 * previews) the cutout is kept.
 */
@Composable
fun topBarWindowInsets(): WindowInsets {
    val cutout = WindowInsets.displayCutout
    val cutoutSides = if (LocalHostSystemBars.current?.statusVisible == false) {
        WindowInsetsSides.Horizontal
    } else {
        WindowInsetsSides.Horizontal + WindowInsetsSides.Top
    }
    return WindowInsets.systemBars.union(cutout.only(cutoutSides)).union(topBarCutoutSides())
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
}

private val NoInsets = WindowInsets(0, 0, 0, 0)

/** The top display cutout, or nothing while the host hides the status bar (see [topBarWindowInsets]). */
@Composable
fun topBarCutoutTop(): WindowInsets =
    if (LocalHostSystemBars.current?.statusVisible == false) NoInsets
    else WindowInsets.displayCutout.only(WindowInsetsSides.Top)

/**
 * The top display cutout's bounding rects, in window pixels, as the bars see them. Null (the default) reads
 * the platform's ([platformTopCutoutRects]); tests provide a list.
 */
val LocalTopCutoutRects = compositionLocalOf<List<IntRect>?> { null }

/** The platform's top display-cutout bounding rects in window pixels; empty where there are none or no API. */
@Composable
internal expect fun platformTopCutoutRects(): List<IntRect>

/** Physical left/right padding, in pixels, that clears a top cutout sideways (see [topBarCutoutSidePadding]). */
data class CutoutSidePadding(val left: Int, val right: Int)

/** Width of the button zone at each end of a top bar: one 48 dp button plus the bar's edge inset. */
private val TopBarEdgeButtonZone = 56.dp

/**
 * #3900: with the status bar hidden the top bars do not reserve the cutout band (0aee33422b), so a camera
 * hole can sit on a bar's edge button. This pads only that side, just far enough to clear the hole:
 * a cutout counts for the left side when it starts inside the left [edgeZone] and ends within the first
 * quarter of [barWidth], and the same mirrored on the right. A centred hole or notch (over the title) and a
 * cutout too wide to clear without squeezing the bar get no padding. Sides are physical, not start/end: the
 * zones are symmetric, so in RTL the mirrored buttons are cleared the same way. All values in pixels, with
 * [cutouts] relative to the bar's left edge.
 */
fun topBarCutoutSidePadding(barWidth: Int, edgeZone: Int, cutouts: List<IntRect>): CutoutSidePadding {
    val cap = barWidth / 4
    var left = 0
    var right = 0
    for (r in cutouts) {
        if (r.right <= r.left) continue
        if (r.left < edgeZone && r.right in 1..cap) left = maxOf(left, r.right)
        val fromRight = barWidth - r.left
        if (r.right > barWidth - edgeZone && fromRight in 1..cap) right = maxOf(right, fromRight)
    }
    return CutoutSidePadding(left, right)
}

/**
 * The sideways cutout avoidance of [topBarCutoutSidePadding] as insets for a top bar spanning the window
 * width, while the host hides the status bar. Nothing otherwise: with the status bar shown the cutout band is
 * reserved vertically, and with no host state (goldens, previews) behaviour stays as it was.
 */
@Composable
fun topBarCutoutSides(): WindowInsets {
    if (LocalHostSystemBars.current?.statusVisible != false) return NoInsets
    val rects = LocalTopCutoutRects.current ?: platformTopCutoutRects()
    if (rects.isEmpty()) return NoInsets
    val width = LocalWindowInfo.current.containerSize.width
    val zone = with(LocalDensity.current) { TopBarEdgeButtonZone.roundToPx() }
    val pad = topBarCutoutSidePadding(width, zone, rects)
    return if (pad.left == 0 && pad.right == 0) NoInsets else WindowInsets(pad.left, 0, pad.right, 0)
}
