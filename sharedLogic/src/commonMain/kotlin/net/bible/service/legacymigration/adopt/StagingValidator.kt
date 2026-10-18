package net.bible.service.legacymigration.adopt

import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.service.legacymigration.LegacyMigrationException
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.service.legacymigration.map.ReportEntry
import net.bible.service.legacymigration.stage.LegacyDatabaseFactory
import net.bible.service.legacymigration.stage.StagingDatabases
import okio.FileSystem
import okio.Path

/** The database file each [LegacyCategory] is staged in (the app's own file names), in the order the adopter moves them. */
internal val STAGED_DATABASES: List<Pair<LegacyCategory, String>> = listOf(
    LegacyCategory.WORKSPACES to WorkspaceDatabase.dbFileName,
    LegacyCategory.BOOKMARKS to BookmarkDatabase.dbFileName,
    LegacyCategory.READING_PLANS to ReadingPlanDatabase.dbFileName,
    LegacyCategory.PROGRESS to ProgressDatabase.dbFileName,
    LegacyCategory.MY_DOCUMENTS to MyDocumentDatabase.dbFileName,
    LegacyCategory.AI_SETTINGS to AiSettingsDatabase.dbFileName,
    LegacyCategory.SETTINGS to SettingsDatabase.dbFileName,
    LegacyCategory.REPOSITORIES to RepoDatabase.dbFileName,
)

/**
 * A table whose staged row count has a derivable relation to the legacy store: [table] of [category] is filled one to one
 * from the legacy entities [readerEntities] (summed). [capped] is false where the mapper legitimately adds rows of its own.
 */
internal class RowExpectation(
    val category: LegacyCategory,
    val table: String,
    val readerEntities: List<String>,
    val capped: Boolean = true,
)

/**
 * The per-table expectation table. Written rows `w` of such a table must satisfy
 *
 *     reader rows - skipped rows of the category  <=  w  <=  reader rows        (the upper bound only when `capped`)
 *
 * so every legacy row is either in the staged table or accounted for by a reported skip, and the mapper never invents
 * rows of a table it fills one to one. The skip count is the category's total (reports do not attribute a skip to a
 * table), which makes the lower bound lenient, never wrong. Tables derived from several sources or from settings keys
 * (`StudyPad` link rows are 1:1, but `ReadingPlanStatus`, the settings tables, `WorkspaceLabelOverride`,
 * `GlobalTextDisplaySettings`, the progress tables, repositories) have no derivable count and only get the checks that
 * hold for every table: the staged count equals the report's written count, and the file passes `integrity_check`.
 * The AI tables are not capped: the preserved Android file adds rows (decision U11).
 */
internal val ROW_EXPECTATIONS: List<RowExpectation> = listOf(
    RowExpectation(LegacyCategory.WORKSPACES, "Workspace", listOf("Workspace")),
    RowExpectation(LegacyCategory.WORKSPACES, "Window", listOf("Window")),
    RowExpectation(LegacyCategory.WORKSPACES, "PageManager", listOf("PageManager")),
    RowExpectation(LegacyCategory.WORKSPACES, "HistoryItem", listOf("HistoryItem")),
    RowExpectation(LegacyCategory.BOOKMARKS, "Label", listOf("Label")),
    RowExpectation(LegacyCategory.BOOKMARKS, "BibleBookmark", listOf("BibleBookmark")),
    RowExpectation(LegacyCategory.BOOKMARKS, "BibleBookmarkNotes", listOf("BibleBookmarkNotes")),
    RowExpectation(LegacyCategory.BOOKMARKS, "BibleBookmarkToLabel", listOf("BibleBookmarkToLabel")),
    RowExpectation(LegacyCategory.BOOKMARKS, "GenericBookmark", listOf("GenericBookmark")),
    RowExpectation(LegacyCategory.BOOKMARKS, "GenericBookmarkNotes", listOf("GenericBookmarkNotes")),
    RowExpectation(LegacyCategory.BOOKMARKS, "GenericBookmarkToLabel", listOf("GenericBookmarkToLabel")),
    RowExpectation(LegacyCategory.BOOKMARKS, "StudyPadTextEntry", listOf("StudyPadTextEntry")),
    RowExpectation(LegacyCategory.BOOKMARKS, "StudyPadTextEntryText", listOf("StudyPadTextEntryText")),
    RowExpectation(LegacyCategory.READING_PLANS, "ReadingPlan", listOf("ReadingPlan")),
    RowExpectation(LegacyCategory.MY_DOCUMENTS, "MyDocument", listOf("MyDocument")),
    RowExpectation(LegacyCategory.MY_DOCUMENTS, "MyDocumentPage", listOf("MyDocumentPage")),
    RowExpectation(LegacyCategory.MY_DOCUMENTS, "MyDocumentPageContent", listOf("MyDocumentPageContent")),
    RowExpectation(LegacyCategory.MY_DOCUMENTS, "AiPageCacheEntry", listOf("AiPageCacheEntry")),
    RowExpectation(LegacyCategory.AI_SETTINGS, "LlmProviderConfig", listOf("LLMProviderConfig"), capped = false),
    RowExpectation(LegacyCategory.AI_SETTINGS, "LlmConfiguredModel", listOf("LLMConfiguredModel"), capped = false),
    RowExpectation(LegacyCategory.AI_SETTINGS, "AgentPrompt", listOf("AgentPrompt"), capped = false),
    RowExpectation(LegacyCategory.AI_SETTINGS, "PromptCategory", listOf("PromptCategory"), capped = false),
    RowExpectation(LegacyCategory.AI_SETTINGS, "BuiltinPromptOverride", listOf("BuiltInPromptOverride"), capped = false),
    RowExpectation(LegacyCategory.AI_SETTINGS, "LlmUsageRecord", listOf("LLMUsageRecord"), capped = false),
)

