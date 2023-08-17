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
import net.bible.android.misc.elementToString
import net.bible.android.view.activity.installzip.InstallZipEvent
import net.bible.android.view.activity.page.application
import net.bible.service.common.useXPathInstance
import org.crosswire.jsword.passage.Key
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.filter.Filters
import java.io.File


fun EpubBackendState.optimizeEpub() {
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
        val s = application.getString(R.string.processing_epub, "${bookMetaData.name}: ${k.name}")
        ABEventBus.post(InstallZipEvent(s))
        Log.i(TAG, "${bookMetaData.name}: optimizing ${k.osisRef}")

        val fragments = splitIntoFragments(k)
        val ids = writeDao.insert(*fragments.toTypedArray())
        for((id, frag) in ids.zip(fragments)) {
            frag.id = id
        }
        for(frag in fragments) {
            Log.i(TAG, "${bookMetaData.name}: writing frag ${frag.id}")
            writeFragment(frag)
            val epubHtmlToFrags = findIds(frag).map {
                EpubHtmlToFrag("${k.osisRef}#$it", frag.id)
            }.toTypedArray()
            writeDao.insert(*epubHtmlToFrags)
            frag.element = null // clear up memory
        }
    }
}
