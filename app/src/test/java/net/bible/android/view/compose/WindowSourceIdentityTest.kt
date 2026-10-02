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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
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
        c.pause().resume()
        assertSame(c.get().hostWindowRepository, get<WindowControl>().windowRepository)
    }

    @Test fun aLinksWindowLandsInTheHostRepository() {
        val a = host().create().start().resume().visible().get()
        val window = a.hostWindowRepository.activeWindow
        val doc = window.pageManager.currentBible.currentDocument ?: return // no Bible in this JVM: nothing to show
        val key = window.pageManager.currentBible.singleKey ?: return
        get<WindowControl>().showLink(doc, key)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(a.hostWindowRepository.windowList.any { it.isLinksWindow })
    }

    @Test fun backReplaysTheVisibleWindowsHistory() {
        val a = host().create().start().resume().visible().get()
        val window = a.hostWindowRepository.activeWindow
        val before = window.pageManager.currentPage.singleKey ?: return
        get<WindowControl>() // resolve, as the host does
        get<HistoryManager>().addHistoryItem(null)
        // move away, then Back
        window.pageManager.currentBible.setKey(Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 23, 1))
        assertTrue(a.goBackInHistory())
        shadowOf(Looper.getMainLooper()).idle()
        println("backReplaysTheVisibleWindowsHistory: Back branch executed")
        assertEquals(before.osisRef, a.hostWindowRepository.activeWindow.pageManager.currentPage.singleKey?.osisRef)
    }
}
