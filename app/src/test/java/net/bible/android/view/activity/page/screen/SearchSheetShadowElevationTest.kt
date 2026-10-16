package net.bible.android.view.activity.page.screen

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import net.bible.service.common.DisplayColorMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hidden search sheet must not cast a shadow on greyscale-base (BW and MONOCHROME) paper. */
@OptIn(ExperimentalMaterial3Api::class)
class SearchSheetShadowElevationTest {
    @Test fun monochromeHasNoShadow() =
        assertEquals(0.dp, searchSheetShadowElevation(DisplayColorMode.MONOCHROME))

    @Test fun blackAndWhiteHasNoShadow() =
        assertEquals(0.dp, searchSheetShadowElevation(DisplayColorMode.BW))

    @Test fun normalKeepsDefaultElevation() =
        assertEquals(BottomSheetDefaults.Elevation, searchSheetShadowElevation(DisplayColorMode.NORMAL))

    @Test fun colorEinkKeepsDefaultElevation() =
        assertEquals(BottomSheetDefaults.Elevation, searchSheetShadowElevation(DisplayColorMode.COLOR_EINK))

    @Test fun everyModeIsCoveredByAnExpectation() {
        val expectedFlat = setOf(DisplayColorMode.MONOCHROME, DisplayColorMode.BW)
        DisplayColorMode.entries.forEach {
            val flat = searchSheetShadowElevation(it) == 0.dp
            assertEquals(it.name, it in expectedFlat, flat)
        }
    }
}
