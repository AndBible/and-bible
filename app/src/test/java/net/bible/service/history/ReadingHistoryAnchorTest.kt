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

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
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
import org.robolectric.android.controller.ActivityController
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

    /**
     * R7b: `setVisible(false)` no longer resets everything on its own — the visibility rule is now
     * "registered by a FOREGROUND host", and the foreground token lives in [ReadingHostPresence].
     * A reading host driven through its lifecycle here declares itself foreground, so a test
     * that did not retract it would hand the next one a stale host.
     */
    private fun resetReadingSeams() {
        ReadingViewVisibility.setVisible(false)
        ReadingHostPresence.setForeground(null)
    }

    /**
     * Slice 8 F2: the reading host these production-wiring tests drive is the reading-route
     * [NavHostComposeActivity] (`MainBibleActivity` is deleted). `firstTime` is pinned false for the
     * reason `ReadingHostBackChainTest.host()` gives (a pending night-mode `recreate()`), and Compose's
     * JVM-wide main dispatcher is re-armed first ([resetComposeUiDispatcher], slice 8 D1): without it,
     * after the first NavHost host in this JVM the reading destination never composes, and a test
     * reading the destination's `enter`/`exit` would be asserting on the bootstrap bridge alone.
     */
    private fun readingHost(): ActivityController<NavHostComposeActivity> {
        firstTime = false
        resetComposeUiDispatcher()
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        )
    }

    private fun historyManagerWithOneWindow(): HistoryManager {
        val kjv = requireNotNull(Books.installed().getBook("KJV")) { "KJV test module must be installed" }
        window.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)
        windowControl.activeWindow = window
        return historyManager
    }

    @Before
    fun setUp() {
        resetReadingSeams()
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()
        historyManager = HistoryManager(windowControl)
    }

    @After
    fun tearDown() {
        resetReadingSeams()
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
     * **The Activity path still has to have an owner (nav-graph slice 7 Task 6 fix round 1).**
     *
     * Task 6 moved the flag's ownership into the reading destination's `DisposableEffect` and
     * deleted these three tests with `MainBibleActivity`'s four setters — but the destination cannot
     * render the reading view yet (`ComposeReadingViewHost` is constructed with a
     * `MainBibleActivity`), so its effect never runs in the real app and the LIVE path was left with
     * no owner at all: no `KeyHistoryItem` ever created, and `HistoryManager.goBack`'s
     * `if (!isVisible) finish()` firing on every back-with-history. The four call sites are back,
     * driving [ReadingViewVisibility.setActivityVisible] — an input orthogonal to the destination's
     * depth counter — and these three tests are back with them, because every test above drives the
     * flag by hand and so cannot see a flag nobody sets.
     *
     * They are the gate on the PRODUCTION wiring, and each one is proven RED by deleting its own
     * call site. They go when the destination's content slot becomes real and the Activity input is
     * deleted; the destination half of the same gate is
     * `ReadingDestinationInGraphTest.theReadingDestinationOwnsTheVisibilityFlag`, and the
     * composition of the two inputs is `ReadingViewVisibilityTest` in `:sharedCore`.
     *
     * **Reading-host re-typing R7b added a SECOND thing each of these call sites does.** The rule
     * is now "registered by a host [ReadingHostPresence] says is FOREGROUND", so every place that
     * declares this Activity's reading view present also declares the Activity itself foreground,
     * and `onPause` retracts both. The assertions below are unchanged in direction because the
     * classic Activity declares and retracts the two together — which is exactly why
     * [theReadingActivityDeclaresAndRetractsItsForegroundPresence] exists beside them: with both
     * halves moving as one, an assertion on `isVisible` alone cannot tell which half is wired.
     *
     * `ActivityBase.onCreate`'s FIRST line is `CurrentActivityHolder.activate(this)`
     * (`ActivityBase.kt:88`), so the OLD predicate (`currentActivity is MainBibleActivity`) was true
     * for the whole of `onCreate` — and `MainBibleActivity.onCreate` really does call
     * `HistoryManager.recordIfCreated` inside that window, via its `openLink` deep-link branch ->
     * `WindowControl.showLink` -> `setCurrentDocumentAndKey` -> `CurrentPageBase.setKey(key,
     * addHistoryItem = true)` -> `HistoryManager.recordIfCreated`, synchronously. A
     * flag first set in `onResume` is false there, and `createHistoryItem` then falls through to the
     * `currentActivity is AndBibleActivity` arm — `MainBibleActivity` IS an `AndBibleActivity` with
     * `integrateWithHistoryManager = true` — recording a WRONG `IntentHistoryItem` whose
     * `revertTo()` re-runs the deep-link intent. Hence the first of the three.
     *
     * **Slice 8 F2: rehosted on the reading-route [NavHostComposeActivity]** (`MainBibleActivity` is
     * deleted). Its `bootstrapIfNeeded` declares the presence and the bootstrap bridge
     * (`setActivityVisible(this, true)`) inside `onCreate`, `onResume` re-declares the presence, and
     * `onPause` retracts both. The classic fourth call site's test,
     * `aChooserResultMakesTheReadingViewVisibleAgainBeforeOnResume`, was DELETED with that class: it
     * measured `MainBibleActivity.onActivityResult` flipping visibility before `onResume`, which the nav
     * host deliberately does not do (`NavHostComposeActivity.onActivityResult`'s kdoc: results are held
     * until `onResume`).
     */
    @Test
    fun theReadingActivityIsAlreadyVisibleAtTheEndOfOnCreate() {
        ReadingViewVisibility.setVisible(false)
        val controller = readingHost()
        try {
            controller.create()
            assertTrue(
                ReadingViewVisibility.isVisible,
                "the old predicate was true from CurrentActivityHolder.activate() in " +
                    "ActivityBase.onCreate onwards, and onCreate's openLink branch calls " +
                    "HistoryManager.recordIfCreated inside that window",
            )
        } finally {
            controller.close()
        }
    }

    @Test
    fun theReadingActivityLifecycleTurnsTheFlagOnAtResumeAndOffAtPause() {
        ReadingViewVisibility.setVisible(false)
        val controller = readingHost()
        try {
            controller.create().start().resume().visible()
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
     * **INVERTED by reading-host re-typing R7b, deliberately — this was
     * `anActivityPauseDoesNotClearAComposedDestination`, and it asserted the defect.**
     *
     * The old rule was "either input, whoever registered it", so this test asserted that a paused
     * Activity with ANOTHER host's reading destination still composed counts as visible. That is
     * precisely the divergence R7b fixes: a composition-scoped effect stays entered while its host
     * Activity is in the background (navigation-compose does not dispose the current entry's
     * content when the Activity stops), so the state this test described is "the user is looking at
     * a classic secondary screen while a backgrounded host's reading destination keeps the flag
     * on" — which made `HistoryManager.goBack()`'s `if (!isVisible) finish()` never fire, i.e. a
     * DEAD BACK KEY, and recorded a `KeyHistoryItem` for a `HistoryManager.recordIfCreated` call made in the
     * background.
     *
     * Under the new rule a registration only counts while [ReadingHostPresence] says its host is
     * foreground, so the assertion flips. The property the old test was really protecting — that
     * one reading view's exit cannot un-register another's — did not go away and is pinned where it
     * can be stated without a lifecycle: `ReadingViewVisibilityTest.oneHostsRegistrationsAreNotAnothersToRemove`
     * and `.theTwoInputsCompose` in `:sharedCore`.
     *
     * Mutation: drop the [ReadingHostPresence] gate from `ReadingViewVisibility.isVisible` and the
     * second assertion fails — which is the RED this task started from.
     */
    @Test
    fun anActivityPauseClearsTheFlagEvenWhenAnotherHostsDestinationIsComposed() {
        resetReadingSeams()
        val navHost = Any()  // another host's token — the nav host, as far as this seam can tell
        val controller = readingHost()
        try {
            controller.create().start().resume()
            // A reading DESTINATION, as the nav host composes it — registered under ITS host.
            ReadingViewVisibility.enter(navHost)
            assertTrue(ReadingViewVisibility.isVisible, "sanity: the reading host's view is in front")

            controller.pause()
            assertFalse(
                ReadingViewVisibility.isVisible,
                "the reading host paused and the other host is not in front either — a " +
                    "destination composed under a backgrounded host is not what the user sees",
            )

            // …and it is that host's presence, not its registration, that was missing: give the
            // nav host the front and the same, untouched registration counts again.
            ReadingHostPresence.setForeground(navHost)
            assertTrue(
                ReadingViewVisibility.isVisible,
                "the destination was never un-registered — only its host was in the background",
            )

            ReadingViewVisibility.exit(navHost)
            assertFalse(ReadingViewVisibility.isVisible, "…and with nothing registered it is false")
        } finally {
            controller.close()
        }
    }

    /**
     * **R7b: the classic Activity's OTHER production wiring.** Every call site that declares its
     * reading view present also declares the Activity foreground ([ReadingHostPresence]), and
     * `onPause` retracts it — `clearForeground(this)`, not `setForeground(null)`, so a stale pause
     * cannot clear a host that came to the front after it.
     *
     * **What this test adds that the three `isVisible` tests above cannot (corrected in fix round 1
     * — the first version of this kdoc claimed they would all still pass without the `onResume`
     * declaration, and that is simply false: `onPause` retracts the presence, so the second
     * `controller.resume()` in [theReadingActivityLifecycleTurnsTheFlagOnAtResumeAndOffAtPause]
     * would leave the Activity registration alone and `isVisible` false, and its third assertion
     * goes red).** Two things:
     *
     *  - **The stale-pause interleaving**, which nothing else at the Activity level covers: another
     *    host takes the front while this Activity is still resumed, and only then does this
     *    Activity's pause arrive. Swap `clearForeground(this)` for `setForeground(null)` and every
     *    other test in this file stays green, because with one host in play the two are identical.
     *  - **A direct assertion on the presence**, not on `isVisible`. The three tests above read the
     *    conjunction of the presence and the Activity input; when the task that makes the reading
     *    destination's content slot real deletes that Activity input (and those three tests with
     *    it), this one still pins the half that stays.
     */
    @Test
    fun theReadingActivityDeclaresAndRetractsItsForegroundPresence() {
        resetReadingSeams()
        val controller = readingHost()
        try {
            val activity = controller.create().get()
            assertTrue(
                ReadingHostPresence.isForeground(activity),
                "onCreate declares the presence, for the same reason it declares the flag: the " +
                    "deep-link history is recorded inside onCreate",
            )

            controller.start().resume().pause()
            assertFalse(
                ReadingHostPresence.isForeground(activity),
                "onPause retracts this Activity's own presence",
            )

            // A stale pause: another host is already in front when this one's onPause arrives.
            controller.resume()
            val otherHost = Any()
            ReadingHostPresence.setForeground(otherHost)
            controller.pause()
            assertTrue(
                ReadingHostPresence.isForeground(otherHost),
                "a stale onPause must retract only its OWN presence — clearForeground(this), not " +
                    "setForeground(null)",
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
