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

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.versification.BibleBook

/*
 * Workspaces as Phos "workspace" records: ws:<workspace id hex> with the name and one panel per
 * window. Only what the Phos clients know is mapped (document, category, Bible position, key,
 * width, sync group, Strong's); everything else of a window (display settings, history, layout
 * state) stays as AndBible has it. Windows match panels by position.
 */

/** One window of a workspace in Phos terms. */
data class PhosPanel(
    val module: String,
    val kind: String,
    val width: Double,
    val groupId: Int,
    val strongsAsTag: Boolean,
    val bookIndex: Int,
    val chapIndex: Int,
    val verseIndex: Int,
    val dictKey: String?,
)

object PhosWorkspaceCodec {
    val panelFields = setOf("module", "kind", "width", "groupId", "strongsAsTag", "bookIndex", "chapIndex", "verseIndex", "dictKey")

    fun kindOf(category: String): String = when (category) {
        "BIBLE" -> "Bible"
        "COMMENTARY" -> "Commentary"
        "DICTIONARY" -> "Dictionary"
        "MAPS" -> "Map"
        else -> "Book"
    }

    fun categoryOf(kind: String): String = when (kind) {
        "Bible" -> "BIBLE"
        "Commentary" -> "COMMENTARY"
        "Dictionary" -> "DICTIONARY"
        "Map" -> "MAPS"
        else -> "GENERAL_BOOK"
    }

    /** JSword BibleBook ordinal (with its intro pseudo books) to the 0-based 66 book canon index. */
    fun canonIndexOfOrdinal(ordinal: Int): Int =
        BibleBook.entries.getOrNull(ordinal)?.let { PhosVerses.canonIndex(it) }?.takeIf { it >= 0 } ?: 0

    fun ordinalOfCanonIndex(index: Int): Int {
        val i = index.coerceIn(0, 65)
        val book = if (i < 39) BibleBook.entries[BibleBook.GEN.ordinal + i] else BibleBook.entries[BibleBook.MATT.ordinal + i - 39]
        return book.ordinal
    }

    fun panelToJson(p: PhosPanel): JsonObject {
        val o = LinkedHashMap<String, JsonElement>()
        o["module"] = JsonPrimitive(p.module)
        o["kind"] = JsonPrimitive(p.kind)
        o["width"] = JsonPrimitive(p.width)
        o["groupId"] = JsonPrimitive(p.groupId)
        o["strongsAsTag"] = JsonPrimitive(p.strongsAsTag)
        o["bookIndex"] = JsonPrimitive(p.bookIndex)
        o["chapIndex"] = JsonPrimitive(p.chapIndex)
        o["verseIndex"] = JsonPrimitive(p.verseIndex)
        p.dictKey?.takeIf { it.isNotEmpty() }?.let { o["dictKey"] = JsonPrimitive(it) }
        return JsonObject(o)
    }

    fun panelFromJson(o: JsonObject): PhosPanel = PhosPanel(
        module = o.str("module").orEmpty(),
        kind = o.str("kind") ?: "Bible",
        width = (o["width"] as? JsonPrimitive)?.doubleOrNull ?: 1.0,
        groupId = o.intOr("groupId", 0),
        strongsAsTag = o.boolOr("strongsAsTag", false),
        bookIndex = o.intOr("bookIndex", 0),
        chapIndex = o.intOr("chapIndex", 0),
        verseIndex = o.intOr("verseIndex", 1),
        dictKey = o.str("dictKey") ?: o.str("entryKey"),
    )

    fun workspaceToJson(idHex: String, name: String, panels: List<PhosPanel>): JsonObject = JsonObject(mapOf(
        "id" to JsonPrimitive(idHex),
        "name" to JsonPrimitive(name),
        "panels" to JsonArray(panels.map { panelToJson(it) }),
    ))

    /** Own fields over the server's copy, panel by panel, so what others keep per panel survives. */
    fun overlay(server: JsonObject?, mine: JsonObject): JsonObject {
        if (server == null) return mine
        val out = LinkedHashMap<String, JsonElement>(server)
        out["id"] = server["id"] ?: mine["id"]!!
        out["name"] = mine["name"]!!
        val sp = (server["panels"] as? JsonArray).orEmpty()
        val mp = (mine["panels"] as? JsonArray).orEmpty()
        out["panels"] = JsonArray(mp.mapIndexed { i, e ->
            val m = e as JsonObject
            val s = sp.getOrNull(i) as? JsonObject
            if (s == null || s.str("module") != m.str("module") || s.str("kind") != m.str("kind")) m
            else JsonObject(LinkedHashMap<String, JsonElement>().apply {
                for ((k, v) in s) if (k !in panelFields) put(k, v)
                putAll(m)
            })
        })
        return JsonObject(out)
    }
}

