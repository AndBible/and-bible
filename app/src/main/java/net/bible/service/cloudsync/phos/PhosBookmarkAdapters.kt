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

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.JsonObject
import net.bible.android.common.toV11n
import net.bible.android.database.bookmarks.AI_LABEL_NAME
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_NAME
import net.bible.android.database.bookmarks.defaultLabelColor
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications

/*
 * The bookmark database seen as Phos records: "label" records (by name) and "bookmark" records
 * (Bible bookmarks, bookmarks on other documents and study pad text entries, all keyed
 * bm:<uuid hex>). Writes go through plain SQL so the sync triggers record them like any other
 * change, and the classic AndBible sync carries them on when it is enabled too.
 */

private val SPECIAL_LABELS = setOf(SPEAK_LABEL_NAME, UNLABELED_NAME, PARAGRAH_BREAK_LABEL_NAME, AI_LABEL_NAME)

internal fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
internal fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)
internal fun Cursor.hexId(i: Int): String? = if (isNull(i)) null else getBlob(i)?.takeIf { it.size == 16 }?.toHex()

internal fun SupportSQLiteDatabase.rows(sql: String, args: Array<Any?> = emptyArray(), each: (Cursor) -> Unit) {
    query(sql, args).use { c -> while (c.moveToNext()) each(c) }
}

internal fun blob(hex: String): ByteArray = ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

/** Labels of the bookmark database, by id and by name. */
internal class LabelIndex(db: SupportSQLiteDatabase) {
    val byId = HashMap<String, AbLabel>()
    val byName = HashMap<String, AbLabel>()

    init {
        db.rows("SELECT id, name, color, markerStyle, markerStyleWholeVerse, underlineStyle, underlineStyleWholeVerse, " +
            "hideStyle, hideStyleWholeVerse, favourite, type, customIcon FROM Label") { c ->
            val id = c.hexId(0) ?: return@rows
            val l = AbLabel(id, c.str(1).orEmpty(), c.getInt(2), c.getInt(3) != 0, c.getInt(4) != 0, c.getInt(5) != 0,
                c.getInt(6) != 0, c.getInt(7) != 0, c.getInt(8) != 0, c.getInt(9) != 0, c.str(10), c.str(11))
            byId[id] = l
            byName.putIfAbsent(l.name, l)
        }
    }

    fun isSpecial(name: String) = name in SPECIAL_LABELS
}

internal fun writeLabel(db: SupportSQLiteDatabase, l: AbLabel) {
    db.execSQL("INSERT INTO Label (id, name, color, markerStyle, markerStyleWholeVerse, underlineStyle, underlineStyleWholeVerse, " +
        "hideStyle, hideStyleWholeVerse, favourite, type, customIcon) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
        "ON CONFLICT(id) DO UPDATE SET name=excluded.name, color=excluded.color, markerStyle=excluded.markerStyle, " +
        "markerStyleWholeVerse=excluded.markerStyleWholeVerse, underlineStyle=excluded.underlineStyle, " +
        "underlineStyleWholeVerse=excluded.underlineStyleWholeVerse, hideStyle=excluded.hideStyle, " +
        "hideStyleWholeVerse=excluded.hideStyleWholeVerse, favourite=excluded.favourite, type=excluded.type, customIcon=excluded.customIcon",
        arrayOf<Any?>(blob(l.idHex), l.name, l.color, l.markerStyle.i, l.markerStyleWholeVerse.i, l.underlineStyle.i,
            l.underlineStyleWholeVerse.i, l.hideStyle.i, l.hideStyleWholeVerse.i, l.favourite.i, l.type, l.customIcon))
}

private val Boolean.i get() = if (this) 1 else 0

/** Colour of a label created for a name that only a bookmark record named so far. */
private val DEFAULT_LABEL_COLOR get() = defaultLabelColor

class PhosLabelAdapter(private val db: () -> SupportSQLiteDatabase) : PhosAdapter {
    override val type = "label"
    override val priority = 10
    override val ownedFields = PhosBookmarkCodec.labelFields

