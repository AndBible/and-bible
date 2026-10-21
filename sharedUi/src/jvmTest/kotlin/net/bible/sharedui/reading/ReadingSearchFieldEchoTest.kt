package net.bible.sharedui.reading

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The host publishes the query one hop after the keystroke (`combine`/`stateIn`, then `collectAsState`). Echoes of text the
 * user has already typed past must not overwrite the field, or the next key is typed onto stale text and a character is lost.
 */
@OptIn(ExperimentalTestApi::class)
class ReadingSearchFieldEchoTest {
    @Test
    fun lateEchoOfAnOlderEditDoesNotEatTheNextKey() = runComposeUiTest {
        val sent = mutableListOf<String>()
        var hostValue by mutableStateOf("")
        setContent {
            ReadingSearchField(
                value = hostValue, onValueChange = { sent += it }, textStyle = TextStyle.Default,
                cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Black),
                keyboardOptions = KeyboardOptions.Default, keyboardActions = KeyboardActions.Default,
            )
        }
        val field = onNodeWithTag(SEARCH_FIELD_TAG)
        field.performTextInput("a")
        field.performTextInput("b")
        hostValue = "a" // the echo of the first key lands after the second was typed
        waitForIdle()
        field.performTextInput("c")
        hostValue = "ab"
        waitForIdle()
        hostValue = "abc"
        waitForIdle()
        assertEquals(listOf("a", "ab", "abc"), sent, "every key lands on the text the user saw")
    }
}
