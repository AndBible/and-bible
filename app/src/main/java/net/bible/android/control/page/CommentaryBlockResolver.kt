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
package net.bible.android.control.page

import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.useSaxBuilder
import net.bible.service.sword.OsisToPlainText
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.StringReader

/**
 * Renders a commentary entry's raw OSIS XML for [key], or null when the entry is empty
 * (blank or `<div/>`). Shared by GetCommentariesTool and [renderComparable] so the
 * read+blank-check is defined once.
 */
internal fun renderCommentaryFragmentXml(book: Book, key: Key): String? {
    val outputter = XMLOutputter(Format.getRawFormat())
    val xml = outputter.outputString(SwordContentFacade.readOsisFragment(book, key))
    return if (xml.isBlank() || xml == "<div/>") null else xml
}

/**
 * Produces a stable comparison key for a commentary verse: the entry rendered to plain text,
 * or null if empty. Plain text (not raw XML) is compared so that entries which are semantically
 * identical but differ only in per-verse OSIS metadata (e.g. across a chapter boundary) still
 * collapse into one block — mirroring the dedup behaviour in GetCommentariesTool.
 */
internal fun renderComparable(book: Book, verse: Verse): String? {
    val xml = renderCommentaryFragmentXml(book, verse) ?: return null
    val text = OsisToPlainText.convert(useSaxBuilder { it.build(StringReader(xml)).rootElement }).trim()
    return text.ifBlank { null }
}

/** A run of consecutive verses sharing identical rendered content. [content] is null for an empty verse. */
data class CommentaryBlock(val start: Verse, val end: Verse, val content: String?)

/** Abstracts verse traversal + rendering so the resolver can be unit-tested without a real book. */
interface CommentaryWalker {
    /** Next verse, or null at the end of the traversable range. */
    fun next(verse: Verse): Verse?
    /** Previous verse, or null at the start of the traversable range. */
    fun prev(verse: Verse): Verse?
    /** Comparison content for [verse], or null when it has no commentary entry. */
    fun render(verse: Verse): String?
}

/**
 * Resolves commentary content blocks lazily. Empty verses act as block separators and are
 * skipped during navigation. A small per-resolver cache avoids re-rendering a verse touched
 * by more than one walk; callers create a fresh resolver per navigation action.
 */
class CommentaryBlockResolver(private val walker: CommentaryWalker) {
    private val cache = HashMap<Verse, String?>()

    /**
     * Renders [verse] to its comparison content, or null. A verse the module cannot render
     * (e.g. a chapter-intro "verse 0", or a key outside the module) throws from the SWORD layer;
     * such a verse is treated as having no content (null), i.e. a block separator that navigation
     * skips — never propagated, so a verse-by-verse walk never crashes the caller. This mirrors
     * the exception handling in GetCommentariesTool's per-verse rendering.
     */
    private fun render(verse: Verse): String? = cache.getOrPut(verse) {
        try { walker.render(verse) } catch (e: Exception) { null }
    }

    /**
     * Expands the block containing [verse]. If [verse] itself is empty, returns a single-verse
     * block with null content (no snapping to a neighbouring block). Blocks are delimited by
     * empty verses (whose rendered content is null), so two spans with coincidentally identical
     * content separated by an empty verse remain distinct blocks.
     */
    fun resolveBlock(verse: Verse): CommentaryBlock {
        val content = render(verse) ?: return CommentaryBlock(verse, verse, null)
        var start = verse
        while (true) {
            val p = walker.prev(start) ?: break
            if (render(p) == content) start = p else break
        }
        var end = verse
        while (true) {
            val n = walker.next(end) ?: break
            if (render(n) == content) end = n else break
        }
        return CommentaryBlock(start, end, content)
    }

    /** Start verse of the next non-empty block after [blockEnd], or null at the end. */
    fun nextBlockStart(blockEnd: Verse): Verse? {
        var v = blockEnd
        while (true) {
            v = walker.next(v) ?: return null
            if (render(v) != null) return v
        }
    }

    /** Start verse of the previous non-empty block before [blockStart], or null at the start. */
    fun prevBlockStart(blockStart: Verse): Verse? {
        var v = blockStart
        while (true) {
            v = walker.prev(v) ?: return null
            if (render(v) != null) return resolveBlock(v).start
        }
    }
}

/** Production walker backed by JSword traversal + plain-text rendering. */
class SwordCommentaryWalker(
    private val book: AbstractPassageBook,
    private val bibleTraverser: BibleTraverser,
) : CommentaryWalker {
    override fun next(verse: Verse): Verse? =
        try { bibleTraverser.getNextVerse(book, verse).takeIf { it.ordinal > verse.ordinal } } catch (e: Exception) { null }
    override fun prev(verse: Verse): Verse? =
        try { bibleTraverser.getPrevVerse(book, verse).takeIf { it.ordinal < verse.ordinal } } catch (e: Exception) { null }
    override fun render(verse: Verse): String? = renderComparable(book, verse)
}
