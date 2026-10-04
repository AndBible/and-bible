package net.bible.android.view.activity.nav

import android.os.Build
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F68: which window mode each destination runs in, per API band. Constants are compile-time ints. */
class ReadingWindowModeTest {
    private val r = Build.VERSION_CODES.R

    @Test fun belowTheThresholdNothingIsTouched() {
        assertNull(windowModeFor(NavRoutes.READING, 28, r))
        assertNull(windowModeFor(NavRoutes.AI_TOOL_INFO, 29, r))
    }

    @Test fun readingIsEdgeToEdgeWithAdjustNothingFromApi30() {
        for (sdk in listOf(30, 34, 35, 36)) {
            assertEquals("sdk $sdk", WindowMode(false, SOFT_INPUT_ADJUST_NOTHING), windowModeFor(NavRoutes.READING, sdk, r))
        }
    }

    @Test fun readingWithArgumentsIsStillReading() =
        assertEquals(WindowMode(false, SOFT_INPUT_ADJUST_NOTHING), windowModeFor("${NavRoutes.READING}?x=1", 30, r))

    // Review Focus 3: other destinations keep decor-fits below 35 (the framework lifts them there)
    // and edge-to-edge from 35 (where the platform forces it and AbScaffold consumes the IME, Task 4).
    @Test fun otherDestinationsKeepDecorFitsBelow35AndAdjustResizeEverywhere() {
        assertEquals(WindowMode(true, SOFT_INPUT_ADJUST_RESIZE), windowModeFor(NavRoutes.AI_TOOL_INFO, 30, r))
        assertEquals(WindowMode(true, SOFT_INPUT_ADJUST_RESIZE), windowModeFor(NavRoutes.AI_TOOL_INFO, 34, r))
        assertEquals(WindowMode(false, SOFT_INPUT_ADJUST_RESIZE), windowModeFor(NavRoutes.AI_TOOL_INFO, 35, r))
        assertEquals(WindowMode(true, SOFT_INPUT_ADJUST_RESIZE), windowModeFor(null, 30, r))
    }
}
