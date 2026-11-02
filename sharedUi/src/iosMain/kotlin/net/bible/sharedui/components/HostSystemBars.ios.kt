package net.bible.sharedui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    val imeBottom = WindowInsets.ime.getBottom(density)
    val safeBottom = WindowInsets.safeDrawing.getBottom(density)
    val barsBottom = WindowInsets.systemBars.getBottom(density)
    // [0] = latest geometry, [1] = last logged; logged from a slow loop so the slide-in does not log every frame.
    val lines = remember { arrayOfNulls<String>(2) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val line = lines[0]
            if (line != null && line != lines[1]) {
                lines[1] = line
                nsLog(line)
            }
        }
    }
    Spacer(
        Modifier.onGloballyPositioned { c ->
            val column = c.parentLayoutCoordinates?.boundsInWindow()
            val root = c.findRootCoordinates().size
            lines[0] = "SHEET-GEOMETRY window=${window.width}x${window.height} root=${root.width}x${root.height} " +
                "column=$column ime=$imeBottom safeDrawing=$safeBottom systemBars=$barsBottom density=${density.density}"
        },
    )
}

// The iOS host never hides the status bar through LocalHostSystemBars, so there is nothing to clear sideways.
@Composable
internal actual fun platformTopCutoutRects(): List<IntRect> = emptyList()
