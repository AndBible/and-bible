/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.service.db.blockingAtEdge
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.service.common.ArchiveUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.bible.sharedcore.log.Log
import net.bible.android.SharedConstants
import net.bible.android.control.page.OrdinalRange
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubMeta
import net.bible.service.common.useSaxBuilder
import net.bible.service.common.XmlQueries
import net.bible.service.sword.BookAndKey
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import org.jdom2.Namespace
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

class EpubBackendState(private val epubDir: File, internal val strings: CoreStrings, private val env: AppEnvironment): OpenFileState {
    constructor(epubDir: File, metadata: SwordBookMetaData, strings: CoreStrings, env: AppEnvironment): this(epubDir, strings, env) {
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
    private val contentFileName = XmlQueries.rootfiles(metaInfo, containerNamespace).first().getAttribute("full-path").value

    private val contentXmlFile = File(epubDir, contentFileName)
    val rootFolder: File = contentXmlFile.parentFile!!

    private val content = useSaxBuilder { it.build(contentXmlFile) }
    internal val fileToId = XmlQueries.manifestItems(content, epubNamespace).associate {
        val fileName = URLDecoder.decode(it.getAttribute("href").value, "UTF-8")
        fileName to it.getAttribute("id").value
    }

    internal val idToFile = fileToId.entries.associate { it.value to it.key }

    private val tocFile = XmlQueries.tocItem(content, epubNamespace)?.getAttribute("href")?.value?.let { File(rootFolder, it) }

    private val toc = tocFile?.run { useSaxBuilder { it.build(this) } }

    internal val fileToTitle = toc?.let { document ->
        XmlQueries.navPointContents(document, tocNamespace).reversed().associate {
            val textElem = XmlQueries.navLabelTexts(it, tocNamespace).first()
            val fileAndId = getFileAndId(it.getAttribute("src").value)
            val fileName = fileAndId?.first?.let { name -> URLDecoder.decode(name, "UTF-8") }
            fileName to textElem.text
        }
    }

    private fun queryMetadata(key: String): String? = XmlQueries.dcElements(content, key, dcNamespace).firstOrNull()?.value

    private fun fragmentBlocking(key: Key): EpubFragment? = blockingAtEdge { dao.getFragment(key.getOsisRef().toLong()) } // L1-edge: JSword synchronous Backend contract

    private suspend fun fragment(key: Key): EpubFragment? = dao.getFragment(key.getOsisRef().toLong())

    fun fileForOriginalId(id: String): File? = idToFile[id]?.let {File(rootFolder, it) }

    suspend fun styleSheets(key: Key): List<File> {
        val frag = fragment(key)?: return emptyList()
        val file = fileForOriginalId(frag.originalId)?: return emptyList()
        val parentFolder = file.parentFile
        return dao.styleSheets(frag.originalId).map { File(parentFolder, it.styleSheetFile).canonicalFile }
    }

    private fun getKey(fragment: EpubFragment, label: String? = null): Key {
        val filePath = idToFile.let { it[fragment.originalId] }
        val keyName = label ?: filePath?.let { fp -> fileToTitle?.let {it[fp] } } ?: strings.namelessTitle
        return DefaultLeafKeyList(keyName, "${fragment.id}")
    }

    suspend fun tocKeys(): List<Key> {
        // Check if toc is null and return an empty list if it is
        if (toc == null) return emptyList()

        val content = XmlQueries.navPointContents(toc, tocNamespace)
        val book = Books.installed().getBook(getBookMetaData().initials)
        return content.mapNotNull { c ->
            val label = XmlQueries.navLabelTexts(c, tocNamespace).firstOrNull()?.text
            val fileAndId = getFileAndId(c.getAttribute("src").value)
            val fileName = fileAndId?.first?.let { URLDecoder.decode(it, "UTF-8") }
            val htmlId = fileAndId?.second?.let { it.ifEmpty { null } }
            val id = fileToId[fileName] ?: return@mapNotNull null
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
        val frag = fragmentBlocking(key)?: return ""
        return readFragment(frag)
    }

    private fun readFragment(frag: EpubFragment): String {
        val sourceFile = File(fragDir, frag.fragFileName)
        if (!sourceFile.exists()) {
            // The fragment database and the optimized fragment files can get out of sync
            // (e.g. an orphaned database reused after a failed optimization, or external
            // storage cleaned by the system). A missing fragment file must not crash the
            // app: return empty content and let the caller degrade gracefully.
            Log.e(TAG, "Fragment file missing: ${sourceFile.absolutePath} (frag ${frag.id}); returning empty content")
            return ""
        }
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

    internal val originalIds: List<String> get() = XmlQueries.spineItemrefs(content, epubNamespace).map { it.getAttribute("idref").value }

    val keys: List<Key> get() = blockingAtEdge { dao.fragments() }.map { getKey(it) } // L1-edge: JSword synchronous Backend contract

    internal val fragDir get() = File(epubDir,  "optimized")
    internal val versionFile = File(fragDir, "version.txt")
    internal val optimizeLockFile = File(epubDir, "optimize.lock")
    val optimizerVersion get() = try { String(versionFile.readBytes()).toLong() } catch (e: Exception) {1}

    private val epubDbFilename = "optimized.sqlite3.gz"
    internal val appDbFilename = "epub-${getBookMetaData().initials}.sqlite3"
    private val searchDbFilename = "epub-${getBookMetaData().initials}-search.sqlite3"
    private val searchDbFile = File(epubDir, searchDbFilename)
    private val alternativeEpubDbFilename = "${appDbFilename}.gz"

    init {
        val appDbFile = File(env.databasePath(appDbFilename))
        var epubDbFile = File(epubDir, alternativeEpubDbFilename)
        if(!epubDbFile.exists()) {
            epubDbFile = File(epubDir, epubDbFilename)
        }

        // F124: the gz is only as good as the fragment files it indexes. version.txt is written last by
        // optimizeEpub, so its absence means the optimized output is missing or incomplete (an EPUB shipped
        // with a foreign gz, or external storage cleaned). Re-optimizing needs the source files, though:
        // every optimizer deletes each source after optimizing it, and EPUBs optimized before version.txt
        // existed (2023-11) are intact but have neither version.txt nor sources. Those must be reused as is;
        // deleting their gz/fragments would destroy the only copy. So discard only when sources are present.
        val optimizedOutputComplete = fragDir.isDirectory && versionFile.canRead()
        val ids = originalIds
        val sourcesPresent = ids.isNotEmpty() && ids.all { fileForOriginalId(it)?.exists() == true }
        if(epubDbFile.exists() && !optimizedOutputComplete && sourcesPresent) {
            Log.w(TAG, "Optimized output missing for ${epubDir.name}; discarding ${epubDbFile.name} and re-optimizing")
            epubDbFile.delete()
        }

        if(!epubDbFile.exists()) {
            optimizeEpub()
            ArchiveUtils.gzipFile(appDbFile, epubDbFile)
        } else {
            if(!appDbFile.exists())  {
                ArchiveUtils.gunzipFile(epubDbFile, appDbFile)
            }
        }
    }
    private val readDb = getEpubDatabase(appDbFilename)
    private val search = EpubSearch(searchDbFile)
    init {
        getBookMetaData().indexStatus = if(search.isIndexed) IndexStatus.DONE else IndexStatus.UNDONE
    }

    private val dao = readDb.epubDao()

    val isIndexed get() = search.isIndexed

    fun deleteSearchIndex() {
        search.deleteIndex()
    }

    suspend fun buildSearchIndex() = withContext(Dispatchers.IO) {
        // Not just `return`: an existing index must also be REPORTED as done, so that any caller
        // polling `indexStatus` learns the truth rather than waiting out a timeout.
        //
        // NOTE, because an earlier version of this comment claimed otherwise: this branch is NOT
        // reachable from the reading view's index prompt. `SearchIndexServiceImpl.createIndex:42`
        // calls `SwordDocumentFacade.deleteDocumentIndex` FIRST, which for an EPUB drops the FTS5
        // table outright (`EpubSearch.deleteIndex` is a `DROP TABLE`), so `search.isIndexed` is
        // always false by the time this runs from that path. The branch guards a direct caller that
        // has not deleted first — it is correct and cheap, but it has no test and no device-checklist
        // item, and nothing in the F43 batch exercises it.
        if (search.isIndexed) {
            getBookMetaData().indexStatus = IndexStatus.DONE
            return@withContext
        }
        getBookMetaData().indexStatus = IndexStatus.CREATING
        // Reset failures so the app-owned index job can be retried; always finish its progress row.
        var job: Progress? = null
        try {
            val jobName = strings.creatingIndexFor(getBookMetaData().name)
            job = org.crosswire.common.progress.JobManagerJvm.createJob("index-creation-${epubDir.path}", jobName, null)
            job.isNotifyUser = true
            job.beginJob(jobName)
            search.createTable()
            val frags = dao.fragments()
            job.totalWork = frags.size
            for(i in frags.indices) {
                currentCoroutineContext().ensureActive()
                val frag = frags[i]
                job.work = i
                val reader = StringReader(readFragment(frag))
                val doc = useSaxBuilder { it.build(reader) }
                for(bva in XmlQueries.bvas(doc, xhtmlNamespace)) {
                    val ordinal = bva.getAttribute("ordinal").value.toInt()
                    search.addContent(bva.text, frag.id, ordinal)
                }
            }
            getBookMetaData().indexStatus = IndexStatus.DONE
        } catch (e: Exception) {
            Log.e(TAG, "buildSearchIndex failed for ${getBookMetaData().initials}", e)
            // Table existence is the completion signal, so never retain an interrupted build.
            search.deleteIndex()
            getBookMetaData().indexStatus = IndexStatus.UNDONE
            if (e is CancellationException) throw e
        } finally {
            job?.done()
        }
    }

    suspend fun search(search: String): List<KeyAndText> = withContext(Dispatchers.IO) {
        val book = Books.installed().getBook(getBookMetaData().initials)
        val hits = this@EpubBackendState.search.search(search)
        hits.mapNotNull {
            val frag = dao.getFragment(it.fragId) ?: return@mapNotNull null
            val key = BookAndKey(getKey(frag), book, OrdinalRange(it.ordinal))
            KeyAndText(key, it.text)
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
            metadata.setDriver(EpubSwordDriver())
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
        env.deleteDatabase(appDbFilename)
    }

    suspend fun getKey(originalKey: String, htmlId: String): Key? {
        val frag = dao.getFragment(if(htmlId.isNotEmpty()) "$originalKey#$htmlId" else originalKey)
        return frag?.let {getKey(it)}
    }

    suspend fun getOrdinalRange(key: Key): IntRange {
        val frag = fragment(key) ?: return 0..0
        return frag.ordinalStart .. frag.ordinalEnd
    }

    /** Total ordinal span of the whole book (anchor ordinals restart per spine item). */
    suspend fun bookOrdinalSpan(): Int = dao.fragments().sumOf { it.ordinalEnd - it.ordinalStart + 1 }

    /** Sum of the ordinal spans of all fragments preceding [key] in book order. */
    suspend fun fragmentOffset(key: Key): Int {
        val targetId = fragment(key)?.id ?: return 0
        var offset = 0
        for (frag in dao.fragments().sortedBy { it.id }) {
            if (frag.id == targetId) break
            offset += frag.ordinalEnd - frag.ordinalStart + 1
        }
        return offset
    }

    /**
     * Total visible-text character count of the whole book. Computed once from the
     * fragment BVA text and cached in the EpubMeta table; subsequent reads are O(1).
     * This avoids forcing a re-optimization of already-installed EPUBs.
     */
    suspend fun totalCharacters(): Int = withContext(Dispatchers.IO) {
        dao.getMeta()?.let { return@withContext it.totalCharacters }
        var total = 0
        var failed = false
        for (frag in dao.fragments()) {
            try {
                val doc = useSaxBuilder { it.build(StringReader(readFragment(frag))) }
                for (bva in XmlQueries.bvas(doc, xhtmlNamespace)) {
                    total += bva.text.length
                }
            } catch (e: Exception) {
                // A single unreadable/corrupt fragment must not abort the whole-book
                // character count (used only for the reading-progress indicator).
                Log.e(TAG, "Failed to read fragment ${frag.id} while computing totalCharacters; skipping", e)
                failed = true
            }
        }
        // Only cache the result when every fragment was read successfully; otherwise a
        // transient/repairable inconsistency would freeze an inaccurate count forever.
        if (!failed) dao.insert(EpubMeta(totalCharacters = total))
        total
    }
}
