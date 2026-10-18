package net.bible.service.legacymigration.map

import androidx.room3.immediateTransaction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.android.database.DbDate
import net.bible.android.database.GlobalTextDisplaySettings
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.legacymigration.plist.LegacyUuid
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.legacymigration.stage.StagingDatabases
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications

/**
 * The rows of the five workspace entities of the legacy stores, plus the local `Setting` map that carries the
 * Android fidelity side data. [from] reads them from a [LegacySnapshot]; tests can also build one by hand.
 */
class LegacyWorkspaceData(
    val workspaces: List<LegacyRow>,
    val windows: List<LegacyRow>,
    val pageManagers: List<LegacyRow>,
    val historyItems: List<LegacyRow>,
    val settings: Map<String, String>,
) {
    companion object {
        fun from(snapshot: LegacySnapshot) = LegacyWorkspaceData(
            workspaces = snapshot.cloud.rows("Workspace"),
            windows = snapshot.cloud.rows("Window"),
            pageManagers = snapshot.cloud.rows("PageManager"),
            historyItems = snapshot.cloud.rows("HistoryItem"),
            settings = snapshot.settings,
        )
    }
}

/** The current-schema rows a [WorkspacesMapper] run produces, in insertion (foreign key) order. */
class WorkspaceStage(
    val workspaces: List<WorkspaceEntities.Workspace>,
    val windows: List<WorkspaceEntities.Window>,
    val pageManagers: List<WorkspaceEntities.PageManager>,
    val historyItems: List<WorkspaceEntities.HistoryItem>,
    val labelOverrides: List<WorkspaceEntities.WorkspaceLabelOverride>,
    val global: GlobalTextDisplaySettings?,
)

/**
 * Migrates workspaces, windows, page positions, window history, label overrides and the global text display
 * settings from the legacy Swift stores into the current `WorkspaceDatabase`.
 *
 * This is a port of the row building of the Swift app's own Android exporter:
 * `RemoteSyncInitialBackupUploadService.buildWorkspaceInitialBackup` (and its `insert*Row`, `bind*` and
 * `projectWorkspaceHistory` helpers) over `RemoteSyncWorkspaceSnapshotService.snapshotCurrentStateStrict`, with the
 * Android-only values merged back from the `remote_sync.workspaces.fidelity.*` settings exactly as
 * `RemoteSyncWorkspaceFidelityStore` / `RemoteSyncWorkspaceRoomFidelityStore` define them. Differences, all deliberate:
 *
 * - The Bible location goes through [repairBibleBookEncoding] first (the Swift app runs
 *   `PageManagerBibleBookEncodingMigrationService` at startup, before any export).
 * - Rows are written through Room entities and DAOs, so the current Room converters produce the text columns: an
 *   empty id list, id set or cursor map is stored as `[]`/`{}` where the Swift exporter wrote NULL (Android's own
 *   writer also stores `[]`), and JSON key order is Kotlin's. The values are identical once parsed.
 * - Malformed stored fidelity or JSON values are skipped and counted in the [MigrationReport] where the Swift
 *   exporter would abort the whole export: a migration must not fail on a side value.
 * - `LogEntry`, `SyncStatus` and `SyncConfiguration` are not written: staged databases carry no sync history, and
 *   the sync triggers are not installed on a staging file.
 * - Workspace-level `speakSettings` is always rebuilt from the native columns (the Swift exporter does the same;
 *   its fidelity fallback `workspace.<id>` is only used when JSON encoding throws, which it never does).
 * - The Swift-only `PageManager.epubHref`/`epubIdentifier` and `TextDisplaySettings.enableVerseSelection` have no
 *   Android column and are not migrated.
 */
class WorkspacesMapper : CategoryMapper {
    override val category = LegacyCategory.WORKSPACES

    /**
     * Values that were present but of an unexpected [LegacyValue] type (or archive content), counted by reason during
     * one [build] and reported afterwards, so that nothing is dropped silently. Makes [build] not thread-safe.
     */
    private val drops = LinkedHashMap<String, Int>()

