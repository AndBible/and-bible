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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class VolumeButtonScrollTest {
    private val context get() = RuntimeEnvironment.getApplication()

    /**
     * Give this view (and all its descendants) a non-zero height so the visibility/size
     * guard passes. Must be called *after* the whole tree is assembled — laying out a
     * parent in Robolectric re-lays-out its children with their measured size (0), which
     * would otherwise clobber a height set on a child before it was added.
     */
    private fun <T : View> T.laidOut(h: Int = 200): T = apply {
        layout(0, 0, 100, h)
        if (this is ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).laidOut(h)
        }
    }

    @Test
    fun `finds RecyclerView nested in a container`() {
        val recycler = RecyclerView(context)
        val root = LinearLayout(context).apply { addView(recycler) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), sameInstance<View>(recycler))
    }

    @Test
    fun `finds ListView (AbsListView) nested in a container`() {
        val list = ListView(context)
        val root = LinearLayout(context).apply { addView(TextView(context)); addView(list) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), instanceOf(ListView::class.java))
    }

    @Test
    fun `finds NestedScrollView`() {
        val scroll = NestedScrollView(context)
        val root = LinearLayout(context).apply { addView(scroll) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), instanceOf(NestedScrollView::class.java))
    }

    @Test
    fun `skips GONE scrollable view`() {
        val recycler = RecyclerView(context).apply { visibility = View.GONE }
        val root = LinearLayout(context).apply { addView(recycler) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), nullValue())
    }

    @Test
    fun `skips zero-height scrollable view`() {
        val recycler = RecyclerView(context)
        // Root laid out, but the scrollable child is forced back to height 0.
        val root = LinearLayout(context).apply { addView(recycler) }.laidOut()
        recycler.layout(0, 0, 0, 0)
        assertThat(VolumeButtonScroll.findScrollableView(root), nullValue())
    }

    @Test
    fun `returns null when nothing scrollable`() {
        val root = LinearLayout(context).apply { addView(TextView(context)) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), nullValue())
    }

    @Test
    fun `returns first scrollable in depth-first order`() {
        val first = ScrollView(context)
        val second = RecyclerView(context)
        val root = LinearLayout(context).apply { addView(first); addView(second) }.laidOut()
        assertThat(VolumeButtonScroll.findScrollableView(root), sameInstance<View>(first))
    }

    @Test
    fun `pageDelta scrolls down by ~90 percent of height`() {
        assertThat(VolumeButtonScroll.pageDelta(200, down = true), equalTo(180))
    }

    @Test
    fun `pageDelta scrolls up by negative ~90 percent of height`() {
        assertThat(VolumeButtonScroll.pageDelta(200, down = false), equalTo(-180))
    }

    @Test
    fun `pageDelta scales with height`() {
        assertThat(VolumeButtonScroll.pageDelta(1000, down = true), equalTo(900))
    }

    /**
     * Smoke-test scroll() over each per-type dispatch arm. The test asserts no exception
     * is thrown — this catches wrong API signatures / NoSuchMethodError, which is the most
     * likely silent breakage in the per-type dispatch (the AbsListView branch in particular
     * uses a different API shape: smoothScrollBy(distance, duration) / scrollListBy(distance)
     * rather than smoothScrollBy(0, delta) / scrollBy(0, delta)).
     */
    private fun exerciseScroll(view: View) {
        view.laidOut()
        VolumeButtonScroll.scroll(view, down = true, animate = false)
        VolumeButtonScroll.scroll(view, down = false, animate = false)
        VolumeButtonScroll.scroll(view, down = true, animate = true)
        VolumeButtonScroll.scroll(view, down = false, animate = true)
        // Reaching here without an exception is the assertion.
        assertThat(true, equalTo(true))
    }

    @Test
    fun `scroll on RecyclerView does not throw`() {
        exerciseScroll(RecyclerView(context))
    }

    @Test
    fun `scroll on ListView (AbsListView) does not throw`() {
        exerciseScroll(ListView(context))
    }

    @Test
    fun `scroll on ScrollView does not throw`() {
        exerciseScroll(ScrollView(context))
    }

    @Test
    fun `scroll on NestedScrollView does not throw`() {
        exerciseScroll(NestedScrollView(context))
    }
}
