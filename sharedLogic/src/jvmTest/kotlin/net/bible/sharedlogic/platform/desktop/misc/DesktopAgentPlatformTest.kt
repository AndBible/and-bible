package net.bible.sharedlogic.platform.desktop.misc

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.ui.dialog.AppDialogController

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DesktopAgentPlatformTest {
    @Test fun permissionQuestionAndNotifierUseRealControllers() = kotlinx.coroutines.test.runTest {
        val permissions = AgentPermissionController()
        val dialogs = AppDialogController()
        val notices = mutableListOf<String>()
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) { notices += message }
            override fun showError(message: String, cause: Throwable?) = Unit
        }
        val agent = DesktopAgentPlatform(notifier, dialogs, permissions, { "translated:$it" }, DesktopLifecycleEvents()) { true }
        try {
            for (choice in net.bible.sharedcore.ai.AgentPermissionChoice.entries) {
                val answer = async { agent.askToolPermission("tool", "description", "action") }
                runCurrent()
                kotlin.test.assertEquals(net.bible.sharedcore.ai.AgentPermissionRequest("tool", "description", "action"), permissions.pending.value)
                permissions.respond(choice)
                kotlin.test.assertEquals(choice.name, answer.await().name)
            }
            for (result in listOf(net.bible.sharedcore.ui.dialog.AppDialogResult.Ok, net.bible.sharedcore.ui.dialog.AppDialogResult.Cancel)) {
                val answer = async { agent.askQuestion("question", "body") }
                runCurrent()
                val shown = dialogs.pending.value!!
                val request = shown.request as net.bible.sharedcore.ui.dialog.AppDialogRequest.Confirm
                kotlin.test.assertEquals("translated:okay", request.confirmText)
                kotlin.test.assertEquals("translated:cancel", request.dismissText)
                dialogs.respond(shown.id, result)
                kotlin.test.assertEquals(result == net.bible.sharedcore.ui.dialog.AppDialogResult.Ok, answer.await())
            }
            agent.showShortMessage("notice")
            kotlin.test.assertEquals(listOf("notice"), notices)
        } finally { agent.close() }
    }

    @Test fun agentForegroundFollowsWindowFocus() {
        val events = DesktopLifecycleEvents()
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) = Unit
            override fun showError(message: String, cause: Throwable?) = Unit
        }
        val agent = DesktopAgentPlatform(notifier, AppDialogController(), AgentPermissionController(), { it }, events) { false }
        assertFalse(agent.hasForegroundUi)
        events.emit(DesktopLifecycleEvent.Active)
        assertTrue(agent.hasForegroundUi)
        events.emit(DesktopLifecycleEvent.ResignActive)
        assertFalse(agent.hasForegroundUi)
        events.emit(DesktopLifecycleEvent.Active)
        events.emit(DesktopLifecycleEvent.Background)
        assertFalse(agent.hasForegroundUi)
        agent.close()
        events.emit(DesktopLifecycleEvent.Active)
        assertFalse(agent.hasForegroundUi)
    }
}
