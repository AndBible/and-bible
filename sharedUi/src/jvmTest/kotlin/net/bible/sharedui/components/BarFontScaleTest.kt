package net.bible.sharedui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BarFontScaleTest {
    @Test
    fun capLimitsFontScaleInsideBar() = runComposeUiTest {
        var inside = Float.NaN
        var outside = Float.NaN
        var densityInside = Float.NaN
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale = 3.1f),
                LocalBarFontScaleCap provides 1.35f,
            ) {
                CappedBarFontScale {
                    inside = LocalDensity.current.fontScale
                    densityInside = LocalDensity.current.density
                    Text("Bar label")
                }
                outside = LocalDensity.current.fontScale
            }
        }
        waitForIdle()
        assertEquals(1.35f, inside, "fixed-height bar must cap text scaling")
        assertEquals(1f, densityInside, "cap must not change layout density")
        assertEquals(3.1f, outside, "cap must not leak into surrounding content")
    }

    @Test
    fun noCapByDefaultKeepsAndroidScale() = runComposeUiTest {
        var seen = Float.NaN
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 3.1f)) {
                CappedBarFontScale {
                    seen = LocalDensity.current.fontScale
                    Text("Uncapped label")
                }
            }
        }
        waitForIdle()
        assertEquals(3.1f, seen, "without an iOS cap provider Android scaling stays unchanged")
    }
}
