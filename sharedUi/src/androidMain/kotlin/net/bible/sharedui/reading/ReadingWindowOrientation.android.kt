package net.bible.sharedui.reading

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import net.bible.sharedcore.window.splitIsHorizontal

@Composable
internal actual fun readingWindowIsLandscape(boxWidthPx: Float, boxHeightPx: Float): Boolean =
    when (LocalConfiguration.current.orientation) {
        // Compose 1.11's containerSize can sample the old WindowMetrics during rotation and
        // retain them until the next configuration change (#4220). Configuration is current
        // and, unlike the measured reading box, does not change when the IME opens.
        Configuration.ORIENTATION_LANDSCAPE -> true
        Configuration.ORIENTATION_PORTRAIT -> false
        else -> LocalWindowInfo.current.containerSize.let {
            splitIsHorizontal(it.width, it.height, boxWidthPx, boxHeightPx, reverseSplitMode = false)
        }
    }
