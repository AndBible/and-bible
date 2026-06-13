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

package net.bible.service.sword

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jdom2.Content
import org.jdom2.Element
import org.jdom2.Text

/** Output format for content tools (getVerseContent, getCommentaries, getDictionaryEntry). */
@Serializable
enum class ContentFormat {
    @SerialName("text") TEXT,
    @SerialName("xml") XML
}

/**
 * Converts OSIS XML elements to readable plain text with light semantic annotations.
 *
 * Preserves all visible content (text the user sees on screen) while stripping XML markup,
 * Strong's numbers, morphology codes, and other metadata. Useful for LLM consumption where
 * raw OSIS XML wastes tokens unnecessarily.
 */
object OsisToPlainText {

    private val SKIP_ELEMENTS = setOf("milestone", "chapter")
    /**
     * Converts a JDOM2 Element (typically an OSIS fragment) to readable plain text.
     *
     * @param injectAnchors When true, inserts `[§N]` markers at every BVA (sentence) boundary
     *   using BVA ordinals. These markers allow LLMs to reference specific positions within
     *   commentary text via `sword://MODULE/KEY#oN` URLs.
     */
    fun convert(element: Element, injectAnchors: Boolean = false): String {
        val sb = StringBuilder()
        walkElement(element, sb, injectAnchors)
        return sb.toString()
            .replace(Regex(" +\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Converts an OSIS osisRef attribute value to a sword:// URL.
     * Module-qualified refs like "MHC:Matt.5.3" become "sword://MHC/Matt.5.3".
     * Plain refs like "Matt.5.3" become "sword:///Matt.5.3".
     */
    /**
     * URI-encodes an OSIS reference, preserving unreserved characters (RFC 3986)
     * and OSIS-specific delimiters (dots, hyphens, colons).
     */
    private fun encodeOsisRef(ref: String): String {
        val sb = StringBuilder(ref.length)
        for (c in ref) {
            if (c.isLetterOrDigit() || c in "-._~") {
                sb.append(c)
            } else {
                for (byte in c.toString().toByteArray(Charsets.UTF_8)) {
                    sb.append("%%%02X".format(byte.toInt() and 0xFF))
                }
            }
        }
        return sb.toString()
    }

    internal fun osisRefToUrl(osisRef: String): String {
        val colonIndex = osisRef.indexOf(':')
        if (colonIndex > 0) {
            val prefix = osisRef.substring(0, colonIndex)
            if (prefix[0].isUpperCase()) {
                val key = osisRef.substring(colonIndex + 1)
                return "sword://${encodeOsisRef(prefix)}/${encodeOsisRef(key)}"
            }
        }
        return "sword:///${encodeOsisRef(osisRef)}"
    }

    private fun walkElement(
        element: Element,
        sb: StringBuilder,
        injectAnchors: Boolean = false
    ) {
        val name = element.name

        // Skip invisible elements entirely
        if (name in SKIP_ELEMENTS) return
        // Skip BibleView-specific elements (x- prefixed custom elements)
        if (name.startsWith("x-")) return

        // BVA (BibleViewAnchor) elements: emit anchor marker for every BVA when enabled
        if (name == "BVA") {
            if (injectAnchors) {
                val ordinal = element.getAttributeValue("ordinal")
                if (ordinal != null) {
                    sb.append("[§$ordinal] ")
                }
            }
            // Always process BVA children (the actual text content)
            for (content: Content in element.content) {
                when (content) {
                    is Text -> sb.append(content.text)
                    is Element -> walkElement(content, sb, injectAnchors)
                }
            }
            return
        }

        // Reference elements need special handling: collect child text first for markdown link
        if (name == "reference") {
            val osisRef = element.getAttributeValue("osisRef")
            if (osisRef != null) {
                val innerSb = StringBuilder()
                for (child: Content in element.content) {
                    when (child) {
                        is Text -> innerSb.append(child.text)
                        is Element -> walkElement(child, innerSb, injectAnchors)
                    }
                }
                sb.append("[${innerSb}](${osisRefToUrl(osisRef)})")
                return
            }
        }

        // Element opening
        when (name) {
            "title" -> sb.append("\n## ")
            "note" -> sb.append(" [Footnote: ")
            "transChange" -> sb.append("*")
            "hi" -> {
                val type = element.getAttributeValue("type")
                sb.append(if (type == "bold") "**" else "*")
            }
            "verse" -> {
                val osisId = element.getAttributeValue("osisID")
                val verseNum = osisId?.substringAfterLast(".")
                if (verseNum != null) sb.append("$verseNum. ")
            }
            "q" -> {
                val marker = element.getAttributeValue("marker")
                if (marker != null) sb.append(marker)
            }
            "l", "lb" -> sb.append("\n")
            "p", "div", "list", "lg" -> sb.append("\n")
            "item" -> sb.append("\n- ")
            "row" -> sb.append("\n")
        }

        // Process children
        for (content: Content in element.content) {
            when (content) {
                is Text -> sb.append(content.text)
                is Element -> walkElement(content, sb, injectAnchors)
            }
        }

        // Element closing
        when (name) {
            "title" -> sb.append("\n")
            "note" -> sb.append("]")
            "transChange" -> sb.append("*")
            "hi" -> {
                val type = element.getAttributeValue("type")
                sb.append(if (type == "bold") "**" else "*")
            }
            "p", "div", "list", "lg" -> sb.append("\n")
            "row" -> sb.append("\n")
            "cell" -> sb.append(" ")
        }
    }
}
