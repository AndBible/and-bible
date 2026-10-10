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

package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedui.bookmark.LabelStyleTag
import net.bible.sharedui.components.AbColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list row's style tag, all four styles at once. Since round 15a the tag's TEXT names the AXIS
 * ("Selection" / "Whole verse"), not the style, so this image is the rendering check only — the
 * wording is fixture English, not a translated string under test. Captured across the full matrix
 * because the monochrome substitutions (fixed greys for the highlight, onSurface for the underline —
 * the reader's own rules) are the part most likely to break, and they only appear in the bw / eink
 * modes.
 *
 * Each row now shows BOTH decoration axes side by side: partial (left, the selection axis) and full
 * (right, the whole-verse axis). Reading the pair is what shows that "half" is what says
 * "selection" -- and that MARKER/HIDDEN are unaffected, having nothing to decorate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelStyleTagGoldenTest {

    @Test fun labelStyleTag_all() = captureMatrix("LabelStyleTag", "all", heightDp = 300) {
        Column {
            BookmarkDisplayStyle.entries.forEach { style ->
                val glyphTint = net.bible.sharedui.bookmark.bookmarkMarkerTint(AbColor.palette[1])
                // Left: the selection axis (partial). Right: the whole-verse axis (full). Read the
                // pair to see that "half" is what says "selection" -- and that MARKER/HIDDEN are
                // unaffected, having nothing to decorate.
                Row {
                    LabelStyleTag(
                        text = "Selection",
                        style = style,
                        colorArgb = AbColor.palette[1],
                        decoratePartially = true,
                    ) {
                        // Same production tint resolver as the list row and editor marker.
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = glyphTint)
                    }
                    Spacer(Modifier.width(12.dp))
                    LabelStyleTag(
                        text = "Whole verse",
                        style = style,
                        colorArgb = AbColor.palette[1],
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = glyphTint)
                    }
                }
            }
        }
    }
}
