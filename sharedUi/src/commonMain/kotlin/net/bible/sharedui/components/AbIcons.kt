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
 *
 * Contract: [source] must be a single flat group of plain [VectorPath] children — no nested
 * [VectorGroup]. A nested group carries its own `rotate`/`translate`/`scale`/`pivot`/`clip`
 * (`VectorGroup`'s own transform fields), which a flattening recursion would silently drop rather
 * than reproduce, producing a geometrically wrong "hollow variant" with nothing to say so. A
 * grouped source is refused outright instead.
 *
 * Per path, what IS carried over: `pathData`, `pathFillType`, `name`, `fillAlpha`, `strokeAlpha`
 * and the three `trimPath*` fields — genuine geometry of the source path, meaningful regardless of
 * whether it ends up filled or stroked.
 *
 * What is deliberately NOT carried, even though [VectorPath] has the fields: `strokeLineCap`,
 * `strokeLineJoin`, `strokeLineMiter`. These are STROKE-appearance parameters, and [source] is a
 * fill-only path (`stroke == null`) whose own values for them were never exercised when it was
 * drawn — they are incidental leftovers from however the source vector's builder happened to
 * default them, not a considered choice about how a stroke should look. Copying them here found
 * this out the hard way: `Icons.Filled.Bolt`'s path carries `strokeLineJoin = Bevel` and
 * `strokeLineMiter = 1f` (Compose Material's own `materialPath` DSL default for a filled path),
 * which — once actually used to stroke [BoltOutline] — visibly squared off the bolt's sharp tips
 * compared to the sensible `Miter`/`4f` stroke default, changing `AbIcons_boltPair_dark`'s
 * rasterised pixels with no change in silhouette. `strokeLineCap`/`strokeLineJoin`/`strokeLineMiter`
 * are left at `addPath`'s own defaults (`Butt`/`Miter`/`4f`) instead — this transform's own explicit
 * choice for how ITS stroke should join, independent of what the never-stroked source happened to
 * carry.
 *
 * What is deliberately OVERRIDDEN outright, not carried at all: `fill` (forced to `null`) and
 * `strokeLineWidth` (forced to [HollowStrokeWidth]) — those two are the whole point of the
 * transform. `Icons.Filled.Bolt` is a single ungrouped path, so nothing in current use is affected
 * by the group refusal.
 */
private fun hollowVariantOf(source: ImageVector, name: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = source.defaultWidth,
        defaultHeight = source.defaultHeight,
        viewportWidth = source.viewportWidth,
        viewportHeight = source.viewportHeight,
    )
    source.root.forEach { node ->
        val path = node as? VectorPath
            ?: error(
                "hollowVariantOf($name): '${source.name}' contains a nested VectorGroup; only " +
                    "flat, ungrouped vectors are supported (see hollowVariantOf's KDoc)"
            )
        builder.addPath(
            pathData = path.pathData,
            pathFillType = path.pathFillType,
            name = path.name,
            fill = null,
            fillAlpha = path.fillAlpha,
            stroke = SolidColor(Color.Black),
            strokeAlpha = path.strokeAlpha,
            strokeLineWidth = HollowStrokeWidth,
            // strokeLineCap/strokeLineJoin/strokeLineMiter intentionally NOT copied from `path` --
            // see the KDoc above. Left at addPath's own defaults (Butt/Miter/4f).
            trimPathStart = path.trimPathStart,
            trimPathEnd = path.trimPathEnd,
            trimPathOffset = path.trimPathOffset,
        )
    }
    return builder.build()
}
