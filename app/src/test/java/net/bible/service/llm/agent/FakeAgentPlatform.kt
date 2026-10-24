package net.bible.service.llm.agent

import kotlinx.coroutines.awaitCancellation
import net.bible.sharedcore.event.EventSource

internal class FakeAgentPlatform : AgentPlatform {
    override var hasForegroundUi = true
    override val foregroundChanges = EventSource<Boolean>()
    override suspend fun awaitForegroundUi(): Boolean {
        if (hasForegroundUi) return true
        awaitCancellation()
    }
    override suspend fun askToolPermission(toolDisplayName: String, toolDescription: String, actionDescription: String?) = ToolPermissionAnswer.DENY
    override suspend fun askQuestion(title: String?, message: String) = false
    override fun showShortMessage(message: String) = Unit
}
