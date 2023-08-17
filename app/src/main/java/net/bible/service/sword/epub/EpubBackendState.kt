/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.database.EpubFragment
import net.bible.service.common.useSaxBuilder
import net.bible.service.common.useXPathInstance
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.filter.Filters
import java.io.File
import java.net.URLDecoder


private val re = Regex("[^a-zA-z0-9]")
private fun sanitizeModuleName(name: String): String = name.replace(re, "_")

private val hrefRe = Regex("""^([^#]+)?#?(.*)$""")
private fun getFileAndId(href: String): Pair<String, String>? {
    val m = hrefRe.matchEntire(href)?: return null
    return m.groupValues[1] to m.groupValues[2]
}


class EpubBackendState(internal val epubDir: File): OpenFileState {
    constructor(epubDir: File, metadata: SwordBookMetaData): this(epubDir) {
        this._metadata = metadata
    }

    override fun close() {}

    private var _metadata: SwordBookMetaData? = null

    private val dcNamespace = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/")
    private val epubNamespace = Namespace.getNamespace("ns", "http://www.idpf.org/2007/opf")
    private val containerNamespace = Namespace.getNamespace("ns", "urn:oasis:names:tc:opendocument:xmlns:container")
    private val tocNamespace = Namespace.getNamespace("ns", "http://www.daisy.org/z3986/2005/ncx/")
    internal val xhtmlNamespace = Namespace.getNamespace("ns", "http://www.w3.org/1999/xhtml")
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

    private val toc = tocFile?.run { useSaxBuilder { it.build(this) } }

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
            .mapNotNull { it.getAttribute("href").value?.let { File(parentFolder, it) } }
    }

    private fun getFragmentForOptimizedKey(key: Key): EpubFragment = dao.getFragment(key.osisRef.toLong())

    fun styleSheetsForOptimizedKey(key: Key): List<File> {
        val frag = getFragmentForOptimizedKey(key)
        return styleSheetsForOriginalKey(getOriginalKey(frag.originalHtmlFileName))
    }

    internal fun readOriginal(key: Key): Pair<Document, Int> {
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
                val maxOrdinal = SwordContentFacade.addAnchors(processed, bookMetaData.language.code, true)
                val resultDoc = Document(processed.clone())
                Pair(resultDoc, maxOrdinal)
            }
    }

    private fun getOriginalKey(idRef: String): Key {
        val keyName = fileToTitle?.let {f2t -> idToFile[idRef]?.let { f2t[it] } } ?: BibleApplication.application.getString(
            R.string.nameless)
        return DefaultLeafKeyList(keyName, idRef)
    }

    private fun getFragmentKey(fragment: EpubFragment): Key {
        val filePath = idToFile.let { it[fragment.originalHtmlFileName] }
        val keyName = filePath?.let { fp -> fileToTitle?.let {it[fp] } } ?: BibleApplication.application.getString(R.string.nameless)
        return DefaultLeafKeyList(keyName, "${fragment.id}")
    }

    val tocKeys: List<Key> get() {
        val content = useXPathInstance { xp ->
            xp.compile("//ns:navPoint/ns:content", Filters.element(), null, tocNamespace)
                .evaluate(toc)
        }
        val book = Books.installed().getBook(bookMetaData.initials)
        return content.map { c ->
            val fileAndId = getFileAndId(c.getAttribute("src").value)
            val fileName = fileAndId?.first?.let { URLDecoder.decode(it, "UTF-8") }
            val htmlId = fileAndId?.second?.let { it.ifEmpty { null } }
            val id = fileToId[fileName]!!
            val keyStr: String = if(htmlId == null) {
                id
            } else {
                "$id#$htmlId"
            }
            val frag = dao.getFragment(keyStr)
            val key = getFragmentKey(frag)
            BookAndKey(key, book, htmlId = htmlId)
        }
    }

    fun readOptimized(key: Key): String {
        val frag = getFragmentForOptimizedKey(key)
        return String(File(cacheDir, frag.cacheFileName).readBytes())
    }

    fun indexOfOptimizedKey(key: Key): Int = optimizedKeys.indexOf(key)

    fun getFromOptimizedIndex(index: Int): Key = optimizedKeys[index]

    val optimizedCardinality get() = optimizedKeys.size

    internal val originalKeys: List<Key>
        get() = queryContent("//ns:spine/ns:itemref")
            .map { getOriginalKey(it.getAttribute("idref").value) }

    val optimizedKeys: List<Key> get() = dao.fragments().map { getFragmentKey(it) }

    internal val cacheDir get() = File(epubDir,  "optimized")

    internal val dbFilename = "epub-${bookMetaData.initials}.sqlite3"

    init {
        val appDbFile = BibleApplication.application.getDatabasePath(dbFilename)
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
        BibleApplication.application.deleteDatabase(dbFilename)
    }

    fun getKey(originalKey: String, htmlId: String): Key {
        val frag = dao.getFragment("$originalKey#$htmlId")
        return getFragmentKey(frag)
    }
}
