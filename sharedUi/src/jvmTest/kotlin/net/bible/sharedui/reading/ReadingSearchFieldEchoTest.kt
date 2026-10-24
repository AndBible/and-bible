package net.bible.sharedui.reading

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
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

    /** Types [text] one key at a time while the host echoes the text [lag] keystrokes behind; returns what the field shows at the end. */
    private fun typeWithLaggingEcho(text: String, lag: Int): Pair<List<String>, String> {
        val sent = mutableListOf<String>()
        var shown = ""
        runComposeUiTest {
            var hostValue by mutableStateOf("")
            setContent {
                ReadingSearchField(
                    value = hostValue, onValueChange = { sent += it }, textStyle = TextStyle.Default,
                    cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Black),
                    keyboardOptions = KeyboardOptions.Default, keyboardActions = KeyboardActions.Default,
                )
            }
            val field = onNodeWithTag(SEARCH_FIELD_TAG)
            for (i in text.indices) {
                field.performTextInput(text[i].toString())
                // the echo of the text sent `lag` keys ago arrives only now
                val echoed = i - lag
                if (echoed >= 0) { hostValue = text.substring(0, echoed + 1); waitForIdle() }
            }
            waitForIdle()
            // the last `lag` echoes arrive after the final key, in order; none may revert the field
            for (echoed in maxOf(0, text.length - lag) until text.length) {
                hostValue = text.substring(0, echoed + 1); waitForIdle()
            }
            val sentBefore = sent.size
            waitForIdle()
            assertEquals(sentBefore, sent.size, "lag $lag: delivering echoes must not send anything more")
            shown = field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        }
        return sent to shown
    }

    @Test
    fun typedNicodemusSurvivesLaggingEcho() {
        for (lag in 1..3) {
            val (sent, last) = typeWithLaggingEcho("Nicodemus", lag)
            assertEquals("Nicodemus", last, "lag $lag: the field shows with exactly the typed text, sent=$sent")
            assertEquals("Nicodemus".indices.map { "Nicodemus".substring(0, it + 1) }, sent, "lag $lag: no key lands on stale text")
        }
    }

    @Test
    fun typedStrongsQuerySurvivesLaggingEcho() {
        for (lag in 1..3) {
            val (sent, last) = typeWithLaggingEcho("strong:g3530", lag)
            assertEquals("strong:g3530", last, "lag $lag: sent=$sent")
            assertEquals("strong:g3530".indices.map { "strong:g3530".substring(0, it + 1) }, sent, "lag $lag: no key lands on stale text")
        }
    }
}
