package net.bible.android.view.compose

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import android.os.Bundle
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.comingFromStartupActivity
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.bookmark.BookmarksController
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.ReadingFixtureOwners
import net.bible.test.awaitHostCondition
import net.bible.test.awaitHostRoute
import net.bible.test.closeReadingHostsBeforeReset
import net.bible.test.resetComposeUiDispatcher
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real host regressions: retaining the route must not reset its draft or orphan its parent request. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookmarkRecreationStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val hosts = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private var originalFirstTime = false
    private var originalStartup = false
    private val control: BookmarkControl get() = GlobalContext.get().get()

    @Before fun prepare() {
        originalFirstTime = firstTime
        originalStartup = comingFromStartupActivity
        firstTime = false
        comingFromStartupActivity = false
        resetComposeUiDispatcher()
    }

    @After fun cleanup() {
        closeReadingHostsBeforeReset(hosts, restoreBeforeReset = {
            firstTime = originalFirstTime
            comingFromStartupActivity = originalStartup
        })
    }

    private fun host(): NavHostComposeActivity = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.bookmarks()),
    ).also { hosts += it }.create().start().resume().visible().get().also {
        awaitHostRoute(it, NavRoutes.BOOKMARKS_PATTERN, ::advance)
        awaitHostCondition("real bookmark DAO load", ::advance) { !bookmarks(it).loading.value && bookmarks(it).rows.value.isNotEmpty() }
    }

    private fun advance() { compose.mainClock.advanceTimeBy(32) }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun nav(activity: NavHostComposeActivity) = field(activity, "navController") as NavHostController

    private fun bookmarks(activity: NavHostComposeActivity): BookmarksController {
        val pair = NavHostComposeActivity::class.java.declaredMethods.single { it.name == "getBookmarksSession" }
            .apply { isAccessible = true }.invoke(activity) as Pair<*, *>
        return field(requireNotNull(pair.second), "controller") as BookmarksController
    }

    private fun managerOrNull(activity: NavHostComposeActivity): ManageLabelsController? =
        (field(activity, "manageLabelsSession") as Pair<*, *>?)?.second?.let {
            field(it, "controller") as ManageLabelsController
        }

    private fun manager(activity: NavHostComposeActivity): ManageLabelsController {
        // A back-stack route is published before the entering arm constructs its host session.
        awaitHostCondition("manager destination constructs its new host-bound controller", ::advance) {
            managerOrNull(activity) != null
        }
        return requireNotNull(managerOrNull(activity))
    }

    private fun seedBookmark(): BookmarkEntities.BaseBookmarkWithNotes = runBlocking {
        control.addOrUpdateBookmark(
            BookmarkEntities.BibleBookmarkWithNotes(
                VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Psalms 119:1"),
                null, true, null,
            ), null,
        )
    }

    private fun openNewAssignedLabel(activity: NavHostComposeActivity, bookmarkId: String) {
        bookmarks(activity).enterSelection(bookmarkId)
        bookmarks(activity).assignSelected()
        awaitHostRoute(activity, NavRoutes.MANAGE_LABELS_PATTERN, ::advance)
        awaitHostCondition("manager finishes its DAO label load", ::advance) { manager(activity).rows.value.isNotEmpty() }
        manager(activity).newLabel()
        awaitHostRoute(activity, NavRoutes.LABEL_EDIT_PATTERN, ::advance)
        compose.onNodeWithText("Label 1").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("BM13 unsaved assigned label")
        // Close only the identity sheet. The name is a live controller edit, not a submitted dialog.
        // ModalBottomSheet owns a separate dialog window; the Activity's dispatcher is not its Back owner.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss)
        awaitHostCondition("identity sheet finishes dismissing before recreation", ::advance) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Favourite label").performClick()
        compose.onNodeWithText("BM13 unsaved assigned label").assertExists()
        compose.onNodeWithContentDescription("Favourite label").assertIsOn()
    }

    private fun recreate(activity: NavHostComposeActivity, route: String = NavRoutes.LABEL_EDIT_PATTERN): NavHostComposeActivity {
        // Keep the old owners until teardown has joined them, even though the controller now owns the new instance.
        ReadingFixtureOwners.retain(activity)
        resetComposeUiDispatcher()
        return hosts.last().recreate().get().also {
            assertNotSame("must cross the actual Activity recreation boundary", activity, it)
            awaitHostRoute(it, route, ::advance)
        }
    }

    @Test fun bookmarkSearchSelectionAndExpansionSurviveRecreation() {
        val bookmark = seedBookmark()
        runBlocking {
            bookmark.notes = "BM13 searchable note"
            control.addOrUpdateBookmark(bookmark, updateNotes = true)
        }
        assertEquals(listOf(bookmark.id.toString()), runBlocking {
            control.getBibleBookmarksWithLabel(control.allLabels().first(), search = "BM13 searchable note").map { it.id.toString() }
        })
        val activity = host()
        val old = bookmarks(activity)
        old.openSearch()
        old.setSearch("BM13 searchable note")
        awaitHostCondition("filtered bookmark rows finish loading", ::advance) {
            !old.loading.value && old.rows.value.any { it.id == bookmark.id.toString() }
        }
        old.enterSelection(bookmark.id.toString())
        old.toggleExpanded(bookmark.id.toString())
        assertEquals("BM13 searchable note", old.searchText.value)
        assertTrue(old.searchModeActive.value)
        assertEquals(setOf(bookmark.id.toString()), old.selection.value)

        val recreated = recreate(activity, NavRoutes.BOOKMARKS_PATTERN)
        awaitHostCondition("new bookmark controller finishes DAO load", ::advance) {
            val pair = NavHostComposeActivity::class.java.declaredMethods.single { it.name == "getBookmarksSession" }
                .apply { isAccessible = true }.invoke(recreated) as Pair<*, *>?
            pair != null && !bookmarks(recreated).loading.value
        }
        val restored = bookmarks(recreated)
        assertNotSame(old, restored)
        assertEquals("BM13 searchable note", restored.searchText.value)
        assertTrue(restored.searchModeActive.value)
        assertEquals(0, restored.selectedFilterIndex.value)
        assertEquals(setOf(bookmark.id.toString()), restored.selection.value)
        assertEquals(setOf(bookmark.id.toString()), restored.expandedIds.value)
        assertEquals(listOf(bookmark.id.toString()), restored.rows.value.map { it.id })
        recreated.onBackPressedDispatcher.onBackPressed()
        awaitHostCondition("Back clears selection before search", ::advance) { restored.selection.value.isEmpty() }
        assertTrue(restored.searchModeActive.value)
        // The arm's handler lambda captures `selection` from composition. A user cannot press Back
        // before the frame that follows the first Back, so let that recomposition happen first.
        compose.waitForIdle()
        recreated.onBackPressedDispatcher.onBackPressed()
        awaitHostCondition("second Back closes search", ::advance) { !restored.searchModeActive.value }
        assertEquals("", restored.searchText.value)
    }

    @Test fun myDocumentsSearchAndSelectedIdentitySurviveInitialReloadAfterRecreation() {
        val dao = net.bible.service.db.DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        runBlocking {
            dao.insert(net.bible.android.database.mydocument.MyDocument(
                name = "BM13 Alpha", initials = "MyDoc_BM13_Alpha", orderNumber = 0,
            ))
            dao.insert(net.bible.android.database.mydocument.MyDocument(
                name = "BM13 Beta", initials = "MyDoc_BM13_Beta", orderNumber = 1,
            ))
        }
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.myDocuments()),
        ).also { hosts += it }.create().start().resume().visible().get()
        awaitHostRoute(activity, NavRoutes.MY_DOCUMENTS_PATTERN, ::advance)
        fun documentsOrNull(host: NavHostComposeActivity): net.bible.sharedcore.mydocuments.MyDocumentsController? =
            field(host, "myDocumentsSession")?.let { field(it, "controller") as net.bible.sharedcore.mydocuments.MyDocumentsController }
        awaitHostCondition("MyDocuments real DAO initial load", ::advance) { documentsOrNull(activity)?.totalCount?.value == 2 }
        val old = requireNotNull(documentsOrNull(activity))
        // Reordering before selecting exercises the token-to-persistent-document identity boundary.
        old.moveItem(1, 0)
        old.openSearch()
        old.setQuery("Beta")
        val beta = old.documents.value.single()
        assertEquals("MyDoc_BM13_Beta", beta.initials)
        old.toggleSelect(beta.id)
        assertEquals(setOf(beta.id), old.selection.value)
        assertTrue(old.searchModeActive.value)
        assertEquals("Beta", old.query.value)

        val recreated = recreate(activity, NavRoutes.MY_DOCUMENTS_PATTERN)
        awaitHostCondition("recreated MyDocuments completes real DAO load", ::advance) { documentsOrNull(recreated)?.totalCount?.value == 2 }
        val restored = requireNotNull(documentsOrNull(recreated))
        assertNotSame(old, restored)
        assertEquals("Beta", restored.query.value)
        assertTrue(restored.searchModeActive.value)
        assertEquals(listOf("MyDoc_BM13_Beta"), restored.documents.value.map { it.initials })
        assertEquals(setOf("MyDoc_BM13_Beta"), restored.documents.value
            .filter { it.id in restored.selection.value }.map { it.initials }.toSet())
        assertEquals(2, restored.totalCount.value)
        assertFalse("old-host autosave is acknowledged, not replayed as a dirty draft", restored.dirty.value)
        assertEquals(listOf("MyDoc_BM13_Beta", "MyDoc_BM13_Alpha"), runBlocking {
            dao.allDocuments().map { it.initials }
        })
        assertTrue("DAO reorder changed the positional token; selection must follow initials", beta.id != restored.documents.value.single().id)
    }

    @Test fun savedStateDoesNotGrowWithUnchangedLabels() {
        runBlocking {
            repeat(400) { i ->
                control.insertOrUpdateLabel(BookmarkEntities.Label(new = true).apply { name = "BM13 bulk label $i" })
            }
        }
        val bookmark = seedBookmark()
        val activity = host()
        bookmarks(activity).enterSelection(bookmark.id.toString())
        bookmarks(activity).assignSelected()
        awaitHostRoute(activity, NavRoutes.MANAGE_LABELS_PATTERN, ::advance)
        awaitHostCondition("manager loaded every label", ::advance) { manager(activity).currentLabelItems().size >= 400 }
        val saved = Bundle()
        hosts.last().saveInstanceState(saved)
        val payload = saved.getString("bookmark-recreation").orEmpty()
        assertTrue("unchanged labels must be reloaded from the database, not Bundled (was ${payload.length} chars)",
            payload.length < 20_000)
    }

    @Test fun malformedBookmarkRecreationBundleFallsBackToAFreshHost() {
        val saved = Bundle().apply { putString("bookmark-recreation", "{not-json") }
        resetComposeUiDispatcher()
        val restored = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.bookmarks()),
        ).also { hosts += it }.create(saved).start().resume().visible().get()
        awaitHostRoute(restored, NavRoutes.BOOKMARKS_PATTERN, ::advance)
    }

    @Test fun recreatedAssignedLabelSavesThroughItsRestoredManagerAndBookmarkRequest() {
        val bookmark = seedBookmark()
        val activity = host()
        val originalBookmarks = bookmarks(activity)
        openNewAssignedLabel(activity, bookmark.id.toString())
        val originalManager = manager(activity)
        val recreated = recreate(activity)

        // This is the principal RED at HEAD: the new composition displays the original "Label 1".
        compose.onNodeWithText("BM13 unsaved assigned label").assertExists()
        compose.onNodeWithContentDescription("Favourite label").assertIsOn()
        assertTrue("a draft must never be written merely to survive recreation", runBlocking {
            control.allLabels().none { it.name == "BM13 unsaved assigned label" }
        })

        compose.onNodeWithContentDescription("OK").performClick()
        awaitHostRoute(recreated, NavRoutes.MANAGE_LABELS_PATTERN, ::advance)
        awaitHostCondition("editor result reaches restored manager", ::advance) {
            managerOrNull(recreated)?.currentLabelItems()?.any { it.name == "BM13 unsaved assigned label" } == true
        }
        assertNotSame(originalManager, manager(recreated))
        assertTrue("editor Save stages the entity; manager Save owns persistence", runBlocking {
            control.allLabels().none { it.name == "BM13 unsaved assigned label" }
        })
        manager(recreated).save()
        awaitHostRoute(recreated, NavRoutes.BOOKMARKS_PATTERN, ::advance)
        awaitHostCondition("restored pending assignment reaches the real bookmark", ::advance) {
            runBlocking { control.labelsForBookmark(bookmark) }.any { it.name == "BM13 unsaved assigned label" && it.favourite }
        }
        assertNotSame(originalBookmarks, bookmarks(recreated))
        assertEquals(1, runBlocking { control.allLabels().count { it.name == "BM13 unsaved assigned label" } })

        // A completed visit must not seed the next editor from the old draft.
        bookmarks(recreated).manageLabels()
        awaitHostRoute(recreated, NavRoutes.MANAGE_LABELS_PATTERN, ::advance)
        manager(recreated).newLabel()
        awaitHostRoute(recreated, NavRoutes.LABEL_EDIT_PATTERN, ::advance)
        compose.onNodeWithText("Label 1").assertExists()
    }

    @Test fun recreatedDirtyLabelBackStillAsksAndDiscardCancelsWithoutWriting() {
        val bookmark = seedBookmark()
        val activity = host()
        openNewAssignedLabel(activity, bookmark.id.toString())
        val recreated = recreate(activity)

        recreated.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("No").performClick()
        compose.onNodeWithText("BM13 unsaved assigned label").assertExists()
        recreated.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithText("Yes").performClick()
        awaitHostRoute(recreated, NavRoutes.MANAGE_LABELS_PATTERN, ::advance)
        assertFalse(manager(recreated).currentLabelItems().any { it.name == "BM13 unsaved assigned label" })
        manager(recreated).save()
        awaitHostRoute(recreated, NavRoutes.BOOKMARKS_PATTERN, ::advance)
        // The (non-)assignment is applied asynchronously on IO; settle the host before asserting absence.
        val settleUntil = System.nanoTime() + 1_000_000_000L
        while (System.nanoTime() < settleUntil) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).runOneTask(); Thread.sleep(20)
        }
        assertTrue(runBlocking { control.labelsForBookmark(bookmark) }.isEmpty())
        assertTrue(runBlocking { control.allLabels().none { it.name == "BM13 unsaved assigned label" } })
    }
}
