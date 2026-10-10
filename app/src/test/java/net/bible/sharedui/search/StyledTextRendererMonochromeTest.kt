package net.bible.sharedui.search

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.monoInk
import net.bible.sharedui.theme.monoPaper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class StyledTextRendererMonochromeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun highlightedMatchLight() = highlightedMatch(false)
    @Test fun highlightedMatchDark() = highlightedMatch(true)

    private fun highlightedMatch(dark: Boolean) {
        var result: AnnotatedString? = null
        rule.setContent {
            AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME) {
                result = styledTextToAnnotatedString(StyledText(listOf(StyledRun("x", highlight = true))))
            }
        }
        rule.waitForIdle()
        val span = result!!.spanStyles.single().item
        assertEquals(monoPaper(dark), span.color)
        assertEquals(monoInk(dark), span.background)
    }
}
