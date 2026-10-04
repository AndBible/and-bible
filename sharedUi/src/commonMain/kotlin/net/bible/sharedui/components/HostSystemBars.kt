package net.bible.sharedui.components

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
