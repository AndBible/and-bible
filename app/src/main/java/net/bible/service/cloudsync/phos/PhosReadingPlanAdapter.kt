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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/*
 * Reading plan progress as Phos "planProgress" records: plan:<plan code> with the start date, the
 * days done and the readings ticked per day (1-based days, readings in order). A plan is matched
 * by its code, so progress meets on other devices where a plan of the same name exists.
 */

/** Progress of one plan as AndBible stores it. */
data class AbPlanProgress(
    val planCode: String,
    val startMs: Long,
    val currentDay: Int,
    /** day -> read flags of its readings (index 0 = reading 1) */
    val status: Map<Int, List<Boolean>>,
)

object PhosReadingPlanCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun date(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(ms)

    fun dateMs(s: String?): Long? {
        if (s.isNullOrEmpty()) return null
        return try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s) ?: return null
            Calendar.getInstance().apply { time = d }.timeInMillis
        } catch (e: Exception) { null }
    }

    /** {"chapterReadArray":[{"readingNumber":1,"isRead":true}, ...]} to read flags. */
    fun parseStatus(s: String): List<Boolean> {
        val arr = try { (json.parseToJsonElement(s) as? JsonObject)?.get("chapterReadArray") as? JsonArray } catch (e: Exception) { null }
            ?: return emptyList()
        val read = HashMap<Int, Boolean>()
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val n = (o["readingNumber"] as? JsonPrimitive)?.intOrNull ?: continue
            read[n] = o.boolOr("isRead", false)
        }
        val max = read.keys.maxOrNull() ?: return emptyList()
        return (1..max).map { read[it] == true }
    }

    /** [parsed] only carries readings the user has touched; pad with false up to the day's real reading
     *  count so an untouched reading doesn't make the day look fully read. */
    fun padTouched(parsed: List<Boolean>, readingCount: Int): List<Boolean> =
        if (parsed.size < readingCount) parsed + List(readingCount - parsed.size) { false } else parsed

    fun statusString(flags: List<Boolean>): String = Json.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("chapterReadArray", buildJsonArray {
            flags.forEachIndexed { i, r -> add(buildJsonObject { put("readingNumber", i + 1); put("isRead", r) }) }
        })
    })

    fun toJson(p: AbPlanProgress): JsonObject {
        val done = sortedSetOf<Int>()
        for (d in 1 until p.currentDay) done += d
        for ((d, flags) in p.status) if (flags.isNotEmpty() && flags.all { it }) done += d
        val o = LinkedHashMap<String, JsonElement>()
        o["startDate"] = JsonPrimitive(date(p.startMs))
        o["doneDays"] = JsonArray(done.map { JsonPrimitive(it) })
        o["checks"] = JsonObject(p.status.filterValues { it.isNotEmpty() }.toSortedMap()
            .mapKeys { it.key.toString() }.mapValues { (_, f) -> JsonArray(f.map { JsonPrimitive(it) }) })
        return JsonObject(o)
    }

    /** The progress a record describes; [readings] tells how many readings a day has. */
    fun fromJson(planCode: String, p: JsonObject, existing: AbPlanProgress?, readings: (Int) -> Int): AbPlanProgress {
        val start = dateMs(p.str("startDate"))?.let { ms ->
            if (existing != null && date(existing.startMs) == date(ms)) existing.startMs else ms
        } ?: existing?.startMs ?: System.currentTimeMillis()
        val done = (p["doneDays"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull }.toSortedSet()
        val checks = HashMap<Int, List<Boolean>>()
        (p["checks"] as? JsonObject)?.forEach { (k, v) ->
            val day = k.toIntOrNull() ?: return@forEach
            checks[day] = (v as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.contentOrNull == "true" }
        }
        var current = 1
        while (current in done) current++
        val status = HashMap<Int, List<Boolean>>()
        // keep every explicit status, not just the ones at or after the first incomplete day: a date-based
        // plan does not treat days before "current" as implicitly read the way a day-number plan does
        for ((d, flags) in checks) status[d] = flags
        for (d in done) {
            val n = maxOf(readings(d), status[d]?.size ?: 0, 1)
            status[d] = List(n) { true }
        }
        return AbPlanProgress(planCode, start, current, status)
    }
}

