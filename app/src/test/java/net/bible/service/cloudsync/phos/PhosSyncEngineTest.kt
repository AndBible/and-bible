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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A minimal stand-in for the server: last write wins, one personal collection, a journal cursor. */
private class FakeServer {
    class Rec(val type: String, val key: String, var rev: Long, var hlc: String, var deleted: Boolean, var payload: JsonObject?, var seq: Long)

    val records = LinkedHashMap<String, Rec>()
    private var seq = 0L
    val pushes = mutableListOf<JsonObject>()

    fun recJson(r: Rec) = buildJsonObject {
        put("collection", "p1"); put("type", r.type); put("key", r.key); put("rev", r.rev); put("hlc", r.hlc)
        put("device", "x"); put("deleted", r.deleted); r.payload?.let { put("payload", it) }
    }

    fun http() = object : PhosHttp {
        override fun get(path: String, params: Map<String, String>): JsonObject = when (path) {
            "/status" -> buildJsonObject { put("personalCollection", "p1"); put("serverTime", 1_700_000_000_000L) }
            "/conflicts" -> buildJsonObject { put("conflicts", JsonArray(emptyList())) }
            "/changes" -> {
                val cursor = params["cursor"]!!.toLong()
                val types = params["types"]!!.split(",").toSet()
                val list = records.values.filter { it.seq > cursor && it.type in types }.sortedBy { it.seq }
                buildJsonObject {
                    put("changes", JsonArray(list.map { recJson(it) }))
                    put("cursor", list.maxOfOrNull { it.seq } ?: cursor)
                    put("hasMore", false)
                    put("collections", buildJsonArray { add(buildJsonObject { put("id", "p1"); put("kind", "personal"); put("owner", "u") }) })
                }
            }
            else -> error(path)
        }

        override fun post(path: String, body: JsonObject): JsonObject = when (path) {
            "/devices" -> JsonObject(emptyMap())
            "/push" -> {
                pushes += body
                val results = (body["changes"] as JsonArray).map { it as JsonObject }.map { ch ->
                    val id = ch.str("type") + "\u0001" + ch.str("key")
                    val r = records.getOrPut(id) { Rec(ch.str("type")!!, ch.str("key")!!, 0, "", false, null, 0) }
                    r.rev++; r.hlc = ch.str("hlc")!!; r.deleted = ch.str("op") == "delete"
                    r.payload = ch["payload"] as? JsonObject; r.seq = ++seq
                    buildJsonObject { put("changeId", ch.str("changeId")); put("status", "accepted"); put("record", recJson(r)) }
                }
                buildJsonObject { put("results", JsonArray(results)) }
            }
            else -> error(path)
        }
    }
}

private class MemStore : PhosStateStore {
    var text: String? = null
    override fun load() = text
    override fun save(text: String) { this.text = text }
}

/** Items as plain JSON objects by key; non-UUID "bm:" keys are mapped like the bookmark adapter does. */
private class MapAdapter(override val type: String, val mapKeys: Boolean = false) : PhosAdapter, PhosKeyMapper {
    val items = LinkedHashMap<String, JsonObject>()
    var applied = 0
    override fun enumerate() = items.map { PhosItem(it.key, it.value) }
    override fun apply(key: String, payload: JsonObject?, deleted: Boolean): Boolean {
        applied++
        if (deleted) items.remove(key) else items[key] = payload!!
        return true
    }
    override fun localKeyFor(serverKey: String) =
        if (mapKeys) "bm:" + PhosIds.bookmarkUuidHex(serverKey.removePrefix("bm:")) else serverKey
}

