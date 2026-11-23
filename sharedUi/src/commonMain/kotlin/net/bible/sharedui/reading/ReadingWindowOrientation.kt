package net.bible.sharedui.reading

import androidx.compose.runtime.Composable

/** Window orientation, independent of the keyboard-shrunk reading box. */
@Composable
internal expect fun readingWindowIsLandscape(boxWidthPx: Float, boxHeightPx: Float): Boolean
