package net.bible.sharedui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.delay
import net.bible.sharedcore.log.nsLog

/**
 * Nothing to mirror on iOS (the host never hides bars through [LocalHostSystemBars]). Every sheet calls this inside
 * its content, so it also logs the sheet's geometry once per change (`SHEET-GEOMETRY`, NSLog; Console.app, process
 * AndBible): a diagnostic for the Mac report of a bottom sheet floating above the window's bottom edge.
 */
@Composable
actual fun MirrorHostSystemBars() {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    // Read in composition (insets are snapshot state), handed to the logging loop below as current values.
    val bottoms by rememberUpdatedState(
        Triple(
            WindowInsets.ime.getBottom(density),
            WindowInsets.safeDrawing.getBottom(density),
            WindowInsets.systemBars.getBottom(density),
        ),
    )
    val currentWindow by rememberUpdatedState(window)
    // Latest content-column bounds in the window and root (dialog layer) height, written on layout, read by the loop.
    val geometry = remember { arrayOfNulls<Any>(2) }
    LaunchedEffect(Unit) {
        var logged: String? = null
        // A slow loop rather than a line per layout pass: the slide-in would log every frame.
        while (true) {
            delay(500)
            val column = geometry[0] as? Rect ?: continue
            val rootHeight = geometry[1] as? Int ?: continue
            val (ime, safe, bars) = bottoms
            // Sheet surface bottom = column bottom + the sheet's own bottom padding (safeDrawing); 0 = on the bottom.
            val gapPx = rootHeight - column.bottom - safe
            val line = "SHEET-GEOMETRY window=${currentWindow.width}x${currentWindow.height} rootHeight=$rootHeight " +
                "column=$column gapPx=$gapPx ime=$ime safeDrawing=$safe systemBars=$bars density=${density.density}"
            if (line != logged) {
                logged = line
                nsLog(line)
            }
        }
    }
    Spacer(
        Modifier.onGloballyPositioned { c ->
            geometry[0] = c.parentLayoutCoordinates?.boundsInWindow()
            geometry[1] = c.findRootCoordinates().size.height
        },
    )
}

// The iOS host never hides the status bar through LocalHostSystemBars, so there is nothing to clear sideways.
@Composable
internal actual fun platformTopCutoutRects(): List<IntRect> = emptyList()
