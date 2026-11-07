package net.bible.sharedui.settings

/**
 * Device-local "offer public beta updates" choice. Bound only where it can matter: a GitHub-package or
 * AppImage install of a beta or production build (not test builds, dev runs, Flatpak or MSIX).
 * [enabled] already includes the default (ON for a beta install, OFF for a production install).
 */
interface DesktopBetaUpdateSetting {
    val enabled: Boolean
    fun setEnabled(enabled: Boolean)
    fun observeChanges(onChange: () -> Unit): () -> Unit
}
