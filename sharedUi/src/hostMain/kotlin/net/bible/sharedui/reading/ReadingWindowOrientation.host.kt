package net.bible.sharedui.reading

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import net.bible.sharedcore.window.splitIsHorizontal

@Composable
internal actual fun readingWindowIsLandscape(boxWidthPx: Float, boxHeightPx: Float): Boolean =
    LocalWindowInfo.current.containerSize.let {
        splitIsHorizontal(it.width, it.height, boxWidthPx, boxHeightPx, reverseSplitMode = false)
    }
