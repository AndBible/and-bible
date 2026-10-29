package net.bible.sharedlogic.platform.desktop.misc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.ui.dialog.AppDialogController

class DesktopAgentPlatformTest {
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
