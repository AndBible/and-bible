package net.bible.sharedui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntRect

@Composable
actual fun MirrorHostSystemBars() {}

@Composable
internal actual fun platformTopCutoutRects(): List<IntRect> = emptyList()
