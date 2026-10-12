package net.bible.sharedui.components

import androidx.compose.runtime.Composable

@Composable
actual fun MirrorHostSystemBars() {}

// The JVM test target has neither Android dialog flags nor a navigation-bar scrim.
@Composable
actual fun NoDialogDim() {}

@Composable
actual fun NoDialogNavigationContrast() {}
