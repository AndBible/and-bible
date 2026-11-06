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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityMenuPreference
import net.bible.android.control.activity.ActivityState
import net.bible.android.control.activity.BackgroundErrorRouter
import net.bible.android.control.activity.SyncActivitySource
import net.bible.android.control.event.UserMessages
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.platform.ActivityMenuDefault
import net.bible.sharedcore.platform.AppSettings
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

    private class MemorySettings : AppSettings {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = values["String:$key"] as? String ?: default
        override fun getLong(key: String, default: Long) = values["Long:$key"] as? Long ?: default
        override fun getInt(key: String, default: Int) = values["Int:$key"] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean) = values["Boolean:$key"] as? Boolean ?: default
        override fun getDouble(key: String, default: Double) = values["Double:$key"] as? Double ?: default
        override fun getFloat(key: String, default: Float) = values["Float:$key"] as? Float ?: default
        override fun setString(key: String, value: String?) { values["String:$key"] = value }
        override fun setLong(key: String, value: Long?) { values["Long:$key"] = value }
        override fun setInt(key: String, value: Int?) { values["Int:$key"] = value }
        override fun setBoolean(key: String, value: Boolean?) { values["Boolean:$key"] = value }
        override fun setDouble(key: String, value: Double?) { values["Double:$key"] = value }
        override fun setFloat(key: String, value: Float?) { values["Float:$key"] = value }
    }

    private fun withRouter(enabled: Boolean, block: (Recorder, ActivityCenter, ActivityMenuPreference) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val center = ActivityCenter(scope) { 0L }
        val pref = ActivityMenuPreference(MemorySettings(), ActivityMenuDefault { enabled })
        val sync = SyncActivitySource(center, EventSource<Boolean>()) { "Syncing" }
        val router = BackgroundErrorRouter({ pref.enabled.value }, sync) { "$it - see menu" }
        val recorder = Recorder()
        val presenter = DesktopUserMessagesPresenter({ recorder }, reroute = router::reroute)
        try { block(recorder, center, pref) } finally { presenter.close(); sync.close() }
    }

    @Test fun desktopErrorNotificationGoesToListWhenOn() = withRouter(enabled = true) { r, center, _ ->
        UserMessages.errorNotification("WebDAV failed")
        assertEquals(listOf("info:WebDAV failed - see menu:false"), r.calls)
        val item = center.items.value.single()
        assertEquals(ActivityState.FAILED, item.state)
        assertEquals("WebDAV failed", item.message)
        assertEquals(1, center.unreadFailures.value)
    }

    @Test fun desktopErrorNotificationDialogWhenOff() = withRouter(enabled = false) { r, center, pref ->
        UserMessages.errorNotification("WebDAV failed")
        assertEquals(listOf("error:WebDAV failed"), r.calls)
        // The failure is recorded even though the dialog is kept, so turning the setting on later shows it.
        assertEquals(listOf("WebDAV failed"), center.items.value.map { it.message })
        pref.set(true)
        UserMessages.errorNotification("again")
        assertEquals("info:again - see menu:false", r.calls.last())
    }

    @Test fun androidRecordIsOnlyDoneWhenOnAndNeverReroutes() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val center = ActivityCenter(scope) { 0L }
        val pref = ActivityMenuPreference(MemorySettings(), ActivityMenuDefault { false })
        val sync = SyncActivitySource(center, EventSource<Boolean>()) { "Syncing" }
        val router = BackgroundErrorRouter({ pref.enabled.value }, sync) { "$it - see menu" }
        try {
            router.record("off")
            assertTrue(center.items.value.isEmpty())
            pref.set(true)
            router.record("on")
            assertEquals(listOf("on"), center.items.value.map { it.message })
            assertEquals(1, center.unreadFailures.value)
        } finally { sync.close() }
    }

    @Test fun toastsAreNeverRerouted() = withRouter(enabled = true) { r, center, _ ->
        UserMessages.toast("hello")
        assertEquals(listOf("info:hello:false"), r.calls)
        assertTrue(center.items.value.isEmpty())
    }
}
