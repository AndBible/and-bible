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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.timeIntervalSince1970
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.llm.agent.ToolPermissionAnswer
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ai.AgentPermissionRequest
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import platform.Foundation.NSNotificationCenter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosReadingPlatformPortsTest {
    private val logs = mutableListOf<Triple<LogLevel, String, String>>()

    init { Log.sinkOverride = { level, tag, msg, _ -> logs += Triple(level, tag, msg) } }

    @AfterTest fun restoreLog() { Log.sinkOverride = null }

    @Test fun linkPlatformRoutesEachCallToItsLambda() {
        val seen = mutableListOf<String>()
        val p = IosLinkPlatform(
            openRoute = { seen += "route:$it" },
            searchStrongs = { ref, docs -> seen += "strongs:$ref:$docs"; ref == "H1" },
            reportError = { seen += "report" },
        )
        assertTrue(p.searchStrongsInReadingView("H1", listOf("StrongsHebrew")))
        assertFalse(p.searchStrongsInReadingView("G9", emptyList()))
        p.openRoute("reading")
        p.reportJsError()
        assertEquals(listOf("strongs:H1:[StrongsHebrew]", "strongs:G9:[]", "route:reading", "report"), seen)
    }

    @Test fun historyPlatformAsksTheShellWhetherReadingIsShownAndLeavesThroughIt() {
        var onReading = true
        var left = 0
        val p = IosHistoryPlatform(isOnReading = { onReading }, leave = { left++ })
        assertTrue(p.isOnReadingScreen())
        onReading = false
        assertFalse(p.isOnReadingScreen())
        p.leaveCurrentScreen()
        assertEquals(1, left)
    }

    @Test fun historyPlatformDefaultsStayOnTheReadingScreen() {
        val p = IosHistoryPlatform()
        assertTrue(p.isOnReadingScreen())
        p.leaveCurrentScreen()
    }

    // --- the copy-settings picker (the window-free body; a Window needs a repository)

    private val texts = mapOf(
        "copy_settings_title" to "Which?", "okay" to "OK", "cancel" to "Cancel", "select_all" to "All", "select_none" to "None",
    )
    private val types = listOf(TextDisplaySettings.Types.FONTSIZE, TextDisplaySettings.Types.JUSTIFY, TextDisplaySettings.Types.STRONGS)

    private fun picker(dialogs: AppDialogController) = IosWindowSettingsPicker(dialogs, { _, t -> t.name }, { texts.getValue(it) })

    @Test fun windowSettingsResultApplies() = runTest {
        val dialogs = AppDialogController()
        val answer = async { picker(dialogs).ask(types) { it.name.lowercase() } }
        yield()
        val shown = dialogs.pending.value!!
        val request = shown.request as AppDialogRequest.MultiChoice
        assertEquals("Which?", request.title)
        assertEquals(listOf("fontsize", "justify", "strongs"), request.options.map { it.label })
        assertEquals(emptyList(), request.selectedIds)
        assertEquals("All", request.selectAllText)
        dialogs.respond(shown.id, AppDialogResult.SelectedMany(listOf("2", "0")))
        assertEquals(listOf(TextDisplaySettings.Types.FONTSIZE, TextDisplaySettings.Types.STRONGS), answer.await())
    }

    @Test fun windowSettingsCancelAndNothingCheckedAreEmpty() = runTest {
        val dialogs = AppDialogController()
        val cancelled = async { picker(dialogs).ask(types) { it.name } }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertEquals(emptyList(), cancelled.await())
        val none = async { picker(dialogs).ask(types) { it.name } }
        yield()
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedMany(emptyList()))
        assertEquals(emptyList(), none.await())
    }

    // --- IosUserNotifier / IosAgentPlatform 

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

    @Test fun infoReachesTheInfoSinkWithItsDuration() {
        val shown = mutableListOf<Pair<String, Boolean>>()
        val n: UserNotifier = IosUserNotifier(AppDialogController(), onInfo = { m, l -> shown += m to l })
        n.showInfo("short", false)
        n.showInfo("long", true)
        assertEquals(listOf("short" to false, "long" to true), shown)
    }

    private val dialogsForAgent = AppDialogController()
    private val permissionsForAgent = AgentPermissionController()

    private fun agent(notifier: UserNotifier, center: NSNotificationCenter, live: () -> Boolean = { true }) =
        IosAgentPlatform(notifier, dialogsForAgent, permissionsForAgent, { texts.getValue(it) }, center, live)

    @Test fun agentPermissionAsksAndReturnsTheAnswer() = runTest {
        val agent = agent(silentNotifier, NSNotificationCenter())
        try {
            val answers = ToolPermissionAnswer.entries.associateWith { expected ->
                val deferred = async { agent.askToolPermission("tool", "desc", "do it") }
                yield()
                assertEquals(AgentPermissionRequest("tool", "desc", "do it"), permissionsForAgent.pending.value)
                permissionsForAgent.respond(AgentPermissionChoice.valueOf(expected.name))
                deferred.await()
            }
            assertEquals(ToolPermissionAnswer.entries.associateWith { it }, answers)
        } finally { agent.close() }
    }

    @Test fun agentPermissionDismissedAnswersDeny() = runTest {
        val agent = agent(silentNotifier, NSNotificationCenter())
        try {
            val deferred = async { agent.askToolPermission("tool", "desc", null) }
            yield()
            permissionsForAgent.dismiss()
            assertEquals(ToolPermissionAnswer.DENY, deferred.await())
        } finally { agent.close() }
    }

    @Test fun agentQuestionIsAConfirmDialogAndOkIsTrue() = runTest {
        val agent = agent(silentNotifier, NSNotificationCenter())
        try {
            val yes = async { agent.askQuestion("Sure?", "Really?") }
            yield()
            val shown = dialogsForAgent.pending.value!!
            assertEquals(AppDialogRequest.Confirm("Sure?", "Really?", "OK", "Cancel"), shown.request)
            dialogsForAgent.respond(shown.id, AppDialogResult.Ok)
            assertTrue(yes.await())
            val no = async { agent.askQuestion(null, "Again?") }
            yield()
            dialogsForAgent.respond(dialogsForAgent.pending.value!!.id, AppDialogResult.Cancel)
            assertFalse(no.await())
        } finally { agent.close() }
    }

    @Test fun agentShortMessagesGoToTheNotifier() {
        val infos = mutableListOf<String>()
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) { infos += message }
            override fun showError(message: String, cause: Throwable?) = Unit
        }
        val agent = agent(notifier, NSNotificationCenter())
        try {
            agent.showShortMessage("short")
            assertEquals(listOf("short"), infos)
        } finally { agent.close() }
    }

    private val silentNotifier = object : UserNotifier {
        override fun showInfo(message: String, long: Boolean) = Unit
        override fun showError(message: String, cause: Throwable?) = Unit
    }

    @Test fun agentForegroundFollowsTheUiApplicationNotifications() = runTest {
        val center = NSNotificationCenter()
        var live = false
        val agent = agent(silentNotifier, center) { live }
        try {
            val seen = mutableListOf<Boolean>()
            val sub = agent.foregroundChanges.subscribe { seen += it }
            center.postNotificationName("UIApplicationDidBecomeActiveNotification", null)
            center.postNotificationName("UIApplicationDidEnterBackgroundNotification", null)
            center.postNotificationName("UIApplicationDidBecomeActiveNotification", null)
            sub.cancel()
            assertEquals(listOf(true, false, true), seen)
        } finally { agent.close() }
    }

    @Test fun awaitForegroundUiWaitsUntilTheLiveStateBecomesActive() = runBlocking {
        val center = NSNotificationCenter()
        var live = false
        val agent = agent(silentNotifier, center) { live }
        try {
            assertFalse(agent.hasForegroundUi)
            val waiting = async(Dispatchers.Default) { agent.awaitForegroundUi() }
            delay(700)
            assertFalse(waiting.isCompleted, "still in the background")
            live = true
            // Off Main the flag is the notification-fed one, so the transition arrives as UIKit would post it.
            center.postNotificationName("UIApplicationDidBecomeActiveNotification", null)
            assertTrue(withTimeout(5_000) { waiting.await() })
        } finally { agent.close() }
    }

    @Test fun initialStateIsReadThroughTheSeamOnMainAndOffMain() {
        val center = NSNotificationCenter()
        // Constructed on the test (main) thread.
        agent(silentNotifier, center) { true }.also { assertTrue(it.hasForegroundUi); it.close() }
        // Constructed off Main while the app is active. Main must stay free (pumped), because the constructor hops to it.
        var built: IosAgentPlatform? = null
        var offMainRead: Boolean? = null
        dispatch_async(dispatch_get_global_queue(0, 0u)) {
            check(!NSThread.isMainThread)
            val agent = agent(silentNotifier, center) { check(NSThread.isMainThread); true }
            offMainRead = agent.hasForegroundUi   // off Main: the cached flag, which the constructor seeded
            built = agent
        }
        val deadline = NSDate().timeIntervalSince1970 + 10
        while (built == null && NSDate().timeIntervalSince1970 < deadline) {
            NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        try {
            assertTrue(built != null, "construction off Main must finish (no deadlock)")
            assertEquals(true, offMainRead, "initial active state must not be lost when built off Main")
        } finally { built?.close() }
    }
}
