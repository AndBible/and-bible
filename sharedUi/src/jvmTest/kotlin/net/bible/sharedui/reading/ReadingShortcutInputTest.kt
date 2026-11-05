package net.bible.sharedui.reading

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A real text-input session, not the visibility of the reading search row, owns editing chords. */
@OptIn(ExperimentalTestApi::class)
class ReadingShortcutInputTest {
    @Test
    fun commandsResumeWhenTextInputLosesFocus() = runComposeUiTest {
        val commands = mutableListOf<String>()
        val requester = FocusRequester()
        setContent {
            ReadingShortcutInput(onKeyEvent = { event ->
                readingShortcut(event)?.let { commands += it; true } ?: false
            }) { modifier ->
                Column(modifier) {
                    var value by remember { mutableStateOf("") }
                    BasicTextField(value, { value = it }, Modifier.testTag("field"))
                    Box(Modifier.size(48.dp).testTag("other").focusRequester(requester).focusable())
                }
            }
        }
        val other = onNodeWithTag("other")
        runOnIdle { requester.requestFocus() }
        other.performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.B); keyUp(Key.B); keyUp(Key.CtrlLeft)
        }
        assertEquals(listOf("CtrlKeyB"), commands)

        onNodeWithTag("field").performClick()
        onNodeWithTag("field").performTextInput("query")
        runOnIdle { requester.requestFocus() }
        other.performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.B); keyUp(Key.B); keyUp(Key.CtrlLeft)
        }
        assertEquals(listOf("CtrlKeyB", "CtrlKeyB"), commands)
    }

    @Test
    fun anUnconsumedControlBFromAnyTextFieldDoesNotReachReading() = runComposeUiTest {
        val commands = mutableListOf<String>()
        setContent {
            ReadingShortcutInput(onKeyEvent = { event ->
                readingShortcut(event)?.let { commands += it; true } ?: false
            }) { modifier ->
                Column(modifier) {
                    var value by remember { mutableStateOf("") }
                    BasicTextField(value, { value = it }, Modifier.testTag("unrelated-text-field"))
                }
            }
        }
        val field = onNodeWithTag("unrelated-text-field")
        field.performClick()
        field.performTextInput("query")
        field.performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.B); keyUp(Key.B); keyUp(Key.CtrlLeft)
        }
        assertEquals(emptyList(), commands, "the parent must not navigate from an editing chord")
    }
}
