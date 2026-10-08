package net.bible.sharedui

import androidx.compose.runtime.Composable

@Composable
actual fun PassThroughBackHandler(enabled: Boolean, onBack: (passThrough: () -> Unit) -> Unit) {
    // no-op on the test-only jvm() target, as on iOS
}
