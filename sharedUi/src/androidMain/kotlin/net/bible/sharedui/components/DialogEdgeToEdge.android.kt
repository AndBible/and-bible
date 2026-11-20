package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Edge-to-edge itself comes from [textPageDialogProperties]; this only sets the bar icon contrast. */
@Composable
actual fun DialogEdgeToEdge(darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, darkIcons) {
        val window = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
        onDispose { }
    }
}

actual fun textPageDialogProperties(dismissOnBackPress: Boolean): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)
