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
package net.bible.service.sword

import android.text.TextUtils
import android.util.LayoutDirection
import android.util.Log
import android.util.LruCache
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.MyLocaleProvider
import net.bible.android.activity.R
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.activity.page.Selection
import net.bible.service.common.Logger
import net.bible.service.common.htmlToSpan
import net.bible.service.common.useSaxBuilder
import net.bible.service.device.speak.SpeakCommand
import net.bible.service.device.speak.SpeakCommandArray
import net.bible.service.device.speak.TextCommand
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.service.format.osistohtml.osishandlers.OsisToCanonicalTextSaxHandler
import net.bible.service.format.osistohtml.osishandlers.OsisToSpeakTextSaxHandler
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.epub.xhtmlNamespace
import org.crosswire.common.xml.JDOMSAXEventProvider
import org.crosswire.common.xml.SAXEventProvider
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookData
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleNames
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.VersificationConverter
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.Text
import org.jdom2.filter.Filters
import org.jdom2.input.SAXBuilder
import org.jdom2.xpath.XPathFactory
import org.xml.sax.ContentHandler
import java.io.StringReader
import java.util.*
import kotlin.math.min


open class OsisError(xmlMessage: String, val stringMsg: String) : Exception(xmlMessage) {
    constructor(msg: String): this(msg, msg)
    val xml: Element = useSaxBuilder { it.build(StringReader("<div>$xmlMessage</div>")).rootElement }
}

class DocumentNotFound(xmlMessage: String, stringMsg: String) : OsisError(xmlMessage, stringMsg) {
    constructor(msg: String): this(msg, msg)
}
class JSwordError(message: String) : OsisError(message, message)

