/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.format.osistohtml.osishandlers

import net.bible.service.common.Logger
import net.bible.service.format.osistohtml.TextWriter
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Convert OSIS input into Canonical text (used when creating search index)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class OsisSaxHandler : DefaultHandler() {
    // debugging
    private var isDebugMode = false
    val writer: TextWriter = TextWriter()

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    /* @Override */
    override fun toString(): String {
        return writer.html
    }

    protected fun getName(eName: String?, qName: String): String {
        return if (eName != null && eName.length > 0) {
            eName
        } else {
            qName // not namespace-aware
        }
    }

    protected open fun write(s: String?): Boolean {
        return writer.write(s)
    }

    /** check the value of the specified attribute and return true if same as checkvalue
     *
     * @param attrs
     * @param attrName
     * @param checkValue
     * @return
     */
    protected fun isAttrValue(attrs: Attributes?, attrName: String?, checkValue: String): Boolean {
        if (attrs == null) {
            return false
        }
        val value = attrs.getValue(attrName)
        return checkValue == value
    }

    protected fun debug(name: String, attrs: Attributes?, isStartTag: Boolean) {
        if (isDebugMode) {
            write("*$name")
            if (attrs != null) {
                for (i in 0 until attrs.length) {
                    var aName = attrs.getLocalName(i) // Attr name
                    if ("" == aName) aName = attrs.getQName(i)
                    write(" ")
                    write(aName + "=\"" + attrs.getValue(i) + "\"")
                }
            }
            write("*\n")
        }
    }

    fun setDebugMode(isDebugMode: Boolean) {
        this.isDebugMode = isDebugMode
    }

    protected fun reset() {
        writer.reset()
    }

    companion object {
        private val log = Logger("OsisSaxHandler")
    }

}
