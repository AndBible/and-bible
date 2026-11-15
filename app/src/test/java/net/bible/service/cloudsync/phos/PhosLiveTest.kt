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

import kotlinx.serialization.json.JsonObject
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Against a real server with the Phos app; runs only when PHOS_LIVE_URL, PHOS_LIVE_USER and
 * PHOS_LIVE_PASSWORD are set (use a fresh test account).
 */
class PhosLiveTest {
    private val url = System.getenv("PHOS_LIVE_URL")
    private val user = System.getenv("PHOS_LIVE_USER")
    private val password = System.getenv("PHOS_LIVE_PASSWORD")

    private class Store : PhosStateStore {
        var text: String? = null
        override fun load() = text
        override fun save(text: String) { this.text = text }
    }

    private class Items(override val type: String, override val priority: Int) : PhosAdapter, PhosKeyMapper {
        val items = LinkedHashMap<String, JsonObject>()
        override fun enumerate() = items.map { PhosItem(it.key, it.value) }
        override fun apply(key: String, payload: JsonObject?, deleted: Boolean): Boolean {
            if (deleted) items.remove(key) else items[key] = payload!!
            return true
        }
        override fun localKeyFor(serverKey: String) =
            if (type == "bookmark") "bm:" + PhosIds.bookmarkUuidHex(serverKey.removePrefix("bm:")) else serverKey
        override fun overlay(server: JsonObject?, mine: JsonObject): JsonObject? = when (type) {
            "bookmark" -> PhosBookmarkCodec.bookmarkOverlay(server, mine)
            "workspace" -> PhosWorkspaceCodec.overlay(server, mine)
            else -> null
        }
    }

    private fun adapters() = listOf(Items("label", 10), Items("bookmark", 20), Items("workspace", 30), Items("planProgress", 40))

    @Test
    fun roundTripThroughServer() {
        assumeTrue(url != null && user != null && password != null)
        val kjv = Versifications.instance().getVersification("KJV")
        val a = adapters()
        a[0].items["label:Gebet"] = PhosBookmarkCodec.labelToJson(AbLabel(PhosIds.labelUuidHex("Gebet"), "Gebet", 0xFF336699.toInt()))
        val ords = PhosVerses.ordinals(kjv, PhosLocation("John", 42, 3, 16, 17))!!
        val bm = AbBibleBookmark("0123456789abcdef0123456789abcdef", "KJV", ords.first, ords.second, "KJV", null, null, true,
            1_700_000_000_000L, 1_700_000_100_000L, null, AbNote("<p>Gott <i>liebt</i></p>", "HTML"), listOf(AbLink("Gebet")), "Gebet")
        a[1].items["bm:" + bm.idHex] = PhosBookmarkCodec.bibleToJson(bm, PhosVerses.location(kjv, ords.first, ords.second)!!)
        val pad = AbStudyPadEntry("1123456789abcdef0123456789abcdef", "Gebet", 0, 0, "Eigene Notiz", "MARKDOWN")
        a[1].items["bm:" + pad.idHex] = PhosBookmarkCodec.studyPadToJson(pad)
        a[2].items["ws:2123456789abcdef0123456789abcdef"] = PhosWorkspaceCodec.workspaceToJson("2123456789abcdef0123456789abcdef", "Lesen",
            listOf(PhosPanel("KJV", "Bible", 1.0, 0, false, 42, 2, 16, null), PhosPanel("StrongsGreek", "Dictionary", 1.0, 0, false, 0, 0, 1, "G25")))
        a[3].items["plan:y1ot1nt2_mcheyne"] = PhosReadingPlanCodec.toJson(AbPlanProgress("y1ot1nt2_mcheyne",
            PhosReadingPlanCodec.dateMs("2026-01-05")!!, 3, mapOf(5 to listOf(true, false, true, true))))

        val r1 = PhosSyncEngine(PhosHttpClient(url!!, user!!, password!!), Store(), a, "live-a").run()
        assertEquals(emptyList<String>(), r1.problems)
        assertEquals(5, r1.pushed)

        val b = adapters()
        val r2 = PhosSyncEngine(PhosHttpClient(url, user, password), Store(), b, "live-b").run()
        assertEquals(emptyList<String>(), r2.problems)
        for (i in a.indices) {
            if (a[i].type == "workspace") {
                // the server re-encodes 1.0 as 1: compare what the panels mean
                fun panels(m: Map<String, JsonObject>) = m.mapValues { (_, v) ->
                    v.str("name") to (v["panels"] as kotlinx.serialization.json.JsonArray).map { PhosWorkspaceCodec.panelFromJson(it as JsonObject) }
                }
                assertEquals(panels(a[i].items), panels(b[i].items))
            } else {
                assertEquals(a[i].items, b[i].items)
            }
        }

        // nothing changed: a second run sends nothing
        val r3 = PhosSyncEngine(PhosHttpClient(url, user, password), Store(), b, "live-b2").run()
        assertEquals(0, r3.problems.size)
    }
}
