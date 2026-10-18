package net.bible.sharedui.app.ports

interface HelpDialogs {
    fun show(topic: HelpTopic)
}

/** The help topics lifted call sites use; each lifting task adds its constants. */
enum class HelpTopic {
    /** The reading-progress screen's overflow help. */
    READING_PROGRESS,

    /** The workspace selector's help (`help_workspaces_title`). */
    WORKSPACES,

    /** The cloud documents screen's overflow help (`help_document_sync_text`). */
    DOCUMENT_SYNC,

    /** The AI connection settings overflow help (`help_ai_connection_text`). */
    AI_CONNECTION,
}
