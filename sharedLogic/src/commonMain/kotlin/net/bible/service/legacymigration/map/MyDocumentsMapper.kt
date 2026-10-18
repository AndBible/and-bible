package net.bible.service.legacymigration.map

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import net.bible.android.database.IdType
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.legacymigration.stage.StagingDatabases

// ---- My Documents ---------------------------------------------------------------------------------------------

/** The legacy rows a [MyDocumentsMapper] needs, from the cloud store. [from] reads them from a [LegacySnapshot]; tests can build one by hand. */
class LegacyMyDocumentData(
    val documents: List<LegacyRow>,
    val pages: List<LegacyRow>,
    val contents: List<LegacyRow>,
    val cacheEntries: List<LegacyRow>,
) {
    companion object {
        fun from(snapshot: LegacySnapshot) = LegacyMyDocumentData(
            documents = snapshot.cloud.rows("MyDocument"),
            pages = snapshot.cloud.rows("MyDocumentPage"),
            contents = snapshot.cloud.rows("MyDocumentPageContent"),
            cacheEntries = snapshot.cloud.rows("AiPageCacheEntry"),
        )
    }
}

/**
 * Migrates My Documents (documents, pages, page content and the AI page cache metadata) from the legacy Swift cloud
 * store into the current `MyDocumentDatabase`.
 *
 * Port of the row derivation of the Swift exporter: `RemoteSyncMyDocumentSnapshotService.buildSnapshot`
 * (which rows are exported: a page needs a document, its content and cache entry need an exported page; one cache entry
 * per page, the first by id), the sorts of `RemoteSyncInitialBackupUploadService.buildMyDocumentInitialBackup`
 * (`myDocumentSort`, `myDocumentPageSort`, ...) and its `insertMyDocumentRow`, `insertMyDocumentPageRow`,
 * `insertMyDocumentPageContentRow` and `insertAiPageCacheEntryRow` column lists. Differences, all deliberate:
 *
 * - **`AiPageCacheEntry` is migrated** (decision: the Swift My Documents export carries it, and the AI document markers
 *   in the Bible view depend on it). The fixtures hold no cache entry, so only hand-built tests exercise it.
 * - **Failures are reported, not fatal.** The Swift exporter silently drops a page without an exported document, orphan
 *   content or cache entries, and aborts on a unique-index conflict or a value that does not fit 32 bits. Here every such
 *   row is skipped and counted in the [MigrationReport]; the rest is still written. A page content type other than
 *   MARKDOWN, HTML or OSIS is stored as MARKDOWN, like Swift's `contentType` getter, and counted.
 * - Rows whose id repeats replace the earlier row (Swift keys rows by id), counted; a second document with an already
 *   used `initials` and a second page with an already used page key of one document are skipped (unique indices).
 * - The remote-sync bookkeeping (`LogEntry`, fingerprints, the accepted baseline) is not migrated (sync stays off).
 * - Rows are inserted with SQL, in one writer transaction, with text bound as bytes so an embedded NUL survives.
 *
 * The staging database is only created when there is a row to write.
 */
class MyDocumentsMapper : CategoryMapper {
    override val category = LegacyCategory.MY_DOCUMENTS

