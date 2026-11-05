package net.bible.sharedui.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor

/**
 * Leaves all chords with a focused Compose editor, including keys its own handler does not consume.
 * A platform text-input session covers any descendant field, not just the reading search toolbar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReadingShortcutInput(onKeyEvent: (KeyEvent) -> Boolean, content: @Composable (Modifier) -> Unit) {
    var activeSessions by remember { mutableIntStateOf(0) }
    val interceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            activeSessions++
            try {
                nextHandler.startInputMethod(request)
            } finally {
                activeSessions--
            }
        }
    }
    InterceptPlatformTextInput(interceptor) {
        content(Modifier.onKeyEvent { event ->
            if (activeSessions > 0) false else onKeyEvent(event)
        })
    }
}
