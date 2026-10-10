package net.bible.sharedui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.service.common.ThemePaletteStyle
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.theme.contrastRatio
import net.bible.sharedui.search.styledTextToAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Measures the actual laid-out highlight spans, not just the palette's unused role pairs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class StyledTextRendererContrastTest {
    @get:Rule val compose = createComposeRule()

    private data class Case(
        val seed: Int?, val dark: Boolean, val style: ThemePaletteStyle,
        val mode: DisplayColorMode = DisplayColorMode.NORMAL,
    )

    private fun assertReadable(cases: List<Case>) {
        val current = mutableStateOf(cases.first())
        compose.setContent {
            val case = current.value
            MaterialTheme(colorScheme = abColorScheme(case.seed, case.dark, case.mode, case.style)) {
                Text(styledTextToAnnotatedString(StyledText(listOf(
                    StyledRun("plain "), StyledRun("match", highlight = true), StyledRun(" bold", bold = true),
                ))))
            }
        }
        for (case in cases) {
            compose.runOnIdle { current.value = case }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("plain match bold").fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val text = layouts.single().layoutInput.text
            val highlight = text.spanStyles.single { it.start == 6 && it.end == 11 }.item
            val ratio = contrastRatio(highlight.color.toArgb(), highlight.background.toArgb())
            assertTrue("$case rendered highlight contrast $ratio:1 is below 4.5:1", ratio >= 4.5)
            assertEquals(FontWeight.Bold, highlight.fontWeight)
            val plain = text.spanStyles.single { it.start == 0 }.item
            assertEquals(Color.Unspecified, plain.color)
            assertEquals(Color.Unspecified, plain.background)
            val bold = text.spanStyles.single { it.start == 11 }.item
            assertEquals(FontWeight.Bold, bold.fontWeight)
            assertEquals(Color.Unspecified, bold.background)
        }
    }

    @Test fun defaultFidelityHighlightIsReadableInLightAndDark() = assertReadable(
        listOf(false, true).map { Case(null, it, ThemePaletteStyle.DEFAULT) },
    )

    @Test fun monochromeHighlightsRemainReadableInLightAndDark() = assertReadable(
        listOf(DisplayColorMode.BW, DisplayColorMode.COLOR_EINK).flatMap { mode ->
            listOf(null, 0xFF795548.toInt()).flatMap { seed ->
                listOf(false, true).map { dark -> Case(seed, dark, ThemePaletteStyle.DEFAULT, mode) }
            }
        },
    )

    @Test fun renderedHighlightsAreReadableAcrossStylesSeedsAndLightDark() = assertReadable(
        ThemePaletteStyle.entries.flatMap { style ->
            listOf(null, 0xFF444444.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
                0xFF795548.toInt(), 0xFFFF0000.toInt(), 0xFF0066FF.toInt()).flatMap { seed ->
                listOf(false, true).map { dark -> Case(seed, dark, style) }
            }
        },
    )
}
