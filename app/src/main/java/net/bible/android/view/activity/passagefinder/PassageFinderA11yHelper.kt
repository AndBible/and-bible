/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.view.activity.passagefinder

import android.graphics.Rect
import android.os.Bundle
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/** What activating an accessibility node should do. */
sealed class A11yTarget {
    /** Select the book at [index] and drill into its chapters. */
    data class Book(val index: Int) : A11yTarget()

    /** Select chapter [number] (1-based) and drill into its verses. */
    data class Chapter(val number: Int) : A11yTarget()

    /** Select verse [number] (1-based). */
    data class Verse(val number: Int) : A11yTarget()

    /** Commit the current selection and navigate there. */
    object Confirm : A11yTarget()
}

/**
 * One item exposed to accessibility services.
 *
 * @param id stable virtual view id; see [PassageFinderA11yHelper] for the numbering.
 * @param bounds position in the host view's coordinates, slide offset included.
 */
data class A11yNode(
    val id: Int,
    val label: String,
    val bounds: Rect,
    val target: A11yTarget,
    val selected: Boolean,
)

/**
 * Exposes the hand-drawn passage finder to TalkBack and other accessibility services.
 *
 * A self-drawn view has no child views for an accessibility service to find, so every
 * spine, chapter cell, verse cell and the preview bubble is published as a *virtual*
 * view through [ExploreByTouchHelper]. That gives explore-by-touch, linear navigation
 * and activation on the drawn items exactly as if each were a real child, which is what
 * the Compose implementation got from its `semantics` modifiers.
 *
 * Virtual ids are derived from the item's identity rather than its position in the list
 * of visible nodes, so a node keeps its id as the strips scroll and a service holding
 * accessibility focus does not lose it.
 */
class PassageFinderA11yHelper(
    private val view: PassageFinderView,
) : ExploreByTouchHelper(view) {

    private val scratchBounds = Rect()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        for (node in view.accessibilityNodes()) {
            if (node.bounds.contains(x.toInt(), y.toInt())) return node.id
        }
        return HOST_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        for (node in view.accessibilityNodes()) {
            virtualViewIds.add(node.id)
        }
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat,
    ) {
        val item = view.accessibilityNodes().firstOrNull { it.id == virtualViewId }
        if (item == null) {
            // The item scrolled away between the service's query and now. A node must
            // still be populated with non-empty bounds or the framework complains.
            node.contentDescription = ""
            scratchBounds.set(0, 0, 1, 1)
            node.setBoundsInParent(scratchBounds)
            return
        }
        node.contentDescription = item.label
        node.className = "android.widget.Button"
        node.isClickable = true
        node.isSelected = item.selected
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        node.setBoundsInParent(item.bounds)
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?,
    ): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
        val item = view.accessibilityNodes().firstOrNull { it.id == virtualViewId } ?: return false
        view.onAccessibilityAction(item.target)
        sendEventForVirtualView(virtualViewId, android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
        return true
    }

    companion object {
        /** Virtual id of the preview bubble's confirm target. */
        const val ID_CONFIRM = 1

        /** Virtual ids of book spines start here, offset by the book index. */
        const val ID_BOOK_BASE = 10_000_000

        /** Virtual ids of chapter cells start here, offset by the chapter number. */
        const val ID_CHAPTER_BASE = 20_000_000

        /** Virtual ids of verse cells start here, offset by the verse number. */
        const val ID_VERSE_BASE = 30_000_000
    }
}
