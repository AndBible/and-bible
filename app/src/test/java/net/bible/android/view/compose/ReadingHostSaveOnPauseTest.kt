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

import kotlinx.coroutines.runBlocking
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * F93/F94 (fix batch 1, spec §2.1): classic `MainBibleActivity.onPause` saved the window repository;
 * the Compose host did not, so every reload from the DB (new host, process death, resume-reclaim)
 * restored whatever the last incidental save wrote.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostSaveOnPauseTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private val rom81 = VerseFactory.fromString(Versifications.instance().getVersification("KJV"), "Rom.8.1")!!

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun host(route: String = NavRoutes.READING) = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
    ).also { controllers += it }

    private fun navigateActiveWindowToKjvRomans8(activity: NavHostComposeActivity) {
        val kjv = assertNotNull(SwordDocumentFacade.getDocumentByInitials("KJV"), "KJV test module missing (~/.sword)")
        activity.hostWindowRepository.activeWindow.pageManager.setCurrentDocumentAndKey(kjv, rom81)
    }

    private fun savedBiblePage(activity: NavHostComposeActivity) =
        assertNotNull(
            runBlocking { DatabaseContainer.instance.workspaceDb.workspaceDao()
                .pageManager(activity.hostWindowRepository.activeWindow.id) },
        ).biblePage

    @Test
    fun pausingTheReadingHostWritesTheActiveWindowsBiblePageToTheDb() {
        val controller = host().apply { create().start().resume() }
        val activity = controller.get()
        navigateActiveWindowToKjvRomans8(activity)

        controller.pause()

        val saved = savedBiblePage(activity)
        assertEquals("KJV", saved.document, "the document switch never reached the DB -- F93")
        assertEquals(rom81, saved.verse.jswordVerse, "the verse navigation never reached the DB -- F93")

        // ...and a repository reloaded from the DB (what a new host / process death does) sees it --
        // built exactly as ReadingAppBootstrap.createWindowRepository() builds one.
        val reloaded = WindowRepository(activity.lifecycleScope).apply { initialize() }
        assertEquals("KJV", reloaded.activeWindow.pageManager.currentBible.currentDocument?.initials)
    }

    @Test
    fun aHostPausedOnAnotherDestinationStillSavesItsRepository() {
        val controller = host().apply { create().start().resume() }
        val activity = controller.get()
        navigateActiveWindowToKjvRomans8(activity)
        activity.navigateInGraph(NavRoutes.SETTINGS)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        controller.pause()

        assertEquals("KJV", savedBiblePage(activity).document)
    }
}