class PhosSyncEngineTest {
    private fun obj(vararg p: Pair<String, Any>) = JsonObject(p.associate { (k, v) ->
        k to when (v) { is Int -> JsonPrimitive(v); is Boolean -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
    })

    private fun engine(server: FakeServer, store: MemStore, vararg adapters: PhosAdapter) =
        PhosSyncEngine(server.http(), store, adapters.toList(), "test", now = { 1_700_000_100_000L })

    @Test
    fun pushThenPullOnSecondDevice() {
        val server = FakeServer()
        val a = MapAdapter("label")
        a.items["label:Gebet"] = obj("name" to "Gebet", "color" to "#ff0000")
        val r1 = engine(server, MemStore(), a).run()
        assertEquals(1, r1.pushed)

        val b = MapAdapter("label")
        val r2 = engine(server, MemStore(), b).run()
        assertEquals(1, r2.pulled)
        assertEquals("#ff0000", b.items["label:Gebet"]!!.str("color"))
        assertEquals(setOf("label:Gebet"), r2.appliedKeys["label"])
    }

    @Test
    fun unchangedItemsAreNotSentAgain() {
        val server = FakeServer()
        val a = MapAdapter("label")
        a.items["label:A"] = obj("name" to "A")
        val store = MemStore()
        engine(server, store, a).run()
        val again = engine(server, store, a).run()
        assertEquals(0, again.pushed)
        assertEquals(1, server.pushes.size)
    }

    @Test
    fun pulledRecordIsNotEchoed() {
        val server = FakeServer()
        val a = MapAdapter("label")
        a.items["label:A"] = obj("name" to "A")
        engine(server, MemStore(), a).run()
        val b = MapAdapter("label")
        val storeB = MemStore()
        engine(server, storeB, b).run()
        val pushesBefore = server.pushes.size
        engine(server, storeB, b).run()
        assertEquals(pushesBefore, server.pushes.size)
    }

    @Test
    fun deletionTravels() {
        val server = FakeServer()
        val a = MapAdapter("label")
        a.items["label:A"] = obj("name" to "A")
        val storeA = MemStore()
        engine(server, storeA, a).run()
        val b = MapAdapter("label")
        val storeB = MemStore()
        engine(server, storeB, b).run()
        a.items.clear()
        engine(server, storeA, a).run()
        engine(server, storeB, b).run()
        assertTrue(b.items.isEmpty())
    }

    @Test
    fun foreignBookmarkIdIsMappedAndPushedBackUnderItsOwnKey() {
        val server = FakeServer()
        val desktop = MapAdapter("bookmark")
        desktop.items["bm:6A168692-F30E62E0"] = obj("id" to "6A168692-F30E62E0", "noteMd" to "alt")
        engine(server, MemStore(), desktop).run()

        val ab = MapAdapter("bookmark", mapKeys = true)
        val storeAb = MemStore()
        engine(server, storeAb, ab).run()
        val local = "bm:" + PhosIds.derivedUuidHex("phos-bookmark", "6A168692-F30E62E0")
        assertNotNull(ab.items[local])
        assertNull(ab.items["bm:6A168692-F30E62E0"])

        ab.items[local] = JsonObject(ab.items[local]!! + ("noteMd" to JsonPrimitive("neu")))
        engine(server, storeAb, ab).run()
        val lastPush = (server.pushes.last()["changes"] as JsonArray).single() as JsonObject
        assertEquals("bm:6A168692-F30E62E0", lastPush.str("key"))
        assertEquals(1, server.records.values.count { it.type == "bookmark" })
    }

    @Test
    fun massDeleteIsHeldBack() {
        val server = FakeServer()
        val a = MapAdapter("label")
        for (i in 1..20) a.items["label:L$i"] = obj("name" to "L$i")
        val store = MemStore()
        engine(server, store, a).run()
        a.items.clear()
        val r = engine(server, store, a).run()
        assertEquals(20, r.heldDeletes)
        assertFalse(server.records.values.any { it.deleted })
        engine(server, store, a).run(confirmDeletes = true)
        assertTrue(server.records.values.all { it.deleted })
    }

    @Test
    fun hlcIsLowercaseAndMonotonic() {
        var t = 1000L
        val h = PhosHlc("0123456789abcdef", "", { t })
        val a = h.tick()
        val b = h.tick()
        assertTrue(a < b)
        assertEquals("0000000001000-0001-0123456789abcdef", b)
        h.observe("0000000005000-00ff-fedcba9876543210")
        assertEquals("0000000005000-0100-0123456789abcdef", h.last)
        t = 6000L
        assertEquals("0000000006000-0000-0123456789abcdef", h.tick())
    }
}