    override fun enumerate(): List<PhosItem> {
        val d = db()
        val idx = LabelIndex(d)
        // AndBible's example labels travel only once a bookmark of the user's own uses them
        val used = HashSet<String>()
        for (t in listOf("BibleBookmarkToLabel" to "BibleBookmark", "GenericBookmarkToLabel" to "GenericBookmark")) {
            val typeFilter = if (t.second == "BibleBookmark") " WHERE b.type IS NULL OR b.type != 'EXAMPLE'" else ""
            d.rows("SELECT DISTINCT t.labelId FROM ${t.first} t JOIN ${t.second} b ON b.id=t.bookmarkId$typeFilter") { c ->
                c.hexId(0)?.let { used += it }
            }
        }
        d.rows("SELECT DISTINCT labelId FROM StudyPadTextEntry") { c -> c.hexId(0)?.let { used += it } }
        return idx.byId.values.filter { it.name.isNotEmpty() && !idx.isSpecial(it.name) && (it.type != "EXAMPLE" || it.idHex in used) }
            .sortedBy { it.name }
            .map { PhosItem("label:" + it.name, PhosBookmarkCodec.labelToJson(it)) }
    }

    override fun apply(key: String, payload: JsonObject?, deleted: Boolean): Boolean {
        val name = key.removePrefix("label:")
        if (name.isEmpty() || name in SPECIAL_LABELS) return false
        val d = db()
        val idx = LabelIndex(d)
        val existing = idx.byName[name]
        if (deleted) {
            if (existing != null) d.execSQL("DELETE FROM Label WHERE id=?", arrayOf<Any?>(blob(existing.idHex)))
            return true
        }
        val l = PhosBookmarkCodec.labelFromJson(name, payload ?: return false, existing, DEFAULT_LABEL_COLOR)
        if (l != existing) writeLabel(d, l)
        return true
    }
}

/**
 * Bible bookmarks, bookmarks on other documents and study pad text entries. The three share one
 * id space, as "bookmark" records share one key space on the Phos side.
 */
class PhosBookmarkAdapter(private val db: () -> SupportSQLiteDatabase) : PhosAdapter, PhosKeyMapper {
    override val type = "bookmark"
    override val priority = 20
    override val ownedFields = PhosBookmarkCodec.bookmarkFields

    override fun overlay(server: JsonObject?, mine: JsonObject): JsonObject = PhosBookmarkCodec.bookmarkOverlay(server, mine)

    override fun localKeyFor(serverKey: String): String = "bm:" + PhosIds.bookmarkUuidHex(serverKey.removePrefix("bm:"))

    override fun modifiedMs(payload: JsonObject): Long = payload.longOr("modified", 0L) * 1000

    private val v11nCache = HashMap<String, Versification?>()

    private fun v11n(name: String?): Versification? {
        if (name.isNullOrEmpty()) return null
        return v11nCache.getOrPut(name) {
            try { Versifications.instance().getVersification(name) } catch (e: Exception) { null }
        }
    }

    private fun moduleV11n(module: String?): Versification? = module?.let {
        try { (Books.installed().getBook(it) as? AbstractPassageBook)?.versification } catch (e: Exception) { null }
    }

    // ---------------- reading ----------------

    private fun links(d: SupportSQLiteDatabase, table: String, labels: LabelIndex): Map<String, List<AbLink>> {
        val out = HashMap<String, MutableList<AbLink>>()
        d.rows("SELECT bookmarkId, labelId, orderNumber, indentLevel, expandContent FROM $table") { c ->
            val b = c.hexId(0) ?: return@rows
            val l = labels.byId[c.hexId(1) ?: return@rows] ?: return@rows
            if (labels.isSpecial(l.name)) return@rows
            out.getOrPut(b) { mutableListOf() } += AbLink(l.name, c.getInt(2), c.getInt(3), c.getInt(4) != 0)
        }
        for (list in out.values) list.sortBy { it.labelName }
        return out
    }

    internal fun readBible(d: SupportSQLiteDatabase, labels: LabelIndex, only: String? = null): List<AbBibleBookmark> {
        val links = links(d, "BibleBookmarkToLabel", labels)
        val out = mutableListOf<AbBibleBookmark>()
        val where = if (only != null) " WHERE b.id=?" else ""
        d.rows("SELECT b.id, b.v11n, b.ordinalStart, b.ordinalEnd, b.book, b.startOffset, b.endOffset, b.wholeVerse, " +
            "b.createdAt, b.lastUpdatedOn, b.customIcon, b.primaryLabelId, n.notes, n.contentType " +
            "FROM BibleBookmark b LEFT JOIN BibleBookmarkNotes n ON n.bookmarkId=b.id$where" +
            (if (only == null) " WHERE b.type IS NULL OR b.type != 'EXAMPLE'" else ""),
            if (only != null) arrayOf<Any?>(blob(only)) else emptyArray()) { c ->
            val id = c.hexId(0) ?: return@rows
            out += AbBibleBookmark(id, c.str(1).orEmpty(), c.getInt(2), c.getInt(3), c.str(4), c.intOrNull(5), c.intOrNull(6),
                c.getInt(7) != 0, c.getLong(8), c.getLong(9), c.str(10),
                c.str(12)?.let { AbNote(it, c.str(13)) }, links[id].orEmpty(),
                c.hexId(11)?.let { labels.byId[it]?.name }?.takeIf { !labels.isSpecial(it) })
        }
        return out
    }