/**
 * Checks the staged databases before they are adopted. Every failure is a [LegacyMigrationException] of kind
 * [Kind.VALIDATION_FAILED]. For each staged file:
 *
 * 1. it opens through [databases] (the current Room schema, identity hash and migrations);
 * 2. `PRAGMA integrity_check` returns `ok`;
 * 3. for each table the report says was written, the table exists and its real row count equals the written count
 *    (this is what catches a mapper that reports one thing and stores another);
 * 4. the per-table [ROW_EXPECTATIONS] against the reader's counts hold, for every expected table whether or not the
 *    report mentions it. A table that must hold rows (reader rows minus the category's skips is positive) but is absent or
 *    short fails; a category whose file was not staged fails the same way.
 *
 * It also fails when the report claims rows for a category whose file is missing.
 */
class StagingValidator(private val databases: LegacyDatabaseFactory, private val fs: FileSystem = FileSystem.SYSTEM) {

    /**
     * Validates [stagingDir] and returns the names of the staged database files, in adoption order. [readerCount] is the
     * legacy store's row count of an entity (`Workspace`, `BibleBookmark`, ...).
     */
    suspend fun validate(stagingDir: Path, report: MigrationReport, readerCount: (String) -> Int): List<String> {
        val present = STAGED_DATABASES.filter { (_, name) -> fs.exists(stagingDir / name) }
        val written = report.entries.filterIsInstance<ReportEntry.Written>()
        for ((category, name) in STAGED_DATABASES) {
            if (name !in present.map { it.second } && written.any { it.category == category && it.rows > 0 }) {
                fail("$category reports written rows but $name was not staged")
            }
        }
        val skippedByCategory = report.entries.filterIsInstance<ReportEntry.Skipped>().groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.rows } }
        for ((category, name) in STAGED_DATABASES) {
            if (name in present.map { it.second }) continue
            val skipped = skippedByCategory[category] ?: 0
            val missing = ROW_EXPECTATIONS.firstOrNull { it.category == category && it.readerEntities.sumOf(readerCount) - skipped > 0 }
            if (missing != null) fail("$category was not staged ($name is missing) but the legacy store has rows for ${missing.table}")
        }

        val staging = StagingDatabases(stagingDir, databases)
        try {
            for ((category, name) in present) {
                val db = try {
                    database(staging, name)
                } catch (e: Exception) {
                    fail("$name does not open: ${e.message}", e)
                }
                try {
                    checkDatabase(db, category, name, written, skippedByCategory, readerCount)
                } catch (e: LegacyMigrationException) {
                    throw e
                } catch (e: Exception) {
                    fail("$name cannot be checked: ${e.message}", e)
                }
            }
        } finally {
            staging.close()
        }
        return present.map { it.second }
    }

    private suspend fun checkDatabase(
        db: RoomDatabase,
        category: LegacyCategory,
        name: String,
        written: List<ReportEntry.Written>,
        skippedByCategory: Map<LegacyCategory, Int>,
        readerCount: (String) -> Int,
    ) {
        db.useReaderConnection { c ->
            val integrity = c.usePrepared("PRAGMA integrity_check") { st -> buildList { while (st.step()) add(st.getText(0)) } }
            if (integrity != listOf("ok")) fail("integrity_check of $name: ${integrity.take(3)}")

            val tables = c.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table'") { st -> buildList { while (st.step()) add(st.getText(0)) } }.toSet()
            val writtenRows = written.filter { it.category == category }.groupBy { it.table }.mapValues { (_, v) -> v.sumOf { it.rows } }
            val actual = HashMap<String, Int>()
            for ((table, rows) in writtenRows) {
                if (table !in tables) fail("$category: reported table $table is not in $name")
                val count = c.usePrepared("SELECT COUNT(*) FROM `$table`") { st -> check(st.step()); st.getLong(0).toInt() }
                actual[table] = count
                if (count != rows) fail("$category.$table: $count rows staged but the report says $rows were written")
            }
            val skipped = skippedByCategory[category] ?: 0
            for (expectation in ROW_EXPECTATIONS.filter { it.category == category }) {
                val reader = expectation.readerEntities.sumOf(readerCount)
                val rows = actual[expectation.table] ?: if (expectation.table in tables) {
                    c.usePrepared("SELECT COUNT(*) FROM `${expectation.table}`") { st -> check(st.step()); st.getLong(0).toInt() }
                } else {
                    if (reader - skipped > 0) fail("$category.${expectation.table}: the table is missing from $name but the legacy store has $reader rows")
                    continue
                }
                if (rows < reader - skipped) fail("$category.${expectation.table}: $rows rows staged, legacy store has $reader and only $skipped were reported as skipped")
                if (expectation.capped && rows > reader) fail("$category.${expectation.table}: $rows rows staged but the legacy store has only $reader")
            }
        }
    }

    private fun database(staging: StagingDatabases, name: String): RoomDatabase = when (name) {
        BookmarkDatabase.dbFileName -> staging.bookmarks
        WorkspaceDatabase.dbFileName -> staging.workspaces
        ReadingPlanDatabase.dbFileName -> staging.readingPlans
        MyDocumentDatabase.dbFileName -> staging.myDocuments
        ProgressDatabase.dbFileName -> staging.progress
        AiSettingsDatabase.dbFileName -> staging.aiSettings
        SettingsDatabase.dbFileName -> staging.settings
        RepoDatabase.dbFileName -> staging.repositories
        else -> error("not a staged database: $name")
    }

    private fun fail(message: String, cause: Throwable? = null): Nothing = throw LegacyMigrationException(Kind.VALIDATION_FAILED, message, cause)
}
