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
package net.bible.service.history

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Slice 7 Task 3 / spec §5.1.
 *
 * `HistoryManager.createHistoryItem` used to ask `CurrentActivityHolder.currentActivity is
 * MainBibleActivity`; it now asks [ReadingViewVisibility]. That branch is the ONLY producer of
 * [KeyHistoryItem], which is in turn the only item type `getEntities`/`restoreFrom` persist — so a
 * silent regression here empties the verse back-stack AND stops history surviving a restart, with
 * no compile error and no other failing test.
 *
 * The fixture is a real `WindowControl`/`WindowRepository`/`Window` graph on Robolectric (same
 * style as `WindowPaneMenuStateBuilderTest`), because `Window` and `WindowControl` are non-`open`
 * Kotlin classes that Mockito's default mock maker cannot stub, and because `createHistoryItem`
 * reads a real `currentPage.currentDocument` / `singleKey` / `anchorOrdinal`.
 *
 * Note on the predicate's OTHER side: in a unit test `CurrentActivityHolder.currentActivity` is
 * always null, so the old `is MainBibleActivity` check is permanently false here. That is why the
 * "no item" case is paired with [theVisibilityFlagIsWhatGatesKeyHistoryItemCreation], a
 * differential test whose visible-true half fails if the predicate is reverted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHistoryAnchorTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var historyManager: HistoryManager

    private val window: Window get() = windowRepository.activeWindow

    /** Psalm 139:2 in KJV — a real installed module, so `getEntities`/`restoreFrom` can round-trip. */
    private val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 139, 2)

    private fun historyManagerWithOneWindow(): HistoryManager {
        val kjv = requireNotNull(Books.installed().getBook("KJV")) { "KJV test module must be installed" }
        window.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)
        windowControl.activeWindow = window
        return historyManager
    }

    @Before
    fun setUp() {
        ReadingViewVisibility.setVisible(false)
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()
        historyManager = HistoryManager(windowControl)
    }

    @After
    fun tearDown() {
        ReadingViewVisibility.setVisible(false)
        ABEventBus.unregister(historyManager)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    @Test
    fun aKeyHistoryItemIsCreatedWhenTheReadingViewIsVisible() {
        ReadingViewVisibility.setVisible(true)
        val manager = historyManagerWithOneWindow()
        manager.addHistoryItem(window)
        assertTrue(
            manager.getHistory(window.id).firstOrNull() is KeyHistoryItem,
            "the reading view is visible, so the verse position must be recorded"
        )
    }

    @Test
    fun noKeyHistoryItemIsCreatedWhenTheReadingViewIsNotVisible() {
        ReadingViewVisibility.setVisible(false)
        val manager = historyManagerWithOneWindow()
        manager.addHistoryItem(window)
        assertTrue(
            manager.getHistory(window.id).none { it is KeyHistoryItem },
            "nothing is looking at the reading view, so there is no verse position to record"
        )
    }

    /**
     * The discriminating form of the two tests above: ONE manager, one window, the flag flipped
     * between the two calls. Reverting the predicate to `currentActivity is MainBibleActivity`
     * fails this at the first assertion (nothing is ever recorded); hard-wiring the branch to
     * `true` fails it at the second (a second item appears).
     */
    @Test
    fun theVisibilityFlagIsWhatGatesKeyHistoryItemCreation() {
        val manager = historyManagerWithOneWindow()

        ReadingViewVisibility.setVisible(true)
        manager.addHistoryItem(window)
        assertEquals(1, manager.getHistory(window.id).count { it is KeyHistoryItem })

        ReadingViewVisibility.setVisible(false)
        window.pageManager.currentBible.setCurrentDocumentAndKey(
            requireNotNull(Books.installed().getBook("KJV")),
            Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 23, 1)
        )
        manager.addHistoryItem(window)
        assertEquals(
            1, manager.getHistory(window.id).count { it is KeyHistoryItem },
            "the flag is off, so the second move must not be recorded"
        )
    }

    /**
     * `getEntities`/`restoreFrom` (`HistoryManager.kt:80-114`) persist ONLY `KeyHistoryItem`, so the
     * predicate is also what keeps history alive across a restart. A test that only checked item
     * creation would miss a regression in persistence.
     */
    @Test
    fun keyHistoryItemsStillRoundTripThroughTheEntities() {
        ReadingViewVisibility.setVisible(true)
        val manager = historyManagerWithOneWindow()
        manager.addHistoryItem(window)

        val entities = manager.getEntities(window.id)
        assertEquals(1, entities.size, "the visible reading view must produce one persistable entity")
        assertEquals("KJV", entities.single().document)

        manager.clear()
        assertEquals(0, manager.getHistory(window.id).size)

        manager.restoreFrom(window, entities)
        val restored = manager.getHistory(window.id)
        assertEquals(1, restored.size)
        assertEquals(verse.osisID, (restored.single() as KeyHistoryItem).key.osisID)
    }

    // ------------------------------------------------------------------ the production wiring

    /**
     * Fix round 1, Major 3. Every test above drives [ReadingViewVisibility] by hand, so deleting
     * all the `setVisible` calls from `MainBibleActivity` leaves them all green while
     * `KeyHistoryItem` is never created again in the real app — exactly the silent failure this
     * task exists to prevent. These two tests are the gate on the PRODUCTION wiring.
     *
     * Fix round 1, Major 1. `ActivityBase.onCreate`'s FIRST line is
     * `CurrentActivityHolder.activate(this)` (`ActivityBase.kt:88`), so the OLD predicate
     * (`currentActivity is MainBibleActivity`) was true for the whole of `onCreate` — and
     * `MainBibleActivity.onCreate` really does post `AddHistoryItem` inside that window, via its
     * `openLink` deep-link branch -> `WindowControl.showLink` -> `setCurrentDocumentAndKey` ->
     * `CurrentPageBase.setKey(key, addHistoryItem = true)` -> `ABEventBus.post(AddHistoryItem)`,
     * and the bus is synchronous. A flag first set in `onResume` is false there, and
     * `createHistoryItem` then falls through to the `currentActivity is AndBibleActivity` arm —
     * `MainBibleActivity` IS an `AndBibleActivity` with `integrateWithHistoryManager = true`
     * (`MainBibleActivity.kt:370`) — recording a WRONG `IntentHistoryItem` whose `revertTo()`
     * re-runs the deep-link intent.
     *
     * Task 6 note: when the setters move into the reading destination's `DisposableEffect`, these
     * two tests are the one place to update — replace the Activity controller with whatever drives
     * that composition; the assertions themselves stay.
     */
    @Test
    fun theReadingActivityIsAlreadyVisibleAtTheEndOfOnCreate() {
        ReadingViewVisibility.setVisible(false)
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            controller.create()
            assertTrue(
                ReadingViewVisibility.isVisible,
                "the old predicate was true from CurrentActivityHolder.activate() in " +
                    "ActivityBase.onCreate onwards, and onCreate's openLink branch posts " +
                    "AddHistoryItem inside that window",
            )
        } finally {
            controller.close()
        }
    }

    @Test
    fun theReadingActivityLifecycleTurnsTheFlagOnAtResumeAndOffAtPause() {
        ReadingViewVisibility.setVisible(false)
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            controller.create().start().resume()
            assertTrue(ReadingViewVisibility.isVisible, "a resumed reading view is visible")

            controller.pause()
            assertFalse(ReadingViewVisibility.isVisible, "a paused reading view is not visible")

            // The assertion after the FIRST resume cannot see a deleted `onResume` setter — the
            // onCreate one has already made the flag true. Coming BACK from paused is what only
            // onResume can do.
            controller.resume()
            assertTrue(
                ReadingViewVisibility.isVisible,
                "returning from paused must turn it back on — this is the assertion that fails if " +
                    "the onResume setter is deleted",
            )
        } finally {
            controller.close()
        }
    }

    /**
     * The fourth call site. Chooser results are delivered BEFORE `onResume`, and the arms below
     * the setter call `setKey(…, addHistoryItem = true)` / `setCurrentDocument(…)`, which post
     * `AddHistoryItem` synchronously — so the flag has to be back on by then. The
     * `CurrentActivityHolder.activate(this)` on the line above it is the old predicate's version of
     * exactly this, which is what makes it the right place.
     *
     * An unknown `ActivityResultKind` extra is used deliberately: `fromExtra` returns null, the
     * `when` does nothing, and the assertion is about the setter alone rather than about any
     * chooser's payload handling.
     */
    @Test
    fun aChooserResultMakesTheReadingViewVisibleAgainBeforeOnResume() {
        ReadingViewVisibility.setVisible(false)
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().start().resume().pause().get()
            assertFalse(ReadingViewVisibility.isVisible, "sanity: the chooser is on top")

            activity.onActivityResult(
                ActivityBase.STD_REQUEST_CODE,
                Activity.RESULT_OK,
                Intent().apply { putExtra(ActivityResultKind.EXTRA, "NoSuchKind") },
            )

            assertTrue(
                ReadingViewVisibility.isVisible,
                "the chooser result is handled before onResume, and its arms post AddHistoryItem",
            )
        } finally {
            controller.close()
        }
    }

    // ------------------------------------------------------------------ the other two arms

    /**
     * Fix round 1, Minor 2. Nothing covered `createHistoryItem`'s `intent != null` arm, so
     * reordering the arms to test `isVisible` first compiles, passes every other test, and
     * silently breaks the bookmark history path (`NavHostComposeActivity.kt:2770` calls
     * `addHistoryItem(null, resultIntent)` while the reading view is visible). The intent arm must
     * win over a visible reading view.
     */
    @Test
    fun anIntentStillWinsOverAVisibleReadingView() {
        ReadingViewVisibility.setVisible(true)
        val manager = historyManagerWithOneWindow()
        val intent = Intent().apply { putExtra("description", "Bookmarks") }

        manager.addHistoryItem(window, intent)

        val item = manager.getHistory(window.id).firstOrNull()
        assertTrue(
            item is IntentHistoryItem,
            "an explicit intent must still produce an IntentHistoryItem; got ${item?.javaClass?.simpleName}",
        )
        assertEquals("Bookmarks", item.description.toString(), "…carrying the intent's own description")
    }

    // ------------------------------------------------------------------ persistence, deeper

    /**
     * Fix round 1, Minor 3. [keyHistoryItemsStillRoundTripThroughTheEntities] is one item deep with
     * a null `anchorOrdinal`, so `getEntities`' consecutive-duplicate collapse
     * (`HistoryManager.kt:83-85`) and the `anchorOrdinal` -> [OrdinalRange] round trip (`:110`)
     * both survive deletion. This one exercises both.
     *
     * The duplicate pair is built through `restoreFrom` rather than through `addHistoryItem`,
     * because `add()` (`:216`) already refuses to push an item equal to `stack.peek()` and
     * `KeyHistoryItem.equals` compares document + key only — so a consecutive duplicate can ONLY
     * enter the stack by being restored from the database, which is precisely the case the
     * collapse in `getEntities` exists for.
     */
    @Test
    fun consecutiveDuplicatesCollapseAndAnchorOrdinalsSurviveTheRoundTrip() {
        ReadingViewVisibility.setVisible(true)
        val manager = historyManagerWithOneWindow()
        val kjv = requireNotNull(Books.installed().getBook("KJV"))
        val psalm23 = Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 23, 1)

        val restored = listOf(
            WorkspaceEntities.HistoryItem(window.id, Date(1_000), "KJV", verse.osisID, 7),
            WorkspaceEntities.HistoryItem(window.id, Date(2_000), "KJV", verse.osisID, 9),
            WorkspaceEntities.HistoryItem(window.id, Date(3_000), "KJV", psalm23.osisID, null),
        )
        manager.restoreFrom(window, restored)

        // `getHistory` reverses the stack (most recent first), so undo that to read them in the
        // order they were restored in.
        val items = manager.getHistory(window.id).filterIsInstance<KeyHistoryItem>().reversed()
        assertEquals(3, items.size, "restoreFrom pushes every entity, duplicates included")
        assertEquals(
            listOf(7, 9),
            items.filter { it.key.osisID == verse.osisID }.map { it.anchorOrdinal?.start },
            "each restored anchorOrdinal must become an OrdinalRange with the same start",
        )
        assertEquals(
            null, items.last().anchorOrdinal,
            "…and a null anchorOrdinal stays null rather than becoming OrdinalRange(0)",
        )

        val entities = manager.getEntities(window.id)
        assertEquals(
            2, entities.size,
            "the two consecutive same-document/same-key items must collapse to one; got " +
                entities.map { "${it.document}/${it.key}@${it.anchorOrdinal}" },
        )
        assertEquals(
            listOf(verse.osisID, psalm23.osisID), entities.map { it.key },
            "the FIRST of the duplicate pair is the one kept",
        )
        assertEquals(
            listOf(7, null), entities.map { it.anchorOrdinal },
            "…and its anchorOrdinal survives the trip back out",
        )
        assertEquals("KJV", entities.first().document)
        assertEquals(kjv.initials, entities.first().document, "sanity: that IS the KJV's initials")
    }
}
