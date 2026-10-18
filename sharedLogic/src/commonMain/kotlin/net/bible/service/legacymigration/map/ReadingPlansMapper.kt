package net.bible.service.legacymigration.map

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.bible.android.database.DbDate
import net.bible.android.database.IdType
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.legacymigration.stage.StagingDatabases
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8

/**
 * The legacy rows a [ReadingPlansMapper] needs: the `ReadingPlan` and `ReadingPlanDay` entities of the cloud store and
 * the local `Setting` map that carries the preserved Android status payloads and exact start dates.
 */
class LegacyReadingPlanData(val plans: List<LegacyRow>, val days: List<LegacyRow>, val settings: Map<String, String>) {
    companion object {
        fun from(snapshot: LegacySnapshot) = LegacyReadingPlanData(
            plans = snapshot.cloud.rows("ReadingPlan"),
            days = snapshot.cloud.rows("ReadingPlanDay"),
            settings = snapshot.settings,
        )
    }
}

/** The current-schema rows a [ReadingPlansMapper] run produces, in insertion order. */
class ReadingPlanStage(val plans: List<ReadingPlan>, val statuses: List<ReadingPlanStatus>)

/**
 * Migrates the started reading plans and their per-day status from the legacy Swift cloud store into the current
 * `ReadingPlanDatabase` (`ReadingPlan` and `ReadingPlanStatus` tables).
 *
 * This is a port of the row derivation of the Swift app's own Android exporter:
 * `RemoteSyncReadingPlanSnapshotService.snapshotCurrentStateStrict` (plan rows from `ReadingPlan`, status rows from the
 * preserved Android payloads or synthesized from completed days: `makeStatusRow`, `syntheticStatusID`,
 * `isImplicitHistoricStatus`), the `RemoteSyncReadingPlanStatusStore` / `RemoteSyncReadingPlanTimestampStore` settings
 * decoding, `ReadingPlanDayAssignment` (the reading count of a day) and `AndroidReadingPlanStatusPayload`
 * (`settingAll`, `androidJSON`), plus the sort orders and column lists of
 * `RemoteSyncInitialBackupUploadService.buildReadingPlanInitialBackup` (`insertReadingPlanRow`,
 * `insertReadingPlanStatusRow`). Differences, all deliberate:
 *
 * - **Failures are reported, not fatal.** The Swift exporter is strict and aborts the whole export on one bad row. Here a
 *   malformed preserved status or start date setting, a plan or day whose number does not fit Android's 32-bit column,
 *   a plan without an id and a second plan with an already used plan code (the Android table has a unique index) are
 *   skipped and counted in the [MigrationReport]; everything else is still written.
 * - The start date is the exact `start_date_millis` setting when present (so a restored Android plan keeps its `Long`),
 *   else the legacy `Date`. The reader truncates a date to whole milliseconds like the other Swift exporters, whereas
 *   `AndroidTimestamp.milliseconds(from:)` rounds, so a fractional-millisecond start date can differ by 1 ms. Android
 *   treats a start date as a day, so this is accepted.
 * - The remote-sync bookkeeping (`LogEntry` rows, the accepted baseline, fingerprints) is not migrated. The custom plan
 *   definition files (`.properties`) are not database content: the file steps of the migration list and copy them.
 * - Rows go through the Room DAO (`ReadingPlanDao`), whose entity converters (`DbDate`, `IdType`) round-trip these
 *   columns faithfully, in one writer transaction.
 *
 * The staging database is only created when there is a plan to write.
 */
class ReadingPlansMapper : CategoryMapper {
    override val category = LegacyCategory.READING_PLANS

    /** Values present but of an unexpected type, or settings rejected, counted by reason during one [build]. */
    private val drops = LinkedHashMap<String, Int>()

