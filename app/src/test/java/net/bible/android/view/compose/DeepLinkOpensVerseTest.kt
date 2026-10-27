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

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.bootHandoffIntent
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.resetComposeUiDispatcher
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
import kotlin.test.assertTrue

/** External VIEW intents must navigate the existing active pane on both cold and warm starts (#3210). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DeepLinkOpensVerseTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        net.bible.test.closeReadingHostsBeforeReset(controllers)
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    private fun viewHandoff(url: String = "https://read.andbible.org/1Sam.1.2") = bootHandoffIntent(
        ApplicationProvider.getApplicationContext(),
        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
        NavRoutes.READING,
    )

    private fun readingHost(intent: Intent): ActivityController<NavHostComposeActivity> {
        resetComposeUiDispatcher()
        firstTime = false
        return Robolectric.buildActivity(NavHostComposeActivity::class.java, intent)
            .also { controllers += it }.apply { create().start().resume() }
    }

    private fun assertLinkedPassage(controller: ActivityController<NavHostComposeActivity>) {
        val repo = controller.get().hostWindowRepository
        assertEquals("1Sam.1.2", repo.activeWindow.pageManager.currentPage.singleKey?.getOsisID())
        assertEquals(1, repo.sortedWindows.size, "external links must not create a links window")
    }

    @Test
    fun aColdViewIntentOpensThePassageWithoutAddingAWindow() {
        val controller = readingHost(viewHandoff())
        idleMain()
        assertLinkedPassage(controller)
        controller.get().hostWindowRepository.activeWindow.pageManager.currentBible.setKey("Gen.2.3")
        controller.pause().resume()
        idleMain()
        assertEquals("Gen.2.3", controller.get().hostWindowRepository.activeWindow.pageManager.currentPage.singleKey?.getOsisID(),
            "resuming must not replay a consumed deep link")
    }

    @Test
    fun aWarmViewIntentOpensThePassageWithoutAddingAWindow() {
        val controller = readingHost(NavHostComposeActivity.intentFor(
            ApplicationProvider.getApplicationContext(), NavRoutes.READING))
        idleMain()
        val repo = controller.get().hostWindowRepository
        assertEquals(1, repo.sortedWindows.size)
        repo.activeWindow.pageManager.currentBible.setKey("Gen.1.1")
        assertEquals("Gen.1.1", repo.activeWindow.pageManager.currentPage.singleKey?.getOsisID())
        controller.pause().newIntent(viewHandoff()).resume()
        idleMain()
        assertLinkedPassage(controller)
    }

    @Test
    fun aDeepLinkWithAnExplicitDocumentShowsTheVerseInTheActiveWindow() {
        firstTime = false
        val intent = NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING)
            .putExtra("openLink", "https://read.andbible.org/Ps.23.1?document=KJV")
        val controller = Robolectric.buildActivity(NavHostComposeActivity::class.java, intent).also { controllers += it }
            .apply { create().start().resume() }
        idleMain()
        val repo = controller.get().hostWindowRepository
        val active = repo.activeWindow
        assertEquals("KJV", active.pageManager.currentPage.currentDocument?.initials)
        assertEquals("Ps.23.1", active.pageManager.currentPage.singleKey?.getOsisID())
        assertEquals(1, repo.sortedWindows.size)
        assertTrue(active.isVisible && !active.isMinimised)
    }
}
