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
import java.time.Duration
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * F102 probe (fix batch 1, spec §2.10): does the `openLink` extra a deep link launches the host with
 * actually end up showing the verse in some window?
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DeepLinkOpensVerseTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    @Test
    fun aDeepLinkOpenLinkExtraShowsTheVerseInSomeWindow() {
        firstTime = false
        val intent = NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING)
            .putExtra("openLink", "https://read.andbible.org/Ps.23.1?document=KJV")
        val controller = Robolectric.buildActivity(NavHostComposeActivity::class.java, intent).also { controllers += it }
            .apply { create().start().resume() }
        idleMain()
        val repo = controller.get().hostWindowRepository
        val shown = repo.sortedWindows.map { it.pageManager.currentPage.currentDocument?.initials to it.pageManager.currentPage.singleKey?.osisID }
        val activeShown = repo.activeWindow.let { it.pageManager.currentPage.currentDocument?.initials to it.pageManager.currentPage.singleKey?.osisID }
        println("F102 PROBE windows=$shown active=$activeShown activeIsShowing=${shown.any { it == activeShown }} links=${repo.sortedWindows.map { it.isLinksWindow }}")
        assertTrue(
            shown.any { (doc, key) -> doc == "KJV" && key?.startsWith("Ps.23.1") == true },
            "no window shows KJV Ps 23:1 after the deep link: $shown",
        )
    }
}
