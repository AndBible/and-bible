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
) {
    companion object {
        val EMPTY = ToolbarState("", "", false, false, false, false, 0, false, false, true)
    }
}
