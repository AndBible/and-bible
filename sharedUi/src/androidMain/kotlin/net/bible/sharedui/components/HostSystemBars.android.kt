package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
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
