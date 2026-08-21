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

package net.bible.android.view.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import net.bible.sharedui.components.AbIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the one thing that made the shipped "off" bolt useless: an outline that is not actually
 *  hollow. Material's `Icons.Outlined.Bolt` is the same solid silhouette as the filled one, so this
 *  round derives the hollow shape from the filled vector's OWN path data — identical silhouette,
 *  stroked instead of filled. If that derivation ever regresses to a fill, the row's false state
 *  silently becomes a colour change again, which is exactly what no golden caught last round. */
class AbIconsTest {

    private fun paths(vector: ImageVector): List<VectorPath> {
        val out = mutableListOf<VectorPath>()
        fun walk(group: VectorGroup) {
            group.forEach { node ->
                when (node) {
                    is VectorPath -> out += node
                    is VectorGroup -> walk(node)
                }
            }
        }
        walk(vector.root)
        return out
    }

    @Test
    fun boltOutline_is_the_filled_silhouette_stroked_not_filled() {
        val filled = paths(Icons.Filled.Bolt)
        val hollow = paths(AbIcons.BoltOutline)

        assertEquals("same number of subpaths as the filled bolt", filled.size, hollow.size)
        assertTrue("the filled bolt must have at least one path", filled.isNotEmpty())
        filled.zip(hollow).forEach { (f, h) ->
            assertEquals("identical silhouette", f.pathData, h.pathData)
            assertNull("must not be filled — a filled outline is indistinguishable from the on state", h.fill)
            assertNotNull("must be stroked", h.stroke)
            assertTrue("stroke must be visible", h.strokeLineWidth > 0f)
        }
    }

    @Test
    fun boltOutline_keeps_the_source_viewport_so_it_scales_like_its_filled_twin() {
        assertEquals(Icons.Filled.Bolt.viewportWidth, AbIcons.BoltOutline.viewportWidth, 0.001f)
        assertEquals(Icons.Filled.Bolt.viewportHeight, AbIcons.BoltOutline.viewportHeight, 0.001f)
        assertEquals(Icons.Filled.Bolt.defaultWidth, AbIcons.BoltOutline.defaultWidth)
        assertEquals(Icons.Filled.Bolt.defaultHeight, AbIcons.BoltOutline.defaultHeight)
    }
}