    override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) =
        map(LegacyWorkspaceData.from(snapshot), staging, report)

    /** Maps [data] into [staging]; [map] with a snapshot reads the data from the stores and calls this. */
    suspend fun map(data: LegacyWorkspaceData, staging: StagingDatabases, report: MigrationReport) {
        val stage = build(data, report)
        val db = staging.workspaces
        val dao = db.workspaceDao()
        // One writer transaction: the staging file is either fully written or (on a crash) discarded, and a heavy
        // user's history is not committed row by row.
        db.useWriterConnectionMarked { transactor ->
            transactor.immediateTransaction {
                stage.workspaces.forEach { dao.insertWorkspace(it) }
                stage.windows.forEach { dao.insertWindow(it) }
                stage.pageManagers.forEach { dao.insertPageManager(it) }
                if (stage.historyItems.isNotEmpty()) dao.insertHistoryItems(stage.historyItems)
                stage.labelOverrides.forEach { dao.insertOrUpdateLabelOverride(it) }
                stage.global?.let { db.globalTextDisplaySettingsDao().set(it) }
            }
        }
        report.written(category, "Workspace", stage.workspaces.size)
        report.written(category, "Window", stage.windows.size)
        report.written(category, "PageManager", stage.pageManagers.size)
        report.written(category, "HistoryItem", stage.historyItems.size)
        report.written(category, "WorkspaceLabelOverride", stage.labelOverrides.size)
        report.written(category, "GlobalTextDisplaySettings", if (stage.global == null) 0 else 1)
    }

    /** Builds the target rows without touching any database. Skipped rows and values are noted in [report]. */
    fun build(data: LegacyWorkspaceData, report: MigrationReport): WorkspaceStage {
        drops.clear()
        try {
            return buildStage(data, report)
        } finally {
            for ((what, rows) in drops) skip(report, what, rows)
            drops.clear()
        }
    }

    private fun buildStage(data: LegacyWorkspaceData, report: MigrationReport): WorkspaceStage {
        val fidelity = Fidelity.read(data.settings, report, category)

        // Port of `snapshotCurrentStateStrict`: workspaces in (orderNumber, id) order, each with its windows in the
        // same order; a later row with the same id replaces an earlier one (the Swift dictionaries are keyed by id).
        val workspaceRows = data.workspaces
            .filter { it.id("id") != null }
            .also { skip(report, "workspace without id", data.workspaces.size - it.size, "Workspace") }
            .sortedWith(compareBy<LegacyRow>({ it.int("orderNumber") ?: 0 }, { it.id("id")!!.toString() }))
        val workspaces = LinkedHashMap<String, WorkspaceEntities.Workspace>()
        val workspacePkToId = HashMap<Long, IdType>()
        for (row in workspaceRows) {
            val id = row.id("id")!!
            workspacePkToId[row.pk] = id
            workspaces[id.toString()] = workspace(row, id, fidelity, report)
        }
        skip(report, "duplicate workspace id", workspaceRows.size - workspaces.size, "Workspace")

        val pageManagersByPk = data.pageManagers.associateBy { it.pk }
        val pageManagerPkByWindowPk = data.pageManagers.mapNotNull { pm -> pm.toOne["window"]?.let { it to pm.pk } }.toMap()
        val windowRows = data.windows
            .filter { it.id("id") != null && it.toOne["workspace"] in workspacePkToId }
            .also { skip(report, "window without id or workspace", data.windows.size - it.size, "Window") }
            .sortedWith(
                compareBy<LegacyRow>(
                    { workspacePkToId.getValue(it.toOne["workspace"]!!).toString() },
                    { it.int("orderNumber") ?: 0 },
                    { it.id("id")!!.toString() },
                ),
            )
        val windows = LinkedHashMap<String, WorkspaceEntities.Window>()
        val pageManagers = LinkedHashMap<String, WorkspaceEntities.PageManager>()
        val usedPageManagerPks = HashSet<Long>() // a legacy page manager shared by two windows counts once
        for (row in windowRows) {
            val id = row.id("id")!!
            windows[id.toString()] = window(row, id, workspacePkToId.getValue(row.toOne["workspace"]!!))
            val pmRow = (row.toOne["pageManager"] ?: pageManagerPkByWindowPk[row.pk])?.let { pageManagersByPk[it] }
            if (pmRow != null) usedPageManagerPks += pmRow.pk
            pageManagers[id.toString()] = pageManager(pmRow, id, fidelity, report)
        }
        skip(report, "duplicate window id", windowRows.size - windows.size, "Window")
        skip(report, "page manager without an exported window", data.pageManagers.size - usedPageManagerPks.size, "PageManager")

        val history = historyItems(data, windows.keys, fidelity, report)

        val overrides = fidelity.labelOverrides.filter { it.workspaceId.toString() in workspaces }
            .sortedWith(compareBy({ it.workspaceId.toString() }, { it.labelId.toString() }))
        skip(report, "label override of an unknown workspace", fidelity.labelOverrides.size - overrides.size, "WorkspaceLabelOverride")

        return WorkspaceStage(
            workspaces = workspaces.values.toList(),
            windows = windows.values.toList(),
            pageManagers = pageManagers.values.toList(),
            historyItems = history,
            labelOverrides = overrides,
            global = global(data.settings, fidelity, report),
        )
    }

    private fun skip(report: MigrationReport, reason: String, rows: Int, table: String? = null) {
        if (rows > 0) report.skipped(category, reason, rows, table)
    }

    // ---- Workspace -------------------------------------------------------------------------------------------

    private fun workspace(row: LegacyRow, id: IdType, fidelity: Fidelity, report: MigrationReport): WorkspaceEntities.Workspace {
        val v = row.values
        val ws = workspaceSettings(v, report)
        return WorkspaceEntities.Workspace(
            name = v.text("name") ?: "",
            contentsText = v.text("contentsText"),
            id = id,
            orderNumber = v.int("orderNumber") ?: 0,
            textDisplaySettings = textDisplay(RowTds(v, "textDisplaySettings."), fidelity.workspaceText[id.toString()] ?: TextFidelity()),
            workspaceSettings = ws,
            unPinnedWeight = v.float("unPinnedWeight"),
            maximizedWindowId = v.id("maximizedWindowId"),
            primaryTargetLinksWindowId = v.id("primaryTargetLinksWindowId"),
        )
    }

    /** Port of `bindWorkspaceSettings` with `normalizeAutoAssignPrimaryLabel`; NULL columns take the Swift defaults. */
    private fun workspaceSettings(v: Map<String, LegacyValue?>, report: MigrationReport): WorkspaceEntities.WorkspaceSettings {
        val p = "workspaceSettings."
        val autoAssign = v.idSet("${p}autoAssignLabels")
        val primary = v.id("${p}autoAssignPrimaryLabel")
        return WorkspaceEntities.WorkspaceSettings(
            enableTiltToScroll = v.bool("${p}enableTiltToScroll") ?: false,
            enableReverseSplitMode = v.bool("${p}enableReverseSplitMode") ?: false,
            autoPin = v.bool("${p}autoPin") ?: true,
            speakSettings = speakSettings(v),
            recentLabels = recentLabels(v, "${p}recentLabels", report),
            autoAssignLabels = autoAssign.toMutableSet(),
            // `WorkspaceSettings.normalizeAutoAssignPrimaryLabel()`: the primary label is always one of the assigned.
            autoAssignPrimaryLabel = if (primary != null && primary in autoAssign) primary else autoAssign.firstOrNull(),
            studyPadCursors = studyPadCursors(v, "${p}studyPadCursors", report),
            hideCompareDocuments = v.stringSet("${p}hideCompareDocuments").toMutableSet(),
            limitAmbiguousModalSize = v.bool("${p}limitAmbiguousModalSize") ?: false,
            workspaceColor = v.int("workspaceColor"),
            restoreButtonsVisible = v.bool("${p}restoreButtonsVisible") ?: true,
        )
    }

    /** Port of `SpeakSettings.androidJSON()`'s source: the native Swift speak columns with Android's defaults. */
    private fun speakSettings(v: Map<String, LegacyValue?>): SpeakSettings {
        val s = "workspaceSettings.speakSettings."
        val pb = "${s}playbackSettings."
        return SpeakSettings(
            playbackSettings = PlaybackSettings(
                speakChapterChanges = v.bool("${pb}speakChapterChanges") ?: true,
                speakTitles = v.bool("${pb}speakTitles") ?: true,
                speakFootnotes = v.bool("${pb}speakFootnotes") ?: false,
                speed = v.int("${pb}speed") ?: 100,
                bookId = v.text("${pb}bookId"),
                bookmarkWasCreated = v.bool("${pb}bookmarkWasCreated"),
                verseRange = parseVerseRange(v.text("${pb}verseRangeAndroidString")),
            ),
            sleepTimer = v.int("${s}sleepTimer") ?: 0,
            lastSleepTimer = v.int("${s}lastSleepTimer") ?: 10,
            queue = v.bool("${s}queue") ?: true,
            repeat = v.bool("${s}repeatPlayback") ?: false,
            numPagesToSpeakId = v.int("${s}numPagesToSpeakId") ?: 0,
        )
    }

    /**
     * Parses `versification::osisRef`. Swift's `SpeakVerseRange(androidString:)` only checks the `::` separator and
     * that both parts are non-empty, but JSword's validation here is stricter on purpose: a range JSword cannot parse
     * (unknown versification, bad OSIS) becomes null, because Android's `PlaybackSettings.fromJson` would otherwise
     * reset the whole playback object when it reads the stored string. Expected deviation from the Swift oracle for
     * such ranges.
     */
    private fun parseVerseRange(text: String?): VerseRange? {
        if (text == null) return null
        return try {
            val parts = text.split("::")
            val v11n = Versifications.instance().getVersification(parts[0]) ?: return null
            VerseRangeFactory.fromString(v11n, parts[1])
        } catch (e: Exception) {
            null
        }
    }

    /**
     * `ZRECENTLABELS` is the Swift `[RecentLabel]` JSON with `lastAccess` as seconds since the Core Data reference
     * date (Foundation's default date coding). It is converted to Unix milliseconds with the arithmetic of
     * `Int64(Date.timeIntervalSince1970 * 1000.0)` (offset added in floating point, then truncated). Note that the Swift
     * exporter's `JSONEncoder` with `.millisecondsSince1970` writes a fractional Double (for example
     * `1700000001234.567`), which Android's Long cannot decode; the whole-millisecond Long written here is a deliberate
     * improvement and an expected deviation from the oracle for sub-millisecond values.
     */
    private fun recentLabels(v: Map<String, LegacyValue?>, key: String, report: MigrationReport): MutableList<WorkspaceEntities.RecentLabel> {
        val json = v.typed<LegacyValue.JsonV>(key)?.value ?: return mutableListOf()
        return try {
            json.parseJson().jsonArray.map {
                val o = it.jsonObject
                val seconds = o.getValue("lastAccess").jsonPrimitive.doubleOrNull ?: error("lastAccess")
                WorkspaceEntities.RecentLabel(
                    IdType.fromString(o.getValue("labelId").jsonPrimitive.content),
                    ((seconds + REFERENCE_DATE_OFFSET_SECONDS) * 1000.0).toLong(),
                )
            }.toMutableList()
        } catch (e: Exception) {
            skip(report, "malformed recentLabels JSON", 1)
            mutableListOf()
        }
    }

    /** `ZSTUDYPADCURSORS`: a Swift `[UUID: Int]` is coded as the flat array `[key, value, key, value, ...]`. */
    private fun studyPadCursors(v: Map<String, LegacyValue?>, key: String, report: MigrationReport): MutableMap<IdType, Int> {
        val json = v.typed<LegacyValue.JsonV>(key)?.value ?: return mutableMapOf()
        return try {
            val pairs: List<Pair<String, Int>> = when (val root = json.parseJson()) {
                is JsonArray -> root.chunked(2).map { (k, n) -> k.jsonPrimitive.content to (n.jsonPrimitive.intOrNull ?: error("cursor")) }
                is JsonObject -> root.map { (k, n) -> k to (n.jsonPrimitive.intOrNull ?: error("cursor")) }
                else -> error("cursors")
            }
            pairs.map { (k, n) -> IdType.fromString(k.lowercase()) to n }
                .sortedBy { it.first.toString() }.toMap(LinkedHashMap())
        } catch (e: Exception) {
            skip(report, "malformed studyPadCursors JSON", 1)
            mutableMapOf()
        }
    }

    // ---- Window, PageManager, HistoryItem --------------------------------------------------------------------

    private fun window(row: LegacyRow, id: IdType, workspaceId: IdType): WorkspaceEntities.Window {
        val v = row.values
        return WorkspaceEntities.Window(
            workspaceId = workspaceId,
            isSynchronized = v.bool("isSynchronized") ?: false,
            isPinMode = v.bool("isPinMode") ?: false,
            isLinksWindow = v.bool("isLinksWindow") ?: false,
            windowLayout = WorkspaceEntities.WindowLayout(v.text("layoutState") ?: "", v.float("layoutWeight") ?: 1.0f),
            id = id,
            orderNumber = v.int("orderNumber") ?: 0,
            targetLinksWindowId = v.id("targetLinksWindowId"),
            syncGroup = v.int("syncGroup") ?: 0,
        )
    }

    /**
     * Port of the page manager projection of `snapshotCurrentStateStrict`: one row per exported window, also for a
     * window without a page manager (all documents empty, Genesis 1:1, category `BIBLE`). The Bible location comes
     * from [repairBibleBookEncoding]; the raw category name, commentary source and the dictionary, general book and map
     * anchor ordinals (which iOS cannot store) come from the page manager fidelity setting when there is one.
     */
    private fun pageManager(row: LegacyRow?, windowId: IdType, fidelity: Fidelity, report: MigrationReport): WorkspaceEntities.PageManager {
        val v = row?.let { repairBibleBookEncoding(it) } ?: emptyMap()
        val fid = fidelity.pageManager[windowId.toString()]
        val local = v.text("currentCategoryName") ?: "bible"
        return WorkspaceEntities.PageManager(
            windowId = windowId,
            biblePage = WorkspaceEntities.BiblePage(
                document = v.text("bibleDocument"),
                verse = WorkspaceEntities.Verse(
                    versification = v.text("bibleVersification") ?: ANDROID_DEFAULT_VERSIFICATION,
                    bibleBook = v.int("bibleBibleBook") ?: ANDROID_DEFAULT_BIBLE_BOOK.toInt(),
                    chapterNo = v.int("bibleChapterNo") ?: ANDROID_DEFAULT_CHAPTER.toInt(),
                    verseNo = v.int("bibleVerseNo") ?: ANDROID_DEFAULT_VERSE.toInt(),
                ),
            ),
            commentaryPage = WorkspaceEntities.CommentaryPage(
                document = v.text("commentaryDocument"),
                anchorOrdinal = v.int("commentaryAnchorOrdinal"),
                sourceBookAndKey = fid?.commentarySourceBookAndKey,
            ),
            dictionaryPage = WorkspaceEntities.Page(v.text("dictionaryDocument"), v.text("dictionaryKey"), fid?.dictionaryAnchorOrdinal),
            generalBookPage = WorkspaceEntities.Page(v.text("generalBookDocument"), v.text("generalBookKey"), fid?.generalBookAnchorOrdinal),
            mapPage = WorkspaceEntities.Page(v.text("mapDocument"), v.text("mapKey"), fid?.mapAnchorOrdinal),
            currentCategoryName = fid?.rawCurrentCategoryName ?: remoteCurrentCategoryName(local),
            textDisplaySettings = textDisplay(RowTds(v, "textDisplaySettings."), fidelity.pageManagerText[windowId.toString()] ?: TextFidelity()),
            jsState = v.text("jsState"),
        )
    }

    /** Port of `RemoteSyncWorkspaceSnapshotService.remoteCurrentCategoryName(from:)`. */
    private fun remoteCurrentCategoryName(local: String): String = when (local.lowercase()) {
        "bible" -> "BIBLE"
        "commentary" -> "COMMENTARY"
        "dictionary" -> "DICTIONARY"
        "general_book" -> "GENERAL_BOOK"
        "map" -> "MAPS"
        else -> local.uppercase()
    }

    /**
     * Port of `projectWorkspaceHistory`: Android history rows have integer ids, the Swift ones UUIDs. Items of an
     * exported window are numbered in the exporter's order (window id, creation time, document, key, item id) from
     * one above the highest id the `history_alias` fidelity settings already assign; an item that has an alias keeps
     * that id.
     */
    private fun historyItems(
        data: LegacyWorkspaceData,
        windowIds: Set<String>,
        fidelity: Fidelity,
        report: MigrationReport,
    ): List<WorkspaceEntities.HistoryItem> {
        val windowIdByPk = data.windows.mapNotNull { w -> w.id("id")?.let { w.pk to it.toString() } }.toMap()
        class Item(val window: String, val createdAt: Long, val document: String, val key: String, val anchor: Int?, val id: String)
        val items = data.historyItems.mapNotNull { row ->
            val window = row.toOne["window"]?.let { windowIdByPk[it] }
            if (window == null || window !in windowIds) return@mapNotNull null
            val v = row.values
            Item(
                window = window,
                createdAt = v.typed<LegacyValue.DateV>("createdAt")?.epochMillis ?: 0L,
                document = v.text("document") ?: "",
                key = v.text("key") ?: "",
                anchor = v.int("anchorOrdinal"),
                id = v.id("id")?.toString() ?: "",
            )
        }
        skip(report, "history item without an exported window", data.historyItems.size - items.size, "HistoryItem")
        val sorted = items.sortedWith(
            compareBy<Item>({ it.window }, { it.createdAt }, { it.document }, { it.key }, { it.id }),
        )
        var next = (fidelity.historyAliases.values.maxOrNull() ?: 0L) + 1
        // Two legacy rows with one UUID get the same aliased Android id. The Swift exporter would then fail on the primary
        // key; the Room DAO ignores the second insert silently. Either way the staged count would differ from the report, so
        // the later row is skipped here and reported.
        val usedRemoteIds = HashSet<Long>()
        var duplicates = 0
        val result = ArrayList<WorkspaceEntities.HistoryItem>(sorted.size)
        for (item in sorted) {
            val remoteId = fidelity.historyAliases[item.id] ?: next++
            if (!usedRemoteIds.add(remoteId)) { duplicates++; continue }
            result += WorkspaceEntities.HistoryItem(IdType.fromString(item.window), DbDate(item.createdAt), item.document, item.key, item.anchor, remoteId)
        }
        skip(report, "duplicate history item id", duplicates, "HistoryItem")
        return result
    }

    // ---- Global text display settings ------------------------------------------------------------------------

    /**
     * Port of the `GlobalTextDisplaySettings` row of `snapshotCurrentStateStrict`: present when the app-level
     * `global_text_display_settings` setting (the Swift `TextDisplaySettings` as flat JSON) or the global fidelity
     * entry exists; its id is the fidelity entry's or Android's fixed singleton id.
     */
    private fun global(settings: Map<String, String>, fidelity: Fidelity, report: MigrationReport): GlobalTextDisplaySettings? {
        val raw = settings[GLOBAL_TEXT_DISPLAY_KEY]
        val tds: JsonObject? = raw?.let {
            try {
                it.parseJson().jsonObject
            } catch (e: Exception) {
                skip(report, "malformed global_text_display_settings JSON", 1)
                null
            }
        }
        val entry = fidelity.global
        if (tds == null && entry == null) return null
        return GlobalTextDisplaySettings(
            id = entry?.first ?: GlobalTextDisplaySettings.SINGLETON_ID,
            textDisplaySettings = textDisplay(JsonTds(tds ?: JsonObject(emptyMap())), entry?.second ?: TextFidelity()),
        )
    }

    // ---- Text display settings -------------------------------------------------------------------------------

    /** The 37 native Swift text display fields, whatever they are read from; absent or NULL is null. */
    private interface Tds {
        fun int(name: String): Int?
        fun bool(name: String): Boolean?
        fun text(name: String): String?
        fun hideLabels(): List<IdType>?
    }

    private inner class RowTds(val v: Map<String, LegacyValue?>, val prefix: String) : Tds {
        override fun int(name: String) = v.int(prefix + name)
        override fun bool(name: String) = v.bool(prefix + name)
        override fun text(name: String) = v.text(prefix + name)
        override fun hideLabels(): List<IdType>? = v.archiveIds(prefix + "bookmarksHideLabels")
    }

    private class JsonTds(val o: JsonObject) : Tds {
        private fun prim(name: String): JsonPrimitive? = (o[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }
        override fun int(name: String) = prim(name)?.intOrNull
        override fun bool(name: String) = prim(name)?.booleanOrNull
        override fun text(name: String) = prim(name)?.takeIf { it.isString }?.contentOrNull
        override fun hideLabels(): List<IdType>? =
            (o["bookmarksHideLabels"] as? JsonArray)?.map { IdType.fromString(it.jsonPrimitive.content.lowercase()) }
    }

    /**
     * Port of `RemoteSyncWorkspaceTextDisplaySettingsWire.sqliteValues()`: the native Swift fields plus the
     * Android-only [TextFidelity] fields, which fill the columns iOS has no model for.
     */
    private fun textDisplay(t: Tds, f: TextFidelity) = WorkspaceEntities.TextDisplaySettings(
        marginSize = WorkspaceEntities.MarginSize(t.int("marginLeft"), t.int("marginRight"), t.int("maxWidth")),
        colors = WorkspaceEntities.Colors(
            dayTextColor = t.int("dayTextColor"),
            dayBackground = t.int("dayBackground"),
            dayNoise = t.int("dayNoise"),
            nightTextColor = t.int("nightTextColor"),
            nightBackground = t.int("nightBackground"),
            nightNoise = t.int("nightNoise"),
            dayBackgroundImage = f.dayBackgroundImage,
            nightBackgroundImage = f.nightBackgroundImage,
            dayBackgroundImageOpacity = f.dayBackgroundImageOpacity,
            nightBackgroundImageOpacity = f.nightBackgroundImageOpacity,
        ),
        strongsMode = t.int("strongsMode"),
        showMorphology = t.bool("showMorphology"),
        showFootNotes = t.bool("showFootNotes"),
        showFootNotesInline = t.bool("showFootNotesInline"),
        expandXrefs = t.bool("expandXrefs"),
        showXrefs = t.bool("showXrefs"),
        showRedLetters = t.bool("showRedLetters"),
        showSectionTitles = t.bool("showSectionTitles"),
        showVerseNumbers = t.bool("showVerseNumbers"),
        showVersePerLine = t.bool("showVersePerLine"),
        showBookmarks = t.bool("showBookmarks"),
        showMyNotes = t.bool("showMyNotes"),
        justifyText = t.bool("justifyText"),
        hyphenation = t.bool("hyphenation"),
        topMargin = t.int("topMargin"),
        fontSize = t.int("fontSize"),
        fontFamily = t.text("fontFamily"),
        lineSpacing = t.int("lineSpacing"),
        bookmarksHideLabels = t.hideLabels(),
        showPageNumber = t.bool("showPageNumber"),
        infiniteScroll = t.bool("infiniteScroll"),
        nonStrongsWordItalic = t.bool("nonStrongsWordItalic"),
        showMarkAsReadButton = t.bool("showMarkAsReadButton"),
        showTitleScrollButton = t.bool("showTitleScrollButton"),
        showMemorizationIndicators = t.bool("showMemorizationIndicators"),
        autoTrackReading = f.autoTrackReading,
        showAiDocMarkers = t.bool("showAiDocMarkers"),
        pageScrollAmount = t.int("pageScrollAmount"),
        scrollHelperLines = f.scrollHelperLines,
        scrollHelperLineStyle = f.scrollHelperLineStyle,
        showPageButtons = f.showPageButtons,
        showOrdinals = t.bool("showOrdinals"),
        showReadingProgress = f.showReadingProgress,
    )

    // ---- Fidelity settings -----------------------------------------------------------------------------------

    /** Android-only text display fields (`RemoteSyncWorkspaceTextDisplaySettingsFidelity`); all optional. */
    @Serializable
    internal class TextFidelity(
        val autoTrackReading: Boolean? = null,
        val scrollHelperLines: Boolean? = null,
        val scrollHelperLineStyle: Int? = null,
        val showPageButtons: Boolean? = null,
        val showReadingProgress: Boolean? = null,
        val dayBackgroundImage: String? = null,
        val nightBackgroundImage: String? = null,
        val dayBackgroundImageOpacity: Int? = null,
        val nightBackgroundImageOpacity: Int? = null,
    )

    /** `StoredPageManagerEntry` of `RemoteSyncWorkspaceFidelityStore`. */
    @Serializable
    internal class PageManagerFidelity(
        val rawCurrentCategoryName: String,
        val commentarySourceBookAndKey: String? = null,
        val dictionaryAnchorOrdinal: Int? = null,
        val generalBookAnchorOrdinal: Int? = null,
        val mapAnchorOrdinal: Int? = null,
    )

    /** `RemoteSyncCurrentWorkspaceLabelOverrideRow` as the Swift `JSONEncoder` writes it. */
    @Serializable
    private class LabelOverrideJson(
        @SerialName("workspaceID") val workspaceId: String,
        @SerialName("labelID") val labelId: String,
        val overrideMode: Int? = null,
    )

    /** `RemoteSyncWorkspaceFidelityStore.GlobalTextDisplayEntry`. */
    @Serializable
    private class GlobalEntryJson(val id: String, val fidelity: TextFidelity)

    /**
     * The `remote_sync.workspaces.fidelity.*` settings, decoded like `RemoteSyncWorkspaceFidelityStore` and
     * `RemoteSyncWorkspaceRoomFidelityStore` do, keyed by the lower-case owner id. An undecodable entry is skipped
     * and counted in the report.
     */
    internal class Fidelity(
        val workspaceText: Map<String, TextFidelity>,
        val pageManagerText: Map<String, TextFidelity>,
        val pageManager: Map<String, PageManagerFidelity>,
        val labelOverrides: List<WorkspaceEntities.WorkspaceLabelOverride>,
        val global: Pair<IdType, TextFidelity>?,
        val historyAliases: Map<String, Long>,
    ) {
        companion object {
            private val lenient = Json { ignoreUnknownKeys = true }
            private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

            fun read(settings: Map<String, String>, report: MigrationReport, category: LegacyCategory): Fidelity {
                var malformed = 0
                fun <T> perOwner(prefix: String, decode: (String) -> T): Map<String, T> {
                    val result = LinkedHashMap<String, T>()
                    for ((key, value) in settings.entries.sortedBy { it.key }) {
                        if (!key.startsWith("$prefix.")) continue
                        val owner = key.removePrefix("$prefix.")
                        if (!uuidPattern.matches(owner)) { malformed++; continue }
                        try { result[owner.lowercase()] = decode(value) } catch (e: Exception) { malformed++ }
                    }
                    return result
                }
                val base = "remote_sync.workspaces.fidelity"
                val workspaceText = perOwner("$base.workspace_text_display") { lenient.decodeFromString<TextFidelity>(it) }
                val pageManagerText = perOwner("$base.page_manager_text_display") { lenient.decodeFromString<TextFidelity>(it) }
                val pageManager = perOwner("$base.page_manager") { lenient.decodeFromString<PageManagerFidelity>(it) }

                val overrides = mutableListOf<WorkspaceEntities.WorkspaceLabelOverride>()
                for ((key, value) in settings.entries.sortedBy { it.key }) {
                    if (!key.startsWith("$base.label_override.")) continue
                    try {
                        val row = lenient.decodeFromString<LabelOverrideJson>(value)
                        val ws = row.workspaceId.lowercase()
                        val label = row.labelId.lowercase()
                        // The Swift reader accepts a row only when its key is exactly the key built from its own ids.
                        if (key != "$base.label_override.$ws.$label") throw IllegalArgumentException("key mismatch")
                        overrides += WorkspaceEntities.WorkspaceLabelOverride(IdType.fromString(ws), IdType.fromString(label), row.overrideMode)
                    } catch (e: Exception) {
                        malformed++
                    }
                }

                val global = settings["$base.global_text_display"]?.let {
                    try {
                        val entry = lenient.decodeFromString<GlobalEntryJson>(it)
                        IdType.fromString(entry.id.lowercase()) to entry.fidelity
                    } catch (e: Exception) {
                        malformed++
                        null
                    }
                }

                val aliases = LinkedHashMap<String, Long>()
                for ((key, value) in settings) {
                    if (!key.startsWith("$base.history_alias.")) continue
                    val remote = key.removePrefix("$base.history_alias.").toLongOrNull()
                    if (remote == null || !uuidPattern.matches(value)) { malformed++; continue }
                    aliases[value.lowercase()] = remote
                }
                if (malformed > 0) report.skipped(category, "malformed workspace fidelity settings", malformed)
                return Fidelity(workspaceText, pageManagerText, pageManager, overrides, global, aliases)
            }
        }
    }

    private companion object {
        /** Seconds between 1970-01-01 and the Core Data reference date 2001-01-01. */
        const val REFERENCE_DATE_OFFSET_SECONDS = 978_307_200.0
        const val GLOBAL_TEXT_DISPLAY_KEY = "global_text_display_settings"

        fun String.parseJson(): JsonElement = Json.parseToJsonElement(this)

        fun idString(item: Any?): String? = when (item) {
            is LegacyUuid -> item.value
            is String -> item.lowercase()
            else -> null
        }
    }

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

    // Android columns are 32-bit; like SQLite's own integer fetch, an out-of-range value keeps its low 32 bits.
    private fun Map<String, LegacyValue?>.int(key: String): Int? = typed<LegacyValue.IntV>(key)?.value?.toInt()
    private fun Map<String, LegacyValue?>.bool(key: String): Boolean? = typed<LegacyValue.BoolV>(key)?.value
    private fun Map<String, LegacyValue?>.text(key: String): String? = typed<LegacyValue.TextV>(key)?.value
    private fun Map<String, LegacyValue?>.float(key: String): Float? = typed<LegacyValue.RealV>(key)?.value?.toFloat()
    private fun Map<String, LegacyValue?>.id(key: String): IdType? = typed<LegacyValue.UuidV>(key)?.value?.let { IdType.fromString(it) }
    private fun LegacyRow.int(key: String) = values.int(key)
    private fun LegacyRow.id(key: String) = values.id(key)

    /** The members of an archived `NSArray`/`NSSet` at [key]; null when the column is NULL or unusable (counted). */
    private fun Map<String, LegacyValue?>.archiveMembers(key: String): Collection<*>? {
        val content = typed<LegacyValue.ArchiveV>(key)?.value ?: return null
        if (content is Collection<*>) return content
        drop("unexpected archive content for $key")
        return null
    }

    /** Ids of a decoded `NSArray` (kept in order) or `NSSet` (ordered by id for a stable result) of UUIDs. */
    private fun Map<String, LegacyValue?>.archiveIds(key: String): List<IdType>? {
        val members = archiveMembers(key) ?: return null
        val ids = members.mapNotNull { m -> idString(m).also { if (it == null) drop("unexpected archive element in $key") } }
        return (if (members is Set<*>) ids.sorted() else ids).map { IdType.fromString(it) }
    }

    private fun Map<String, LegacyValue?>.idSet(key: String): List<IdType> = archiveIds(key)?.sortedBy { it.toString() }.orEmpty()

    private fun Map<String, LegacyValue?>.stringSet(key: String): List<String> =
        archiveMembers(key)?.mapNotNull { m -> (m as? String).also { if (it == null) drop("unexpected archive element in $key") } }
            ?.sorted().orEmpty()
}
