package net.bible.sharedui.download

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedcore.download.EditorState
import net.bible.sharedcore.download.Validation
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CustomRepositoryEditorHttpsMessageTest {
    private val strings = iosStrings("en")
    private val reason = "Custom repositories must use a secure https:// address."

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: EditorState) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                CustomRepositoryEditorScreen(
                    state = state, onUrlChange = {}, onPaste = {}, onPackageDirChange = {},
                    onSave = {}, onDelete = {}, onUp = {},
                )
            }
        }
    }

    @Test fun uppercaseHttpGetsHttpsReason() = runComposeUiTest {
        assertEquals(reason, strings.customRepositoryHttpsRequired)
        show(EditorState(url = "HTTP://example.org/x", validation = Validation.InsecureUrl))
        onNodeWithText(reason).assertIsDisplayed()
    }

    @Test fun invalidUrlHasNoHttpsReason() = runComposeUiTest {
        show(EditorState(url = "ftp://x", validation = Validation.Invalid))
        onNodeWithText(reason).assertDoesNotExist()
    }
}
