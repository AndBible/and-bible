package net.bible.service.legacymigration.map

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import net.bible.android.database.AI_SETTINGS_DATABASE_VERSION
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.migrations.aiSettingsMigrations
import net.bible.service.db.buildAppDatabase
import net.bible.service.db.openSqlite
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.llm.GlobalAiSettings
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/** The legacy rows an [AiSettingsMapper] needs, from the cloud store. [from] reads them from a [LegacySnapshot]; tests can build one by hand. */
class LegacyAiData(
    val providers: List<LegacyRow>,
    val models: List<LegacyRow>,
    val prompts: List<LegacyRow>,
    val globals: List<LegacyRow>,
    val usages: List<LegacyRow>,
    val categories: List<LegacyRow>,
    val overrides: List<LegacyRow>,
) {
    companion object {
        fun from(snapshot: LegacySnapshot) = LegacyAiData(
            providers = snapshot.cloud.rows("LLMProviderConfig"),
            models = snapshot.cloud.rows("LLMConfiguredModel"),
            prompts = snapshot.cloud.rows("AgentPrompt"),
            globals = snapshot.cloud.rows("GlobalAISettings"),
            usages = snapshot.cloud.rows("LLMUsageRecord"),
            categories = snapshot.cloud.rows("PromptCategory"),
            overrides = snapshot.cloud.rows("BuiltInPromptOverride"),
        )
    }
}

/**
 * Migrates the AI settings (providers, configured models, agent prompts, global settings, usage records, prompt
 * categories and built-in prompt overrides; never the API keys, which are in the Keychain) into the current
 * `AiSettingsDatabase`, merging in the Android `ai_settings.sqlite3` that an earlier Android restore left behind
 * ([preservedAiDb], null when there is none; any schema version 1..23, migrated on a copy). Decision U11: the Swift app's own rows win on an id collision; the
 * preserved file's rows are added where the id is absent.
 *
 * The Swift side is a port of `RemoteSyncAISettingsSnapshotService.snapshotCurrentStateStrict` (row projection and sorts)
 * and of `RemoteSyncAISettingsDatabaseWriter`'s validation and INSERT column lists (reached from
 * `RemoteSyncInitialBackupUploadService.buildAISettingsInitialBackup`). Differences, all deliberate:
 *
 * - **Failures are reported, not fatal.** Swift aborts on a repeated id, a repeated (provider, model) or (model, device)
 *   pair, a singleton id other than `GlobalAiSettings.SINGLETON_ID`, a non-finite number, a model without a provider or
 *   a prompt/override pointing at a missing model. Here the row is skipped (a dangling model reference of a prompt or
 *   override is cleared, as the `ON DELETE SET_NULL` foreign key would) and counted in the [MigrationReport]. A repeated id
 *   replaces the earlier row.
 * - **Preserved rows.** A preserved model that repeats a (provider, model id) pair is skipped and everything that
 *   referred to it follows the existing model. The preserved file's raw LLM logs (device-local debug data) and its sync
 *   tables are not migrated; a file of version 0 or newer than the current schema, or a missing or unreadable one, is reported and ignored.
 *   The source file is only copied, never opened.
 * - Rows are inserted with SQL, not Room entities: the converters of `AgentPrompt`, `GlobalAiSettings` and
 *   `LlmProviderConfig` would rewrite the raw values the Swift store holds (unknown enum names become null, JSON sets are
 *   re-encoded), which the migration must carry verbatim. They go in one writer transaction.
 *
 * The staging database is only created when there is a row to write.
 */
class AiSettingsMapper(private val preservedAiDb: Path?) : CategoryMapper {
    override val category = LegacyCategory.AI_SETTINGS

