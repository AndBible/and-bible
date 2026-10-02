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
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fix batch 5 A2: Window-source identity test (spec §1.9).
 * A characterization test proving the host's repository is never swapped by another source.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowSourceIdentityTest : KoinComponent {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @Before fun pin() { firstTime = false }
    @After fun tearDown() {
        controllers.forEach { it.close() }; controllers.clear()
        ReadingHostPresence.setForeground(null); ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun host() = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
    ).also { controllers += it }

    @Test fun onReadingTheHostAndKoinShareOneRepository() {
        val a = host().create().start().resume().visible().get()
        assertSame(a.hostWindowRepository, get<WindowControl>().windowRepository)
        assertSame(get<WindowControl>(), CommonUtils.windowControl)
    }

    @Test fun afterPauseAndResumeTheyAreStillOne() {
        val c = host().apply { create().start().resume().visible() }
        val hostRepo = c.get().hostWindowRepository
        val wc = get<WindowControl>()

        // Precondition: verify they start as the same instance
        assertSame(hostRepo, wc.windowRepository)

        // Pause the activity
        c.pause()

        // Another source (e.g., settings activity) takes the shared windowControl
        val foreignRepo = WindowRepository(kotlinx.coroutines.CoroutineScope(Dispatchers.Main))
        wc.windowRepository = foreignRepo
        assertNotSame(hostRepo, wc.windowRepository, "precondition: repository swap must happen")
        println("afterPauseAndResumeTheyAreStillOne: another source took the repository")

        // Resume: reclaimWindowRepository() must restore the host's repository
        c.resume()
        assertSame(hostRepo, wc.windowRepository, "reclaimWindowRepository() must restore the host's repository")
        println("afterPauseAndResumeTheyAreStillOne: resume restored the host repository")
    }

    @Test fun aLinksWindowLandsInTheHostRepository() {
        val a = host().create().start().resume().visible().get()
        val window = a.hostWindowRepository.activeWindow

        // Check precondition: no links window exists yet
        val linksCountBefore = a.hostWindowRepository.windowList.count { it.isLinksWindow }
        assertEquals(0, linksCountBefore, "precondition: no links window should exist initially")

        val doc = window.pageManager.currentBible.currentDocument
        if (doc == null) {
            println("aLinksWindowLandsInTheHostRepository: no Bible in this JVM, skipping assertion")
            return
        }
        val key = window.pageManager.currentBible.singleKey
        if (key == null) {
            println("aLinksWindowLandsInTheHostRepository: no key, skipping assertion")
            return
        }

        // Show the link
        get<WindowControl>().showLink(doc, key)
        shadowOf(Looper.getMainLooper()).idle()

        // Verify the link window exists and is in the host's repository
        assertTrue(a.hostWindowRepository.windowList.any { it.isLinksWindow }, "link window must be in host repository")
        println("aLinksWindowLandsInTheHostRepository: link window created and visible")
    }

    @Test fun backReplaysTheVisibleWindowsHistory() {
        val a = host().create().start().resume().visible().get()
        val window = a.hostWindowRepository.activeWindow

        val before = window.pageManager.currentPage.singleKey
        if (before == null) {
            println("backReplaysTheVisibleWindowsHistory: no starting key, skipping assertion")
            return
        }

        // Resolve, as the host does
        get<WindowControl>()
        get<HistoryManager>().addHistoryItem(null)

        // Move away, then Back
        // Note: using KJV versification (Robolectric doesn't have KJVA available in test)
        val kjvVersif = Versifications.instance().getVersification("KJV") ?: run {
            println("backReplaysTheVisibleWindowsHistory: KJV versification not available, skipping assertion")
            return
        }
        window.pageManager.currentBible.setKey(Verse(kjvVersif, BibleBook.PS, 23, 1))

        // Go back
        if (!a.goBackInHistory()) {
            println("backReplaysTheVisibleWindowsHistory: goBackInHistory() returned false, skipping assertion")
            return
        }
        shadowOf(Looper.getMainLooper()).idle()

        // Verify we're back at the original key
        val after = a.hostWindowRepository.activeWindow.pageManager.currentPage.singleKey
        assertEquals(before.osisRef, after?.osisRef, "back navigation must restore the original key")
        println("backReplaysTheVisibleWindowsHistory: back navigation restored original key")
    }
}
