package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * iOS back: delegates to Compose's common `BackHandler` (ui-backhandler), which is driven by the
 * edge-swipe back gesture (built into `ComposeUIViewController` in CMP 1.11, no flag).
 */
@Suppress("DEPRECATION") // the replacement (NavigationEventHandler) is not used by shared screens yet
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
