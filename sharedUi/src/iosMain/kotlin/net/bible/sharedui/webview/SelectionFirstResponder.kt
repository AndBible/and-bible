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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.webview

import platform.UIKit.UIKeyInputProtocol
import platform.UIKit.UIView

/** The view in [root]'s hierarchy (root included) that is first responder, or null. */
internal fun firstResponderIn(root: UIView): UIView? {
    if (root.isFirstResponder()) return root
    for (child in root.subviews) {
        firstResponderIn(child as UIView)?.let { return it }
    }
    return null
}

/**
 * Makes [webView] first responder once its pane is shown and its route resumed, before the user can start a
 * text-selection gesture on it (issue #3968). If the content view only becomes first responder *during* the long press,
 * WebKit selects the word but UIKit never builds or shows the edit menu for it: the first selection after launch, or after
 * returning from another route (the window then has no first responder), had no menu while every later one did. A claim
 * at touch-down was measured too late; seconds ahead (at RESUMED) the menu appears. [current] is the window's first
 * responder now.
 *
 * Nothing is claimed while the web view (its content view) already is first responder, nor from a text-input view
 * elsewhere (a focused text field keeps its keyboard). Returns whether it claimed.
 */
internal fun claimFirstResponderForSelection(
    webView: UIView,
    current: UIView?,
    become: () -> Boolean = { webView.becomeFirstResponder() },
): Boolean {
    if (current != null && (current.isDescendantOfView(webView) || current is UIKeyInputProtocol)) return false
    return become()
}
