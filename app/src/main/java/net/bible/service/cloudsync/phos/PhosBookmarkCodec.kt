/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync.phos

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification

/*
 * Mapping between AndBible's bookmark rows and the "label" / "bookmark" records of the Phos
 * clients. Pure functions over plain values, so they can be tested without a database.
 *
 * Phos records carry what all clients understand. What only AndBible knows (marker styles,
 * text offsets, the exact ordinals, the HTML of a note, the study pad layout per label) travels in
 * an "ab" object inside the payload; other clients keep it untouched.
 */

/** A label as stored in AndBible. */
data class AbLabel(
    val idHex: String,
    val name: String,
    val color: Int,
    val markerStyle: Boolean = false,
    val markerStyleWholeVerse: Boolean = false,
    val underlineStyle: Boolean = false,
    val underlineStyleWholeVerse: Boolean = true,
    val hideStyle: Boolean = false,
    val hideStyleWholeVerse: Boolean = false,
    val favourite: Boolean = false,
    val type: String? = null,
    val customIcon: String? = null,
)

/** The link of a bookmark to one label, with its study pad layout. */
data class AbLink(val labelName: String, val orderNumber: Int = -1, val indentLevel: Int = 0, val expandContent: Boolean = true)

data class AbNote(val text: String, val contentType: String?)

/** A Bible bookmark (BibleBookmark + BibleBookmarkNotes + BibleBookmarkToLabel) as stored in AndBible. */
data class AbBibleBookmark(
    val idHex: String,
    val v11n: String,
    val ordinalStart: Int,
    val ordinalEnd: Int,
    val module: String?,
    val startOffset: Int?,
    val endOffset: Int?,
    val wholeVerse: Boolean,
    val createdMs: Long,
    val updatedMs: Long,
    val customIcon: String?,
    val note: AbNote?,
    val links: List<AbLink>,
    val primaryLabel: String?,
)

/** A bookmark on a non-Bible document (GenericBookmark + notes + labels). */
data class AbGenericBookmark(
    val idHex: String,
    val key: String,
    val bookInitials: String,
    val ordinalStart: Int?,
    val ordinalEnd: Int?,
    val startOffset: Int?,
    val endOffset: Int?,
    val wholeVerse: Boolean,
    val createdMs: Long,
    val updatedMs: Long,
    val customIcon: String?,
    val note: AbNote?,
    val links: List<AbLink>,
    val primaryLabel: String?,
)

/** A text entry of a study pad (StudyPadTextEntry + StudyPadTextEntryText). */
data class AbStudyPadEntry(
    val idHex: String,
    val labelName: String,
    val orderNumber: Int,
    val indentLevel: Int,
    val text: String,
    val contentType: String?,
)

/** Where a Bible bookmark points, in Phos terms. */
data class PhosLocation(val bookOsis: String, val bookIdx: Int, val chap: Int, val vStart: Int, val vEnd: Int,
                        val endBookOsis: String? = null, val endChap: Int? = null, val endVerse: Int? = null)

object PhosVerses {
    /** 0-based index in the 66 book Protestant canon (Gen = 0, Rev = 65); -1 for other books. */
    fun canonIndex(book: BibleBook): Int {
        val o = book.ordinal
        return when (o) {
            in BibleBook.GEN.ordinal..BibleBook.MAL.ordinal -> o - BibleBook.GEN.ordinal
            in BibleBook.MATT.ordinal..BibleBook.REV.ordinal -> 39 + o - BibleBook.MATT.ordinal
            else -> -1
        }
    }

    fun location(v11n: Versification, ordinalStart: Int, ordinalEnd: Int): PhosLocation? {
        val s = try { v11n.decodeOrdinal(ordinalStart) } catch (e: Exception) { return null } ?: return null
        val e = try { v11n.decodeOrdinal(maxOf(ordinalEnd, ordinalStart)) } catch (ex: Exception) { s } ?: s
        val sameChapter = e.book == s.book && e.chapter == s.chapter
        return PhosLocation(
            bookOsis = s.book.osis,
            bookIdx = canonIndex(s.book),
            chap = s.chapter,
            vStart = s.verse,
            vEnd = if (sameChapter) maxOf(e.verse, s.verse) else s.verse,
            endBookOsis = if (sameChapter || e.book == s.book) null else e.book.osis,
            endChap = if (sameChapter) null else e.chapter,
            endVerse = if (sameChapter) null else e.verse,
        )
    }

