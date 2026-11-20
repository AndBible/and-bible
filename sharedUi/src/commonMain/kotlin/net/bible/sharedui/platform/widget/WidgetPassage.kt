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

package net.bible.sharedui.platform.widget

import net.bible.android.control.page.OrdinalRange
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.sword.BookAndKey
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.versification.system.Versifications

/**
 * Resolves a widget's [WidgetUrlAction.OpenPassage] the way Android's `ReadingAppBootstrap.openDeepLink` resolves a
 * `read.andbible.org` link (`CommonUtils.parseAndBibleReference`): a [BookAndKey] carrying the ordinal, that the
 * caller opens in the ACTIVE window. It must not go through `LinkControl`, which opens in the links window when
 * "open links in a dedicated window" is on.
 */
object WidgetPassage {
    private const val TAG = "WidgetPassage"

    /** The target of [action], or null when the key cannot be resolved. */
    fun resolve(action: WidgetUrlAction.OpenPassage): BookAndKey? {
        var docStr = action.document
        var keyStr = action.key
        // JSword style "StrongsGreek:5548": the document is derived when it was not given.
        if (':' in keyStr && docStr.isNullOrBlank()) {
            docStr = keyStr.substringBefore(':')
            keyStr = keyStr.substringAfter(':')
        }
        val doc = docStr?.let { Books.installed().getBook(it) }
        val defV11n = if (doc is SwordBook) doc.versification else KJVA
        val v11n = action.v11n?.let { Versifications.instance().getVersification(it) } ?: defV11n
        return try {
            if (doc is SwordDictionary) return BookAndKey(doc.getKey(zeroPad(keyStr)), doc)
            val key = PassageKeyFactory.instance().getKey(v11n, keyStr)
            BookAndKey(key, doc, ordinal = action.ordinal?.let { OrdinalRange(it) })
        } catch (e: Exception) {
            Log.e(TAG, "Cannot resolve widget passage: $action", e)
            null
        }
    }

    /** Resolves [action] and hands the target to [openInActiveWindow] (document, key); false when it cannot be resolved. */
    fun open(action: WidgetUrlAction.OpenPassage, openInActiveWindow: (org.crosswire.jsword.book.Book?, BookAndKey) -> Unit): Boolean {
        val target = resolve(action) ?: return false
        openInActiveWindow(target.document, target)
        return true
    }

    /** Same padding as `CommonUtils.prependDictionaryKeyWithZeros`. */
    private fun zeroPad(keyStr: String) = keyStr.padStart(5, '0')
}