    override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) =
        map(LegacyReadingPlanData.from(snapshot), staging, report)

    /** Maps [data] into [staging]; [map] with a snapshot reads the data from the stores and calls this. */
    suspend fun map(data: LegacyReadingPlanData, staging: StagingDatabases, report: MigrationReport) {
        val stage = build(data, report)
        if (stage.plans.isEmpty() && stage.statuses.isEmpty()) return
        val db = staging.readingPlans
        val dao = db.readingPlanDao()
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                stage.plans.forEach { dao.updatePlan(it) }
                stage.statuses.forEach { dao.addPlanStatus(it) }
            }
        }
        report.written(category, "ReadingPlan", stage.plans.size)
        report.written(category, "ReadingPlanStatus", stage.statuses.size)
    }

    /** Builds the target rows without touching any database. Skipped rows and values are noted in [report]. */
    fun build(data: LegacyReadingPlanData, report: MigrationReport): ReadingPlanStage {
        drops.clear()
        try {
            return buildStage(data)
        } finally {
            for ((what, rows) in drops) if (rows > 0) report.skipped(category, what, rows)
            drops.clear()
        }
    }

    private fun drop(reason: String) {
        drops[reason] = (drops[reason] ?: 0) + 1
    }

    private class RawPlan(val pk: Long, val id: IdType, val planCode: String, val startMillis: Long, val currentDay: Long)

    private fun buildStage(data: LegacyReadingPlanData): ReadingPlanStage {
        val storedStatuses = storedStatuses(data.settings)
        val exactStarts = exactStartMillis(data.settings)

        // Swift: plans sorted (planCode, id.uuidString); an id-less row cannot be exported.
        val raw = data.plans.mapNotNull { row ->
            val id = row.values.typed<LegacyValue.UuidV>("id")?.value?.let { IdType.fromString(it) }
            if (id == null) { drop("reading plan without id"); return@mapNotNull null }
            RawPlan(
                pk = row.pk,
                id = id,
                planCode = row.values.typed<LegacyValue.TextV>("planCode")?.value ?: "",
                startMillis = exactStarts[id.toString()]
                    ?: row.values.typed<LegacyValue.DateV>("startDate")?.epochMillis
                    ?: 0L,
                currentDay = row.values.typed<LegacyValue.IntV>("currentDay")?.value ?: 0L,
            )
        }.sortedWith(compareBy<RawPlan>({ it.planCode }, { it.id.toString().uppercase() }))

        val daysByPlanPk = data.days.groupBy { it.toOne["plan"] }
        val plans = mutableListOf<ReadingPlan>()
        val statusByKey = LinkedHashMap<String, ReadingPlanStatus>()
        val usedCodes = HashSet<String>()
        for (plan in raw) {
            if (plan.currentDay !in Int.MIN_VALUE..Int.MAX_VALUE) { drop("reading plan current day out of range"); continue }
            if (!usedCodes.add(plan.planCode)) { drop("duplicate reading plan code"); continue }
            val currentDay = plan.currentDay.toInt()
            plans += ReadingPlan(plan.planCode, DbDate(plan.startMillis), currentDay, plan.id)
            val days = daysByPlanPk[plan.pk].orEmpty().mapNotNull { row ->
                val number = row.values.typed<LegacyValue.IntV>("dayNumber")?.value ?: 1L
                if (number !in Int.MIN_VALUE..Int.MAX_VALUE) { drop("reading plan day number out of range"); null } else row to number.toInt()
            }.sortedBy { it.second }
            for ((row, dayNumber) in days) {
                val readings = row.values.typed<LegacyValue.TextV>("readings")?.value ?: ""
                val completed = row.values.typed<LegacyValue.BoolV>("isCompleted")?.value ?: false
                val stored = storedStatuses[plan.planCode to dayNumber]
                val status = if (stored != null) {
                    ReadingPlanStatus(plan.planCode, dayNumber, stored.json, stored.remoteId ?: syntheticStatusId(plan.id, dayNumber))
                } else if (completed && !isImplicitHistoric(readings, dayNumber, currentDay)) {
                    ReadingPlanStatus(plan.planCode, dayNumber, allReadJson(readingCount(readings)), syntheticStatusId(plan.id, dayNumber))
                } else null
                // Swift keys rows by their (deterministic or preserved) id, so a repeated day replaces the earlier row.
                if (status != null) statusByKey["${plan.planCode}|$dayNumber"] = status
            }
        }
        val statuses = statusByKey.values.sortedWith(
            compareBy<ReadingPlanStatus>({ it.planCode }, { it.planDay }, { it.id.toString().uppercase() }),
        )
        return ReadingPlanStage(plans, statuses)
    }

    // ---- Settings ---------------------------------------------------------------------------------------------

    private class StoredStatus(val json: String, val remoteId: IdType?)

    /**
     * Port of `RemoteSyncReadingPlanStatusStore.allStatusesStrict`/`decodeEntryStrict`: the settings under
     * `remote_sync.readingplans.android_status` whose key is `<prefix>.<url-safe base64 of the plan code>.<day>` and whose
     * value is either the JSON envelope `{planCode, dayNumber, readingStatusJSON, remoteStatusID?}` or a bare Android
     * `readingStatus` payload. An undecodable entry is counted and ignored (Swift would abort the export).
     */
    private fun storedStatuses(settings: Map<String, String>): Map<Pair<String, Int>, StoredStatus> {
        val result = LinkedHashMap<Pair<String, Int>, StoredStatus>()
        for ((key, value) in settings.entries.filter { it.key.startsWith(STATUS_PREFIX) }.sortedBy { it.key }) {
            val decoded = decodeStatus(key, value)
            if (decoded == null) { drop("malformed reading plan status setting"); continue }
            result[decoded.first] = decoded.second
        }
        return result
    }

    private fun decodeStatus(key: String, value: String): Pair<Pair<String, Int>, StoredStatus>? {
        if (!key.startsWith("$STATUS_PREFIX.")) return null
        val suffix = key.removePrefix("$STATUS_PREFIX.")
        val separator = suffix.lastIndexOf('.')
        if (separator < 0) return null
        val day = suffix.substring(separator + 1).toIntOrNull() ?: return null
        val planCode = decodeKeySegment(suffix.substring(0, separator)) ?: return null
        if (value.isEmpty()) return null
        val inner = parseObject(value)?.let { envelopeOrNull(it) }
        if (inner != null) {
            if (inner.planCode != planCode || inner.day != day) return null
            if (!isValidStatusPayload(inner.json)) return null
            return (planCode to day) to StoredStatus(inner.json, inner.remoteId)
        }
        if (!isValidStatusPayload(value)) return null
        return (planCode to day) to StoredStatus(value, null)
    }

    private class Envelope(val planCode: String, val day: Int, val json: String, val remoteId: IdType?)

    /** The `Status` Codable of the Swift store: the three required keys, the optional UUID; null when it does not decode. */
    private fun envelopeOrNull(o: JsonObject): Envelope? {
        val planCode = o.string("planCode") ?: return null
        val day = o["dayNumber"].plainLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: return null
        val json = o.string("readingStatusJSON") ?: return null
        val remote = o["remoteStatusID"]
        val remoteId = when {
            remote == null || remote is JsonNull -> null
            remote is JsonPrimitive && remote.isString && UUID_PATTERN.matches(remote.content) -> IdType.fromString(remote.content.lowercase())
            else -> return null
        }
        return Envelope(planCode, day, json, remoteId)
    }

    /** Port of `decodeKeySegment`: URL-safe Base64, padding optional, must be valid UTF-8. */
    private fun decodeKeySegment(segment: String): String? {
        val bytes = segment.decodeBase64()?.toByteArray() ?: return null
        val text = bytes.decodeToString()
        return if (text.encodeToByteArray().contentEquals(bytes)) text else null
    }

    /**
     * Port of `RemoteSyncReadingPlanTimestampStore.allMilliseconds`: `remote_sync.readingplans.start_date_millis.<plan id>`
     * holds the exact Android start date as a decimal `Long`. Returns it by lower-case plan id; a malformed or repeated
     * entry is counted and ignored.
     */
    private fun exactStartMillis(settings: Map<String, String>): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        val prefix = "$START_PREFIX."
        for ((key, value) in settings.entries.filter { it.key.startsWith(prefix) }.sortedBy { it.key }) {
            val id = key.removePrefix(prefix)
            val millis = value.toLongOrNull()
            if (!UUID_PATTERN.matches(id) || millis == null || millis.toString() != value || result.put(id.lowercase(), millis) != null) {
                drop("malformed reading plan start date setting")
            }
        }
        return result
    }

    // ---- Port of the Android status helpers -----------------------------------------------------------------

    /**
     * Port of `AndroidReadingPlanStatusPayload(androidJSON:)`: an object with a `chapterReadArray` of objects holding an
     * integer `readingNumber` and a boolean `isRead`. Other keys are ignored, like the Codable decode.
     */
    private fun isValidStatusPayload(json: String): Boolean {
        val array = (parseObject(json) ?: return false)["chapterReadArray"] as? JsonArray ?: return false
        return array.all { element ->
            val o = element as? JsonObject ?: return@all false
            val isRead = o["isRead"] as? JsonPrimitive
            o["readingNumber"].plainLong() != null && isRead != null && !isRead.isString && (isRead.content == "true" || isRead.content == "false")
        }
    }

    /** `AndroidReadingPlanStatusPayload.settingAll(readingCount:isRead: true).androidJSON()` (sorted keys, no whitespace). */
    private fun allReadJson(readingCount: Int): String =
        (1..readingCount).joinToString(",", "{\"chapterReadArray\":[", "]}") { "{\"isRead\":true,\"readingNumber\":$it}" }

    /**
     * Port of `ReadingPlanDayAssignment.readings.count`: the readings are the comma separated entries after the last
     * `;` (or the whole value), trimmed; trailing empty entries are dropped, interior empty ones keep their position.
     */
    private fun readingCount(rawValue: String): Int {
        val portion = if (rawValue.contains(';')) rawValue.substring(rawValue.lastIndexOf(';') + 1) else rawValue
        val parts = portion.split(',').map { it.trim() }.toMutableList()
        while (parts.isNotEmpty() && parts.last().isEmpty()) parts.removeAt(parts.size - 1)
        return parts.size
    }

    /** Port of `isImplicitHistoricStatus`: Android derives a completed ordinal-day plan day before `currentDay` as read. */
    private fun isImplicitHistoric(readings: String, dayNumber: Int, currentDay: Int): Boolean =
        !readings.contains(';') && dayNumber < currentDay

    /** Port of `syntheticStatusID`: the first 16 bytes of SHA-256 of `reading-plan-status|<lower-case plan id>|<day>`. */
    private fun syntheticStatusId(planId: IdType, dayNumber: Int): IdType =
        IdType.fromByteArray(
            "reading-plan-status|${planId.toString().lowercase()}|$dayNumber".encodeUtf8().sha256().toByteArray().copyOf(16),
        )!!

    // ---- Value access ----------------------------------------------------------------------------------------

    /** The value at [key] as [T]; a present value of another type is counted in [drops] and treated as absent. */
    private inline fun <reified T : LegacyValue> Map<String, LegacyValue?>.typed(key: String): T? {
        val v = this[key] ?: return null
        if (v is T) return v
        drop("unexpected value type ${v::class.simpleName} for $key")
        return null
    }

    private fun parseObject(text: String): JsonObject? =
        try { Json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.plainLong(): Long? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private companion object {
        const val STATUS_PREFIX = "remote_sync.readingplans.android_status"
        const val START_PREFIX = "remote_sync.readingplans.start_date_millis"
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
