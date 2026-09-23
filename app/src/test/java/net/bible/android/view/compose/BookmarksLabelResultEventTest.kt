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

import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarksAddedOrUpdatedEvent
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.control.page.ClientBibleBookmark
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * F54. Labels assigned from the BOOKMARKS LIST are written to the database but never announced, so an
 * open reading view keeps the verse's old labels until the page is reloaded. The reading view's own
 * quick-assign sheet works because it goes through `addOrUpdateBookmark`, which posts.
 *
 * Not a port regression in itself -- classic used the same DAO method. What changed is that the
 * bookmarks list is now a destination inside the SAME Activity, so the reading WebViews survive the
 * round trip instead of being re-created on return.
 *
 * `applyBookmarksManageLabelsResult`, [NavHostComposeActivity.BookmarksSession] and its
 * `bookmarksControllerFor` builder are all `private`, so this test drives the REAL host through the
 * same private-field/method reflection technique `CloudDocumentsControllerRebuildIsolationTest`
 * already uses in this module: build the real Activity, call the real private
 * `bookmarksControllerFor` to seed a real `BookmarksSession` (exactly as opening the bookmarks list
 * would), park a pending assignment on it the way `requestAssignLabels` does, then invoke the real
 * private `applyBookmarksManageLabelsResult` with a `ManageLabelsResult`.
 *
 * `applyBookmarksManageLabelsResult`'s Room writes run inside `lifecycleScope.launch(Dispatchers.IO)`,
 * a REAL background thread even under Robolectric (no test dispatcher is installed anywhere in this
 * module -- see `CloudDocumentsControllerRebuildIsolationTest`'s kdoc for the same fact), so a
 * `CountDownLatch` synchronizes the assertion with the background post rather than polling or
 * sleeping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookmarksLabelResultEventTest {

    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    /** `firstTime` pinned false first -- see `ReadingHostBackChainTest.host()`'s kdoc for why. */
    private fun host(): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.bookmarks()),
        ).also { hostControllers += it }.create().get()
    }

    @After
    fun tearDown() {
        hostControllers.forEach { it.close() }
        hostControllers.clear()
        DatabaseResetter.resetDatabase()
    }

    private fun bookmarkControl(): BookmarkControl = GlobalContext.get().get()
    private fun bookmarksService(): BookmarksServiceImpl = GlobalContext.get().get()

    private val kjv = Versifications.instance().getVersification("KJV")

    /** Two real Bible bookmarks, persisted through the same `BookmarkControl` the host itself injects
     *  (a Koin singleton -- see `ReadingHostBackChainTest.historyManager()` for the same reasoning). */
    private fun twoBookmarks(): List<BookmarkEntities.BaseBookmarkWithNotes> {
        val control = bookmarkControl()
        val one = control.addOrUpdateBibleBookmark(
            BookmarkEntities.BibleBookmarkWithNotes(
                VerseRangeFactory.fromString(kjv, "Psalms 119:1"), null, true, null,
            ),
            null,
        )
        val two = control.addOrUpdateBibleBookmark(
            BookmarkEntities.BibleBookmarkWithNotes(
                VerseRangeFactory.fromString(kjv, "Psalms 119:2"), null, true, null,
            ),
            null,
        )
        return listOf(one, two)
    }

    /**
     * The same two bookmarks, but reloaded through [BookmarksServiceImpl.loadRows] /
     * [BookmarksServiceImpl.bookmarksByIds] -- the REAL path `requestAssignLabels` uses
     * (`bookmarksService.bookmarksByIds(ids)`), which loads with `addData = false` and therefore
     * `labelIds == null` (F54's actual fixture shape; [twoBookmarks] alone already has
     * `labelIds = []` from `addOrUpdateBibleBookmark`'s own `addLabels` call, which would hide the
     * NPE this test is about).
     */
    private fun twoBookmarksAsTheBookmarksListWouldLoadThem(): List<BookmarkEntities.BaseBookmarkWithNotes> {
        val created = twoBookmarks()
        val service = bookmarksService()
        runBlocking { service.loadRows(filterIndex = 0, sort = BookmarkSortMode.BIBLE_ORDER, search = null, showNotes = false) }
        val reloaded = service.bookmarksByIds(created.map { it.id.toString() })
        assertEquals("fixture setup: both bookmarks must reload", 2, reloaded.size)
        reloaded.forEach { assertEquals("fixture setup: loadRows must not populate labelIds", null, it.labelIds) }
        return reloaded
    }

    /** `private fun bookmarksControllerFor(initialFilterIndex: Int, onSelectBookmark: ..., navigateToManageLabels: ...): BookmarksController`. */
    private fun seedBookmarksSession(activity: NavHostComposeActivity) {
        val method = NavHostComposeActivity::class.java.declaredMethods
            .single { it.name == "bookmarksControllerFor" }
        method.isAccessible = true
        val onSelectBookmark: (Any?) -> Unit = {}
        val navigateToManageLabels: (String) -> Unit = {}
        method.invoke(activity, 0, onSelectBookmark, navigateToManageLabels)
    }

    /** The `BookmarksSession` [seedBookmarksSession] just built -- `private class BookmarksSession`,
     *  reached the same untyped way `CloudDocumentsControllerRebuildIsolationTest.currentEntry` reaches
     *  its own private per-entry state. */
    private fun currentBookmarksSession(activity: NavHostComposeActivity): Any {
        val field = NavHostComposeActivity::class.java.getDeclaredField("bookmarksSession")
        field.isAccessible = true
        val pair = field.get(activity) as Pair<*, *>?
        return requireNotNull(pair?.second) { "bookmarksControllerFor did not seed bookmarksSession" }
    }

    /** `var pendingAssign: List<BookmarkEntities.BaseBookmarkWithNotes>?` on `BookmarksSession` -- what
     *  `requestAssignLabels` records before navigating to the label manager. */
    private fun setPendingAssign(session: Any, bookmarks: List<BookmarkEntities.BaseBookmarkWithNotes>) {
        val field = session.javaClass.getDeclaredField("pendingAssign")
        field.isAccessible = true
        field.set(session, bookmarks)
    }

    /** `private fun applyBookmarksManageLabelsResult(result: ManageLabelsResult)`. */
    private fun applyManageLabelsResult(activity: NavHostComposeActivity, result: ManageLabelsResult) {
        val method = NavHostComposeActivity::class.java.declaredMethods
            .single { it.name == "applyBookmarksManageLabelsResult" }
        method.isAccessible = true
        method.invoke(activity, result)
    }

    /**
     * Fixture: two bookmarks and a `ManageLabelsResult` selecting one label, then the host's
     * `applyBookmarksManageLabelsResult` -- the round trip `requestAssignLabels` /
     * `applyBookmarksManageLabelsResult` make between them, minus the navigation itself (which is the
     * arm's job, not the host's, and not what F54 is about).
     */
    private fun applyLabelsFromTheBookmarksListTo(
        bookmarks: List<BookmarkEntities.BaseBookmarkWithNotes>,
    ): BookmarkEntities.Label {
        val activity = host()
        seedBookmarksSession(activity)
        val session = currentBookmarksSession(activity)
        setPendingAssign(session, bookmarks)

        val label = bookmarkControl().insertOrUpdateLabel(BookmarkEntities.Label(new = true).apply { name = "Grace" })
        val payload = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.ASSIGN,
            selectedLabels = mutableSetOf(label.id),
        ).toJSON()

        applyManageLabelsResult(activity, ManageLabelsResult(payload))
        return label
    }

    @Test
    fun assigningLabelsFromTheBookmarksListAnnouncesTheChangeOnce() {
        // Built BEFORE the event bus is watched: `addOrUpdateBibleBookmark` (the creation path) posts
        // its own BookmarksAddedOrUpdatedEvent per bookmark, which would otherwise be counted as if it
        // were the label-assignment announcement under test and mask a genuine 0.
        val bookmarks = twoBookmarks()

        val seen = mutableListOf<BookmarksAddedOrUpdatedEvent>()
        val latch = CountDownLatch(1)
        ABEventBus.register(this) {
            on<BookmarksAddedOrUpdatedEvent> {
                seen += it
                latch.countDown()
            }
        }
        try {
            applyLabelsFromTheBookmarksListTo(bookmarks)

            assertTrue(
                "the background write never announced the change within 5s (F54)",
                latch.await(5, TimeUnit.SECONDS),
            )
            assertEquals(
                "the change must be announced exactly once for the whole batch -- the open reading " +
                    "view learns of it no other way (F54)",
                1,
                seen.size,
            )
            assertEquals(
                "and it must carry both bookmarks",
                2,
                seen.single().bookmarks.size,
            )
        } finally {
            ABEventBus.unregister(this)
        }
    }

    /**
     * F54 fix round 2. `assigningLabelsFromTheBookmarksListAnnouncesTheChangeOnce` above only ever
     * checked the event COUNT -- it never looked at what the posted bookmarks actually contain, so it
     * stayed green while the posted objects carried stale/null `labelIds` (the guard-6 pattern the
     * final review's C1 called out). This test drives the fixture through the REAL load path
     * (`BookmarksServiceImpl.loadRows`/`bookmarksByIds`, `addData = false`) so the pending-assign
     * bookmarks start with `labelIds == null`, exactly as `requestAssignLabels` hands them over in
     * production, then asserts the POSTED bookmarks carry the newly-chosen label and that serialising
     * them for the WebView (`ClientBibleBookmark(...).asJson`) does not throw.
     */
    @Test
    fun assigningLabelsFromTheBookmarksListPostsBookmarksWithTheNewLabels() {
        val bookmarks = twoBookmarksAsTheBookmarksListWouldLoadThem()

        val seen = mutableListOf<BookmarksAddedOrUpdatedEvent>()
        val latch = CountDownLatch(1)
        ABEventBus.register(this) {
            on<BookmarksAddedOrUpdatedEvent> {
                seen += it
                latch.countDown()
            }
        }
        try {
            val label = applyLabelsFromTheBookmarksListTo(bookmarks)

            assertTrue(
                "the background write never announced the change within 5s (F54)",
                latch.await(5, TimeUnit.SECONDS),
            )
            val posted = seen.single().bookmarks
            assertEquals(2, posted.size)
            posted.forEach {
                val bibleBookmark = it as BookmarkEntities.BibleBookmarkWithNotes
                assertEquals(
                    "the posted bookmark must carry the label just assigned to it, not a stale/null " +
                        "list (F54 -- ClientBibleBookmark.asJson would otherwise NPE on labelIds!!)",
                    listOf(label.id),
                    bibleBookmark.labelIds,
                )
                // The WebView subscriber's actual serialisation call (BibleView.kt:1015 ->
                // ClientPageObjects.kt:340, `bookmark.labelIds!!`). Must not throw.
                ClientBibleBookmark(bibleBookmark, kjv).asJson
            }
        } finally {
            ABEventBus.unregister(this)
        }
    }
}
