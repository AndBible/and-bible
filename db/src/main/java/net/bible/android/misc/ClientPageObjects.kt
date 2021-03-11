/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.misc

import kotlinx.serialization.serializer
import net.bible.android.database.json
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange

// Unique identifier that can be used as ID in DOM
val Key.uniqueId: String get() {
    return if (this is VerseRange) {
        "ordinal-${start.ordinal}-${end.ordinal}"
    } else {
        this.osisID.replace(".", "-")
    }
}

fun wrapString(str: String): String = "\"$str\""

class OsisFragment(
    val xml: String,
    val key: Key,
    private val book: Book
) {
    private val keyStr: String get () = "${book.initials}--${key.uniqueId}"
    val features: Map<String, String> get () {
        val type = when {
            book.hasFeature(FeatureType.HEBREW_DEFINITIONS) -> "hebrew"
            book.hasFeature(FeatureType.GREEK_DEFINITIONS) -> "greek"
            else -> null
        }
        return if (type != null) {
            hashMapOf("type" to type, "keyName" to key.name)
        } else emptyMap()
    }

    val toHashMap: Map<String, String> get() {
        val ordinalRangeStr = json.encodeToString(
            serializer(),
            if(key is VerseRange) listOf(key.start.ordinal, key.end.ordinal) else null
        )
        return mapOf(
            "xml" to "`${xml.replace("`", "\\`")}`",
            "key" to wrapString(keyStr),
            "keyName" to wrapString(key.name),
            "bookCategory" to wrapString(book.bookCategory.name),
            "bookInitials" to wrapString(book.initials),
            "osisRef" to wrapString(key.osisRef),
            "features" to json.encodeToString(serializer(), features),
            "ordinalRange" to ordinalRangeStr,
            "language" to wrapString(book.language.code),
            "direction" to wrapString(if(book.isLeftToRight) "ltr" else "rtl"),
        )
    }
}
