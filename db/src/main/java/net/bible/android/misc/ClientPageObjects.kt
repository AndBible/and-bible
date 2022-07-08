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
import org.apache.commons.text.StringEscapeUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.jdom2.Element
import org.jdom2.Text
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.jdom2.output.support.AbstractXMLOutputProcessor
import org.jdom2.output.support.FormatStack
import java.io.Writer

// Unique identifier that can be used as ID in DOM
val Key.uniqueId: String get() {
    return if (this is VerseRange) {
        "ordinal-${start.ordinal}-${end.ordinal}"
    } else {
        this.osisID.replace(".", "-")
    }
}

fun wrapString(str_: String?, replaceBackslash: Boolean = false): String =
    if(str_ == null) "null"
    else {
        var str = str_
        if(replaceBackslash) {
            str = str.replace("\\", "\\\\")
        }
        str = str.replace("`", "\\`")
        "`$str`"
    }

fun elementToString(e: Element): String {
    val format = Format.getRawFormat()
    val processor = object: AbstractXMLOutputProcessor() {
        override fun printText(out: Writer, fstack: FormatStack, text: Text) {
            // We might have html-encoded characters in OSIS text.
            // Let's un-encode them first, lest we will end up with double-encoded strings
            // such as &amp;quot;
            text.text = StringEscapeUtils.unescapeHtml4(text.text)
            super.printText(out, fstack, text)
        }
    }
    return XMLOutputter(format, processor).outputString(e)
}


class OsisFragment(
    val xml: Element,
    val key: Key,
    private val book: Book
) {
    val xmlStr = elementToString(xml)
    private val keyStr: String get () = "${book.initials}--${key.uniqueId}"

    val hasChapter: Boolean get() = xml.getChild("div")?.getChild("chapter") != null

    private val features: Map<String, String> get () {
        val hasHebrew = book.hasFeature(FeatureType.HEBREW_DEFINITIONS)
        val hasGreek = book.hasFeature(FeatureType.GREEK_DEFINITIONS)

        val type = when {
            hasHebrew && hasGreek -> "hebrew-and-greek"
            hasHebrew -> "hebrew"
            hasGreek -> "greek"
            else -> null
        }
        return if (type != null) {
            hashMapOf("type" to type, "keyName" to key.name)
        } else emptyMap()
    }

    val annotateRef: VerseRange? get() {
        val annotateRef = xml.getChild("div")?.getAttribute("annotateRef")?.value ?: return null
        if(book !is SwordBook) return null
        return try {VerseRangeFactory.fromString(book.versification, annotateRef) } catch (e: NoSuchVerseException) { null }
    }

    val toHashMap: Map<String, String> get() {
        val ordinalRangeStr = json.encodeToString(
            serializer(),
            if(key is VerseRange) listOf(key.start.ordinal, key.end.ordinal) else null
        )
        val isNewTestament = key is VerseRange && key.start.ordinal >= BibleBook.MATT.ordinal

        return mapOf(
            "xml" to wrapString(xmlStr),
            "key" to wrapString(keyStr),
            "keyName" to wrapString(key.name),
            "v11n" to wrapString(if(book is SwordBook) book.versification.name else null),
            "bookCategory" to wrapString(book.bookCategory.name),
            "bookInitials" to wrapString(book.initials),
            "bookAbbreviation" to wrapString(book.abbreviation),
            "osisRef" to wrapString(key.osisRef),
            "isNewTestament" to json.encodeToString(serializer(), isNewTestament),
            "features" to json.encodeToString(serializer(), features),
            "ordinalRange" to ordinalRangeStr,
            "language" to wrapString(book.language.code),
            "direction" to wrapString(if(book.isLeftToRight) "ltr" else "rtl"),
        )
    }
}
