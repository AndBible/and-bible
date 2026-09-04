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

import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.displayName
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.OverrideMode

/**
 * Pure `LabelData` ↔ `LabelEditState` mapper. This is the one place Room types
 * (`BookmarkEntities.Label`, `WorkspaceEntities.WorkspaceLabelOverride`) meet the portable
 * `LabelEditState` consumed by the shared `LabelEditController`/`LabelEditScreen`. Mirrors the
 * verbatim field-by-field behaviour of classic `LabelEditActivity.updateUI()`/`updateData()`.
 */
object LabelEditMapper {

    /** Builds the initial [LabelEditState] from a freshly-decoded [LabelEditContract.LabelData]. */
    fun toState(data: LabelEditContract.LabelData): LabelEditState {
        val label = data.label
        val name = if (label.name.isEmpty() && data.suggestedName != null) {
            data.suggestedName
        } else {
            label.displayName
        }
        return LabelEditState(
            labelId = label.id.toString(),
            name = name,
            color = label.color,
            customIcon = label.customIcon,
            selectionStyle = label.displayStyle,
            wholeVerseStyle = label.displayStyleWholeVerse,
            favourite = label.favourite,
            isAssigning = data.isAssigning,
            thisBookmarkSelected = data.isThisBookmarkSelected,
            thisBookmarkPrimary = data.isThisBookmarkPrimary,
            hasWorkspaceContext = data.hasWorkspaceContext,
            autoAssign = data.isAutoAssign,
            autoAssignPrimary = data.isAutoAssignPrimary,
            overrideMode = overrideModeFromInt(data.workspaceOverride?.overrideMode),
            isSpecialLabel = label.isSpecialLabel,
            isSpeakLabel = label.isSpeakLabel,
        )
    }

    /**
     * Writes [state] back into [data] (mutating `data.label` in place, same as classic
     * `updateData()`) and returns [data] for chaining. The label's `name` is only overwritten
     * when it's editable (a special label's name is never user-editable, so [state]'s
     * `nameEditable == false` there and the display-only name must not clobber the stored one).
     */
    fun applyToData(data: LabelEditContract.LabelData, state: LabelEditState): LabelEditContract.LabelData {
        val label = data.label
        if (state.nameEditable) {
            label.name = state.name
        }
        label.color = state.color
        label.customIcon = state.customIcon
        label.displayStyle = state.selectionStyle
        label.displayStyleWholeVerse = state.wholeVerseStyle
        label.favourite = state.favourite

        data.isAutoAssign = state.autoAssign
        data.isAutoAssignPrimary = if (state.autoAssign) state.autoAssignPrimary else false
        data.isThisBookmarkSelected = state.thisBookmarkSelected
        data.isThisBookmarkPrimary = if (state.thisBookmarkSelected) state.thisBookmarkPrimary else false

        if (data.hasWorkspaceContext) {
            data.workspaceOverride = data.workspaceOverride?.copy(overrideMode = overrideModeToInt(state.overrideMode))
        }
        return data
    }

    /** internal, not private: [ManageLabelsServiceImpl.overrideDisplayStyle] expresses itself in
     *  terms of this + [net.bible.sharedcore.bookmark.displayStyle] rather than re-deriving the
     *  same four `WorkspaceLabelOverride.MODE_*` constants a second time. */
    internal fun overrideModeFromInt(mode: Int?): OverrideMode = when (mode) {
        null -> OverrideMode.NONE
        WorkspaceEntities.WorkspaceLabelOverride.MODE_HIGHLIGHT -> OverrideMode.HIGHLIGHT
        WorkspaceEntities.WorkspaceLabelOverride.MODE_UNDERLINE -> OverrideMode.UNDERLINE
        WorkspaceEntities.WorkspaceLabelOverride.MODE_MARKER -> OverrideMode.MARKER
        WorkspaceEntities.WorkspaceLabelOverride.MODE_HIDDEN -> OverrideMode.HIDDEN
        else -> OverrideMode.NONE
    }

    private fun overrideModeToInt(mode: OverrideMode): Int? = when (mode) {
        OverrideMode.NONE -> null
        OverrideMode.HIGHLIGHT -> WorkspaceEntities.WorkspaceLabelOverride.MODE_HIGHLIGHT
        OverrideMode.UNDERLINE -> WorkspaceEntities.WorkspaceLabelOverride.MODE_UNDERLINE
        OverrideMode.MARKER -> WorkspaceEntities.WorkspaceLabelOverride.MODE_MARKER
        OverrideMode.HIDDEN -> WorkspaceEntities.WorkspaceLabelOverride.MODE_HIDDEN
    }
}
