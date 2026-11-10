package net.bible.sharedui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.strings.keyed.genericFontChoice

@OptIn(ExperimentalTestApi::class)
class FontChoiceTest {
    @Test fun fontChoiceRowUsesTheSelectedTypeface() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AbListChoiceContent(listOf(genericFontChoice("serif")), "serif", {})
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("Serif", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(FontFamily.Serif, layouts.single().layoutInput.style.fontFamily)
    }
}
