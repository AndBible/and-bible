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

package net.bible.sharedui.reading.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.control.link.BibleLink
import net.bible.android.control.search.SearchControl
import net.bible.android.database.IdType
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.epub.EpubBackend
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import net.bible.android.control.page.OrdinalRange

/** The URL schemes of links the reading view's text can carry. */
object UriConstants {
    const val SCHEME_DOWNLOAD = "download"
    const val SCHEME_ERROR = "ab-error"
    const val SCHEME_W = "ab-w"
    const val SCHEME_REFERENCE = "osis"
    const val MULTI_REFERENCE = "multi"
    const val SCHEME_MYNOTES = "my-notes"
    const val SCHEME_EPUB_REF = "epub-ref"
    const val SCHEME_STUDYPAD = "journal"
    const val SCHEME_FIND_ALL_OCCURRENCES = "ab-find-all"
    const val SCHEME_SWORD = "sword"
    const val SCHEME_STRONGS = "strongs"
    const val SCHEME_MORPHOLOGY = "morphology"
}

/**
 * Opens the links of a reading window's text (the former `BibleView.openLink`). A link with missing or
 * unusable parameters is not handled ([handle] returns false) instead of throwing.
 *
 * @param openEpubLink opens an EPUB target in [session]'s window (the owner's `ReadingBridge`)
 */
class ReadingLinkHandler(
    private val session: BibleWindowSession,
    private val openEpubLink: (SwordGenBook, EpubBackend, String, String) -> Unit,
) {
    private val linkControl get() = session.linkControl

    /** Opens [url]; true when it was handled, false for an unknown scheme or unusable parameters. */
    fun handle(url: String): Boolean {
        val link = parseLink(url)
        if (link == null) {
            Log.e(TAG, "Unsupported link $url")
            return false
        }
        return when (link.scheme) {
            UriConstants.SCHEME_W -> {
                val links = mutableListOf<BibleLink>()
                for ((paramName, values) in link.query) {
                    links.addAll(values.map { BibleLink(paramName, it) })
                }
                if (links.isEmpty()) return false
                if (links.size > 1) {
                    linkControl.openMulti(links)
                } else {
                    linkControl.loadApplicationUrl(links.first(), null)
                }
                true
            }
            UriConstants.SCHEME_MYNOTES -> {
                val ordinal = link.param("ordinal")?.toIntOrNull() ?: return false
                val v11n = link.param("v11n") ?: return false
                linkControl.openMyNotes(v11n, ordinal)
            }
            UriConstants.SCHEME_EPUB_REF -> {
                val bookStr = link.param("book") ?: return false
                val keyStr = link.param("toKey") ?: return false
                val idStr = link.param("toId") ?: return false

                val book = Books.installed().getBook(bookStr) as? SwordGenBook ?: return false
                val backend = book.backend as? EpubBackend ?: return false
                openEpubLink(book, backend, keyStr, idStr)
                true
            }
            UriConstants.MULTI_REFERENCE -> {
                val osisRefs = link.query["osis"] ?: return false
                val v11n = link.param("v11n")
                linkControl.openMulti(osisRefs.map { BibleLink("osis", it, v11n) })
            }
            UriConstants.SCHEME_STUDYPAD -> {
                val id = link.param("id") ?: return false
                val entryId = link.param("entryId") ?: link.param("bookmarkId")
                // The label lookup suspends, so launch; the context-menu window mode is read now because
                // the menu handler resets it as soon as this synchronous URL handler returns.
                val windowMode = linkControl.windowMode
                session.scope.launch(Dispatchers.Main) { linkControl.openStudyPad(IdType(id), IdType(entryId), windowMode) }
                true
            }
            UriConstants.SCHEME_REFERENCE -> {
                val osisRef = link.param("osis")
                val doc = link.param("doc")
                val ordinal = link.param("ordinal")
                val v11n = link.param("v11n")
                val forceDoc = link.booleanParam("force-doc", false)
                val book = doc?.let { Books.installed().getBook(it) }
                if (ordinal != null && ordinal.toIntOrNull() != null && book != null) {
                    val bookKey = try {
                        book.getKey(osisRef ?: return false)
                    } catch (e: NoSuchKeyException) {
                        return false
                    }.let { if (it is RangedPassage) it.first() else it }
                    linkControl.showLink(book, BookAndKey(bookKey, book, OrdinalRange(ordinal.toInt())))
                } else if (osisRef != null) {
                    linkControl.loadApplicationUrl(BibleLink("osis", osisRef.trim(), v11n, forceDoc = forceDoc), book)
                } else {
                    val contentRef = link.param("content") ?: return false
                    linkControl.loadApplicationUrl(BibleLink("content", contentRef.trim(), v11n, forceDoc = forceDoc), book)
                }
                true
            }
            UriConstants.SCHEME_FIND_ALL_OCCURRENCES -> {
                val type = link.param("type") ?: return false
                var name = link.param("name")?.lowercase() ?: return false
                if (!(name.startsWith("g") || name.startsWith("h"))) {
                    name = (type.firstOrNull() ?: return false) + name
                }
                linkControl.showAllOccurrences(name, SearchControl.SearchBibleSection.ALL)
                true
            }
            UriConstants.SCHEME_ERROR -> {
                linkControl.errorLink()
                true
            }
            UriConstants.SCHEME_DOWNLOAD -> {
                session.navigator.openDownloadSearch(link.param("initials"))
                true
            }
            UriConstants.SCHEME_SWORD -> {
                // Handle sword://MODULE/KEY format
                // URI like sword://KJV/Matt.5.3 or sword://MHC/Matt.5.3
                linkControl.loadApplicationUrl(BibleLink("sword", url), null)
                true
            }
            UriConstants.SCHEME_STRONGS -> {
                // Document-independent Strong's links: strongs://G2316, strongs://H430
                // Resolves to user's configured default Strong's dictionary via UriAnalyzer
                val ref = link.authority ?: link.schemeSpecificPart
                linkControl.loadApplicationUrl(BibleLink("strong", ref), null)
                true
            }
            UriConstants.SCHEME_MORPHOLOGY -> {
                // Document-independent morphology links: morphology://robinson/V-PAI-3S
                // Resolves to user's configured default morphology dictionary via UriAnalyzer
                val morphType = link.authority ?: ""
                val code = link.path?.trimStart('/') ?: ""
                linkControl.loadApplicationUrl(BibleLink(morphType, code), null)
                true
            }
            "http", "https" -> {
                session.navigator.openWebLink(url)
                true
            }
            else -> {
                Log.e(TAG, "Unsupported scheme ${link.scheme}")
                false
            }
        }
    }

    private companion object {
        const val TAG = "ReadingLinkHandler"
    }
}
