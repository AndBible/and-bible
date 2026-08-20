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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
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
 * The list row's style tag, all four styles at once. The tag's TEXT is the style's own localised
 * name drawn with that style, so this one image is both the rendering check and the wording check.
 * Captured across the full matrix because the monochrome substitutions (fixed greys for the
 * highlight, onSurface for the underline — the reader's own rules) are the part most likely to
 * break, and they only appear in the bw / eink modes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelStyleTagGoldenTest {

    @Test fun labelStyleTag_all() = captureMatrix("LabelStyleTag", "all", heightDp = 300) {
        Column {
            BookmarkDisplayStyle.entries.forEach { style ->
                LabelStyleTag(style = style, colorArgb = AbColor.palette[1]) {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = Color(AbColor.palette[1]))
                }
            }
        }
    }
}
