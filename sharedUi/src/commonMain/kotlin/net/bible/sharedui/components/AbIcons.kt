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

package net.bible.sharedui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * Vectors this app needs and Material does not have.
 *
 * [BoltOutline] exists because **Material's outlined bolt is not hollow**: `Icons.Outlined.Bolt` is
 * the same solid lightning as `Icons.Filled.Bolt`, so an on/off pair built from them differs only in
 * tint — which is the round-10a auto-assign toggle the device feedback called out as having no false
 * state at all (the heart's filled/hollow pair is what it is compared against).
 *
 * Rather than hand-transcribe an SVG path (a silhouette that would drift from the filled twin at the
 * first Material update), the hollow variant is derived from the filled vector's own `pathData`,
 * stroked instead of filled. Same shape by construction; only the paint changes.
 */
object AbIcons {
    /** The filled bolt's silhouette as an outline — the "auto-assign is off" mark. */
    val BoltOutline: ImageVector by lazy { hollowVariantOf(Icons.Filled.Bolt, "BoltOutline") }
}

/** Viewport units, matched to Material's own outlined icons (which stroke at 2 units of 24). */
private const val HollowStrokeWidth = 2f

/**
 * Copies [source]'s geometry with every path stroked rather than filled. The stroke colour is black
 * on purpose: `Icon` tints the whole painter through a `ColorFilter`, so the caller's tint wins —
 * exactly as it does for a Material vector.
 */
private fun hollowVariantOf(source: ImageVector, name: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = source.defaultWidth,
        defaultHeight = source.defaultHeight,
        viewportWidth = source.viewportWidth,
        viewportHeight = source.viewportHeight,
    )
    fun emit(group: VectorGroup) {
        group.forEach { node ->
            when (node) {
                is VectorPath -> builder.addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = HollowStrokeWidth,
                )
                is VectorGroup -> emit(node)
            }
        }
    }
    emit(source.root)
    return builder.build()
}
