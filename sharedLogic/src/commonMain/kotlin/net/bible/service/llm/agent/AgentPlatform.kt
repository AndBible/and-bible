package net.bible.service.llm.agent

import net.bible.sharedcore.event.Events

/** The foreground UI and user decisions needed by an agent run. */
interface AgentPlatform {
    val hasForegroundUi: Boolean
    val foregroundChanges: Events<Boolean>
    suspend fun awaitForegroundUi(): Boolean
    suspend fun askToolPermission(toolDisplayName: String, toolDescription: String, actionDescription: String?): ToolPermissionAnswer
    suspend fun askQuestion(title: String?, message: String): Boolean
    fun showShortMessage(message: String)
}

enum class ToolPermissionAnswer { ALLOW, ALLOW_FOR_SESSION, ALLOW_ALL_SESSION, ALLOW_ALWAYS, DENY }

/** Only selection data consumed by agent runs, independent of the reading view. */
interface LlmSelection {
    val bookInitials: String?
    val startOrdinal: Int
    val startOffset: Int?
    val endOrdinal: Int
    val endOffset: Int?
    val text: String
    val osisRef: String?
    val noteEditorEntityType: NoteEditorEntityType?
    val noteEditorEntityId: String?
    val noteEditorContent: String?
    val noteEditorContentType: String?
}
