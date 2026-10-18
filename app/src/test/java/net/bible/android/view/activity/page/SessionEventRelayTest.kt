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

package net.bible.android.view.activity.page

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkChange
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.progress.ProgressChange
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.misc.OsisFragment
import net.bible.service.common.CommonUtils
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.event.EventSource
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.DocumentLoader
import net.bible.sharedui.reading.session.DocumentLoaderPlatform
import net.bible.sharedui.reading.session.SessionEventRelay
import net.bible.sharedui.reading.session.SessionEventRelayPlatform
import net.bible.service.sword.mydocument.MyDocumentChange
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [SessionEventRelay] driven with its owner streams replaced by plain event sources and a recording [JsHost]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionEventRelayTest {
    private class RecordingHost : JsHost {
        val js = CopyOnWriteArrayList<String>()
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override fun post(block: () -> Unit) = block()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {
            this.js += js
            callback?.invoke("null")
        }
    }

    private class PlatformCalls : SessionEventRelayPlatform {
        val calls = CopyOnWriteArrayList<String>()
        override fun onActiveWindowChanged(isThisWindowActive: Boolean) { calls += "active=$isThisWindowActive" }
        override fun updateOffsets(immediate: Boolean) { calls += "offsets=$immediate" }
        override fun markWindowsCheckNeeded() { calls += "check" }
        override fun updateConfig() { calls += "config" }
    }

    private val loaderPlatform = object : DocumentLoaderPlatform {
        override val lastUpdated = 0L
        override val topOffset = 0f
        override val bottomOffset = 0f
        override val hasActiveIndicator = false
        override fun configInputs(favouriteLabelIds: List<IdType>) = error("unused")
        override fun isCurrentView() = true
        override fun enableZoomForMap(isMap: Boolean) {}
    }

    private val surface = object : ReadingWindowSurface {
        override fun copyText(label: String?, text: String) {}
        override fun shareText(text: String, subject: String?) {}
        override fun shareHtml(html: String, title: String) {}
        override fun stopSelection(removeRanges: Boolean) {}
        override fun toast(text: String) {}
        override fun openWebSearch(query: String) {}
    }

    private lateinit var repo: net.bible.android.control.page.window.WindowRepository
    private lateinit var originalRepo: net.bible.android.control.page.window.WindowRepository
    private val host = RecordingHost()
    private val platform = PlatformCalls()
    private val bookmarkEvents = EventSource<BookmarkChange>()
    private val progressEvents = EventSource<ProgressChange>()
    private val windowEvents = EventSource<WindowChange>()
    private val aiDocEvents = EventSource<MyDocumentChange>()
    private val settingsEvents = EventSource<Unit>()
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var session: BibleWindowSession
    private lateinit var relay: SessionEventRelay

    @Before fun setUp() {
        repo = net.bible.test.testWindowRepository()
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        val channel = BibleViewJsChannel(host) {}
        channel.markClientReady()
        session = BibleWindowSession(
            window = repo.activeWindow,
            jsChannel = channel,
            hostScope = { hostScope },
            surface = surface,
            navigator = java.lang.reflect.Proxy.newProxyInstance(
                ReadingNavigator::class.java.classLoader, arrayOf(ReadingNavigator::class.java),
            ) { _, m, _ -> error("unexpected navigator call ${m.name}") } as ReadingNavigator,
        )
        relay = SessionEventRelay(
            session, DocumentLoader(session, loaderPlatform), platform,
            windowChanges = { windowEvents },
            progressChanges = progressEvents,
            readingProgressSettingsChanged = settingsEvents,
            aiDocChanges = aiDocEvents,
            bookmarkChangeEvents = { bookmarkEvents },
        )
    }

    @After fun tearDown() {
        try {
            relay.stop()
            session.dispose()
            DatabaseResetter.resetDatabase(repo.scope, hostScope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
        }
    }

    private fun newBookmark(): BookmarkEntities.BibleBookmarkWithNotes =
        BookmarkEntities.BibleBookmarkWithNotes(
            VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Gen 1:1"),
            null, true, null,
        ).apply { labelIds = emptyList() }

    private fun awaitJs(timeoutMs: Long = 5_000, condition: (List<String>) -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition(host.js.toList())) return true
            Thread.sleep(20)
        }
        return condition(host.js.toList())
    }

    @Test fun bookmarkAddedInAnotherWindowReachesThisWindow() {
        relay.start()
        val bookmark = newBookmark()
        bookmarkEvents.emit(BookmarkChange.BookmarksUpserted(listOf(bookmark)))
        assertTrue("JS: ${host.js}", awaitJs { js -> js.any { it.contains("add_or_update_bookmarks") && it.contains(bookmark.id.toString()) } })
    }

    @Test fun memorizationOrdinalsAreConvertedToTheDocumentsV11n() {
        val book = Books.installed().getBook("FinRK") as SwordBook
        val german = book.versification
        val key = Verse(german, BibleBook.PS, 3, 2)
        session.firstDocument = BibleDocument(
            emptyList(), VerseRange(german, key),
            OsisFragment(SwordContentFacade.readOsisFragment(book, key, strings = net.bible.test.testCoreStrings()), key, book),
            book, key,
        )
        relay.start()
        val kjvPs3v1 = Verse(KJVA, BibleBook.PS, 3, 1).ordinal
        val germanPs3v2 = Verse(german, BibleBook.PS, 3, 2).ordinal
        assertTrue("fixture must really differ", kjvPs3v1 != germanPs3v2)
        progressEvents.emit(ProgressChange.Memorization(addedMemorized = listOf(kjvPs3v1)))
        val js = host.js.single { it.contains("update_memorization_data") }
        assertTrue(js, js.contains("\"addedMemorized\": [$germanPs3v2]"))
    }

    @Test fun stoppedRelayEmitsNothing() {
        relay.start()
        relay.stop()
        bookmarkEvents.emit(BookmarkChange.BookmarksUpserted(listOf(newBookmark())))
        progressEvents.emit(ProgressChange.Memorization(addedMemorized = listOf(1)))
        settingsEvents.emit(Unit)
        windowEvents.emit(WindowChange.RestoreButtonsChanged)
        awaitJs(300) { false }
        assertEquals(emptyList<String>(), host.js.toList())
        assertEquals(emptyList<String>(), platform.calls.toList())
    }

    @Test fun startingTwiceSubscribesOnce() {
        relay.start()
        relay.start()
        windowEvents.emit(WindowChange.RestoreButtonsChanged)
        assertEquals(listOf("offsets=false"), platform.calls.toList())
    }

    @Test fun disposedSessionEmitsNothing() {
        relay.start()
        // Control: the settings stream emits JS even with no document loaded.
        settingsEvents.emit(Unit)
        assertTrue("settings change must reach JS before dispose", host.js.isNotEmpty())
        host.js.clear()
        session.dispose()
        settingsEvents.emit(Unit)
        assertEquals(emptyList<String>(), host.js.toList())
        assertFalse(session.emit(BibleViewEvents.response(1L, null)))
    }

    @Test fun windowChangesReachThePlatformInOrder() {
        relay.start()
        windowEvents.emit(WindowChange.LayoutConfigurationChanged)
        windowEvents.emit(WindowChange.ActiveWindowChanged(repo.activeWindow))
        assertEquals(listOf("check", "active=true"), platform.calls.toList().take(2))
    }
}
