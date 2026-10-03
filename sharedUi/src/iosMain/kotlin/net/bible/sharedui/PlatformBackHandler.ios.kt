package net.bible.sharedui

import androidx.compose.runtime.Composable

/**
 * iOS has no system Back button, so interception is a no-op. A later phase can wire a swipe-back or
 * nav-bar gesture; inventing an iOS back semantic here would be guessing at a host that does not
 * exist yet.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op on iOS
}
