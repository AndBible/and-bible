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
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.page.OrdinalRange
import net.bible.android.database.EpubFragment
import net.bible.service.common.CommonUtils
import net.bible.service.common.useSaxBuilder
import net.bible.service.common.useXPathInstance
import net.bible.service.sword.BookAndKey
import org.crosswire.common.progress.JobManager
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import org.jdom2.Namespace
import org.jdom2.filter.Filters
import java.io.File
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.GZIPInputStream


private val re = Regex("[^a-zA-z0-9]")
private fun sanitizeModuleName(name: String): String = name.replace(re, "_")

fun epubInitials(dirName: String): String = "Epub-" + sanitizeModuleName(dirName)

private val hrefRe = Regex("""^([^#]+)?#?(.*)$""")
internal fun getFileAndId(href: String): Pair<String, String>? {
    val m = hrefRe.matchEntire(href)?: return null
    return m.groupValues[1] to m.groupValues[2]
}

val xhtmlNamespace: Namespace = Namespace.getNamespace("ns", "http://www.w3.org/1999/xhtml")
val svgNamespace: Namespace = Namespace.getNamespace("svg", "http://www.w3.org/2000/svg")
val xlinkNamespace: Namespace = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink");

class KeyAndText(val key: BookAndKey, val text: String)

class EpubBackendState(private val epubDir: File): OpenFileState {
    constructor(epubDir: File, metadata: SwordBookMetaData): this(epubDir) {
        this._metadata = metadata
    }

    override fun close() {}

    private var _metadata: SwordBookMetaData? = null

    private val dcNamespace = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/")
    private val epubNamespace = Namespace.getNamespace("ns", "http://www.idpf.org/2007/opf")
    private val containerNamespace = Namespace.getNamespace("ns", "urn:oasis:names:tc:opendocument:xmlns:container")
    private val tocNamespace = Namespace.getNamespace("ns", "http://www.daisy.org/z3986/2005/ncx/")

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
    internal val fileToId = useXPathInstance { xp ->
        xp.compile("//ns:manifest/ns:item", Filters.element(), null, epubNamespace)
            .evaluate(content).associate {
                val fileName = URLDecoder.decode(it.getAttribute("href").value, "UTF-8")
                fileName to it.getAttribute("id").value
            }
    }

    internal val idToFile = fileToId.entries.associate { it.value to it.key }

    private val tocFile = useXPathInstance { xp ->
        val elem =
            xp.compile(
                "//ns:manifest/ns:item[@media-type='application/x-dtbncx+xml']",
                Filters.element(),
                null,
                epubNamespace
            ).evaluateFirst(content) ?:
            xp.compile(
                "//ns:manifest/ns:item[@id='toc']",
                Filters.element(),
                null,
                epubNamespace
            ).evaluateFirst(content) ?:
            xp.compile(
                "//ns:manifest/ns:item[@properties='nav']",
                Filters.element(),
                null,
                epubNamespace
            ).evaluateFirst(content)

        elem?.getAttribute("href")?.value?.run { File(rootFolder, this) }
    }

    private val toc = tocFile?.run { useSaxBuilder { it.build(this) } }