    internal fun readGeneric(d: SupportSQLiteDatabase, labels: LabelIndex, only: String? = null): List<AbGenericBookmark> {
        val links = links(d, "GenericBookmarkToLabel", labels)
        val out = mutableListOf<AbGenericBookmark>()
        val where = if (only != null) " WHERE g.id=?" else ""
        d.rows("SELECT g.id, g.`key`, g.bookInitials, g.ordinalStart, g.ordinalEnd, g.startOffset, g.endOffset, g.wholeVerse, " +
            "g.createdAt, g.lastUpdatedOn, g.customIcon, g.primaryLabelId, n.notes, n.contentType " +
            "FROM GenericBookmark g LEFT JOIN GenericBookmarkNotes n ON n.bookmarkId=g.id$where",
            if (only != null) arrayOf<Any?>(blob(only)) else emptyArray()) { c ->
            val id = c.hexId(0) ?: return@rows
            out += AbGenericBookmark(id, c.str(1).orEmpty(), c.str(2).orEmpty(), c.intOrNull(3), c.intOrNull(4), c.intOrNull(5),
                c.intOrNull(6), c.getInt(7) != 0, c.getLong(8), c.getLong(9), c.str(10),
                c.str(12)?.let { AbNote(it, c.str(13)) }, links[id].orEmpty(),
                c.hexId(11)?.let { labels.byId[it]?.name }?.takeIf { !labels.isSpecial(it) })
        }
        return out
    }

    internal fun readStudyPad(d: SupportSQLiteDatabase, labels: LabelIndex, only: String? = null): List<AbStudyPadEntry> {
        val out = mutableListOf<AbStudyPadEntry>()
        val where = if (only != null) " WHERE e.id=?" else ""
        d.rows("SELECT e.id, e.labelId, e.orderNumber, e.indentLevel, e.contentType, t.text " +
            "FROM StudyPadTextEntry e JOIN StudyPadTextEntryText t ON t.studyPadTextEntryId=e.id$where",
            if (only != null) arrayOf<Any?>(blob(only)) else emptyArray()) { c ->
            val id = c.hexId(0) ?: return@rows
            val label = labels.byId[c.hexId(1) ?: return@rows] ?: return@rows
            out += AbStudyPadEntry(id, label.name, c.getInt(2), c.getInt(3), c.str(5).orEmpty(), c.str(4))
        }
        return out
    }

    override fun enumerate(): List<PhosItem> {
        val d = db()
        val labels = LabelIndex(d)
        val out = mutableListOf<PhosItem>()
        for (b in readBible(d, labels)) {
            val v = v11n(b.v11n) ?: continue
            val loc = PhosVerses.location(v, b.ordinalStart, b.ordinalEnd) ?: continue
            out += PhosItem("bm:" + b.idHex, PhosBookmarkCodec.bibleToJson(b, loc))
        }
        for (b in readGeneric(d, labels)) {
            if (b.bookInitials.isEmpty() || b.key.isEmpty()) continue
            out += PhosItem("bm:" + b.idHex, PhosBookmarkCodec.genericToJson(b))
        }
        for (e in readStudyPad(d, labels)) {
            if (labels.isSpecial(e.labelName)) continue
            out += PhosItem("bm:" + e.idHex, PhosBookmarkCodec.studyPadToJson(e))
        }
        return out
    }

    // ---------------- writing ----------------

