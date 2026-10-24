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
package net.bible.sharedlogic.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class NightModeResolverTest {
    @Test fun systemModeFollowsTheSystemAndIgnoresTheManualBoolean() {
        for (manual in listOf(true, false)) for (system in listOf(true, false)) for (auto in listOf(null, true, false)) {
            assertEquals(system, resolveNightMode("system", manual, system, auto), "manual=$manual system=$system auto=$auto")
        }
    }

    @Test fun manualModeReadsTheBooleanAndIgnoresTheRest() {
        for (manual in listOf(true, false)) for (system in listOf(true, false)) for (auto in listOf(null, true, false)) {
            assertEquals(manual, resolveNightMode("manual", manual, system, auto), "system=$system auto=$auto")
        }
    }

    @Test fun automaticUsesTheReadingWhenThereIsOne() {
        assertEquals(true, resolveNightMode("automatic", false, false, true))
        assertEquals(false, resolveNightMode("automatic", true, true, false))
    }

    @Test fun automaticWithoutAReadingFallsBackToTheSystem() {
        assertEquals(true, resolveNightMode("automatic", false, true, null))
        assertEquals(false, resolveNightMode("automatic", true, false, null))
    }

    @Test fun anUnknownModeIsTreatedAsManualLikeAndroidsElseBranch() {
        assertEquals(true, resolveNightMode("dark", true, false, null))
        assertEquals(false, resolveNightMode("", false, true, true))
    }
}
