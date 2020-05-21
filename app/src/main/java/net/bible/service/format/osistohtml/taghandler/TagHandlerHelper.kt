/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
package net.bible.service.format.osistohtml.taghandler

import net.bible.service.common.Logger
import org.apache.commons.lang3.StringUtils
import org.xml.sax.Attributes

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object TagHandlerHelper {
    private val log = Logger("TagHandlerHelper")

    /** support defaultvalue with attribute fetch
     */
    fun getAttribute(attributeName: String?, attrs: Attributes, defaultValue: String): String {
        val attrValue = attrs.getValue(attributeName)
        return attrValue ?: defaultValue
    }

    /** support defaultvalue with attribute fetch
     */
    fun getAttribute(attributeName: String, attrs: Attributes, defaultValue: Int): Int {
        var retval = defaultValue
        try {
            val attrValue = attrs.getValue(attributeName)
            if (attrValue != null) {
                retval = attrValue.toInt()
            }
        } catch (e: Exception) {
            log.warn("Non numeric but expected integer for $attributeName")
        }
        return retval
    }

    /** Return true if attribute contains the desiredValue in any case
     */
    fun contains(attributeName: String?, attrs: Attributes, desiredValue: String?): Boolean {
        var attribContainsExpectedValue = false
        val attrValue = attrs.getValue(attributeName)
        if (attrValue != null) {
            attribContainsExpectedValue = StringUtils.containsIgnoreCase(attrValue, desiredValue)
        }
        return attribContainsExpectedValue
    }

    /**
     * see if an attribute exists and has a value
     *
     * @param attributeName
     * @param attrs
     * @return
     */
    @JvmStatic
    fun isAttr(attributeName: String?, attrs: Attributes): Boolean {
        val attrValue = attrs.getValue(attributeName)
        return StringUtils.isNotEmpty(attrValue)
    }

    /** return verse from osis id of format book.chap.verse
     *
     * @param osisID osis Id
     * @return verse number
     */
    @JvmStatic
    fun osisIdToVerseNum(osisID: String?): Int {
        /* You have to use "\\.", the first backslash is interpreted as an escape by the
        Java compiler, so you have to use two to get a String that contains one
        backslash and a dot, which is what you want the regexp engine to see.*/
        if (osisID != null) {
            val parts = osisID.split(".").toTypedArray()
            if (parts.size > 1) {
                val verse = parts[parts.size - 1]
                return Integer.valueOf(verse)
            }
        }
        return 0
    }

    fun printAttributes(attrs: Attributes) {
        for (i in 0 until attrs.length) {
            log.debug(attrs.getLocalName(i) + ":" + attrs.getValue(i))
        }
    }
}
