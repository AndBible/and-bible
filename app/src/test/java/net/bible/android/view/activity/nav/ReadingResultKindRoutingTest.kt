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

package net.bible.android.view.activity.nav

import net.bible.android.view.activity.base.ActivityBase
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Slice 8 B1: the four routes the reading view now awaits in-graph, and the abandonment claim. */
class ReadingResultKindRoutingTest {

    @Test
    fun everyNewRouteMapsToItsKindWithOrWithoutArguments() {
        assertEquals(ReadingResultKind.KeyChooser, ReadingResultKind.forRoute(NavRoutes.CHOOSE_GENERAL_BOOK_KEY))
        assertEquals(ReadingResultKind.KeyChooser, ReadingResultKind.forRoute(NavRoutes.CHOOSE_MAP_KEY))
        assertEquals(ReadingResultKind.KeyChooser, ReadingResultKind.forRoute(NavRoutes.CHOOSE_DICTIONARY_WORD))
        assertEquals(ReadingResultKind.PassageGrid, ReadingResultKind.forRoute(NavRoutes.gridChoosePassage(isScripture = true)))
        assertEquals(ReadingResultKind.ChooseDocument, ReadingResultKind.forRoute(NavRoutes.chooseDocument()))
        assertEquals(ReadingResultKind.ChooseDocument, ReadingResultKind.forRoute(NavRoutes.chooseDocument("BIBLE")))
        assertEquals(ReadingResultKind.Workspace, ReadingResultKind.forRoute(NavRoutes.WORKSPACE_SELECTOR))
    }

    @Test
    fun routesThatAnswerNothingStillMapToNothing() {
        // TextDisplaySettings writes through as it edits; Download/Settings answer "the user came back".
        assertNull(ReadingResultKind.forRoute(NavRoutes.textDisplaySettings()))
        assertNull(ReadingResultKind.forRoute(NavRoutes.download()))
        assertNull(ReadingResultKind.forRoute(NavRoutes.SETTINGS))
    }

    @Test
    fun onlyTheThreeChooserKindsAnswerAbandonment() {
        assertEquals(
            setOf(ReadingResultKind.KeyChooser, ReadingResultKind.PassageGrid, ReadingResultKind.ChooseDocument),
            ReadingResultKind.entries.filter { it.answersAbandonment }.toSet(),
        )
    }

    @Test
    fun anAbandonedRequestIsClaimedOnceAndOnlyWhileNothingIsPending() {
        val requests = ReadingResultRequests()
        requests.record(ReadingResultKind.KeyChooser, ActivityBase.ASYNC_REQUEST_CODE_START + 5)
        requests.record(ReadingResultKind.PassageGrid, ActivityBase.STD_REQUEST_CODE)
        requests.record(ReadingResultKind.ManageLabels, ActivityBase.ASYNC_REQUEST_CODE_START + 6)

        val first = requests.claimAbandoned { kind -> kind == ReadingResultKind.PassageGrid }

        assertEquals(listOf(ReadingResultKind.KeyChooser to ActivityBase.ASYNC_REQUEST_CODE_START + 5), first)
        assertTrue("a kind with an answer pending is the collector's, not abandoned", requests.isAwaiting(ReadingResultKind.PassageGrid))
        assertTrue("a T8c kind is never claimed as abandoned", requests.isAwaiting(ReadingResultKind.ManageLabels))
        assertEquals(emptyList<Pair<ReadingResultKind, Int>>(), requests.claimAbandoned { false }.filter { it.first == ReadingResultKind.KeyChooser })
    }
}
