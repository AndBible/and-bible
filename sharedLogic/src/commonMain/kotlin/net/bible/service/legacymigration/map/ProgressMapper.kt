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
import net.bible.android.common.isConvertibleTo
import net.bible.android.common.toV11n
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.progress.ChapterReadHistory
import net.bible.android.database.progress.GlobalReadingProgressSettings
import net.bible.android.database.progress.MemorizationTarget
import net.bible.android.database.progress.MemorizedVerse
import net.bible.android.database.progress.ReadingSource
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.stage.StagingDatabases
import okio.ByteString.Companion.encodeUtf8
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications

/** The local `Setting` values a [ProgressMapper] needs: the JSON documents `reading_progress_state_v1` and `memorization_progress_state_v1`. */
class LegacyProgressData(val settings: Map<String, String>) {
    companion object {
        fun from(snapshot: LegacySnapshot) = LegacyProgressData(snapshot.settings)
    }
}

/** The current-schema rows a [ProgressMapper] run produces: [verses] by ordinal, [targets] newest first, [history] in stored order. */
class ProgressStage(
    val verses: List<MemorizedVerse>,
    val targets: List<MemorizationTarget>,
    val history: List<ChapterReadHistory>,
    val settings: GlobalReadingProgressSettings,
)

/**
 * Migrates the reading and memorization progress of the legacy Swift app into the current `ProgressDatabase`
 * (`MemorizedVerse`, `MemorizationTarget`, `ChapterReadHistory`, `GlobalReadingProgressSettings`).
 *
 * The legacy app keeps progress as two JSON documents in its local `Setting` store (`reading_progress_state_v1`,
 * `memorization_progress_state_v1`), not as entities. This is a port of the Swift app's own Android exporter,
 * `AndroidDatabaseBackupProgressMapper.writeDatabase`: the strict decoding of `ReadingProgressStore.strictSnapshot` and
 * `MemorizationProgressStore.persistenceSnapshotStrict` (with the legacy range format, the ordinal trust defaults of
 * `decodedLegacyMemorizationTrustMetadata` and the `normalizedVerses` merge), `exportableMemorizedVerses` and
 * `exportableMemorizationTargets` (only rows whose KJVA ordinals the app itself trusts,
 * `PersistedOrdinalTrustPolicy.isTrustedKJVARange`), and the insert order and columns of its `insert*` helpers. The
 * singleton settings row is always written, with defaults when the document is absent, as Swift does.
 * Differences, all deliberate:
 *
 * - **Failures are reported, not fatal.** Swift aborts the export when a document is undecodable or a row invalid. Here an
 *   undecodable document is skipped whole (`unreadable reading_progress_state_v1` / `unreadable
 *   memorization_progress_state_v1`, 1) while the other one is still written; invalid history rows (cycle below one,
 *   unknown KJVA book, chapter below one), values outside Android's 32-bit columns, duplicate ids and invalid settings
 *   values (negative cycle, unsupported word visibility, both replaced by the default as `normalized` does) are counted
 *   and skipped individually.
 * - Memorization rows without a trusted KJVA range are not written but counted per kind (Swift does not export them
 *   either), and so are rows merged away as duplicates. A memorization target without an `id` gets a deterministic id
 *   (Swift draws a random one), so such a row can never match the oracle byte for byte; identical id-less targets
 *   collapse into one and are counted (`identical memorization targets without id collapsed`).
 * - Rows go through the Room DAOs, whose converters (`IdType`, `ReadingSource`) round-trip these columns faithfully, in
 *   one writer transaction. `LogEntry` rows (remote sync) are not written.
 */
class ProgressMapper : CategoryMapper {
    override val category = LegacyCategory.PROGRESS

