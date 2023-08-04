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
import net.bible.android.SharedConstants
import net.bible.android.misc.elementToString
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.KeyType
import org.crosswire.jsword.book.basic.AbstractBook
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
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.filter.Filters
import org.jdom2.input.SAXBuilder
import org.jdom2.input.sax.XMLReaders
import org.jdom2.xpath.XPathFactory
import java.io.File
import kotlin.math.min

fun getConfig(
    initials: String,
    abbreviation: String,
    description: String,
    language: String,
    about: String,
): String = """
[$initials]
Description=$description
Abbreviation=$abbreviation
Category=${BookCategory.GENERAL_BOOK.name}
AndBibleEpubModule=1
Lang=$language
Version=0.0
Encoding=UTF-8
SourceType=OSIS
ModDrv=RawGenBook
About=$about
"""

const val TAG = "MyBibleBook"

private val re = Regex("[^a-zA-z0-9]")

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
        // TODO
        //book.epubDir.delete()
        Books.installed().removeBook(book)
    }
}

fun sanitizeModuleName(name: String): String = name.replace(re, "_")

class EpubBackendState(val epubDir: File): OpenFileState {
    constructor(sqliteFile: File, metadata: SwordBookMetaData): this(sqliteFile) {
        this.metadata = metadata
    }

    override fun close() {}

    private var metadata: SwordBookMetaData? = null
    val saxBuilder = SAXBuilder(XMLReaders.NONVALIDATING)
    val xPathInstance = XPathFactory.instance()

    private val dcNamespace = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/")
    private val epubNamespace = Namespace.getNamespace("ns", "http://www.idpf.org/2007/opf")
    private val contentXmlFile = File(epubDir, "OEBPS/content.opf")
    private val content = saxBuilder.build(contentXmlFile)
    val fileToId = xPathInstance.compile("//ns:manifest/ns:item", Filters.element(), null, epubNamespace)
        .evaluate(content).associate { it.getAttribute("href").value to it.getAttribute("id").value
    }

    private fun queryMetadata(key: String): String? =
        xPathInstance.compile("//dc:$key", Filters.element(), null, dcNamespace).evaluateFirst(content)?.value
    fun queryContent(expression: String): List<Element> =
        xPathInstance.compile(expression, Filters.element(), null, epubNamespace).evaluate(content)

    fun queryFirst(expression: String): Element =
        xPathInstance.compile(expression, Filters.element(), null, epubNamespace).evaluateFirst(content)
    override fun getBookMetaData(): SwordBookMetaData {
        return metadata?: synchronized(this) {
            val initials = "Epub-" + sanitizeModuleName(File(epubDir.path).name)
            val title = queryMetadata("title") ?: "-"
            val description = queryMetadata("description")?: "-" // TODO: should de-encode html stuff
            val abbreviation = title.slice(0 .. min(5, title.length - 1))
            val language = queryMetadata("language") ?: "en"
            val conf = getConfig(
                initials = initials,
                abbreviation = abbreviation,
                description = title,
                about = description,
                language = language,
            )
            Log.i(TAG, "Creating EpubBook metadata $initials, $description $language")
            val metadata = SwordBookMetaData(conf.toByteArray(), initials)
            metadata.driver = EpubSwordDriver()
            this.metadata = metadata
            return@synchronized metadata
        }
    }

    override fun releaseResources() = close()