/** JSword facade
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

object SwordContentFacade {
    private const val docCacheSize = 100
    private val osisFragmentCache = LruCache<String, Element>(docCacheSize)
    private val plainTextCache = LruCache<String, Map<Int, String>>(docCacheSize)

    /** top level method to fetch html from the raw document data
     */
    fun clearCaches() {
        osisFragmentCache.evictAll()
        plainTextCache.evictAll()
    }

    private val dashesRe = Regex("""\p{Pd}""")
    fun resolveRef(searchRef_: String, lang: String, v11n: Versification): Key? {
        val searchRef = searchRef_.replace(dashesRe, "-")
        val bibleNames = BibleNames.instance()

        fun getKey(): Key? {
            val k =
                try { PassageKeyFactory.instance().getKey(v11n, searchRef) }
                catch (e: Exception) { null }
            if(k != null &&
                try {
                    k.getRangeAt(0, RestrictionType.NONE)?.start?.chapter
                } catch (e: Exception) {
                    Log.e(TAG, "k.getRangeAt exception", e)
                    null
                } == 0
            )  {
                return null
            }
            return k
        }

        val key =
            synchronized(bibleNames) {
                val orig = bibleNames.enableFuzzy
                bibleNames.enableFuzzy = false
                val k = getKey()
                bibleNames.enableFuzzy = orig
                k
            } ?:
            if (lang != MyLocaleProvider.userLocale.language) {
                synchronized(bibleNames) {
                    MyLocaleProvider.override = Locale(lang)
                    val orig = bibleNames.enableFuzzy
                    bibleNames.enableFuzzy = false
                    val k = getKey()
                    bibleNames.enableFuzzy = orig
                    MyLocaleProvider.override = null
                    k
                }
            } else null

        return key
    }
    @Throws(OsisError::class)
    fun readOsisFragment(book: Book?, key: Key?): Element {
        val cacheKey = "${book?.initials}-${key?.osisRef}"

        if(book == null || key == null) {
            Log.e(TAG, "Key or book was null")
            throw OsisError(application.getString(R.string.error_no_content))
        }

        osisFragmentCache.get(cacheKey)?.let { return it }

        when {
            Books.installed().getBook(book.initials) == null -> {
                Log.w(TAG, "Book may have been uninstalled:$book")
                val link = "<AndBibleLink href='download://?initials=${book.initials}'>${book.initials}</AndBibleLink>"
                val errorXml = application.getString(R.string.document_not_installed, link)
                val errorMsg = application.getString(R.string.document_not_installed, book.initials)
                throw DocumentNotFound(errorXml, errorMsg)
            }
            !bookContainsAnyOf(book, key) -> {
                Log.w(TAG, "KEY:" + key.osisID + " not found in doc:" + book)
                throw DocumentNotFound(application.getString(R.string.error_key_not_in_document2, key.name, book.initials))
            }
        }

        return synchronized(book) {
            osisFragmentCache.get(cacheKey) ?: let {
                Log.e(TAG, "Cache key $cacheKey not found in cache, size now ${osisFragmentCache.size()}")
                readXmlTextStandardJSwordMethod(book, key)
            }.also {
                osisFragmentCache.put(cacheKey, it)
                Log.i(TAG, "Put to cache $cacheKey, size ${osisFragmentCache.size()}")
            }
        }
    }

    private const val TARGET_MAX_LENGTH = 150

    private val regexCache = LruCache<Int, Regex>(1000)
    private fun getCutRegex(targetLength: Int): Regex =
        regexCache.get(targetLength)
            ?: Regex("""(.{$targetLength}\p{Z}\p{L}+\p{Z}+)(\p{L}+\p{Z}.*)""")
                .also { regexCache.put(targetLength, it) }
    private fun cutLongSentences(p: String): List<String> {
        val newPieces = mutableListOf<String>()
        if (p.length > TARGET_MAX_LENGTH) {
            val targetLength = p.length/2
            val re = getCutRegex(targetLength)
            val m = re.find(p)
            if(m == null) {
                newPieces.add(p)
            }
            else {
                for(part in listOf(p.slice(0 until m.range.first) + m.groupValues[1], m.groupValues[2])) {
                    if (part.length > TARGET_MAX_LENGTH*1.1) {
                        newPieces.addAll(cutLongSentences(part))
                    } else {
                        newPieces.add(part)
                    }
                }
            }
        } else {
            newPieces.add(p)
        }
        return newPieces
    }

    /*
       IMPORTANT! This may not be changed ever! If it is changed, non-bible bookmark locations are messed up.
       Split sentences as well as possible, but avoid splitting bible references.

        before: before sentence ending punctuation marker, we allow 2 digits or non-digit.
          We want to avoid matching for example "1. John", but we can safely allow
          "... sentence ending with Matt 12. ..."
        marker:
          m1: after marker there can be also ending quotation marker
          m2: marker could be also dash.
        after: After sentence there must be a real word starting. Before word can be
          some punctuations, like quotation marks etc.
     */
    private val splitMatch = Regex(""+
        // group 1: before marker
        """((\d{2,}|\D)""" +
        // marker itself
        """(([.,;:!?。，；]["'\p{Pf}]?\p{Z}+)|(\p{Z}*\p{Pd}\p{Z}*)))"""+
        // group 6: after marker
        """(["'¡¿\p{Pi}]?\p{L})"""
    )

    fun splitSentences(text: String): List<String> {
        val matches = splitMatch.findAll(text)
        val pieces = mutableListOf<String>()
        var lastStartPosition = 0
        val currentPiece = StringBuilder()

        fun addStr(s: String) {
            if(s.length > TARGET_MAX_LENGTH) {
                pieces.addAll(cutLongSentences(s))
            } else {
                pieces.add(s)
            }
        }

        for(m in matches) {
            currentPiece.append(text.slice(lastStartPosition until m.range.first) + m.groupValues[1])
            addStr(currentPiece.toString())
            currentPiece.clear()
            currentPiece.append(m.groupValues[6])
            lastStartPosition = m.range.last + 1
        }
        currentPiece.append(text.slice(lastStartPosition   until text.length))
        if (currentPiece.isNotEmpty()) {
            addStr(currentPiece.toString())
        }
        return pieces
    }

    // Detect bible references, like 1 John 2:3-4, 4:5-6:7, 4-5
    val bibleRefRe = Regex("" +
        // Beginning of bible reference. 1 John 2:3
        """(((\d\.?\p{Z}+)?\p{Lu}\p{L}+\.?)\p{Z}+((\d+)(:\d+)?)(\p{Pd}\d+(:\d+)?)?)"""+
        // Continuation, separated by comma. First: ,4:5-6:7 Second: , 4-5
        """([,;]?\p{Z}*(\d+:\d+|\d+)(?!\.?\p{Z}*\p{L})(\p{Pd}\d+(:\d+)?)?)*"""
    )
    fun bibleRefSplit(text: String): List<Pair<String, Boolean>> {
        val matches = bibleRefRe.findAll(text)
        val pieces = mutableListOf<Pair<String, Boolean>>()
        var lastStartPosition = 0

        for(m in matches) {
            val prevText = text.slice(lastStartPosition until m.range.first)
            if(prevText.isNotEmpty()) {
                pieces.add(Pair(prevText, false))
            }
            pieces.add(Pair(m.groupValues[0], true))
            lastStartPosition = m.range.last + 1
        }
        val leftover = text.slice(lastStartPosition   until text.length)
        if (leftover.isNotEmpty()) {
            pieces.add(Pair(leftover, false))
        }
        return pieces
    }


    private val textQuery = XPathFactory.instance().compile(".//text()[not(ancestor::note)]", Filters.text())
    private val ns =  Namespace.getNamespace("http://www.w3.org/1999/xhtml")

    // IMPORTANT! The logic of this function not be changed ever! If it is changed, non-bible bookmark locations are messed up.
    fun addAnchors(frag: Element, lang: String, isEpub: Boolean = false): Int {
        var ordinal = 0
        val startTime = System.currentTimeMillis()

        fun addContent(span: Element, textContent: String) {
            if(isEpub) {
                for ((t, isRef) in bibleRefSplit(textContent)) {
                    if (!isRef) {
                        span.addContent(Text(t))
                    } else {
                        val osisRef = resolveRef(t, lang, KJVA)?.osisRef
                        if (osisRef == null) {
                            Log.e(TAG, "Failed parsing ref $t")
                            span.addContent(Text(t))
                        } else {
                            val refNode = Element("reference", ns)
                            refNode.addContent(Text(t))
                            refNode.setAttribute("osisRef", osisRef)
                            span.addContent(refNode)
                        }
                    }
                }
            } else {
                span.addContent(Text(textContent))
            }
        }

        for(content in textQuery.evaluate(frag)) {
            if(content.text.trim().isEmpty()) continue
            val parent = content.parentElement
            val textContents = splitSentences(content.text)
            if (textContents.isNotEmpty()) {
                var pos = parent.indexOf(content)
                content.detach()
                for (textContent in textContents) {
                    val bva = Element("BVA", ns) // BibleViewAnchor.vue
                    bva.setAttribute("ordinal", "${ordinal++}")
                    addContent(bva, textContent)
                    parent.addContent(pos++, bva)
                }
            }
        }

        val delta = System.currentTimeMillis() - startTime
        Log.i(TAG, "Parsing took ${delta/1000.0} seconds")
        return ordinal
    }
    @Throws(OsisError::class)
    private fun readXmlTextStandardJSwordMethod(book: Book, key: Key): Element {
        log.debug("Using standard JSword to fetch document data")
        return try {
            val data = BookData(book, key)
            val frag = data.osisFragment

            if (book.bookCategory == BookCategory.COMMENTARY && key.cardinality == 1) {
                val verse = frag.getChild("verse")
                    ?: throw DocumentNotFound(
                        application.getString(
                            R.string.error_key_not_in_document2,
                            key.name,
                            book.initials
                        )
                    )
                val verseContent = verse.content.toList()
                verse.removeContent()
                frag.removeContent()
                frag.addContent(verseContent)
                addAnchors(frag, book.language.code)
                frag
            } else if(book.bookCategory != BookCategory.BIBLE) {
                if(!book.isEpub) {
                    addAnchors(frag, book.language.code)
                }

                frag
            } else {
                frag
            }
        } catch (e: OsisError) {
            throw e
        } catch (e: Throwable) {
            if (e is Exception)
                log.error("Parsing error", e)
            else
                log.error("Parsing error $e")
            throw JSwordError(application.getString(R.string.error_occurred))
        }
    }

    fun getTextWithinOrdinalsAsString(book: Book, key: Key, ordinalRange: IntRange): List<String> {
        val map = cachedText(book, key)
        return ordinalRange.mapNotNull { map[it] }
    }

    private val bvaQuery = XPathFactory.instance().compile(".//ns:BVA", Filters.element(), null, xhtmlNamespace)

    private fun cachedText(book: Book, key: Key): Map<Int, String> = synchronized(this) {
        val cacheKey = "${book.initials}-${key.osisRef}"
        plainTextCache.get(cacheKey) ?: run {
            val frag = try {
                readOsisFragment(book, key)
            } catch (e: OsisError) {
                Log.e(TAG, "Could not read fragment", e)
                return@run emptyMap()
            }
            val texts = bvaQuery.evaluate(frag).associate { it.getAttribute("ordinal").value.toInt() to getTextRecursively(it) }
            plainTextCache.put(cacheKey, texts)
            texts
        }
    }

    fun ordinalRangeFor(book: Book, key: Key): IntRange {
        return if(book.isEpub) {
            ((book as SwordGenBook).backend as EpubBackend).getOrdinalRange(key)
        } else {
            val texts = cachedText(book, key)
            val keys = texts.keys
            if (keys.isEmpty()) return 0..0
            val first = keys.min()
            val last = keys.max()
            first..last
        }
    }

    private fun getTextRecursively(element: Element): String {
        val textBuilder = StringBuilder()
        for(c in element.content) {
            if(c is Element) {
                textBuilder.append(getTextRecursively(c))
            }
            if(c is Text) {
                textBuilder.append(c.text)
            }
        }
        return textBuilder.toString()
    }
    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     *
     * @param book
     * the book to use
     * @param key
     * a reference, appropriate for the book, of one or more entries
     */
    @Throws(NoSuchKeyException::class, BookException::class, OsisError::class)
    open fun getCanonicalText(book: Book?, key: Key?, compatibleOffsets: Boolean = false): String {
        return try {
            val data = BookData(book, key)
            val osissep = data.saxEventProvider
            val osisHandler: ContentHandler = OsisToCanonicalTextSaxHandler(compatibleOffsets)
            osissep.provideSAXEvents(osisHandler)
            osisHandler.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text from book", e)
            application.getString(R.string.error_occurred)
        }
    }

    /**
     * Gets the user's selected text, with markup options
     *
     * @param selection
     * verse or verse range Selection
     *
     * @param showVerseNumbers
     * if true and selection contains multiple verses, verse numbers are included
     *
     * @param advertiseApp
     * if true, an ad for the app is included
     *
     * @param showReference
     * if true, verse reference is included
     *
     * @param showReferenceAtFront
     * if true and showReference is true, the verse reference appears before the verse text
     *
     * @param abbreviateReference
     * if true and showReference is true, the verse reference is abbreviated
     *
     * @param showNotes
     * if true, any notes the user has added to any part of the selection is included
     *
     * @param showVersion
     * if true, the Bible translation version is included
     *
     * @param showSelectionOnly
     * if true, the selection is based on the user's exact text selection, not a verse or range of verses
     *
     * @param showEllipsis
     * if true and showSelectionOnly is true, an ellipsis appears where the user's exact text selection truncates verse
     * text
     *
     * @param showQuotes
     * if true, quotation marks surround the returned String
     *
     * @return
     * the selected text, with markup according to options
     */
    fun getSelectionText(
        selection: Selection,
        showVerseNumbers: Boolean,
        advertiseApp: Boolean,
        showReference: Boolean = true,
        showReferenceAtFront: Boolean = false,
        abbreviateReference: Boolean = true,
        showNotes: Boolean = true,
        showVersion: Boolean = true,
        showSelectionOnly: Boolean = true,
        showEllipsis: Boolean = true,
        showQuotes: Boolean = true
    ): String {

        class VerseAndText(val verse: Verse, val text: String)

        val book = selection.swordBook
        val verseTexts = selection.verseRange?.map {
            VerseAndText(it as Verse, getCanonicalText(book, it, true).trimEnd())
        }?: return ""
        val startOffset = selection.startOffset ?: 0
        var startVerse = verseTexts.first().text
        val endOffset = selection.endOffset ?: verseTexts.last().text.length

        val start = startVerse.slice(0 until min(startOffset, startVerse.length))

        var startVerseNumber = ""
        if (showVerseNumbers && !showReferenceAtFront && verseTexts.size > 1) {
            startVerseNumber = "${selection.verseRange?.start?.verse}. "
        }
        if (showSelectionOnly && startOffset > 0 && showEllipsis) {
            startVerseNumber = "$startVerseNumber..."
        }
        val bookLocale = selection.book?.language?.code?.let { Locale(it) }
        val isRtl = TextUtils.getLayoutDirectionFromLocale(bookLocale) == LayoutDirection.RTL

        val versionText = if (showVersion) (selection.book?.abbreviation) else ""
        val quotationStart = if (showQuotes) "“" else ""
        val quotationEnd = if (showQuotes) "”" else ""

        val reference = if (showReference) {
            if (abbreviateReference) {
                synchronized(BookName::class.java) {
                    val oldValue = BookName.isFullBookName()
                    BookName.setFullBookName(false)
                    val verseRangeName = selection.verseRange?.getNameInLocale(null, bookLocale)
                    BookName.setFullBookName(oldValue)
                    "$verseRangeName"
                }
            } else {
                val verseRangeName = selection.verseRange?.getNameInLocale(null, bookLocale)
                "$verseRangeName"
            }
        } else
            ""

        val advertise = if (advertiseApp) "\n\n${
            application.getString(
                R.string.verse_share_advertise,
                application.getString(R.string.app_name_long)
            )
        } (https://andbible.github.io)" else ""
        val notesOrig = selection.notes
        val notes =
            if (showNotes && notesOrig != null)
                "\n\n" + htmlToSpan(notesOrig)
            else
                ""


        val verseText = when {
            verseTexts.size == 1 -> {
                val end = startVerse.slice(endOffset until startVerse.length)
                val text = startVerse.slice(startOffset until min(endOffset, startVerse.length))
                val post = if (showSelectionOnly && end.isNotEmpty() && showEllipsis) "..." else ""
                if (!showSelectionOnly) """$quotationStart$startVerseNumber$start$text$end$quotationEnd""" else "$quotationStart$startVerseNumber$text$post$quotationEnd"
            }
            verseTexts.size > 1 -> {
                startVerse = startVerse.slice(startOffset until startVerse.length)
                val lastVerse = verseTexts.last()
                val endVerseNum = if (showVerseNumbers) "${lastVerse.verse.verse}. " else ""
                val endVerse = lastVerse.text.slice(0 until min(lastVerse.text.length, endOffset))
                val end = lastVerse.text.slice(endOffset until lastVerse.text.length)
                var middleVerses = if (verseTexts.size > 2) {
                    verseTexts.slice(1 until verseTexts.size - 1).joinToString(" ") {
                        if (showVerseNumbers && it.verse.verse != 0) "${it.verse.verse}. ${it.text}" else it.text
                    }
                } else ""
                if (middleVerses.isNotEmpty()) {
                    middleVerses += " "
                }
                val text = "${startVerse.trimEnd()} ${middleVerses.trimStart()}$endVerseNum$endVerse"
                val post = if (showSelectionOnly && end.isNotEmpty() && showEllipsis) "..." else ""

                if (!showSelectionOnly) """$quotationStart$startVerseNumber$start$text$end$post$quotationEnd""" else "$quotationStart$startVerseNumber$text$post$quotationEnd"
            }
            else -> throw RuntimeException("what")
        }
        return if (showReference) {
            if (showReferenceAtFront) {
                "${("$reference $versionText").trim()} $verseText$notes$advertise"
            } else {
                "$verseText (${if (versionText == "") reference else "$reference, $versionText"})$notes$advertise"
            }
        } else {
            "$verseText$notes$advertise"
        }
    }

    private fun getSpeakCommandsForVerse(settings: SpeakSettings, book: Book, key: Key): ArrayList<SpeakCommand> = try {
        val data = BookData(book, key)
        val frag = data.getOsisFragment(false)
        val doc = frag.document ?: Document(frag)
        val osissep: SAXEventProvider = JDOMSAXEventProvider(doc)
        val osisHandler: ContentHandler = OsisToBibleSpeak(settings, book.language.code)
        osissep.provideSAXEvents(osisHandler)
        (osisHandler as OsisToBibleSpeak).speakCommands
    } catch (e: Exception) {
        Log.e(TAG, "Error getting text from book", e)
        ArrayList()
    }

    fun getBibleSpeakCommands(settings: SpeakSettings, book: SwordBook, verse: Verse?): SpeakCommandArray {
        val v11nConverter = VersificationConverter()
        val verse_ = v11nConverter.convert(verse, book.versification)
        val lst = SpeakCommandArray()
        if (verse_.verse == 1) {
            lst.addAll(
                getSpeakCommandsForVerse(
                    settings, book,
                    Verse(book.versification, verse_.book, verse_.chapter, 0)
                )
            )
        }
        lst.addAll(getSpeakCommandsForVerse(settings, book, verse_))
        return lst
    }

    fun getGenBookSpeakCommands(key: BookAndKey): SpeakCommandArray {
        val book = key.document!!
        var actualKey = key.key
        if(actualKey is VerseRange) {
            actualKey = actualKey.start
        }
        val arr = SpeakCommandArray()
        arr.addAll(
            getTextWithinOrdinalsAsString(book,
                actualKey,
                key.ordinal!!.start .. key.ordinal.start
            )
                .map {
                    TextCommand(it.replace("\n", " "))
                })
        return arr
    }

    /**
     * Get text to be spoken without any markup.
     *
     * @param book
     * the book to use
     * @param key
     * a reference, appropriate for the book, of one or more entries
     */
    fun getTextToSpeak(book: Book, key: Key?): String {
        return try {
            val data = BookData(book, key)
            val osissep = data.saxEventProvider
            val sayReferences = BookCategory.GENERAL_BOOK == book.bookCategory
            val osisHandler = OsisToSpeakTextSaxHandler(sayReferences)
            osissep.provideSAXEvents(osisHandler)
            osisHandler.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting text from book", e)
            application.getString(R.string.error_occurred)
        }
    }

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     *
     * @param book
     * the book to use
     * @param reference
     * a reference, appropriate for the book, of one or more entries
     */
    @Throws(BookException::class, NoSuchKeyException::class)
    fun getPlainText(book: Book?, reference: String?): String {
        var plainText = ""
        try {
            if (book != null) {
                val key = book.getKey(reference)
                plainText = getPlainText(book, key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting plain text", e)
        }
        return plainText
    }

    /**
     * Get just the canonical text of one or more book entries without any
     * markup.
     *
     * @param book
     * the book to use
     * @param key
     * a reference, appropriate for the book, of one or more entries
     */
    @Throws(BookException::class, NoSuchKeyException::class)
    fun getPlainText(book: Book?, key: Key?): String {
        var plainText = ""
        try {
            if (book != null) {
                plainText = getCanonicalText(book, key)
                // trim any preceeding spaces that make the final output look uneven
                plainText = plainText.trim { it <= ' ' }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting plain text", e)
        }
        return plainText
    }

    @Throws(BookException::class)
    fun search(bible: Book, searchText: String?): Key {
        // example of fetching Strongs ref - only works with downloaded indexes!
        // Book book = getDocumentByInitials("KJV");
        // Key key1 = book.find("strong:h3068");
        // System.out.println("h3068 result count:"+key1.getCardinality());
        Log.i(TAG, "Searching:$bible Search term:$searchText")
        // This does a standard operator search. See the search
        // documentation for more examples of how to search
        val key = bible.find(searchText) //$NON-NLS-1$
        Log.i(TAG, "There are " + key.cardinality + " verses containing " + searchText)
        return key
    }

    /**
     * When checking a book contains a chapter SwordBook returns false if verse 0 is not in the chapter so this method compensates for that
     *
     * This can be removed if SwordBook.contains is converted to be containsAnyOf as discussed in JS-273
     */
    private fun bookContainsAnyOf(book: Book, key: Key): Boolean {
        try {
            if (book.contains(key)) {
                return true
            }
            for (aKey in key) {
                if (book.contains(aKey)) {
                    return true
                }
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            return false
        }
        return false
    }

    private const val TAG = "SwordContentFacade"

    // set to false for testing
    private var isAndroid = true //CommonUtils.isAndroid();
    private val log = Logger(SwordContentFacade::class.java.name)
    fun setAndroid(isAndroid: Boolean) {
        this.isAndroid = isAndroid
    }
}
