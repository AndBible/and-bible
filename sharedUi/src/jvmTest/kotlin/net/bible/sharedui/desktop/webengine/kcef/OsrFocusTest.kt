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
package net.bible.sharedui.desktop.webengine.kcef

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.sharedui.desktop.webengine.DesktopWebPaneKeys
import java.awt.event.KeyEvent as Awt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class OsrFocusTest {
    private class Sink : OsrKeyboardSink {
        val focus = mutableListOf<Boolean>()
        val keys = mutableListOf<Awt>()
        override fun setFocus(focused: Boolean) { focus += focused }
        override fun sendKey(e: Awt) { keys += e }
    }

    private val sink = Sink()
    private val pane = OsrFocus(sink)

    @AfterTest fun clear() = pane.release()

    private fun androidx.compose.ui.test.ComposeUiTest.setUpContent(otherRequester: FocusRequester, window: (String) -> Unit = {}) {
        setContent {
            Column(
                Modifier
                    .onPreviewKeyEvent { DesktopWebPaneKeys.windowKey { window("preview"); false } }
                    .onKeyEvent { DesktopWebPaneKeys.windowKey { window("bubble"); false } },
            ) {
                Box(Modifier.size(20.dp).testTag("pane").osrKeyboard(pane))
                Box(Modifier.size(20.dp).testTag("other").focusRequester(otherRequester).focusable())
            }
        }
    }

    @Test fun focusGainCallsSetFocusTrue() = runComposeUiTest {
        setUpContent(FocusRequester())
        runOnIdle { pane.requester.requestFocus() }
        runOnIdle {
            assertEquals(listOf(true), sink.focus)
            assertTrue(DesktopWebPaneKeys.ownsKeys)
        }
    }

    @Test fun focusLossCallsSetFocusFalse() = runComposeUiTest {
        val other = FocusRequester()
        setUpContent(other)
        runOnIdle { pane.requester.requestFocus() }
        runOnIdle { other.requestFocus() }
        runOnIdle {
            assertEquals(listOf(true, false), sink.focus)
            assertFalse(DesktopWebPaneKeys.ownsKeys)
        }
    }

    @Test fun keyForwardedOnceAndNotSeenByWindowHandler() = runComposeUiTest {
        val seen = mutableListOf<String>()
        setUpContent(FocusRequester()) { seen += it }
        runOnIdle { pane.requester.requestFocus() }
        onNodeWithTagPane().performKeyInput { pressKey(Key.B) }
        runOnIdle {
            assertEquals(listOf(Awt.KEY_PRESSED, Awt.KEY_RELEASED), sink.keys.map { it.id })
            assertEquals(emptyList(), seen)
        }
    }

    @Test fun keysStopWhilePaneUnfocused() = runComposeUiTest {
        val other = FocusRequester()
        val seen = mutableListOf<String>()
        setUpContent(other) { seen += it }
        runOnIdle { pane.requester.requestFocus() }
        runOnIdle { other.requestFocus() }
        onNodeWithTag("other").performKeyInput { pressKey(Key.B) }
        runOnIdle {
            assertEquals(emptyList(), sink.keys)
            // The window handler runs again for the other node's key (down and up).
            assertEquals(listOf("preview", "bubble", "preview", "bubble"), seen)
        }
    }

    @Test fun tabIsForwardedAndFocusStaysInThePane() = runComposeUiTest {
        setUpContent(FocusRequester())
        runOnIdle { pane.requester.requestFocus() }
        onNodeWithTagPane().performKeyInput { pressKey(Key.Tab) }
        runOnIdle {
            assertEquals(listOf(Awt.VK_TAB, Awt.VK_TAB), sink.keys.map { it.keyCode })
            assertTrue(pane.isFocused)
            assertEquals(listOf(true), sink.focus)
        }
    }

    @Test fun pageMousedownRequestsComposeFocusInOsr() = runComposeUiTest {
        setUpContent(FocusRequester())
        // The OSR pane's requestFocus action is the Compose requester; the X11 helper belongs to the windowed pane only.
        val handled = PaneFocusRequest.handle(PaneFocusRequest.REQUEST, { runOnIdle { pane.requestFocus() } }, {})
        assertTrue(handled)
        runOnIdle {
            assertTrue(pane.isFocused)
            assertEquals(listOf(true), sink.focus)
        }
    }

    @Test fun focusRequestBeforeTheNodeExistsIsIgnored() {
        pane.requestFocus() // no composition: must not throw
        assertFalse(pane.isFocused)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onNodeWithTagPane() = onNodeWithTag("pane")
    private fun androidx.compose.ui.test.ComposeUiTest.onNodeWithTag(t: String) = onNode(androidx.compose.ui.test.hasTestTag(t))
}
