package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntRect

@Composable
actual fun MirrorHostSystemBars() {}

// The iOS host never hides the status bar through LocalHostSystemBars, so there is nothing to clear sideways.
@Composable
internal actual fun platformTopCutoutRects(): List<IntRect> = emptyList()
