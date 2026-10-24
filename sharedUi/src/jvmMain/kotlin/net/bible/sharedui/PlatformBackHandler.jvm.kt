package net.bible.sharedui

import androidx.compose.runtime.Composable

/** Test-only jvm() target has no system Back button; interception is a no-op, as on iOS. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op on jvm
}
