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
    /** Reports resetRequired exactly once, then a full snapshot, like a server that purged old tombstones. */
    var resetOnce = false
    /** Forces the next matching change's push response to "duplicate" with the given record instead of the
     *  normal accept-and-store, simulating a retry whose earlier, lost response the server already applied. */
    var forceDuplicate: ((JsonObject) -> JsonObject?)? = null

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
                if (resetOnce && cursor != 0L) {
                    resetOnce = false
                    buildJsonObject { put("resetRequired", true); put("cursor", 0L); put("hasMore", false); put("changes", JsonArray(emptyList())) }
                } else {
                    // a real server's cursor advances past everything it scanned, not just what matched
                    // "types": a type left out of the request can still be skipped over and never seen again
                    val scanned = records.values.filter { it.seq > cursor }.sortedBy { it.seq }
                    val list = scanned.filter { it.type in types }
                    buildJsonObject {
                        put("changes", JsonArray(list.map { recJson(it) }))
                        put("cursor", scanned.maxOfOrNull { it.seq } ?: cursor)
                        put("hasMore", false)
                        put("collections", buildJsonArray { add(buildJsonObject { put("id", "p1"); put("kind", "personal"); put("owner", "u") }) })
                    }
                }
            }
            else -> error(path)
        }

        override fun post(path: String, body: JsonObject): JsonObject = when (path) {
            "/devices" -> JsonObject(emptyMap())
            "/push" -> {
                pushes += body
                val results = (body["changes"] as JsonArray).map { it as JsonObject }.map { ch ->
                    val forced = forceDuplicate?.invoke(ch)
                    if (forced != null) {
                        forceDuplicate = null
                        buildJsonObject { put("changeId", ch.str("changeId")); put("status", "duplicate"); put("record", forced) }
                    } else {
                        val id = ch.str("type") + "\u0001" + ch.str("key")
                        val r = records.getOrPut(id) { Rec(ch.str("type")!!, ch.str("key")!!, 0, "", false, null, 0) }
                        r.rev++; r.hlc = ch.str("hlc")!!; r.deleted = ch.str("op") == "delete"
                        r.payload = ch["payload"] as? JsonObject; r.seq = ++seq
                        buildJsonObject { put("changeId", ch.str("changeId")); put("status", "accepted"); put("record", recJson(r)) }
                    }
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
    /** Keys that should throw instead of writing, once each, like an unexpected DB failure. */
    val failOnce = mutableSetOf<String>()
    override fun enumerate() = items.map { PhosItem(it.key, it.value) }
    override fun apply(key: String, payload: JsonObject?, deleted: Boolean): Boolean {
        if (failOnce.remove(key)) error("simulated write failure for $key")
        applied++
        if (deleted) items.remove(key) else items[key] = payload!!
        return true
    }
    override fun localKeyFor(serverKey: String) =
        if (mapKeys) "bm:" + PhosIds.bookmarkUuidHex(serverKey.removePrefix("bm:")) else serverKey
}

/** A [PhosHttp] that throws instead of delegating to [inner] for the given path, once. */
private class FailingHttp(private val inner: PhosHttp, private val failPath: String) : PhosHttp {
    var failed = false
    override fun get(path: String, params: Map<String, String>) = inner.get(path, params)
    override fun post(path: String, body: JsonObject): JsonObject {
        if (path == failPath && !failed) { failed = true; throw PhosHttpException(0, "simulated network failure") }
        return inner.post(path, body)
    }
}

/** A [PhosHttp] that lets [inner] actually process the call (so the server's state really changes), then
 *  throws instead of returning the result, once — like a request that succeeded but whose response was lost. */
private class LostResponseHttp(private val inner: PhosHttp, private val failPath: String) : PhosHttp {
    var failed = false
    override fun get(path: String, params: Map<String, String>) = inner.get(path, params)
    override fun post(path: String, body: JsonObject): JsonObject {
        val result = inner.post(path, body)
        if (path == failPath && !failed) { failed = true; throw PhosHttpException(0, "simulated lost response") }
        return result
    }
}

/** A [PhosHttp] that runs [onPush] right before delegating a "/push" call to [inner], to simulate a local
 *  edit landing while that HTTP call is in flight. */
private class EditDuringPushHttp(private val inner: PhosHttp, private val onPush: () -> Unit) : PhosHttp {
    override fun get(path: String, params: Map<String, String>) = inner.get(path, params)
    override fun post(path: String, body: JsonObject): JsonObject {
        if (path == "/push") onPush()
        return inner.post(path, body)
    }
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

    @Test
    fun disabledTypeStaysQueuedUntilReenabled() {
        val server = FakeServer()
        val label = MapAdapter("label")
        val bookmark = MapAdapter("bookmark")
        label.items["label:A"] = obj("name" to "A")
        bookmark.items["bm:1"] = obj("id" to "1")
        val store = MemStore()
        val failingHttp = FailingHttp(server.http(), "/push")
        try {
            PhosSyncEngine(failingHttp, store, listOf(label, bookmark), "test", now = { 1_700_000_100_000L }).run()
        } catch (e: PhosHttpException) {
            // expected: both changes stay queued, nothing reached the server
        }
        assertTrue(server.records.isEmpty())

        // bookmark disabled now: only label is registered with the engine
        val r = PhosSyncEngine(server.http(), store, listOf(label), "test", now = { 1_700_000_200_000L }).run()
        assertEquals(1, r.pushed)
        assertTrue(server.records.keys.none { it.startsWith("bookmark\u0001") })

        // re-enabling bookmark lets its still-queued mutation go out
        val r2 = PhosSyncEngine(server.http(), store, listOf(label, bookmark), "test", now = { 1_700_000_300_000L }).run()
        assertEquals(1, r2.pushed)
        assertTrue(server.records.keys.any { it.startsWith("bookmark\u0001") })
    }

    @Test
    fun duplicatePushResponseAppliesTheServersNewerRecord() {
        val server = FakeServer()
        val a = MapAdapter("label")
        a.items["label:A"] = obj("name" to "A", "color" to "#111111")
        // the server already has this changeId from an earlier attempt whose response was lost, and its
        // stored record has since moved on to a different color
        server.forceDuplicate = { ch -> buildJsonObject {
            put("collection", "p1"); put("type", "label"); put("key", ch.str("key")!!); put("rev", 5L); put("hlc", "x")
            put("device", "other"); put("deleted", false); put("payload", obj("name" to "A", "color" to "#222222"))
        } }
        engine(server, MemStore(), a).run()
        assertEquals("#222222", a.items["label:A"]!!.str("color"))
    }

    @Test
    fun resetRequiredReconcilesPurgedDeletions() {
        val server = FakeServer()
        val remote = MapAdapter("label")
        remote.items["label:A"] = obj("name" to "A")
        remote.items["label:B"] = obj("name" to "B")
        engine(server, MemStore(), remote).run()

        val local = MapAdapter("label")
        val store = MemStore()
        engine(server, store, local).run()
        assertEquals(setOf("label:A", "label:B"), local.items.keys)

        // "label:A"'s tombstone was purged server-side; a resetRequired snapshot only shows what remains
        server.resetOnce = true
        server.records.remove("label\u0001label:A")
        engine(server, store, local).run()
        assertEquals(setOf("label:B"), local.items.keys)
    }

    @Test
    fun reenabledTypeBackfillsChangesMissedWhileDisabled() {
        val server = FakeServer()
        val remoteLabel = MapAdapter("label")
        val remoteBookmark = MapAdapter("bookmark")
        remoteLabel.items["label:A"] = obj("name" to "A")
        remoteBookmark.items["bm:1"] = obj("id" to "1")
        engine(server, MemStore(), remoteLabel, remoteBookmark).run()

        val label = MapAdapter("label")
        val bookmark = MapAdapter("bookmark")
        val store = MemStore()
        engine(server, store, label, bookmark).run()
        assertNotNull(bookmark.items["bm:1"])

        // device B disables bookmarks; meanwhile another device edits the bookmark
        remoteBookmark.items["bm:1"] = obj("id" to "1", "noteMd" to "later")
        engine(server, MemStore(), remoteLabel, remoteBookmark).run()

        // device B syncs with only "label" active: the shared cursor advances past the missed bookmark change
        engine(server, store, label).run()

        // re-enabling bookmark must still pick up what was missed
        engine(server, store, label, bookmark).run()
        assertEquals("later", bookmark.items["bm:1"]!!.str("noteMd"))
    }

    @Test
    fun writeFailureDoesNotBlacklistOrBlockSiblingRecords() {
        val server = FakeServer()
        val remote = MapAdapter("label")
        remote.items["label:A"] = obj("name" to "A")
        remote.items["label:B"] = obj("name" to "B")
        engine(server, MemStore(), remote).run()

        val local = MapAdapter("label")
        local.failOnce += "label:A"
        val store = MemStore()
        val r = engine(server, store, local).run()
        assertEquals("label:A failed but did not stop label:B from being applied", 1, local.items.size)
        assertEquals("B", local.items["label:B"]!!.str("name"))
        assertTrue(r.problems.isNotEmpty())

        // not permanently blacklisted: a later, unrelated remote edit to the same key still gets a chance
        remote.items["label:A"] = obj("name" to "A", "color" to "#ff0000")
        engine(server, MemStore(), remote).run()
        engine(server, store, local).run()
        assertEquals("#ff0000", local.items["label:A"]!!.str("color"))
    }

    @Test
    fun revertedEditClearsAndSupersedesStaleQueuedMutation() {
        val server = FakeServer()
        val a = MapAdapter("label")
        a.items["label:A"] = obj("name" to "A", "color" to "#111111")
        val store = MemStore()
        engine(server, store, a).run()

        a.items["label:A"] = obj("name" to "A", "color" to "#222222")
        val failingHttp = FailingHttp(server.http(), "/push")
        try {
            PhosSyncEngine(failingHttp, store, listOf(a), "test", now = { 1_700_000_200_000L }).run()
        } catch (e: PhosHttpException) {
            // expected: the edit is queued but the push attempt fails before any response arrives
        }

        a.items["label:A"] = obj("name" to "A", "color" to "#111111")
        engine(server, store, a).run()

        assertEquals("#111111", server.records.values.single { it.type == "label" }.payload!!.str("color"))
    }

    @Test
    fun deletingAnUnconfirmedItemBeforeRetryDoesNotResurrectIt() {
        val server = FakeServer()
        val a = MapAdapter("bookmark")
        a.items["bm:1"] = obj("id" to "1")
        val store = MemStore()
        val lossyHttp = LostResponseHttp(server.http(), "/push")
        try {
            PhosSyncEngine(lossyHttp, store, listOf(a), "test", now = { 1_700_000_100_000L }).run()
        } catch (e: PhosHttpException) {
            // expected: the server actually stored it, but this device never saw the confirmation, so there
            // is no shadow entry for it
        }
        assertEquals(1, server.records.size)

        a.items.remove("bm:1")   // the user deletes it locally before any retry happens
        engine(server, store, a).run()

        assertTrue("the stale, never-confirmed upsert must not resurrect the deleted bookmark",
            server.records.values.all { it.deleted })
        assertTrue(a.items.isEmpty())
    }

    @Test
    fun pushMergedResponseDoesNotOverwriteAConcurrentLocalEdit() {
        val server = FakeServer()
        val a = MapAdapter("label")
        a.items["label:A"] = obj("name" to "A", "color" to "#111111")
        val store = MemStore()
        engine(server, store, a).run()   // baseline, so there is a confirmed shadow to compare against

        a.items["label:A"] = obj("name" to "A", "color" to "#222222")   // queues an upsert for "#222222"
        // the server answers with something other than "accepted" (duplicate/merged/...), carrying a
        // record this device never asked for
        server.forceDuplicate = { ch -> buildJsonObject {
            put("collection", "p1"); put("type", "label"); put("key", ch.str("key")!!); put("rev", 9L); put("hlc", "x")
            put("device", "other"); put("deleted", false); put("payload", obj("name" to "A", "color" to "#333333"))
        } }
        // the user edits again while that push's HTTP call is in flight
        val http = EditDuringPushHttp(server.http()) { a.items["label:A"] = obj("name" to "A", "color" to "#444444") }
        PhosSyncEngine(http, store, listOf(a), "test", now = { 1_700_000_200_000L }).run()

        assertEquals("the concurrent edit must survive the stale push response", "#444444", a.items["label:A"]!!.str("color"))
    }

    @Test
    fun pendingRetryAppliesOnALaterRunEvenAfterTheCursorHasMovedOn() {
        val server = FakeServer()
        val remote = MapAdapter("label")
        remote.items["label:A"] = obj("name" to "A")
        remote.items["label:B"] = obj("name" to "B")
        engine(server, MemStore(), remote).run()

        val local = MapAdapter("label")
        local.failOnce += "label:A"
        val store = MemStore()
        engine(server, store, local).run()
        assertEquals(1, local.items.size)   // "A" failed to apply, "B" did not

        // nothing new on the server; the cursor has already moved past both records, so only the stashed
        // pending retry can bring "A" back
        engine(server, store, local).run()
        assertEquals(2, local.items.size)
        assertEquals("A", local.items["label:A"]!!.str("name"))
    }
}
