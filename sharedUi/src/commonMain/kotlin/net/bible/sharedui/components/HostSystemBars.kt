package net.bible.sharedui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

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
 * (ActivityBase padded `systemBars()`, which excludes `displayCutout`). With no host state (goldens,
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
    return WindowInsets.systemBars.union(cutout.only(cutoutSides))
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
}

private val NoInsets = WindowInsets(0, 0, 0, 0)

/** The top display cutout, or nothing while the host hides the status bar (see [topBarWindowInsets]). */
@Composable
fun topBarCutoutTop(): WindowInsets =
    if (LocalHostSystemBars.current?.statusVisible == false) NoInsets
    else WindowInsets.displayCutout.only(WindowInsetsSides.Top)
