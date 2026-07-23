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
package net.bible.android.control.page.toolbar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.passage.PassageChangedEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.speak.SpeakControl
import net.bible.service.cloudsync.CloudSyncEvent
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.sharedcore.reading.ToolbarState
import net.bible.test.DatabaseResetter
import net.bible.test.PassageTestData
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises [ToolbarStateServiceImpl] against the REAL [WindowControl]/[DocumentControl]/
 * [PageControl]/[SpeakControl] graph (Robolectric + [TestBibleApplication], same style as
 * [net.bible.android.control.page.window.WindowStateServiceTest]) rather than mocking the
 * collaborators: `DocumentControl` and `SpeakControl` are plain (non-`open`) Kotlin classes, so
 * Mockito's default (non-inline) mock maker cannot stub them. `DocumentControl`/`PageControl`/
 * `SpeakControl` are resolved from the real Koin container (started by
 * [net.bible.android.BibleApplication.onCreate]) so they share the SAME [WindowControl] singleton
 * ([CommonUtils.windowControl]) whose `windowRepository` this test replaces with a fresh
 * in-memory one, mirroring `WindowStateServiceTest.setUp`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ToolbarStateServiceImplTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var pageControl: PageControl
    private lateinit var service: ToolbarStateServiceImpl

    private val kjv: org.crosswire.jsword.book.Book get() = Books.installed().getBook("KJV")!!
    private val kjvV11n get() = Versifications.instance().getVersification("KJV")
    private val gen11 get() = Verse(kjvV11n, BibleBook.GEN, 1, 1)

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository

        val koin = GlobalContext.get()
        pageControl = koin.get()
        service = ToolbarStateServiceImpl(
            windowControl = windowControl,
            documentControl = koin.get<DocumentControl>(),
            pageControl = pageControl,
            speakControl = koin.get<SpeakControl>(),
        )
    }

    @After
    fun tearDown() {
        ABEventBus.unregister(service)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    /** Seeds the active window's Bible page WITHOUT posting any event (CurrentPage-level overload). */
    private fun seedActivePageSilently(book: org.crosswire.jsword.book.Book, key: Verse) {
        windowControl.activeWindowPageManager.currentBible.setCurrentDocumentAndKey(book, key)
    }

    @Test
    fun toolbarStartsEmpty() {
        assertThat(service.toolbar.value, equalTo(ToolbarState.EMPTY))
    }

    @Test
    fun passageChangedEvent_rebuildsSnapshotFromActivePage() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)

        ABEventBus.post(PassageChangedEvent())

        val state = service.toolbar.value
        val pageManager = windowControl.activeWindowPageManager
        assertThat(state.documentTitle, equalTo(PassageTestData.ESV.name))
        assertThat(state.pageTitle, equalTo(pageControl.currentBibleVerse.name))
        assertThat(state.showBible, equalTo(true)) // at least one bible (ESV/KJV) is installed
        assertThat(state.showStrongs, equalTo(pageManager.hasStrongs))
        assertThat(state.searchable, equalTo(true))
        assertThat(state.speakable, equalTo(true))
        assertThat(state.speakStopped, equalTo(true)) // nothing is speaking in the test process
    }

    @Test
    fun cloudSyncEvent_setsOnlySyncRunning() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val before = service.toolbar.value
        assertThat(before.syncRunning, equalTo(false))

        ABEventBus.post(CloudSyncEvent(running = true))
        val duringSync = service.toolbar.value
        assertThat(duringSync.syncRunning, equalTo(true))
        // every other field is untouched
        assertThat(duringSync.copy(syncRunning = false), equalTo(before))

        ABEventBus.post(CloudSyncEvent(running = false))
        assertThat(service.toolbar.value, equalTo(before))
    }

    @Test
    fun currentWindowChangedEvent_rebuildsFromNewlyActiveWindow() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        assertThat(service.toolbar.value.documentTitle, equalTo(PassageTestData.ESV.name))

        // 2-arg addNewWindow seeds the new (still inactive) window's page directly.
        val w2 = windowControl.addNewWindow(kjv, gen11)

        // switching active window posts CurrentWindowChangedEvent (WindowRepository.notifyActiveWindowChanged)
        windowControl.activeWindow = w2

        assertThat(service.toolbar.value.documentTitle, equalTo(kjv.name))
    }

    @Test
    fun currentVerseChangedEvent_rebuildsSnapshot() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val before = service.toolbar.value

        seedActivePageSilently(kjv, gen11) // silent -> no event yet, state must still be stale
        assertThat(service.toolbar.value, equalTo(before))

        ABEventBus.post(CurrentVerseChangedEvent(windowControl.activeWindow))

        val after = service.toolbar.value
        assertThat(after.documentTitle, equalTo(kjv.name))
        assertThat(after.documentTitle, not(equalTo(before.documentTitle)))
    }

    @Test
    fun strongsMode_reflectsActiveWindowTextDisplaySetting() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)

        windowControl.activeWindowPageManager.textDisplaySettings.strongsMode = 1
        service.refresh()
        assertThat(service.toolbar.value.strongsMode, equalTo(1))

        // A later event-driven rebuild (not just refresh()) must re-read the live setting too.
        windowControl.activeWindowPageManager.textDisplaySettings.strongsMode = 2
        ABEventBus.post(PassageChangedEvent())
        assertThat(service.toolbar.value.strongsMode, equalTo(2))
    }

    @Test
    fun speakEvent_rebuildsSnapshot() {
        seedActivePageSilently(PassageTestData.ESV, PassageTestData.PS_139_2)
        service.refresh()
        val before = service.toolbar.value

        seedActivePageSilently(kjv, gen11) // silent -> no event yet
        assertThat(service.toolbar.value, equalTo(before))

        ABEventBus.post(SpeakEvent(SpeakEvent.SpeakState.SILENT))

        val after = service.toolbar.value
        assertThat(after.documentTitle, equalTo(kjv.name))
        assertThat(after.documentTitle, not(equalTo(before.documentTitle)))
    }
}
