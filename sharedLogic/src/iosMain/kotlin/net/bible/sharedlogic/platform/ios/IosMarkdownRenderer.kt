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
package net.bible.sharedlogic.platform.ios

import net.bible.sharedcore.platform.MarkdownRenderer
import net.bible.sharedlogic.markdown.HtmlToXhtml
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * iOS [MarkdownRenderer] on JetBrains markdown 0.7.18 (GFM flavour), post-processed by [HtmlToXhtml].
 *
 * Known, accepted difference from Android (commonmark-java, see `MarkdownToXhtml` and the L1b phase 0 note there):
 * no newlines between block elements, GFM table `align` dropped on the first column plus
 * `class="intellij-row-even"` rows, raw HTML such as `<tag-like>` escaped, and an unknown named entity is escaped
 * instead of resolved. Stored pages are Markdown, so the difference is in the rendering only, never in stored data.
 */
class IosMarkdownRenderer : MarkdownRenderer {
    private val flavour = GFMFlavourDescriptor()

    override fun toXhtml(markdown: String): String {
        val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        return HtmlToXhtml.toXhtml(HtmlGenerator(markdown, tree, flavour).generateHtml())
    }
}
