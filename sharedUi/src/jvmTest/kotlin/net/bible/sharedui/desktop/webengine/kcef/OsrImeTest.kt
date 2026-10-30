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
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package net.bible.sharedui.desktop.webengine.kcef

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.cef.input.CefCompositionUnderline
import org.cef.misc.CefRange
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsrImeTest {
    private class Sink : OsrImeSink {
        val calls = mutableListOf<String>()
        var underlines: List<CefCompositionUnderline> = emptyList()
        var selection: CefRange? = null
        var replacement: CefRange? = null
        override fun setComposition(text: String, underlines: List<CefCompositionUnderline>, replacement: CefRange, selection: CefRange) {
            calls += "compose:$text"; this.underlines = underlines; this.selection = selection; this.replacement = replacement
        }
        override fun commitText(text: String, replacement: CefRange) { calls += "commit:$text" }
        override fun cancelComposing() { calls += "cancel" }
        override fun finishComposing() { calls += "finish" }
    }

    private val sink = Sink()
    private val ime = OsrIme(sink)

    /** One Compose input-method event: it always commits (possibly empty), then composes when text is left. */
    private fun event(committed: String, composing: String) = ime.editText { s ->
        s.commitText(committed, 1)
        if (composing.isNotEmpty()) s.setComposingText(composing, 1)
    }

    @Test fun imeCompositionForwardedAsSetComposition() {
        event("", "ni")
        assertEquals(listOf("compose:ni"), sink.calls)
        assertEquals(CefRange(2, 2).let { it.from to it.to }, sink.selection!!.let { it.from to it.to })
        assertEquals(-1, sink.replacement!!.from)
        val u = sink.underlines.single()
        assertEquals(0 to 2, u.range.from to u.range.to)
        assertEquals(CefCompositionUnderline.Style.SOLID, u.style)
    }

    @Test fun compositionThatChangesDoesNotFlickerThroughACancel() {
        event("", "ni"); event("", "n")
        assertEquals(listOf("compose:ni", "compose:n"), sink.calls)
    }

    @Test fun imeCommitSentOnce() {
        event("", "ni"); event("你", "")
        assertEquals(listOf("compose:ni", "commit:你"), sink.calls)
    }

    @Test fun partialCommitThenNewComposition() {
        event("", "nihao"); event("你", "hao")
        assertEquals(listOf("compose:nihao", "commit:你", "compose:hao"), sink.calls)
    }

    @Test fun imeCancelSendsCancelComposing() {
        event("", "ni"); event("", "")
        assertEquals(listOf("compose:ni", "cancel"), sink.calls)
        event("", "") // nothing composing any more: no second cancel
        assertEquals(2, sink.calls.size)
    }

    @Test fun emptyEventWithoutCompositionDoesNothing() {
        event("", "")
        assertTrue(sink.calls.isEmpty())
    }

    @Test fun deadKeyComposesAccent() {
        event("", "´"); event("é", "")
        assertEquals(listOf("compose:´", "commit:é"), sink.calls)
    }

    @Test fun deadKeyFollowedByNonComposingCharCommitsBoth() {
        event("", "´"); event("´x", "")
        assertEquals(listOf("compose:´", "commit:´x"), sink.calls)
    }

    @Test fun finishComposingFinishesOnlyWhileComposing() {
        ime.editText { it.finishComposingText() }
        assertTrue(sink.calls.isEmpty())
        event("", "ab")
        ime.editText { it.finishComposingText() }
        assertEquals(listOf("compose:ab", "finish"), sink.calls)
    }

    @Test fun editorStateExposesTheComposition() {
        val state = ime.request().state
        assertEquals(null, state.composition)
        event("", "ni")
        assertEquals("ni", state.toString())
        assertEquals(0 to 2, state.composition!!.let { it.start to it.end })
    }

    @Test fun caretRectFromCompositionRangeChanged() {
        ime.originInRootPx = Offset(100f, 50f)
        ime.scale = 2.0
        assertEquals(Rect(Offset(100f, 50f), androidx.compose.ui.geometry.Size.Zero), ime.focusedRectInRoot())
        ime.onCompositionRangeChanged(CefRange(0, 2), arrayOf(Rectangle(10, 20, 8, 16), Rectangle(18, 20, 8, 16)))
        // The last character, DIP to pixels, plus the pane origin in the window.
        assertEquals(Rect(100f + 36f, 50f + 40f, 100f + 52f, 50f + 72f), ime.focusedRectInRoot())
        assertEquals(ime.focusedRectInRoot(), ime.request().focusedRectInRoot())
        ime.reset()
        assertEquals(Offset(100f, 50f), ime.focusedRectInRoot().topLeft)
    }

    @Test fun renderHandlerForwardsCompositionRange() {
        var seen: Array<Rectangle>? = null
        val h = OsrRenderHandler({ OsrGeometry(10, 10, 1.0, java.awt.Point(0, 0)) }, OsrFrameBuffer(publisher = { it.run() }), {}, { _, b -> seen = b })
        val b = arrayOf(Rectangle(1, 2, 3, 4))
        h.OnImeCompositionRangeChanged(null, CefRange(0, 1), b)
        assertEquals(b.toList(), seen?.toList())
    }
}