    override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) =
        map(LegacyMyDocumentData.from(snapshot), staging, report)

    /** Maps [data] into [staging]; [map] with a snapshot reads the data from the stores and calls this. */
    suspend fun map(data: LegacyMyDocumentData, staging: StagingDatabases, report: MigrationReport) {
        val stage = build(data, report)
        val sections = listOf(
            DOCUMENT to stage.documents, PAGE to stage.pages, CONTENT to stage.contents, CACHE to stage.cacheEntries,
        )
        if (sections.all { it.second.isEmpty() }) return
        staging.myDocuments.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                for ((def, rows) in sections) for (cells in rows) usePrepared(def.insertSql) { it.bindCells(cells); it.step() }
            }
        }
        for ((def, rows) in sections) report.written(category, def.table, rows.size)
    }

    /** The staged rows of a run, in insertion order. */
    class Stage internal constructor(
        internal val documents: List<List<Any?>>,
        internal val pages: List<List<Any?>>,
        internal val contents: List<List<Any?>>,
        internal val cacheEntries: List<List<Any?>>,
    )

    /** Builds the target rows without touching any database. Skipped rows and values are noted in [report]. */
    fun build(data: LegacyMyDocumentData, report: MigrationReport): Stage {
        val drops = Drops()
        try {
            return buildStage(data, drops)
        } finally {
            drops.flushTo(report, category)
        }
    }

    private fun buildStage(data: LegacyMyDocumentData, drops: Drops): Stage {
        // Swift: documents sorted (orderNumber, name, id), keyed by id so a later row replaces an earlier one.
        val documentsById = LinkedHashMap<String, List<Any?>>()
        for (row in data.documents.sortedWith(compareBy<LegacyRow>({ it.long("orderNumber") }, { it.text("name") }, { it.uuidString("id") }))) {
            val cells = DOCUMENT.fromLegacy(row, drops) ?: continue
            if (documentsById.put((cells[0] as ByteArray).hexKey(), cells) != null) drops.add("duplicate My Document id")
        }
        val usedInitials = HashSet<String>()
        val documents = documentsById.values.filter { cells ->
            (usedInitials.add((cells[3] as Txt).bytes.decodeToString())).also { if (!it) drops.add("My Document with an initials value already in use") }
        }
        val documentIds = documents.map { (it[0] as ByteArray).hexKey() }.toSet()
        val documentIdByPk = data.documents.associate { it.pk to (it.values["id"] as? LegacyValue.UuidV)?.value?.let { id -> IdType.fromString(id).toByteArray() } }

        // Swift: pages sorted (document id, orderNumber, title, id); a page needs an exported document.
        val pagesById = LinkedHashMap<String, List<Any?>>()
        val pageSort = compareBy<LegacyRow>(
            { it.toOne["document"]?.let { pk -> documentIdByPk[pk] }?.let { id -> IdType.fromByteArray(id).toString().uppercase() } ?: "" },
            { it.long("orderNumber") }, { it.text("title") }, { it.uuidString("id") },
        )
        for (row in data.pages.sortedWith(pageSort)) {
            val documentId = row.toOne["document"]?.let { pk -> documentIdByPk[pk] }
            if (documentId == null || documentId.hexKey() !in documentIds) { drops.add("My Document page without an exported document"); continue }
            val rawType = row.values["contentTypeRawValue"] as? LegacyValue.TextV
            val contentType = rawType?.value?.takeIf { it in CONTENT_TYPES } ?: "MARKDOWN"
            if (rawType != null && rawType.value !in CONTENT_TYPES) drops.add("My Document page with an unknown content type stored as MARKDOWN")
            val cells = PAGE.fromLegacy(row, drops, mapOf("documentId" to documentId, "contentType" to Txt(contentType))) ?: continue
            if (pagesById.put((cells[0] as ByteArray).hexKey(), cells) != null) drops.add("duplicate My Document page id")
        }
        val usedKeys = HashSet<String>()
        val pages = pagesById.values.filter { cells ->
            usedKeys.add((cells[1] as ByteArray).hexKey() + "\u0000" + (cells[3] as Txt).bytes.decodeToString())
                .also { if (!it) drops.add("duplicate My Document page key") }
        }
        val pageIds = pages.map { (it[0] as ByteArray).hexKey() }.toSet()

        // Swift: contents sorted by page id, keyed by page id; only for exported pages.
        val contentsByPage = LinkedHashMap<String, List<Any?>>()
        for (row in data.contents.sortedBy { it.uuidString("pageId") }) {
            val cells = CONTENT.fromLegacy(row, drops) ?: continue
            if ((cells[0] as ByteArray).hexKey() !in pageIds) { drops.add("My Document page content without an exported page"); continue }
            if (contentsByPage.put((cells[0] as ByteArray).hexKey(), cells) != null) drops.add("duplicate My Document page content")
        }

        // Swift: cache entries sorted (page id, id); only for exported pages, the first one per page.
        val cacheByPage = LinkedHashMap<String, List<Any?>>()
        for (row in data.cacheEntries.sortedWith(compareBy<LegacyRow>({ it.uuidString("pageId") }, { it.uuidString("id") }))) {
            val cells = CACHE.fromLegacy(row, drops) ?: continue
            val key = (cells[0] as ByteArray).hexKey()
            if (key !in pageIds) { drops.add("AI page cache entry of a page that was not exported"); continue }
            if (key in cacheByPage) { drops.add("duplicate AI page cache entry for one page"); continue }
            cacheByPage[key] = cells
        }
        return Stage(documents, pages, contentsByPage.values.toList(), cacheByPage.values.toList())
    }

    companion object {
        private val CONTENT_TYPES = setOf("MARKDOWN", "HTML", "OSIS")

        private val DOCUMENT = TableDef(
            "MyDocument", "My Document",
            listOf(
                Col("id", Kind.ID), Col("name", Kind.TEXT), Col("description", Kind.TEXT, "documentDescription", nullable = true),
                Col("initials", Kind.TEXT), Col("orderNumber", Kind.INT32), Col("createdAt", Kind.DATE), Col("updatedAt", Kind.DATE),
                Col("sourcePromptId", Kind.ID, nullable = true),
            ),
        )
        private val PAGE = TableDef(
            "MyDocumentPage", "My Document page",
            listOf(
                Col("id", Kind.ID), Col("documentId", Kind.ID, source = null), Col("title", Kind.TEXT), Col("pageKey", Kind.TEXT),
                Col("contentType", Kind.TEXT, source = null), Col("orderNumber", Kind.INT32), Col("createdAt", Kind.DATE),
                Col("updatedAt", Kind.DATE), Col("sourcePromptId", Kind.ID, nullable = true), Col("languageCode", Kind.TEXT, nullable = true),
            ),
        )
        private val CONTENT = TableDef(
            "MyDocumentPageContent", "My Document page content",
            listOf(Col("pageId", Kind.ID), Col("content", Kind.TEXT)),
        )
        private val CACHE = TableDef(
            "AiPageCacheEntry", "AI page cache entry",
            listOf(
                Col("pageId", Kind.ID), Col("sourcePromptId", Kind.ID), Col("sourceContext", Kind.TEXT, nullable = true),
                Col("kjvOrdinalStart", Kind.INT32, nullable = true), Col("kjvOrdinalEnd", Kind.INT32, nullable = true),
                Col("contextHash", Kind.TEXT, nullable = true), Col("usedWriteTools", Kind.BOOL),
                Col("sourceModelName", Kind.TEXT, nullable = true), Col("sourceBookInitials", Kind.TEXT, nullable = true),
                Col("sourceBookKey", Kind.TEXT, nullable = true),
            ),
        )

        /** The INSERT statements, exposed for the test that compares their column lists with the staged tables. */
        internal val INSERT_STATEMENTS: List<String> = listOf(DOCUMENT, PAGE, CONTENT, CACHE).map { it.insertSql }
    }
}
