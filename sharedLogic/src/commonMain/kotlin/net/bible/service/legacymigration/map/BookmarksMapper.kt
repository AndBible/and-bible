package net.bible.service.legacymigration.map

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteStatement
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.AI_LABEL_ID
import net.bible.android.database.bookmarks.AI_LABEL_NAME
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.android.database.bookmarks.UNLABELED_NAME
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.legacymigration.stage.StagingDatabases
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications

/**
 * The rows of the nine bookmark entities of the legacy cloud store, plus the local `Setting` map that carries the
 * Android fidelity side data. [from] reads them from a [LegacySnapshot]; tests can also build one by hand.
 */
class LegacyBookmarkData(
    val labels: List<LegacyRow>,
    val bibleBookmarks: List<LegacyRow>,
    val bibleNotes: List<LegacyRow>,
    val bibleLinks: List<LegacyRow>,
    val genericBookmarks: List<LegacyRow>,
    val genericNotes: List<LegacyRow>,
    val genericLinks: List<LegacyRow>,
    val studyPadEntries: List<LegacyRow>,
    val studyPadTexts: List<LegacyRow>,
    val settings: Map<String, String>,
) {
    companion object {
        fun from(snapshot: LegacySnapshot) = LegacyBookmarkData(
            labels = snapshot.cloud.rows("Label"),
            bibleBookmarks = snapshot.cloud.rows("BibleBookmark"),
            bibleNotes = snapshot.cloud.rows("BibleBookmarkNotes"),
            bibleLinks = snapshot.cloud.rows("BibleBookmarkToLabel"),
            genericBookmarks = snapshot.cloud.rows("GenericBookmark"),
            genericNotes = snapshot.cloud.rows("GenericBookmarkNotes"),
            genericLinks = snapshot.cloud.rows("GenericBookmarkToLabel"),
            studyPadEntries = snapshot.cloud.rows("StudyPadTextEntry"),
            studyPadTexts = snapshot.cloud.rows("StudyPadTextEntryText"),
            settings = snapshot.settings,
        )
    }
}

/** A `Label` row in the current (database version 13) shape. Style ints are `BookmarkDisplayStyle` ordinals. */
data class StagedLabel(
    val id: IdType,
    val name: String,
    val color: Int,
    val displayStyle: Int,
    /** NULL means "same as [displayStyle]", exactly as the v12 to v13 migration stores it. */
    val displayStyleWholeVerse: Int?,
    val favourite: Boolean,
    val type: String?,
    val customIcon: String?,
)

/** A `BibleBookmark` row. [book] and [v11n] are the stored strings, not resolved JSword objects. */
data class StagedBibleBookmark(
    val id: IdType,
    val kjvOrdinalStart: Int,
    val kjvOrdinalEnd: Int,
    val ordinalStart: Int,
    val ordinalEnd: Int,
    val v11n: String,
    val playbackSettings: String?,
    val createdAt: Long,
    val book: String?,
    val startOffset: Int?,
    val endOffset: Int?,
    val primaryLabelId: IdType?,
    val lastUpdatedOn: Long,
    val wholeVerse: Boolean,
    val type: String?,
    val customIcon: String?,
    val sourcePromptId: IdType?,
    val editActionMode: String?,
    val editActionContent: String?,
)

/** A `GenericBookmark` row. */
data class StagedGenericBookmark(
    val id: IdType,
    val key: String,
    val createdAt: Long,
    val bookInitials: String,
    val ordinalStart: Int?,
    val ordinalEnd: Int?,
    val startOffset: Int?,
    val endOffset: Int?,
    val primaryLabelId: IdType?,
    val lastUpdatedOn: Long,
    val wholeVerse: Boolean,
    val playbackSettings: String?,
    val customIcon: String?,
    val sourcePromptId: IdType?,
    val editActionMode: String?,
    val editActionContent: String?,
)

/** A `BibleBookmarkNotes` or `GenericBookmarkNotes` row. */
data class StagedNote(val bookmarkId: IdType, val notes: String, val contentType: String?, val sourcePromptId: IdType?)

/** A `BibleBookmarkToLabel` or `GenericBookmarkToLabel` row. */
data class StagedLink(val bookmarkId: IdType, val labelId: IdType, val orderNumber: Int, val indentLevel: Int, val expandContent: Boolean)

/** A `StudyPadTextEntry` row. */
data class StagedStudyPadEntry(
    val id: IdType,
    val labelId: IdType,
    val orderNumber: Int,
    val indentLevel: Int,
    val contentType: String?,
    val sourcePromptId: IdType?,
)

/** A `StudyPadTextEntryText` row. */
data class StagedStudyPadText(val entryId: IdType, val text: String)

/** The current-schema rows a [BookmarksMapper] run produces, each list in insertion (foreign key) order. */
class BookmarkStage(
    val labels: List<StagedLabel>,
    val bibleBookmarks: List<StagedBibleBookmark>,
    val bibleNotes: List<StagedNote>,
    val bibleLinks: List<StagedLink>,
    val genericBookmarks: List<StagedGenericBookmark>,
    val genericNotes: List<StagedNote>,
    val genericLinks: List<StagedLink>,
    val studyPadEntries: List<StagedStudyPadEntry>,
    val studyPadTexts: List<StagedStudyPadText>,
)

