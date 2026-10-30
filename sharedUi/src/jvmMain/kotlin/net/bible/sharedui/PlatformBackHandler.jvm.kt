package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import net.bible.sharedui.desktop.reading.DesktopBackDispatcher

/**
 * Desktop: a real back handler of the Compose back stack. Esc in a Compose window reaches it through Compose's own
 * input; page keys and mouse buttons reach it through [DesktopBackDispatcher.back].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    DesktopBackDispatcher.Attach()
    BackHandler(enabled = enabled, onBack = onBack)
}
