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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.StudyPadSearchResult
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.displayName
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.bookmark.displayStyle
import net.bible.sharedcore.platform.AppSettings
import kotlin.random.Random.Default.nextInt

/** Android-side impl of the [ManageLabelsService] seam, backed by [BookmarkControl]/[WindowControl]. */
class ManageLabelsServiceImpl(
    private val bookmarkControl: BookmarkControl,
    private val windowControl: WindowControl,
    private val settings: AppSettings,
) : ManageLabelsService {

    // Off the main thread: the controller loads on the host's (Main) lifecycle scope.
    override suspend fun assignableLabels(): List<LabelItem> =
        withContext(Dispatchers.IO) { bookmarkControl.assignableLabels() }.assignableLabelItems()

    override suspend fun unlabeledLabel(): LabelItem =
        withContext(Dispatchers.IO) { bookmarkControl.labelUnlabelled() }.toLabelItem()

    override fun recentLabelIds(): List<String> =
        windowControl.windowRepository.workspaceSettings.recentLabels.map { it.labelId.toString() }

    override suspend fun overriddenLabelStyles(): Map<String, BookmarkDisplayStyle> {
        val workspaceId = windowControl.windowRepository.id
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        return withContext(Dispatchers.IO) { workspaceDao.labelOverrides(workspaceId) }
            .mapNotNull { override ->
                overrideDisplayStyle(override.overrideMode)?.let { override.labelId.toString() to it }
            }
            .toMap()
    }

    // Matches classic ManageLabels.randomColor() (ManageLabels.kt:526) exactly, including the
    // (0, 255)-exclusive-upper-bound nextInt calls.
    override fun randomColorArgb(): Int = Color.argb(255, nextInt(0, 255), nextInt(0, 255), nextInt(0, 255))

    // Run off the main thread: classic ManageLabels.kt:804-842 dispatches this search on
    // Dispatchers.IO (Room DAO queries), and the controller launches it on its own scope, which
    // may be Main-confined (lifecycleScope on the host).
    override suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult> =
        withContext(Dispatchers.IO) {
            bookmarkControl.searchStudyPadsByContent(text).map { it.toSearchResultRow() }
        }

    override fun styleTagsVisible(): Boolean =
        settings.getBoolean(STYLE_TAGS_PREF, true)

    override fun setStyleTagsVisible(visible: Boolean) =
        settings.setBoolean(STYLE_TAGS_PREF, visible)
}

private const val STYLE_TAGS_PREF = "manage_labels_style_tags"

/** [ManageLabelsRow.SearchResult] view of a classic [StudyPadSearchResult] — takes only the FIRST
 *  match's snippet/span/entry id (mirrors classic `ManageLabelItemAdapter`'s `VIEW_TYPE_SEARCH_RESULT`,
 *  which likewise surfaces one representative match per Study Pad row); empty/zero/null when there
 *  are no matches (shouldn't normally occur — every result here came from a matching query row). */
fun StudyPadSearchResult.toSearchResultRow(): ManageLabelsRow.SearchResult {
    val first = matches.firstOrNull()
    return ManageLabelsRow.SearchResult(
        labelId = label.id.toString(),
        name = label.displayName,
        color = label.color,
        matchCount = matchCount,
        snippet = first?.textSnippet ?: "",
        matchStart = first?.matchStart ?: 0,
        matchEnd = first?.matchEnd ?: 0,
        firstMatchEntryId = first?.entryId?.toString(),
    )
}

/** The label manager's list: every label but the Unlabeled special (classic: filter !isUnlabeledLabel). */
fun List<BookmarkEntities.Label>.assignableLabelItems(): List<LabelItem> =
    filter { !it.isUnlabeledLabel }.map { it.toLabelItem() }

/** [LabelItem] view of a Room [BookmarkEntities.Label]. `overrideStyle` is always `null` here — the
 *  controller relinks it from [ManageLabelsService.overriddenLabelStyles] on every rebuild. */
fun BookmarkEntities.Label.toLabelItem(): LabelItem = LabelItem(
    id = id.toString(),
    name = displayName,
    color = color,
    favourite = favourite,
    isUnlabeled = isUnlabeledLabel,
    isSpecial = isSpecialLabel,
    customIcon = customIcon,
    selectionStyle = displayStyle,
    wholeVerseStyle = displayStyleWholeVerse,
)

/** The display style a `WorkspaceLabelOverride.overrideMode` int imposes, or `null` for no override.
 *  Must agree with [BookmarkEntities.Label.withStyleOverrides], which is what the reader obeys —
 *  `OverrideDisplayStyleTest` pins the two together.
 *
 *  Expressed via [LabelEditMapper.overrideModeFromInt] + [displayStyle] rather than re-listing the
 *  same four `WorkspaceLabelOverride.MODE_*` constants a second time — that mapper function is the
 *  editor's own int↔[net.bible.sharedcore.bookmark.OverrideMode] conversion, already tested by
 *  `LabelEditMapperTest`, and its own `else -> OverrideMode.NONE` fallback is exactly this
 *  function's `null` fallback ([OverrideMode.NONE]'s `displayStyle` is `null`). */
internal fun overrideDisplayStyle(overrideMode: Int?): BookmarkDisplayStyle? =
    LabelEditMapper.overrideModeFromInt(overrideMode).displayStyle
