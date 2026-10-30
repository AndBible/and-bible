/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(ExperimentalComposeUiApi::class)

package net.bible.sharedui.desktop.webengine.kcef

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.cef.input.CefCompositionUnderline
import org.cef.misc.CefRange
import java.awt.Color
import java.awt.Rectangle

/** Where the IME bridge sends composition to; implemented over the CEF browser's `Ime*` calls. */
internal interface OsrImeSink {
    /** The input method's pre-edit [text] (shown underlined by the page), with the caret or selection at [selection]. */
    fun setComposition(text: String, underlines: List<CefCompositionUnderline>, replacement: CefRange, selection: CefRange)

    /** Final [text] from the input method; replaces the composition. */
    fun commitText(text: String, replacement: CefRange)

    /** The input method abandoned the composition. */
    fun cancelComposing()

    /** The composition is kept as it stands and ends (`ImeFinishComposingText`). */
    fun finishComposing()
}

/**
 * IME bridge of the off-screen pane. Chosen path: Compose's platform text input. The window's AWT component only forwards
 * `InputMethodEvent`s to a text input session the focused node establishes (`DesktopTextInputService2`); a second listener
 * on the component would fight it and would not receive `getTextLocation`. So the pane's node establishes a session while
 * focused ([OsrImeElement]); Compose turns each `InputMethodEvent` into [editText] calls ("commit this, then compose that"),
 * which become CEF's composition calls here, and Compose answers the input method's `getTextLocation` from
 * [PlatformTextInputMethodRequest.focusedRectInRoot], which is the candidate rectangle CEF reports through
 * `OnImeCompositionRangeChanged` ([onCompositionRangeChanged]).
 *
 * Compose emits `commitText(committed)` for every input-method event, empty or not, followed by `setComposingText` while
 * composing. An empty commit with no composing text after it therefore means the composition was cancelled; that is decided
 * at the end of each [editText] batch so a changing composition ("ni" then "n") never flickers through a cancel.
 */
internal class OsrIme(private val sink: OsrImeSink) {
    private var composing = ""

    @Volatile
    private var caretDip: Rectangle? = null

    /** Position of the pane in the window in Compose pixels, kept up to date by the pane's layout. */
    @Volatile
    var originInRootPx: Offset = Offset.Zero

    /** Compose pixels per DIP. */
    @Volatile
    var scale: Double = 1.0

    /** The editor state Compose reads between events: the composing text only, committed text is the page's. */
    private val state = object : TextEditorState, CharSequence by "" {
        override val length: Int get() = composing.length
        override fun get(index: Int): Char = composing[index]
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = composing.subSequence(startIndex, endIndex)
        override fun toString(): String = composing
        override val selection: TextRange get() = TextRange(composing.length)
        override val composition: TextRange? get() = if (composing.isEmpty()) null else TextRange(0, composing.length)
    }

    /** Runs one batch of Compose's edit calls against the CEF sink. */
    fun editText(block: (TextEditingScope) -> Unit) {
        val scope = Scope()
        block(scope)
        if (scope.cancelPending) {
            composing = ""
            sink.cancelComposing()
        }
    }

    private inner class Scope : TextEditingScope {
        var cancelPending = false

        override fun deleteSurroundingTextInCodePoints(lengthBeforeCursor: Int, lengthAfterCursor: Int) {
            // The page owns the text; only a macOS dead-key workaround of Compose asks for this.
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int) {
            if (text.isEmpty()) {
                if (composing.isNotEmpty()) cancelPending = true
                return
            }
            composing = ""
            cancelPending = false
            sink.commitText(text.toString(), NO_RANGE)
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int) {
            cancelPending = false
            composing = text.toString()
            if (composing.isEmpty()) return
            val len = composing.length
            sink.setComposition(
                composing,
                listOf(CefCompositionUnderline(CefRange(0, len), Color.BLACK, Color(0, 0, 0, 0), 0, CefCompositionUnderline.Style.SOLID)),
                NO_RANGE,
                CefRange(len, len),
            )
        }

        override fun finishComposingText() {
            if (composing.isEmpty()) return
            composing = ""
            sink.finishComposing()
        }
    }

    /**
     * CEF's report of where the composition is: [bounds] are the character rectangles of the composition in view DIP.
     * Called on CEF's UI thread. The candidate window sits under the last character (the caret).
     */
    fun onCompositionRangeChanged(range: CefRange?, bounds: Array<Rectangle>?) {
        caretDip = bounds?.lastOrNull()?.let { Rectangle(it) }
    }

    /** The caret or candidate rectangle in the window's Compose pixels (an empty rectangle at the pane origin before CEF reported one). */
    fun focusedRectInRoot(): Rect {
        val o = originInRootPx
        val r = caretDip ?: return Rect(o, Size.Zero)
        return Rect(o.x + (r.x * scale).toFloat(), o.y + (r.y * scale).toFloat(), o.x + ((r.x + r.width) * scale).toFloat(), o.y + ((r.y + r.height) * scale).toFloat())
    }

    /** Forgets the composition and caret (focus left the pane). */
    fun reset() {
        composing = ""
        caretDip = null
    }

    /** The request a focused pane hands to Compose's text input session. */
    @OptIn(ExperimentalComposeUiApi::class)
    fun request(): PlatformTextInputMethodRequest = object : PlatformTextInputMethodRequest {
        override val value: () -> TextFieldValue = { TextFieldValue(composing, TextRange(composing.length), state.composition) }
        override val state: TextEditorState get() = this@OsrIme.state
        override val imeOptions: ImeOptions = ImeOptions.Default
        override val onEditCommand: (List<androidx.compose.ui.text.input.EditCommand>) -> Unit = {}
        override val onImeAction: ((androidx.compose.ui.text.input.ImeAction) -> Unit)? = null
        override val textLayoutResult: () -> androidx.compose.ui.text.TextLayoutResult? = { null }
        override val focusedRectInRoot: () -> Rect? = { this@OsrIme.focusedRectInRoot() }
        override val textFieldRectInRoot: () -> Rect? = { this@OsrIme.focusedRectInRoot() }
        override val textClippingRectInRoot: () -> Rect? = { this@OsrIme.focusedRectInRoot() }
        override val unclippedTextOffsetInRoot: () -> Offset? = { this@OsrIme.focusedRectInRoot().topLeft }
        override val editText: (TextEditingScope.() -> Unit) -> Unit = { block -> this@OsrIme.editText { it.block() } }
    }

    companion object {
        /** CEF's "no range" value. */
        val NO_RANGE = CefRange(-1, -1)
    }
}

/** Modifier element that establishes Compose's text input session for [ime] while the node is focused. */
internal data class OsrImeElement(val ime: OsrIme) : ModifierNodeElement<OsrImeNode>() {
    override fun create() = OsrImeNode(ime)
    override fun update(node: OsrImeNode) { node.ime = ime }
}

internal class OsrImeNode(var ime: OsrIme) : Modifier.Node(), PlatformTextInputModifierNode, FocusEventModifierNode {
    private var session: Job? = null

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused) start() else stop()
    }

    private fun start() {
        if (session?.isActive == true) return
        session = coroutineScope.launch { establishTextInputSession { startInputMethod(ime.request()) } }
    }

    private fun stop() {
        session?.cancel()
        session = null
        ime.reset()
    }

    override fun onDetach() = stop()
}
