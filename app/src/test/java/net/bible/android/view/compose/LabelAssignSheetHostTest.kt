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
package net.bible.android.view.compose

import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.bookmark.LabelEditContract
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsWrites
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.bookmark.BookmarksController
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.nav.LabelEditResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.bookmark.BookmarkHost
import net.bible.sharedui.app.bookmark.LabelAssignOutcome
import net.bible.sharedui.app.bookmark.LabelAssignSheetSession
import net.bible.sharedui.app.bookmark.ManageLabelsSession
import net.bible.sharedui.nav.NavResultChannel
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Issue #4201, round 2: the label-assign sheet's host ([BookmarkHost.assignSheet], built by `LabelAssignSheetHost`)
 * in the real `NavHostComposeActivity`, with the real controller memo, save and Room.
 *
 * The sheet's controls are driven through its [LabelAssignSheetSession] (the footer, ✕ and New (+) call exactly these
 * members; `LabelAssignSheetTest` covers the wiring from the controls). The host starts on `backup/restore`, as
 * [BookmarksManageLabelsCollectorTest] explains, so nothing but the host-level overlay can answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LabelAssignSheetHostTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        DatabaseResetter.resetDatabase()
    }

    private fun host(): NavHostComposeActivity {
        resetComposeUiDispatcher()
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.BACKUP),
        ).also { controllers += it }.create().start().resume().visible().get().also { idle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    /** Idles the main looper until [condition] holds (the save and the bookmark writes hop through Dispatchers.IO). */
    private fun idleUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }
    }

    /** Idles for about [millis] of wall time, for writes that must NOT happen. */
    private fun idleWallTime(millis: Long) {
        val end = System.nanoTime() + Duration.ofMillis(millis).toNanos()
        while (System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }
    }

    private val control: BookmarkControl get() = GlobalContext.get().get()

    private fun navController(activity: NavHostComposeActivity): NavHostController =
        NavHostComposeActivity::class.java.getDeclaredField("navController")
            .apply { isAccessible = true }.get(activity) as NavHostController

    @Suppress("UNCHECKED_CAST")
    private fun labelEditResults(activity: NavHostComposeActivity): NavResultChannel<LabelEditResult> =
        NavHostComposeActivity::class.java.getDeclaredField("labelEditResults")
            .apply { isAccessible = true }.get(activity) as NavResultChannel<LabelEditResult>

    private fun memo(host: BookmarkHost): Pair<*, *>? =
        BookmarkHost::class.java.getDeclaredField("manageLabelsSession").apply { isAccessible = true }.get(host) as Pair<*, *>?

    private fun session(host: BookmarkHost): ManageLabelsSession = memo(host)!!.second as ManageLabelsSession

    private fun label(name: String): BookmarkEntities.Label = runBlocking {
        control.insertOrUpdateLabel(BookmarkEntities.Label(new = true).apply { this.name = name })
    }

    private fun bookmark(ref: String, vararg labels: BookmarkEntities.Label): BookmarkEntities.BaseBookmarkWithNotes =
        runBlocking {
            control.addOrUpdateBibleBookmark(
                BookmarkEntities.BibleBookmarkWithNotes(
                    VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), ref),
                    null, true, null,
                ),
                labels.map { it.id }.toSet().takeIf { it.isNotEmpty() },
            )
        }

    private fun labelNames(bookmark: BookmarkEntities.BaseBookmarkWithNotes): Set<String> =
        runBlocking { control.labelsForBookmark(bookmark) }.map { it.name }.toSet()

    private fun assignPayload(selected: Set<IdType>, primary: IdType? = selected.firstOrNull()): String =
        ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.ASSIGN,
            selectedLabels = selected.toMutableSet(),
            bookmarkPrimaryLabel = primary,
        ).toJSON()

    /** One request through the host's own [BookmarkHost.requestLabelAssign], as the reading view makes it. */
    private class Asked(val job: Job) {
        var outcome: LabelAssignOutcome? = null
    }

    private fun ask(activity: NavHostComposeActivity, payload: String): Asked {
        lateinit var asked: Asked
        val job = activity.lifecycleScope.launch(Dispatchers.Main, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            asked.outcome = activity.appHosts.bookmark.requestLabelAssign(payload)
        }
        asked = Asked(job)
        job.start()
        return asked
    }

    /** The open sheet's session, once its controller has loaded the labels. */
    private fun openSheet(host: BookmarkHost): Pair<LabelAssignSheetSession, ManageLabelsController> {
        idleUntil("the assign sheet to open") { host.assignSheet != null && memo(host) != null }
        val controller = session(host).controller
        idleUntil("the sheet's labels to load") { controller.rows.value.any { it is ManageLabelsRow.Item } }
        return host.assignSheet!! to controller
    }

    private fun realBookmarksController(activity: NavHostComposeActivity, navigated: MutableList<String>): BookmarksController {
        val method = BookmarkHost::class.java.declaredMethods.single { it.name == "bookmarksControllerFor" }
        method.isAccessible = true
        return method.invoke(activity.appHosts.bookmark, 0, { _: Any? -> }, { payload: String -> navigated += payload })
            as BookmarksController
    }

    private fun loadBookmarkRows() {
        val service: net.bible.android.view.activity.bookmark.BookmarksServiceImpl = GlobalContext.get().get()
        runBlocking {
            service.loadRows(
                filterIndex = 0, sort = net.bible.sharedcore.bookmark.BookmarkSortMode.BIBLE_ORDER, search = null,
                showNotes = false,
            )
        }
    }

    // --- finding 1: a close without a change writes nothing ------------------------------------------------------

    /**
     * Two bookmarks with different labels: the sheet starts from the union (Red and Blue ticked). Closing it untouched
     * must not give both bookmarks both labels.
     */
    @Test
    fun closingTheSheetUntouchedLeavesSeveralBookmarksAsTheyWere() {
        val activity = host()
        val red = label("Red")
        val blue = label("Blue")
        val a = bookmark("Psalms 119:1", red)
        val b = bookmark("John 3:16", blue)
        loadBookmarkRows()
        val navigated = mutableListOf<String>()
        val list = realBookmarksController(activity, navigated)
        val host = activity.appHosts.bookmark

        list.enterSelection(a.id.toString())
        list.toggleSelection(b.id.toString())
        list.assignSelected()
        val (sheet, controller) = openSheet(host)
        assertEquals("fixture: the sheet starts from the union", setOf(red.id.toString(), blue.id.toString()),
            controller.resultSelected())

        sheet.dismiss()
        idleUntil("the sheet to close") { host.labelAssignSheets.current == null }
        idleWallTime(1500)

        assertEquals(setOf("Red"), labelNames(a))
        assertEquals(setOf("Blue"), labelNames(b))
        assertTrue("the close opened nothing else", navigated.isEmpty())
    }

    /** The same close after a real change still saves, onto every selected bookmark. */
    @Test
    fun closingTheSheetAfterAChangeSavesOntoEverySelectedBookmark() {
        val activity = host()
        val red = label("Red")
        val blue = label("Blue")
        val green = label("Green")
        val a = bookmark("Psalms 119:1", red)
        val b = bookmark("John 3:16", blue)
        loadBookmarkRows()
        val list = realBookmarksController(activity, mutableListOf())
        val host = activity.appHosts.bookmark

        list.enterSelection(a.id.toString())
        list.toggleSelection(b.id.toString())
        list.assignSelected()
        val (sheet, controller) = openSheet(host)
        controller.toggleChecked(green.id.toString())
        sheet.dismiss()

        idleUntil("the ticked set to reach both bookmarks") {
            labelNames(a) == setOf("Red", "Blue", "Green") && labelNames(b) == setOf("Red", "Blue", "Green")
        }
    }

    /** One bookmark (the reading view's request): an untouched close ends Cancelled; a tick ends Assigned. */
    @Test
    fun forOneBookmarkAnUntouchedCloseIsCancelledAndATickIsAssigned() {
        val activity = host()
        val red = label("Red")
        val blue = label("Blue")
        val host = activity.appHosts.bookmark

        val untouched = ask(activity, assignPayload(setOf(red.id)))
        val (sheet, _) = openSheet(host)
        sheet.dismiss()
        idleUntil("the untouched request to end") { untouched.outcome != null }
        assertEquals(LabelAssignOutcome.Cancelled, untouched.outcome)
        assertNull("nothing was saved, so nothing is memoised", memo(host))

        val ticked = ask(activity, assignPayload(setOf(red.id)))
        val (sheet2, controller2) = openSheet(host)
        controller2.toggleChecked(blue.id.toString())
        sheet2.dismiss()
        idleUntil("the ticked request to end") { ticked.outcome != null }
        val answer = ticked.outcome as LabelAssignOutcome.Assigned
        assertEquals(setOf(red.id, blue.id), ManageLabelsContract.ManageLabelsData.fromJSON(answer.data).selectedLabels)
    }

    // --- finding 3: the host's own paths ---------------------------------------------------------------------------

    /** "Edit labels" saves and answers Expand, also when nothing was changed. */
    @Test
    fun editLabelsAnswersExpandWithTheSheetsState() {
        val activity = host()
        val red = label("Red")
        val asked = ask(activity, assignPayload(setOf(red.id)))
        val (sheet, _) = openSheet(activity.appHosts.bookmark)

        sheet.editLabels()
        idleUntil("the request to end") { asked.outcome != null }
        val expand = asked.outcome as LabelAssignOutcome.Expand
        assertEquals(setOf(red.id), ManageLabelsContract.ManageLabelsData.fromJSON(expand.data).selectedLabels)
        assertNull(activity.appHosts.bookmark.labelAssignSheets.current)
    }

    /** A save whose writes fail delivers nothing: the sheet is shown again, and a later close saves. */
    @Test
    fun aFailedSaveShowsTheSheetAgain() {
        val activity = host()
        val red = label("Red")
        val blue = label("Blue")
        val host = activity.appHosts.bookmark
        val asked = ask(activity, assignPayload(setOf(red.id)))
        val (sheet, controller) = openSheet(host)
        val session = session(host)
        val realWrites = session.writes
        val writesField = ManageLabelsSession::class.java.getDeclaredField("writes").apply { isAccessible = true }
        val quietScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })
        writesField.set(session, ManageLabelsWrites(control, quietScope, barrier = { error("disk full") }))

        controller.toggleChecked(blue.id.toString())
        sheet.dismiss()
        idleUntil("the failed save to show the sheet again") { sheet.phase == LabelAssignSheetSession.Phase.OPEN }
        assertNull("no answer was delivered", asked.outcome)
        assertNotNull("the request is still open", host.labelAssignSheets.current)

        writesField.set(session, realWrites)
        sheet.dismiss()
        idleUntil("the retried save to answer") { asked.outcome != null }
        assertTrue(asked.outcome is LabelAssignOutcome.Assigned)
    }

    /** A second request waits for the first sheet's save and answer, then gets its own sheet. */
    @Test
    fun aSecondRequestWaitsForTheFirstSheet() {
        val activity = host()
        val red = label("Red")
        val blue = label("Blue")
        val host = activity.appHosts.bookmark
        val first = ask(activity, assignPayload(setOf(red.id)))
        val (sheet, controller) = openSheet(host)
        val second = ask(activity, assignPayload(setOf(blue.id)))
        idle()
        assertEquals("the first sheet stays open", assignPayload(setOf(red.id)), host.labelAssignSheets.current?.data)
        assertNull("the first request is not cancelled", first.outcome)

        controller.toggleChecked(blue.id.toString())
        sheet.dismiss()
        assertEquals("while the first saves, the second still waits", sheet, host.assignSheet)
        idleUntil("the first request to end") { first.outcome != null }
        assertTrue(first.outcome is LabelAssignOutcome.Assigned)

        idleUntil("the second sheet to open") { host.labelAssignSheets.current?.data == assignPayload(setOf(blue.id)) }
        val (secondSheet, secondController) = openSheet(host)
        assertNotSame(sheet, secondSheet)
        assertEquals("the second sheet starts from its own payload", setOf(blue.id.toString()), secondController.resultSelected())
        secondSheet.dismiss()
        idleUntil("the second request to end") { second.outcome != null }
    }

    /** A cancelled requester takes its sheet down and drops the memo, so the next full screen starts fresh. */
    @Test
    fun aCancelledRequestDropsItsMemo() {
        val activity = host()
        val red = label("Red")
        val blue = label("Blue")
        val host = activity.appHosts.bookmark
        val payload = assignPayload(setOf(red.id))
        val asked = ask(activity, payload)
        val (_, controller) = openSheet(host)
        controller.toggleChecked(blue.id.toString())

        asked.job.cancel()
        idleUntil("the sheet to close") { host.labelAssignSheets.current == null && host.assignSheet == null }
        assertNull("the cancelled sheet's working state is not memoised", memo(host))

        val controllerFor = BookmarkHost::class.java.declaredMethods.single { it.name == "manageLabelsControllerFor" }
            .apply { isAccessible = true }
        val fresh = controllerFor.invoke(host, payload, { _: String -> }, { _: Any? -> }) as ManageLabelsController
        assertNotSame(controller, fresh)
        assertEquals(setOf(red.id.toString()), fresh.resultSelected())
    }

    // --- New (+) and long press: the full-screen label editor -------------------------------------------------------

    private fun editorPayload(activity: NavHostComposeActivity): String {
        val nav = navController(activity)
        idleUntil("the label editor to open") {
            nav.currentBackStackEntry?.destination?.route == NavRoutes.LABEL_EDIT_PATTERN
        }
        return requireNotNull(nav.currentBackStackEntry?.arguments?.getString(NavRoutes.ARG_LABEL_DATA))
    }

    private fun answerEditor(activity: NavHostComposeActivity, result: LabelEditResult) {
        val nav = navController(activity)
        labelEditResults(activity).deliver(nav, nav.currentBackStackEntry, result)
        idle()
    }

    /** New (+) opens the editor; its save ticks the new label, and the sheet's save inserts it with a real id. */
    @Test
    fun newLabelOpensTheEditorAndItsSaveTicksTheLabel() {
        val activity = host()
        val red = label("Red")
        val host = activity.appHosts.bookmark
        val asked = ask(activity, assignPayload(setOf(red.id)))
        val (sheet, controller) = openSheet(host)

        sheet.newLabel()
        val payload = LabelEditContract.LabelData.fromJSON(editorPayload(activity))
        assertTrue("the editor opens on a new label", payload.label.new)
        assertEquals("the sheet steps aside", LabelAssignSheetSession.Phase.EDITING, sheet.phase)
        val tempId = payload.label.id.toString()

        payload.label.name = "Sermons"
        answerEditor(activity, LabelEditResult.Saved(payload.toJSON()))
        idleUntil("the sheet to come back") { sheet.phase == LabelAssignSheetSession.Phase.OPEN }
        assertTrue("the new label is ticked", tempId in controller.resultSelected())
        assertEquals("the editor was popped", NavRoutes.BACKUP, navController(activity).currentBackStackEntry?.destination?.route)

        sheet.dismiss()
        idleUntil("the request to end") { asked.outcome != null }
        val answer = ManageLabelsContract.ManageLabelsData.fromJSON((asked.outcome as LabelAssignOutcome.Assigned).data)
        val saved = runBlocking { control.assignableLabels() }.single { it.name == "Sermons" }
        assertFalse(saved.new)
        assertEquals("the temporary id is remapped to the saved one", setOf(red.id, saved.id), answer.selectedLabels)
    }

    /** Cancelling the editor changes nothing: the sheet comes back, and an untouched close stays Cancelled. */
    @Test
    fun cancellingTheNewLabelEditorChangesNothing() {
        val activity = host()
        val red = label("Red")
        val host = activity.appHosts.bookmark
        val asked = ask(activity, assignPayload(setOf(red.id)))
        val (sheet, controller) = openSheet(host)

        sheet.newLabel()
        editorPayload(activity)
        answerEditor(activity, LabelEditResult.Cancelled)
        idleUntil("the sheet to come back") { sheet.phase == LabelAssignSheetSession.Phase.OPEN }
        assertFalse(controller.hasChanges())

        sheet.dismiss()
        idleUntil("the request to end") { asked.outcome != null }
        assertEquals(LabelAssignOutcome.Cancelled, asked.outcome)
        assertTrue(runBlocking { control.assignableLabels() }.none { it.new || it.name.isEmpty() })
    }

    /** A row's long press opens the editor on that label; making it the main label there reaches the answer. */
    @Test
    fun aLongPressEditsTheLabelAndItsSettingsReachTheAnswer() {
        val activity = host()
        val red = label("Red")
        val blue = label("Blue")
        val host = activity.appHosts.bookmark
        val asked = ask(activity, assignPayload(setOf(red.id, blue.id), primary = red.id))
        val (sheet, _) = openSheet(host)

        sheet.editLabel(blue.id.toString())
        val payload = LabelEditContract.LabelData.fromJSON(editorPayload(activity))
        assertEquals(blue.id, payload.label.id)
        payload.isThisBookmarkPrimary = true
        answerEditor(activity, LabelEditResult.Saved(payload.toJSON()))
        idleUntil("the sheet to come back") { sheet.phase == LabelAssignSheetSession.Phase.OPEN }

        sheet.dismiss()
        idleUntil("the request to end") { asked.outcome != null }
        val answer = ManageLabelsContract.ManageLabelsData.fromJSON((asked.outcome as LabelAssignOutcome.Assigned).data)
        assertEquals(blue.id, answer.bookmarkPrimaryLabel)
    }
}