class PhosReadingPlanAdapter(
    private val db: () -> SupportSQLiteDatabase,
    private val readings: (planCode: String, day: Int) -> Int = ::readingsOnDay,
) : PhosAdapter {
    override val type = "planProgress"
    override val priority = 40

    private fun read(d: SupportSQLiteDatabase, only: String? = null): List<AbPlanProgress> {
        val plans = mutableListOf<Triple<String, Long, Int>>()
        d.rows("SELECT planCode, planStartDate, planCurrentDay FROM ReadingPlan" + (if (only != null) " WHERE planCode=?" else ""),
            if (only != null) arrayOf<Any?>(only) else emptyArray()) { c -> plans += Triple(c.getString(0), c.getLong(1), c.getInt(2)) }
        return plans.map { (code, start, current) ->
            val status = HashMap<Int, List<Boolean>>()
            d.rows("SELECT planDay, readingStatus FROM ReadingPlanStatus WHERE planCode=?", arrayOf<Any?>(code)) { c ->
                val day = c.getInt(0)
                val parsed = PhosReadingPlanCodec.parseStatus(c.getString(1))
                status[day] = PhosReadingPlanCodec.padTouched(parsed, readings(code, day))
            }
            AbPlanProgress(code, start, current, status)
        }
    }

    override fun enumerate(): List<PhosItem> = read(db()).map { PhosItem("plan:" + it.planCode, PhosReadingPlanCodec.toJson(it)) }

    override fun apply(key: String, payload: JsonObject?, deleted: Boolean): Boolean {
        val code = key.removePrefix("plan:")
        if (code.isEmpty()) return false
        val d = db()
        val existing = read(d, code).firstOrNull()
        if (deleted || payload == null) {
            if (existing != null) {
                d.execSQL("DELETE FROM ReadingPlanStatus WHERE planCode=?", arrayOf<Any?>(code))
                d.execSQL("DELETE FROM ReadingPlan WHERE planCode=?", arrayOf<Any?>(code))
            }
            return true
        }
        // only plans this device knows: progress of a plan it cannot show stays on the server
        if (existing == null && readings(code, 1) <= 0) return false
        val p = PhosReadingPlanCodec.fromJson(code, payload, existing) { day -> readings(code, day) }
        if (p == existing) return true
        if (existing == null) {
            d.execSQL("INSERT INTO ReadingPlan (planCode, planStartDate, planCurrentDay, id) VALUES (?,?,?,?)",
                arrayOf<Any?>(code, p.startMs, p.currentDay, newId()))
        } else if (existing.startMs != p.startMs || existing.currentDay != p.currentDay) {
            d.execSQL("UPDATE ReadingPlan SET planStartDate=?, planCurrentDay=? WHERE planCode=?", arrayOf<Any?>(p.startMs, p.currentDay, code))
        }
        val old = existing?.status.orEmpty()
        for (day in old.keys - p.status.keys) {
            d.execSQL("DELETE FROM ReadingPlanStatus WHERE planCode=? AND planDay=?", arrayOf<Any?>(code, day))
        }
        for ((day, flags) in p.status) {
            if (old[day] == flags) continue
            d.execSQL("INSERT INTO ReadingPlanStatus (planCode, planDay, readingStatus, id) VALUES (?,?,?,?) " +
                "ON CONFLICT(planCode, planDay) DO UPDATE SET readingStatus=excluded.readingStatus",
                arrayOf<Any?>(code, day, PhosReadingPlanCodec.statusString(flags), newId()))
        }
        return true
    }

    private fun newId(): ByteArray = blob(UUID.randomUUID().toString().replace("-", ""))
}

/** Number of readings of a day of an installed plan; 0 when the plan is not known here. */
private fun readingsOnDay(planCode: String, day: Int): Int = try {
    net.bible.service.readingplan.ReadingPlanTextFileDao().getReading(planCode, day).numReadings
} catch (e: Exception) {
    0
}