private class WindowRow(val idHex: String, val orderNumber: Int, val panel: PhosPanel, val strongsMode: Int?)

class PhosWorkspaceAdapter(private val db: () -> SupportSQLiteDatabase) : PhosAdapter {
    override val type = "workspace"
    override val priority = 30

    override fun overlay(server: JsonObject?, mine: JsonObject): JsonObject = PhosWorkspaceCodec.overlay(server, mine)

    private fun windows(d: SupportSQLiteDatabase, wsHex: String): List<WindowRow> {
        val out = mutableListOf<WindowRow>()
        d.rows("SELECT w.id, w.orderNumber, w.syncGroup, w.window_layout_weight, p.currentCategoryName, p.bible_document, " +
            "p.bible_verse_bibleBook, p.bible_verse_chapterNo, p.bible_verse_verseNo, p.commentary_document, " +
            "p.dictionary_document, p.dictionary_key, p.general_book_document, p.general_book_key, p.map_document, p.map_key, " +
            "p.text_display_settings_strongsMode FROM Window w LEFT JOIN PageManager p ON p.windowId=w.id " +
            "WHERE w.workspaceId=? AND w.isLinksWindow=0 ORDER BY w.orderNumber", arrayOf<Any?>(blob(wsHex))) { c ->
            val id = c.hexId(0) ?: return@rows
            val cat = c.str(4) ?: "BIBLE"
            val (module, key) = when (cat) {
                "BIBLE" -> c.str(5) to null
                "COMMENTARY" -> c.str(9) to null
                "DICTIONARY" -> c.str(10) to c.str(11)
                "MAPS" -> c.str(14) to c.str(15)
                else -> c.str(12) to c.str(13)
            }
            val versed = cat == "BIBLE" || cat == "COMMENTARY"
            val strongs = c.intOrNull(16)
            out += WindowRow(id, c.getInt(1), PhosPanel(
                module = module.orEmpty(),
                kind = PhosWorkspaceCodec.kindOf(cat),
                width = c.getDouble(3),
                groupId = c.getInt(2),
                strongsAsTag = (strongs ?: 0) > 0,
                bookIndex = if (versed) PhosWorkspaceCodec.canonIndexOfOrdinal(c.getInt(6)) else 0,
                chapIndex = if (versed) maxOf(0, c.getInt(7) - 1) else 0,
                verseIndex = if (versed) c.getInt(8) else 1,
                dictKey = key,
            ), strongs)
        }
        return out
    }

    override fun enumerate(): List<PhosItem> {
        val d = db()
        val out = mutableListOf<PhosItem>()
        d.rows("SELECT id, name FROM Workspace ORDER BY orderNumber") { c ->
            val id = c.hexId(0) ?: return@rows
            out += PhosItem("ws:$id", PhosWorkspaceCodec.workspaceToJson(id, c.str(1).orEmpty(), windows(d, id).map { it.panel }))
        }
        return out
    }

