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
     * comparison (batch 4b feedback, 2026-08-01): once the workspace theme is on, derived is now
     * the DEFAULT; the literal colour survives only as an opt-out
     * (`net.bible.sharedui.TOOLBAR_LITERAL_COLOR_FEATURE`). So `false` here covers two distinct
     * cases — the theme master switch is off (no workspace seed exists at all; this is today's
     * shipped appearance and stays bit-for-bit unchanged) OR the switch is on but the user opted
     * back into the literal colour — and `true` means the theme is on and that opt-out is not set.
     *
     * Computed in `ToolbarStateServiceImpl` as "theme switch on AND NOT literal opt-out", so the
     * derived variant cannot be active while the theme itself is off — one field, so there is no
     * second condition on the `:sharedUi` side to drift out of step.
     */
    val deriveToolbarFromTheme: Boolean = false,
) {
    companion object {
        val EMPTY = ToolbarState("", "", false, false, false, false, 0, false, false, true, null, false)
    }
}