    internal val fileToTitle = toc?.run { useXPathInstance { xp ->
        xp.compile("//ns:navPoint/ns:content", Filters.element(), null, tocNamespace)
            .evaluate(this)
            .reversed()
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

    private fun getFragment(key: Key): EpubFragment? = dao.getFragment(key.osisRef.toLong())

    fun fileForOriginalId(id: String): File? = idToFile[id]?.let {File(rootFolder, it) }

    fun styleSheets(key: Key): List<File> {
        val frag = getFragment(key)?: return emptyList()
        val file = fileForOriginalId(frag.originalId)?: return emptyList()
        val parentFolder = file.parentFile
        return dao.styleSheets(frag.originalId).map { File(parentFolder, it.styleSheetFile).canonicalFile }
    }

    private fun getKey(fragment: EpubFragment, label: String? = null): Key {
        val filePath = idToFile.let { it[fragment.originalId] }
        val keyName = label ?: filePath?.let { fp -> fileToTitle?.let {it[fp] } } ?: BibleApplication.application.getString(R.string.nameless)
        return DefaultLeafKeyList(keyName, "${fragment.id}")
    }

    val tocKeys: List<Key> get() {
        val content = useXPathInstance { xp ->
            xp.compile("//ns:navPoint/ns:content", Filters.element(), null, tocNamespace)
                .evaluate(toc)
        }
        val book = Books.installed().getBook(bookMetaData.initials)
        return content.mapNotNull { c ->
            val label =
                useXPathInstance { xp2 ->
                    xp2.compile("../ns:navLabel/ns:text", Filters.element(), null, tocNamespace)
                        .evaluateFirst(c)
                }?.text
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
            frag?.let { BookAndKey(getKey(it, label = label), book, htmlId = htmlId) }
        }
    }

    fun read(key: Key): String {
        val frag = getFragment(key)?: return ""
        val sourceFile = File(fragDir, frag.fragFileName)
        val bytes = sourceFile.inputStream().use { inp ->
            GZIPInputStream(inp).use {gzip ->
                gzip.readBytes()
            }
        }
        return String(bytes)
    }

    fun indexOf(key: Key): Int = keys.indexOf(key)

    fun get(index: Int): Key = keys[index]

    val cardinality get() = keys.size

    internal val originalIds: List<String> get() = useXPathInstance { xp ->
        xp.compile("//ns:spine/ns:itemref", Filters.element(), null, epubNamespace).evaluate(content)
    }.map { it.getAttribute("idref").value }

    val keys: List<Key> get() = dao.fragments().map { getKey(it) }

    internal val fragDir get() = File(epubDir,  "optimized")
    internal val versionFile = File(fragDir, "version.txt")
    internal val optimizeLockFile = File(epubDir, "optimize.lock")
    val optimizerVersion get() = try { String(versionFile.readBytes()).toLong() } catch (e: Exception) {1}

    private val epubDbFilename = "optimized.sqlite3.gz"
    internal val appDbFilename = "epub-${bookMetaData.initials}.sqlite3"
    private val searchDbFilename = "epub-${bookMetaData.initials}-search.sqlite3"
    private val searchDbFile = File(epubDir, searchDbFilename)
    private val alternativeEpubDbFilename = "${appDbFilename}.gz"

    init {
        val appDbFile = BibleApplication.application.getDatabasePath(appDbFilename)
        var epubDbFile = File(epubDir, alternativeEpubDbFilename)
        if(!epubDbFile.exists()) {
            epubDbFile = File(epubDir, epubDbFilename)
        }

        if(!epubDbFile.exists()) {
            optimizeEpub()
            CommonUtils.gzipFile(appDbFile, epubDbFile)
        } else {
            if(!appDbFile.exists())  {
                CommonUtils.gunzipFile(epubDbFile, appDbFile)
            }
        }
    }
    private val readDb = getEpubDatabase(appDbFilename)
    private val search = EpubSearch(searchDbFile)
    init {
        bookMetaData.indexStatus = if(search.isIndexed) IndexStatus.DONE else IndexStatus.UNDONE
    }

    private val dao = readDb.epubDao()

    val isIndexed get() = search.isIndexed

    fun deleteSearchIndex() {
        search.deleteIndex()
    }

    fun buildSearchIndex() {
        if (search.isIndexed) return
        bookMetaData.indexStatus = IndexStatus.CREATING
        val jobName = application.getString(R.string.creating_index_for, bookMetaData.name)
        val job = JobManager.createJob("index-creation-${epubDir.path}", jobName, null)
        job.isNotifyUser = true
        job.beginJob(jobName)
        search.createTable()
        val frags = dao.fragments()
        job.totalWork = frags.size
        for(i in frags.indices) {
            val frag = frags[i]
            job.work = i
            val key = getKey(frag)
            val reader = StringReader(read(key))
            val doc = useSaxBuilder { it.build(reader) }
            for(bva in useXPathInstance { xp -> xp.compile("//ns:BVA", Filters.element(), null, xhtmlNamespace).evaluate(doc) }) {
                val ordinal = bva.getAttribute("ordinal").value.toInt()
                search.addContent(bva.text, frag.id, ordinal)
            }
        }
        bookMetaData.indexStatus = IndexStatus.DONE
        job.done()
    }

    fun search(search: String): List<KeyAndText> {
        val book = Books.installed().getBook(bookMetaData.initials)
        return this.search.search(search).mapNotNull {
            val frag = dao.getFragment(it.fragId)?: return@mapNotNull null
            val key = BookAndKey(getKey(frag), book, OrdinalRange(it.ordinal))
            val text = it.text
            KeyAndText(key, text)
        }
    }

    fun getResource(resourcePath: String) =
        File(rootFolder, resourcePath)

    override fun getBookMetaData(): SwordBookMetaData {
        return _metadata?: synchronized(this) {
            val initials = epubInitials(File(epubDir.path).name)
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
                version = optimizerVersion,
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
        BibleApplication.application.deleteDatabase(appDbFilename)
    }

    fun getKey(originalKey: String, htmlId: String): Key? {
        val frag = dao.getFragment(if(htmlId.isNotEmpty()) "$originalKey#$htmlId" else originalKey)
        return frag?.let {getKey(it)}
    }

    fun getOrdinalRange(key: Key): IntRange {
        val frag = getFragment(key) ?: return 0..0
        return frag.ordinalStart .. frag.ordinalEnd
    }
}
