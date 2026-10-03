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

package net.bible.android.view.activity.nav

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Slice 8 C2 (spec §3.1 rule 2): the start route decides whether the app is initialised. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class NavHostRouteInitTest {

    @Test
    fun onlyWelcomeAndBackupStartUninitialised() {
        assertTrue(routeStartsUninitialised(NavRoutes.WELCOME))
        assertTrue(routeStartsUninitialised(NavRoutes.BACKUP))
        assertFalse(routeStartsUninitialised(NavRoutes.installZip()))
        assertFalse(routeStartsUninitialised(NavRoutes.installZip("android.intent.action.VIEW", listOf("content://x/y.zip"))))
        assertFalse(routeStartsUninitialised(NavRoutes.READING))
        assertFalse(routeStartsUninitialised(NavRoutes.download(firstDownload = true)))
        assertFalse("no route defaults to reading, which initialises", routeStartsUninitialised(null))
    }

    /** Read BEFORE `onCreate` runs, which is when `ActivityBase.onCreate` consults it (`:92`). */
    @Test
    fun theHostAnswersFromItsLaunchIntentBeforeOnCreate() {
        fun hostFor(route: String) = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
        ).get()
        assertTrue(hostFor(NavRoutes.BACKUP).doNotInitializeApp)
        assertTrue(hostFor(NavRoutes.WELCOME).doNotInitializeApp)
        assertFalse(hostFor(NavRoutes.installZip()).doNotInitializeApp)
        assertFalse(hostFor(NavRoutes.READING).doNotInitializeApp)
    }

    /** After a recreate the SAVED start route wins, as it does for the graph (navHostStartRoute). */
    @Test
    fun aRestoredStartRouteWinsOverTheIntent() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.BACKUP),
        )
        try {
            val saved = Bundle().apply { putString(STATE_START_ROUTE, NavRoutes.AI_TOOL_INFO) }
            val activity = controller.create(saved).get()
            assertFalse(activity.doNotInitializeApp)
        } finally {
            controller.close()
        }
    }
}
