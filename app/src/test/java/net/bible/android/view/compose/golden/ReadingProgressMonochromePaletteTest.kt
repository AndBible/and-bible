package net.bible.android.view.compose.golden

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.progress.*
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingProgressMonochromePaletteTest {
    @get:Rule val rule = createComposeRule()

    @Test fun zeroPartialAndFullRemainDistinctInBothThemes() {
        rule.setContent {
            for (dark in listOf(false, true)) {
                AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME) {
                    val ink = if (dark) Color.White else Color.Black
                    val paper = if (dark) Color.Black else Color.White
                    assertEquals(Color(0xFF808080), countHeatColors(0, 10).background)
                    assertEquals(paper, countHeatColors(1, 10).background)
                    assertEquals(ink, countHeatColors(10, 10).background)
                    assertEquals(Color(0xFF808080), bookProgressColors(0f, 2f).background)
                    assertEquals(paper, bookProgressColors(0.5f, 2f).background)
                    assertEquals(ink, bookProgressColors(1f, 2f).background)
                    assertEquals(Color(0xFF808080), memorizationColors(0).background)
                    for (level in 1..3) assertEquals(paper, memorizationColors(level).background)
                    assertEquals(ink, memorizationColors(4).background)
                    assertEquals(Color(0xFF808080), calendarLevelColor(0))
                    for (level in 1..3) assertEquals(paper, calendarLevelColor(level))
                    assertEquals(ink, calendarLevelColor(4))
                    assertEquals(paper, memorizationColors(4).content)
                    assertEquals(ink, memorizationColors(1).content)
                }
            }
        }
        rule.waitForIdle()
    }
}
