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
package net.bible.android.view.activity.bookmark

import android.graphics.Color
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.displayName
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsService
import kotlin.random.Random.Default.nextInt

/** Android-side impl of the [ManageLabelsService] seam, backed by [BookmarkControl]/[WindowControl]. */
class ManageLabelsServiceImpl(
    private val bookmarkControl: BookmarkControl,
    private val windowControl: WindowControl,
) : ManageLabelsService {

    override fun assignableLabels(): List<LabelItem> =
        bookmarkControl.assignableLabels.filter { !it.isUnlabeledLabel }.map { it.toLabelItem() }

    override fun unlabeledLabel(): LabelItem = bookmarkControl.labelUnlabelled.toLabelItem()

    override fun recentLabelIds(): List<String> =
        windowControl.windowRepository.workspaceSettings.recentLabels.map { it.labelId.toString() }

    override fun overriddenLabelIds(): Set<String> {
        val workspaceId = windowControl.windowRepository.id
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        return workspaceDao.labelOverrides(workspaceId)
            .filter { it.hasOverride }
            .map { it.labelId.toString() }
            .toSet()
    }

    // Matches classic ManageLabels.randomColor() (ManageLabels.kt:526) exactly, including the
    // (0, 255)-exclusive-upper-bound nextInt calls.
    override fun randomColorArgb(): Int = Color.argb(255, nextInt(0, 255), nextInt(0, 255), nextInt(0, 255))
}

/** [LabelItem] view of a Room [BookmarkEntities.Label]. `hasOverride` is always `false` here — the
 *  controller relinks it from [ManageLabelsService.overriddenLabelIds] on every rebuild. */
fun BookmarkEntities.Label.toLabelItem(): LabelItem = LabelItem(
    id = id.toString(),
    name = displayName,
    color = color,
    favourite = favourite,
    isUnlabeled = isUnlabeledLabel,
    isSpecial = isSpecialLabel,
    customIcon = customIcon,
    hasOverride = false,
)
