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

package net.bible.sharedlogic.platform.ios

import kotlinx.coroutines.test.runTest
import net.bible.service.llm.agent.ToolPermissionAnswer
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import platform.Foundation.NSNotificationCenter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPendingUiPortsTest {
    private val logs = mutableListOf<Triple<LogLevel, String, String>>()

    init { Log.sinkOverride = { level, tag, msg, _ -> logs += Triple(level, tag, msg) } }

    @AfterTest fun restoreLog() { Log.sinkOverride = null }

    private fun assertLoggedOnce(tag: String) {
        assertEquals(1, logs.count { it.second == tag }, "one $tag log per call, got $logs")
    }

    @Test fun keyChooserIsNotStartedAndLogsOnce() {
        assertFalse(PendingKeyChooserLauncher().notStarted())
        assertLoggedOnce("C7-pending")
        assertTrue(logs.single().third.contains("PendingKeyChooserLauncher"))
    }

    @Test fun windowSettingsPickerIsCancelledAndLogsOnce() {
        assertEquals(emptyList(), PendingWindowSettingsPicker().cancelled())
        assertLoggedOnce("C7-pending")
    }

    @Test fun linkPlatformHandlesNothingAndLogsOncePerCall() {
        val p = PendingLinkPlatform()
        assertFalse(p.searchStrongsInReadingView("H1", listOf("StrongsHebrew")))
        assertLoggedOnce("C7-pending")
        p.openRoute("reading")
        p.reportJsError()
        assertEquals(3, logs.count { it.second == "C7-pending" })
        assertTrue(logs[1].third.contains("openRoute(reading)"))
        assertTrue(logs[2].third.contains("reportJsError"))
    }

    @Test fun historyPlatformStaysOnTheReadingScreenAndIsNotPending() {
        val p = IosHistoryPlatform()
        assertTrue(p.isOnReadingScreen())
        p.leaveCurrentScreen()
        assertTrue(logs.isEmpty())
    }

    // --- IosUserNotifier / IosAgentPlatform (same C7 policy)

    @Test fun errorsBecomeAnOwnerlessMessageRequestAndInfoOnlyLogs() {
        val dialogs = AppDialogController()
        val n: UserNotifier = IosUserNotifier(dialogs) { "Okay!" }
        n.showInfo("hello")
        assertNull(dialogs.pending.value)
        n.showError("boom")
        val request = dialogs.pending.value!!.request as AppDialogRequest.Message
        assertEquals("boom", request.message)
        assertEquals("Okay!", request.confirmText)
        assertNull(request.title)
        assertTrue(logs.any { it.second == "UserNotifier" && it.third.contains("hello") })
    }

    @Test fun agentFailsClosedOnPermissionAndQuestionAndShortMessagesGoToTheNotifier() = runTest {
        val infos = mutableListOf<String>()
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) { infos += message }
            override fun showError(message: String, cause: Throwable?) = Unit
        }
        val center = NSNotificationCenter()
        val agent = IosAgentPlatform(notifier, center) { true }
        try {
            assertEquals(ToolPermissionAnswer.DENY, agent.askToolPermission("tool", "desc", null))
            assertFalse(agent.askQuestion("t", "m"))
            assertEquals(2, logs.count { it.second == "C7-pending" })
            agent.showShortMessage("short")
            assertEquals(listOf("short"), infos)
        } finally { agent.close() }
    }

    @Test fun agentForegroundFollowsTheUiApplicationNotifications() = runTest {
        val center = NSNotificationCenter()
        val agent = IosAgentPlatform(object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) = Unit
            override fun showError(message: String, cause: Throwable?) = Unit
        }, center) { true }
        try {
            val seen = mutableListOf<Boolean>()
            val sub = agent.foregroundChanges.subscribe { seen += it }
            center.postNotificationName("UIApplicationDidEnterBackgroundNotification", null)
            center.postNotificationName("UIApplicationDidBecomeActiveNotification", null)
            sub.cancel()
            assertEquals(listOf(false, true), seen)
            assertTrue(agent.awaitForegroundUi())
        } finally { agent.close() }
    }
}
