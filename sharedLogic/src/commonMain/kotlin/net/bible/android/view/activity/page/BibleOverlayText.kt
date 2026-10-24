/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.page

import net.bible.android.control.page.PageControl
import net.bible.android.control.versification.wholeChapter
import net.bible.sharedlogic.sync.bookNameMonitor
import net.bible.sharedlogic.sync.withMonitor
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BookName

/**
 * The current page's display title: the second half of [bibleOverlayText] and, on Android, the text of the classic page
 * title. Lifted out of Android's `ReadingCommands.pageTitleText` so iOS shows the same text; Android delegates here.
 *
 * In a Bible the key is the current Bible verse; a verse 0 ("the whole chapter") shows the chapter. Null means there is no
 * current key (Android turns that into its `KeyIsNull`).
 */
fun pageTitleText(pageControl: PageControl): String? {
    val page = pageControl.currentPageManager.currentPage
    val isBible = page.currentDocument?.bookCategory == BookCategory.BIBLE
    val key = if (isBible) pageControl.currentBibleVerse else page.displayKey
    return pageTitleFor(key)
}

/** The title of [key]: a verse 0 stands for its whole chapter; null for no key. Pure, for tests. */
internal fun pageTitleFor(key: Key?): String? =
    if (key is Verse && key.verse == 0) wholeChapter(key, false).getName() else key?.getName()

/**
 * The Bible reference overlay's text, `"<document abbreviation>:<page title>"` with short book names (for example
 * `"KJV:John 3:16"`); null when there is no current key. Lifted from Android's `ReadingCommands.bibleOverlayText`.
 */
fun bibleOverlayText(pageControl: PageControl): String? {
    val abbreviation = pageControl.currentPageManager.currentPage.currentDocument?.abbreviation
    return withShortBookNames { overlayTextFor(abbreviation, pageTitleText(pageControl)) }
}

/** `"<abbreviation>:<title>"`; null without a title. Pure, for tests. */
internal fun overlayTextFor(abbreviation: String?, title: String?): String? =
    title?.let { "$abbreviation:$it" }

/** Runs [block] with short book names, restoring the previous setting; under the one monitor everything else uses. */
internal inline fun <T> withShortBookNames(crossinline block: () -> T): T = withMonitor(bookNameMonitor) {
    val oldValue = BookName.isFullBookName()
    BookName.setFullBookName(false)
    try {
        block()
    } finally {
        BookName.setFullBookName(oldValue)
    }
}
