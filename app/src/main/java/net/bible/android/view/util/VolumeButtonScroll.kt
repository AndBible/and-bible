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
package net.bible.android.view.util

import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

/**
 * Generic hardware volume-key page scrolling for list/scroll screens.
 *
 * Locates the first visible scrollable container in a view tree and page-scrolls it,
 * so e-ink users can navigate any [net.bible.android.view.activity.base.ActivityBase]
 * screen with the volume buttons, the same way the main BibleView already works.
 */
object VolumeButtonScroll {
    /** Fraction of the visible height scrolled per key press (leaves a small overlap). */
    private const val PAGE_FRACTION = 0.9

    /** Smooth-scroll animation duration (ms) for AbsListView, which needs an explicit duration. */
    private const val LIST_SMOOTH_DURATION = 250

    /**
     * Depth-first search for the first visible, laid-out scrollable container.
     * Type-based (not [View.canScrollVertically]) so the view is still found when
     * already scrolled to an edge, leaving the opposite direction usable.
     */
    fun findScrollableView(root: View): View? {
        if (root.visibility != View.VISIBLE || root.height <= 0) return null
        if (isScrollable(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findScrollableView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun isScrollable(view: View): Boolean =
        view is RecyclerView || view is AbsListView || view is ScrollView || view is NestedScrollView

    /** Signed page delta in pixels: positive scrolls down, negative scrolls up. */
    fun pageDelta(viewHeight: Int, down: Boolean): Int {
        val amount = (viewHeight * PAGE_FRACTION).toInt()
        return if (down) amount else -amount
    }

    /** Page-scroll [view] one screenful [down] or up, smoothly unless [animate] is false. */
    fun scroll(view: View, down: Boolean, animate: Boolean) {
        val delta = pageDelta(view.height, down)
        when (view) {
            is RecyclerView -> if (animate) view.smoothScrollBy(0, delta) else view.scrollBy(0, delta)
            is NestedScrollView -> if (animate) view.smoothScrollBy(0, delta) else view.scrollBy(0, delta)
            is ScrollView -> if (animate) view.smoothScrollBy(0, delta) else view.scrollBy(0, delta)
            is AbsListView -> if (animate) view.smoothScrollBy(delta, LIST_SMOOTH_DURATION) else view.scrollListBy(delta)
        }
    }
}
