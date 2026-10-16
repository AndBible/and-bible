package net.bible.sharedui.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun NoDialogDim() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        val dimFlag = WindowManager.LayoutParams.FLAG_DIM_BEHIND
        val wasDimmed = (window?.attributes?.flags?.and(dimFlag) ?: 0) != 0
        window?.clearFlags(dimFlag)
        onDispose {
            // Restore only our flag, leaving dimAmount and unrelated window flags untouched.
            if (wasDimmed) window?.addFlags(dimFlag) else window?.clearFlags(dimFlag)
        }
    }
}