    /** Ordinals of a Phos location in [v11n]; null when the book or verse does not exist there. */
    fun ordinals(v11n: Versification, loc: PhosLocation): Pair<Int, Int>? {
        val book = BibleBook.fromOSIS(loc.bookOsis) ?: return null
        if (!v11n.containsBook(book) || loc.chap < 0 || loc.vStart < 0) return null
        if (loc.chap > v11n.getLastChapter(book)) return null
        val start = v11n.getOrdinal(org.crosswire.jsword.passage.Verse(v11n, book, loc.chap, minOf(loc.vStart, v11n.getLastVerse(book, loc.chap))))
        val endBook = loc.endBookOsis?.let { BibleBook.fromOSIS(it) } ?: book
        val endChap = loc.endChap ?: loc.chap
        if (!v11n.containsBook(endBook) || endChap > v11n.getLastChapter(endBook)) return start to start
        val sameChapter = endBook == book && endChap == loc.chap
        val endVerse = minOf(if (sameChapter) maxOf(loc.vEnd, loc.vStart) else maxOf(loc.endVerse ?: 1, 1),
            v11n.getLastVerse(endBook, endChap))
        val end = v11n.getOrdinal(org.crosswire.jsword.passage.Verse(v11n, endBook, endChap, endVerse))
        return start to maxOf(start, end)
    }
}

/** Plain text (Markdown) of an HTML note, as the other Phos clients show and merge it. */
object PhosNoteText {
    private val entities = mapOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'")

    fun htmlToText(html: String): String {
        var s = html.replace("\r", "")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(p|div|h[1-6]|blockquote)>"), "\n\n")
        s = s.replace(Regex("(?i)<li[^>]*>"), "- ")
        s = s.replace(Regex("(?i)</li>"), "\n")
        s = s.replace(Regex("(?i)</?(b|strong)>"), "**")
        s = s.replace(Regex("(?i)</?(i|em)>"), "*")
        s = s.replace(Regex("<[^>]*>"), "")
        for ((k, v) in entities) s = s.replace(k, v)
        s = s.replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        s = s.replace("&amp;", "&")
        s = s.replace(Regex("[ \\t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n")
        return s.trim()
    }
}

object PhosBookmarkCodec {
    const val MARKDOWN = "MARKDOWN"
    const val HTML = "HTML"

    /** Fields a label record of this device writes; the rest (style, icon, ...) stays as others set it. */
    val labelFields = setOf("name", "color", "favorite", "ab")

    /** Fields a bookmark record of this device writes. */
    val bookmarkFields = setOf("kind", "module", "bookOsis", "bookIdx", "chap", "vStart", "vEnd", "entryKey",
        "labelNames", "noteMd", "sortOrder", "indent", "created", "modified", "ab")

    // ---------------- labels ----------------

