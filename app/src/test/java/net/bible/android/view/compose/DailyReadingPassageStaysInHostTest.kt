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

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
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
        net.bible.test.closeReadingHostsBeforeReset(controllers)
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
     * host-side (`loadReadingPlanDay`). Await its suspend read and actual navigation, not merely
     * the main looper: the DAO resumes from IO before the destination can be composed.
     */
    private fun openDay(controller: ActivityController<NavHostComposeActivity>) {
        controller.newIntent(
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.dailyReading(plan = PLAN, day = 5),
            ),
        )
        net.bible.test.awaitHostRoute(controller.get(), NavRoutes.dailyReading(plan = PLAN, day = 5))
        // The route alone is not enough on a host that STARTED on this day (it is already there): read() is a no-op
        // until the day's readings are loaded, and they load on IO after the route.
        val loaded = NavHostComposeActivity::class.java.getDeclaredField("loadedReadingDay")
            .run { isAccessible = true; get(controller.get()) as kotlinx.coroutines.flow.StateFlow<*> }
        net.bible.test.awaitHostCondition("day 5's readings must load") { loaded.value.toString().contains("day=5") }
        idleMain()
    }

    /** What `ReadingPlanControl.setReadingPlan` writes (its `READING_PLAN` pref key). */
    private fun selectAReadingPlan() = CommonUtils.settings.setString("reading_plan", PLAN)

    @Test
    fun passageFromAnInGraphDailyReadingReturnsToTheReadingViewAndKeepsTheHost() {
        selectAReadingPlan()
        val controller = host().apply { create().start().resume().visible() }
        val activity = controller.get()
        net.bible.test.awaitReadingGraph(activity)
        idleMain()
        openDay(controller)
        activity.dailyReadingControllerForTest().read(1)
        // read() acts after a suspending lifecycleScope job: await its outcome, or the checks below pass vacuously.
        net.bible.test.awaitHostCondition("Passage returns to the reading view") {
            activity.isFinishing || activity.currentRouteForTest()?.substringBefore('?') == NavRoutes.READING
        }
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
        net.bible.test.awaitHostRoute(activity, NavRoutes.dailyReading(plan = PLAN, day = 5))
        idleMain()
        openDay(controller)
        activity.dailyReadingControllerForTest().read(1)

        // read() marks the reading and moves the passage in a suspending lifecycleScope job; finish() comes after it.
        net.bible.test.awaitHostCondition("Passage from a start-destination day finishes the host") { activity.isFinishing }
    }


    @Test fun older_held_intent_cannot_publish_over_newer_day() {
        selectAReadingPlan()
        val controller = host().apply { create().start().resume().visible() }
        val activity = controller.get()
        net.bible.test.awaitReadingGraph(activity)
        val container = net.bible.service.db.DatabaseContainer.instance
        val original = container.readingPlanDb
        val real = original.readingPlanDao()
        val held = kotlinx.coroutines.CompletableDeferred<Unit>()
        val entered = java.util.concurrent.atomic.AtomicBoolean()
        val heldDao = object : net.bible.android.database.readingplan.ReadingPlanDao by real {
            override suspend fun getPlan(planCode: String): net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan? {
                if (entered.compareAndSet(false, true)) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { held.await() }
                }
                return real.getPlan(planCode)
            }
        }
        val db = com.nhaarman.mockitokotlin2.mock<net.bible.android.database.ReadingPlanDatabase>()
        com.nhaarman.mockitokotlin2.whenever(db.readingPlanDao()).thenReturn(heldDao)
        try {
            container.readingPlanDb = db
            val owner = activity.lifecycleScope.coroutineContext[Job]!!
            val priorJobs = owner.children.toSet()
            controller.newIntent(NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.dailyReading(PLAN, 5)))
            val oldJobs = owner.children.filter { it !in priorJobs }.toList()
            assertTrue(oldJobs.isNotEmpty(), "actual intent load must own a coroutine")
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!entered.get() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).runOneTask(); Thread.sleep(10) }
            assertTrue(entered.get(), "actual old intent must enter held DAO")
            controller.newIntent(NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.dailyReading(PLAN, 6)))
            net.bible.test.awaitHostRoute(activity, NavRoutes.dailyReading(PLAN, 6))
            idleMain()
            held.complete(Unit)
            // Drain IO resumption and the actual stale coroutine, not merely its initial main task.
            val end = System.nanoTime() + 10_000_000_000L
            while (oldJobs.any { !it.isCompleted } && System.nanoTime() < end) {
                shadowOf(Looper.getMainLooper()).runOneTask(); Thread.sleep(10)
            }
            assertTrue(oldJobs.all { it.isCompleted }, "held intent must finish before checking publication")
            val day = NavHostComposeActivity::class.java.getDeclaredField("dayLoaded").run { isAccessible = true; getInt(activity) }
            assertEquals(6, day, "old suspended intent replaced the current day")
            val loaded = NavHostComposeActivity::class.java.getDeclaredField("loadedReadingDay").run { isAccessible = true; get(activity) as kotlinx.coroutines.flow.StateFlow<*> }
            assertTrue(loaded.value.toString().contains("day=6"), "loaded publication is stale: ${loaded.value}")
        } finally {
            held.complete(Unit)
            controller.close()
            controllers.remove(controller)
            container.readingPlanDb = original
        }
    }

    private companion object {
        const val PLAN = "y1ot1nt1_OTandNT"
    }
}
