package net.bible.android.view.activity.page

import net.bible.android.control.page.window.Window

/** The window's view as the Android [BibleView], for view code that needs members beyond `WindowView`. */
val Window.androidBibleView: BibleView? get() = bibleView as BibleView?