    fun colorToHex(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')

    fun hexToColor(hex: String?, fallback: Int): Int {
        val s = hex?.removePrefix("#") ?: return fallback
        if (s.length < 6) return fallback
        val rgb = s.substring(0, 6).toIntOrNull(16) ?: return fallback
        return (0xFF shl 24) or rgb
    }

    fun labelToJson(l: AbLabel): JsonObject = buildJsonObject {
        put("name", l.name)
        put("color", colorToHex(l.color))
        put("favorite", l.favourite)
        put("ab", buildJsonObject {
            put("abId", l.idHex)
            put("markerStyle", l.markerStyle)
            put("markerStyleWholeVerse", l.markerStyleWholeVerse)
            put("underlineStyle", l.underlineStyle)
            put("underlineStyleWholeVerse", l.underlineStyleWholeVerse)
            put("hideStyle", l.hideStyle)
            put("hideStyleWholeVerse", l.hideStyleWholeVerse)
            l.type?.let { put("type", it) }
            l.customIcon?.let { put("customIcon", it) }
        })
    }

    /** The label a record describes; [existing] keeps id and what the record does not say. */
    fun labelFromJson(name: String, p: JsonObject, existing: AbLabel?, defaultColor: Int): AbLabel {
        val ab = p["ab"] as? JsonObject
        val base = existing ?: AbLabel(PhosIds.labelUuidHex(name), name, defaultColor)
        return base.copy(
            name = name,
            color = hexToColor(p.str("color"), base.color),
            favourite = p.boolOr("favorite", base.favourite),
            markerStyle = ab?.boolOr("markerStyle", base.markerStyle) ?: base.markerStyle,
            markerStyleWholeVerse = ab?.boolOr("markerStyleWholeVerse", base.markerStyleWholeVerse) ?: base.markerStyleWholeVerse,
            underlineStyle = ab?.boolOr("underlineStyle", base.underlineStyle) ?: base.underlineStyle,
            underlineStyleWholeVerse = ab?.boolOr("underlineStyleWholeVerse", base.underlineStyleWholeVerse) ?: base.underlineStyleWholeVerse,
            hideStyle = ab?.boolOr("hideStyle", base.hideStyle) ?: base.hideStyle,
            hideStyleWholeVerse = ab?.boolOr("hideStyleWholeVerse", base.hideStyleWholeVerse) ?: base.hideStyleWholeVerse,
            type = if (ab != null && ab.containsKey("type")) ab.str("type") else base.type ?: if (existing == null) "HIGHLIGHT" else null,
            customIcon = if (ab != null && ab.containsKey("customIcon")) ab.str("customIcon") else base.customIcon,
        )
    }

    // ---------------- notes ----------------

    private fun putNote(o: MutableMap<String, JsonElement>, ab: MutableMap<String, JsonElement>, note: AbNote?) {
        if (note == null) return
        if (note.contentType == MARKDOWN) {
            o["noteMd"] = JsonPrimitive(note.text)
        } else {
            o["noteMd"] = JsonPrimitive(PhosNoteText.htmlToText(note.text))
            ab["noteHtml"] = JsonPrimitive(note.text)
        }
    }

    /** The note a record carries: the HTML kept in "ab" while the text still is its own, else the text. */
    fun noteFromJson(p: JsonObject): AbNote? {
        val md = p.str("noteMd") ?: return null
        val html = (p["ab"] as? JsonObject)?.str("noteHtml")
        if (html != null && PhosNoteText.htmlToText(html) == md) return AbNote(html, HTML)
        if (md.isEmpty()) return null
        return AbNote(md, MARKDOWN)
    }

    private fun linksJson(links: List<AbLink>): JsonObject = buildJsonObject {
        for (l in links) put(l.labelName, buildJsonArray {
            add(JsonPrimitive(l.orderNumber)); add(JsonPrimitive(l.indentLevel)); add(JsonPrimitive(l.expandContent))
        })
    }

    /** Label links of a record: names from "labelNames", layout from "ab" where this device left it. */
    fun linksFromJson(p: JsonObject): List<AbLink> {
        val names = (p["labelNames"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.distinct()
        val layout = (p["ab"] as? JsonObject)?.get("labels") as? JsonObject
        val sortOrder = (p["sortOrder"] as? JsonPrimitive)?.intOrNull ?: -1
        val indent = (p["indent"] as? JsonPrimitive)?.intOrNull ?: 0
        return names.map { n ->
            val a = layout?.get(n) as? JsonArray
            if (a != null && a.size >= 3) {
                AbLink(n, (a[0] as? JsonPrimitive)?.intOrNull ?: -1, (a[1] as? JsonPrimitive)?.intOrNull ?: 0,
                    (a[2] as? JsonPrimitive)?.contentOrNull != "false")
            } else {
                AbLink(n, sortOrder, indent, true)
            }
        }
    }

    private fun primaryLink(links: List<AbLink>, primary: String?): AbLink? =
        links.firstOrNull { it.labelName == primary } ?: links.firstOrNull()

    private fun putCommon(o: MutableMap<String, JsonElement>, ab: MutableMap<String, JsonElement>,
                          links: List<AbLink>, primary: String?, createdMs: Long, updatedMs: Long, customIcon: String?) {
        o["labelNames"] = JsonArray(links.map { JsonPrimitive(it.labelName) })
        val pl = primaryLink(links, primary)
        o["sortOrder"] = JsonPrimitive(maxOf(0, pl?.orderNumber ?: 0))
        if ((pl?.indentLevel ?: 0) != 0) o["indent"] = JsonPrimitive(pl!!.indentLevel)
        o["created"] = JsonPrimitive(createdMs / 1000)
        o["modified"] = JsonPrimitive(updatedMs / 1000)
        if (links.isNotEmpty()) ab["labels"] = linksJson(links)
        primary?.let { ab["primaryLabel"] = JsonPrimitive(it) }
        customIcon?.let { ab["customIcon"] = JsonPrimitive(it) }
    }

    // ---------------- Bible bookmarks ----------------

    fun bibleToJson(b: AbBibleBookmark, loc: PhosLocation): JsonObject {
        val o = LinkedHashMap<String, JsonElement>()
        val ab = LinkedHashMap<String, JsonElement>()
        o["id"] = JsonPrimitive(b.idHex)
        o["kind"] = JsonPrimitive(if (b.ordinalEnd > b.ordinalStart) "range" else "verse")
        b.module?.let { o["module"] = JsonPrimitive(it) }
        o["bookOsis"] = JsonPrimitive(loc.bookOsis)
        o["bookIdx"] = JsonPrimitive(loc.bookIdx)
        o["chap"] = JsonPrimitive(loc.chap)
        o["vStart"] = JsonPrimitive(loc.vStart)
        o["vEnd"] = JsonPrimitive(loc.vEnd)
        putNote(o, ab, b.note)
        putCommon(o, ab, b.links, b.primaryLabel, b.createdMs, b.updatedMs, b.customIcon)
        ab["v11n"] = JsonPrimitive(b.v11n)
        ab["ordinalStart"] = JsonPrimitive(b.ordinalStart)
        ab["ordinalEnd"] = JsonPrimitive(b.ordinalEnd)
        loc.endChap?.let { ab["endChap"] = JsonPrimitive(it) }
        loc.endBookOsis?.let { ab["endBookOsis"] = JsonPrimitive(it) }
        loc.endVerse?.let { ab["endVerse"] = JsonPrimitive(it) }
        b.startOffset?.let { ab["startOffset"] = JsonPrimitive(it) }
        b.endOffset?.let { ab["endOffset"] = JsonPrimitive(it) }
        ab["wholeVerse"] = JsonPrimitive(b.wholeVerse)
        o["ab"] = JsonObject(ab)
        return JsonObject(o)
    }

    /** The Phos location a record names (null when it names no Bible verse). */
    fun locationOf(p: JsonObject): PhosLocation? {
        val osis = p.str("bookOsis")?.takeIf { it.isNotEmpty() } ?: return null
        val ab = p["ab"] as? JsonObject
        val chap = p.intOr("chap", 1)
        val vStart = p.intOr("vStart", 1)
        return PhosLocation(osis, p.intOr("bookIdx", -1), chap, vStart, maxOf(p.intOr("vEnd", vStart), 0),
            ab?.str("endBookOsis"), (ab?.get("endChap") as? JsonPrimitive)?.intOrNull,
            (ab?.get("endVerse") as? JsonPrimitive)?.intOrNull)
    }

    /**
     * The bookmark a record describes, in [v11n] (the module's versification). The exact ordinals of
     * "ab" are used while they still point where the record does, so a round trip changes nothing.
     */
    fun bibleFromJson(idHex: String, p: JsonObject, v11n: Versification, existing: AbBibleBookmark?): AbBibleBookmark? {
        val loc = locationOf(p) ?: return null
        val ab = p["ab"] as? JsonObject
        val abStart = (ab?.get("ordinalStart") as? JsonPrimitive)?.intOrNull
        val abEnd = (ab?.get("ordinalEnd") as? JsonPrimitive)?.intOrNull
        val ords = if (ab?.str("v11n") == v11n.name && abStart != null && abEnd != null &&
            PhosVerses.location(v11n, abStart, abEnd)?.let { samePlace(it, loc) } == true) {
            abStart to abEnd
        } else {
            PhosVerses.ordinals(v11n, loc) ?: return null
        }
        val created = p.longOr("created", 0L)
        val modified = p.longOr("modified", created)
        val links = linksFromJson(p)
        return AbBibleBookmark(
            idHex = idHex,
            v11n = v11n.name,
            ordinalStart = ords.first,
            ordinalEnd = ords.second,
            module = p.str("module")?.takeIf { it.isNotEmpty() },
            startOffset = (ab?.get("startOffset") as? JsonPrimitive)?.intOrNull,
            endOffset = (ab?.get("endOffset") as? JsonPrimitive)?.intOrNull,
            wholeVerse = ab?.boolOr("wholeVerse", true) ?: true,
            createdMs = keepMs(existing?.createdMs, created),
            updatedMs = keepMs(existing?.updatedMs, modified),
            customIcon = ab?.str("customIcon"),
            note = noteFromJson(p),
            links = links,
            primaryLabel = ab?.str("primaryLabel")?.takeIf { n -> links.any { it.labelName == n } } ?: links.firstOrNull()?.labelName,
        )
    }

    private fun samePlace(a: PhosLocation, b: PhosLocation) =
        a.bookOsis == b.bookOsis && a.chap == b.chap && a.vStart == b.vStart && a.vEnd == b.vEnd &&
            a.endChap == b.endChap && a.endBookOsis == b.endBookOsis && a.endVerse == b.endVerse

    /** Seconds of a record back to ms, keeping the local ms while they round to the same second. */
    private fun keepMs(localMs: Long?, seconds: Long): Long =
        if (localMs != null && localMs / 1000 == seconds) localMs else seconds * 1000

    // ---------------- generic bookmarks ----------------

    fun genericToJson(b: AbGenericBookmark): JsonObject {
        val o = LinkedHashMap<String, JsonElement>()
        val ab = LinkedHashMap<String, JsonElement>()
        o["id"] = JsonPrimitive(b.idHex)
        o["kind"] = JsonPrimitive("entry")
        o["module"] = JsonPrimitive(b.bookInitials)
        o["entryKey"] = JsonPrimitive(b.key)
        putNote(o, ab, b.note)
        putCommon(o, ab, b.links, b.primaryLabel, b.createdMs, b.updatedMs, b.customIcon)
        b.ordinalStart?.let { ab["ordinalStart"] = JsonPrimitive(it) }
        b.ordinalEnd?.let { ab["ordinalEnd"] = JsonPrimitive(it) }
        b.startOffset?.let { ab["startOffset"] = JsonPrimitive(it) }
        b.endOffset?.let { ab["endOffset"] = JsonPrimitive(it) }
        ab["wholeVerse"] = JsonPrimitive(b.wholeVerse)
        o["ab"] = JsonObject(ab)
        return JsonObject(o)
    }

    fun genericFromJson(idHex: String, p: JsonObject, existing: AbGenericBookmark?): AbGenericBookmark? {
        val module = p.str("module")?.takeIf { it.isNotEmpty() } ?: return null
        val key = p.str("entryKey")?.takeIf { it.isNotEmpty() } ?: return null
        val ab = p["ab"] as? JsonObject
        val created = p.longOr("created", 0L)
        val modified = p.longOr("modified", created)
        val links = linksFromJson(p)
        return AbGenericBookmark(
            idHex = idHex,
            key = key,
            bookInitials = module,
            ordinalStart = (ab?.get("ordinalStart") as? JsonPrimitive)?.intOrNull,
            ordinalEnd = (ab?.get("ordinalEnd") as? JsonPrimitive)?.intOrNull,
            startOffset = (ab?.get("startOffset") as? JsonPrimitive)?.intOrNull,
            endOffset = (ab?.get("endOffset") as? JsonPrimitive)?.intOrNull,
            wholeVerse = ab?.boolOr("wholeVerse", true) ?: true,
            createdMs = keepMs(existing?.createdMs, created),
            updatedMs = keepMs(existing?.updatedMs, modified),
            customIcon = ab?.str("customIcon"),
            note = noteFromJson(p),
            links = links,
            primaryLabel = ab?.str("primaryLabel")?.takeIf { n -> links.any { it.labelName == n } } ?: links.firstOrNull()?.labelName,
        )
    }

    // ---------------- study pad text entries ----------------

    /** A study pad text entry is the Phos clients' "own note": no target, one label, the note. */
    fun studyPadToJson(e: AbStudyPadEntry): JsonObject {
        val o = LinkedHashMap<String, JsonElement>()
        val ab = LinkedHashMap<String, JsonElement>()
        o["id"] = JsonPrimitive(e.idHex)
        o["kind"] = JsonPrimitive("entry")
        putNote(o, ab, AbNote(e.text, e.contentType))
        if (!o.containsKey("noteMd")) o["noteMd"] = JsonPrimitive("")
        o["labelNames"] = JsonArray(listOf(JsonPrimitive(e.labelName)))
        o["sortOrder"] = JsonPrimitive(maxOf(0, e.orderNumber))
        if (e.indentLevel != 0) o["indent"] = JsonPrimitive(e.indentLevel)
        ab["studyPad"] = JsonPrimitive(true)
        ab["orderNumber"] = JsonPrimitive(e.orderNumber)
        o["ab"] = JsonObject(ab)
        return JsonObject(o)
    }

    /** True when a record is an own note without target (stored as a study pad entry here). */
    fun isStudyPadNote(p: JsonObject): Boolean =
        p.str("bookOsis").isNullOrEmpty() &&
            (p.str("module").isNullOrEmpty() || p.str("entryKey").isNullOrEmpty()) &&
            (!p.str("noteMd").isNullOrEmpty() || !p.str("noteHtml").isNullOrEmpty() ||
                (p["ab"] as? JsonObject)?.boolOr("studyPad", false) == true)

    fun studyPadFromJson(idHex: String, p: JsonObject): AbStudyPadEntry? {
        val label = (p["labelNames"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return null
        val ab = p["ab"] as? JsonObject
        val note = noteFromJson(p) ?: AbNote(p.str("noteMd").orEmpty(), MARKDOWN)
        val sortOrder = p.intOr("sortOrder", 0)
        val abOrder = (ab?.get("orderNumber") as? JsonPrimitive)?.intOrNull
        return AbStudyPadEntry(
            idHex = idHex,
            labelName = label,
            orderNumber = if (abOrder != null && maxOf(0, abOrder) == sortOrder) abOrder else sortOrder,
            indentLevel = p.intOr("indent", 0),
            text = note.text,
            contentType = note.contentType,
        )
    }

    // ---------------- overlay ----------------

    /**
     * What this device sends for a bookmark: its own fields over the server's copy. The id stays the
     * sender's, and a module this device does not have installed is not dropped.
     */
    fun bookmarkOverlay(server: JsonObject?, mine: JsonObject): JsonObject {
        if (server == null) return mine
        val out = LinkedHashMap<String, JsonElement>()
        for ((k, v) in server) if (k !in bookmarkFields) out[k] = v
        out.putAll(mine)
        server["id"]?.let { out["id"] = it }
        if (!mine.containsKey("module") && server.containsKey("module")) out["module"] = server["module"]!!
        // the note changed here: an RTF copy of the old note on the desktop no longer matches
        if (server.str("noteMd").orEmpty() != mine.str("noteMd").orEmpty()) out.remove("noteHtml")
        return JsonObject(out)
    }
}

internal fun JsonObject.intOr(k: String, d: Int): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: d
internal fun JsonObject.longOr(k: String, d: Long): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: d
internal fun JsonObject.boolOr(k: String, d: Boolean): Boolean = when ((this[k] as? JsonPrimitive)?.contentOrNull) {
    "true" -> true
    "false" -> false
    else -> d
}
