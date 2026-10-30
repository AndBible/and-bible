package net.bible.sharedui.settings

/** Device-local update consent; absent on platforms managed by a package store. */
interface DesktopUpdateCheckSetting {
    val enabled: Boolean
    fun setEnabled(enabled: Boolean)
    fun observeChanges(onChange: () -> Unit): () -> Unit
}
