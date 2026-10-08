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

package net.bible.service.llm.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.AppPosition
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.llm.tools.read.GetCommentariesTool
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.event.Subscription
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * F132 behaviour: `AgentExecutor`'s private `showPermissionDialog` / `showContinueDialog` announce a
 * pending decision when the app leaves while their dialog is open. The real Koin
 * [AppDialogController] / [AgentPermissionController] are driven directly (no rendered host) and a
 * real [CalculatorComposeActivity] is the current Activity, so `awaitActivity` does not post its own wait and
 * every `PermissionWaiting` seen here comes from `awaitingUserDecision`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AgentExecutorDialogWaitingTest {
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private val permissions: AgentPermissionController get() = KoinJavaComponent.get(AgentPermissionController::class.java)

    private val ws = IdType()
    private val seen = mutableListOf<AgentSessionChange.PermissionWaiting>()
    private var sub: Subscription? = null
    private var controller: ActivityController<*>? = null
    private lateinit var savedActivities: ArrayList<ActivityBase>
    private val savedAllowed = CommonUtils.aiSettings.permanentlyAllowedTools
    private val savedDenied = CommonUtils.aiSettings.permanentlyDeniedTools

    @Suppress("UNCHECKED_CAST")
    private fun holderActivities(): ArrayList<ActivityBase> =
        CurrentActivityHolder::class.java.getDeclaredField("activities").apply { isAccessible = true }
            .get(CurrentActivityHolder) as ArrayList<ActivityBase>

    @Before fun setUp() {
        savedActivities = ArrayList(holderActivities())
        holderActivities().clear()
        controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        sub = AgentSessionManager.changes.subscribe { if (it is AgentSessionChange.PermissionWaiting) seen += it }
    }

    @After fun tearDown() {
        sub?.cancel()
        dialogs.cancelAll()
        permissions.dismiss()
        CommonUtils.aiSettings.permanentlyAllowedTools = savedAllowed
        CommonUtils.aiSettings.permanentlyDeniedTools = savedDenied
        runCatching { controller?.pause()?.stop()?.destroy() }
        holderActivities().clear()
        holderActivities().addAll(savedActivities)
    }

    private suspend fun callPrivate(name: String, vararg args: Any?): Any? {
        val executor = AgentExecutor()
        val fn = AgentExecutor::class.declaredFunctions.first { it.name == name }.apply { isAccessible = true }
        return fn.callSuspend(executor, *args)
    }

    private fun waiting(w: Boolean, tool: String? = null) = AgentSessionChange.PermissionWaiting(ws, w, tool)

    @Test fun continueDialogAnnouncesWaitingWhenTheAppLeavesAndClearsOnAnswer() = runTest {
        val result = async { callPrivate("showContinueDialog", 5, 5, ws) }
        yield()
        assertNotNull(dialogs.pending.value)
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        assertEquals(listOf(waiting(true)), seen.toList())
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
        assertEquals(true, result.await())
        assertEquals(listOf(waiting(true), waiting(false)), seen.toList())
    }

    @Test fun permissionDialogAnnouncesWaitingWithTheToolNameWhenTheAppLeaves() = runTest {
        val toolName = ToolRegistry.getDisplayName(GetCommentariesTool)
        val result = async { callPrivate("showPermissionDialog", GetCommentariesTool, JSONObject(), ws) }
        yield()
        assertNotNull(permissions.pending.value)
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        assertEquals(listOf(waiting(true, toolName)), seen.toList())
        permissions.respond(AgentPermissionChoice.ALLOW)
        result.await()
        assertEquals(listOf(waiting(true, toolName), waiting(false)), seen.toList())
    }

    /** The "always allow" confirmation is part of the same decision: no false between the two dialogs. */
    @Test fun alwaysAllowConfirmationStaysInsideTheAnnouncedWait() = runTest {
        val toolName = ToolRegistry.getDisplayName(GetCommentariesTool)
        val result = async { callPrivate("showPermissionDialog", GetCommentariesTool, JSONObject(), ws) }
        yield()
        CurrentActivityHolder.notifyAppPosition(AppPosition.BACKGROUND)
        permissions.respond(AgentPermissionChoice.ALLOW_ALWAYS)
        yield()
        assertNotNull("confirmation dialog is open", dialogs.pending.value)
        assertEquals(listOf(waiting(true, toolName)), seen.toList())
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        result.await()
        assertEquals(listOf(waiting(true, toolName), waiting(false)), seen.toList())
    }
}
