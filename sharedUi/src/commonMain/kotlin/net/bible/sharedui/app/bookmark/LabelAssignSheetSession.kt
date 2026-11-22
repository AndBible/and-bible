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

package net.bible.sharedui.app.bookmark

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.bible.sharedcore.nav.LabelEditResult

/**
 * What one open label-assign sheet does, apart from its composition (issue #4201). `BookmarkHost` builds one per
 * [LabelAssignSheetRequests.Request] and wires the sheet's controls to it; host tests drive it directly.
 *
 * - [dismiss] (swipe, scrim, back, ✕): saves when [hasChanges], and the request ends [LabelAssignOutcome.Assigned]
 *   with the saved payload; with nothing changed it writes nothing and ends [LabelAssignOutcome.Cancelled]. A save
 *   would otherwise give every selected bookmark the union of their labels the sheet was seeded with.
 * - [editLabels]: always saves (new labels must exist before the full screen lists them) and ends
 *   [LabelAssignOutcome.Expand].
 * - [newLabel] / [editLabel]: the sheet steps aside ([Phase.EDITING]) while the full-screen label editor is open;
 *   [onEditorResult] applies its answer and shows the sheet again.
 * - [onSaveFailed]: a save that failed delivered nothing, so the sheet is shown again.
 *
 * @param hasChanges whether the sheet's working state differs from its seeds (`ManageLabelsController.hasChanges`)
 * @param save starts the save; its result arrives through [onSaved] or [onSaveFailed]
 * @param startEditor asks the controller for the editor (`null` = a new label); the host navigates once the editor's
 *   payload is built, and tells [onEditorOpened]
 * @param applyEditorResult applies the editor's answer to the sheet's working state
 */
@kotlin.native.HiddenFromObjC
class LabelAssignSheetSession(
    private val request: LabelAssignSheetRequests.Request,
    private val hasChanges: () -> Boolean,
    private val save: () -> Unit,
    private val startEditor: (labelId: String?) -> Unit,
    private val applyEditorResult: (LabelEditResult) -> Unit,
) {
    /** Where the sheet is in its life; only [Phase.OPEN] draws it. */
    enum class Phase { OPEN, EDITING, CLOSING }

    var phase: Phase by mutableStateOf(Phase.OPEN)
        private set

    private var expand = false

    /** Every close of the sheet itself. */
    fun dismiss() = close(toFullScreen = false)

    /** The "Edit labels" footer. */
    fun editLabels() = close(toFullScreen = true)

    private fun close(toFullScreen: Boolean) {
        if (phase != Phase.OPEN) return
        phase = Phase.CLOSING
        if (!toFullScreen && !hasChanges()) {
            request.finish(LabelAssignOutcome.Cancelled)
            return
        }
        expand = toFullScreen
        save()
    }

    /** The save delivered [data]: the request's answer. */
    fun onSaved(data: String) {
        request.finish(if (expand) LabelAssignOutcome.Expand(data) else LabelAssignOutcome.Assigned(data))
    }

    /** The save failed and delivered nothing: show the sheet again, so the user can retry. */
    fun onSaveFailed() {
        expand = false
        phase = Phase.OPEN
    }

    /** New (+): the full-screen label editor on a new label. */
    fun newLabel() = openEditor(null)

    /** A row's long press: the full-screen label editor on that label. */
    fun editLabel(labelId: String) = openEditor(labelId)

    private fun openEditor(labelId: String?) {
        if (phase != Phase.OPEN) return
        startEditor(labelId)
    }

    /** The editor is on screen: the sheet steps aside until [onEditorResult]. */
    fun onEditorOpened() {
        if (phase == Phase.OPEN) phase = Phase.EDITING
    }

    /** The editor answered (saved, deleted or cancelled): apply it and show the sheet again. */
    fun onEditorResult(result: LabelEditResult) {
        if (phase != Phase.EDITING) return
        applyEditorResult(result)
        phase = Phase.OPEN
    }
}
