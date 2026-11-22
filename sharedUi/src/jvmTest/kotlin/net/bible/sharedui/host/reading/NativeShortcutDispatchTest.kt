package net.bible.sharedui.host.reading

import net.bible.sharedui.reading.NativeShortcut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeShortcutDispatchTest {
    private class Recorder(var bookmarkHandled: Boolean = true) : NativeShortcutTarget {
        val calls = mutableListOf<String>()
        override fun syncNow() { calls += "sync" }
        override fun openSettings() { calls += "settings" }
        override fun changeFontSize(change: FontSizeChange) { calls += "font:$change" }
        override fun focusNextWindow() { calls += "next" }
        override fun focusPreviousWindow() { calls += "previous" }
        override fun toggleFullScreen() { calls += "fullscreen" }
        override fun showHistory() { calls += "history" }
        override fun bookmarkSelectionOrChooser(): Boolean { calls += "bookmark"; return bookmarkHandled }
    }

    @Test fun everyShortcutRunsItsOwnAction() {
        val expected = mapOf(
            NativeShortcut.SyncNow to "sync", NativeShortcut.OpenSettings to "settings",
            NativeShortcut.FontLarger to "font:Larger", NativeShortcut.FontSmaller to "font:Smaller",
            NativeShortcut.FontReset to "font:Reset", NativeShortcut.NextWindow to "next",
            NativeShortcut.PreviousWindow to "previous", NativeShortcut.ToggleFullScreen to "fullscreen",
            NativeShortcut.BookmarkSelection to "bookmark", NativeShortcut.ShowHistory to "history",
        )
        assertEquals(NativeShortcut.entries.toSet(), expected.keys, "a new shortcut needs a dispatch test")
        for ((shortcut, call) in expected) {
            val r = Recorder()
            assertTrue(dispatchNativeShortcut(shortcut, r), shortcut.name)
            assertEquals(listOf(call), r.calls, shortcut.name)
        }
    }

    @Test fun bookmarkWithoutAWebViewIsNotConsumed() {
        assertFalse(dispatchNativeShortcut(NativeShortcut.BookmarkSelection, Recorder(bookmarkHandled = false)))
    }
}
