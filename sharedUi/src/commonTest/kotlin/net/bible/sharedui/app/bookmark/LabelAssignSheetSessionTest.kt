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

package net.bible.sharedui.app.bookmark

import net.bible.sharedcore.nav.LabelEditResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #4201: what the assign sheet's closes and its editor round trip do, apart from Compose and Room. */
class LabelAssignSheetSessionTest {
    private var outcome: LabelAssignOutcome? = null
    private var changed = false
    private var ticksChanged = true
    private var saves = 0
    private val editors = mutableListOf<String?>()
    private val applied = mutableListOf<LabelEditResult>()

    private val sheet = LabelAssignSheetSession(
        request = LabelAssignSheetRequests.Request("in") { outcome = it },
        hasChanges = { changed },
        save = { saves++ },
        startEditor = { editors += it },
        applyEditorResult = { applied += it },
        bookmarkLabelsChanged = { ticksChanged },
    )

    @Test
    fun anUntouchedCloseIsCancelledWithoutASave() {
        sheet.dismiss()
        assertEquals(0, saves)
        assertEquals(LabelAssignOutcome.Cancelled, outcome)
        assertEquals(LabelAssignSheetSession.Phase.CLOSING, sheet.phase)
    }

    @Test
    fun aCloseAfterAChangeSavesAndAnswersAssigned() {
        changed = true
        sheet.dismiss()
        sheet.dismiss() // a second close while saving is ignored
        assertEquals(1, saves)
        assertNull(outcome, "the answer waits for the save")
        sheet.onSaved("saved")
        assertEquals(LabelAssignOutcome.Assigned("saved"), outcome)
    }

    /** Review round 3: a save of label edits only says so, so several bookmarks are not given the seeded union. */
    @Test
    fun aSaveWithTheTicksAsSeededSaysTheBookmarksLabelsDidNotChange() {
        changed = true
        ticksChanged = false
        sheet.dismiss()
        sheet.onSaved("saved")
        assertEquals(LabelAssignOutcome.Assigned("saved", bookmarkLabelsChanged = false), outcome)
    }

    @Test
    fun editLabelsSavesEvenUntouchedAndAnswersExpand() {
        sheet.editLabels()
        assertEquals(1, saves)
        sheet.onSaved("state")
        assertEquals(LabelAssignOutcome.Expand("state"), outcome)
    }

    @Test
    fun aFailedSaveShowsTheSheetAgainAndAPlainCloseThenAnswersAssigned() {
        sheet.editLabels()
        sheet.onSaveFailed()
        assertEquals(LabelAssignSheetSession.Phase.OPEN, sheet.phase)
        assertNull(outcome)

        changed = true
        sheet.dismiss()
        sheet.onSaved("saved")
        assertEquals(LabelAssignOutcome.Assigned("saved"), outcome, "the failed Edit labels does not make this an Expand")
    }

    @Test
    fun theEditorRoundTripStepsAsideAndComesBack() {
        sheet.newLabel()
        assertEquals(listOf<String?>(null), editors)
        assertEquals(LabelAssignSheetSession.Phase.EDITING, sheet.phase, "steps aside before the editor's payload is built")

        sheet.dismiss()
        sheet.newLabel()
        assertEquals(0, saves, "a sheet that stepped aside neither closes nor opens a second editor")
        assertEquals(1, editors.size)

        sheet.onEditorResult(LabelEditResult.Cancelled)
        assertEquals(listOf<LabelEditResult>(LabelEditResult.Cancelled), applied)
        assertEquals(LabelAssignSheetSession.Phase.OPEN, sheet.phase)

        sheet.editLabel("7")
        assertEquals(listOf(null, "7"), editors)
    }

    @Test
    fun anEditorThatCouldNotOpenShowsTheSheetAgain() {
        sheet.editLabel("gone")
        sheet.editLabel("gone") // a second tap before the first editor opened
        assertEquals(listOf<String?>("gone"), editors, "one editor only")
        sheet.onEditorFailed()
        assertEquals(LabelAssignSheetSession.Phase.OPEN, sheet.phase)
        sheet.onEditorFailed() // an open sheet stays open
        changed = true
        sheet.dismiss()
        assertEquals(1, saves)
    }

    @Test
    fun anEditorAnswerWhileTheSheetIsNotWaitingForOneIsIgnored() {
        sheet.onEditorResult(LabelEditResult.Saved("{}"))
        assertTrue(applied.isEmpty())
    }
}
