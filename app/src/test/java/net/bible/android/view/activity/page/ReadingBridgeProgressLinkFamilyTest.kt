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

import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import net.bible.android.control.search.SearchControl
import net.bible.android.platform.AndroidAppEnvironment
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.sharedcore.platform.UserNotifier
import net.bible.android.control.link.LinkPlatform
import net.bible.sharedui.reading.bridge.ReadingSpeaker
import org.crosswire.jsword.passage.Verse
import org.koin.core.context.GlobalContext
import org.robolectric.shadows.ShadowStatFs
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.progress.ProgressControl
import net.bible.android.control.progress.ProgressJsActions
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.ReadingSheets
import net.bible.test.DatabaseResetter
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Progress, memorization, speak and link families of [ReadingBridge], driven on a bare
 * [BibleWindowSession] (no WebView).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingBridgeProgressLinkFamilyTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private lateinit var navigator: ReadingNavigator
    private lateinit var linkControl: LinkControl
    private lateinit var downloadControl: DownloadControl
    private lateinit var fakeBooks: FakeBookFactory
    private val speaker = RecordingSpeaker()
    private val storagePath get() = Environment.getExternalStorageDirectory().path
    private lateinit var hostScope: CoroutineScope
    private lateinit var session: BibleWindowSession
    private lateinit var bridge: ReadingBridge
    private val appScope = AppCoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var launcher: OrderedLauncher

    @Before fun setUp() {
        windowControl = WindowControl()
        repo = net.bible.test.testWindowRepository()
        windowControl.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        launcher = OrderedLauncher(appScope)
        navigator = mock()
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) {}
            override fun showError(message: String, cause: Throwable?) {}
        }
        fakeBooks = FakeBookFactory(testCoreStrings())
        linkControl = LinkControl(fakeBooks, windowControl,
            BookmarkControl(windowControl, testAppSettings(), testCoreStrings(), launcher,
                net.bible.android.platform.androidSaxTextPolicy()),
            GlobalContext.get().get<SearchControl>(), testAppSettings(), notifier, testCoreStrings(),
            Mockito.mock(LinkPlatform::class.java))
        downloadControl = DownloadControl(notifier, testCoreStrings(),
            AndroidAppEnvironment(ApplicationProvider.getApplicationContext()))
        hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        session = BibleWindowSession(
            repo.activeWindow, BibleViewJsChannel(mock<JsHost>()) {}, { hostScope },
            mock<ReadingWindowSurface>(), navigator,
            windowControlProvider = { windowControl },
            linkControlProvider = { linkControl }, downloadControlProvider = { downloadControl },
        )
        val bookmarkControl = BookmarkControl(windowControl, testAppSettings(), testCoreStrings(), launcher,
            net.bible.android.platform.androidSaxTextPolicy())
        bridge = ReadingBridge(session,
            progressJsActionsProvider = { ProgressJsActions(launcher) },
            speakerProvider = { speaker },
            bookmarkJsActionsProvider = { BookmarkJsActions(launcher, bookmarkControl) })
    }

    @After fun tearDown() {
        try {
            session.dispose()
            appScope.cancel()
            hostScope.cancel()
            DatabaseResetter.resetDatabase(repo.scope, appScope, hostScope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
            ShadowStatFs.unregisterStats(storagePath)
        }
    }

    private class RecordingSpeaker : ReadingSpeaker {
        var speaking = false
        val calls = CopyOnWriteArrayList<String>()
        override val isSpeaking get() = speaking
        override fun pauseForNextSpeech() { calls += "pause" }
        override fun speakBible(book: SwordBook, verse: Verse) { calls += "speakBible" }
        override fun speakMemorizationLoop(book: SwordBook, startVerse: Verse, endVerse: Verse) { calls += "loop" }
        override fun speakGeneric(bookAndKey: BookAndKey) { calls += "generic" }
    }

    /** Waits until every progress write already queued for the session's window has run. */
    private fun drain() = runBlocking { withTimeout(10_000) { launcher.launch(session.window.id) {}.join() } }

    /** Runs the main looper (the bridge posts to Dispatchers.Main) until [done] holds. */
    private fun awaitMain(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("main-thread work did not finish", done())
    }

    private val targets get() = runBlocking { ProgressControl.getAllMemorizationTargets() }

    private val memorizeDocument get() = fakeBooks.memorizeDocument
    private val memorizeShown get() =
        repo.windowList.any { it.pageManager.currentPage.currentDocument === memorizeDocument }

    @Test fun memorizeAddsATargetThenStartsMemorizeMode() {
        bridge.memorize("KJV", 1000, 1002)
        awaitMain { memorizeShown }
        assertEquals("memorize mode started after the target was written", 1, targets.size)
    }

    @Test fun memorizeOfUnknownBookWritesNoTarget() {
        bridge.memorize("NoSuchBook", 1000, 1002)
        drain()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(targets.isEmpty())
    }

    @Test fun markAsMemorizedWritesProgress() {
        assertEquals(0, runBlocking { ProgressControl.getTotalMemorizedVerses() })
        bridge.markAsMemorized("KJV", 1000, 1002)
        drain()
        assertEquals(3, runBlocking { ProgressControl.getTotalMemorizedVerses() })
        bridge.unmarkMemorized("KJV", 1000, 1002)
        drain()
        assertEquals(0, runBlocking { ProgressControl.getTotalMemorizedVerses() })
    }

    @Test fun addAndRemoveMemorizationTargetByRange() {
        bridge.addMemorizationTarget("KJV", 1000, 1002)
        drain()
        assertEquals(1, targets.size)
        bridge.removeMemorizationTarget("KJV", 1000, 1002)
        drain()
        assertTrue(targets.isEmpty())
    }

    @Test fun markAsMemorizedForUnknownBookDoesNothing() {
        bridge.markAsMemorized("NoSuchBook", 1000, 1002)
        drain()
        assertEquals(0, runBlocking { ProgressControl.getTotalMemorizedVerses() })
    }

    private fun chapterReads(): Int {
        val verse = org.crosswire.jsword.passage.Verse(KJVA, BibleBook.GEN, 1, 1)
        return runBlocking { ProgressControl.getChapterReadCount(KJVA, verse.book, 1) }
    }

    @Test fun recordChapterReadUnknownSourceFallsBackToManual() {
        // The source string is a ReadingSource name; an unknown one falls back to MANUAL (the row
        // is recorded, nothing throws).
        val ordinal = KJVA.getOrdinal(org.crosswire.jsword.passage.Verse(KJVA, BibleBook.GEN, 1, 1))
        bridge.recordChapterRead("KJV", ordinal, 1, "NOT_A_SOURCE")
        drain()
        assertEquals(1, chapterReads())
    }

    @Test fun recordChapterReadForUnknownBookWritesNothing() {
        bridge.recordChapterRead("NoSuchBook", 1, 1, "AUTO")
        drain()
        assertEquals(0, chapterReads())
    }

    @Test fun speakPausesARunningSessionFirst() {
        speaker.speaking = true
        bridge.speak("KJV", "KJV", 1000, -1)
        awaitMain { "speakBible" in speaker.calls }
        assertEquals(listOf("pause", "speakBible"), speaker.calls.toList())
    }

    @Test fun speakDoesNotPauseWhenNothingIsSpeaking() {
        bridge.speak("KJV", "KJV", 1000, -1)
        awaitMain { "speakBible" in speaker.calls }
        assertEquals(listOf("speakBible"), speaker.calls.toList())
    }

    @Test fun speakMemorizationLoopPausesARunningSessionFirst() {
        speaker.speaking = true
        bridge.speakMemorizationLoop("KJV", "KJV", 1000, 1002)
        awaitMain { "loop" in speaker.calls }
        assertEquals(listOf("pause", "loop"), speaker.calls.toList())
    }

    @Test fun speakAfterDisposeIsIgnored() {
        session.dispose()
        bridge.speak("KJV", "KJV", 1000, -1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(speaker.calls.isEmpty())
    }

    @Test fun openDownloadsDoesNothingWhenDownloadNotOkay() {
        // Robolectric reports no free space, so the storage check fails.
        bridge.openDownloads()
        verify(navigator, never()).openDownloads(any())
    }

    @Test fun openDownloadsOpensAddonsWhenDownloadIsOkay() {
        ShadowStatFs.registerStats(storagePath, 1_000_000, 1_000_000, 1_000_000)
        bridge.openDownloads()
        verify(navigator).openDownloads(true)
    }

    @Test fun externalBrowserLinkGoesThroughTheNavigator() {
        bridge.openExternalLink("https://example.org")
        verify(navigator).openExternalUrl("https://example.org")
    }

    @Test fun externalLinkAfterDisposeIsIgnored() {
        session.dispose()
        bridge.openExternalLink("https://example.org")
        verify(navigator, never()).openExternalUrl(any())
    }

    @Test fun openChapterReadHistoryUsesTheKjvBookNameOnTheHostSheet() {
        val sheets = mock<ReadingSheets>()
        session.hostSheets = sheets
        val ordinal = KJVA.getOrdinal(org.crosswire.jsword.passage.Verse(KJVA, BibleBook.GEN, 1, 1))
        bridge.openChapterReadHistory("KJV", ordinal, 1)
        awaitMain { Mockito.mockingDetails(sheets).invocations.isNotEmpty() }
        verify(sheets).showReadHistory(BibleBook.GEN.name, 1)
    }

    @Test fun toggleCompareDocumentTogglesTheHiddenSet() {
        val hidden = session.workspaceSettings.hideCompareDocuments
        hidden.remove("KJV")
        bridge.toggleCompareDocument("KJV")
        assertTrue(hidden.contains("KJV"))
        bridge.toggleCompareDocument("KJV")
        assertTrue(!hidden.contains("KJV"))
    }
}
