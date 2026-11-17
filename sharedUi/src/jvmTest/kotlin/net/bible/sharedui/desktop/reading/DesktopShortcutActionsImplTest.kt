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
package net.bible.sharedui.desktop.reading

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopShortcutActionsImplTest {
    private val log = mutableListOf<String>()
    private var overlay = false
    private var modal = false
    private var history = true
    private var maximisedMemorize = false

    private val actions = DesktopShortcutActionsImpl(
        dispatchBack = { log += "dispatch"; overlay },
        canGoBackInHistory = { history },
        goBackInHistory = { log += "history" },
        closeModals = { log += "modals:${it ?: "active"}"; modal },
        unMaximiseMemorize = { if (maximisedMemorize) log += "unmaximise"; maximisedMemorize },
    )

    @Test fun anOverlayOwnsBackBeforeThePageModalsAndHistory() {
        overlay = true
        actions.navigateBack("w1")
        assertEquals(listOf("dispatch"), log)
    }

    @Test fun anOpenPageModalClosesInsteadOfGoingBackInHistory() {
        modal = true
        actions.navigateBack("w1")
        assertEquals(listOf("dispatch", "modals:w1"), log)
    }

    @Test fun withNothingOpenBackGoesBackInHistoryWhenItCan() {
        actions.navigateBack(null)
        history = false
        actions.navigateBack(null)
        assertEquals(listOf("dispatch", "modals:active", "history", "dispatch", "modals:active"), log)
    }

    /** #4118: a maximised Memorize window un-maximises; stepping its history would swap it back to the Bible. */
    @Test fun backInAMaximisedMemorizeWindowUnMaximisesInsteadOfGoingBackInHistory() {
        maximisedMemorize = true
        actions.navigateBack("w1")
        actions.back(historyFallback = true)
        assertEquals(listOf("dispatch", "modals:w1", "unmaximise", "dispatch", "unmaximise"), log)
    }

    @Test fun anOpenPageModalClosesBeforeTheMaximisedMemorizeWindowUnMaximises() {
        modal = true
        maximisedMemorize = true
        actions.navigateBack("w1")
        assertEquals(listOf("dispatch", "modals:w1"), log)
    }
}
