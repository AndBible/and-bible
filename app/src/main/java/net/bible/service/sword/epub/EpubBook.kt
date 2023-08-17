/*
 * Copyright (c) 2022-2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.epub

import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.database.EpubDatabase
import net.bible.android.database.EpubFragment
import net.bible.android.database.epubMigrations
import net.bible.android.misc.elementToString
import net.bible.service.common.useSaxBuilder
import net.bible.service.common.useXPathInstance
import net.bible.service.sword.SwordContentFacade.addAnchors
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.KeyType
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.Backend
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.filter.Filters
import java.io.File
import java.net.URLDecoder

fun getConfig(
    initials: String,
    abbreviation: String,
    description: String,
    language: String,
    about: String,
    path: String,
): String = """
[$initials]
Description=$description
Abbreviation=$abbreviation
Category=${BookCategory.GENERAL_BOOK.name}
AndBibleEpubModule=1
AndBibleEpubDir=$path
Lang=$language
Version=0.0
Encoding=UTF-8
SourceType=OSIS
ModDrv=RawGenBook
About=$about
"""

const val TAG = "EpubBook"

val dbFactory = if(application.isRunningTests) null else RequerySQLiteOpenHelperFactory()
fun getEpubDatabase(name: String): EpubDatabase =
    Room.databaseBuilder(
        application, EpubDatabase::class.java, name
    )
        .allowMainThreadQueries()
        .addMigrations(*epubMigrations)
        .openHelperFactory(dbFactory)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .build()

class EpubSwordDriver: AbstractBookDriver() {
    override fun getBooks(): Array<Book> {
        return emptyArray()
    }

    override fun getDriverName(): String {
        return "EpubSwordDriver"
    }

    override fun isDeletable(book: Book): Boolean {
        return true
    }

    override fun delete(book: Book) {
        ((book as? SwordGenBook)?.backend as? EpubBackend)?.delete()
        Books.installed().removeBook(book)
    }
}

private val re = Regex("[^a-zA-z0-9]")
private fun sanitizeModuleName(name: String): String = name.replace(re, "_")

private val hrefRe = Regex("""^([^#]+)?#?(.*)$""")
private fun getFileAndId(href: String): Pair<String, String>? {
    val m = hrefRe.matchEntire(href)?: return null
    return m.groupValues[1] to m.groupValues[2]
}


class EpubBackendState(private val epubDir: File): OpenFileState {
    constructor(sqliteFile: File, metadata: SwordBookMetaData): this(sqliteFile) {
        this._metadata = metadata
    }

    override fun close() {}

    private var _metadata: SwordBookMetaData? = null

    private val dcNamespace = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/")
    private val epubNamespace = Namespace.getNamespace("ns", "http://www.idpf.org/2007/opf")
    private val containerNamespace = Namespace.getNamespace("ns", "urn:oasis:names:tc:opendocument:xmlns:container")
    private val tocNamespace = Namespace.getNamespace("ns", "http://www.daisy.org/z3986/2005/ncx/")
    private val xhtmlNamespace = Namespace.getNamespace("ns", "http://www.w3.org/1999/xhtml")
    private val urlRe = Regex("""^https?://.*""")

    private val metaInfoFile = File(epubDir, "META-INF/container.xml")
    private val metaInfo = useSaxBuilder {  it.build(metaInfoFile) }
    private val contentFileName = useXPathInstance { xp ->
        xp.compile("//ns:rootfile", Filters.element(), null, containerNamespace)
            .evaluateFirst(metaInfo)
            .getAttribute("full-path").value
    }

    private val contentXmlFile = File(epubDir, contentFileName)
    val rootFolder: File = contentXmlFile.parentFile!!

    private val content = useSaxBuilder { it.build(contentXmlFile) }
    private val fileToId = useXPathInstance { xp ->
        xp.compile("//ns:manifest/ns:item", Filters.element(), null, epubNamespace)
            .evaluate(content).associate {
                val fileName = URLDecoder.decode(it.getAttribute("href").value, "UTF-8")
                fileName to it.getAttribute("id").value
            }
    }

    private val idToFile = fileToId.entries.associate { it.value to it.key }

    private val tocFile = useXPathInstance { xp ->
        xp.compile(
            "//ns:manifest/ns:item[@media-type='application/x-dtbncx+xml']",
            Filters.element(),
            null,
            epubNamespace
        )
            .evaluateFirst(content)?.getAttribute("href")?.value?.run { File(rootFolder, this) }
    }

    private val toc = tocFile?.run {useSaxBuilder { it.build(this) } }

    private val fileToTitle = toc?.run { useXPathInstance { xp ->
        xp.compile("//ns:navPoint/ns:content", Filters.element(), null, tocNamespace)
            .evaluate(this)
            .associate {
                val textElem =
                    useXPathInstance { xp2 ->
                        xp2.compile("../ns:navLabel/ns:text", Filters.element(), null, tocNamespace)
                            .evaluateFirst(it)
                    }
                val fileAndId = getFileAndId(it.getAttribute("src").value)
                val fileName = fileAndId?.first?.let { URLDecoder.decode(it, "UTF-8") }
                fileName to textElem.text
            }
    } }

    private fun queryMetadata(key: String): String? = useXPathInstance { xp ->
        xp.compile ("//dc:$key", Filters.element(), null, dcNamespace)
            .evaluateFirst(content)?.value
    }
    private fun queryContent(expression: String): List<Element> = useXPathInstance { xp ->
        xp.compile(expression, Filters.element(), null, epubNamespace).evaluate(content)
    }
    private fun queryFirst(expression: String): Element = useXPathInstance { xp ->
        xp.compile(expression, Filters.element(), null, epubNamespace).evaluateFirst(content)
    }
    private fun fileForOriginalKey(key: Key): File {
        val fileName = queryFirst("//ns:manifest/ns:item[@id='${key.osisRef}']").getAttribute("href").value
        return File(rootFolder, fileName)
    }
    private fun styleSheetsForOriginalKey(key: Key): List<File> {
        val file = fileForOriginalKey(key)
        val htmlRoot = useSaxBuilder {  it.build(file) }.rootElement
        val head = htmlRoot.children.find { it.name == "head" }!!
        val parentFolder = file.parentFile

        return head.children
            .filter { it.name == "link" && it.getAttribute("type")?.value == "text/css" }
            .mapNotNull { it.getAttribute("href").value?.let {File(parentFolder, it) } }
    }

    private fun getFragmentForOptimizedKey(key: Key): EpubFragment = dao.getFragment(key.osisRef.toLong())

    fun styleSheetsForOptimizedKey(key: Key): List<File> {
        val frag = getFragmentForOptimizedKey(key)
        return styleSheetsForOriginalKey(getOriginalKey(frag.originalHtmlFileName))
    }

    private fun readOriginal(key: Key): Pair<Document, Int> {
        val file = fileForOriginalKey(key)
        val parentFolder = file.parentFile!!

        fun epubSrc(src: String): String {
            val f = File(parentFolder, src)
            val filePath = f.toRelativeString(rootFolder)
            return "/epub/${bookMetaData.initials}/$filePath"
        }

        fun fixReferences(e: Element): Element {
            for(img in useXPathInstance { xp -> xp.compile("//ns:img", Filters.element(), null, xhtmlNamespace).evaluate(e) }) {
                val src = img.getAttribute("src").value
                val finalSrc = epubSrc(src)
                img.setAttribute("src", finalSrc)
            }
            for(a in useXPathInstance { xp -> xp.compile("//ns:a", Filters.element(), null, xhtmlNamespace).evaluate(e) }) {
                val href = a.getAttribute("href")?.value?: continue
                val fileAndId = getFileAndId(href)
                if(fileAndId != null && !urlRe.matches(href)) {
                    val fileStr = URLDecoder.decode(fileAndId.first, "UTF-8")
                    val fileName = if(fileStr.isEmpty()) null else File(parentFolder, fileStr).toRelativeString(rootFolder)
                    val id: String = fileName?.let {fileToId[it] }?: key.osisRef
                    a.name = "epubRef"
                    a.setAttribute("to-key", id)
                    a.setAttribute("to-id", fileAndId.second)
                } else {
                    a.name = "epubA"
                }
            }
            return e
        }

        return useSaxBuilder { it.build(file) }.rootElement.children
            .find { it.name == "body" }!!
            .run {
                name = "div"
                val processed = fixReferences(this)
                val maxOrdinal = addAnchors(processed, bookMetaData.language.code, true)
                val resultDoc = Document(processed.clone())
                Pair(resultDoc, maxOrdinal)
            }
    }
    fun indexOfOriginalKey(that: Key): Int =
        queryContent("//ns:spine/ns:itemref")
            .map { it.getAttribute("idref").value }.indexOf(that.osisRef)
    private fun getOriginalKey(idRef: String): Key {
        val keyName = fileToTitle?.let {f2t -> idToFile[idRef]?.let { f2t[it] } } ?: application.getString(R.string.nameless)
        return DefaultLeafKeyList(keyName, idRef)
    }

    private fun getFragmentKey(fragment: EpubFragment): Key {
        val filePath = idToFile.let { it[fragment.originalHtmlFileName] }
        val keyName = filePath?.let { fp -> fileToTitle?.let {it[fp] } } ?: application.getString(R.string.nameless)
        return DefaultLeafKeyList(keyName, "${fragment.id}")
    }

    fun readOptimized(key: Key): String {
        val frag = getFragmentForOptimizedKey(key)
        return String(File(cacheDir, frag.cacheFileName).readBytes())
    }

    fun indexOfOptimizedKey(key: Key): Int = optimizedKeys.indexOf(key)

    // TODO: remove
    fun getFromOriginalIndex(index: Int): Key =
        queryContent("//ns:spine/ns:itemref")[index]
            .run { getOriginalKey(getAttribute("idref").value) }

    fun getFromOptimizedIndex(index: Int): Key = optimizedKeys[index]

    val originalCardinality: Int get() = queryContent("//ns:package/ns:spine/ns:itemref").size

    val optimizedCardinality get() = optimizedKeys.size

    private val originalKeys: List<Key>
        get() = queryContent("//ns:spine/ns:itemref")
            .map { getOriginalKey(it.getAttribute("idref").value) }

    val optimizedKeys: List<Key> get() = dao.fragments().map { getFragmentKey(it) }

    // TODO: no need initials...
    private val cacheDir get() = File(epubDir,  "cache/${bookMetaData.initials}")

    private val dbFilename = "epub-${bookMetaData.initials}.sqlite3"

    init {
        val appDbFile = application.getDatabasePath(dbFilename)
        val epubDbFile = File(epubDir, dbFilename)
        //appDbFile.delete()
        //epubDbFile.delete()

        if(!epubDbFile.exists()) {
            optimizeEpub()
            appDbFile.copyTo(epubDbFile)
        } else {
            if(!appDbFile.exists())  {
                epubDbFile.copyTo(appDbFile)
            }
        }
    }
    private val readDb = getEpubDatabase(dbFilename)
    private val dao = readDb.epubDao()

    private fun optimizeEpub() {
        val ordinalsPerFragment = 100

        fun getSplitPoint(element: Document, splitPoint: Int): Element? = useXPathInstance { xp ->
            xp.compile(
                "//*[descendant::BVA[@ordinal='$splitPoint'] and (following-sibling::ns:p or preceding-sibling::ns:p or self::ns:p)]",
                Filters.element(), null, xhtmlNamespace
            ).evaluateFirst(element)
        }

        fun removeSiblingsBefore(ele: Element) {
            val parent = ele.parentElement?: return
            val idx = parent.indexOf(ele)
            for(i in 0 until idx) {
                parent.removeContent(0)
            }
        }

        fun removeSiblingsAfter(ele: Element) {
            val parent = ele.parentElement ?:return
            val idx = parent.indexOf(ele) + 1
            val removeAmount = parent.contentSize - idx
            for(i in 0 until removeAmount) {
                parent.removeContent(idx)
            }
        }

        // Extract document that contains splitOrdinal1, but paragraph containing splitOrdinal2 will be left out
        fun extractBetween(orig: Document, splitOrdinal1: Int?, splitOrdinal2: Int?): Document? {
            val doc = orig.clone()
            val splitElem1 = splitOrdinal1?.let { getSplitPoint(doc, it) }
            val splitElem2 = splitOrdinal2?.let { getSplitPoint(doc, it) }

            // TODO: check that this test works in JDOM2
            if(splitElem1 == splitElem2) return null // contained inside same paragraph

            if(splitElem1 != null) {
                removeSiblingsBefore(splitElem1)
                var parent = splitElem1.parentElement
                while (parent != null) {
                    removeSiblingsBefore(parent)
                    parent = parent.parentElement
                }
            }
            if(splitElem2 != null) {
                // Let's this element as well as all content after this element
                var parent = splitElem2.parentElement
                while (parent?.parentElement != null) {
                    removeSiblingsAfter(parent)
                    parent = parent.parentElement
                }
                removeSiblingsAfter(splitElem2)
                splitElem2.detach()

            } // JOSTAIN SYYSTÄ LOPPUUN TULEE  (MELKEIN) KOKONAINEN DOKUMENTTI
            return doc
        }

        fun splitIntoN(doc: Document, ordinalRange: IntRange, n: Int): List<Document> {
            if(n == 0) return listOf(doc)
            val first = ordinalRange.first
            val pieceLength = (ordinalRange.last - first) / n

            val firstFrag = extractBetween(doc, null, first+pieceLength) ?: return listOf(doc)

            var splitPoint1 = first+pieceLength
            var splitPoint2 = first+pieceLength * 2

            val docs = mutableListOf<Document>()
            docs.add(firstFrag)
            while(splitPoint2 < ordinalRange.last) {
                val extractedDoc = extractBetween(doc, splitPoint1, splitPoint2)
                if(extractedDoc != null) {
                    splitPoint1 = splitPoint2
                    docs.add(extractedDoc)
                }
                splitPoint2 += pieceLength
            }
            if(splitPoint1 < ordinalRange.last) {
                val lastFrag = extractBetween(doc, splitPoint1, null)
                lastFrag?.let { docs.add(it) }
            }
            return docs
        }

        fun getOrdinalRange(doc: Document): IntRange {
            val bvas = useXPathInstance { xp ->
                xp.compile(
                    "//BVA",
                    Filters.element(), null, xhtmlNamespace
                ).evaluate(doc)
            }
            if(bvas.size == 0) return 0..0
            return bvas.first().getAttribute("ordinal").intValue ..
                bvas.last().getAttribute("ordinal").intValue
        }

        fun splitIntoFragments(originalKey: Key): List<EpubFragment> {
            val (origElement, maxOrdinal) = readOriginal(originalKey)
            return splitIntoN(origElement, 0..maxOrdinal, maxOrdinal/ordinalsPerFragment).map {
                val ordinalRange = getOrdinalRange(it)
                EpubFragment(originalHtmlFileName = originalKey.osisRef, ordinalRange.first, ordinalRange.last).apply {
                    element=it.rootElement
                }
            }
        }

        fun writeFragment(frag: EpubFragment) {
            val f = File(cacheDir, frag.cacheFileName)
            val strContent = elementToString(frag.element!!)
            f.outputStream().use {
                it.write(strContent.toByteArray())
            }
        }

        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        val writeDb = getEpubDatabase(dbFilename)
        val writeDao = writeDb.epubDao()

        for(k in originalKeys) {
            Log.i(TAG, "${epubDir.name}: optimizing ${k.osisRef}")

            val fragments = splitIntoFragments(k)
            val ids = writeDao.insert(*fragments.toTypedArray())
            for((id, frag) in ids.zip(fragments)) {
                frag.id = id
            }
            for(frag in fragments) {
                Log.i(TAG, "${epubDir.name}: writing frag ${frag.id}")
                writeFragment(frag)
                frag.element = null // clear up memory
            }
        }
    }

    fun getResource(resourcePath: String) = File(rootFolder, resourcePath)

    override fun getBookMetaData(): SwordBookMetaData {
        return _metadata?: synchronized(this) {
            val initials = "Epub-" + sanitizeModuleName(File(epubDir.path).name)
            val title = queryMetadata("title") ?: epubDir.name
            val description = queryMetadata("description")?: epubDir.name //TODO: should de-encode html stuff
            val abbreviation = title //.slice(0 .. min(5, title.length - 1))
            val language = queryMetadata("language") ?: "en"
            val conf = getConfig(
                initials = initials,
                abbreviation = abbreviation,
                description = title,
                about = description,
                language = language,
                path = epubDir.toRelativeString(SharedConstants.modulesDir)
            )
            Log.i(TAG, "Creating EpubBook metadata $initials, $description $language")
            val metadata = SwordBookMetaData(conf.toByteArray(), initials)
            metadata.driver = EpubSwordDriver()
            this._metadata = metadata
            return@synchronized metadata
        }
    }

    override fun releaseResources() = close()

    private var _lastAccess: Long  = 0L
    override fun getLastAccess(): Long = _lastAccess
    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
    }

    fun delete() {
        epubDir.deleteRecursively()
        readDb.close()
        application.deleteDatabase(dbFilename)
    }
}

class EpubBackend(val state: EpubBackendState, metadata: SwordBookMetaData): AbstractKeyBackend<EpubBackendState>(metadata) {
    override fun initState(): EpubBackendState = state
    override fun readIndex(): Key {
        val key = DefaultKeyList(null, bookMetaData.name)
        for(i in iterator()) {
            key.addAll(i)
        }
        return key
    }
    override fun getCardinality(): Int = state.optimizedCardinality

    override fun iterator(): MutableIterator<Key> =
        state.optimizedKeys
            .toMutableList()
            .iterator()
    override fun get(index: Int): Key {
        return state.getFromOptimizedIndex(index)
    }
    override fun indexOf(that: Key): Int {
        return state.indexOfOptimizedKey(that)
    }
    fun getResource(resourcePath: String): File = state.getResource(resourcePath)
    fun styleSheets(key: Key): List<File> = state.styleSheetsForOptimizedKey(key)
    override fun readRawContent(state: EpubBackendState, key: Key): String {
        return state.readOptimized(key)
    }
    fun delete() = state.delete()
}

val epubBookType = object: BookType("EpubBook", BookCategory.GENERAL_BOOK, KeyType.TREE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book =
        SwordGenBook(sbmd, backend)
    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val state = EpubBackendState(File(sbmd.location), sbmd)
        return EpubBackend(state, sbmd)
    }
}

fun addEpubBook(file: File) {
    if(!(file.canRead() && file.isDirectory)) return
    val state = EpubBackendState(file)
    val metadata = state.bookMetaData
    if(Books.installed().getBook(metadata.initials) != null) return
    val backend = EpubBackend(state, metadata)
    val book = SwordGenBook(metadata, backend)

    if(IndexManagerFactory.getIndexManager().isIndexed(book)) {
        metadata.indexStatus = IndexStatus.DONE
    } else {
        metadata.indexStatus = IndexStatus.UNDONE
    }

    Books.installed().addBook(book)
}

fun addManuallyInstalledEpubBooks() {
    val dir = File(SharedConstants.modulesDir, "epub")
    dir.mkdirs()
    if(!(dir.isDirectory && dir.canRead())) return

    for(f in dir.listFiles()!!) {
        addEpubBook(f)
    }
}

val Book.isManuallyInstalledEpub get() = bookMetaData.getProperty("AndBibleEpubModule") != null
val Book.isEpub get() = isManuallyInstalledEpub || ((bookMetaData as? SwordBookMetaData)?.bookType == epubBookType)
val Book.epubDir get() = bookMetaData.getProperty("AndBibleEpubDir")
