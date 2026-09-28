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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F99 (fix batch 1, spec §2.3): `BibleView.assignLabels` awaited an intent from an IO coroutine;
 * since F53 a self-launch is a `NavController.navigate`, which asserts the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AwaitIntentOffMainThreadTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    /** `firstTime` pinned false: see DailyReadingPassageStaysInHostTest.host. */
    private fun host(): ActivityController<NavHostComposeActivity> {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun anAwaitIntentFromAnIoCoroutineNavigatesOnTheMainThreadInsteadOfCrashing() {
        val controller = host().apply { create().start().resume().visible() }
        val activity = controller.get()
        idleMain()
        val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.ASSIGN).toJSON()
        val intent = NavHostComposeActivity.intentFor(activity, NavRoutes.manageLabels(data))
        val failure = AtomicReference<Throwable?>(null)

        val job = CoroutineScope(Dispatchers.IO).launch {
            try { activity.awaitIntent(intent) } catch (t: Throwable) { if (t !is CancellationException) failure.set(t) }
        }
        // The IO coroutine hops to Main; drain the main looper until the navigate lands (bounded).
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline &&
            activity.currentRouteForTest()?.startsWith("bookmarks/manageLabels") != true &&
            failure.get() == null) {
            idleMain(); Thread.sleep(10)
        }
        job.cancel()

        assertNull(failure.get(), "awaitIntent threw off the main thread -- F99: ${failure.get()}")
        assertTrue(activity.currentRouteForTest()?.startsWith("bookmarks/manageLabels") == true)
    }
}
