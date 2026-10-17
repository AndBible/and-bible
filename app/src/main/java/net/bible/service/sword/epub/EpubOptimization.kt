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

import net.bible.sharedcore.log.Log
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubHtmlToFrag
import net.bible.android.database.StyleSheet
import net.bible.android.misc.elementToString
import net.bible.service.installzip.InstallZipProgress
import net.bible.service.common.useKdomBuilder
import net.bible.service.common.XmlQueries
import net.bible.service.sword.SwordContentFacade
import fi.sykero.kdom.Document
import fi.sykero.kdom.Element
import net.bible.sharedlogic.util.currentTimeMillis
import net.bible.sharedlogic.util.decodeUrl
import org.crosswire.common.util.KFiles

private val urlRe = Regex("""^https?://.*""")

const val EPUB_OPTIMIZER_VERSION = 2

fun EpubBackendState.readOriginal(origId: String): Pair<Document?, Int> {
    val file = fileForOriginalId(origId)?: return Pair(null, 0)
    val parentFolder = file.parent!!

    fun epubSrc(src: String): String {
        val f = parentFolder.child(src)
        val filePath = f.normalized().relativeTo(rootFolder).toString()
        return "/epub/$filePath"
    }

    fun fixReferences(e: Element): Element {
        for(img in XmlQueries.svgImagesWithHref(e, svgNamespace, xlinkNamespace)) {
            val src = img.getAttribute("href", xlinkNamespace)!!.value
            val finalSrc = epubSrc(src)
            img.setAttribute("href", finalSrc, xlinkNamespace)
        }
        for(img in XmlQueries.imgs(e, xhtmlNamespace)) {
            val src = img.getAttribute("src")!!.value
            val finalSrc = epubSrc(src)
            img.setAttribute("src", finalSrc)
        }
        for(a in XmlQueries.links(e, xhtmlNamespace)) {
            val href = a.getAttribute("href")?.value?: continue
            val fileAndId = getFileAndId(href)
            if(fileAndId != null && !urlRe.matches(href)) {
                val fileStr = decodeUrl(fileAndId.first)
                val fileName = if(fileStr.isEmpty()) null else parentFolder.child(fileStr).normalized().relativeTo(rootFolder).toString()
                val id: String = fileName?.let {fileToId[it] }?: origId
                a.name = "epubRef"
                a.setAttribute("to-key", id)
                a.setAttribute("to-id", fileAndId.second)
            } else {
                a.name = "epubA"
            }
        }
        return e
    }

    return useKdomBuilder { it.build(KFiles.readBytes(file)) }
        .let {
            val processed = fixReferences(it.rootElement)
            val body = XmlQueries.body(processed, xhtmlNamespace)
                ?: return Pair(null, 0)
            val maxOrdinal = SwordContentFacade.addAnchors(body, getBookMetaData().language.code, true)
            Pair(it, maxOrdinal)
        }
}

const val ORDINALS_PER_FRAGMENT = 500
fun EpubBackendState.optimizeEpub() {

    fun getSplitPoint(element: Document, splitPoint: Int): Element? = XmlQueries.splitPoint(element, splitPoint, xhtmlNamespace)

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

        }
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

    fun getOrdinalRange(doc: Document): IntRange? {
        val bvas = XmlQueries.bvas(doc, xhtmlNamespace)
        if(bvas.size == 0) return null
        return bvas.first().getAttribute("ordinal")!!.intValue ..
            bvas.last().getAttribute("ordinal")!!.intValue
    }

    fun splitIntoFragments(originalId: String, origDocument: Document, maxOrdinal: Int): List<EpubFragment> {
        return splitIntoN(origDocument, 0..maxOrdinal, maxOrdinal/ORDINALS_PER_FRAGMENT).mapNotNull {
            val ordinalRange = getOrdinalRange(it)
            if (ordinalRange == null) null
            else EpubFragment(originalId = originalId, ordinalRange.first, ordinalRange.last).apply {
                element=it.rootElement
            }
        }
    }

    fun findIds(frag: EpubFragment): List<String> {
        val elemsWithId = XmlQueries.elementsWithId(frag.element!!)
        return elemsWithId.map { it.getAttribute("id")!!.value }
    }

    fun writeFragment(frag: EpubFragment) {
        val f = fragDir.child(frag.fragFileName)
        val body = XmlQueries.body(frag.element!!, xhtmlNamespace)!!
        body.name = "div"
        val strContent = elementToString(body)
        writeFragment(f, strContent)
    }

    KFiles.deleteRecursively(fragDir)
    try { KFiles.createDirectories(fragDir) } catch (e: okio.IOException) { Log.w(TAG, "Could not create $fragDir", e) } // File.mkdirs returned false
    KFiles.write(optimizeLockFile, byteArrayOf(1))
    val writeDb = getEpubDatabase(appDbFilename)
    val writeDao = writeDb.epubDao()
    // The database file lives in internal storage and can outlive the (external) epub dir
    // when a module is removed via the failure path. Clear any stale rows from a previous
    // optimization so newly-written fragment files are the only ones referenced.
    epubClearBlocking(writeDao)
    val start = currentTimeMillis()
    for(k in originalIds) {
        val title = fileToTitle?.let {f2t -> f2t[idToFile[k]]} ?: strings.namelessTitle
        val s = strings.processingEpub("${getBookMetaData().name}: $title")
        InstallZipProgress.report(s)
        Log.i(TAG, "${getBookMetaData().name}: optimizing $k")

        val (origDocument, maxOrdinal) = readOriginal(k)
        origDocument ?: continue

        val fragments = splitIntoFragments(k, origDocument, maxOrdinal).let {
            it.ifEmpty{ listOf(EpubFragment(k, 0, 0).apply { element = origDocument.rootElement }) }
        }
        val head = origDocument.rootElement.children.find { it.name == "head" }!!
        val styleSheets = head.children
            .filter { it.name == "link" && it.getAttribute("type")?.value == "text/css" }
            .mapNotNull { StyleSheet(k, it.getAttribute("href")!!.value) }.toTypedArray()

        epubInsertFragmentsBlocking(writeDao, fragments, k, styleSheets)

        val epubHtmlToFrags = mutableListOf<EpubHtmlToFrag>()
        for(frag in fragments) {
            Log.i(TAG, "${getBookMetaData().name}: writing frag ${frag.id}")
            writeFragment(frag)
            findIds(frag).mapTo(epubHtmlToFrags) { EpubHtmlToFrag("$k#$it", frag.id) }
            frag.element = null // clear up memory
        }
        epubInsertHtmlToFragsBlocking(writeDao, epubHtmlToFrags)
        fileForOriginalId(k)?.let { KFiles.delete(it) }
    }
    KFiles.write(versionFile, "$EPUB_OPTIMIZER_VERSION".toByteArray())
    val total = (currentTimeMillis() - start) / 1000
    writeDb.close()
    KFiles.delete(optimizeLockFile)
    Log.i(TAG, "Total time in optimization: $total")
}
