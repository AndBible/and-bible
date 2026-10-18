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

package net.bible.sharedui.ios.ports

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import net.bible.sharedui.app.bookmark.BookmarkActionIcon
import net.bible.sharedui.app.bookmark.LabelIcons
import net.bible.sharedui.ios.IosPending

/** Pending (stream H): no glyphs, every slot renders nothing. */
@kotlin.native.HiddenFromObjC
class IosLabelIcons : LabelIcons {
    override val iconKeys: List<String?> = emptyList()

    @Composable
    override fun LabelIcon(name: String?, tint: Color) {
        IosPending.log("LabelIcons.LabelIcon")
    }

    @Composable
    override fun ManageLabelIcon(name: String?, tint: Color, studyPadMode: Boolean) {
        IosPending.log("LabelIcons.ManageLabelIcon")
    }

    @Composable
    override fun ShareIcon() {
        IosPending.log("LabelIcons.ShareIcon")
    }

    @Composable
    override fun SaveIcon() {
        IosPending.log("LabelIcons.SaveIcon")
    }

    @Composable
    override fun ActionIcon(icon: BookmarkActionIcon, contentDescription: String?, modifier: Modifier) {
        IosPending.log("LabelIcons.ActionIcon")
    }
}