/**
 * Migrates labels, Bible and generic bookmarks (with their notes and label links) and StudyPad entries from the legacy
 * Swift cloud store into the current `BookmarkDatabase`.
 *
 * This is a port of the row building of the Swift app's own Android exporter:
 * `RemoteSyncBookmarkSnapshotService.makeSnapshot(includeUnverifiedBibleBookmarks: false)` (the ordinal trust filter,
 * the fixed system label ids, the Android `book` and `playbackSettings` fidelity merge, the label link projection and
 * the sort orders) and `RemoteSyncInitialBackupUploadService.buildBookmarkInitialBackup` with its `insert*Row` helpers
 * (the exact columns). Differences, all deliberate:
 *
 * - **Unverified Bible bookmarks are not written, and not lost silently** (plan decision D-a): a bookmark whose persisted
 *   KJVA ordinals the Swift app itself does not trust (`hasTrustedPersistedOrdinals` false: state `legacyPendingModule` or
 *   `legacyUnresolved`, or verified metadata that is internally inconsistent) is counted per state in the
 *   [MigrationReport] (`ordinal trust <state>`). It stays in the untouched legacy store, together with its note and
 *   label links, which are not exported either. The Swift exporter does the same (`suppressedKeys`).
 * - `PersistedOrdinalTrustPolicy.isTrustedKJVARange` is ported without its last step for `verifiedMappingV1` rows, which
 *   re-derives the KJVA range with the Swift `VersificationMapper` (no equivalent here): such a row is trusted when its
 *   state, mapping version, ranges, source versification, provenance and module initials are consistent.
 * - The Swift `Label` style booleans (`markerStyle`, `underlineStyle`, `hideStyle` and their whole verse variants) are
 *   converted to the current `displayStyle` columns with the exact rule of the `labelDisplayStyleEnum` database
 *   migration (hide before marker before underline; whole verse NULL when equal), because the Swift exporter writes
 *   database version 12.
 * - Rows are inserted with SQL, not Room entities: the entity converters resolve `book` through the installed modules
 *   and `v11n` through JSword and so would lose or reject what a migration has to carry verbatim. Text is bound as
 *   bytes (`CAST(? AS TEXT)`) so that embedded NUL characters survive.
 * - Values the Swift exporter writes blindly but that the current Android readers would reject are not written and are
 *   counted instead: a dangling `primaryLabelId` becomes NULL, a bookmark with an unknown versification is skipped
 *   (Android's converter throws on read), unknown `type`/`editAction.mode` enum strings become NULL, and note or StudyPad
 *   text rows without an exported owner are skipped (the Swift staging file has no foreign keys).
 * - A preserved verse range of a playback payload that JSword cannot parse is dropped from the payload, as in
 *   [WorkspacesMapper].
 * - `LogEntry` rows (the Swift `appendManualBookmarkLogEntries`) are not written: sync stays off, so the staged database
 *   carries no change history (decision U2). The iOS-only trust columns have no Android column and are not migrated.
 */
class BookmarksMapper : CategoryMapper {
    override val category = LegacyCategory.BOOKMARKS

    /** Values present but of an unexpected type, counted by reason during one [build]; makes [build] not thread-safe. */
    private val drops = LinkedHashMap<String, Int>()