    override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) =
        map(LegacyAiData.from(snapshot), staging, report)

    /** Maps [data] (and the preserved file) into [staging]; [map] with a snapshot reads the data from the stores and calls this. */
    suspend fun map(data: LegacyAiData, staging: StagingDatabases, report: MigrationReport) {
        val drops = Drops()
        val stage = try {
            merge(swiftRows(data, drops), preservedRows(staging, drops), drops)
        } finally {
            drops.flushTo(report, category)
        }
        if (stage.all { it.second.isEmpty() }) return
        staging.aiSettings.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                for ((def, rows) in stage) for (cells in rows) usePrepared(def.insertSql) { it.bindCells(cells); it.step() }
            }
        }
        for ((def, rows) in stage) report.written(category, def.table, rows.size)
    }

    /** A row with where it came from: the Swift store (`false`) or the preserved Android file (`true`). */
    private class Tagged(val cells: List<Any?>, val preserved: Boolean) {
        val id get() = (cells[0] as ByteArray).hexKey()
    }

    private class Tables(val rows: Map<TableDef, MutableList<Tagged>> = ALL.associateWith { mutableListOf() })

    // ---- The Swift rows ---------------------------------------------------------------------------------------

    /** Port of the snapshot projection: rows sorted like Swift, a repeated id replacing the earlier row. */
    private fun swiftRows(data: LegacyAiData, drops: Drops): Tables {
        val tables = Tables()
        fun add(def: TableDef, rows: List<LegacyRow>, sort: Comparator<LegacyRow>) {
            val byId = LinkedHashMap<String, Tagged>()
            for (row in rows.sortedWith(sort)) {
                val cells = def.fromLegacy(row, drops) ?: continue
                if (def === GLOBAL && !(cells[0] as ByteArray).contentEquals(GlobalAiSettings.SINGLETON_ID.toByteArray()!!)) {
                    drops.add("GlobalAiSettings row with an id other than the singleton"); continue
                }
                val tagged = Tagged(cells, preserved = false)
                if (byId.put(tagged.id, tagged) != null) drops.add("duplicate ${def.label} id")
            }
            tables.rows.getValue(def) += byId.values
        }
        add(PROVIDER, data.providers, compareBy<LegacyRow>({ it.long("orderNumber") }, { it.text("displayName") }, { it.uuidString("id") }))
        add(MODEL, data.models, compareBy<LegacyRow>({ it.uuidString("providerConfigId") }, { it.long("orderNumber") }, { it.text("modelId") }, { it.uuidString("id") }))
        add(PROMPT, data.prompts, compareBy<LegacyRow>({ it.long("orderNumber") }, { it.long("createdAtMilliseconds") }, { it.uuidString("id") }))
        add(GLOBAL, data.globals, compareBy { it.uuidString("id") })
        add(USAGE, data.usages, compareBy<LegacyRow>({ it.uuidString("configuredModelId") }, { it.text("deviceId") }, { it.uuidString("id") }))
        add(CATEGORY, data.categories, compareBy<LegacyRow>({ it.long("orderNumber") }, { it.text("name") }, { it.uuidString("id") }))
        add(OVERRIDE, data.overrides, compareBy { it.uuidString("id") })
        return tables
    }

    // ---- The preserved Android file ---------------------------------------------------------------------------

    /**
     * Reads the preserved file's rows of the seven synchronised tables, or nothing (with a report line) when it cannot be
     * used. The Swift app keeps an Android file of any schema version up to 22, so the file is first copied (with its
     * `-wal`/`-journal` sidecars) into the staging directory and brought to the current schema by the app's own
     * [aiSettingsMigrations] on the copy; the source is never opened. Nothing is counted in [drops] for a read that fails.
     */
    private suspend fun preservedRows(staging: StagingDatabases, drops: Drops): Tables {
        val source = preservedAiDb ?: return Tables()
        // Fail fast (outside the read's catch): buildAppDatabase resolves a relative path against the app's database
        // directory and would move a corrupt copy aside instead of throwing.
        require(staging.directory.isAbsolute) { "staging directory must be absolute: ${staging.directory}" }
        val local = Drops()
        val tables = try {
            readPreserved(source, staging, local)
        } catch (e: Exception) {
            drops.add("preserved ai_settings.sqlite3 could not be read: ${e.message}")
            return Tables()
        }
        drops.merge(local)
        return tables
    }

    private suspend fun readPreserved(source: Path, staging: StagingDatabases, drops: Drops): Tables {
        val tables = Tables()
        val fs = FileSystem.SYSTEM
        if (!fs.exists(source)) { drops.add(MISSING_FILE_REASON); return tables }
        fs.createDirectories(staging.directory)
        val copy = fs.canonicalize(staging.directory) / "ai_settings.preserved.sqlite3"
        val allSuffixes = listOf("", "-wal", "-journal", "-shm", ".lck")
        // A leftover of an earlier run must not leak into this copy.
        for (suffix in allSuffixes) fs.delete((copy.toString() + suffix).toPath(), mustExist = false)
        try {
            for (suffix in listOf("", "-wal", "-journal")) {
                val from = (source.toString() + suffix).toPath()
                if (fs.exists(from)) {
                    val to = (copy.toString() + suffix).toPath()
                    fs.delete(to, mustExist = false)
                    fs.copy(from, to)
                }
            }
            val version = openSqlite(copy.toString()).use { scalar(it, "PRAGMA user_version") }
            if (version < 1 || version > AI_SETTINGS_DATABASE_VERSION) { drops.add(unsupportedVersionReason(version.toInt())); return tables }
            // What the migration chain removes (rows of dropped tables, the prompts' model link) is counted before and after.
            val before = if (version < AI_SETTINGS_DATABASE_VERSION) openSqlite(copy.toString()).use { survey(it) } else null
            if (version < AI_SETTINGS_DATABASE_VERSION) {
                val db = buildAppDatabase<AiSettingsDatabase>(copy.toString(), *aiSettingsMigrations)
                try {
                    db.useWriterConnection { it.usePrepared("SELECT 1") { st -> st.step() } }
                } finally {
                    db.close()
                }
            }
            val db = openSqlite(copy.toString(), readOnly = true)
            try {
                val existing = names(db)
                if (before != null) {
                    val after = survey(db)
                    for (def in ALL) {
                        val lost = (before.rows[def.table] ?: 0L) - (after.rows[def.table] ?: 0L)
                        if (lost > 0) drops.add(migrationDropReason(def.table, version.toInt()), lost.toInt())
                    }
                    if (before.promptModelLinks > after.promptModelLinks) {
                        drops.add(migrationLinkDropReason(version.toInt()), (before.promptModelLinks - after.promptModelLinks).toInt())
                    }
                }
                for (def in ALL) {
                    if (def.table !in existing) { drops.add("preserved ai_settings.sqlite3 has no ${def.table} table"); continue }
                    tables.rows.getValue(def) += readTable(db, def, drops)
                }
                if ("LlmRawLogRecord" in existing) {
                    val logs = scalar(db, "SELECT COUNT(*) FROM LlmRawLogRecord").toInt()
                    if (logs > 0) drops.add("preserved LlmRawLogRecord rows are device-local debug logs and are not migrated", logs)
                }
            } finally {
                db.close()
            }
        } finally {
            for (suffix in allSuffixes) fs.delete((copy.toString() + suffix).toPath(), mustExist = false)
        }
        return tables
    }

    /** Row counts of the seven tables (0 for a table the schema does not have yet) and how many prompts name a model. */
    private class Survey(val rows: Map<String, Long>, val promptModelLinks: Long)

    private fun survey(db: SQLiteConnection): Survey {
        val existing = names(db)
        val rows = ALL.associate { it.table to if (it.table in existing) scalar(db, "SELECT COUNT(*) FROM ${it.table}") else 0L }
        // The link is `providerConfigId` before schema v9 and `configuredModelId` from then on.
        val link = if ("AgentPrompt" !in existing) null else
            db.prepare("SELECT name FROM pragma_table_info('AgentPrompt')").use { st ->
                buildList { while (st.step()) add(st.getText(0)) }
            }.firstOrNull { it == "configuredModelId" || it == "providerConfigId" }
        val links = link?.let { scalar(db, "SELECT COUNT(*) FROM AgentPrompt WHERE $it IS NOT NULL") } ?: 0L
        return Survey(rows, links)
    }

    private fun scalar(db: SQLiteConnection, sql: String): Long = db.prepare(sql).use { check(it.step()); it.getLong(0) }

    private fun names(db: SQLiteConnection): Set<String> =
        db.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { st -> buildSet { while (st.step()) add(st.getText(0)) } }

    private fun readTable(db: SQLiteConnection, def: TableDef, drops: Drops): List<Tagged> {
        val result = ArrayList<Tagged>()
        db.prepare("SELECT ${def.columns.joinToString(", ") { it.name }} FROM ${def.table}").use { st ->
            rows@ while (st.step()) {
                val cells = ArrayList<Any?>(def.columns.size)
                for ((i, col) in def.columns.withIndex()) {
                    val type = st.getColumnType(i)
                    val cell: Any? = when {
                        type == SQLITE_DATA_NULL -> if (col.nullable) null else { drops.add("preserved ${def.label} row with an unexpected value"); continue@rows }
                        col.kind == Kind.ID && type == SQLITE_DATA_BLOB && st.getBlob(i).size == 16 -> st.getBlob(i)
                        col.kind == Kind.TEXT && type == SQLITE_DATA_TEXT -> Txt(st.getBlob(i))
                        (col.kind == Kind.INT64 || col.kind == Kind.DATE) && type == SQLITE_DATA_INTEGER -> st.getLong(i)
                        col.kind == Kind.INT32 && type == SQLITE_DATA_INTEGER && st.getLong(i) in Int.MIN_VALUE..Int.MAX_VALUE -> st.getLong(i)
                        col.kind == Kind.BOOL && type == SQLITE_DATA_INTEGER -> if (st.getLong(i) != 0L) 1L else 0L
                        col.kind == Kind.REAL && (type == SQLITE_DATA_FLOAT || type == SQLITE_DATA_INTEGER) && st.getDouble(i).isFinite() -> st.getDouble(i)
                        else -> { drops.add("preserved ${def.label} row with an unexpected value"); continue@rows }
                    }
                    cells += cell
                }
                result += Tagged(cells, preserved = true)
            }
        }
        return result
    }

    // ---- Merge (U11) and integrity ------------------------------------------------------------------------------

    /**
     * Unions the preserved rows into the Swift rows (Swift wins an id collision), then enforces what Android's schema
     * needs: models need a provider, (provider, model id) and (model, device) are unique, and a prompt or override only
     * points at a model that exists. Returns the tables in dependency order.
     */
    private fun merge(swift: Tables, preserved: Tables, drops: Drops): List<Pair<TableDef, List<List<Any?>>>> {
        val merged = Tables()
        for (def in ALL) {
            val byId = LinkedHashMap<String, Tagged>()
            for (row in swift.rows.getValue(def)) byId[row.id] = row
            for (row in preserved.rows.getValue(def)) {
                if (def === GLOBAL && !(row.cells[0] as ByteArray).contentEquals(GlobalAiSettings.SINGLETON_ID.toByteArray()!!)) {
                    drops.add("preserved GlobalAiSettings row with an id other than the singleton"); continue
                }
                if (row.id in byId) drops.add(supersededReason(def.table)) else byId[row.id] = row
            }
            merged.rows.getValue(def) += byId.values
        }

        val providerIds = merged.rows.getValue(PROVIDER).map { it.id }.toSet()
        // (provider, model id) is unique: the first row wins (Swift rows come first); references of a loser follow the winner.
        val remap = HashMap<String, ByteArray>()
        val winners = HashMap<String, ByteArray>()
        val models = merged.rows.getValue(MODEL).filter { row ->
            val provider = (row.cells[1] as ByteArray).hexKey()
            if (provider !in providerIds) { drops.add("LlmConfiguredModel without an exported provider"); return@filter false }
            val pair = provider + "\u0000" + (row.cells[2] as Txt).bytes.decodeToString()
            val winner = winners[pair]
            if (winner == null) { winners[pair] = row.cells[0] as ByteArray; true } else {
                drops.add(if (row.preserved) MODEL_CONFLICT_REASON else "duplicate LlmConfiguredModel provider and model id")
                remap[row.id] = winner
                false
            }
        }
        val modelIds = models.map { it.id }.toSet()
        fun followed(cell: Any?): Any? = (cell as ByteArray?)?.let { remap[it.hexKey()] ?: it }

        val prompts = merged.rows.getValue(PROMPT).map { row ->
            Tagged(clearMissingModel(row.cells, 11, modelIds, remap, "AgentPrompt", drops), row.preserved)
        }
        val overrides = merged.rows.getValue(OVERRIDE).map { row ->
            Tagged(clearMissingModel(row.cells, 1, modelIds, remap, "BuiltinPromptOverride", drops), row.preserved)
        }
        // A usage record may point at a model that is gone (Android lets it dangle); it only follows a skipped duplicate.
        val usedPairs = HashSet<String>()
        val usages = merged.rows.getValue(USAGE).map { row -> Tagged(row.cells.toMutableList().also { it[1] = followed(it[1]) }, row.preserved) }
            .filter { row ->
                usedPairs.add((row.cells[1] as ByteArray).hexKey() + "\u0000" + (row.cells[2] as Txt).bytes.decodeToString()).also {
                    if (!it) drops.add(if (row.preserved) "preserved duplicate LlmUsageRecord model and device" else "duplicate LlmUsageRecord model and device")
                }
            }

        return listOf(
            PROVIDER to merged.rows.getValue(PROVIDER).map { it.cells },
            MODEL to models.map { it.cells },
            PROMPT to prompts.map { it.cells },
            // A preserved global row's default model follows a skipped duplicate model like every other reference.
            GLOBAL to merged.rows.getValue(GLOBAL).map { if (it.preserved) it.cells.toMutableList().also { c -> c[9] = followed(c[9]) } else it.cells },
            USAGE to usages.map { it.cells },
            CATEGORY to merged.rows.getValue(CATEGORY).map { it.cells },
            OVERRIDE to overrides.map { it.cells },
        )
    }

    /** Makes the model reference in [cells] at [index] follow [remap], or clears it when that model is not staged. */
    private fun clearMissingModel(cells: List<Any?>, index: Int, modelIds: Set<String>, remap: Map<String, ByteArray>, label: String, drops: Drops): List<Any?> {
        val ref = cells[index] as ByteArray? ?: return cells
        val target = remap[ref.hexKey()] ?: ref
        if (target.hexKey() in modelIds) return if (target === ref) cells else cells.toMutableList().also { it[index] = target }
        drops.add("$label configuredModelId without an exported model cleared")
        return cells.toMutableList().also { it[index] = null }
    }

    companion object {
        private val PROVIDER = TableDef(
            "LlmProviderConfig", "LlmProviderConfig",
            listOf(
                Col("id", Kind.ID), Col("providerType", Kind.TEXT), Col("displayName", Kind.TEXT), Col("endpoint", Kind.TEXT, nullable = true),
                Col("apiFormat", Kind.TEXT, "apiFormatRawValue", nullable = true), Col("orderNumber", Kind.INT32),
            ),
        )
        private val MODEL = TableDef(
            "LlmConfiguredModel", "LlmConfiguredModel",
            listOf(
                Col("id", Kind.ID), Col("providerConfigId", Kind.ID), Col("modelId", Kind.TEXT), Col("orderNumber", Kind.INT32),
                Col("inputPricePerMillion", Kind.REAL), Col("outputPricePerMillion", Kind.REAL),
                Col("cacheCreationPricePerMillion", Kind.REAL), Col("cacheReadPricePerMillion", Kind.REAL),
            ),
        )
        // configuredModelId is column 11 (see clearMissingModel).
        private val PROMPT = TableDef(
            "AgentPrompt", "AgentPrompt",
            listOf(
                Col("id", Kind.ID), Col("name", Kind.TEXT), Col("description", Kind.TEXT, "promptDescription", nullable = true),
                Col("promptTemplate", Kind.TEXT), Col("showIn", Kind.TEXT, "showInRawValue"), Col("orderNumber", Kind.INT32),
                Col("createdAt", Kind.INT64, "createdAtMilliseconds"), Col("strictContextMatching", Kind.BOOL, default = true),
                Col("permissionMode", Kind.TEXT, "permissionModeRawValue", nullable = true),
                Col("allowedTools", Kind.TEXT, "allowedToolsRawValue", nullable = true),
                Col("deniedTools", Kind.TEXT, "deniedToolsRawValue", nullable = true),
                Col("configuredModelId", Kind.ID, nullable = true), Col("editBeforeRun", Kind.BOOL, "specifyBeforeRun"),
                Col("noDocumentCreation", Kind.BOOL), Col("maxIterations", Kind.INT32, nullable = true),
                Col("autoIncludeDocuments", Kind.BOOL), Col("autoIncludeCommentaries", Kind.BOOL), Col("bibleOnly", Kind.BOOL),
                Col("isTextTransformation", Kind.BOOL), Col("categoryId", Kind.ID, nullable = true),
            ),
        )
        private val GLOBAL = TableDef(
            "GlobalAiSettings", "GlobalAiSettings",
            listOf(
                Col("id", Kind.ID), Col("agentPermissionMode", Kind.TEXT, "agentPermissionModeRawValue", nullable = true),
                Col("permanentlyAllowedTools", Kind.TEXT, "permanentlyAllowedToolsRawValue", nullable = true),
                Col("permanentlyDeniedTools", Kind.TEXT, "permanentlyDeniedToolsRawValue", nullable = true),
                Col("aiExcludedDocuments", Kind.TEXT, "aiExcludedDocumentsRawValue"),
                Col("commentaryMaxResponseTokens", Kind.INT32, default = 15_000L),
                Col("hiddenBuiltInPrompts", Kind.TEXT, "hiddenBuiltInPromptsRawValue"), Col("maxIterations", Kind.INT32, default = 10L),
                Col("commentaryDeselected", Kind.TEXT, "commentaryDeselectedRawValue"), Col("defaultModelId", Kind.ID, nullable = true),
                Col("aiLanguage", Kind.TEXT, nullable = true), Col("askModelBeforeRun", Kind.BOOL), Col("aiDisclaimerAccepted", Kind.BOOL),
                Col("hiddenBuiltInCategories", Kind.TEXT, "hiddenBuiltInCategoriesRawValue"),
                Col("customAgentSystemPrompt", Kind.TEXT, nullable = true), Col("customTextTransformationSystemPrompt", Kind.TEXT, nullable = true),
                Col("favoritePrompts", Kind.TEXT, "favoritePromptsRawValue"), Col("rawLogRetentionDays", Kind.INT32, nullable = true),
                Col("autoHideAgentLogOnCompletion", Kind.BOOL),
            ),
        )
        private val USAGE = TableDef(
            "LlmUsageRecord", "LlmUsageRecord",
            listOf(
                Col("id", Kind.ID), Col("configuredModelId", Kind.ID), Col("deviceId", Kind.TEXT), Col("inputTokens", Kind.INT64),
                Col("outputTokens", Kind.INT64), Col("cacheCreationTokens", Kind.INT64), Col("cacheReadTokens", Kind.INT64),
                Col("estimatedCostUsd", Kind.REAL, "estimatedCostUSD"),
            ),
        )
        private val CATEGORY = TableDef(
            "PromptCategory", "PromptCategory",
            listOf(Col("id", Kind.ID), Col("name", Kind.TEXT), Col("orderNumber", Kind.INT32), Col("hidden", Kind.BOOL)),
        )
        private val OVERRIDE = TableDef(
            "BuiltinPromptOverride", "BuiltinPromptOverride",
            listOf(Col("id", Kind.ID), Col("configuredModelId", Kind.ID, nullable = true)),
        )

        /** The seven tables in dependency order. */
        private val ALL = listOf(PROVIDER, MODEL, PROMPT, GLOBAL, USAGE, CATEGORY, OVERRIDE)

        /** The INSERT statements, exposed for the test that compares their column lists with the staged tables. */
        internal val INSERT_STATEMENTS: List<String> = ALL.map { it.insertSql }

        /** Report line for [count] preserved rows of [table] that a Swift row with the same id replaced. */
        fun supersededReason(table: String) = "preserved $table row superseded by the Swift app's own row with the same id"

        /** Report line for [table] rows that the Android migration chain from schema [version] removed from the preserved file. */
        fun migrationDropReason(table: String, version: Int) = "preserved $table rows dropped by the Android schema migration from v$version"

        fun migrationLinkDropReason(version: Int) = "preserved AgentPrompt model links dropped by the Android schema migration from v$version"

        fun unsupportedVersionReason(version: Int) = "preserved ai_settings.sqlite3 has unsupported schema version $version"

        const val MODEL_CONFLICT_REASON = "preserved LlmConfiguredModel row skipped: same provider and model id as an existing row"
        const val MISSING_FILE_REASON = "preserved ai_settings.sqlite3 not found"
    }
}