    private var _lastAccess: Long  = 0L
    override fun getLastAccess(): Long = _lastAccess
    override fun setLastAccess(lastAccess: Long) {
        _lastAccess = lastAccess
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
    override fun getCardinality(): Int =
        state.queryContent("//ns:package/ns:spine/ns:itemref").size

    override fun iterator(): MutableIterator<Key> =
        state.queryContent("//ns:spine/ns:itemref")
            .map { DefaultLeafKeyList(it.getAttribute("idref").value) }
            .toMutableList()
            .iterator()

    override fun get(index: Int): Key =
        state.queryContent("//ns:spine/ns:itemref")[index]
            .run { DefaultLeafKeyList(getAttribute("idref").value) }

    override fun indexOf(that: Key): Int =
        state.queryContent("//ns:spine/ns:itemref")
            .map { it.getAttribute("idref").value }.indexOf(that.name)

    private fun fileForKey(key: Key): File {
        val fileName = state.queryFirst("//ns:manifest/ns:item[@id='${key.name}']").getAttribute("href").value
        return File(state.epubDir, "OEBPS/$fileName")
    }

    fun getResource(resourcePath: String): File = File(state.epubDir, "OEBPS/$resourcePath")

    fun styleSheets(key: Key): List<File> {
        val file = fileForKey(key)
        val htmlRoot = state.saxBuilder.build(file).rootElement
        val head = htmlRoot.children.find { it.name == "head" }!!
        val parentFolder = file.parentFile

        return head.children
            .filter { it.name == "link" && it.getAttribute("type").value == "text/css" }
            .map { File(parentFolder, it.getAttribute("href").value) }
    }

    private val xhtmlNamespace = Namespace.getNamespace("x", "http://www.w3.org/1999/xhtml")
    private val hrefRe = Regex("""^([^#]+)?#?(.*)$""")
    override fun readRawContent(state: EpubBackendState, key: Key): String {
        val file = fileForKey(key)
        val parentFolder = file.parentFile!!
        val root = parentFolder.parentFile!!

        fun epubSrc(src: String): String {
            val f = File(parentFolder, src)
            val filePath = f.toRelativeString(root)
            return "/epub/${state.bookMetaData.initials}/$filePath"
        }

        fun fixReferences(e: Element): Element {
            for(img in state.xPathInstance.compile("//x:img", Filters.element(), null, xhtmlNamespace).evaluate(e)) {
                val src = img.getAttribute("src").value
                val finalSrc = epubSrc(src)
                img.setAttribute("src", finalSrc)
            }
            for(a in state.xPathInstance.compile("//x:a", Filters.element(), null, xhtmlNamespace).evaluate(e)) {
                val href = a.getAttribute("href")?.value?: continue
                val m = hrefRe.matchEntire(href)
                if(m != null) {
                    val fileStr = m.groupValues[1]
                    val id = if(fileStr.isEmpty()) key.name else state.fileToId[File(parentFolder, fileStr).toRelativeString(root)]
                    a.name = "epubRef"
                    a.setAttribute("to-key", id)
                    a.setAttribute("to-id", m.groupValues[2])
                }
            }
            return e
        }

        return state.saxBuilder.build(file).rootElement.children
            .find { it.name == "body" }!!
            .run { elementToString(fixReferences(this)) }
    }
}

val epubBookType = object: BookType("EpubBook", BookCategory.GENERAL_BOOK, KeyType.TREE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book =
        SwordGenBook(sbmd, backend)
    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val state = EpubBackendState(File(sbmd.location), sbmd)
        return EpubBackend(state, sbmd)
    }
}

fun addEpubBook(file: File): AbstractBook? {
    if(!(file.canRead() && file.isDirectory)) return null
    val state = EpubBackendState(file)
    val metadata = state.bookMetaData
    val backend = EpubBackend(state, metadata)
    val book = SwordGenBook(metadata, backend)

    if(IndexManagerFactory.getIndexManager().isIndexed(book)) {
        metadata.indexStatus = IndexStatus.DONE
    } else {
        metadata.indexStatus = IndexStatus.UNDONE
    }

    Books.installed().addBook(book)
    return book
}

fun addManuallyInstalledEpubBooks() {
    val dir = File(SharedConstants.modulesDir, "epub")
    dir.mkdirs()
    if(!(dir.isDirectory && dir.canRead())) return

    for(f in dir.listFiles()!!) {
        addEpubBook(f)
    }
}

val Book.isEpubBook get() = bookMetaData.getProperty("AndBibleEpubModule") != null
