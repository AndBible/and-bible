/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import fi.sykero.kdom.input.build
import org.crosswire.common.util.KLocale
import org.crosswire.common.util.toKLocale
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import net.bible.sharedlogic.sync.bookNameMonitor
import net.bible.sharedlogic.sync.withMonitor

import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.android.control.speak.SpeakPlatform

import net.bible.sharedcore.log.Log
import androidx.collection.LruCache
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.service.common.useKdomBuilder
import net.bible.sharedcore.reading.ShareVersesEntry
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.service.device.speak.SpeakCommand
import net.bible.service.device.speak.SpeakCommandArray
import net.bible.service.device.speak.TextCommand
import net.bible.service.format.osistohtml.osishandlers.OsisToBibleSpeak
import net.bible.service.format.osistohtml.osishandlers.OsisToCanonicalTextSaxHandler
import net.bible.service.format.osistohtml.osishandlers.OsisToSpeakTextSaxHandler
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.epubBackend
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
import fi.sykero.kdom.Document
import fi.sykero.kdom.Element
import fi.sykero.kdom.Namespace
import fi.sykero.kdom.Text
import fi.sykero.kdom.sax.ContentHandler
import java.io.StringReader
import java.util.*
import kotlin.math.min


open class OsisError(xmlMessage: String, val stringMsg: String) : Exception(xmlMessage) {
    constructor(msg: String): this(msg, msg)
    val xml: Element = useKdomBuilder { it.build(StringReader("<div>$xmlMessage</div>")).rootElement }
}

class DocumentNotFound(xmlMessage: String, stringMsg: String) : OsisError(xmlMessage, stringMsg) {
    constructor(msg: String): this(msg, msg)
}
class JSwordError(message: String) : OsisError(message, message)

