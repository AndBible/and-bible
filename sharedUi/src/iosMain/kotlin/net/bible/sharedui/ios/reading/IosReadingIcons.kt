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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.reading

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import net.bible.sharedui.reading.ReadingToolbarIcons

/**
 * Material stand-ins for the Android drawables of the reading toolbar (the bitmaps live in `:app`'s resources).
 * Replaced by the real glyphs when iOS gets its own icon resources.
 */
@Composable
@kotlin.native.HiddenFromObjC
fun rememberIosReadingIcons(): ReadingToolbarIcons = ReadingToolbarIcons(
    home = rememberVectorPainter(Icons.Outlined.Home),
    search = rememberVectorPainter(Icons.Outlined.Search),
    speak = rememberVectorPainter(Icons.AutoMirrored.Outlined.VolumeUp),
    strongs = rememberVectorPainter(Icons.Outlined.Translate),
    bible = rememberVectorPainter(Icons.Outlined.Book),
    commentary = rememberVectorPainter(Icons.AutoMirrored.Outlined.MenuBook),
    workspace = rememberVectorPainter(Icons.Outlined.Dashboard),
    overflow = rememberVectorPainter(Icons.Outlined.MoreVert),
    sync = rememberVectorPainter(Icons.Outlined.Sync),
)
