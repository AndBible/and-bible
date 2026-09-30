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

package net.bible.sharedui.ai.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class QuickSetupEntryExitTest {
    private var left = 0

    @Test fun dismissingTheDisclaimerOfAQuickSetupEntryLeavesIt() {
        QuickSetupEntryExit(enteredForQuickSetup = true) { left++ }.disclaimerDismissed(wasQuickSetupStart = true)
        assertEquals(1, left)
    }

    @Test fun dismissingTheDisclaimerOfAnAddLeavesNothing() {
        // Inside a Quick Setup entry the user may also tap Add; that disclaimer's cancel stays put.
        QuickSetupEntryExit(enteredForQuickSetup = true) { left++ }.disclaimerDismissed(wasQuickSetupStart = false)
        assertEquals(0, left)
    }

    @Test fun closingTheWizardOfAQuickSetupEntryLeavesItAtAnyStep() {
        QuickSetupEntryExit(enteredForQuickSetup = true) { left++ }.wizardClosed()
        assertEquals(1, left)
    }

    @Test fun aPlainProvidersEntryNeverLeavesOnItsOwn() {
        val exit = QuickSetupEntryExit(enteredForQuickSetup = false) { left++ }
        exit.disclaimerDismissed(wasQuickSetupStart = true)
        exit.wizardClosed()
        assertEquals(0, left)
    }
}
