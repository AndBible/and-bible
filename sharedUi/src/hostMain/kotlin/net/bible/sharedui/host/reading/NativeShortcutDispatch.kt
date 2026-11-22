package net.bible.sharedui.host.reading

import net.bible.sharedui.reading.NativeShortcut

/** What a [NativeShortcut] does, as calls on the platform; [HostNativeShortcutTarget] is the real one. */
internal interface NativeShortcutTarget {
    fun syncNow()
    fun openSettings()
    fun changeFontSize(change: FontSizeChange)
    fun focusNextWindow()
    fun focusPreviousWindow()
    fun toggleFullScreen()
    fun showHistory()

    /** Cmd+B; false (not handled, the key bubbles on) when there is no reading web view to ask. */
    fun bookmarkSelectionOrChooser(): Boolean
}

/** Pure shortcut -> action mapping (#4177); returns whether the key was consumed. */
internal fun dispatchNativeShortcut(shortcut: NativeShortcut, target: NativeShortcutTarget): Boolean {
    when (shortcut) {
        NativeShortcut.SyncNow -> target.syncNow()
        NativeShortcut.OpenSettings -> target.openSettings()
        NativeShortcut.FontLarger -> target.changeFontSize(FontSizeChange.Larger)
        NativeShortcut.FontSmaller -> target.changeFontSize(FontSizeChange.Smaller)
        NativeShortcut.FontReset -> target.changeFontSize(FontSizeChange.Reset)
        NativeShortcut.NextWindow -> target.focusNextWindow()
        NativeShortcut.PreviousWindow -> target.focusPreviousWindow()
        NativeShortcut.ToggleFullScreen -> target.toggleFullScreen()
        NativeShortcut.BookmarkSelection -> return target.bookmarkSelectionOrChooser()
        NativeShortcut.ShowHistory -> target.showHistory()
    }
    return true
}

/** The actions the desktop shares ([HostShortcutActions]); the bookmark action is platform specific. */
internal class HostNativeShortcutTarget(private val bookmark: () -> Boolean) : NativeShortcutTarget {
    override fun syncNow() = HostShortcutActions.syncNow()
    override fun openSettings() = HostShortcutActions.openSettings()
    override fun changeFontSize(change: FontSizeChange) = HostShortcutActions.changeFontSize(change)
    override fun focusNextWindow() = HostShortcutActions.focusNextWindow()
    override fun focusPreviousWindow() = HostShortcutActions.focusPreviousWindow()
    override fun toggleFullScreen() = HostReadingWindows.toggleFullScreen()
    override fun showHistory() = HostShortcutActions.showHistory()
    override fun bookmarkSelectionOrChooser() = bookmark()
}
