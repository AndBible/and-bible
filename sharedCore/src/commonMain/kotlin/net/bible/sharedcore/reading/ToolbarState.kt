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
    /**
     * Take the toolbar's container colour from the theme (`primaryContainer`) instead of the
     * literal workspace colour (A/B batch 4b, §6). TEMPORARY: this exists so the maintainer can
     * compare the two on the device; the losing branch is deleted once the choice is made.
     *
     * Computed in `ToolbarStateServiceImpl` as "both experimental switches on", so the toolbar
     * variant cannot be active while the theme itself is off — one field, so there is no second
     * condition on the `:sharedUi` side to drift out of step.
     */
    val deriveToolbarFromTheme: Boolean = false,
) {
    companion object {
        val EMPTY = ToolbarState("", "", false, false, false, false, 0, false, false, true, null, false)
    }
}
