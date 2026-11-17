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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom

/*
 * Client of the record sync API of the "Phos" Nextcloud app (protocol v2, see
 * docs/phos-nextcloud-sync.md). Ported from the Phos Mobile sync engine (GPL-2.0-or-later).
 *
 * Only the server merges: this engine detects local changes against a shadow of what the
 * server last confirmed, pushes them with the revision they were based on, and adopts whatever
 * the server answers. There is no merge code on the client.
 *
 * Adapters translate between AndBible's databases and the record payloads that the other Phos
 * clients (desktop, mobile, web) use, so all of them read each other's records.
 */

class PhosHttpException(val status: Int, message: String, val network: Boolean = false) : Exception(message)

/** OCS v2 calls below `/ocs/v2.php/apps/phos/api/v1`; returns `ocs.data`. Blocking. */
interface PhosHttp {
    fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject
    fun post(path: String, body: JsonObject): JsonObject
}

/** Where the engine keeps its state (shadow, outbox, cursor, clock) between runs. */
interface PhosStateStore {
    fun load(): String?
    fun save(text: String)
}

/** One local item as an adapter renders it: the record payload this device would send. */
data class PhosItem(val key: String, val payload: JsonObject)

interface PhosAdapter {
    val type: String

    /** Lower first when applying (labels before the bookmarks that name them). */
    val priority: Int get() = 50

    fun enumerate(): List<PhosItem>

    /** Stores a record locally. False = not representable here (kept remote-only, never deleted). */
    fun apply(key: String, payload: JsonObject?, deleted: Boolean): Boolean

    /** The item's own modification time (ms): the clock of a record that was never sent before. */
    fun modifiedMs(payload: JsonObject): Long = 0L

    /**
     * Fields this adapter writes. Any other field of the server's copy (what another device keeps
     * and this one does not know) is sent back unchanged instead of being removed. null = the
     * adapter's rendering is the whole record.
     */
    val ownedFields: Set<String>? get() = null

    /** Own way to put this device's rendering over the server's copy; null = by [ownedFields]. */
    fun overlay(server: JsonObject?, mine: JsonObject): JsonObject? = null

    fun beginApply() {}

    /** [success] false means this record's own write threw partway through and must not be committed. */
    fun endApply(success: Boolean = true) {}
}

/** Hybrid logical clock `<ms:13>-<counter:4 hex>-<device:16 hex>`. */
class PhosHlc(private val device: String, var last: String, private val now: () -> Long) {
    private fun parse(h: String): Pair<Long, Int> {
        val p = h.split('-')
        if (p.size < 3) return 0L to 0
        return (p[0].toLongOrNull() ?: 0L) to (p[1].toIntOrNull(16) ?: 0)
    }

    private fun fmt(ms: Long, c: Int) =
        ms.toString().padStart(13, '0') + "-" + c.coerceAtMost(0xFFFF).toString(16).padStart(4, '0') + "-" + device

    fun tick(): String {
        val (ms, c) = parse(last)
        val n = now()
        last = if (n > ms) fmt(n, 0) else fmt(ms, c + 1)
        return last
    }

    fun observe(remote: String) {
        val (rms, rc) = parse(remote)
        val (ms, c) = parse(last)
        val n = now()
        val m = maxOf(ms, rms, n)
        val nc = when {
            m == ms && m == rms -> maxOf(c, rc) + 1
            m == ms -> c + 1
            m == rms -> rc + 1
            else -> 0
        }
        last = fmt(m, nc)
    }

    /** A record that was never sent carries its own modification time, not "now". */
    fun first(modifiedMs: Long): String =
        if (modifiedMs in 1..now()) fmt(modifiedMs, 0) else tick()
}

/** Canonical JSON (object keys sorted recursively): what the server compares. */
fun phosCanonicalJson(e: JsonElement): String = Json.encodeToString(JsonElement.serializer(), canonical(e))

