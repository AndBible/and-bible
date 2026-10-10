package net.bible.sharedui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun NoDialogNavigationContrast() {
    val view = LocalView.current
    DisposableEffect(view) {
        // Sheets own a separate full-screen window. Never fall back to the Activity window.
        val window = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced
        } else null
        if (original != null) window?.isNavigationBarContrastEnforced = false
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && original != null) {
                window?.isNavigationBarContrastEnforced = original
            }
        }
    }
}