    override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) =
        map(LegacyProgressData.from(snapshot), staging, report)

    /** Maps [data] into [staging]; [map] with a snapshot reads the data from the stores and calls this. */
    suspend fun map(data: LegacyProgressData, staging: StagingDatabases, report: MigrationReport) {
        val stage = build(data, report)
        val db = staging.progress
        val dao = db.progressDao()
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                stage.verses.forEach { dao.insertMemorizedVerse(it) }
                stage.targets.forEach { dao.insertMemorizationTarget(it) }
                stage.history.forEach { dao.insertChapterReadHistory(it) }
                db.globalReadingProgressSettingsDao().set(stage.settings)
            }
        }
        report.written(category, "MemorizedVerse", stage.verses.size)
        report.written(category, "MemorizationTarget", stage.targets.size)
        report.written(category, "ChapterReadHistory", stage.history.size)
        report.written(category, "GlobalReadingProgressSettings", 1)
    }

    /** Builds the target rows without touching any database. Skipped rows and values are noted in [report]. */
    fun build(data: LegacyProgressData, report: MigrationReport): ProgressStage {
        val skips = LinkedHashMap<String, Long>()
        val skip = { reason: String, rows: Long -> if (rows > 0) skips[reason] = (skips[reason] ?: 0L) + rows }
        val reading = readReading(data.settings[READING_KEY], skip)
        val memorization = readMemorization(data.settings[MEMORIZATION_KEY], skip)
        for ((reason, rows) in skips) report.skipped(category, reason, rows.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return ProgressStage(memorization.first, memorization.second, reading.history, reading.settings)
    }

    /** Thrown by the strict decoders when a document does not decode like the Swift `Codable`. */
    private class Unreadable : Exception()

    // ---- reading_progress_state_v1 ----------------------------------------------------------------------------

    private class Reading(val history: List<ChapterReadHistory>, val settings: GlobalReadingProgressSettings)

    private class RawHistory(val id: IdType, val book: Long, val chapter: Long, val cycle: Long, val readAt: Long, val initials: String, val source: ReadingSource)

    private fun readReading(text: String?, skip: (String, Long) -> Unit): Reading {
        val defaults = Reading(emptyList(), GlobalReadingProgressSettings())
        if (text == null) return defaults
        val raws: List<RawHistory>
        val settings: JsonObject
        try {
            val root = parseObject(text)
            raws = (root["history"] as? JsonArray ?: throw Unreadable()).map { element ->
                val o = element as? JsonObject ?: throw Unreadable()
                RawHistory(
                    id = o.requiredUuid("id"),
                    book = o.requiredLong("kjvBookOrdinal"),
                    chapter = o.requiredLong("chapter"),
                    cycle = o.optionalLong("cycle") ?: 1L,
                    readAt = o.requiredLong("readAt"),
                    initials = o.optionalString("bookInitials") ?: "",
                    source = o.optionalString("source")?.let { name -> ReadingSource.entries.firstOrNull { it.name == name } ?: throw Unreadable() }
                        ?: ReadingSource.MANUAL,
                )
            }
            settings = root["settings"] as? JsonObject ?: throw Unreadable()
            // The synthesized Codable of the settings snapshot requires all eight keys.
            for (key in SETTING_BOOLEANS) settings.requiredBool(key)
            settings.requiredLong("activeCycle")
            settings.requiredString("memorizeWordVisibility")
        } catch (e: Unreadable) {
            skip("unreadable $READING_KEY", 1)
            return defaults
        }

        var invalid = 0L
        var outOfRange = 0L
        var duplicates = 0L
        val seen = HashSet<IdType>()
        val history = mutableListOf<ChapterReadHistory>()
        for (r in raws) {
            when {
                r.cycle <= 0 || r.chapter <= 0 || r.book !in Int.MIN_VALUE..Int.MAX_VALUE || !isKjvaBookOrdinal(r.book.toInt()) -> invalid++
                r.chapter > Int.MAX_VALUE || r.cycle > Int.MAX_VALUE -> outOfRange++
                !seen.add(r.id) -> duplicates++
                else -> history += ChapterReadHistory(r.id, r.book.toInt(), r.chapter.toInt(), r.cycle.toInt(), r.readAt, r.initials, r.source)
            }
        }
        skip("invalid reading history row", invalid)
        skip("reading history value out of range", outOfRange)
        skip("duplicate reading history id", duplicates)

        var invalidSettings = 0L
        var activeCycle = settings.requiredLong("activeCycle")
        if (activeCycle !in 0..Int.MAX_VALUE) { activeCycle = 0; invalidSettings++ }
        var visibility = settings.requiredString("memorizeWordVisibility")
        if (visibility !in WORD_VISIBILITIES) { visibility = "light"; invalidSettings++ }
        skip("invalid reading progress settings value", invalidSettings)
        return Reading(
            history,
            GlobalReadingProgressSettings(
                autoTrackReading = settings.requiredBool("autoTrackReading"),
                autoMarkMemorized = settings.requiredBool("autoMarkMemorized"),
                memorizeTypeFullWords = settings.requiredBool("memorizeTypeFullWords"),
                memorizeWordVisibility = visibility,
                memorizeErrorHeatmap = settings.requiredBool("memorizeErrorHeatmap"),
                memorizeScrambleHideUsed = settings.requiredBool("memorizeScrambleHideUsed"),
                memorizeIncludeReference = settings.requiredBool("memorizeIncludeReference"),
                activeCycle = activeCycle.toInt(),
            ),
        )
    }

    // ---- memorization_progress_state_v1 -----------------------------------------------------------------------

    /** `PersistedOrdinalTrustMetadata`: the durable trust decision and the retained source coordinates of a row. */
    private class Trust(
        val state: String,
        val mappingVersion: Long,
        val provenance: String,
        val sourceBookInitials: String?,
        val sourceVersification: String?,
        val sourceStart: Long?,
        val sourceEnd: Long?,
    )

    private class VerseRow(val id: IdType, val ordinal: Long, val memorizedAt: Long, val trust: Trust)

    private class TargetRow(val id: IdType, val start: Long, val end: Long, val createdAt: Long, val trust: Trust, val idGenerated: Boolean = false)

    private fun readMemorization(text: String?, skip: (String, Long) -> Unit): Pair<List<MemorizedVerse>, List<MemorizationTarget>> {
        val none = emptyList<MemorizedVerse>() to emptyList<MemorizationTarget>()
        if (text == null) return none
        val verses = mutableListOf<VerseRow>()
        val targets = mutableListOf<TargetRow>()
        // Ranges of the legacy format that cannot be trusted are counted instead of expanded one row per ordinal.
        var legacyUntrustedVerses = 0L
        var invalidRanges = 0L
        try {
            val root = parseObject(text)
            val verseArray = root.optionalArray("memorizedVerses")
            val targetArray = root.optionalArray("targetRows")
            val memorizedRanges = root.optionalArray("memorizedRanges").orEmpty().map { decodeRange(it) }
            val targetRanges = root.optionalArray("targetRanges").orEmpty().map { decodeRange(it) }
            if (verseArray != null) {
                verseArray.forEach { verses += decodeVerse(it) }
            } else {
                for (range in memorizedRanges) {
                    if (range.start <= 0 || range.end < range.start) { invalidRanges++; continue }
                    if (range.initials.isBlank()) {
                        for (ordinal in range.start..minOf(range.end, MAX_ORDINAL)) {
                            verses += VerseRow(stableVerseId(range.initials, ordinal), ordinal, 0, legacyTrust(range.initials, ordinal, ordinal))
                        }
                        legacyUntrustedVerses += (range.end - maxOf(MAX_ORDINAL, range.start - 1)).coerceAtLeast(0)
                    } else {
                        legacyUntrustedVerses += range.end - range.start + 1
                    }
                }
            }
            if (targetArray != null) {
                targetArray.forEach { targets += decodeTarget(it) }
            } else {
                for (range in targetRanges) {
                    targets += TargetRow(stableTargetId(range.initials, range.start, range.end, 0), range.start, range.end, 0, legacyTrust(range.initials, range.start, range.end), idGenerated = true)
                }
            }
        } catch (e: Unreadable) {
            skip("unreadable $MEMORIZATION_KEY", 1)
            return none
        }

        skip("invalid memorized verse range", invalidRanges)
        // `normalizedVerses`: one trusted row per ordinal, the newest (ties: the smaller id) wins; untrusted rows are quarantined by id.
        val trustedVerses = LinkedHashMap<Long, VerseRow>()
        var untrustedVerses = legacyUntrustedVerses
        var duplicateVerses = 0L
        for (v in verses) {
            if (!isTrusted(v.trust, v.ordinal, v.ordinal)) { untrustedVerses++; continue }
            val existing = trustedVerses[v.ordinal]
            if (existing == null) { trustedVerses[v.ordinal] = v; continue }
            duplicateVerses++
            if (v.memorizedAt > existing.memorizedAt || (v.memorizedAt == existing.memorizedAt && v.id.toString().uppercase() < existing.id.toString().uppercase())) {
                trustedVerses[v.ordinal] = v
            }
        }
        skip("memorized verse without trusted ordinals", untrustedVerses)
        skip("duplicate memorized verse", duplicateVerses)
        val seenVerseIds = HashSet<IdType>()
        var duplicateVerseIds = 0L
        val outVerses = trustedVerses.values.sortedBy { it.ordinal }.mapNotNull {
            if (!seenVerseIds.add(it.id)) { duplicateVerseIds++; null } else MemorizedVerse(it.id, it.ordinal.toInt(), it.memorizedAt)
        }
        skip("duplicate memorized verse id", duplicateVerseIds)

        val trustedTargets = targets.filter { isTrusted(it.trust, it.start, it.end) }
        skip("memorization target without trusted ordinals", (targets.size - trustedTargets.size).toLong())
        val seenTargetIds = HashSet<IdType>()
        var duplicateTargetIds = 0L
        var collapsedIdless = 0L
        val outTargets = trustedTargets.sortedWith(
            compareBy<TargetRow>({ -it.createdAt }, { it.start }, { it.end }, { it.id.toString().uppercase() }),
        ).mapNotNull {
            if (!seenTargetIds.add(it.id)) { if (it.idGenerated) collapsedIdless++ else duplicateTargetIds++; null } else MemorizationTarget(it.id, it.start.toInt(), it.end.toInt(), it.createdAt)
        }
        skip("duplicate memorization target id", duplicateTargetIds)
        skip("identical memorization targets without id collapsed", collapsedIdless)
        return outVerses to outTargets
    }

    private class Range(val initials: String, val start: Long, val end: Long)

    /** `MemorizationProgressRange`: all three keys are required. */
    private fun decodeRange(element: JsonElement): Range {
        val o = element as? JsonObject ?: throw Unreadable()
        return Range(o.requiredString("bookInitials"), o.requiredLong("startOrdinal"), o.requiredLong("endOrdinal"))
    }

    private fun decodeVerse(element: JsonElement): VerseRow {
        val o = element as? JsonObject ?: throw Unreadable()
        val initials = o.optionalString("bookInitials") ?: ""
        val ordinal = o.requiredLong("kjvOrdinal")
        return VerseRow(
            id = o.optionalUuid("id") ?: stableVerseId(initials, ordinal),
            ordinal = ordinal,
            memorizedAt = o.optionalLong("memorizedAt") ?: 0L,
            trust = o.optionalObject("ordinalTrust")?.let { decodeTrust(it) } ?: legacyTrust(initials, ordinal, ordinal),
        )
    }

    private fun decodeTarget(element: JsonElement): TargetRow {
        val o = element as? JsonObject ?: throw Unreadable()
        val initials = o.optionalString("bookInitials") ?: ""
        val start = o.requiredLong("startOrdinal")
        val end = o.requiredLong("endOrdinal")
        val createdAt = o.optionalLong("createdAt") ?: 0L
        val given = o.optionalUuid("id")
        return TargetRow(
            id = given ?: stableTargetId(initials, start, end, createdAt),
            idGenerated = given == null,
            start = start,
            end = end,
            createdAt = createdAt,
            trust = o.optionalObject("ordinalTrust")?.let { decodeTrust(it) } ?: legacyTrust(initials, start, end),
        )
    }

    /** `PersistedOrdinalTrustMetadata.init(from:)`: an unknown state or provenance decodes as unresolved/unknown. */
    private fun decodeTrust(o: JsonObject): Trust {
        val state = o.optionalString("state")?.takeIf { it in TRUST_STATES } ?: UNRESOLVED
        return Trust(
            state = state,
            mappingVersion = if (state == VERIFIED_ANDROID || state == VERIFIED_MAPPING_V1) o.optionalLong("mappingVersion") ?: 0L else 0L,
            provenance = o.optionalString("provenance")?.takeIf { it in PROVENANCES } ?: "unknown",
            sourceBookInitials = o.optionalString("sourceBookInitials"),
            sourceVersification = o.optionalString("sourceVersification"),
            sourceStart = o.optionalLong("sourceOrdinalStart"),
            sourceEnd = o.optionalLong("sourceOrdinalEnd"),
        )
    }

    /**
     * Port of `decodedLegacyMemorizationTrustMetadata`: a row without trust object and with empty initials and a valid
     * range is global KJVA data (`verifiedMappingV1`, `legacyMigration`); anything else is quarantined.
     */
    private fun legacyTrust(bookInitials: String, start: Long, end: Long): Trust {
        val initials = bookInitials.trim()
        if (initials.isEmpty() && isValidRange(start, end)) {
            return Trust(VERIFIED_MAPPING_V1, CURRENT_MAPPING_VERSION, "legacyMigration", "KJVA", "KJVA", start, end)
        }
        return Trust(if (initials.isEmpty()) UNRESOLVED else PENDING, 0, "unknown", initials.ifEmpty { null }, null, start, end)
    }

    /**
     * Port of `PersistedOrdinalTrustPolicy.isTrustedKJVARange`, including the last step for `verifiedMappingV1`: the source
     * ordinals are re-mapped to KJVA through JSword and must equal the stored KJVA ordinals.
     */
    private fun isTrusted(t: Trust, start: Long, end: Long): Boolean {
        if (t.state != VERIFIED_ANDROID && t.state != VERIFIED_MAPPING_V1) return false
        val versification = t.sourceVersification?.trim()
        val sourceStart = t.sourceStart
        val sourceEnd = t.sourceEnd
        if (t.mappingVersion != CURRENT_MAPPING_VERSION || !isValidRange(start, end) ||
            versification.isNullOrEmpty() || !isKnownVersification(versification) ||
            sourceStart == null || sourceEnd == null || sourceStart <= 0 || sourceEnd < sourceStart
        ) return false
        if (t.state == VERIFIED_ANDROID) return t.provenance == "androidImport"
        return (t.provenance == "nativeMapping" || t.provenance == "legacyMigration") &&
            t.sourceBookInitials?.isBlank() == false &&
            rederivedKjvaRange(versification, sourceStart, sourceEnd) == start..end
    }

    private fun isValidRange(start: Long, end: Long) = start in 1..MAX_ORDINAL && end in 1..MAX_ORDINAL && end >= start

    /** Port of `MemorizedVerseProgress.stableID`: the first 16 bytes of SHA-256 of `memorized-verse|<initials>|<ordinal>`. */
    private fun stableVerseId(bookInitials: String, ordinal: Long): IdType = hashId("memorized-verse|$bookInitials|$ordinal")

    private fun stableTargetId(bookInitials: String, start: Long, end: Long, createdAt: Long): IdType =
        hashId("memorization-target|$bookInitials|$start|$end|$createdAt")

    private fun hashId(seed: String): IdType = IdType.fromByteArray(seed.encodeUtf8().sha256().toByteArray().copyOf(16))!!

    // ---- Strict JSON access (the Codable semantics: a present value of the wrong type is an error) --------------

    private fun parseObject(text: String): JsonObject =
        try { Json.parseToJsonElement(text) as? JsonObject ?: throw Unreadable() } catch (e: Unreadable) { throw e } catch (e: Exception) { throw Unreadable() }

    /** The value at [key], null when absent or JSON null (`decodeIfPresent`). */
    private fun JsonObject.present(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

    private fun JsonElement.asLong(): Long = (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: throw Unreadable()

    private fun JsonElement.asString(): String = (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw Unreadable()

    private fun JsonElement.asBool(): Boolean = (this as? JsonPrimitive)?.takeIf { !it.isString && (it.content == "true" || it.content == "false") }?.content?.toBoolean() ?: throw Unreadable()

    private fun JsonElement.asUuid(): IdType =
        asString().takeIf { UUID_PATTERN.matches(it) }?.let { IdType.fromString(it.lowercase()) } ?: throw Unreadable()

    private fun JsonObject.requiredLong(key: String) = (present(key) ?: throw Unreadable()).asLong()
    private fun JsonObject.requiredString(key: String) = (present(key) ?: throw Unreadable()).asString()
    private fun JsonObject.requiredBool(key: String) = (present(key) ?: throw Unreadable()).asBool()
    private fun JsonObject.requiredUuid(key: String) = (present(key) ?: throw Unreadable()).asUuid()
    private fun JsonObject.optionalLong(key: String) = present(key)?.asLong()
    private fun JsonObject.optionalString(key: String) = present(key)?.asString()
    private fun JsonObject.optionalUuid(key: String) = present(key)?.asUuid()
    private fun JsonObject.optionalObject(key: String) = present(key)?.let { it as? JsonObject ?: throw Unreadable() }
    private fun JsonObject.optionalArray(key: String) = present(key)?.let { it as? JsonArray ?: throw Unreadable() }

    companion object {
        const val READING_KEY = "reading_progress_state_v1"
        const val MEMORIZATION_KEY = "memorization_progress_state_v1"
        private const val CURRENT_MAPPING_VERSION = 1L
        private const val VERIFIED_ANDROID = "verifiedAndroid"
        private const val VERIFIED_MAPPING_V1 = "verifiedMappingV1"
        private const val PENDING = "legacyPendingModule"
        private const val UNRESOLVED = "legacyUnresolved"
        private val TRUST_STATES = setOf(VERIFIED_ANDROID, VERIFIED_MAPPING_V1, PENDING, UNRESOLVED)
        private val PROVENANCES = setOf("unknown", "nativeMapping", "androidImport", "legacyMigration")
        private val WORD_VISIBILITIES = setOf("light", "dim", "hidden")
        private val SETTING_BOOLEANS = listOf(
            "autoTrackReading", "autoMarkMemorized", "memorizeTypeFullWords", "memorizeErrorHeatmap", "memorizeScrambleHideUsed", "memorizeIncludeReference",
        )
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** `JSwordKJVAVersification.maximumOrdinal`: progress ordinals are 1 to this. */
        private val MAX_ORDINAL: Long get() = BookmarksMapper.MAX_KJVA_ORDINAL

        /**
         * Whether [ordinal] is a valid `ChapterReadHistory.kjvBookOrdinal` (Swift `ReadingProgressKJVAIdentity.init?(androidKJVBookOrdinal:chapter:)`):
         * a `BibleBook.ordinal()` of one of the 80 real books of the KJVA versification, book introductions excluded.
         */
        fun isKjvaBookOrdinal(ordinal: Int): Boolean {
            val book = BibleBook.entries.firstOrNull { it.ordinal == ordinal } ?: return false
            return book != BibleBook.INTRO_BIBLE && book != BibleBook.INTRO_OT && book != BibleBook.INTRO_NT && KJVA.containsBook(book)
        }
    }
}