    override fun apply(key: String, payload: JsonObject?, deleted: Boolean): Boolean {
        val id = key.removePrefix("ws:")
        if (!PhosIds.isUuidHex(id)) return false          // records keyed by name are retired
        val d = db()
        var exists = false
        var count = 0
        var name: String? = null
        d.rows("SELECT id, name FROM Workspace") { c ->
            count++
            if (c.hexId(0) == id) { exists = true; name = c.str(1) }
        }
        if (deleted || payload == null) {
            if (!exists) return true
            if (count <= 1) return false                    // AndBible always keeps one workspace
            d.execSQL("DELETE FROM Workspace WHERE id=?", arrayOf<Any?>(blob(id)))
            return true
        }
        val newName = payload.str("name") ?: name ?: "Workspace"
        if (!exists) {
            var maxOrder = -1
            d.rows("SELECT MAX(orderNumber) FROM Workspace") { c -> if (!c.isNull(0)) maxOrder = c.getInt(0) }
            d.execSQL("INSERT INTO Workspace (name, id, orderNumber) VALUES (?,?,?)", arrayOf<Any?>(newName, blob(id), maxOrder + 1))
        } else if (newName != name) {
            d.execSQL("UPDATE Workspace SET name=? WHERE id=?", arrayOf<Any?>(newName, blob(id)))
        }
        val panels = (payload["panels"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(PhosWorkspaceCodec::panelFromJson) }
        val current = windows(d, id)
        panels.forEachIndexed { i, p ->
            val w = current.getOrNull(i)
            if (w == null) insertWindow(d, id, i, p, current) else if (w.panel != p) updateWindow(d, w, p)
        }
        for (w in current.drop(panels.size)) d.execSQL("DELETE FROM Window WHERE id=?", arrayOf<Any?>(blob(w.idHex)))
        if (panels.size > current.size) {
            // a brand-new Window's id was never seen before, so the sync event handler's "is this an
            // existing window?" check can't recognize it; a Workspace-table UPSERT log entry is what makes
            // onEventMainThread(WorkspacesUpdatedViaSyncEvent) reload the active workspace in that case
            d.execSQL("UPDATE Workspace SET name=? WHERE id=?", arrayOf<Any?>(newName, blob(id)))
        }
        return true
    }

    private fun v11nName(module: String): String =
        try { (Books.installed().getBook(module) as? AbstractPassageBook)?.versification?.name } catch (e: Exception) { null } ?: "KJVA"

    private fun insertWindow(d: SupportSQLiteDatabase, wsHex: String, index: Int, p: PhosPanel, current: List<WindowRow>) {
        var winId = PhosIds.windowUuidHex(wsHex, index)
        var taken = false
        d.rows("SELECT 1 FROM Window WHERE id=?", arrayOf<Any?>(blob(winId))) { taken = true }
        if (taken) winId = PhosIds.derivedUuidHex("phos-ab-window", "$wsHex#$index#${System.nanoTime()}")
        val order = (current.maxOfOrNull { it.orderNumber } ?: -1) + 1 + (index - current.size)
        d.execSQL("INSERT INTO Window (workspaceId, isSynchronized, isPinMode, isLinksWindow, id, orderNumber, syncGroup, " +
            "window_layout_state, window_layout_weight) VALUES (?,1,0,0,?,?,?,'VISIBLE',?)",
            arrayOf<Any?>(blob(wsHex), blob(winId), order, p.groupId, p.width.takeIf { it > 0 } ?: 1.0))
        d.execSQL("INSERT INTO PageManager (windowId, currentCategoryName, bible_verse_versification, bible_verse_bibleBook, " +
            "bible_verse_chapterNo, bible_verse_verseNo) VALUES (?,?,?,?,?,?)",
            arrayOf<Any?>(blob(winId), "BIBLE", "KJVA", PhosWorkspaceCodec.ordinalOfCanonIndex(0), 1, 1))
        writePage(d, winId, p, null)
    }

    private fun updateWindow(d: SupportSQLiteDatabase, w: WindowRow, p: PhosPanel) {
        if (w.panel.groupId != p.groupId || w.panel.width != p.width) {
            d.execSQL("UPDATE Window SET syncGroup=?, window_layout_weight=? WHERE id=?",
                arrayOf<Any?>(p.groupId, p.width.takeIf { it > 0 } ?: 1.0, blob(w.idHex)))
        }
        val samePage = w.panel.copy(groupId = p.groupId, width = p.width) == p
        if (!samePage) writePage(d, w.idHex, p, w.strongsMode)
    }

    private fun writePage(d: SupportSQLiteDatabase, winHex: String, p: PhosPanel, strongsMode: Int?) {
        val cat = PhosWorkspaceCodec.categoryOf(p.kind)
        val docColumn = when (cat) {
            "BIBLE" -> "bible_document"
            "COMMENTARY" -> "commentary_document"
            "DICTIONARY" -> "dictionary_document"
            "MAPS" -> "map_document"
            else -> "general_book_document"
        }
        val keyColumn = when (cat) {
            "DICTIONARY" -> "dictionary_key"
            "MAPS" -> "map_key"
            "GENERAL_BOOK" -> "general_book_key"
            else -> null
        }
        val strongs = when {
            p.strongsAsTag == ((strongsMode ?: 0) > 0) -> strongsMode
            p.strongsAsTag -> 1
            else -> 0
        }
        val sets = mutableListOf("currentCategoryName=?", "$docColumn=?", "text_display_settings_strongsMode=?")
        val args = mutableListOf<Any?>(cat, p.module.ifEmpty { null }, strongs)
        if (keyColumn != null) { sets += "$keyColumn=?"; args += p.dictKey }
        if (cat == "BIBLE" || cat == "COMMENTARY") {
            sets += listOf("bible_verse_bibleBook=?", "bible_verse_chapterNo=?", "bible_verse_verseNo=?")
            args.addAll(listOf(PhosWorkspaceCodec.ordinalOfCanonIndex(p.bookIndex), p.chapIndex + 1, maxOf(1, p.verseIndex)))
            if (cat == "BIBLE" && p.module.isNotEmpty()) { sets += "bible_verse_versification=?"; args += v11nName(p.module) }
        }
        args += blob(winHex)
        d.execSQL("UPDATE PageManager SET ${sets.joinToString(", ")} WHERE windowId=?", args.toTypedArray())
    }
}
