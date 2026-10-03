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

import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading-host re-typing T8b, steps 2 and 4: which route the nav host's graph starts on, and what
 * happens when nobody supplied one.
 *
 * These three cases are the whole of the batch spec's "dormant `awaitIntent`-to-self" concern that
 * T8b could actually demonstrate: `onNewIntent` writes the CHILD route onto the host's own intent,
 * and before this the next `onCreate` read it back. See [navHostStartRoute]'s kdoc.
 */
class NavHostStartRouteTest {

    @Test fun aSavedStartRouteBeatsTheIntentSoOnNewIntentCannotChangeWhatARecreateStartsOn() {
        var defaulted = false
        val route = navHostStartRoute(
            savedStartRoute = NavRoutes.READING,
            intentRoute = NavRoutes.download(),
        ) { defaulted = true }
        assertEquals(
            "a recreate must come back on the route the host was CREATED with, not on whatever " +
                "child route onNewIntent last setIntent()ed",
            NavRoutes.READING,
            route,
        )
        assertFalse("nothing was missing, so the loud default must not have been taken", defaulted)
    }

    @Test fun theIntentRouteIsUsedOnAFirstCreateWhenThereIsNoSavedState() {
        var defaulted = false
        val route = navHostStartRoute(
            savedStartRoute = null,
            intentRoute = NavRoutes.download(),
        ) { defaulted = true }
        assertEquals(NavRoutes.download(), route)
        assertFalse(defaulted)
    }

    @Test fun aHostStartedWithNoRouteAtAllFallsBackToReadingAndSaysSo() {
        var defaulted = false
        val route = navHostStartRoute(savedStartRoute = null, intentRoute = null) { defaulted = true }
        assertEquals(
            "the synthesised parent Intent an Up affordance builds carries no extras, and this " +
                "host is now the app's main screen",
            NavRoutes.READING,
            route,
        )
        assertTrue(
            "the fallback must be LOUD — a silent one would hide a caller that forgot intentFor()",
            defaulted,
        )
    }
}
