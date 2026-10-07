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
package net.bible.android.control

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import net.bible.sharedcore.event.Subscription
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class PassageChangeMediatorTest {
    private val received = mutableListOf<PageChange>()
    private lateinit var subscription: Subscription

    @Before fun setUp() {
        PassageChangeMediator.resetSubscribersForTest()
        subscription = PassageChangeMediator.changes.subscribe { received += it }
    }
    @After fun tearDown() { subscription.cancel() }

    private val window get() = CommonUtils.windowControl.activeWindow

    @Test fun currentVerseChangedEmitsVerseChangedForThatWindow() {
        PassageChangeMediator.onCurrentVerseChanged(window)
        assertEquals(listOf<PageChange>(PageChange.VerseChanged(window)), received)
    }

    @Test fun currentPageChangedEmitsVerseChanged() {
        PassageChangeMediator.onCurrentPageChanged(window)
        val verseChanges = received.filterIsInstance<PageChange.VerseChanged>()
        assertEquals(listOf(PageChange.VerseChanged(window)), verseChanges)
    }

    @Test fun bibleVerseSelectedEmitsBibleVerseChanged() {
        PassageChangeMediator.onBibleVerseSelected()
        assertEquals(listOf<PageChange>(PageChange.BibleVerseChanged), received)
    }

    @Test fun contentChangeFinishedEmitsContentLoaded() {
        PassageChangeMediator.contentChangeFinished()
        assertEquals(listOf<PageChange>(PageChange.ContentLoaded), received)
    }

    @Test fun deliveryIsSynchronousOnTheEmittersThread() {
        val handlerThread = AtomicReference<Thread>()
        val sub = PassageChangeMediator.changes.subscribe { handlerThread.set(Thread.currentThread()) }
        try {
            val t = thread { PassageChangeMediator.onCurrentVerseChanged(window) }
            t.join()
            assertSame(t, handlerThread.get())
        } finally { sub.cancel() }
    }

    @Test fun selectingABibleVerseEmitsThroughTheMediator() {
        val page = CommonUtils.windowControl.activeWindowPageManager.currentBible
        val v11n = page.currentBibleVerse.versificationOfLastSelectedVerse
        page.currentBibleVerse.setVerseSelected(v11n, Verse(v11n, BibleBook.JOHN, 3, 16))
        assertTrue(PageChange.BibleVerseChanged in received)
    }
}
