package net.bible.sharedlogic.platform.desktop.misc

import kotlinx.coroutines.delay
import net.bible.service.llm.agent.AgentPlatform
import net.bible.service.llm.agent.ToolPermissionAnswer
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ai.AgentPermissionRequest
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult

class DesktopAgentPlatform(
    private val notifier: UserNotifier,
    private val dialogs: AppDialogController,
    private val permissions: AgentPermissionController,
    private val text: (String) -> String,
    events: DesktopLifecycleEvents,
    initialFocus: () -> Boolean,
) : AgentPlatform {
    private val source = EventSource<Boolean>()
    private val lock = Any()
    @Volatile private var active = false
    private var receivedEvent = false
    private val unsubscribe = events.observe { event ->
        val value = event == DesktopLifecycleEvent.Active
        synchronized(lock) { receivedEvent = true; active = value }
        source.emit(value)
    }
    init {
        val initial = initialFocus()
        synchronized(lock) { if (!receivedEvent) active = initial }
    }
    fun close() = unsubscribe()
    override val hasForegroundUi: Boolean get() = active
    override val foregroundChanges: Events<Boolean> get() = source
    override suspend fun awaitForegroundUi(): Boolean {
        while (!hasForegroundUi) delay(500)
        return true
    }
    override suspend fun askToolPermission(toolDisplayName: String, toolDescription: String, actionDescription: String?): ToolPermissionAnswer =
        when (permissions.await(AgentPermissionRequest(toolDisplayName, toolDescription, actionDescription))) {
            AgentPermissionChoice.ALLOW -> ToolPermissionAnswer.ALLOW
            AgentPermissionChoice.ALLOW_FOR_SESSION -> ToolPermissionAnswer.ALLOW_FOR_SESSION
            AgentPermissionChoice.ALLOW_ALL_SESSION -> ToolPermissionAnswer.ALLOW_ALL_SESSION
            AgentPermissionChoice.ALLOW_ALWAYS -> ToolPermissionAnswer.ALLOW_ALWAYS
            AgentPermissionChoice.DENY -> ToolPermissionAnswer.DENY
        }
    override suspend fun askQuestion(title: String?, message: String): Boolean =
        dialogs.await(AppDialogRequest.Confirm(title = title, message = message, confirmText = text("okay"), dismissText = text("cancel"))) == AppDialogResult.Ok
    override fun showShortMessage(message: String) = notifier.showInfo(message)
}
