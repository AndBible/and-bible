/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 * AndBible is free software under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import net.bible.sharedui.reading.isHardwareKeyEvent

/**
 * Whether the user's latest Find interaction was a hardware key rather than a touch or pointer
 * press. Find results take focus and show their keyboard selection only for keyboard users (#4139).
 *
 * `LocalInputModeManager` alone cannot say this on desktop and iOS: Compose's skiko owner switches
 * to Touch on every mouse press and back to Keyboard only on an unconsumed focus-navigation key, so
 * clicking the field and typing a query would leave a keyboard user in Touch mode. Process-wide like
 * Android's touch mode: there is one user with one keyboard.
 */
internal object FindKeyboardInput {
    var lastWasHardwareKey by mutableStateOf(false)
}

/** Records hardware key presses and pointer presses on the Find field. Consumes nothing. */
internal fun Modifier.trackFindKeyboardInput(): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && isHardwareKeyEvent(event)) FindKeyboardInput.lastWasHardwareKey = true
    false
}.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                FindKeyboardInput.lastWasHardwareKey = false
            }
        }
    }
}
