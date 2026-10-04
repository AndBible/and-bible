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
     * literal workspace colour (A/B batch 4b, §6). Maintainer decision on the rendered
     * comparison (batch 4b feedback, 2026-08-01): derived is the DEFAULT; the literal colour
     * survives only as an opt-out (`net.bible.sharedui.TOOLBAR_LITERAL_COLOR_FEATURE`). So
     * `false` here means only that opt-out is set, and `true` (the default) means it is not.
     * (Until 2026-09-18 this also covered a theme master switch being off; that switch,
     * `workspace_color_theme`, is now retired -- workspace-colour theming is always on.)
     *
     * Computed in `ToolbarStateServiceImpl` as "NOT literal opt-out", so `:sharedUi` reads one
     * field rather than a second condition of its own to drift out of step.
     */
    val deriveToolbarFromTheme: Boolean = false,
) {
    companion object {
        val EMPTY = ToolbarState("", "", false, false, false, false, 0, false, false, true, null, false)
    }
}
