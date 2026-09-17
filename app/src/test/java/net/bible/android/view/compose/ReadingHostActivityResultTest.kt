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

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Reading-host re-typing T8b: a `STD_REQUEST_CODE` chooser result reaches the reading host, and
 * reaches it AFTER `onResume` rather than inside `onActivityResult`.
 *
 * Every chooser the reading view opens that is a separate Activity rather than a nav-graph
 * destination still answers this way — the dictionary chooser always (`KeyChooserRoute.sheetFor`
 * returns null for it), the full `ChooseDocument` screen from the document sheet's footer, the
 * passage grid, the map and general-book key lists. Classic `MainBibleActivity.onActivityResult` was
 * the tree's only reader of that request code, so with this host as the launcher and no override
 * here every one of them was discarded in silence.
 *
 * The deferral is not a detail: `onActivityResult` runs before `onResume`, i.e. before
 * `reclaimWindowRepository()`, before `ReadingHostPresence.setForeground(this)` (which is what makes
 * `ReadingViewVisibility.isVisible` true for the `AddHistoryItem` a `setKey` posts) and before the
 * bootstrap bridge is re-armed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostActivityResultTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun readingHost(): ActivityController<NavHostComposeActivity> =
        Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.READING,
            ),
        ).also { controllers += it }

    private fun passageGridResult(osisRef: String) = Intent()
        .putExtra(ActivityResultKind.EXTRA, ActivityResultKind.PassageGrid.name)
        .putExtra("verse", osisRef)

    private val NavHostComposeActivity.activeKey: String?
        get() = hostWindowRepository.activeWindow.pageManager.currentPage.key?.osisRef

    @Test
    fun aChooserResultIsHeldUntilTheHostHasResumedAndIsThenApplied() {
        val controller = readingHost().apply { create().start().resume() }
        val activity = controller.get()
        val before = activity.activeKey
        assertNotEquals(
            CHOSEN_CHAPTER,
            before,
            "the fixture must not already be on the verse this test chooses, or it proves nothing",
        )

        controller.pause()
        activity.onActivityResult(ActivityBase.STD_REQUEST_CODE, Activity.RESULT_OK, passageGridResult(CHOSEN))
        assertEquals(
            before,
            activity.activeKey,
            "the result must be HELD: onActivityResult runs before onResume has reclaimed the " +
                "window repository or declared this host foreground",
        )

        controller.resume()
        assertEquals(
            CHOSEN_CHAPTER,
            activity.activeKey,
            "…and applied once onResume has reconciled this host's reading state",
        )
    }

    /** A request code this host did not issue must fall straight through, untouched. */
    @Test
    fun aResultForSomeOtherRequestCodeIsNotClaimed() {
        val controller = readingHost().apply { create().start().resume() }
        val activity = controller.get()
        val before = activity.activeKey

        controller.pause()
        activity.onActivityResult(ActivityBase.STD_REQUEST_CODE + 1, Activity.RESULT_OK, passageGridResult(CHOSEN))
        controller.resume()

        assertEquals(before, activity.activeKey)
    }

    companion object {
        /** What the passage grid hands back: a single verse. */
        private const val CHOSEN = "Ps.23.1"

        /** What the Bible page's key becomes once that verse is applied — its CHAPTER. */
        private const val CHOSEN_CHAPTER = "Ps.23"
    }
}
