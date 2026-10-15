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
import net.bible.service.common.CommonUtils
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
import kotlin.test.assertTrue

/**
 * F93/F94 (fix batch 1, spec §2.1): classic `MainBibleActivity.onPause` saved the window repository;
 * the Compose host did not, so every reload from the DB (new host, process death, resume-reclaim)
 * restored whatever the last incidental save wrote.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostSaveOnPauseTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()
    private val rom81 = VerseFactory.fromString(Versifications.instance().getVersification("KJV"), "Rom.8.1")!!
    private val gen21 = VerseFactory.fromString(Versifications.instance().getVersification("KJV"), "Gen.2.1")!!

    @After
    fun tearDown() {
        net.bible.test.closeReadingHostsBeforeReset(controllers)
    }

    private fun host(route: String = NavRoutes.READING) = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
    ).also { controllers += it }

    private fun navigateActiveWindowToKjvRomans8(activity: NavHostComposeActivity) {
        val kjv = assertNotNull(SwordDocumentFacade.getDocumentByInitials("KJV"), "KJV test module missing (~/.sword)")
        activity.hostWindowRepository.activeWindow.pageManager.setCurrentDocumentAndKey(kjv, rom81)
    }

    private fun navigateActiveWindowToKjvGenesis2(activity: NavHostComposeActivity) {
        val kjv = assertNotNull(SwordDocumentFacade.getDocumentByInitials("KJV"), "KJV test module missing (~/.sword)")
        activity.hostWindowRepository.activeWindow.pageManager.setCurrentDocumentAndKey(kjv, gen21)
    }

    /** Two hosts resumed at once; B resumed last, so B owns the shared repository. */
    private fun twoResumedHosts(): Pair<ActivityController<NavHostComposeActivity>, ActivityController<NavHostComposeActivity>> {
        val controllerA = host().apply { create().start().resume() }
        val controllerB = host().apply { create().start().resume() }
        assertTrue(
            CommonUtils.windowControl.windowRepository === controllerB.get().hostWindowRepository,
            "precondition: the host resumed last owns the shared repository",
        )
        assertEquals(
            controllerA.get().hostWindowRepository.activeWindow.id,
            controllerB.get().hostWindowRepository.activeWindow.id,
            "precondition: both hosts show the same workspace's window",
        )
        return controllerA to controllerB
    }

    private fun savedBiblePage(activity: NavHostComposeActivity) =
        assertNotNull(
            runBlocking {
                // The save is an ordered write-through (Task 10): wait for it before reading the row.
                org.koin.java.KoinJavaComponent.get<net.bible.android.control.page.window.WorkspacePersistence>(
                    net.bible.android.control.page.window.WorkspacePersistence::class.java,
                ).awaitPersisted()
                DatabaseContainer.instance.workspaceDb.workspaceDao()
                    .pageManager(activity.hostWindowRepository.activeWindow.id)
            },
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
        val reloaded = WindowRepository(activity.lifecycleScope).apply { initializeAtEdge() }
        assertEquals("KJV", reloaded.activeWindow.pageManager.currentBible.currentDocument?.initials)
    }

    @Test
    fun aHostPausedOnAnotherDestinationStillSavesItsRepository() {
        val controller = host().apply { create().start().resume().visible() }
        val activity = controller.get()
        navigateActiveWindowToKjvRomans8(activity)
        net.bible.test.awaitReadingGraph(activity) { compose.waitForIdle() }
        activity.navigateInGraph(NavRoutes.SETTINGS)
        net.bible.test.awaitHostRoute(activity, NavRoutes.SETTINGS) { compose.waitForIdle() }
        assertEquals(NavRoutes.SETTINGS, activity.currentRouteForTest()?.substringBefore('?'))

        controller.pause()

        assertEquals("KJV", savedBiblePage(activity).document)
    }

    /**
     * CR-1, the review's scenario: A's edit is OLDER than B's. A has to lose top-resumed when B gains
     * it, so A's edit is saved at that moment, and B's later save wins. A pausing at the end must not
     * write A's copy over B's row.
     */
    @Test
    fun anOlderEditInAHostThatLostFocusDoesNotOverwriteANewerOneOnPause() {
        val controllerA = host().apply { create().start().resume() }
        controllerA.topActivityResumed(true)
        navigateActiveWindowToKjvGenesis2(controllerA.get())
        controllerA.topActivityResumed(false)       // the user moves to the second host

        val controllerB = host().apply { create().start().resume() }
        controllerB.topActivityResumed(true)
        navigateActiveWindowToKjvRomans8(controllerB.get())
        controllerB.pause()

        controllerA.pause()

        assertEquals(
            rom81, savedBiblePage(controllerB.get()).verse.jswordVerse,
            "a host that no longer owns the repository wrote its older page over the newer one -- CR-1",
        )
    }

    /**
     * CR-1, split-screen: the user goes back to A, which is already resumed, so only top-resumed
     * changes. A's edit is now the newest and must be the one that survives both hosts pausing.
     */
    @Test
    fun theEditInTheHostUsedLastSurvivesBothHostsPausing() {
        val (controllerA, controllerB) = twoResumedHosts()
        controllerB.topActivityResumed(true)
        navigateActiveWindowToKjvRomans8(controllerB.get())

        controllerB.topActivityResumed(false)       // the user taps the other half of the screen
        controllerA.topActivityResumed(true)
        assertTrue(
            CommonUtils.windowControl.windowRepository === controllerA.get().hostWindowRepository,
            "gaining top-resumed must hand the shared repository to this host",
        )
        assertEquals(
            rom81, savedBiblePage(controllerB.get()).verse.jswordVerse,
            "losing top-resumed must save the host's edits while it still owns the repository",
        )
        navigateActiveWindowToKjvGenesis2(controllerA.get())

        controllerA.pause()
        controllerB.pause()

        assertEquals(
            gen21, savedBiblePage(controllerA.get()).verse.jswordVerse,
            "the edit made last, in the host used last, was lost or overwritten -- CR-1",
        )
    }

    /** Gaining top-resumed hands over ownership WITHOUT reloading: the windows A shows do not jump. */
    @Test
    fun gainingFocusDoesNotReloadTheHostsWindowsFromTheDb() {
        val (controllerA, controllerB) = twoResumedHosts()
        val aVerseBefore = controllerA.get().hostWindowRepository.activeWindow.pageManager.currentBible.singleKey
        navigateActiveWindowToKjvRomans8(controllerB.get())
        controllerB.topActivityResumed(false)       // B saves Rom 8:1 to the DB

        controllerA.topActivityResumed(true)

        assertEquals(
            aVerseBefore, controllerA.get().hostWindowRepository.activeWindow.pageManager.currentBible.singleKey,
            "gaining top-resumed reloaded the host from the DB; its window jumped to the other host's position",
        )
    }

    /**
     * CR-1 review I1: B takes the repository BEFORE A receives top-resumed(false). A's focus-loss
     * save is then skipped (A no longer owns), so the host taking ownership must flush A itself --
     * otherwise B loads a DB lacking A's edit and A later writes it over B's newer row.
     */
    @Test
    fun takingOwnershipFlushesThePreviousOwnerWhateverTheCallbackOrder() {
        val controllerA = host().apply { create().start().resume() }
        controllerA.topActivityResumed(true)
        navigateActiveWindowToKjvGenesis2(controllerA.get())

        val controllerB = host().apply { create().start().resume() }   // before A loses focus
        assertEquals(
            gen21, controllerB.get().hostWindowRepository.activeWindow.pageManager.currentBible.singleKey,
            "B loaded a DB lacking A's last edit -- the new owner did not flush the previous one (CR-1 I1)",
        )
        controllerA.topActivityResumed(false)
        controllerB.topActivityResumed(true)
        navigateActiveWindowToKjvRomans8(controllerB.get())
        controllerB.topActivityResumed(false)

        controllerA.topActivityResumed(true)
        controllerA.topActivityResumed(false)
        controllerA.pause()
        controllerB.pause()

        assertEquals(
            rom81, savedBiblePage(controllerB.get()).verse.jswordVerse,
            "A wrote its older page over B's newer one after the reversed callback order -- CR-1 I1",
        )
    }
}
