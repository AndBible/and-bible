package net.bible.sharedcore.reading

enum class ToolbarButton { BIBLE, COMMENTARY, STRONGS, SEARCH, SPEAK, WORKSPACE }

/** Pure port of MainBibleActivity.updateActions()'s width budget + ordering. */
fun fitToolbarButtons(
    state: ToolbarState,
    screenWidthPx: Int,
    density: Float,
    searchMoreRecent: Boolean,
): List<ToolbarButton> {
    val approxSize = 53f * density
    val maxButtons = if (approxSize <= 0f) 0 else ((screenWidthPx * 0.5f) / approxSize).toInt()
    val result = ArrayList<ToolbarButton>()
    fun add(b: ToolbarButton) { if (result.size < maxButtons) result.add(b) }
    if (state.showBible) add(ToolbarButton.BIBLE)
    if (state.showCommentary) add(ToolbarButton.COMMENTARY)
    if (state.showStrongs) add(ToolbarButton.STRONGS)
    val addSearch = { if (state.searchable) add(ToolbarButton.SEARCH) }
    val addSpeak = { if (state.speakable && state.speakStopped) add(ToolbarButton.SPEAK) }
    if (searchMoreRecent) { addSearch(); addSpeak() } else { addSpeak(); addSearch() }
    add(ToolbarButton.WORKSPACE)
    return result
}
