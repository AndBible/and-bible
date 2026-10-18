package net.bible.service.legacymigration.map

import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.bible.service.db.openSqlite
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyStoreReader
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.testsupport.ApprovedRow
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.JvmLegacyDatabaseFactory
import net.bible.service.legacymigration.testsupport.RowSide
import net.bible.service.legacymigration.testsupport.allFixtures
import net.bible.service.legacymigration.testsupport.compareWithOracleDetailed
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.shareddata.fixtures.createFromExport
import okio.Path
import okio.Path.Companion.toOkioPath

class MyDocumentsAndAiMapperTest {
    private fun snapshot(fx: FixtureDir) = LegacySnapshot(
        LegacyStoreReader(File(fx.root.toFile(), "AndBible.store").path),
        LegacyStoreReader(File(fx.root.toFile(), "LocalStore.store").path),
    )

    private inline fun <T> withStaging(block: (StagingDatabases) -> T): T {
        val dir = Files.createTempDirectory("staging-").toFile()
        val staging = StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory())
        try {
            return block(staging)
        } finally {
            staging.close()
            dir.deleteRecursively()
        }
    }

    /** Staging databases of the hand-built cases, closed and deleted after each test. */
    private val leaked = mutableListOf<Pair<StagingDatabases, File>>()

    @AfterTest
    fun closeHandBuiltStagings() {
        for ((staging, dir) in leaked) {
            try { staging.close() } finally { dir.deleteRecursively() }
        }
        leaked.clear()
    }

    private fun label(fx: FixtureDir) = "${fx.app}/${fx.ios}/${fx.scenario}"
    private fun skipped(report: MigrationReport) = report.entries.filterIsInstance<ReportEntry.Skipped>()
    private fun reasons(report: MigrationReport) = skipped(report).map { it.reason }

    // ---- Reading rows back ------------------------------------------------------------------------------------

    private fun cell(st: SQLiteStatement, i: Int): String = when (st.getColumnType(i)) {
        SQLITE_DATA_NULL -> "null"
        SQLITE_DATA_INTEGER -> "i:${st.getLong(i)}"
        SQLITE_DATA_FLOAT -> "f:${st.getDouble(i)}"
        SQLITE_DATA_BLOB -> "b:" + st.getBlob(i).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        else -> "t:" + st.getBlob(i).decodeToString()
    }

    private fun rows(db: RoomDatabase, sql: String, columns: Int): List<List<String>> = runBlocking {
        db.useReaderConnection { c ->
            c.usePrepared(sql) { st -> buildList { while (st.step()) add((0 until columns).map { cell(st, it) }) } }
        }
    }

    private fun fileRows(file: File, sql: String, columns: Int): List<List<String>> {
        val c = openSqlite(file.path, readOnly = true)
        try {
            val st = c.prepare(sql)
            try {
                return buildList { while (st.step()) add((0 until columns).map { cell(st, it) }) }
            } finally {
                st.close()
            }
        } finally {
            c.close()
        }
    }

    private fun hex(uuid: String) = uuid.replace("-", "")
    private fun blob(uuid: String) = "b:" + hex(uuid)
    private fun uuid(n: Int) = "18000000-0000-0000-0000-%012d".format(n)

    // ======================================================================================================
    // My Documents
    // ======================================================================================================

    private fun mapDocs(fx: FixtureDir) = runBlocking {
        withStaging { staging ->
            val report = MigrationReport()
            snapshot(fx).use { MyDocumentsMapper().map(it, staging, report) }
            compareWithOracleDetailed(fx, LegacyCategory.MY_DOCUMENTS, staging) to report
        }
    }

    @Test
    fun everyFixtureMatchesItsMyDocumentsOracle() {
        val fixtures = allFixtures()
        assertEquals(24, fixtures.size)
        var documentRows = 0
        var pageRows = 0
        var contentRows = 0
        for (fx in fixtures) {
            val (result, report) = mapDocs(fx)
            assertEquals(emptyList(), result.differences, label(fx))
            assertEquals(emptyList(), skipped(report), label(fx))
            documentRows += result.comparedRows["MyDocument"] ?: 0
            pageRows += result.comparedRows["MyDocumentPage"] ?: 0
            contentRows += result.comparedRows["MyDocumentPageContent"] ?: 0
        }
        // Not vacuous: the rich fixtures carry a document with a page and its content.
        assertTrue(documentRows >= 6 && pageRows >= 6 && contentRows >= 6, "documents $documentRows pages $pageRows contents $contentRows")
    }

    private fun t(s: String) = LegacyValue.TextV(s)
    private fun i(n: Long) = LegacyValue.IntV(n)
    private fun b(v: Boolean) = LegacyValue.BoolV(v)
    private fun u(n: Int) = LegacyValue.UuidV(uuid(n))
    private fun d(ms: Long) = LegacyValue.DateV(ms)
    private fun r(v: Double) = LegacyValue.RealV(v)

    private fun row(pk: Long, toOne: Map<String, Long?> = emptyMap(), vararg values: Pair<String, LegacyValue?>) = LegacyRow(pk, mapOf(*values), toOne)

    private fun doc(pk: Long, id: Int, initials: String, order: Long = pk, name: String = "Doc $pk") = row(
        pk, emptyMap(), "id" to u(id), "name" to t(name), "documentDescription" to t("description $pk"), "initials" to t(initials),
        "orderNumber" to i(order), "createdAt" to d(1_700_000_000_000 + pk), "updatedAt" to d(1_800_000_000_000 + pk), "sourcePromptId" to u(900 + pk.toInt()),
    )

    private fun page(pk: Long, id: Int, docPk: Long?, key: String, contentType: String = "HTML", order: Long = pk, title: String = "Page $pk") = row(
        pk, mapOf("document" to docPk), "id" to u(id), "title" to t(title), "pageKey" to t(key), "contentTypeRawValue" to t(contentType),
        "orderNumber" to i(order), "createdAt" to d(1_710_000_000_000 + pk), "updatedAt" to d(1_810_000_000_000 + pk), "sourcePromptId" to u(800 + pk.toInt()),
        "languageCode" to t("fi"),
    )

    private fun content(pk: Long, pageId: Int, text: String) = row(pk, emptyMap(), "pageId" to u(pageId), "content" to t(text))

    private fun cache(pk: Long, id: Int, pageId: Int, used: Boolean, startOrdinal: Long? = pk * 10) = row(
        pk, emptyMap(), "id" to u(id), "pageId" to u(pageId), "sourcePromptId" to u(700 + pk.toInt()), "sourceContext" to t("context $pk"),
        "kjvOrdinalStart" to startOrdinal?.let { i(it) }, "kjvOrdinalEnd" to startOrdinal?.let { i(it + 3) }, "contextHash" to t("hash$pk"),
        "usedWriteTools" to b(used), "sourceModelName" to t("model$pk"), "sourceBookInitials" to t("KJV$pk"), "sourceBookKey" to t("key$pk"),
    )

    private fun mapDocData(data: LegacyMyDocumentData): Pair<MigrationReport, (String, Int) -> List<List<String>>> {
        val report = MigrationReport()
        val dir = Files.createTempDirectory("staging-").toFile()
        val staging = StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory())
        leaked += staging to dir
        runBlocking { MyDocumentsMapper().map(data, staging, report) }
        return report to { sql, n -> if (staging.openedDatabase("mydocuments.sqlite3") == null) emptyList() else rows(staging.myDocuments, sql, n) }
    }

    @Test
    fun myDocumentColumnsComeFromTheirOwnSourceFields() {
        val data = LegacyMyDocumentData(
            documents = listOf(doc(1, 1, "MyDoc_a"), doc(2, 2, "MyDoc_b", order = 5)),
            pages = listOf(
                page(1, 11, 1, "k1", "HTML"), page(2, 12, 1, "k2", "OSIS"), page(3, 13, 2, "k3", "MARKDOWN"), page(4, 14, 2, "k4", "weird-type"),
            ),
            contents = listOf(content(1, 11, "c11"), content(2, 12, "c12\u0000nul"), content(3, 13, "c13")),
            cacheEntries = listOf(cache(1, 21, 11, used = true), cache(2, 22, 12, used = false)),
        )
        val (report, read) = mapDocData(data)
        // Only the unknown content type is noted (it is stored as MARKDOWN); nothing else is dropped.
        assertEquals(listOf("My Document page with an unknown content type stored as MARKDOWN" to 1), skipped(report).map { it.reason to it.rows })
        val docs = read("SELECT id, name, description, initials, orderNumber, createdAt, updatedAt, sourcePromptId FROM MyDocument ORDER BY orderNumber", 8)
        assertEquals(
            listOf(
                listOf(blob(uuid(1)), "t:Doc 1", "t:description 1", "t:MyDoc_a", "i:1", "i:1700000000001", "i:1800000000001", blob(uuid(901))),
                listOf(blob(uuid(2)), "t:Doc 2", "t:description 2", "t:MyDoc_b", "i:5", "i:1700000000002", "i:1800000000002", blob(uuid(902))),
            ),
            docs,
        )
        val pages = read("SELECT id, documentId, title, pageKey, contentType, orderNumber, createdAt, updatedAt, sourcePromptId, languageCode FROM MyDocumentPage ORDER BY orderNumber", 10)
        assertEquals(
            listOf(
                listOf(blob(uuid(11)), blob(uuid(1)), "t:Page 1", "t:k1", "t:HTML", "i:1", "i:1710000000001", "i:1810000000001", blob(uuid(801)), "t:fi"),
                listOf(blob(uuid(12)), blob(uuid(1)), "t:Page 2", "t:k2", "t:OSIS", "i:2", "i:1710000000002", "i:1810000000002", blob(uuid(802)), "t:fi"),
                listOf(blob(uuid(13)), blob(uuid(2)), "t:Page 3", "t:k3", "t:MARKDOWN", "i:3", "i:1710000000003", "i:1810000000003", blob(uuid(803)), "t:fi"),
                // Swift's `contentType` getter falls back to markdown for an unknown raw value.
                listOf(blob(uuid(14)), blob(uuid(2)), "t:Page 4", "t:k4", "t:MARKDOWN", "i:4", "i:1710000000004", "i:1810000000004", blob(uuid(804)), "t:fi"),
            ),
            pages,
        )
        assertEquals(
            listOf(listOf(blob(uuid(11)), "t:c11"), listOf(blob(uuid(12)), "t:c12\u0000nul"), listOf(blob(uuid(13)), "t:c13")),
            read("SELECT pageId, content FROM MyDocumentPageContent ORDER BY pageId", 2),
        )
        // usedWriteTools differs per row: a swapped or constant boolean would show here.
        assertEquals(
            listOf(
                listOf(blob(uuid(11)), blob(uuid(701)), "t:context 1", "i:10", "i:13", "t:hash1", "i:1", "t:model1", "t:KJV1", "t:key1"),
                listOf(blob(uuid(12)), blob(uuid(702)), "t:context 2", "i:20", "i:23", "t:hash2", "i:0", "t:model2", "t:KJV2", "t:key2"),
            ),
            read(
                "SELECT pageId, sourcePromptId, sourceContext, kjvOrdinalStart, kjvOrdinalEnd, contextHash, usedWriteTools, sourceModelName, sourceBookInitials, sourceBookKey FROM AiPageCacheEntry ORDER BY pageId",
                10,
            ),
        )
    }

    /**
     * Decision (c): `AiPageCacheEntry` belongs to My Documents in the Swift exporter (`buildMyDocumentInitialBackup` writes
     * it, one row per page), so it is migrated, not skipped. The fixtures carry no cache entry, so this hand-built case
     * pins the Swift rules: only entries of an exported page, the first by id per page, the rest counted.
     */
    @Test
    fun aiPageCacheEntriesAreKeptForExportedPagesOnly() {
        val data = LegacyMyDocumentData(
            documents = listOf(doc(1, 1, "MyDoc_a")),
            pages = listOf(page(1, 11, 1, "k1"), page(2, 12, 1, "k2"), page(3, 13, null, "k3")),
            contents = emptyList(),
            cacheEntries = listOf(
                cache(1, 22, 11, used = true), cache(2, 21, 11, used = false), // same page: id 21 sorts first and wins
                cache(3, 23, 13, used = true), // page without a document is not exported
                cache(4, 24, 99, used = true), // page that does not exist
            ),
        )
        val (report, read) = mapDocData(data)
        assertEquals(
            listOf(listOf(blob(uuid(11)), blob(uuid(702)), "i:0")),
            read("SELECT pageId, sourcePromptId, usedWriteTools FROM AiPageCacheEntry", 3),
        )
        val reasons = reasons(report)
        assertTrue(reasons.contains("AI page cache entry of a page that was not exported"), reasons.toString())
        assertTrue(reasons.contains("duplicate AI page cache entry for one page"), reasons.toString())
        assertEquals(2, skipped(report).first { it.reason == "AI page cache entry of a page that was not exported" }.rows)
    }

    @Test
    fun myDocumentRowsOutsideTheExportedGraphAreCountedNotLost() {
        val data = LegacyMyDocumentData(
            documents = listOf(
                doc(1, 1, "MyDoc_a"),
                doc(2, 1, "MyDoc_a2", name = "replaced by pk 3"), // same id as pk 1: the later row replaces it (Swift keys by id)
                doc(3, 1, "MyDoc_a3", name = "winner"),
                doc(4, 4, "MyDoc_a3"), // initials already used (unique index)
                doc(5, 5, "MyDoc_big", order = 3_000_000_000L), // does not fit Android's 32-bit column
            ),
            pages = listOf(
                page(1, 11, 3, "k"), page(2, 12, 3, "k"), // duplicate (document, pageKey)
                page(3, 13, 4, "z"), // page of a skipped document
                page(4, 14, null, "y"), // no document at all
                page(5, 15, 3, "big", order = -3_000_000_000L),
            ),
            contents = listOf(content(1, 11, "kept"), content(2, 77, "orphan content"), content(3, 11, "replaces the first")),
            cacheEntries = emptyList(),
        )
        val (report, read) = mapDocData(data)
        assertEquals(listOf(listOf(blob(uuid(1)), "t:winner")), read("SELECT id, name FROM MyDocument", 2))
        assertEquals(listOf(listOf(blob(uuid(11)))), read("SELECT id FROM MyDocumentPage", 1))
        assertEquals(listOf(listOf(blob(uuid(11)), "t:replaces the first")), read("SELECT pageId, content FROM MyDocumentPageContent", 2))
        val byReason = skipped(report).associate { it.reason to it.rows }
        assertEquals(2, byReason["duplicate My Document id"], byReason.toString())
        assertEquals(1, byReason["My Document with an initials value already in use"], byReason.toString())
        assertEquals(1, byReason["My Document value out of 32-bit range"], byReason.toString())
        assertEquals(1, byReason["duplicate My Document page key"], byReason.toString())
        assertEquals(2, byReason["My Document page without an exported document"], byReason.toString())
        assertEquals(1, byReason["My Document page value out of 32-bit range"], byReason.toString())
        assertEquals(1, byReason["My Document page content without an exported page"], byReason.toString())
        assertEquals(1, byReason["duplicate My Document page content"], byReason.toString())
    }

    @Test
    fun hugeRightToLeftPageContentIsByteExact() {
        val unit = "\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05DC\u05DD \u0645\u0631\u062D\u0628\u0627 \u0628\u0627\u0644\u0639\u0627\u0644\u0645 \u202Eabc\u202C \u200F\u0000 end\n"
        val repeats = 1_100_000 / unit.encodeToByteArray().size + 1
        val text = unit.repeat(repeats)
        assertTrue(text.encodeToByteArray().size >= 1_048_576)
        val data = LegacyMyDocumentData(
            documents = listOf(doc(1, 1, "MyDoc_a")), pages = listOf(page(1, 11, 1, "k1")),
            contents = listOf(content(1, 11, text)), cacheEntries = emptyList(),
        )
        val (report, read) = mapDocData(data)
        assertEquals(emptyList(), skipped(report))
        assertEquals(listOf(listOf("i:${text.encodeToByteArray().size}")), read("SELECT length(CAST(content AS BLOB)) FROM MyDocumentPageContent", 1))
        assertTrue(read("SELECT content FROM MyDocumentPageContent", 1).single().single() == "t:$text", "content differs")
    }

    @Test
    fun myDocumentsStagingIsOnlyCreatedWhenThereIsAnythingToWrite() {
        val (_, read) = mapDocData(LegacyMyDocumentData(emptyList(), emptyList(), emptyList(), emptyList()))
        assertEquals(emptyList(), read("SELECT id FROM MyDocument", 1))
    }

    // ======================================================================================================
    // AI settings
    // ======================================================================================================

    private fun mapAi(fx: FixtureDir, preserved: Path?, approvedRows: Set<ApprovedRow> = emptySet()) = runBlocking {
        withStaging { staging ->
            val report = MigrationReport()
            snapshot(fx).use { AiSettingsMapper(preserved).map(it, staging, report) }
            compareWithOracleDetailed(fx, LegacyCategory.AI_SETTINGS, staging, approvedRows = approvedRows) to report
        }
    }

    private fun preservedPath(fx: FixtureDir): Path = File(fx.root.toFile(), "PreservedDatabases/ai_settings.sqlite3").toOkioPath()

    @Test
    fun fixturesWithoutAPreservedFileMatchTheOracleAiDatabase() {
        var withRows = 0
        val compared = HashMap<String, Int>()
        for (fx in allFixtures().filter { it.scenario != "preserved-ai" }) {
            val (result, report) = mapAi(fx, null)
            assertEquals(emptyList(), result.differences, label(fx))
            assertEquals(emptyList(), skipped(report), label(fx))
            if ((result.comparedRows["LlmProviderConfig"] ?: 0) > 0) withRows++
            result.comparedRows.forEach { (table, n) -> compared.merge(table, n, Int::plus) }
        }
        assertTrue(withRows >= 6, "fixtures with AI rows: $withRows")
        for (table in listOf("LlmProviderConfig", "LlmConfiguredModel", "AgentPrompt", "GlobalAiSettings", "LlmUsageRecord", "PromptCategory", "BuiltinPromptOverride")) {
            assertTrue((compared[table] ?: 0) >= 6, "no compared rows in $table: $compared")
        }
    }

    private val androidOnlyProvider = ApprovedRow(
        "LlmProviderConfig", "id=" + blob("a2000000-0000-0000-0000-0000000000aa"), RowSide.ONLY_IN_STAGED,
        "U11: the preserved Android file's own provider is added; the Swift oracle is built from the Swift rows only",
    )

    @Test
    fun preservedAiFixturesAreTheU11UnionWithTheSwiftRowWinningTheGlobalSettings() {
        val fixtures = allFixtures().filter { it.scenario == "preserved-ai" }
        assertEquals(6, fixtures.size)
        for (fx in fixtures) {
            val preserved = preservedPath(fx)
            val before = sha256(preserved.toFile())
            val (result, report) = mapAi(fx, preserved, setOf(androidOnlyProvider))
            // Everything but the Android-only provider equals the Swift export, so the Swift global settings won.
            assertEquals(emptyList(), result.differences, label(fx))
            assertEquals(before, sha256(preserved.toFile()), "preserved file must stay untouched ${label(fx)}")
            assertEquals(1, skipped(report).single { it.reason == AiSettingsMapper.supersededReason("GlobalAiSettings") }.rows, label(fx))
            withStaging { staging ->
                runBlocking { snapshot(fx).use { AiSettingsMapper(preserved).map(it, staging, MigrationReport()) } }
                assertEquals(
                    listOf(listOf(blob("a2000000-0000-0000-0000-0000000000aa"), "t:OPENAI", "t:Android-only provider", "null", "null", "i:1")),
                    rows(staging.aiSettings, "SELECT id, providerType, displayName, endpoint, apiFormat, orderNumber FROM LlmProviderConfig WHERE providerType = 'OPENAI'", 6),
                    label(fx),
                )
                // Swift's own values, not the preserved file's (sv-SE / 4321).
                assertEquals(
                    listOf(listOf("t:fi-FI", "i:12345")),
                    rows(staging.aiSettings, "SELECT aiLanguage, commentaryMaxResponseTokens FROM GlobalAiSettings", 2),
                    label(fx),
                )
            }
        }
    }

    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private fun exec(file: File, vararg sql: String) {
        val c = openSqlite(file.path)
        try {
            sql.forEach { c.execSQL(it) }
        } finally {
            c.close()
        }
    }

    private fun x(n: Int) = "x'${hex(uuid(n))}'"

    /** A writable copy of a rich fixture's Swift-built `ai_settings.sqlite3` (v23): every table has a row whose id the Swift store has too. */
    private fun preservedCopyOfRich(): Pair<FixtureDir, File> {
        val fx = fixture("1.0.2", "26", "rich")
        val copy = File(Files.createTempDirectory("preserved-").toFile(), "ai_settings.sqlite3")
        File(fx.root.toFile(), "oracle/ai_settings.sqlite3").copyTo(copy)
        copy.parentFile.deleteOnExit()
        copy.deleteOnExit()
        return fx to copy
    }

    private fun tableRowsById(file: File, table: String, id: Int, columns: Int) =
        fileRows(file, "SELECT * FROM $table WHERE id = ${x(id)}", columns)

    @Test
    fun swiftRowsWinEveryIdCollisionAndPreservedOnlyRowsLandUntouched() {
        val (fx, copy) = preservedCopyOfRich()
        // Collisions: same id as the Swift row in every table that has ids, with values that differ.
        exec(
            copy,
            "UPDATE LlmProviderConfig SET displayName = 'PRESERVED provider', orderNumber = 77",
            "UPDATE LlmConfiguredModel SET modelId = 'PRESERVED-model', orderNumber = 78, inputPricePerMillion = 9.5",
            "UPDATE AgentPrompt SET name = 'PRESERVED prompt', orderNumber = 79",
            "UPDATE GlobalAiSettings SET aiLanguage = 'sv-SE', commentaryMaxResponseTokens = 4321",
            "UPDATE LlmUsageRecord SET inputTokens = 424242",
            "UPDATE PromptCategory SET name = 'PRESERVED category', orderNumber = 80",
            "UPDATE BuiltinPromptOverride SET configuredModelId = NULL",
            // Rows only the preserved file has. The provider carries an API format the Room converter would turn into NULL.
            "INSERT INTO LlmProviderConfig (id, providerType, displayName, endpoint, apiFormat, orderNumber) VALUES (${x(101)}, 'WEIRD_TYPE', 'Preserved only', 'https://example.invalid/p', 'WEIRD_FORMAT', 11)",
            "INSERT INTO LlmConfiguredModel (id, providerConfigId, modelId, orderNumber, inputPricePerMillion, outputPricePerMillion, cacheCreationPricePerMillion, cacheReadPricePerMillion) VALUES (${x(102)}, ${x(101)}, 'pm', 12, 1.25, 2.5, 3.75, 5.0)",
            "INSERT INTO AgentPrompt (id, name, description, promptTemplate, showIn, orderNumber, createdAt, strictContextMatching, permissionMode, allowedTools, deniedTools, configuredModelId, editBeforeRun, noDocumentCreation, maxIterations, autoIncludeDocuments, autoIncludeCommentaries, bibleOnly, isTextTransformation, categoryId) " +
                "VALUES (${x(103)}, 'Preserved only prompt', 'pd', 'template', '[ \"TEXT_SELECTION\", \"NOT_A_CONTEXT\" ]', 13, 1234567890123, 0, 'ALLOW_ALL', '[\"SEARCH_BIBLE\"]', '[\"NOT_A_TOOL\"]', ${x(102)}, 1, 0, 5, 1, 0, 1, 0, ${x(104)})",
            "INSERT INTO PromptCategory (id, name, orderNumber, hidden) VALUES (${x(104)}, 'Preserved only category', 14, 1)",
            "INSERT INTO BuiltinPromptOverride (id, configuredModelId) VALUES (${x(105)}, ${x(102)})",
            "INSERT INTO LlmUsageRecord (id, configuredModelId, deviceId, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, estimatedCostUsd) VALUES (${x(106)}, ${x(102)}, 'devP', 1, 2, 3, 4, 0.5)",
        )
        val before = sha256(copy)
        val onlyInPreserved = listOf(
            "LlmProviderConfig" to 101, "LlmConfiguredModel" to 102, "AgentPrompt" to 103, "PromptCategory" to 104,
            "BuiltinPromptOverride" to 105, "LlmUsageRecord" to 106,
        )
        val approvedRows = onlyInPreserved.map { (table, id) -> ApprovedRow(table, "id=" + blob(uuid(id)), RowSide.ONLY_IN_STAGED, "U11 preserved-only row") }.toSet()
        val columnCounts = mapOf(
            "LlmProviderConfig" to 6, "LlmConfiguredModel" to 8, "AgentPrompt" to 20, "PromptCategory" to 4, "BuiltinPromptOverride" to 2, "LlmUsageRecord" to 8,
        )
        val (result, report) = mapAi(fx, copy.toOkioPath(), approvedRows)
        // Every Swift row, in every table and column, is exactly the Swift export: it won each collision.
        assertEquals(emptyList(), result.differences)
        assertEquals(before, sha256(copy), "preserved file must stay untouched")
        for (table in listOf("LlmProviderConfig", "LlmConfiguredModel", "AgentPrompt", "GlobalAiSettings", "LlmUsageRecord", "PromptCategory", "BuiltinPromptOverride")) {
            assertEquals(1, skipped(report).single { it.reason == AiSettingsMapper.supersededReason(table) }.rows, table)
        }
        withStaging { staging ->
            runBlocking { snapshot(fx).use { AiSettingsMapper(copy.toOkioPath()).map(it, staging, MigrationReport()) } }
            for ((table, id) in onlyInPreserved) {
                val expected = tableRowsById(copy, table, id, columnCounts.getValue(table))
                assertEquals(1, expected.size, table)
                assertEquals(expected, rows(staging.aiSettings, "SELECT * FROM $table WHERE id = ${x(id)}", columnCounts.getValue(table)), table)
            }
            // The raw values the Room converters would have rewritten (unknown enum names, non-canonical JSON) are verbatim.
            assertEquals(
                listOf(listOf("t:WEIRD_FORMAT")),
                rows(staging.aiSettings, "SELECT apiFormat FROM LlmProviderConfig WHERE id = ${x(101)}", 1),
            )
            assertEquals(
                listOf(listOf("t:[ \"TEXT_SELECTION\", \"NOT_A_CONTEXT\" ]", "t:[\"NOT_A_TOOL\"]")),
                rows(staging.aiSettings, "SELECT showIn, deniedTools FROM AgentPrompt WHERE id = ${x(103)}", 2),
            )
            // Collision rows are the Swift values, not the preserved ones.
            assertEquals(listOf(listOf("t:fi-FI", "i:12345")), rows(staging.aiSettings, "SELECT aiLanguage, commentaryMaxResponseTokens FROM GlobalAiSettings", 2))
            assertTrue(rows(staging.aiSettings, "SELECT 1 FROM LlmProviderConfig WHERE displayName GLOB 'PRESERVED*'", 1).isEmpty())
            assertTrue(rows(staging.aiSettings, "SELECT 1 FROM AgentPrompt WHERE name GLOB 'PRESERVED*'", 1).isEmpty())
            assertTrue(rows(staging.aiSettings, "SELECT 1 FROM LlmUsageRecord WHERE inputTokens = 424242", 1).isEmpty())
        }
    }

    @Test
    fun preservedModelThatDuplicatesASwiftModelIsSkippedAndItsReferencesFollowTheSwiftModel() {
        val (fx, copy) = preservedCopyOfRich()
        val swiftModelId = fileRows(copy, "SELECT id FROM LlmConfiguredModel", 1).single().single()
        // Re-id every preserved row that refers to the model, so nothing collides by id; only the (provider, modelId) pair does.
        exec(
            copy,
            "PRAGMA foreign_keys = OFF",
            "UPDATE LlmConfiguredModel SET id = ${x(201)}",
            "UPDATE AgentPrompt SET id = ${x(202)}, configuredModelId = ${x(201)}",
            "UPDATE BuiltinPromptOverride SET id = ${x(203)}, configuredModelId = ${x(201)}",
            "UPDATE LlmUsageRecord SET id = ${x(204)}, configuredModelId = ${x(201)}, deviceId = 'other-device'",
        )
        val approvedRows = listOf("AgentPrompt" to 202, "BuiltinPromptOverride" to 203, "LlmUsageRecord" to 204)
            .map { (table, id) -> ApprovedRow(table, "id=" + blob(uuid(id)), RowSide.ONLY_IN_STAGED, "U11 preserved-only row") }.toSet()
        val (result, report) = mapAi(fx, copy.toOkioPath(), approvedRows)
        assertEquals(emptyList(), result.differences)
        assertEquals(1, skipped(report).single { it.reason == AiSettingsMapper.MODEL_CONFLICT_REASON }.rows)
        withStaging { staging ->
            runBlocking { snapshot(fx).use { AiSettingsMapper(copy.toOkioPath()).map(it, staging, MigrationReport()) } }
            assertEquals(1, rows(staging.aiSettings, "SELECT id FROM LlmConfiguredModel", 1).size)
            for (sql in listOf(
                "SELECT configuredModelId FROM AgentPrompt WHERE id = ${x(202)}",
                "SELECT configuredModelId FROM BuiltinPromptOverride WHERE id = ${x(203)}",
                "SELECT configuredModelId FROM LlmUsageRecord WHERE id = ${x(204)}",
            )) {
                assertEquals(listOf(listOf(swiftModelId)), rows(staging.aiSettings, sql, 1), sql)
            }
        }
    }

    @Test
    fun preservedFileOfAnUnreadableVersionOrMissingIsReportedAndIgnored() {
        for (version in listOf(24, 0)) {
            val (fx, copy) = preservedCopyOfRich()
            exec(copy, "PRAGMA user_version = $version")
            // The copy equals the Swift rows (same ids), so add a distinct row that would show if it were read.
            exec(copy, "INSERT INTO PromptCategory (id, name, orderNumber, hidden) VALUES (${x(301)}, 'must not appear', 1, 0)")
            val (result, report) = mapAi(fx, copy.toOkioPath())
            assertEquals(emptyList(), result.differences, "v$version")
            assertEquals(1, skipped(report).single { it.reason == AiSettingsMapper.unsupportedVersionReason(version) }.rows)
        }
        val (fx, copy) = preservedCopyOfRich()
        val (result2, report2) = mapAi(fx, File(copy.parentFile, "missing.sqlite3").toOkioPath())
        assertEquals(emptyList(), result2.differences)
        assertEquals(1, skipped(report2).single { it.reason == AiSettingsMapper.MISSING_FILE_REASON }.rows)

        // Not a database at all: reported, and no partial counts of a failed read are left in the report.
        val junk = File(copy.parentFile, "junk.sqlite3").also { it.writeText("this is not an sqlite file at all, just text".repeat(50)) }
        val (result3, report3) = mapAi(fx, junk.toOkioPath())
        assertEquals(emptyList(), result3.differences)
        assertEquals(listOf(true), skipped(report3).map { it.reason.startsWith("preserved ai_settings.sqlite3 could not be read") })
    }

    private val aiDb = "net.bible.android.database.AiSettingsDatabase"

    private fun exportedPreserved(version: Int): File {
        val file = File(Files.createTempDirectory("preserved-v$version-").toFile(), "ai_settings.sqlite3")
        file.parentFile.deleteOnExit()
        file.deleteOnExit()
        createFromExport(aiDb, version, file.path)
        return file
    }

    private fun directoryListing(f: File) = f.parentFile.list().orEmpty().sorted()

    /** The Swift app keeps Android files of schema versions up to 22: such a file must be migrated and merged, not ignored. */
    @Test
    fun preservedFileOfVersion22IsMigratedAndMerged() {
        val fx = fixture("1.0.2", "26", "fresh") // the Swift oracle has no AI rows, so staging is exactly the preserved rows
        val file = exportedPreserved(22)
        exec(
            file,
            "INSERT INTO LlmProviderConfig (id, providerType, displayName, endpoint, apiFormat, orderNumber) VALUES (${x(101)}, 'WEIRD_TYPE', 'Preserved only', 'https://example.invalid/p', 'WEIRD_FORMAT', 11)",
            "INSERT INTO LlmConfiguredModel (id, providerConfigId, modelId, orderNumber, inputPricePerMillion, outputPricePerMillion, cacheCreationPricePerMillion, cacheReadPricePerMillion) VALUES (${x(102)}, ${x(101)}, 'pm', 12, 1.25, 2.5, 3.75, 5.0)",
            "INSERT INTO AgentPrompt (id, name, description, promptTemplate, showIn, orderNumber, createdAt, strictContextMatching, permissionMode, allowedTools, deniedTools, configuredModelId, editBeforeRun, noDocumentCreation, maxIterations, autoIncludeDocuments, autoIncludeCommentaries, bibleOnly, isTextTransformation, categoryId) " +
                "VALUES (${x(103)}, 'Preserved prompt', 'pd', 'template', '[\"TEXT_SELECTION\"]', 13, 1234567890123, 0, 'ALLOW_ALL', '[\"SEARCH_BIBLE\"]', NULL, ${x(102)}, 1, 0, 5, 1, 0, 1, 0, ${x(104)})",
            "INSERT INTO PromptCategory (id, name, orderNumber, hidden) VALUES (${x(104)}, 'Preserved category', 14, 1)",
            "INSERT INTO BuiltinPromptOverride (id, configuredModelId) VALUES (${x(105)}, ${x(102)})",
            "INSERT INTO LlmUsageRecord (id, configuredModelId, deviceId, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, estimatedCostUsd) VALUES (${x(106)}, ${x(102)}, 'devP', 1, 2, 3, 4, 0.5)",
            "INSERT INTO GlobalAiSettings (id, agentPermissionMode, permanentlyAllowedTools, permanentlyDeniedTools, aiExcludedDocuments, commentaryMaxResponseTokens, hiddenBuiltInPrompts, maxIterations, commentaryDeselected, defaultModelId, aiLanguage, askModelBeforeRun, aiDisclaimerAccepted, hiddenBuiltInCategories, customAgentSystemPrompt, customTextTransformationSystemPrompt, favoritePrompts, rawLogRetentionDays) " +
                "VALUES (x'a1000000000000000000000000000001', 'DENY_ALL', NULL, NULL, '', 4321, '', 7, '', ${x(102)}, 'sv-SE', 1, 1, '', NULL, NULL, '', NULL)",
        )
        val before = sha256(file)
        val listingBefore = directoryListing(file)
        val tables = mapOf(
            "LlmProviderConfig" to 6, "LlmConfiguredModel" to 8, "AgentPrompt" to 20, "PromptCategory" to 4, "BuiltinPromptOverride" to 2, "LlmUsageRecord" to 8,
        )
        val approved = (listOf(101, 102, 103, 104, 105, 106).map { it }).let { ids ->
            val names = listOf("LlmProviderConfig", "LlmConfiguredModel", "AgentPrompt", "PromptCategory", "BuiltinPromptOverride", "LlmUsageRecord")
            names.zip(ids).map { (t, id) -> ApprovedRow(t, "id=" + blob(uuid(id)), RowSide.ONLY_IN_STAGED, "preserved v22 row") } +
                ApprovedRow("GlobalAiSettings", "id=b:a1000000000000000000000000000001", RowSide.ONLY_IN_STAGED, "preserved v22 row")
        }.toSet()
        val (result, report) = mapAi(fx, file.toOkioPath(), approved)
        assertEquals(emptyList(), result.differences)
        assertEquals(emptyList(), skipped(report))
        assertEquals(before, sha256(file), "source must stay untouched")
        assertEquals(listingBefore, directoryListing(file), "no -shm/-wal/-journal may appear beside the source")
        withStaging { staging ->
            runBlocking { snapshot(fx).use { AiSettingsMapper(file.toOkioPath()).map(it, staging, MigrationReport()) } }
            for ((table, columns) in tables) {
                assertEquals(1, fileRows(file, "SELECT * FROM $table", columns).size, table)
                assertEquals(fileRows(file, "SELECT * FROM $table", columns), rows(staging.aiSettings, "SELECT * FROM $table", columns), table)
            }
            // The v22 global row gets the column added in v23 with its default.
            assertEquals(
                listOf(listOf("t:DENY_ALL", "i:4321", "i:7", blob(uuid(102)), "t:sv-SE", "i:1", "null", "i:0")),
                rows(staging.aiSettings, "SELECT agentPermissionMode, commentaryMaxResponseTokens, maxIterations, defaultModelId, aiLanguage, askModelBeforeRun, rawLogRetentionDays, autoHideAgentLogOnCompletion FROM GlobalAiSettings", 8),
            )
        }
    }

    /** A file from before LlmConfiguredModel existed (v8): the real migration chain rewrites it, the survivors land in the v23 schema. */
    @Test
    fun preservedFileOfVersion8IsMigratedThroughTheWholeChain() {
        val fx = fixture("1.0.2", "26", "fresh")
        val file = exportedPreserved(8)
        exec(
            file,
            "INSERT INTO LlmProviderConfig (id, providerType, displayName, endpoint, apiFormat, defaultModel, isDefault, orderNumber, customInputPrice, customOutputPrice) VALUES (${x(111)}, 'CUSTOM', 'Old provider', 'https://old.invalid', 'OPENAI', 'gpt-old', 1, 4, 1.5, 2.5)",
            "INSERT INTO AgentPrompt (id, name, description, promptTemplate, showIn, orderNumber, createdAt, strictContextMatching, permissionMode, allowedTools, deniedTools, modelOverride, providerConfigId, editBeforeRun, noDocumentCreation, maxIterations) " +
                "VALUES (${x(112)}, 'Old prompt', NULL, 'old template', '[\"VERSE_SELECTION\"]', 3, 99, 0, NULL, NULL, NULL, 'gpt-old', ${x(111)}, 1, 1, 6)",
            "INSERT INTO GlobalAiSettings (id, agentPermissionMode, permanentlyAllowedTools, permanentlyDeniedTools, aiExcludedDocuments, commentaryMaxResponseTokens, hiddenBuiltInPrompts, maxIterations, commentaryDeselected) " +
                "VALUES (x'a1000000000000000000000000000001', NULL, NULL, NULL, '[\"KJV\"]', 4000, '', 7, '[\"MHCC\"]')",
            "INSERT INTO LlmUsageRecord (id, providerConfigId, deviceId, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens, estimatedCostUsd) VALUES (${x(113)}, ${x(111)}, 'dev', 1, 2, 3, 4, 0.5)",
        )
        val before = sha256(file)
        val (_, report) = mapAi(fx, file.toOkioPath(), emptySet())
        assertEquals(before, sha256(file))
        assertTrue(skipped(report).none { it.reason.startsWith("preserved ai_settings.sqlite3") }, skipped(report).toString())
        withStaging { staging ->
            runBlocking { snapshot(fx).use { AiSettingsMapper(file.toOkioPath()).map(it, staging, MigrationReport()) } }
            assertEquals(
                listOf(listOf(blob(uuid(111)), "t:CUSTOM", "t:Old provider", "t:https://old.invalid", "t:OPENAI", "i:4")),
                rows(staging.aiSettings, "SELECT id, providerType, displayName, endpoint, apiFormat, orderNumber FROM LlmProviderConfig", 6),
            )
            // The v8->v9 migration drops models and usage and detaches prompts from models.
            assertEquals(
                listOf(listOf(blob(uuid(112)), "t:Old prompt", "t:old template", "i:3", "i:99", "i:0", "null", "i:1", "i:1", "i:6")),
                rows(staging.aiSettings, "SELECT id, name, promptTemplate, orderNumber, createdAt, strictContextMatching, configuredModelId, editBeforeRun, noDocumentCreation, maxIterations FROM AgentPrompt", 10),
            )
            assertEquals(listOf(listOf("t:[\"KJV\"]", "i:7", "t:[\"MHCC\"]", "i:0")), rows(staging.aiSettings, "SELECT aiExcludedDocuments, maxIterations, commentaryDeselected, autoHideAgentLogOnCompletion FROM GlobalAiSettings", 4))
            assertEquals(0, rows(staging.aiSettings, "SELECT id FROM LlmConfiguredModel", 1).size)
            assertEquals(0, rows(staging.aiSettings, "SELECT id FROM LlmUsageRecord", 1).size)
        }
    }

    @Test
    fun preservedGlobalDefaultModelFollowsASkippedDuplicateModel() {
        val (fx, copy) = preservedCopyOfRich()
        val swiftModelId = fileRows(copy, "SELECT id FROM LlmConfiguredModel", 1).single().single()
        // The Swift side is hand-built below without a global row, so the preserved global row is the one that lands.
        exec(copy, "UPDATE LlmConfiguredModel SET id = ${x(201)}", "UPDATE AgentPrompt SET configuredModelId = NULL", "UPDATE BuiltinPromptOverride SET configuredModelId = NULL",
            "UPDATE LlmUsageRecord SET configuredModelId = ${x(201)}, id = ${x(204)}", "UPDATE GlobalAiSettings SET defaultModelId = ${x(201)}")
        // Same provider id as the preserved file's provider, same model id string: build the pair from the preserved rows.
        val providerId = fileRows(copy, "SELECT id FROM LlmProviderConfig", 1).single().single().removePrefix("b:")
        val modelName = fileRows(copy, "SELECT modelId FROM LlmConfiguredModel", 1).single().single().removePrefix("t:")
        fun legacyUuid(hex: String) = LegacyValue.UuidV("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}")
        val swiftData = LegacyAiData(
            providers = listOf(row(1, emptyMap(), "id" to legacyUuid(providerId), "providerType" to t("CUSTOM"), "displayName" to t("P"), "orderNumber" to i(1))),
            models = listOf(row(1, emptyMap(), "id" to legacyUuid(swiftModelId.removePrefix("b:")), "providerConfigId" to legacyUuid(providerId), "modelId" to t(modelName), "orderNumber" to i(1))),
            prompts = emptyList(), globals = emptyList(), usages = emptyList(), categories = emptyList(), overrides = emptyList(),
        )
        val report = MigrationReport()
        val dir = Files.createTempDirectory("staging-").toFile()
        val staging = StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory())
        leaked += staging to dir
        runBlocking { AiSettingsMapper(copy.toOkioPath()).map(swiftData, staging, report) }
        assertEquals(1, skipped(report).single { it.reason == AiSettingsMapper.MODEL_CONFLICT_REASON }.rows)
        assertEquals(listOf(listOf(swiftModelId)), rows(staging.aiSettings, "SELECT defaultModelId FROM GlobalAiSettings", 1))
    }

    @Test
    fun preservedLogsAndSyncTablesAreNotMigrated() {
        val (fx, copy) = preservedCopyOfRich()
        exec(
            copy,
            "INSERT INTO LlmRawLogRecord (id, promptName, modelName, providerType, timestamp, totalInputTokens, totalOutputTokens, estimatedCostUsd, logData, iterationCount, wasError) VALUES (${x(401)}, 'p', 'm', 'x', 1, 2, 3, 0.0, x'00', 1, 0)",
        )
        val (result, report) = mapAi(fx, copy.toOkioPath())
        assertEquals(emptyList(), result.differences)
        assertEquals(1, skipped(report).single { it.reason == "preserved LlmRawLogRecord rows are device-local debug logs and are not migrated" }.rows)
    }

    // ---- Hand-built Swift rows --------------------------------------------------------------------------------

    private fun prov(pk: Long, id: Int, name: String, order: Long = pk) = row(
        pk, emptyMap(), "id" to u(id), "providerType" to t("CUSTOM"), "displayName" to t(name), "endpoint" to t("https://e$pk.invalid"),
        "apiFormatRawValue" to t("OPENAI"), "orderNumber" to i(order),
    )

    private fun model(pk: Long, id: Int, provider: Int, modelId: String, price: Double = 1.0 * pk) = row(
        pk, emptyMap(), "id" to u(id), "providerConfigId" to u(provider), "modelId" to t(modelId), "orderNumber" to i(pk),
        "inputPricePerMillion" to r(price), "outputPricePerMillion" to r(price + 0.25), "cacheCreationPricePerMillion" to r(price + 0.5),
        "cacheReadPricePerMillion" to r(price + 0.75),
    )

    private fun prompt(pk: Long, id: Int, model: Int?, flags: List<Boolean>, order: Long = pk) = row(
        pk, emptyMap(), "id" to u(id), "name" to t("Prompt $pk"), "promptDescription" to t("desc $pk"), "promptTemplate" to t("tpl $pk"),
        "showInRawValue" to t("[\"VERSE_SELECTION\"]"), "orderNumber" to i(order), "createdAtMilliseconds" to i(1_600_000_000_000 + pk),
        "strictContextMatching" to b(flags[0]), "permissionModeRawValue" to t("ALLOW_ALL"), "allowedToolsRawValue" to t("[\"SEARCH_BIBLE\"]"),
        "deniedToolsRawValue" to null, "configuredModelId" to model?.let { u(it) }, "specifyBeforeRun" to b(flags[1]),
        "noDocumentCreation" to b(flags[2]), "maxIterations" to i(pk + 20), "autoIncludeDocuments" to b(flags[3]),
        "autoIncludeCommentaries" to b(flags[4]), "bibleOnly" to b(flags[5]), "isTextTransformation" to b(flags[6]), "categoryId" to u(500 + pk.toInt()),
    )

    private fun mapAiData(data: LegacyAiData): Pair<MigrationReport, (String, Int) -> List<List<String>>> {
        val report = MigrationReport()
        val dir = Files.createTempDirectory("staging-").toFile()
        val staging = StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory())
        leaked += staging to dir
        runBlocking { AiSettingsMapper(null).map(data, staging, report) }
        return report to { sql, n -> if (staging.openedDatabase("ai_settings.sqlite3") == null) emptyList() else rows(staging.aiSettings, sql, n) }
    }

    private fun aiData(
        providers: List<LegacyRow> = emptyList(), models: List<LegacyRow> = emptyList(), prompts: List<LegacyRow> = emptyList(),
        globals: List<LegacyRow> = emptyList(), usages: List<LegacyRow> = emptyList(), categories: List<LegacyRow> = emptyList(),
        overrides: List<LegacyRow> = emptyList(),
    ) = LegacyAiData(providers, models, prompts, globals, usages, categories, overrides)

    @Test
    fun everyAiColumnComesFromItsOwnSourceFieldAndBooleansAreNotSwapped() {
        val global = row(
            1, emptyMap(), "id" to LegacyValue.UuidV("a1000000-0000-0000-0000-000000000001"), "agentPermissionModeRawValue" to t("ASK"),
            "permanentlyAllowedToolsRawValue" to t("[\"A\"]"), "permanentlyDeniedToolsRawValue" to t("[\"D\"]"),
            "aiExcludedDocumentsRawValue" to t("[\"x\"]"), "commentaryMaxResponseTokens" to i(111), "hiddenBuiltInPromptsRawValue" to t("hp"),
            "maxIterations" to i(222), "commentaryDeselectedRawValue" to t("cd"), "defaultModelId" to u(2), "aiLanguage" to t("de"),
            "askModelBeforeRun" to b(true), "aiDisclaimerAccepted" to b(false), "hiddenBuiltInCategoriesRawValue" to t("hc"),
            "customAgentSystemPrompt" to t("cas"), "customTextTransformationSystemPrompt" to t("cts"), "favoritePromptsRawValue" to t("fp"),
            "rawLogRetentionDays" to null, "autoHideAgentLogOnCompletion" to b(true),
        )
        val data = aiData(
            providers = listOf(prov(1, 1, "P1"), prov(2, 2, "P2")),
            models = listOf(model(1, 11, 1, "m1"), model(2, 12, 2, "m2")),
            prompts = listOf(
                prompt(1, 21, 11, listOf(true, true, false, false, false, false, false)),
                prompt(2, 22, 12, listOf(false, false, true, true, true, true, true)),
            ),
            globals = listOf(global),
            usages = listOf(
                row(
                    1, emptyMap(), "id" to u(31), "configuredModelId" to u(11), "deviceId" to t("dev"), "inputTokens" to i(1),
                    "outputTokens" to i(2), "cacheCreationTokens" to i(3), "cacheReadTokens" to i(4_000_000_000_000), "estimatedCostUSD" to r(0.125),
                ),
            ),
            categories = listOf(
                row(1, emptyMap(), "id" to u(41), "name" to t("c1"), "orderNumber" to i(1), "hidden" to b(true)),
                row(2, emptyMap(), "id" to u(42), "name" to t("c2"), "orderNumber" to i(2), "hidden" to b(false)),
            ),
            overrides = listOf(row(1, emptyMap(), "id" to u(51), "configuredModelId" to u(12))),
        )
        val (report, read) = mapAiData(data)
        assertEquals(emptyList(), skipped(report))
        assertEquals(
            listOf(
                listOf(blob(uuid(1)), "t:CUSTOM", "t:P1", "t:https://e1.invalid", "t:OPENAI", "i:1"),
                listOf(blob(uuid(2)), "t:CUSTOM", "t:P2", "t:https://e2.invalid", "t:OPENAI", "i:2"),
            ),
            read("SELECT id, providerType, displayName, endpoint, apiFormat, orderNumber FROM LlmProviderConfig ORDER BY orderNumber", 6),
        )
        assertEquals(
            listOf(
                listOf(blob(uuid(11)), blob(uuid(1)), "t:m1", "i:1", "f:1.0", "f:1.25", "f:1.5", "f:1.75"),
                listOf(blob(uuid(12)), blob(uuid(2)), "t:m2", "i:2", "f:2.0", "f:2.25", "f:2.5", "f:2.75"),
            ),
            read("SELECT * FROM LlmConfiguredModel ORDER BY orderNumber", 8),
        )
        assertEquals(
            listOf(
                listOf("i:1", "i:1", "i:0", "i:0", "i:0", "i:0", "i:0"),
                listOf("i:0", "i:0", "i:1", "i:1", "i:1", "i:1", "i:1"),
            ),
            read("SELECT strictContextMatching, editBeforeRun, noDocumentCreation, autoIncludeDocuments, autoIncludeCommentaries, bibleOnly, isTextTransformation FROM AgentPrompt ORDER BY orderNumber", 7),
        )
        assertEquals(
            listOf(
                listOf(blob(uuid(21)), "t:Prompt 1", "t:desc 1", "t:tpl 1", "t:[\"VERSE_SELECTION\"]", "i:1", "i:1600000000001", "t:ALLOW_ALL", "t:[\"SEARCH_BIBLE\"]", "null", blob(uuid(11)), "i:21", blob(uuid(501))),
            ),
            read("SELECT id, name, description, promptTemplate, showIn, orderNumber, createdAt, permissionMode, allowedTools, deniedTools, configuredModelId, maxIterations, categoryId FROM AgentPrompt WHERE orderNumber = 1", 13),
        )
        assertEquals(
            listOf(
                listOf(
                    blob("a1000000-0000-0000-0000-000000000001"), "t:ASK", "t:[\"A\"]", "t:[\"D\"]", "t:[\"x\"]", "i:111", "t:hp", "i:222", "t:cd", blob(uuid(2)),
                    "t:de", "i:1", "i:0", "t:hc", "t:cas", "t:cts", "t:fp", "null", "i:1",
                ),
            ),
            read(
                "SELECT id, agentPermissionMode, permanentlyAllowedTools, permanentlyDeniedTools, aiExcludedDocuments, commentaryMaxResponseTokens, hiddenBuiltInPrompts, maxIterations, commentaryDeselected, defaultModelId, aiLanguage, askModelBeforeRun, aiDisclaimerAccepted, hiddenBuiltInCategories, customAgentSystemPrompt, customTextTransformationSystemPrompt, favoritePrompts, rawLogRetentionDays, autoHideAgentLogOnCompletion FROM GlobalAiSettings",
                19,
            ),
        )
        assertEquals(
            listOf(listOf(blob(uuid(31)), blob(uuid(11)), "t:dev", "i:1", "i:2", "i:3", "i:4000000000000", "f:0.125")),
            read("SELECT * FROM LlmUsageRecord", 8),
        )
        assertEquals(
            listOf(listOf(blob(uuid(41)), "t:c1", "i:1", "i:1"), listOf(blob(uuid(42)), "t:c2", "i:2", "i:0")),
            read("SELECT * FROM PromptCategory ORDER BY orderNumber", 4),
        )
        assertEquals(listOf(listOf(blob(uuid(51)), blob(uuid(12)))), read("SELECT * FROM BuiltinPromptOverride", 2))
    }

    @Test
    fun invalidSwiftRowsAreReportedAndTheRestIsStillWritten() {
        val singleton = LegacyValue.UuidV("a1000000-0000-0000-0000-000000000001")
        val data = aiData(
            providers = listOf(
                prov(1, 1, "first"), prov(2, 1, "replaces the first"), // same id: the later row wins
                prov(3, 3, "too big", order = 3_000_000_000L),
                row(4, emptyMap(), "providerType" to t("CUSTOM")), // no id
            ),
            models = listOf(
                model(1, 11, 2 + 998, "orphan"), // provider does not exist
                model(2, 12, 1, "dup"), model(3, 13, 1, "dup"), // (provider, modelId) already used
                model(4, 14, 1, "nan", price = Double.NaN),
            ),
            prompts = listOf(prompt(1, 21, 99, listOf(true, false, true, false, true, false, true))), // dangling configured model
            globals = listOf(row(1, emptyMap(), "id" to u(7), "aiLanguage" to t("x")), row(2, emptyMap(), "id" to singleton, "aiLanguage" to t("ok"))),
            usages = listOf(
                row(1, emptyMap(), "id" to u(31), "configuredModelId" to u(12), "deviceId" to t("d")),
                row(2, emptyMap(), "id" to u(32), "configuredModelId" to u(12), "deviceId" to t("d")), // same (model, device)
            ),
        )
        val (report, read) = mapAiData(data)
        assertEquals(listOf(listOf(blob(uuid(1)), "t:replaces the first")), read("SELECT id, displayName FROM LlmProviderConfig", 2))
        assertEquals(listOf(listOf(blob(uuid(12)))), read("SELECT id FROM LlmConfiguredModel", 1))
        assertEquals(listOf(listOf(blob(uuid(21)), "null", "i:1", "i:0")), read("SELECT id, configuredModelId, strictContextMatching, editBeforeRun FROM AgentPrompt", 4))
        assertEquals(listOf(listOf("t:ok")), read("SELECT aiLanguage FROM GlobalAiSettings", 1))
        assertEquals(listOf(listOf(blob(uuid(31)))), read("SELECT id FROM LlmUsageRecord", 1))
        val byReason = skipped(report).associate { it.reason to it.rows }
        assertEquals(1, byReason["duplicate LlmProviderConfig id"], byReason.toString())
        assertEquals(1, byReason["LlmProviderConfig value out of 32-bit range"], byReason.toString())
        assertEquals(1, byReason["LlmProviderConfig row without id"], byReason.toString())
        assertEquals(1, byReason["LlmConfiguredModel without an exported provider"], byReason.toString())
        assertEquals(1, byReason["duplicate LlmConfiguredModel provider and model id"], byReason.toString())
        assertEquals(1, byReason["LlmConfiguredModel value is not a finite number"], byReason.toString())
        assertEquals(1, byReason["AgentPrompt configuredModelId without an exported model cleared"], byReason.toString())
        assertEquals(1, byReason["GlobalAiSettings row with an id other than the singleton"], byReason.toString())
        assertEquals(1, byReason["duplicate LlmUsageRecord model and device"], byReason.toString())
    }

    @Test
    fun aiStagingIsOnlyCreatedWhenThereIsAnythingToWrite() {
        val (_, read) = mapAiData(aiData())
        assertEquals(emptyList(), read("SELECT id FROM LlmProviderConfig", 1))
    }

    // ---- Insert column lists ----------------------------------------------------------------------------------

    private fun checkInsertLists(db: RoomDatabase, statements: List<String>) = runBlocking {
        assertTrue(statements.isNotEmpty())
        db.useReaderConnection { c ->
            for (sql in statements) {
                val m = Regex("INSERT INTO (\\w+) \\((.*?)\\) VALUES \\((.*)\\)").matchEntire(sql)!!
                val table = m.groupValues[1]
                val columns = m.groupValues[2].split(",").map { it.trim().trim('`') }
                val actual = c.usePrepared("PRAGMA table_info(`$table`)") { st -> buildList { while (st.step()) add(st.getText(1)) } }
                assertEquals(actual.toSet(), columns.toSet(), "columns of $table")
                assertEquals(columns.size, m.groupValues[3].count { it == '?' }, "placeholders of $table")
            }
        }
    }

    @Test
    fun insertColumnListsCoverEveryColumnOfTheStagedTables() {
        withStaging { staging ->
            checkInsertLists(staging.myDocuments, MyDocumentsMapper.INSERT_STATEMENTS)
            assertEquals(setOf("MyDocument", "MyDocumentPage", "MyDocumentPageContent", "AiPageCacheEntry"), MyDocumentsMapper.INSERT_STATEMENTS.map { Regex("INSERT INTO (\\w+)").find(it)!!.groupValues[1] }.toSet())
            checkInsertLists(staging.aiSettings, AiSettingsMapper.INSERT_STATEMENTS)
            assertEquals(
                setOf("LlmProviderConfig", "LlmConfiguredModel", "AgentPrompt", "GlobalAiSettings", "LlmUsageRecord", "PromptCategory", "BuiltinPromptOverride"),
                AiSettingsMapper.INSERT_STATEMENTS.map { Regex("INSERT INTO (\\w+)").find(it)!!.groupValues[1] }.toSet(),
            )
        }
    }
}
