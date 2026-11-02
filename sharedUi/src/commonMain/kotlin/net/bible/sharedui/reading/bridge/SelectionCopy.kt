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

package net.bible.sharedui.reading.bridge

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.bible.android.view.activity.page.Selection
import net.bible.sharedcore.platform.AppJson
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books

/** What the hardware copy shortcut (Ctrl+C) puts on the clipboard. */
internal sealed interface SelectionCopy {
    /** Bible verses, formatted by the user's share options (reference, version, verse numbers). */
    class Verses(val selection: Selection) : SelectionCopy

    /** The selected text exactly as the page shows it. */
    class PlainText(val text: String) : SelectionCopy
}

private fun isInstalledBible(bookInitials: String?): Boolean =
    bookInitials != null && Books.installed().getBook(bookInitials)?.bookCategory == BookCategory.BIBLE

/**
 * Decides what Ctrl+C copies for a `bibleView.querySelection()` result ([queryResult] is the raw JSON the page
 * returned). Returns null when there is nothing to copy.
 *
 * - A selection in an installed Bible is copied as [SelectionCopy.Verses], so the share options apply.
 * - A selection in any other document (commentary, general book, dictionary, ...) is copied as the selected text
 *   only (#2860). Its ordinals are not Bible verses: formatting them as verses copied whole commentary entries, or
 *   nothing at all for a book without verses.
 * - A selection without document context arrives as a bare JSON string. This is text in a popup, e.g. a footnote,
 *   which the page renders outside its `.document` element (#3462). It is copied as plain text.
 * - Blank text is not copied.
 */
internal fun selectionCopy(
    queryResult: String,
    isBible: (bookInitials: String?) -> Boolean = ::isInstalledBible,
): SelectionCopy? {
    val element = try {
        AppJson.parseToJsonElement(queryResult)
    } catch (e: SerializationException) {
        return null
    }
    return when {
        element is JsonPrimitive && element.isString -> plainText(element.content)
        element is JsonObject -> {
            val selection = try {
                AppJson.decodeFromJsonElement(Selection.serializer(), element)
            } catch (e: SerializationException) {
                return null
            } catch (e: IllegalArgumentException) {
                return null
            }
            if (isBible(selection.bookInitials)) SelectionCopy.Verses(selection) else plainText(selection.text)
        }
        else -> null
    }
}

private fun plainText(text: String): SelectionCopy? = if (text.isBlank()) null else SelectionCopy.PlainText(text)
