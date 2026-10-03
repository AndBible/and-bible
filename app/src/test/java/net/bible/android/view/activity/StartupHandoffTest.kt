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

package net.bible.android.view.activity

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Slice 8 E4 (spec §4 gate a): the boot handoff's two decisions, as plain functions. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class StartupHandoffTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun theStartRouteIsReadingOnlyWithAUsableBible() {
        assertEquals(NavRoutes.READING, startRouteForBoot(usable = true))
        assertEquals(NavRoutes.WELCOME, startRouteForBoot(usable = false))
    }

    /** Review Focus 2: a deep link on a fresh install must survive the Welcome. */
    @Test
    fun aDeepLinkCarriesOpenLinkAndANewTaskToEitherRoute() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://read.andbible.org/Gen.1.1"))
        for (route in listOf(NavRoutes.READING, NavRoutes.WELCOME)) {
            val handoff = bootHandoffIntent(context, link, route)
            assertEquals(NavHostComposeActivity::class.java.name, handoff.component?.className)
            assertEquals(route, handoff.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
            assertEquals("https://read.andbible.org/Gen.1.1", handoff.getStringExtra("openLink"))
            assertEquals(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
                handoff.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
            )
        }
    }

    @Test
    fun aLauncherStartReusesTheTaskAndCarriesNoLink() {
        val handoff = bootHandoffIntent(context, Intent(Intent.ACTION_MAIN), NavRoutes.WELCOME)
        assertNull(handoff.getStringExtra("openLink"))
        assertEquals(
            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            handoff.flags and (Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        assertFalse(handoff.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK != 0)
    }
}
