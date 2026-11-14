package net.bible.sharedui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** An API key must not be readable on screen unless the user asks for it with the eye button. */
@OptIn(ExperimentalTestApi::class)
class AbSecretTextFieldTest {
    private val strings = iosStrings("en")
    private fun androidx.compose.ui.test.ComposeUiTest.shownText() =
        onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun androidx.compose.ui.test.ComposeUiTest.show() = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) { AbSecretTextField(value = "sk-secret", onValueChange = {}, label = "API key") }
        }
    }

    @Test fun isMaskedByDefault() = runComposeUiTest {
        show()
        assertEquals("•".repeat("sk-secret".length), shownText())
        onNodeWithContentDescription(strings.showSecret).assertExists()
    }

    @Test fun eyeButtonRevealsAndHidesAgain() = runComposeUiTest {
        show()
        onNodeWithContentDescription(strings.showSecret).performClick()
        assertEquals("sk-secret", shownText())
        onNodeWithContentDescription(strings.hideSecret).performClick()
        assertEquals("•".repeat("sk-secret".length), shownText())
    }
}
