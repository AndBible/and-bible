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
package net.bible.android.view.activity.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM unit test (no Robolectric/Compose needed — [shouldCloseSearchOnBack] is pure Kotlin)
 * for the Task 7 review Finding 1 fix.
 *
 * The bug it pins: [TextDisplaySettingsComposeActivity] hoists `searchMode` to ACTIVITY level (so
 * hardware back can reach it at all — :sharedUi has commonMain only, no BackHandler there). That
 * hoisting means, unlike the old `remember`-in-composable design (disposed the instant the
 * composition navigated away), `searchMode.active` now OUTLIVES navigating into the Colors/
 * background-image-chooser sub-destinations, which render no search UI. An unconditional
 * `if (searchMode.active.value) searchMode.close() else pop()` back-press check would therefore
 * fire `close()` on an invisible search bar from inside Colors and swallow the press — `pop()`
 * never runs, so the user appears stuck and needs two back presses. [atListDestinationFalse]/
 * [searchActiveTrue] below is exactly that reachable state.
 */
class TextDisplaySettingsComposeActivityBackTest {

    @Test fun searchActiveAtListDestinationClosesSearch() {
        assertTrue(shouldCloseSearchOnBack(atListDestination = true, searchActive = true))
    }

    @Test fun searchInactiveAtListDestinationDoesNotCloseSearch() {
        // Ordinary back press at the list with no search open: falls through to pop()/finish().
        assertFalse(shouldCloseSearchOnBack(atListDestination = true, searchActive = false))
    }

    /** The regression this fix closes: search left active while the user is inside Colors/chooser
     *  must NOT be closed by a back press there — that back press must pop the sub-destination
     *  instead. Before the fix, `shouldCloseSearchOnBack` did not exist and the equivalent
     *  expression was just `searchActive`, which returns `true` here — the wrong answer. */
    @Test fun searchActiveAtListDestinationFalseDoesNotCloseSearch() {
        assertFalse(shouldCloseSearchOnBack(atListDestination = false, searchActive = true))
    }

    @Test fun searchInactiveAtListDestinationFalseDoesNotCloseSearch() {
        assertFalse(shouldCloseSearchOnBack(atListDestination = false, searchActive = false))
    }
}