private fun canonical(e: JsonElement): JsonElement = when (e) {
    is JsonObject -> JsonObject(e.entries.sortedBy { it.key }.associate { it.key to canonical(it.value) })
    is JsonArray -> JsonArray(e.map { canonical(it) })
    else -> e
}

internal fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

fun phosHash(e: JsonElement): String =
    MessageDigest.getInstance("MD5").digest(phosCanonicalJson(e).toByteArray(Charsets.UTF_8)).toHex()

private val random = SecureRandom()

fun newPhosDeviceId(): String = ByteArray(8).also { random.nextBytes(it) }.toHex()

private fun newChangeId(): String {
    val b = ByteArray(16).also { random.nextBytes(it) }
    b[6] = ((b[6].toInt() and 0x0F) or 0x40).toByte()
    b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
    val h = b.toHex()
    return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
}

data class PhosRunResult(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: Int = 0,
    /** Deletions held back by the mass-delete guard; run again with confirmDeletes to send them. */
    val heldDeletes: Int = 0,
    val problems: List<String> = emptyList(),
    val firstContact: Boolean = false,
    /** Local keys stored from the server in this run, by record type. */
    val appliedKeys: Map<String, Set<String>> = emptyMap(),
)

/** Shadow entry: what the server last confirmed for one local item. h "-" = remote-only. */
private class Shadow(var c: String, var k: String, var r: Long, var h: String, var p: JsonObject?)

private class Queued(val sk: String, val h: String, val change: JsonObject)

private class Binding(val c: String, val r: String)

private class CollInfo(val owner: String, val ownerName: String, val kind: String)

