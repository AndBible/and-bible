package net.bible.sharedui.settings

/** Device-local UI scale, applied on the next desktop startup. */
interface DesktopUiScaleSetting {
    val percent: Int
    /** Effective startup scale where the renderer rounds; independent of edits to the saved preset. */
    val effectivePercent: Int? get() = null
    /** An explicit startup override takes precedence over the saved preset in this process. */
    val overriddenThisSession: Boolean get() = false
    fun setPercent(percent: Int)
    fun observeChanges(onChange: () -> Unit): () -> Unit
}

const val DESKTOP_UI_SCALE_KEY = "desktop_ui_scale_percent"
val DESKTOP_UI_SCALE_PRESETS = listOf(75, 100, 125, 150, 175, 200)
const val DESKTOP_UI_SCALE_DEFAULT = 100
