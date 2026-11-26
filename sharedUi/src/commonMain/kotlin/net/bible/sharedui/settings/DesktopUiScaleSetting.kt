package net.bible.sharedui.settings

/** Device-local UI scale, applied on the next desktop startup. */
interface DesktopUiScaleSetting {
    val percent: Int
    /** Actual startup scale where the renderer rounds preferences; null when fractional scaling is supported. */
    val effectivePercent: Int? get() = null
    fun setPercent(percent: Int)
    fun observeChanges(onChange: () -> Unit): () -> Unit
}

const val DESKTOP_UI_SCALE_KEY = "desktop_ui_scale_percent"
val DESKTOP_UI_SCALE_PRESETS = listOf(75, 100, 125, 150, 175, 200)
const val DESKTOP_UI_SCALE_DEFAULT = 100
