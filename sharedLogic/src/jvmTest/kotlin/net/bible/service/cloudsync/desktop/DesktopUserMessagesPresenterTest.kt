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
package net.bible.service.cloudsync.desktop

import net.bible.android.control.event.UserMessages
import net.bible.sharedcore.platform.UserNotifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopUserMessagesPresenterTest {
    private class Recorder : UserNotifier {
        val calls = mutableListOf<String>()
        override fun showInfo(message: String, long: Boolean) { calls += "info:$message:$long" }
        override fun showError(message: String, cause: Throwable?) { calls += "error:$message" }
    }

    private fun <T> presenting(block: (Recorder) -> T): T {
        val recorder = Recorder()
        val presenter = DesktopUserMessagesPresenter({ recorder })
        try { return block(recorder) } finally { presenter.close() }
    }

    @Test fun toastGoesToShowInfo() = presenting { r ->
        UserMessages.toast("hello", true)
        UserMessages.toast("short")
        assertEquals(listOf("info:hello:true", "info:short:false"), r.calls)
    }

    @Test fun errorNotificationGoesToShowError() = presenting { r ->
        UserMessages.errorNotification("broken")
        assertEquals(listOf("error:broken"), r.calls)
    }

    @Test fun messagesWithOnlyAResourceIdAreDropped() = presenting { r ->
        UserMessages.toast(12345)
        UserMessages.errorNotification(12345)
        assertTrue(r.calls.isEmpty())
    }

    /** The message CloudSync raises when a category is disabled for an incompatible patch version. */
    @Test fun theIncompatiblePatchVersionMessageReachesTheNotifier() = presenting { r ->
        UserMessages.errorNotification("Sync disabled: incompatible version", showReportButton = false)
        assertEquals(listOf("error:Sync disabled: incompatible version"), r.calls)
    }

    @Test fun closeStopsPresenting() {
        val r = Recorder()
        DesktopUserMessagesPresenter({ r }).close()
        UserMessages.toast("after close")
        assertTrue(r.calls.isEmpty())
    }
}
