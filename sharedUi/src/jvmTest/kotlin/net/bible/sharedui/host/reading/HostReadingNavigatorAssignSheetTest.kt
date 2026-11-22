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

package net.bible.sharedui.host.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedui.app.bookmark.LabelAssignOutcome
import net.bible.sharedui.app.bookmark.LabelAssignSheetOpener
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Issue #4201: the reading view's label chooser (iOS, desktop) asks the assign sheet before the full-screen list. */
class HostReadingNavigatorAssignSheetTest {
    private val routes = mutableListOf<String>()

    private fun navigator(scope: CoroutineScope, sheet: LabelAssignSheetOpener) = HostReadingNavigator(
        shell = HostNavShell(scope, object : HostNavigator {
            override fun navigate(route: String) { routes += route }
            override fun popBackStack() = false
            override fun popBackStackTo(route: String, inclusive: Boolean) = false
        }),
        hostState = unusedState(),
        links = object : ExternalLinks {
            override val askBeforeOpening = false
            override fun open(url: String) = Unit
        },
        dialogs = net.bible.sharedcore.ui.dialog.AppDialogController(),
        strings = { iosStrings("en") },
        labelResults = NavResultChannel<ManageLabelsResult> {},
        downloadControl = { error("Unused download control") },
        fakeBooks = { error("Unused book factory") },
        mainContext = Dispatchers.Unconfined,
        openReadingSearch = {},
        labelAssignSheet = { sheet },
    )

    private fun unusedState(): ReadingHostState {
        val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ReadingHostState::class.java) as ReadingHostState
    }

    private fun bookmark(primary: IdType? = null) = BookmarkEntities.GenericBookmarkWithNotes(
        key = "k", bookInitials = "X", ordinalStart = null, ordinalEnd = null, startOffset = null, endOffset = null,
        playbackSettings = null, primaryLabelId = primary,
    )

    @Test fun theSheetsSelectedIdsAndPrimaryReachTheBookmarkWithoutTheFullScreen() = runTest(timeout = 30.seconds) {
        val a = IdType()
        val b = IdType()
        val asked = mutableListOf<String>()
        val navigator = navigator(this) { data ->
            asked += data
            LabelAssignOutcome.Assigned(
                ManageLabelsContract.ManageLabelsData(
                    mode = ManageLabelsContract.Mode.ASSIGN,
                    selectedLabels = mutableSetOf(a, b),
                    bookmarkPrimaryLabel = b,
                ).toJSON(),
            )
        }
        val target = bookmark()
        val chosen = navigator.assignLabels(target, listOf(a))

        assertEquals(setOf(a, b), chosen?.toSet())
        assertEquals(b, target.primaryLabelId)
        assertTrue(routes.isEmpty(), "the sheet replaces the full-screen route: $routes")
        val request = ManageLabelsContract.ManageLabelsData.fromJSON(asked.single())
        assertEquals(ManageLabelsContract.Mode.ASSIGN, request.mode)
        assertEquals(setOf(a), request.selectedLabels, "the sheet starts from the bookmark's labels")
    }

    @Test fun aCancelledSheetChangesNothing() = runTest(timeout = 30.seconds) {
        val primary = IdType()
        val target = bookmark(primary)
        assertNull(navigator(this) { LabelAssignOutcome.Cancelled }.assignLabels(target, emptyList()))
        assertEquals(primary, target.primaryLabelId)
        assertTrue(routes.isEmpty())
    }

    @Test fun editLabelsOpensTheFullScreenListWithTheSheetsState() = runTest(timeout = 30.seconds) {
        val state = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.ASSIGN).toJSON()
        val navigator = navigator(this) { LabelAssignOutcome.Expand(state) }
        var failure: Throwable? = null
        val job = backgroundScope.launch {
            try { navigator.assignLabels(bookmark(), emptyList()) } catch (e: Throwable) { failure = e; throw e }
        }
        testScheduler.runCurrent()
        assertNull(failure?.stackTraceToString())
        assertEquals(1, routes.size)
        assertTrue(routes.single().startsWith("bookmarks/manageLabels"), routes.single())
        job.cancel()
    }
}
