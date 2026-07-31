package net.bible.sharedcore.reading

data class ToolbarState(
    val pageTitle: String,
    val documentTitle: String,
    val syncRunning: Boolean,
    val showBible: Boolean,
    val showCommentary: Boolean,
    val showStrongs: Boolean,
    val strongsMode: Int,
    val searchable: Boolean,
    val speakable: Boolean,
    val speakStopped: Boolean,
    /**
     * The workspace's chosen colour as raw ARGB, or `null` when the workspace has none. Interpreted
     * (including the "default = not set" sentinel) by
     * [net.bible.sharedcore.reading.readingToolbarContainerArgb]; carried here rather than resolved
     * host-side so `:sharedUi` can react to the display mode and night mode it alone knows about.
     */
    val workspaceColorArgb: Int? = null,
) {
    companion object {
        val EMPTY = ToolbarState("", "", false, false, false, false, 0, false, false, true, null)
    }
}
