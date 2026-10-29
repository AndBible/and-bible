package net.bible.sharedui.components

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun MirrorHostSystemBars() {
    val bars = LocalHostSystemBars.current
    val view = LocalView.current
    DisposableEffect(bars, view) {
        val window = generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        if (window != null) {
            val c = WindowInsetsControllerCompat(window, window.decorView)
            for (w in barWrites(bars)) when (w) {
                BarWrite.ShowStatus -> c.show(WindowInsetsCompat.Type.statusBars())
                BarWrite.HideStatus -> c.hide(WindowInsetsCompat.Type.statusBars())
                BarWrite.ShowNav -> c.show(WindowInsetsCompat.Type.navigationBars())
                BarWrite.HideNav -> c.hide(WindowInsetsCompat.Type.navigationBars())
                BarWrite.TransientBySwipe -> c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {}
    }
}

@Composable
internal actual fun platformTopCutoutRects(): List<IntRect> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
    val view = LocalView.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // Compose keeps only the cutout's safe insets, not its rects. Reading them (state-backed) recomposes this
    // when the cutout changes (rotation, a cutout overlay), and keys the platform read below.
    val cutout = WindowInsets.displayCutout
    val safe = listOf(cutout.getLeft(density, direction), cutout.getTop(density), cutout.getRight(density, direction))
    return remember(view, safe) {
        val c = view.rootWindowInsets?.displayCutout ?: return@remember emptyList()
        val rects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(c.boundingRectTop).filterNot { it.isEmpty }
        } else {
            c.boundingRects.filter { it.top == 0 && !it.isEmpty }
        }
        rects.map { IntRect(it.left, it.top, it.right, it.bottom) }
    }
}
