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
package net.bible.sharedui.desktop.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.input.key.Key
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput

/**
 * Desktop back navigation: Esc, Alt+Left (Alt+Right in RTL) and the mouse back button reach the Compose back stack
 * (the common back handlers) the same way the system Back does elsewhere.
 *
 * Compose already feeds an Esc pressed in a focused Compose window to its own scene. CEF panes hold the keyboard
 * focus while the user reads, so their keys arrive through the page script and need [back] explicitly. The
 * dispatcher knows every scene's NavigationEventDispatcher that has composed back handlers (see [Attach]); [back]
 * completes a back event in the most recently attached one that has an enabled handler.
 *
 * Threading: [back] and [passThrough] are Swing (AWT event) thread only; the `unhandled` flag is not synchronized. A
 * pass-through can reach an older attachment (another window's scene) when the topmost one has none left; harmless
 * with the single main window.
 */
object DesktopBackDispatcher {
    /** A navigation request produced by a key or a mouse button. */
    enum class Navigation { Back, Forward }

    private class Tracking : NavigationEventInput() {
        var hasEnabled = false
            private set

        override fun onHasEnabledHandlersChanged(enabled: Boolean) {
            hasEnabled = enabled
        }

        fun complete() = dispatchOnBackCompleted()
    }

    private class Attachment(val dispatcher: NavigationEventDispatcher, val input: Tracking, var refs: Int)

    private val lock = Any()
    private val attachments = mutableListOf<Attachment>() // oldest first, last = topmost
    private var unhandled = false

    private fun attach(dispatcher: NavigationEventDispatcher) = synchronized(lock) {
        val existing = attachments.firstOrNull { it.dispatcher === dispatcher }
        if (existing != null) {
            existing.refs++
        } else {
            val input = Tracking()
            dispatcher.addInput(input)
            attachments += Attachment(dispatcher, input, 1)
        }
    }

    private fun detach(dispatcher: NavigationEventDispatcher) = synchronized(lock) {
        val a = attachments.firstOrNull { it.dispatcher === dispatcher } ?: return@synchronized
        if (--a.refs <= 0) {
            attachments.remove(a)
            // Can fail when the scene was already disposed (its dispatcher is gone with it); nothing is left to clean up then.
            runCatching { dispatcher.removeInput(a.input) }
                .onFailure { System.err.println("desktop back dispatcher: removeInput failed (scene already disposed?): $it") }
        }
    }

    /** Registers the current scene's dispatcher with this object while the caller stays in the composition. */
    @Composable
    fun Attach() {
        val dispatcher = currentDispatcher()
        DisposableEffect(dispatcher) {
            if (dispatcher != null) attach(dispatcher)
            onDispose { if (dispatcher != null) detach(dispatcher) }
        }
    }

    /**
     * The back-event dispatcher of the current scene, null when the host provides none. It is the composition local
     * the common `BackHandler` reads (`LocalCompatNavigationEventDispatcherOwner`, an internal-use API of
     * compose-ui that :sharedUi's Compose version pins).
     */
    @OptIn(InternalComposeUiApi::class)
    @Composable
    internal fun currentDispatcher(): NavigationEventDispatcher? =
        LocalCompatNavigationEventDispatcherOwner.current?.navigationEventDispatcher

    private fun dispatchTopmost(): Boolean {
        val top = synchronized(lock) { attachments.lastOrNull { it.input.hasEnabled } } ?: return false
        top.input.complete()
        return true
    }

    /**
     * Completes a back event in the topmost scene that has an enabled back handler (the topmost handler runs first).
     * Returns true when a handler consumed it, false when there was none or every one passed it through.
     */
    fun back(): Boolean {
        unhandled = false
        return dispatchTopmost() && !unhandled
    }

    /**
     * Called by a pass-through handler (already disabled by the caller) that did not handle the back event: offers it
     * to the handlers below. With none left the event counts as unhandled, so [back] returns false.
     */
    internal fun passThrough() {
        if (!dispatchTopmost()) unhandled = true
    }

    /**
     * Runs [navigation]. Back goes to [back]; forward is consumed without effect, as AndBible has no forward history.
     * Returns whether a Compose back handler consumed a back request (always true for forward).
     */
    fun perform(navigation: Navigation): Boolean = when (navigation) {
        Navigation.Back -> back()
        Navigation.Forward -> true
    }

    /**
     * The navigation an arrow or Escape key means. Alt+Left is back in LTR and forward in RTL, Alt+Right the reverse.
     * Escape means back only when [fromPane] (a page key: Compose delivers Esc in its own windows to its back
     * handlers by itself, handling it again would go back twice).
     */
    fun navigationForKey(key: Key, alt: Boolean, isRtl: Boolean, fromPane: Boolean = false): Navigation? = when {
        key == Key.Escape -> if (fromPane) Navigation.Back else null
        !alt -> null
        key == Key.DirectionLeft -> if (isRtl) Navigation.Forward else Navigation.Back
        key == Key.DirectionRight -> if (isRtl) Navigation.Back else Navigation.Forward
        else -> null
    }

    /** As [navigationForKey] for a DOM `KeyboardEvent.key`/`code` ("Escape", "ArrowLeft", "ArrowRight") from a pane. */
    fun navigationForDomKey(key: String, alt: Boolean, isRtl: Boolean): Navigation? = when (key) {
        "Escape" -> Navigation.Back
        "ArrowLeft" -> navigationForKey(Key.DirectionLeft, alt, isRtl)
        "ArrowRight" -> navigationForKey(Key.DirectionRight, alt, isRtl)
        else -> null
    }

    /** DOM `MouseEvent.button`: 3 = the back button, 4 = the forward button. */
    fun navigationForDomMouseButton(button: Int): Navigation? = when (button) {
        3 -> Navigation.Back
        4 -> Navigation.Forward
        else -> null
    }

    /** Whether the AWT toolkit is the X11 one, whose button numbering differs (see [navigationForAwtMouseButton]). */
    fun isX11Toolkit(): Boolean = java.awt.Toolkit.getDefaultToolkit().javaClass.name == "sun.awt.X11.XToolkit"

    /**
     * AWT `MouseEvent.getButton()`. On X11 ([x11] = true) AWT numbers X buttons 6/7 (horizontal scroll/tilt) as presses
     * 4/5 and the X back/forward buttons 8/9 as 6/7, so only 6 = back and 7 = forward count (4/5 do nothing, or a
     * horizontal scroll would go back). Elsewhere (Windows, macOS) 4 = back and 5 = forward.
     */
    fun navigationForAwtMouseButton(button: Int, x11: Boolean = isX11Toolkit()): Navigation? =
        if (x11) when (button) {
            6 -> Navigation.Back
            7 -> Navigation.Forward
            else -> null
        } else when (button) {
            4 -> Navigation.Back
            5 -> Navigation.Forward
            else -> null
        }
}
