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
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F94 (fix batch 1, spec §2.2): daily reading's Passage / Reset were classic
 * `DailyReadingComposeActivity`'s `finish()`. Pushed above `reading` in this host, that closes the
 * reading view's own host.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DailyReadingPassageStaysInHostTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    /**
     * `firstTime` is pinned false: it is a file-level `var` in `ActivityBase.kt` that Robolectric does
     * not reset between test methods, and `fixNightMode()` arms a delayed `recreate()` while it is
     * true -- which destroys the composition (and `navController`) mid-test.
     */
    private fun host(route: String = NavRoutes.READING): ActivityController<NavHostComposeActivity> {
        // The daily reading controller is built by the graph's first frames; an earlier host test in the
        // same JVM can leave Compose's static UI dispatcher stuck, so they never run (fix wave, finding B).
        resetComposeUiDispatcher()
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
        ).also { controllers += it }
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * The host's own `onNewIntent` for a daily-reading route: navigates onto the day AND loads it
     * host-side (`loadReadingPlanDay`), which the destination's LaunchedEffect does not do under a
     * paused Robolectric looper.
     */
    private fun openDay(controller: ActivityController<NavHostComposeActivity>) {
        controller.newIntent(
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.dailyReading(plan = PLAN, day = 5),
            ),
        )
        idleMain()
    }

    /** What `ReadingPlanControl.setReadingPlan` writes (its `READING_PLAN` pref key). */
    private fun selectAReadingPlan() = CommonUtils.settings.setString("reading_plan", PLAN)

    @Test
    fun passageFromAnInGraphDailyReadingReturnsToTheReadingViewAndKeepsTheHost() {
        selectAReadingPlan()
        val controller = host().apply { create().start().resume().visible() }
        val activity = controller.get()
        idleMain()
        openDay(controller)
        activity.dailyReadingControllerForTest().read(1)
        idleMain()
        assertFalse(activity.isFinishing, "Passage finished the host that owns the reading view -- F94's launcher")
        assertEquals(NavRoutes.READING, activity.currentRouteForTest()?.substringBefore('?'))
    }

    /** Review Focus 2: with no `reading` beneath (started on the day), finishing is still right. */
    @Test
    fun passageFromADailyReadingThatIsTheStartDestinationStillFinishes() {
        selectAReadingPlan()
        val controller = host(NavRoutes.dailyReading(plan = PLAN, day = 5)).apply { create().start().resume().visible() }
        val activity = controller.get()
        idleMain()
        openDay(controller)
        activity.dailyReadingControllerForTest().read(1)

        assertTrue(activity.isFinishing)
    }

    private companion object {
        const val PLAN = "y1ot1nt1_OTandNT"
    }
}