    override fun apply(key: String, payload: JsonObject?, deleted: Boolean): Boolean {
        val id = key.removePrefix("bm:")
        if (!PhosIds.isUuidHex(id)) return false
        val d = db()
        if (deleted || payload == null) {
            deleteAll(d, id)
            return true
        }
        val labels = LabelIndex(d)
        if (PhosBookmarkCodec.isStudyPadNote(payload)) {
            val e = PhosBookmarkCodec.studyPadFromJson(id, payload) ?: return false
            if (labels.isSpecial(e.labelName)) return false
            if (readStudyPad(d, labels, id).firstOrNull() == e) return true
            deleteBookmarks(d, id)
            val labelId = ensureLabel(d, labels, e.labelName)
            d.execSQL("INSERT INTO StudyPadTextEntry (id, labelId, orderNumber, indentLevel, contentType) VALUES (?,?,?,?,?) " +
                "ON CONFLICT(id) DO UPDATE SET labelId=excluded.labelId, orderNumber=excluded.orderNumber, " +
                "indentLevel=excluded.indentLevel, contentType=excluded.contentType",
                arrayOf<Any?>(blob(id), blob(labelId), e.orderNumber, e.indentLevel, e.contentType))
            d.execSQL("INSERT INTO StudyPadTextEntryText (studyPadTextEntryId, text) VALUES (?,?) " +
                "ON CONFLICT(studyPadTextEntryId) DO UPDATE SET text=excluded.text", arrayOf<Any?>(blob(id), e.text))
            return true
        }
        val loc = PhosBookmarkCodec.locationOf(payload)
        if (loc != null) {
            val existing = readBible(d, labels, id).firstOrNull()
            val module = payload.str("module")
            val v = moduleV11n(module) ?: existing?.let { v11n(it.v11n) }
                ?: v11n((payload["ab"] as? JsonObject)?.str("v11n")) ?: KJVA
            val b = PhosBookmarkCodec.bibleFromJson(id, payload, v, existing) ?: return false
            if (b == existing) return true
            d.execSQL("DELETE FROM GenericBookmark WHERE id=?", arrayOf<Any?>(blob(id)))
            d.execSQL("DELETE FROM StudyPadTextEntry WHERE id=?", arrayOf<Any?>(blob(id)))
            writeBible(d, labels, b, v)
            return true
        }
        val existing = readGeneric(d, labels, id).firstOrNull()
        val g = PhosBookmarkCodec.genericFromJson(id, payload, existing) ?: return false
        if (g == existing) return true
        d.execSQL("DELETE FROM BibleBookmark WHERE id=?", arrayOf<Any?>(blob(id)))
        d.execSQL("DELETE FROM StudyPadTextEntry WHERE id=?", arrayOf<Any?>(blob(id)))
        writeGeneric(d, labels, g)
        return true
    }

    private fun deleteBookmarks(d: SupportSQLiteDatabase, id: String) {
        d.execSQL("DELETE FROM BibleBookmark WHERE id=?", arrayOf<Any?>(blob(id)))
        d.execSQL("DELETE FROM GenericBookmark WHERE id=?", arrayOf<Any?>(blob(id)))
    }

    private fun deleteAll(d: SupportSQLiteDatabase, id: String) {
        deleteBookmarks(d, id)
        d.execSQL("DELETE FROM StudyPadTextEntry WHERE id=?", arrayOf<Any?>(blob(id)))
    }

    /** The id of the label with this name, created when no label has it yet. */
    private fun ensureLabel(d: SupportSQLiteDatabase, labels: LabelIndex, name: String): String {
        labels.byName[name]?.let { return it.idHex }
        val l = AbLabel(PhosIds.labelUuidHex(name), name, DEFAULT_LABEL_COLOR, type = "HIGHLIGHT")
        val taken = labels.byId[l.idHex]
        val label = if (taken != null) l.copy(idHex = PhosIds.derivedUuidHex("phos-label-" + System.nanoTime(), name)) else l
        writeLabel(d, label)
        labels.byId[label.idHex] = label
        labels.byName[name] = label
        return label.idHex
    }

    private fun kjvOrdinals(v: Versification, start: Int, end: Int): Pair<Int, Int> = try {
        val r = VerseRange(v, Verse(v, start), Verse(v, end)).toV11n(KJVA)
        r.start.ordinal to r.end.ordinal
    } catch (e: Exception) {
        start to end
    }

    private fun writeBible(d: SupportSQLiteDatabase, labels: LabelIndex, b: AbBibleBookmark, v: Versification) {
        val (kjvStart, kjvEnd) = kjvOrdinals(v, b.ordinalStart, b.ordinalEnd)
        val primary = b.primaryLabel?.let { ensureLabel(d, labels, it) }
        d.execSQL("INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt, book, " +
            "startOffset, endOffset, primaryLabelId, lastUpdatedOn, wholeVerse, customIcon) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT(id) DO UPDATE SET kjvOrdinalStart=excluded.kjvOrdinalStart, kjvOrdinalEnd=excluded.kjvOrdinalEnd, " +
            "ordinalStart=excluded.ordinalStart, ordinalEnd=excluded.ordinalEnd, v11n=excluded.v11n, createdAt=excluded.createdAt, " +
            "book=excluded.book, startOffset=excluded.startOffset, endOffset=excluded.endOffset, primaryLabelId=excluded.primaryLabelId, " +
            "lastUpdatedOn=excluded.lastUpdatedOn, wholeVerse=excluded.wholeVerse, customIcon=excluded.customIcon",
            arrayOf<Any?>(blob(b.idHex), kjvStart, kjvEnd, b.ordinalStart, b.ordinalEnd, b.v11n, b.createdMs, b.module,
                b.startOffset, b.endOffset, primary?.let { blob(it) }, b.updatedMs, b.wholeVerse.i, b.customIcon))
        writeNote(d, "BibleBookmarkNotes", b.idHex, b.note)
        writeLinks(d, "BibleBookmarkToLabel", labels, b.idHex, b.links)
    }

