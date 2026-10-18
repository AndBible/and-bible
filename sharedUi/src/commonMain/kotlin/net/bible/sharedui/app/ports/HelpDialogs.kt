package net.bible.sharedui.app.ports

interface HelpDialogs {
    fun show(topic: HelpTopic)
}

/** The help topics lifted call sites use; each lifting task adds its constants. */
enum class HelpTopic {
    /** The reading-progress screen's overflow help. */
    READING_PROGRESS,
}
