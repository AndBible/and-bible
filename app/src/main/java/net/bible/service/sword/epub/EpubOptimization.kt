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
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.EpubFragment
import net.bible.android.database.EpubHtmlToFrag
import net.bible.android.database.StyleSheet
import net.bible.android.misc.elementToString
import net.bible.android.view.activity.installzip.InstallZipEvent
import net.bible.android.view.activity.page.application
import net.bible.service.common.useSaxBuilder
import net.bible.service.common.useXPathInstance
import net.bible.service.sword.SwordContentFacade
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.filter.Filters
import java.io.File
import java.net.URLDecoder
import java.util.zip.GZIPOutputStream

private val urlRe = Regex("""^https?://.*""")
fun EpubBackendState.readOriginal(origId: String): Pair<Document, Int> {
    val file = fileForOriginalId(origId)
    val parentFolder = file.parentFile!!

    fun epubSrc(src: String): String {
        val f = File(parentFolder, src)
        val filePath = f.toRelativeString(rootFolder)
        return "/epub/${bookMetaData.initials}/$filePath"
    }

    fun fixReferences(e: Element): Element {
        for(img in useXPathInstance { xp -> xp.compile("//svg:image[@xlink:href]", Filters.element(), null, svgNamespace, xlinkNamespace).evaluate(e) }) {
            val src = img.getAttribute("href", xlinkNamespace).value
            val finalSrc = epubSrc(src)
            img.setAttribute("href", finalSrc, xlinkNamespace)
        }
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

    return useSaxBuilder { it.build(file) }
        .let {
            val processed = fixReferences(it.rootElement)
            val maxOrdinal = SwordContentFacade.addAnchors(processed, bookMetaData.language.code, true)
            Pair(it, maxOrdinal)
        }
}

const val ORDINALS_PER_FRAGMENT = 500
fun EpubBackendState.optimizeEpub() {

    fun getSplitPoint(element: Document, splitPoint: Int): Element? = useXPathInstance { xp ->
        xp.compile(
            "//*[descendant::ns:BVA[@ordinal='$splitPoint'] and (following-sibling::ns:p or preceding-sibling::ns:p or self::ns:p)]",
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

    fun getOrdinalRange(doc: Document): IntRange {
        val bvas = useXPathInstance { xp ->
            xp.compile(
                "//ns:BVA",
                Filters.element(), null, xhtmlNamespace
            ).evaluate(doc)
        }
        if(bvas.size == 0) return 0..0
        return bvas.first().getAttribute("ordinal").intValue ..
            bvas.last().getAttribute("ordinal").intValue
    }

    fun splitIntoFragments(originalId: String, origDocument: Document, maxOrdinal: Int): List<EpubFragment> {
        return splitIntoN(origDocument, 0..maxOrdinal, maxOrdinal/ORDINALS_PER_FRAGMENT).map {
            val ordinalRange = getOrdinalRange(it)
            EpubFragment(originalId = originalId, ordinalRange.first, ordinalRange.last).apply {
                element=it.rootElement
            }
        }
    }

    fun findIds(frag: EpubFragment): List<String> {
        val elemsWithId = useXPathInstance { xp ->
            xp.compile(
                "//*[@id]",
                Filters.element(), null, xhtmlNamespace
            ).evaluate(frag.element)
        }
        return elemsWithId.map { it.getAttribute("id").value }
    }

    fun writeFragment(frag: EpubFragment) {
        val f = File(fragDir, frag.fragFileName)
        val body = useXPathInstance { xp ->
            xp.compile(
                "//ns:body",
                Filters.element(), null, xhtmlNamespace
            ).evaluateFirst(frag.element)
        }
        body.name = "div"
        val strContent = elementToString(body)
        f.outputStream().use {out ->
            GZIPOutputStream(out).use {gzip ->
                gzip.write(strContent.toByteArray())
            }
        }
    }

    fragDir.deleteRecursively()
    fragDir.mkdirs()
    optimizeLockFile.outputStream().use { it.write(1)}
    val writeDb = getEpubDatabase(dbFilename)
    val writeDao = writeDb.epubDao()
    val start = System.currentTimeMillis()
    for(k in originalIds) {
        val title = fileToTitle?.let {f2t -> f2t[idToFile[k]]} ?: application.getString(R.string.nameless)
        val s = application.getString(R.string.processing_epub, "${bookMetaData.name}: $title")
        ABEventBus.post(InstallZipEvent(s))
        Log.i(TAG, "${bookMetaData.name}: optimizing $k")

        val (origDocument, maxOrdinal) = readOriginal(k)

        val fragments = splitIntoFragments(k, origDocument, maxOrdinal)
        val ids = writeDao.insert(*fragments.toTypedArray())
        for((id, frag) in ids.zip(fragments)) {
            frag.id = id
        }
        writeDao.insert(EpubHtmlToFrag(k, fragments[0].id))

        val head = origDocument.rootElement.children.find { it.name == "head" }!!
        val styleSheets = head.children
            .filter { it.name == "link" && it.getAttribute("type")?.value == "text/css" }
            .mapNotNull { StyleSheet(k, it.getAttribute("href").value) }.toTypedArray()

        writeDao.insert(*styleSheets)

        for(frag in fragments) {
            Log.i(TAG, "${bookMetaData.name}: writing frag ${frag.id}")
            writeFragment(frag)
            val epubHtmlToFrags = findIds(frag).map {
                EpubHtmlToFrag("$k#$it", frag.id)
            }.toTypedArray()
            writeDao.insert(*epubHtmlToFrags)
            frag.element = null // clear up memory
        }
        fileForOriginalId(k).delete()
    }
    val total = (System.currentTimeMillis() - start) / 1000
    writeDb.close()
    optimizeLockFile.delete()
    Log.i(TAG, "Total time in optimization: $total")
}