    private fun writeGeneric(d: SupportSQLiteDatabase, labels: LabelIndex, g: AbGenericBookmark) {
        val primary = g.primaryLabel?.let { ensureLabel(d, labels, it) }
        d.execSQL("INSERT INTO GenericBookmark (id, `key`, createdAt, bookInitials, ordinalStart, ordinalEnd, startOffset, endOffset, " +
            "primaryLabelId, lastUpdatedOn, wholeVerse, customIcon) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT(id) DO UPDATE SET `key`=excluded.`key`, createdAt=excluded.createdAt, bookInitials=excluded.bookInitials, " +
            "ordinalStart=excluded.ordinalStart, ordinalEnd=excluded.ordinalEnd, startOffset=excluded.startOffset, " +
            "endOffset=excluded.endOffset, primaryLabelId=excluded.primaryLabelId, lastUpdatedOn=excluded.lastUpdatedOn, " +
            "wholeVerse=excluded.wholeVerse, customIcon=excluded.customIcon",
            arrayOf<Any?>(blob(g.idHex), g.key, g.createdMs, g.bookInitials, g.ordinalStart, g.ordinalEnd, g.startOffset,
                g.endOffset, primary?.let { blob(it) }, g.updatedMs, g.wholeVerse.i, g.customIcon))
        writeNote(d, "GenericBookmarkNotes", g.idHex, g.note)
        writeLinks(d, "GenericBookmarkToLabel", labels, g.idHex, g.links)
    }

    private fun writeNote(d: SupportSQLiteDatabase, table: String, id: String, note: AbNote?) {
        if (note == null) {
            d.execSQL("DELETE FROM $table WHERE bookmarkId=?", arrayOf<Any?>(blob(id)))
            return
        }
        d.execSQL("INSERT INTO $table (bookmarkId, notes, contentType) VALUES (?,?,?) " +
            "ON CONFLICT(bookmarkId) DO UPDATE SET notes=excluded.notes, contentType=excluded.contentType " +
            "WHERE notes IS NOT excluded.notes OR contentType IS NOT excluded.contentType",
            arrayOf<Any?>(blob(id), note.text, note.contentType))
    }

    /** Sets the bookmark's labels; links to AndBible's special labels stay as they are. */
    private fun writeLinks(d: SupportSQLiteDatabase, table: String, labels: LabelIndex, id: String, links: List<AbLink>) {
        val current = HashMap<String, AbLink>()
        d.rows("SELECT labelId, orderNumber, indentLevel, expandContent FROM $table WHERE bookmarkId=?", arrayOf<Any?>(blob(id))) { c ->
            val l = labels.byId[c.hexId(0) ?: return@rows] ?: return@rows
            current[l.idHex] = AbLink(l.name, c.getInt(1), c.getInt(2), c.getInt(3) != 0)
        }
        val wanted = HashMap<String, AbLink>()
        for (l in links) if (!labels.isSpecial(l.labelName)) wanted[ensureLabel(d, labels, l.labelName)] = l
        for ((labelId, l) in current) {
            if (labelId !in wanted && !labels.isSpecial(l.labelName)) {
                d.execSQL("DELETE FROM $table WHERE bookmarkId=? AND labelId=?", arrayOf<Any?>(blob(id), blob(labelId)))
            }
        }
        for ((labelId, l) in wanted) {
            if (current[labelId] == l) continue
            d.execSQL("INSERT INTO $table (bookmarkId, labelId, orderNumber, indentLevel, expandContent) VALUES (?,?,?,?,?) " +
                "ON CONFLICT(bookmarkId, labelId) DO UPDATE SET orderNumber=excluded.orderNumber, " +
                "indentLevel=excluded.indentLevel, expandContent=excluded.expandContent",
                arrayOf<Any?>(blob(id), blob(labelId), l.orderNumber, l.indentLevel, l.expandContent.i))
        }
    }
}