/** JSword facade
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class SwordContentFacade(
    private val strings: CoreStrings,
    private val environment: AppEnvironment,
    private val htmlToPlainText: HtmlToPlainText,
    private val advancedSpeakSettings: AdvancedSpeakSettings,
    private val speakPlatform: SpeakPlatform,
    internal val saxTextPolicy: net.bible.service.format.SaxTextPolicy,
) {
    companion object {
        private const val docCacheSize = 25
        private val osisFragmentCache = LruCache<String, Element>(docCacheSize)
        private val plainTextCache = LruCache<String, Map<Int, String>>(docCacheSize)

        /** top level method to fetch html from the raw document data
         */
        fun clearCaches() {
            osisFragmentCache.evictAll()
            plainTextCache.evictAll()
        }

        fun evictBook(initials: String) {
            val prefix = "$initials-"
            osisFragmentCache.snapshot().keys
                .filter { it.startsWith(prefix) }
                .forEach { osisFragmentCache.remove(it) }
            plainTextCache.snapshot().keys
                .filter { it.startsWith(prefix) }
                .forEach { plainTextCache.remove(it) }
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
                withMonitor(bibleNames) {
                    val orig = bibleNames.enableFuzzy
                    bibleNames.enableFuzzy = false
                    val k = getKey()
                    bibleNames.enableFuzzy = orig
                    k
                } ?:
                if (lang != LocaleProviderManager.getLocale().language) {
                    withMonitor(bibleNames) {
                        (LocaleProviderManager.getLocaleProvider() as? OverridableLocaleProvider)?.override = KLocale(lang)
                        val orig = bibleNames.enableFuzzy
                        bibleNames.enableFuzzy = false
                        val k = getKey()
                        bibleNames.enableFuzzy = orig
                        (LocaleProviderManager.getLocaleProvider() as? OverridableLocaleProvider)?.override = null
                        k
                    }
                } else null

            return key
        }
        /**
         * Render-cache key. An EPUB key also names its module owner: a render of a replaced owner that finishes
         * after its same-initials replacement was published must not be served for the replacement.
         * Keeps the "$initials-" prefix that [evictBook] relies on.
         */
        private fun renderCacheKey(book: Book?, key: Key?): String {
            val owner = book?.takeIf { it.isEpub }?.epubBackend?.state?.ownerGeneration
            return if (owner == null) "${book?.initials}-${key?.getOsisRef()}"
            else "${book.initials}-@$owner-${key?.getOsisRef()}"
        }

        @Throws(OsisError::class)
        fun readOsisFragment(book: Book?, key: Key?, strings: CoreStrings): Element {
            val cacheKey = renderCacheKey(book, key)

            if(book == null || key == null) {
                Log.e(TAG, "Key or book was null")
                throw OsisError(strings.errorNoContent)
            }

            osisFragmentCache.get(cacheKey)?.let { return it }

            when {
                Books.installed().getBook(book.initials) == null -> {
                    Log.w(TAG, "Book may have been uninstalled:$book")
                    val link = "<AndBibleLink href='download://?initials=${book.initials}'>${book.initials}</AndBibleLink>"
                    val errorXml = strings.documentNotInstalled(link)
                    val errorMsg = strings.documentNotInstalled(book.initials)
                    throw DocumentNotFound(errorXml, errorMsg)
                }
                !bookContainsAnyOf(book, key) -> {
                    Log.w(TAG, "KEY:" + key.getOsisID() + " not found in doc:" + book)
                    throw DocumentNotFound(strings.errorKeyNotInDocument(key.getName(), book.initials))
                }
            }

            return withMonitor(book) {
                osisFragmentCache.get(cacheKey) ?: let {
                    Log.d(TAG, "Cache key $cacheKey not found in cache, size now ${osisFragmentCache.size()}")
                    readXmlTextStandardJSwordMethod(book, key, strings)
                }.also {
                    osisFragmentCache.put(cacheKey, it)
                    Log.d(TAG, "Put to cache $cacheKey, size ${osisFragmentCache.size()}")
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
                            val osisRef = resolveRef(t, lang, KJVA)?.getOsisRef()
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

            for(content in net.bible.service.common.XmlQueries.textsOutsideNotes(frag)) {
                if(content.text.trim().isEmpty()) continue
                val parent = content.parentElement!!
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
        private fun readXmlTextStandardJSwordMethod(book: Book, key: Key, strings: CoreStrings): Element {
            Log.d(TAG, "readXmlTextStandardJSwordMethod: book=${book.initials}, key=${key.getOsisRef()}")
            return try {
                val data = BookData(book, key)
                val frag = data.osisFragment

                val bookCategory = book.bookCategory
                if (bookCategory == BookCategory.COMMENTARY && key.getCardinality() == 1) {
                    val verse = frag.getChild("verse")
                        ?: throw DocumentNotFound(
                            strings.errorKeyNotInDocument(key.getName(), book.initials)
                        )
                    val verseContent = verse.content.toList()
                    verse.removeContent()
                    frag.removeContent()
                    frag.addContent(verseContent)
                    addAnchors(frag, book.language.code)
                    frag
                } else if(bookCategory != BookCategory.BIBLE) {
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
                    Log.e(TAG, "Parsing error", e)
                else
                    Log.e(TAG, "Parsing error $e")
                throw JSwordError(strings.errorOccurred)
            }
        }

        fun getTextWithinOrdinalsAsString(book: Book, key: Key, ordinalRange: IntRange, strings: CoreStrings): List<String> {
            val map = cachedText(book, key, strings)
            return ordinalRange.mapNotNull { map[it] }
        }


        private fun cachedText(book: Book, key: Key, strings: CoreStrings): Map<Int, String> = withMonitor(this) {
            val cacheKey = renderCacheKey(book, key)
            plainTextCache.get(cacheKey) ?: run {
                val frag = try {
                    readOsisFragment(book, key, strings)
                } catch (e: OsisError) {
                    Log.e(TAG, "Could not read fragment", e)
                    return@run emptyMap()
                }
                val texts = net.bible.service.common.XmlQueries.bvasBelow(frag, xhtmlNamespace).associate { it.getAttribute("ordinal")!!.value.toInt() to getTextRecursively(it) }
                plainTextCache.put(cacheKey, texts)
                texts
            }
        }

        suspend fun ordinalRangeFor(book: Book, key: Key, strings: CoreStrings): IntRange {
            return if(book.isEpub) {
                ((book as SwordGenBook).backend as EpubBackend).getOrdinalRange(key)
            } else {
                val texts = cachedText(book, key, strings)
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
        fun getCanonicalText(book: Book?, key: Key?, compatibleOffsets: Boolean = false, strings: CoreStrings, saxTextPolicy: net.bible.service.format.SaxTextPolicy): String {
            return try {
                val data = BookData(book, key)
                val osissep = data.getSAXEventProvider()
                val osisHandler: ContentHandler = OsisToCanonicalTextSaxHandler(compatibleOffsets, saxTextPolicy)
                osissep.provideSAXEvents(osisHandler)
                osisHandler.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting text from book", e)
                strings.errorOccurred
            }
        }

        /**
         * Get text to be spoken without any markup.
         *
         * @param book
         * the book to use
         * @param key
         * a reference, appropriate for the book, of one or more entries
         */
        fun getTextToSpeak(book: Book, key: Key?, strings: CoreStrings, saxTextPolicy: net.bible.service.format.SaxTextPolicy): String {
            return try {
                val data = BookData(book, key)
                val osissep = data.getSAXEventProvider()
                val sayReferences = BookCategory.GENERAL_BOOK == book.bookCategory
                val osisHandler = OsisToSpeakTextSaxHandler(sayReferences, saxTextPolicy)
                osissep.provideSAXEvents(osisHandler)
                osisHandler.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting text from book", e)
                strings.errorOccurred
            }
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
            Log.i(TAG, "There are " + key!!.getCardinality() + " verses containing " + searchText)
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
            } catch (e: NullPointerException) {
                // JSword RawLDBackend can throw NPE when module data files are not properly initialized
                return false
            }
            return false
        }

        private const val TAG = "SwordContentFacade"

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
        selection: ShareSelection,
        showVerseNumbers: Boolean,
        advertiseApp: Boolean,
        showReference: Boolean = true,
        showReferenceAtFront: Boolean = false,
        abbreviateReference: Boolean = true,
        showNotes: Boolean = true,
        showVersion: Boolean = true,
        showSelectionOnly: Boolean = true,
        showEllipsis: Boolean = true,
        showQuotes: Boolean = true,
        separateVersesWithNewlines: Boolean = false,
    ): String {

        class VerseAndText(val verse: Verse, val text: String)

        fun normalizeWhitespace(text: String): String {
            return if (separateVersesWithNewlines) {
                text.replace(Regex("\\s+"), " ").trim()
            } else {
                text
            }
        }

        val book = (Books.installed().getBook(selection.bookInitials) as? SwordBook)
        val verseTexts = selection.verseRange?.map {
            val rawText = getCanonicalText(book, it, true, strings, saxTextPolicy).trimEnd()
            VerseAndText(it as Verse, normalizeWhitespace(rawText))
        }?: return ""
        val startOffset = selection.startOffset ?: 0
        var startVerse = verseTexts.first().text
        val endOffset = selection.endOffset ?: verseTexts.last().text.length

        val start = startVerse.slice(0 until min(startOffset, startVerse.length))

        var startVerseNumber = ""
        if (showVerseNumbers && verseTexts.size > 1 && (!showReferenceAtFront || separateVersesWithNewlines)) {
            startVerseNumber = "${selection.verseRange?.start?.verse}. "
        }
        if (showSelectionOnly && startOffset > 0 && showEllipsis) {
            startVerseNumber = "$startVerseNumber..."
        }
        val bookLocale = Books.installed().getBook(selection.bookInitials)?.language?.code?.let { Locale(it) }
        val isRtl = isRtlLanguage(bookLocale?.language)

        val versionText = if (showVersion) (Books.installed().getBook(selection.bookInitials)?.abbreviation) else ""
        val quotationStart = if (showQuotes) "“" else ""
        val quotationEnd = if (showQuotes) "”" else ""

        val reference = if (showReference) {
            if (abbreviateReference) {
                withMonitor(bookNameMonitor) {
                    val oldValue = BookName.isFullBookName()
                    BookName.setFullBookName(false)
                    try {
                        val verseRangeName = selection.verseRange?.getNameInLocale(null, bookLocale?.toKLocale())
                        "$verseRangeName"
                    } finally {
                        BookName.setFullBookName(oldValue)
                    }
                }
            } else {
                val verseRangeName = selection.verseRange?.getNameInLocale(null, bookLocale?.toKLocale())
                "$verseRangeName"
            }
        } else
            ""

        val advertise = if (shareAdvertAllowed(advertiseApp, environment.isDiscrete)) "\n\n${
            strings.verseShareAdvertise
        } (https://andbible.github.io)" else ""
        val notesOrig = selection.notes
        val notes =
            if (showNotes && notesOrig != null)
                "\n\n" + htmlToPlainText.convert(notesOrig)
            else
                ""


        val verseText = when {
            verseTexts.size == 1 -> {
                val end = startVerse.slice(endOffset until startVerse.length)
                val text = startVerse.slice(startOffset until min(endOffset, startVerse.length))
                val post = if (showSelectionOnly && end.isNotEmpty() && showEllipsis) "..." else ""
                if (!showSelectionOnly) {
                    val beforeText = start
                    val afterText = end
                    """$quotationStart$startVerseNumber$beforeText$text$afterText$quotationEnd"""
                } else {
                    "$quotationStart$startVerseNumber$text$post$quotationEnd"
                }
            }
            verseTexts.size > 1 -> {
                startVerse = startVerse.slice(startOffset until startVerse.length)
                val lastVerse = verseTexts.last()
                val endVerseNum = if (showVerseNumbers) "${lastVerse.verse.verse}. " else ""
                val endVerse = lastVerse.text.slice(0 until min(lastVerse.text.length, endOffset))
                val end = lastVerse.text.slice(endOffset until lastVerse.text.length)
                
                val text = if (separateVersesWithNewlines) {
                    var result = startVerse.trimEnd()
                    if (verseTexts.size > 2) {
                        val middleVerses = verseTexts.slice(1 until verseTexts.size - 1).map {
                            if (showVerseNumbers && it.verse.verse != 0) "${it.verse.verse}. ${it.text}" else it.text
                        }
                        for (middleVerse in middleVerses) {
                            result += "\n\n$middleVerse"
                        }
                    }
                    result += "\n\n$endVerseNum$endVerse"
                    result.replace(Regex("\n{3,}"), "\n\n")
                } else {
                    var middleVerses = if (verseTexts.size > 2) {
                        verseTexts.slice(1 until verseTexts.size - 1).joinToString(" ") {
                            if (showVerseNumbers && it.verse.verse != 0) "${it.verse.verse}. ${it.text}" else it.text
                        }
                    } else ""
                    if (middleVerses.isNotEmpty()) {
                        middleVerses += " "
                    }
                    "${startVerse.trimEnd()} ${middleVerses.trimStart()}$endVerseNum$endVerse"
                }
                
                val post = if (showSelectionOnly && end.isNotEmpty() && showEllipsis) "..." else ""

                if (!showSelectionOnly) {
                    val beforeText = start
                    val afterText = end
                    """$quotationStart$startVerseNumber$beforeText$text$afterText$post$quotationEnd"""
                } else {
                    "$quotationStart$startVerseNumber$text$post$quotationEnd"
                }
            }
            else -> throw RuntimeException("what")
        }
        return if (showReference) {
            if (showReferenceAtFront) {
                val refText = ("$reference $versionText").trim()
                val separator = if (separateVersesWithNewlines) "\n\n" else " "
                "$refText$separator$verseText$notes$advertise"
            } else {
                val refText = if (versionText == "") reference else "$reference, $versionText"
                if (separateVersesWithNewlines) {
                    "$verseText\n\n$refText$notes$advertise"
                } else {
                    "$verseText ($refText)$notes$advertise"
                }
            }
        } else {
            "$verseText$notes$advertise"
        }
    }

    /**
     * Resolves everything [ShareVersesOptions.buildText] needs from JSword/Android for
     * [selection], without baking in any of the ten toggle-controlled formatting choices from
     * [getSelectionText] — those move to [ShareVersesOptions.buildText] (Task 26, share sheet),
     * which has no JSword dependency and lives in `:sharedCore` so the sheet's preview can be
     * rebuilt on every switch toggle without a fresh JSword lookup.
     *
     * This is a SIBLING of [getSelectionText], not a replacement — that function is untouched and
     * still serves its one other caller, [net.bible.android.view.activity.page.Selection
     * .copyToClipboard]. [referenceAbbreviated]/[referenceFull] are both resolved here (mirroring
     * [getSelectionText]'s two `abbreviateReference` branches) so the sheet never needs to re-ask
     * JSword when the user flips that switch.
     */
    fun buildShareVersesInput(selection: ShareSelection): ShareVersesInput {
        val book = (Books.installed().getBook(selection.bookInitials) as? SwordBook)
        val verseRange = selection.verseRange
        val bookLocale = Books.installed().getBook(selection.bookInitials)?.language?.code?.let { Locale(it) }

        // Batch 6 A6: empty when discrete -- ShareVersesOptions.buildText appends no advert for a blank line.
        val advertiseText = if (environment.isDiscrete) "" else "${
            strings.verseShareAdvertise
        } (https://andbible.github.io)"
        val notesOrig = selection.notes
        val notesText = if (notesOrig != null) htmlToPlainText.convert(notesOrig) else null

        if (book == null || verseRange == null) {
            return ShareVersesInput(
                verses = emptyList(),
                startOffset = selection.startOffset,
                endOffset = selection.endOffset,
                referenceAbbreviated = "",
                referenceFull = "",
                versionAbbreviation = "",
                notesText = notesText,
                advertiseText = advertiseText,
                hasRange = (selection.startOffset != null && selection.endOffset != null),
            )
        }

        val verses = verseRange.map {
            val rawText = getCanonicalText(book, it, true, strings, saxTextPolicy).trimEnd()
            ShareVersesEntry((it as Verse).verse, rawText)
        }

        // Mirrors getSelectionText's abbreviateReference=true branch: forces non-full book names
        // for the duration of the call, under the same lock, then restores whatever the app-wide
        // setting was.
        val referenceAbbreviated = withMonitor(bookNameMonitor) {
            val oldValue = BookName.isFullBookName()
            BookName.setFullBookName(false)
            try {
                "${verseRange.getNameInLocale(null, bookLocale?.toKLocale())}"
            } finally {
                BookName.setFullBookName(oldValue)
            }
        }
        // Mirrors getSelectionText's abbreviateReference=false branch: no forcing, just whatever
        // the app-wide BookName.isFullBookName() setting already is. Evaluated right after the
        // synchronized block above (no intervening suspension), so it sees the SAME ambient value
        // getSelectionText would have used had it taken this branch instead.
        val referenceFull = "${verseRange.getNameInLocale(null, bookLocale?.toKLocale())}"

        return ShareVersesInput(
            verses = verses,
            startOffset = selection.startOffset,
            endOffset = selection.endOffset,
            referenceAbbreviated = referenceAbbreviated,
            referenceFull = referenceFull,
            versionAbbreviation = Books.installed().getBook(selection.bookInitials)?.abbreviation ?: "",
            notesText = notesText,
            advertiseText = advertiseText,
            hasRange = (selection.startOffset != null && selection.endOffset != null),
        )
    }

    private fun getSpeakCommandsForVerse(settings: SpeakSettings, book: Book, key: Key): ArrayList<SpeakCommand> = try {
        val data = BookData(book, key)
        val frag = data.getOsisFragment(false)
        val doc = frag.document ?: Document(frag)
        val osissep: SAXEventProvider = JDOMSAXEventProvider(doc)
        val osisHandler: ContentHandler = OsisToBibleSpeak(settings, speakPlatform.phrasesFor(book.language.code), advancedSpeakSettings, speakPlatform.maxSpeechInputLength, saxTextPolicy)
        osissep.provideSAXEvents(osisHandler)
        (osisHandler as OsisToBibleSpeak).speakCommands
    } catch (e: Exception) {
        Log.e(TAG, "Error getting text from book", e)
        ArrayList()
    }

    fun getBibleSpeakCommands(settings: SpeakSettings, book: SwordBook, verse: Verse?): SpeakCommandArray {
        val v11nConverter = VersificationConverter()
        val verse_ = v11nConverter.convert(verse, book.versification)
        val lst = SpeakCommandArray(speakPlatform.maxSpeechInputLength)
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
        val arr = SpeakCommandArray(speakPlatform.maxSpeechInputLength)
        arr.addAll(
            getTextWithinOrdinalsAsString(book,
                actualKey,
                key.ordinal!!.start .. key.ordinal.start, strings
            )
                .map {
                    TextCommand(it.replace("\n", " "))
                })
        return arr
    }


}

/**
 * Fix batch 6 A6 (surface 7): the "Shared using AndBible" advert is never appended when [discrete],
 * whatever the `share_show_add` preference (default true) says.
 */
internal fun shareAdvertAllowed(userWantsAdvert: Boolean, discrete: Boolean): Boolean = userWantsAdvert && !discrete