    override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) =
        map(LegacyBookmarkData.from(snapshot), staging, report)

    /** Maps [data] into [staging]; [map] with a snapshot reads the data from the stores and calls this. */
    suspend fun map(data: LegacyBookmarkData, staging: StagingDatabases, report: MigrationReport) {
        val stage = build(data, report)
        val db = staging.bookmarks
        // One writer transaction: the staging file is either fully written or (on a crash) discarded.
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                for (l in stage.labels) usePrepared(LABEL_SQL) { it.bindLabel(l); it.step() }
                for (b in stage.bibleBookmarks) usePrepared(BIBLE_BOOKMARK_SQL) { it.bindBibleBookmark(b); it.step() }
                for (b in stage.genericBookmarks) usePrepared(GENERIC_BOOKMARK_SQL) { it.bindGenericBookmark(b); it.step() }
                for (n in stage.bibleNotes) usePrepared(noteSql("BibleBookmarkNotes")) { it.bindNote(n); it.step() }
                for (n in stage.genericNotes) usePrepared(noteSql("GenericBookmarkNotes")) { it.bindNote(n); it.step() }
                for (l in stage.bibleLinks) usePrepared(linkSql("BibleBookmarkToLabel")) { it.bindLink(l); it.step() }
                for (l in stage.genericLinks) usePrepared(linkSql("GenericBookmarkToLabel")) { it.bindLink(l); it.step() }
                for (e in stage.studyPadEntries) usePrepared(STUDYPAD_ENTRY_SQL) { it.bindStudyPadEntry(e); it.step() }
                for (t in stage.studyPadTexts) usePrepared(STUDYPAD_TEXT_SQL) { it.bindStudyPadText(t); it.step() }
            }
        }
        report.written(category, "Label", stage.labels.size)
        report.written(category, "BibleBookmark", stage.bibleBookmarks.size)
        report.written(category, "BibleBookmarkNotes", stage.bibleNotes.size)
        report.written(category, "BibleBookmarkToLabel", stage.bibleLinks.size)
        report.written(category, "GenericBookmark", stage.genericBookmarks.size)
        report.written(category, "GenericBookmarkNotes", stage.genericNotes.size)
        report.written(category, "GenericBookmarkToLabel", stage.genericLinks.size)
        report.written(category, "StudyPadTextEntry", stage.studyPadEntries.size)
        report.written(category, "StudyPadTextEntryText", stage.studyPadTexts.size)
    }

    /** Builds the target rows without touching any database. Skipped rows and values are noted in [report]. */
    fun build(data: LegacyBookmarkData, report: MigrationReport): BookmarkStage {
        drops.clear()
        try {
            return buildStage(data, report)
        } finally {
            for ((what, rows) in drops) skip(report, what, rows)
            drops.clear()
        }
    }

    private fun skip(report: MigrationReport, reason: String, rows: Int) {
        if (rows > 0) report.skipped(category, reason, rows)
    }

    private fun buildStage(data: LegacyBookmarkData, report: MigrationReport): BookmarkStage {
        val fidelity = Fidelity.read(data.settings, report, category)

        // ---- Labels. Swift: `labels` sorted (name, id); `remoteLabelIDsByLocalID`; later rows with the same remote
        // id replace earlier ones (`labelRowsByKey`).
        val labelRows = data.labels.filter { it.id("id") != null }
            .also { skip(report, "label without id", data.labels.size - it.size) }
            .sortedWith(compareBy<LegacyRow>({ it.values.text("name") ?: "" }, { it.id("id")!!.toString() }))
        val remoteIdByLocalId = HashMap<String, IdType>()
        val labelLocalIdByPk = HashMap<Long, IdType>()
        val labels = LinkedHashMap<String, StagedLabel>()
        for (row in labelRows) {
            val local = row.id("id")!!
            val name = row.values.text("name") ?: ""
            val remote = fixedLabelId(name) ?: local
            remoteIdByLocalId[local.toString()] = remote
            labelLocalIdByPk[row.pk] = local
            labels[remote.toString()] = label(row, remote, name, report)
        }
        skip(report, "duplicate label id", labelRows.size - labels.size)
        fun remoteLabel(local: IdType): IdType = remoteIdByLocalId[local.toString()] ?: local
        val stagedLabelIds = labels.keys

        /** Port of the `primaryLabelId` projection; a label that is not exported cannot be referenced (foreign key). */
        fun primaryLabel(row: LegacyRow): IdType? {
            val primary = row.values.id("primaryLabelId")?.let { remoteLabel(it) } ?: return null
            if (primary.toString() in stagedLabelIds) return primary
            drop("primary label of an unknown label dropped")
            return null
        }

        // ---- Notes, links.
        val bibleNotes = notesByBookmark(data.bibleNotes, report, "Bible")
        val genericNotes = notesByBookmark(data.genericNotes, report, "generic")

        // ---- Bible bookmarks. Swift: sorted (createdAt, id); an untrusted bookmark is suppressed together with its
        // note and links (they are only ever exported through the bookmark).
        val bibleRows = data.bibleBookmarks.filter { it.id("id") != null }
            .also { skip(report, "Bible bookmark without id", data.bibleBookmarks.size - it.size) }
            .sortedWith(compareBy<LegacyRow>({ it.values.date("createdAt") }, { it.id("id")!!.toString() }))
        val bibleIdByPk = bibleRows.associate { it.pk to it.id("id")!! }
        val bibleBookmarks = LinkedHashMap<String, StagedBibleBookmark>()
        val unverified = LinkedHashMap<String, Int>()
        var unknownV11n = 0
        for (row in bibleRows) {
            val id = row.id("id")!!
            val trust = ordinalTrust(row.values)
            if (trust != null) {
                unverified[trust] = (unverified[trust] ?: 0) + 1
                continue
            }
            val v11n = row.values.text("v11n") ?: DEFAULT_V11N
            if (!isKnownVersification(v11n)) { unknownV11n++; continue }
            bibleBookmarks[id.toString()] = bibleBookmark(row, id, v11n, primaryLabel(row), fidelity)
        }
        for ((state, rows) in unverified) skip(report, "ordinal trust $state", rows)
        skip(report, "Bible bookmark with unknown versification", unknownV11n)
        skip(report, "duplicate Bible bookmark id", bibleRows.size - unverified.values.sum() - unknownV11n - bibleBookmarks.size)

        // ---- Generic bookmarks (no trust concept).
        val genericRows = data.genericBookmarks.filter { it.id("id") != null }
            .also { skip(report, "generic bookmark without id", data.genericBookmarks.size - it.size) }
            .sortedWith(compareBy<LegacyRow>({ it.values.date("createdAt") }, { it.id("id")!!.toString() }))
        val genericIdByPk = genericRows.associate { it.pk to it.id("id")!! }
        val genericBookmarks = LinkedHashMap<String, StagedGenericBookmark>()
        for (row in genericRows) {
            val id = row.id("id")!!
            genericBookmarks[id.toString()] = genericBookmark(row, id, primaryLabel(row), fidelity)
        }
        skip(report, "duplicate generic bookmark id", genericRows.size - genericBookmarks.size)

        val bibleLinks = links(data.bibleLinks, bibleIdByPk, bibleBookmarks.keys, labelLocalIdByPk, ::remoteLabel, report, "Bible")
        val genericLinks = links(data.genericLinks, genericIdByPk, genericBookmarks.keys, labelLocalIdByPk, ::remoteLabel, report, "generic")

        val bibleNoteRows = bibleNotes.filterKeys { it in bibleBookmarks }.values.sortedBy { it.bookmarkId.toString() }
        val genericNoteRows = genericNotes.filterKeys { it in genericBookmarks }.values.sortedBy { it.bookmarkId.toString() }
        val bibleAllIds = bibleRows.map { it.id("id")!!.toString() }.toSet()
        skip(report, "Bible bookmark note without bookmark", bibleNotes.keys.count { it !in bibleAllIds })
        skip(report, "generic bookmark note without bookmark", genericNotes.keys.count { it !in genericBookmarks })

        // ---- StudyPad entries: exported only with a label; sorted (orderNumber, id).
        val textRows = data.studyPadTexts.mapNotNull { row ->
            val entryId = row.values.id("studyPadTextEntryId") ?: return@mapNotNull null
            entryId.toString() to (row.values.text("text") ?: "")
        }
        skip(report, "StudyPad text without entry id", data.studyPadTexts.size - textRows.size)
        val textByEntry = LinkedHashMap<String, String>()
        for ((entry, text) in textRows) textByEntry[entry] = text
        skip(report, "duplicate StudyPad text row", textRows.size - textByEntry.size)

        val entryRows = data.studyPadEntries.filter { it.id("id") != null }
            .also { skip(report, "StudyPad entry without id", data.studyPadEntries.size - it.size) }
        val entries = LinkedHashMap<String, StagedStudyPadEntry>()
        val withLabel = entryRows.filter { it.toOne["label"]?.let { pk -> pk in labelLocalIdByPk } == true }
        skip(report, "StudyPad entry without label", entryRows.size - withLabel.size)
        for (row in withLabel.sortedWith(compareBy<LegacyRow>({ it.values.int("orderNumber") ?: 0 }, { it.id("id")!!.toString() }))) {
            val id = row.id("id")!!
            entries[id.toString()] = StagedStudyPadEntry(
                id = id,
                labelId = remoteLabel(labelLocalIdByPk.getValue(row.toOne["label"]!!)),
                orderNumber = row.values.int("orderNumber") ?: 0,
                indentLevel = row.values.int("indentLevel") ?: 0,
                contentType = row.values.contentType("contentType"),
                sourcePromptId = row.values.id("sourcePromptId"),
            )
        }
        skip(report, "duplicate StudyPad entry id", withLabel.size - entries.size)
        val texts = textByEntry.filterKeys { it in entries }.map { (k, v) -> StagedStudyPadText(IdType.fromString(k), v) }
            .sortedBy { it.entryId.toString() }
        skip(report, "StudyPad text without exported entry", textByEntry.size - texts.size)

        return BookmarkStage(
            labels = labels.values.sortedWith(compareBy<StagedLabel>({ it.name }, { it.id.toString() })),
            bibleBookmarks = bibleBookmarks.values.toList(),
            bibleNotes = bibleNoteRows,
            bibleLinks = bibleLinks,
            genericBookmarks = genericBookmarks.values.toList(),
            genericNotes = genericNoteRows,
            genericLinks = genericLinks,
            studyPadEntries = entries.values.toList(),
            studyPadTexts = texts,
        )
    }

    // ---- Labels ----------------------------------------------------------------------------------------------

    /** Port of `AndroidBookmarkDatabaseContract.fixedLabelID(forName:)`. */
    private fun fixedLabelId(name: String): IdType? = when (name) {
        SPEAK_LABEL_NAME -> SPEAK_LABEL_ID
        UNLABELED_NAME -> UNLABELED_LABEL_ID
        PARAGRAH_BREAK_LABEL_NAME -> PARAGRAPH_BREAK_LABEL_ID
        AI_LABEL_NAME -> AI_LABEL_ID
        else -> null
    }

    private fun label(row: LegacyRow, id: IdType, name: String, report: MigrationReport): StagedLabel {
        val v = row.values
        fun style(hide: String, marker: String, underline: String): Int = when {
            v.bool(hide) == true -> 3
            v.bool(marker) == true -> 2
            v.bool(underline) == true -> 1
            else -> 0
        }
        val display = style("hideStyle", "markerStyle", "underlineStyle")
        val whole = style("hideStyleWholeVerse", "markerStyleWholeVerse", "underlineStyleWholeVerse")
        return StagedLabel(
            id = id,
            name = name,
            color = v.int("color") ?: 0,
            displayStyle = display,
            displayStyleWholeVerse = if (whole == display) null else whole,
            favourite = v.bool("favourite") ?: false,
            type = v.text("type").enumOrNull(LABEL_TYPES, "label type"),
            customIcon = v.text("customIcon"),
        )
    }

    // ---- Bookmarks -------------------------------------------------------------------------------------------

    /**
     * Port of `hasTrustedPersistedOrdinals` (`PersistedOrdinalTrustPolicy.isTrustedKJVARange`). Returns null for a trusted
     * bookmark, else the report reason suffix: the state, or `<state> inconsistent` for a verified state whose metadata
     * does not hold together. An absent or unknown state decodes fail-closed like Swift (default pending, unknown
     * unresolved).
     */
    private fun ordinalTrust(v: Map<String, LegacyValue?>): String? {
        val raw = v.text("ordinalTrustStateRaw") ?: PENDING
        val state = if (raw in TRUST_STATES) raw else UNRESOLVED
        if (state != VERIFIED_ANDROID && state != VERIFIED_MAPPING_V1) return state
        val start = v.long("kjvOrdinalStart") ?: 0L
        val end = v.long("kjvOrdinalEnd") ?: 0L
        val sourceStart = v.long("ordinalSourceStart")
        val sourceEnd = v.long("ordinalSourceEnd")
        val provenance = v.text("ordinalProvenanceRaw") ?: "unknown"
        val consistent = (v.long("ordinalMappingVersion") ?: 0L) == CURRENT_MAPPING_VERSION &&
            start in 1..MAX_KJVA_ORDINAL && end in 1..MAX_KJVA_ORDINAL && end >= start &&
            v.text("ordinalSourceVersification")?.trim()?.let { isKnownVersification(it) } == true &&
            sourceStart != null && sourceEnd != null && sourceStart > 0 && sourceEnd >= sourceStart &&
            when (state) {
                VERIFIED_ANDROID -> provenance == "androidImport"
                else -> (provenance == "nativeMapping" || provenance == "legacyMigration") &&
                    v.text("bookInitials")?.isNotBlank() == true
            }
        return if (consistent) null else "$state inconsistent"
    }

    private fun isKnownVersification(name: String): Boolean {
        if (name.isBlank()) return false
        return try {
            Versifications.instance().getVersification(name) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun bibleBookmark(
        row: LegacyRow,
        id: IdType,
        v11n: String,
        primaryLabel: IdType?,
        fidelity: Fidelity,
    ): StagedBibleBookmark {
        val v = row.values
        // Swift: synthesized from the native playback columns first, else the preserved Android payload.
        val playback = playbackJson(v) ?: fidelity.playback["bible.$id"]
        // Swift `androidBookColumnValue`, unless the exact Android value was preserved (the NULL sentinel included).
        val book = if (id.toString() in fidelity.book) fidelity.book.getValue(id.toString())
        else v.text("bookInitials")?.trim()?.ifEmpty { null }
        return StagedBibleBookmark(
            id = id,
            kjvOrdinalStart = v.int("kjvOrdinalStart") ?: 0,
            kjvOrdinalEnd = v.int("kjvOrdinalEnd") ?: 0,
            ordinalStart = v.int("ordinalStart") ?: 0,
            ordinalEnd = v.int("ordinalEnd") ?: 0,
            v11n = v11n,
            playbackSettings = playback,
            createdAt = v.date("createdAt"),
            book = book,
            startOffset = v.int("startOffset"),
            endOffset = v.int("endOffset"),
            primaryLabelId = primaryLabel,
            lastUpdatedOn = v.date("lastUpdatedOn"),
            wholeVerse = v.bool("wholeVerse") ?: true,
            type = v.text("type").enumOrNull(BOOKMARK_TYPES, "bookmark type"),
            customIcon = v.text("customIcon"),
            sourcePromptId = v.id("sourcePromptId"),
            editActionMode = v.text("editAction.mode.mode").enumOrNull(EDIT_ACTION_MODES, "edit action mode"),
            editActionContent = v.text("editAction.content"),
        )
    }

    private fun genericBookmark(row: LegacyRow, id: IdType, primaryLabel: IdType?, fidelity: Fidelity): StagedGenericBookmark {
        val v = row.values
        return StagedGenericBookmark(
            id = id,
            key = v.text("key") ?: "",
            createdAt = v.date("createdAt"),
            bookInitials = v.text("bookInitials") ?: "",
            ordinalStart = v.int("ordinalStart"),
            ordinalEnd = v.int("ordinalEnd"),
            startOffset = v.int("startOffset"),
            endOffset = v.int("endOffset"),
            primaryLabelId = primaryLabel,
            lastUpdatedOn = v.date("lastUpdatedOn"),
            wholeVerse = v.bool("wholeVerse") ?: true,
            playbackSettings = playbackJson(v) ?: fidelity.playback["generic.$id"],
            customIcon = v.text("customIcon"),
            sourcePromptId = v.id("sourcePromptId"),
            editActionMode = v.text("editAction.mode.mode").enumOrNull(EDIT_ACTION_MODES, "edit action mode"),
            editActionContent = v.text("editAction.content"),
        )
    }

    /**
     * Port of `PlaybackSettings.androidJSON()` (`JSONSerialization` with `.sortedKeys`: every field, explicit nulls, no
     * whitespace, `/` escaped) of the flattened `playbackSettings.*` columns; null when the bookmark has none (the Swift
     * struct is optional, so its non-optional columns are all NULL). `isMemorizationLoop` is runtime-only and not in the
     * JSON. The verse range is the Swift `verseRange.description`, kept only when JSword can parse it (see class KDoc).
     */
    private fun playbackJson(v: Map<String, LegacyValue?>): String? {
        val p = "playbackSettings."
        val present = listOf("speakChapterChanges", "speakTitles", "speakFootnotes", "speed", "bookId", "bookmarkWasCreated", "verseRangeAndroidString")
            .any { v[p + it] != null }
        if (!present) return null
        val range = v.text("${p}verseRangeAndroidString")?.let { verseRange(it) }
        return buildString {
            append("{\"bookId\":").append(jsonString(v.text("${p}bookId")))
            append(",\"bookmarkWasCreated\":").append(v.bool("${p}bookmarkWasCreated")?.toString() ?: "null")
            append(",\"speakChapterChanges\":").append(v.bool("${p}speakChapterChanges") ?: true)
            append(",\"speakFootnotes\":").append(v.bool("${p}speakFootnotes") ?: false)
            append(",\"speakTitles\":").append(v.bool("${p}speakTitles") ?: true)
            append(",\"speed\":").append(v.int("${p}speed") ?: 100)
            append(",\"verseRange\":").append(jsonString(range))
            append('}')
        }
    }

    /** `SpeakVerseRange(androidString:)`: trimmed `versification::osisRef`, both non-empty; JSword must parse it. */
    private fun verseRange(text: String): String? {
        val at = text.indexOf("::")
        if (at < 0) return null
        val v11nName = text.substring(0, at).trim()
        val osis = text.substring(at + 2).trim()
        if (v11nName.isEmpty() || osis.isEmpty()) return null
        return try {
            val v11n = Versifications.instance().getVersification(v11nName) ?: return droppedRange()
            VerseRangeFactory.fromString(v11n, osis)
            "$v11nName::$osis"
        } catch (e: Exception) {
            droppedRange()
        }
    }

    private fun droppedRange(): String? {
        drop("playback verse range not parseable by JSword dropped")
        return null
    }

    // ---- Notes and links -------------------------------------------------------------------------------------

    /** Detached notes by bookmark id (Swift: `Dictionary(uniqueKeysWithValues:)`, which would trap on a duplicate: last wins here). */
    private fun notesByBookmark(rows: List<LegacyRow>, report: MigrationReport, kind: String): Map<String, StagedNote> {
        val result = LinkedHashMap<String, StagedNote>()
        var withoutId = 0
        for (row in rows) {
            val id = row.values.id("bookmarkId")
            if (id == null) { withoutId++; continue }
            result[id.toString()] = StagedNote(
                bookmarkId = id,
                notes = row.values.text("notes") ?: "",
                contentType = row.values.contentType("contentType"),
                sourcePromptId = row.values.id("sourcePromptId"),
            )
        }
        skip(report, "$kind bookmark note without bookmark id", withoutId)
        skip(report, "duplicate $kind bookmark note", rows.size - withoutId - result.size)
        return result
    }

    /**
     * Port of the label link projection: a link needs both its bookmark and its label; the label is mapped to its remote
     * id; per bookmark links are sorted (orderNumber, label id) and a later link to the same label replaces an earlier one.
     * Only links of exported bookmarks are written, ordered by (bookmark id, label id) like the Swift initial backup.
     */
    private fun links(
        rows: List<LegacyRow>,
        bookmarkIdByPk: Map<Long, IdType>,
        exported: Set<String>,
        labelLocalIdByPk: Map<Long, IdType>,
        remoteLabel: (IdType) -> IdType,
        report: MigrationReport,
        kind: String,
    ): List<StagedLink> {
        var dangling = 0
        class Raw(val bookmark: IdType, val label: IdType, val order: Int, val indent: Int, val expand: Boolean)
        val raws = rows.mapNotNull { row ->
            val bookmark = row.toOne["bookmark"]?.let { bookmarkIdByPk[it] }
            val label = row.toOne["label"]?.let { labelLocalIdByPk[it] }?.let(remoteLabel)
            if (bookmark == null || label == null) { dangling++; return@mapNotNull null }
            Raw(bookmark, label, row.values.int("orderNumber") ?: 0, row.values.int("indentLevel") ?: 0, row.values.bool("expandContent") ?: false)
        }
        skip(report, "$kind bookmark label link without bookmark or label", dangling)
        val byKey = LinkedHashMap<String, StagedLink>()
        for (raw in raws.sortedWith(compareBy<Raw>({ it.order }, { it.label.toString() }))) {
            byKey["${raw.bookmark}|${raw.label}"] = StagedLink(raw.bookmark, raw.label, raw.order, raw.indent, raw.expand)
        }
        skip(report, "duplicate $kind bookmark label link", raws.size - byKey.size)
        return byKey.values.filter { it.bookmarkId.toString() in exported }
            .sortedWith(compareBy<StagedLink>({ it.bookmarkId.toString() }, { it.labelId.toString() }))
    }

    // ---- Fidelity settings -----------------------------------------------------------------------------------

    /**
     * The `remote_sync.bookmarks.android_book.<id>` and `remote_sync.bookmarks.android_playback_settings.<kind>.<id>`
     * settings, decoded like `RemoteSyncBookmarkAndroidBookStore` and `RemoteSyncBookmarkPlaybackSettingsStore`
     * `snapshotEntries()`: only keys spelled exactly canonically (lower-case UUID), never an empty value. A raw book value
     * of `__android_null__` means Android stored NULL and is a present entry with a null value. [book] is keyed by
     * bookmark id, [playback] by `bible.<id>` or `generic.<id>`.
     */
    internal class Fidelity(val book: Map<String, String?>, val playback: Map<String, String>) {
        companion object {
            private const val BOOK_PREFIX = "remote_sync.bookmarks.android_book."
            private const val PLAYBACK_PREFIX = "remote_sync.bookmarks.android_playback_settings."
            private const val NULL_SENTINEL = "__android_null__"
            private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

            fun read(settings: Map<String, String>, report: MigrationReport, category: LegacyCategory): Fidelity {
                var malformed = 0
                val book = LinkedHashMap<String, String?>()
                val playback = LinkedHashMap<String, String>()
                for ((key, value) in settings.entries.sortedBy { it.key }) {
                    when {
                        key.startsWith(BOOK_PREFIX) -> {
                            val id = key.removePrefix(BOOK_PREFIX)
                            if (value.isEmpty() || !uuid.matches(id)) { malformed++; continue }
                            book[id] = if (value == NULL_SENTINEL) null else value
                        }
                        key.startsWith(PLAYBACK_PREFIX) -> {
                            val rest = key.removePrefix(PLAYBACK_PREFIX)
                            val kind = rest.substringBefore('.')
                            val id = rest.substringAfter('.', "")
                            if (value.isEmpty() || (kind != "bible" && kind != "generic") || !uuid.matches(id)) { malformed++; continue }
                            playback["$kind.$id"] = value
                        }
                    }
                }
                if (malformed > 0) report.skipped(category, "malformed bookmark fidelity settings", malformed)
                return Fidelity(book, playback)
            }
        }
    }

    // ---- Value access ----------------------------------------------------------------------------------------

    private fun drop(reason: String) {
        drops[reason] = (drops[reason] ?: 0) + 1
    }

    /** The value at [key] as [T]; a present value of another type is counted in [drops] and treated as absent. */
    private inline fun <reified T : LegacyValue> Map<String, LegacyValue?>.typed(key: String): T? {
        val v = this[key] ?: return null
        if (v is T) return v
        drop("unexpected value type ${v::class.simpleName} for $key")
        return null
    }

    // Android columns are 32-bit; like the Swift `bindAndroidSignedInt32`/SQLite fetch, a wider value keeps its low 32 bits.
    private fun Map<String, LegacyValue?>.long(key: String): Long? = typed<LegacyValue.IntV>(key)?.value
    private fun Map<String, LegacyValue?>.int(key: String): Int? = long(key)?.toInt()
    private fun Map<String, LegacyValue?>.bool(key: String): Boolean? = typed<LegacyValue.BoolV>(key)?.value
    private fun Map<String, LegacyValue?>.text(key: String): String? = typed<LegacyValue.TextV>(key)?.value
    private fun Map<String, LegacyValue?>.id(key: String): IdType? = typed<LegacyValue.UuidV>(key)?.value?.let { IdType.fromString(it) }
    private fun Map<String, LegacyValue?>.date(key: String): Long = typed<LegacyValue.DateV>(key)?.epochMillis ?: 0L
    private fun LegacyRow.id(key: String) = values.id(key)

    /** `notesContentTypeRow`: only `HTML` and `MARKDOWN` survive, anything else becomes NULL (inherit). */
    private fun Map<String, LegacyValue?>.contentType(key: String): String? {
        val raw = text(key) ?: return null
        if (raw == "HTML" || raw == "MARKDOWN") return raw
        drop("unknown content type dropped")
        return null
    }

    private fun String?.enumOrNull(allowed: Set<String>, what: String): String? {
        if (this == null) return null
        if (this in allowed) return this
        drop("unknown $what dropped")
        return null
    }

    private companion object {
        const val DEFAULT_V11N = "KJVA"
        const val CURRENT_MAPPING_VERSION = 1L
        const val PENDING = "legacyPendingModule"
        const val UNRESOLVED = "legacyUnresolved"
        const val VERIFIED_ANDROID = "verifiedAndroid"
        const val VERIFIED_MAPPING_V1 = "verifiedMappingV1"
        val TRUST_STATES = setOf(VERIFIED_ANDROID, VERIFIED_MAPPING_V1, PENDING, UNRESOLVED)
        val LABEL_TYPES = setOf("HIGHLIGHT", "EXAMPLE")
        val BOOKMARK_TYPES = setOf("EXAMPLE")
        val EDIT_ACTION_MODES = setOf("APPEND", "PREPEND")

        /** `JSwordKJVAVersification.maximumOrdinal`: the highest ordinal JSword's `SystemKJVA` returns. */
        val MAX_KJVA_ORDINAL: Long by lazy { Versifications.instance().getVersification("KJVA")!!.maximumOrdinal().toLong() }

        /** `NSJSONSerialization` string literal: quotes, backslash and slash escaped, control characters as `\u00xx`. */
        fun jsonString(s: String?): String {
            if (s == null) return "null"
            return buildString {
                append('"')
                for (c in s) when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '/' -> append("\\/")
                    c == '\b' -> append("\\b")
                    c == '\u000C' -> append("\\f")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else -> append(c)
                }
                append('"')
            }
        }

        const val LABEL_SQL = "INSERT INTO Label (id, name, color, displayStyle, displayStyleWholeVerse, favourite, type, customIcon) " +
            "VALUES (?, CAST(? AS TEXT), ?, ?, ?, ?, CAST(? AS TEXT), CAST(? AS TEXT))"
        const val BIBLE_BOOKMARK_SQL = "INSERT INTO BibleBookmark (kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, " +
            "playbackSettings, id, createdAt, book, startOffset, endOffset, primaryLabelId, lastUpdatedOn, wholeVerse, type, " +
            "customIcon, sourcePromptId, editAction_mode, editAction_content) VALUES (?, ?, ?, ?, CAST(? AS TEXT), CAST(? AS TEXT), " +
            "?, ?, CAST(? AS TEXT), ?, ?, ?, ?, ?, CAST(? AS TEXT), CAST(? AS TEXT), ?, CAST(? AS TEXT), CAST(? AS TEXT))"
        const val GENERIC_BOOKMARK_SQL = "INSERT INTO GenericBookmark (id, `key`, createdAt, bookInitials, ordinalStart, ordinalEnd, " +
            "startOffset, endOffset, primaryLabelId, lastUpdatedOn, wholeVerse, playbackSettings, customIcon, sourcePromptId, " +
            "editAction_mode, editAction_content) VALUES (?, CAST(? AS TEXT), ?, CAST(? AS TEXT), ?, ?, ?, ?, ?, ?, ?, " +
            "CAST(? AS TEXT), CAST(? AS TEXT), ?, CAST(? AS TEXT), CAST(? AS TEXT))"
        const val STUDYPAD_ENTRY_SQL = "INSERT INTO StudyPadTextEntry (id, labelId, orderNumber, indentLevel, contentType, sourcePromptId) " +
            "VALUES (?, ?, ?, ?, CAST(? AS TEXT), ?)"
        const val STUDYPAD_TEXT_SQL = "INSERT INTO StudyPadTextEntryText (studyPadTextEntryId, text) VALUES (?, CAST(? AS TEXT))"
        fun noteSql(table: String) = "INSERT INTO $table (bookmarkId, notes, contentType, sourcePromptId) VALUES (?, CAST(? AS TEXT), CAST(? AS TEXT), ?)"
        fun linkSql(table: String) = "INSERT INTO $table (bookmarkId, labelId, orderNumber, indentLevel, expandContent) VALUES (?, ?, ?, ?, ?)"

        // Statement binding. Text goes in as UTF-8 bytes under CAST(? AS TEXT) so an embedded NUL is not a terminator.
        fun SQLiteStatement.text(i: Int, s: String?) = if (s == null) bindNull(i) else bindBlob(i, s.encodeToByteArray())
        fun SQLiteStatement.id(i: Int, id: IdType?) = if (id == null) bindNull(i) else bindBlob(i, id.toByteArray()!!)
        fun SQLiteStatement.int(i: Int, n: Int?) = if (n == null) bindNull(i) else bindLong(i, n.toLong())
        fun SQLiteStatement.bool(i: Int, b: Boolean) = bindLong(i, if (b) 1L else 0L)

        fun SQLiteStatement.bindLabel(l: StagedLabel) {
            id(1, l.id); text(2, l.name); int(3, l.color); int(4, l.displayStyle); int(5, l.displayStyleWholeVerse)
            bool(6, l.favourite); text(7, l.type); text(8, l.customIcon)
        }

        fun SQLiteStatement.bindBibleBookmark(b: StagedBibleBookmark) {
            int(1, b.kjvOrdinalStart); int(2, b.kjvOrdinalEnd); int(3, b.ordinalStart); int(4, b.ordinalEnd); text(5, b.v11n)
            text(6, b.playbackSettings); id(7, b.id); bindLong(8, b.createdAt); text(9, b.book); int(10, b.startOffset)
            int(11, b.endOffset); id(12, b.primaryLabelId); bindLong(13, b.lastUpdatedOn); bool(14, b.wholeVerse)
            text(15, b.type); text(16, b.customIcon); id(17, b.sourcePromptId); text(18, b.editActionMode); text(19, b.editActionContent)
        }

        fun SQLiteStatement.bindGenericBookmark(b: StagedGenericBookmark) {
            id(1, b.id); text(2, b.key); bindLong(3, b.createdAt); text(4, b.bookInitials); int(5, b.ordinalStart)
            int(6, b.ordinalEnd); int(7, b.startOffset); int(8, b.endOffset); id(9, b.primaryLabelId)
            bindLong(10, b.lastUpdatedOn); bool(11, b.wholeVerse); text(12, b.playbackSettings); text(13, b.customIcon)
            id(14, b.sourcePromptId); text(15, b.editActionMode); text(16, b.editActionContent)
        }

        fun SQLiteStatement.bindNote(n: StagedNote) {
            id(1, n.bookmarkId); text(2, n.notes); text(3, n.contentType); id(4, n.sourcePromptId)
        }

        fun SQLiteStatement.bindLink(l: StagedLink) {
            id(1, l.bookmarkId); id(2, l.labelId); int(3, l.orderNumber); int(4, l.indentLevel); bool(5, l.expandContent)
        }

        fun SQLiteStatement.bindStudyPadEntry(e: StagedStudyPadEntry) {
            id(1, e.id); id(2, e.labelId); int(3, e.orderNumber); int(4, e.indentLevel); text(5, e.contentType); id(6, e.sourcePromptId)
        }

        fun SQLiteStatement.bindStudyPadText(t: StagedStudyPadText) {
            id(1, t.entryId); text(2, t.text)
        }
    }
}
