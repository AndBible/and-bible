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

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Issue #4201: the assign sheet's request seam and how each outcome reaches the label chooser's caller. */
class LabelAssignSheetRequestsTest {
    @Test
    fun anAssignedSheetAnswersWithoutTheFullScreen() = runTest(timeout = 30.seconds) {
        val fullScreen = mutableListOf<String>()
        val result = resolveLabelAssign("in", { LabelAssignOutcome.Assigned("out") }) { fullScreen += it; "full" }
        assertEquals("out", result)
        assertTrue(fullScreen.isEmpty(), "an assigned sheet must not open the full-screen list")
    }

    @Test
    fun editLabelsOpensTheFullScreenWithTheSheetsStateAndReturnsItsAnswer() = runTest(timeout = 30.seconds) {
        val fullScreen = mutableListOf<String>()
        val result = resolveLabelAssign("in", { LabelAssignOutcome.Expand("sheet-state") }) { fullScreen += it; "full" }
        assertEquals(listOf("sheet-state"), fullScreen, "the full screen starts from the sheet's working state")
        assertEquals("full", result)
    }

    @Test
    fun editLabelsLeftWithoutAnAnswerIsNoAnswer() = runTest(timeout = 30.seconds) {
        assertNull(resolveLabelAssign("in", { LabelAssignOutcome.Expand("x") }) { null })
    }

    @Test
    fun aCancelledSheetIsNoAnswer() = runTest(timeout = 30.seconds) {
        val fullScreen = mutableListOf<String>()
        assertNull(resolveLabelAssign("in", { LabelAssignOutcome.Cancelled }) { fullScreen += it; "full" })
        assertTrue(fullScreen.isEmpty())
    }

    @Test
    fun withoutASheetTheFullScreenIsUsedAsBefore() = runTest(timeout = 30.seconds) {
        val fullScreen = mutableListOf<String>()
        assertEquals("full", resolveLabelAssign("in", null) { fullScreen += it; "full" })
        assertEquals(listOf("in"), fullScreen)
    }

    @Test
    fun aRequestIsPublishedAndAnsweredOnce() = runTest(timeout = 30.seconds) {
        val requests = LabelAssignSheetRequests()
        val answer = async { requests.request("payload") }
        runCurrent()
        val open = requests.current!!
        assertEquals("payload", open.data)
        open.finish(LabelAssignOutcome.Assigned("a"))
        open.finish(LabelAssignOutcome.Assigned("b"))
        assertEquals(LabelAssignOutcome.Assigned("a"), answer.await())
        assertNull(requests.current, "an answered request closes the sheet")
    }

    @Test
    fun aNewRequestCancelsTheOpenOne() = runTest(timeout = 30.seconds) {
        val requests = LabelAssignSheetRequests()
        val first = async { requests.request("one") }
        runCurrent()
        val second = async { requests.request("two") }
        runCurrent()
        assertEquals(LabelAssignOutcome.Cancelled, first.await())
        assertEquals("two", requests.current?.data)
        requests.current!!.finish(LabelAssignOutcome.Cancelled)
        assertEquals(LabelAssignOutcome.Cancelled, second.await())
    }

    @Test
    fun aCancelledRequesterClosesTheSheet() = runTest(timeout = 30.seconds) {
        val requests = LabelAssignSheetRequests()
        val answer = async { requests.request("payload") }
        runCurrent()
        val open = requests.current
        answer.cancel()
        runCurrent()
        assertNull(requests.current)
        // A late answer from the closing sheet goes nowhere.
        open!!.finish(LabelAssignOutcome.Assigned("late"))
        assertSame(null, requests.current)
    }
}