class PhosSyncEngine(
    private val http: PhosHttp,
    private val store: PhosStateStore,
    adapters: List<PhosAdapter>,
    private val deviceName: String,
    private val appVersion: String = "",
    private val platform: String = "andbible",
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val adapters = adapters.sortedBy { it.priority }
    private val byType = adapters.associateBy { it.type }

    var device: String = ""
        private set
    private var personal = ""
    private var me = ""
    private var cursor = 0L
    /** Types the cursor has covered; a type registered later is backfilled from cursor 0 once. */
    private val pulledTypes = mutableSetOf<String>()
    private val shadow = mutableMapOf<String, Shadow>()
    private val outbox = mutableListOf<Queued>()
    private val labels = mutableMapOf<String, Binding>()
    private val colls = mutableMapOf<String, CollInfo>()
    private val appliedKeys = mutableMapOf<String, MutableSet<String>>()
    private lateinit var hlc: PhosHlc

    init {
        load()
    }

    val outboxSize: Int get() = outbox.size
    val personalCollection: String get() = personal

    private fun sk(type: String, key: String) = type + "\u0001" + key

    // ---------------- state ----------------

    private fun load() {
        val text = store.load()
        val root = try { text?.let { Json.parseToJsonElement(it) as? JsonObject } } catch (t: Throwable) { null }
        device = root?.str("device")?.takeIf { Regex("^[0-9a-f]{16}$").matches(it) } ?: newPhosDeviceId()
        hlc = PhosHlc(device, root?.str("hlc").orEmpty(), now)
        if (root == null) return
        personal = root.str("personal").orEmpty()
        me = root.str("me").orEmpty()
        cursor = (root["cursor"] as? JsonPrimitive)?.longOrNull ?: 0L
        (root["types"] as? JsonArray)?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(pulledTypes::add) }
        (root["shadow"] as? JsonObject)?.forEach { (k, v) ->
            val o = v as? JsonObject ?: return@forEach
            shadow[k] = Shadow(o.str("c").orEmpty(), o.str("k").orEmpty(), (o["r"] as? JsonPrimitive)?.longOrNull ?: 0L,
                o.str("h").orEmpty(), o["p"] as? JsonObject)
        }
        (root["outbox"] as? JsonArray)?.forEach { v ->
            val o = v as? JsonObject ?: return@forEach
            val ch = o["change"] as? JsonObject ?: return@forEach
            outbox += Queued(o.str("sk").orEmpty(), o.str("h").orEmpty(), ch)
        }
        (root["labels"] as? JsonObject)?.forEach { (k, v) ->
            val o = v as? JsonObject ?: return@forEach
            labels[k] = Binding(o.str("c").orEmpty(), o.str("r").orEmpty())
        }
        (root["colls"] as? JsonObject)?.forEach { (k, v) ->
            val o = v as? JsonObject ?: return@forEach
            colls[k] = CollInfo(o.str("owner").orEmpty(), o.str("ownerName").orEmpty(), o.str("kind").orEmpty())
        }
    }

    private fun save() {
        val root = buildJsonObject {
            put("v", 1)
            put("device", device)
            put("hlc", hlc.last)
            put("personal", personal)
            put("me", me)
            put("cursor", cursor)
            put("types", buildJsonArray { for (t in pulledTypes) add(JsonPrimitive(t)) })
            put("shadow", buildJsonObject {
                for ((k, s) in shadow) put(k, buildJsonObject {
                    put("c", s.c); put("k", s.k); put("r", s.r); put("h", s.h)
                    s.p?.let { put("p", it) }
                })
            })
            put("outbox", buildJsonArray {
                for (q in outbox) add(buildJsonObject { put("sk", q.sk); put("h", q.h); put("change", q.change) })
            })
            put("labels", buildJsonObject {
                for ((k, b) in labels) put(k, buildJsonObject { put("c", b.c); put("r", b.r) })
            })
            put("colls", buildJsonObject {
                for ((k, c) in colls) put(k, buildJsonObject { put("owner", c.owner); put("ownerName", c.ownerName); put("kind", c.kind) })
            })
        }
        store.save(Json.encodeToString(JsonObject.serializer(), root))
    }

    /** Forget everything about the server (other account, "start over"). Local data stays. */
    fun reset() {
        personal = ""; me = ""; cursor = 0L; pulledTypes.clear()
        shadow.clear(); outbox.clear(); labels.clear(); colls.clear()
        save()
    }

    /** The key under which the server knows a local item, if it was ever synced. */
    fun remoteKeyOf(type: String, localKey: String): String? = shadow[sk(type, localKey)]?.k

    // ---------------- one run ----------------

    fun run(confirmDeletes: Boolean = false): PhosRunResult {
        val problems = mutableListOf<String>()
        appliedKeys.clear()
        val st = http.get("/status")
        val pc = st.str("personalCollection").orEmpty()
        if (personal.isNotEmpty() && pc != personal) {
            log("other account than last time, starting over")
            reset()
        }
        val firstContact = cursor == 0L && shadow.isEmpty()
        personal = pc
        (st["serverTime"] as? JsonPrimitive)?.longOrNull?.let { hlc.observe(it.toString().padStart(13, '0') + "-0000-" + device) }
        http.post("/devices", buildJsonObject {
            put("id", device); put("name", deviceName); put("platform", platform); put("appVersion", appVersion)
        })
        val held = detect(confirmDeletes)
        save()
        val pushStats = push(problems)
        save()
        val pulled = pull(problems)
        save()
        val conflicts = try {
            (http.get("/conflicts")["conflicts"] as? JsonArray)?.size ?: 0
        } catch (t: Throwable) { 0 }
        return PhosRunResult(pushStats, pulled, conflicts, held, problems, firstContact,
            appliedKeys.mapValues { it.value.toSet() })
    }

    // ---------------- detect ----------------

    private fun detect(confirmDeletes: Boolean): Int {
        var held = 0
        for (a in adapters) {
            val items = a.enumerate()
            val present = HashSet<String>()
            for (item in items) {
                val key = sk(a.type, item.key)
                if (!present.add(key)) continue          // a key twice (duplicate label names): first wins
                val h = phosHash(item.payload)
                val sh = shadow[key]
                // a key mapped by a PhosKeyMapper goes back under the server's own key
                val serverKey = if (a is PhosKeyMapper) sh?.k?.takeIf { it.isNotEmpty() } ?: item.key else item.key
                val (coll, rkey, rpayload) = outgoing(a.type, serverKey, item.payload)
                val q = outbox.indexOfFirst { it.sk == key }
                // clean against the shadow and nothing queued: truly nothing to do. If something is still
                // queued (an edit the user has since reverted, or a delete-then-recreate of the same content),
                // fall through and resend the current state instead of leaving the stale mutation queued or
                // silently dropping it: that also covers the case where the queued request's response was lost
                // after the server actually applied it, since this resend supersedes that stale server state.
                if (sh != null && sh.h == h && sh.c == coll && q < 0) continue
                if (q >= 0 && outbox[q].h == h && outbox[q].change.str("collection") == coll) continue
                val payload = a.overlay(sh?.p, rpayload) ?: overlay(sh?.p, rpayload, a.ownedFields)
                val change = buildJsonObject {
                    put("changeId", newChangeId())
                    put("collection", coll)
                    put("type", a.type)
                    put("key", rkey)
                    put("op", "upsert")
                    put("hlc", if (sh == null) hlc.first(a.modifiedMs(item.payload)) else hlc.tick())
                    put("baseRev", if (sh != null && sh.c == coll) sh.r else 0L)
                    if (sh != null && sh.c != coll && sh.c.isNotEmpty()) put("moveFrom", sh.c)
                    put("payload", payload)
                }
                if (q >= 0) outbox[q] = Queued(key, h, change) else outbox += Queued(key, h, change)
            }
            // gone here: in the shadow, not local, not remote-only
            val gone = shadow.entries.filter { (k, s) ->
                k.startsWith(a.type + "\u0001") && k !in present && s.h != "-"
            }
            val ofType = shadow.keys.count { it.startsWith(a.type + "\u0001") }
            if (!confirmDeletes && gone.size > 10 && gone.size * 3 > ofType) {
                log("${gone.size} of $ofType ${a.type} records gone locally, held back")
                held += gone.size
                continue
            }
            for ((key, s) in gone) {
                if (outbox.any { it.sk == key && it.change.str("op") == "delete" }) continue
                val change = buildJsonObject {
                    put("changeId", newChangeId())
                    put("collection", s.c)
                    put("type", a.type)
                    put("key", s.k)
                    put("op", "delete")
                    put("hlc", hlc.tick())
                    put("baseRev", s.r)
                }
                outbox.removeAll { it.sk == key }
                outbox += Queued(key, "deleted", change)
            }
        }
        return held
    }

    /** The server's copy with this device's fields put over it (see [PhosAdapter.ownedFields]). */
    private fun overlay(server: JsonObject?, mine: JsonObject, owned: Set<String>?): JsonObject {
        if (server == null || owned == null) return mine
        val out = LinkedHashMap<String, JsonElement>()
        for ((k, v) in server) if (k !in owned) out[k] = v
        out.putAll(mine)
        return JsonObject(out)
    }

    // ---------------- push ----------------

    private fun push(problems: MutableList<String>): Int {
        var sent = 0
        while (true) {
            // entries for a type that got disabled since they were queued stay in the outbox, unsent,
            // until that type is enabled again
            val sendable = outbox.filter { it.change.str("type") in byType.keys }
            if (sendable.isEmpty()) break
            val batch = sendable.take(500)
            val res = http.post("/push", buildJsonObject {
                put("device", device)
                put("changes", JsonArray(batch.map { it.change }))
            })
            val results = (res["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val byId = results.associateBy { it.str("changeId") }
            val applied = mutableMapOf<PhosAdapter, MutableSet<String>>()
            for (q in batch) {
                val r = byId[q.change.str("changeId")] ?: continue
                val status = r.str("status").orEmpty()
                val type = q.change.str("type").orEmpty()
                val localKey = q.sk.substringAfter('\u0001')
                if (status == "invalid") {
                    problems += "$type $localKey: ${r.str("error")}"
                    // not sent again until it changes once more
                    val sh = shadow.getOrPut(q.sk) { Shadow(q.change.str("collection").orEmpty(), q.change.str("key").orEmpty(), 0L, "", null) }
                    sh.h = q.h
                    continue
                }
                sent++
                val rec = r["record"] as? JsonObject ?: continue
                rec.str("hlc")?.let { hlc.observe(it) }
                val deleted = rec.bool("deleted")
                // a "duplicate" response means the server already had this changeId (e.g. a retry after a lost
                // response), but its current record may have moved on since: apply it like merged/superseded,
                // not like a confirmed echo of what was sent.
                val sameAsSent = status == "accepted"
                if (status == "conflict" || status == "forbidden") problems += "$type $localKey: $status"
                if (sameAsSent) {
                    if (deleted) shadow.remove(q.sk)
                    else shadow[q.sk] = Shadow(rec.str("collection").orEmpty(), rec.str("key").orEmpty(), rec.long("rev"), q.h, rec["payload"] as? JsonObject)
                } else {
                    // merged / superseded / conflict / forbidden: the server's version is the one. Applied
                    // one record at a time so a write failure here rolls back only that record, not its
                    // batch-mates, and never gets a permanent "unrepresentable" shadow entry.
                    val a = byType[type] ?: continue
                    a.beginApply()
                    var ok = false
                    try {
                        applyRecord(a, rec, localKey)?.let { applied.getOrPut(a) { mutableSetOf() } += it }
                        ok = true
                    } catch (t: Throwable) {
                        problems += "$type $localKey: ${t.message}"
                        log("applying $type $localKey failed: ${t.message}")
                    } finally {
                        a.endApply(ok)
                    }
                }
            }
            for ((a, keys) in applied) {
                rehash(a, keys)
            }
            val ids = batch.map { it.change.str("changeId") }.toSet()
            outbox.removeAll { it.change.str("changeId") in ids }
            save()
        }
        return sent
    }

    // ---------------- pull ----------------

    private fun pull(problems: MutableList<String>): Int {
        val types = adapters.joinToString(",") { it.type }
        val records = mutableListOf<JsonObject>()
        var readable: Set<String>? = null
        var wasReset = false
        var c = cursor
        val newTypes = adapters.map { it.type }.filter { it !in pulledTypes }
        if (c > 0 && newTypes.isNotEmpty()) {
            var bc = 0L
            while (true) {
                val br = http.get("/changes", mapOf("cursor" to bc.toString(), "limit" to "1000",
                    "types" to newTypes.joinToString(","), "device" to device))
                (br["changes"] as? JsonArray)?.forEach { (it as? JsonObject)?.let(records::add) }
                bc = br.long("cursor")
                if (!br.bool("hasMore")) break
            }
        }
        while (true) {
            val r = http.get("/changes", mapOf("cursor" to c.toString(), "limit" to "1000", "types" to types, "device" to device))
            if (r.bool("resetRequired")) {
                wasReset = true
                c = 0L
                records.clear()
                continue
            }
            (r["collections"] as? JsonArray)?.let { arr ->
                val ids = mutableSetOf<String>()
                for (e in arr) {
                    val o = e as? JsonObject ?: continue
                    val id = o.str("id") ?: continue
                    ids += id
                    colls[id] = CollInfo(o.str("owner").orEmpty(), o.str("ownerName").orEmpty(), o.str("kind").orEmpty())
                    if (o.str("kind") == "personal") me = o.str("owner").orEmpty()
                }
                readable = ids
            }
            (r["changes"] as? JsonArray)?.forEach { (it as? JsonObject)?.let(records::add) }
            for (j in (r["joined"] as? JsonArray).orEmpty()) {
                val coll = (j as? JsonPrimitive)?.contentOrNull ?: continue
                var jc = 0L
                while (true) {
                    val jr = http.get("/changes", mapOf("cursor" to jc.toString(), "limit" to "1000", "types" to types,
                        "device" to device, "collections" to coll))
                    (jr["changes"] as? JsonArray)?.forEach { (it as? JsonObject)?.let(records::add) }
                    jc = jr.long("cursor")
                    if (!jr.bool("hasMore")) break
                }
            }
            c = r.long("cursor")
            if (!r.bool("hasMore")) break
        }
        readable?.let { revoke(it) }
        if (wasReset) reconcileAfterReset(records, readable)
        var applied = 0
        for (a in adapters) {
            val mine = records.filter { it.str("type") == a.type }
            if (mine.isEmpty()) continue
            // the current local state, snapshotted once up front, so a record is only overwritten when it is
            // still as clean as detect() last found it (see applyIfNewer)
            val live = a.enumerate().associateBy { it.key }
            // the shadow hash as it stood before any record of this batch was applied, frozen per key so a
            // second historical revision of the same key (e.g. during a backfill) isn't confused by the "?"
            // placeholder the first one's applyRecord() just left in the (otherwise live) shadow map
            val baseline = mutableMapOf<String, String?>()
            for (rec in mine) {
                val lk = localKeyOf(a, rec) ?: continue
                if (lk !in baseline) baseline[lk] = shadow[sk(a.type, lk)]?.h
            }
            val keys = mutableSetOf<String>()
            for (rec in mine) {
                rec.str("hlc")?.let { hlc.observe(it) }
                // applied one record at a time: a write failure here rolls back only that record, and never
                // gets a permanent "unrepresentable" shadow entry
                a.beginApply()
                var ok = false
                try {
                    val k = applyIfNewer(a, rec, live, baseline)
                    ok = true
                    if (k != null) { keys += k; applied++ }
                } catch (t: Throwable) {
                    val localKey = rec.str("key").orEmpty()
                    problems += "${a.type} $localKey: ${t.message}"
                    log("applying ${a.type} $localKey failed: ${t.message}")
                } finally {
                    a.endApply(ok)
                }
            }
            rehash(a, keys)
        }
        cursor = c
        // exactly the currently active types: a type disabled and later re-enabled drops out of this set
        // while it's off, so re-enabling it is treated as new again and triggers the newTypes backfill above
        pulledTypes.clear()
        pulledTypes += adapters.map { it.type }
        return applied
    }

    /** Applies a pulled record unless the shadow already has it or the item waits to be pushed. */
    private fun applyIfNewer(a: PhosAdapter, rec: JsonObject, live: Map<String, PhosItem>, baseline: Map<String, String?>): String? {
        val coll = rec.str("collection").orEmpty()
        val localKey = localKeyOf(a, rec) ?: return null
        val key = sk(a.type, localKey)
        val sh = shadow[key]
        if (sh != null && sh.r >= rec.long("rev") && sh.c == coll) return null
        if (outbox.any { it.sk == key }) return null          // dirty: the server merges on our push
        // the old half of a move: the item lives in another collection now
        if (rec.bool("deleted") && sh != null && sh.c != coll) return null
        if (rec.bool("deleted") && sh == null) return null
        // a confirmed shadow already existed before this pull began (baseline[localKey] != null): if the
        // live local content no longer matches it, an edit landed after detect() snapshotted the outbox for
        // this run (while push/pull were in flight). Keep that edit, just adopt the new revision/payload so
        // the next detect() rebases on the correct baseRev, instead of overwriting it and rehashing it "clean"
        val confirmed = baseline[localKey]
        if (confirmed != null) {
            val liveHash = live[localKey]?.let { phosHash(it.payload) } ?: "-"
            if (liveHash != confirmed) {
                shadow[key] = Shadow(coll, rec.str("key").orEmpty(), rec.long("rev"), confirmed, rec["payload"] as? JsonObject)
                return null
            }
        }
        return applyRecord(a, rec, localKey)
    }

    /** Stores one server record locally; returns the local key when stored (shadow follows in rehash). */
    private fun applyRecord(a: PhosAdapter, rec: JsonObject, knownLocalKey: String? = null): String? {
        val coll = rec.str("collection").orEmpty()
        val deleted = rec.bool("deleted")
        val (localKey, payload) = inbound(a, rec, knownLocalKey) ?: return null
        val key = sk(a.type, localKey)
        if (payload?.containsKey("\$file") == true) {
            log("${a.type} $localKey is stored as a file, not supported here")
            shadow[key] = Shadow(coll, rec.str("key").orEmpty(), rec.long("rev"), "-", null)
            return null
        }
        // an exception here propagates to the caller, which runs this record in its own transaction: no
        // partial commit, and no shadow entry gets written below, so it is never permanently blacklisted
        val ok = a.apply(localKey, payload, deleted)
        if (ok) appliedKeys.getOrPut(a.type) { mutableSetOf() } += localKey
        if (deleted) {
            shadow.remove(key)
            if (a.type == "label") labels.remove(localKey.removePrefix("label:"))
            return null
        }
        shadow[key] = Shadow(coll, rec.str("key").orEmpty(), rec.long("rev"), if (ok) "?" else "-", rec["payload"] as? JsonObject)
        return if (ok) localKey else null
    }

    /** After applying: the shadow hash is what this device renders now, so it is not sent back. */
    private fun rehash(a: PhosAdapter, keys: Set<String>) {
        if (keys.isEmpty()) return
        val now = a.enumerate().associateBy { it.key }
        for (k in keys) {
            val s = shadow[sk(a.type, k)] ?: continue
            s.h = now[k]?.let { phosHash(it.payload) } ?: "-"
        }
    }

    /** Collections no longer readable: others' records go, my own become personal again. */
    private fun revoke(readable: Set<String>) {
        val lost = shadow.entries.filter { it.value.c.isNotEmpty() && it.value.c != personal && it.value.c !in readable }
        if (lost.isEmpty()) return
        val byAdapter = lost.groupBy { it.key.substringBefore('\u0001') }
        for ((type, entries) in byAdapter) {
            val a = byType[type] ?: continue
            for ((key, s) in entries) {
                val own = colls[s.c]?.owner == me && me.isNotEmpty()
                if (!own && s.h != "-") {
                    val localKey = key.substringAfter('\u0001')
                    a.beginApply()
                    var ok = false
                    try {
                        if (a.apply(localKey, null, true)) appliedKeys.getOrPut(type) { mutableSetOf() } += localKey
                        ok = true
                    } catch (t: Throwable) {
                        log("revoking ${a.type} $localKey failed: ${t.message}")
                    } finally {
                        a.endApply(ok)
                    }
                }
                shadow.remove(key)
            }
        }
        labels.entries.removeAll { it.value.c !in readable }
    }

    /**
     * After a resetRequired snapshot: the feed only carries what currently exists, so a shadow entry whose
     * item is genuinely gone (its tombstone was already purged) will never be seen as a delete again. Clean
     * those up now; an item with a pending local change is left alone so it can still be pushed.
     */
    private fun reconcileAfterReset(records: List<JsonObject>, readable: Set<String>?) {
        val seen = mutableSetOf<String>()
        for (rec in records) {
            val a = byType[rec.str("type").orEmpty()] ?: continue
            val localKey = localKeyOf(a, rec) ?: continue
            seen += sk(a.type, localKey)
        }
        val covered = (readable ?: emptySet()) + personal
        for (a in adapters) {
            val gone = shadow.entries.filter { (k, s) ->
                k.startsWith(a.type + "\u0001") && k !in seen && s.h != "-" && s.c in covered && outbox.none { it.sk == k }
            }
            for ((key, _) in gone) {
                val localKey = key.substringAfter('\u0001')
                a.beginApply()
                var ok = false
                try {
                    a.apply(localKey, null, true)
                    ok = true
                } catch (t: Throwable) {
                    log("reconciling ${a.type} $localKey after reset failed: ${t.message}")
                } finally {
                    a.endApply(ok)
                }
                if (ok) shadow.remove(key)
            }
        }
    }

    // ---------------- shared collections (labels decide where a bookmark lives) ----------------

    private fun outgoing(type: String, key: String, payload: JsonObject): Triple<String, String, JsonObject> {
        when (type) {
            "label" -> {
                val b = labels[key.removePrefix("label:")] ?: return Triple(personal, key, payload)
                return Triple(b.c, "label:" + b.r, JsonObject(payload + ("name" to JsonPrimitive(b.r))))
            }
            "bookmark" -> {
                val names = (payload["labelNames"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                // the first shared label decides the collection; members see its labels only
                val coll = names.firstNotNullOfOrNull { labels[it]?.c } ?: return Triple(personal, key, payload)
                val remote = names.mapNotNull { n -> labels[n]?.takeIf { it.c == coll }?.r }
                return Triple(coll, key, JsonObject(payload + ("labelNames" to JsonArray(remote.map { JsonPrimitive(it) }))))
            }
        }
        return Triple(personal, key, payload)
    }

    /**
     * The local key of a server record. Keys are kept as the server has them, except where the
     * adapter maps them ([PhosKeyMapper], e.g. non-UUID bookmark ids of other clients) and for
     * labels of shared collections.
     */
    private fun localKeyOf(a: PhosAdapter, rec: JsonObject): String? {
        val coll = rec.str("collection").orEmpty()
        val key = rec.str("key") ?: return null
        if (a.type != "label" || coll == personal || coll.isEmpty()) {
            return (a as? PhosKeyMapper)?.localKeyFor(key) ?: key
        }
        val remote = key.removePrefix("label:")
        labels.entries.firstOrNull { it.value.c == coll && it.value.r == remote }?.let { return "label:" + it.key }
        return if (rec.bool("deleted")) null else "label:" + localLabelFor(coll, remote)
    }

    private fun inbound(a: PhosAdapter, rec: JsonObject, knownLocalKey: String?): Pair<String, JsonObject?>? {
        val coll = rec.str("collection").orEmpty()
        val payload = rec["payload"] as? JsonObject
        val localKey = knownLocalKey ?: localKeyOf(a, rec) ?: return null
        if (coll == personal || coll.isEmpty() || payload == null) return localKey to payload
        return when (a.type) {
            "label" -> localKey to JsonObject(payload + ("name" to JsonPrimitive(localKey.removePrefix("label:"))))
            "bookmark" -> {
                val names = (payload["labelNames"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                localKey to JsonObject(payload + ("labelNames" to JsonArray(names.map { JsonPrimitive(localLabelFor(coll, it)) })))
            }
            else -> localKey to payload
        }
    }

    /** Someone else's label shows its owner, so it never merges with a label of mine of the same name. */
    private fun localLabelFor(coll: String, remote: String): String {
        labels.entries.firstOrNull { it.value.c == coll && it.value.r == remote }?.let { return it.key }
        val info = colls[coll]
        val local = if (info != null && info.owner.isNotEmpty() && info.owner != me) {
            "$remote (${info.ownerName.ifEmpty { info.owner }})"
        } else {
            remote
        }
        labels[local] = Binding(coll, remote)
        return local
    }

    /** The owner's name for a label shared with this user, or null when it is the user's own. */
    fun labelSharedBy(localName: String): String? {
        val b = labels[localName] ?: return null
        val info = colls[b.c] ?: return null
        return if (info.owner != me) info.ownerName.ifEmpty { info.owner } else null
    }
}

/**
 * Optional for adapters whose local keys differ from some server keys (e.g. bookmark ids of other
 * clients that are not UUIDs). The engine remembers the server key in its shadow and pushes back
 * under it, so the record is never duplicated.
 */
interface PhosKeyMapper {
    fun localKeyFor(serverKey: String): String
}

// ---- small JSON helpers ----

internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
internal fun JsonObject.long(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
internal fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.contentOrNull == "true"
