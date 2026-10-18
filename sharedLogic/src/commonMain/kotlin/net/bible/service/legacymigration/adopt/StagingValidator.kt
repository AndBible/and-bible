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
 * The per-table expectation table. For a table in [ROW_EXPECTATIONS], `w` staged rows (the real `COUNT(*)`), `r` legacy
 * rows of its entity and `s` the skipped rows credited to the table must satisfy
 *
 *     r - s  <=  w        always (the lower bound: every legacy row is staged or accounted for by a reported skip)
 *     w  <=  r            only when `capped` (the mapper never invents rows of a table it fills one to one)
 *
 * `s` is the sum of the category's `Skipped` entries whose `table` is this table, plus the unattributed ones (`table` null:
 * values and rows whose table the mapper does not name), which are credited to every table of the category. Entries
 * attributed to another table do not excuse this one, so a hidden parent whose children vanish must report them with their
 * table (the bookmark mapper does, for notes, label links and StudyPad rows of a hidden or dropped parent).
 *
 * Final table, per category:
 * - WORKSPACES: `Workspace`, `Window`, `HistoryItem` exact-or-lower (lower bound with skips, capped). History items of a
 *   window that was not exported are skipped and reported. `PageManager` has NO reader expectation: the mapper writes one
 *   page manager per exported window and synthesizes a default when a window has none (and two windows may share one
 *   legacy row), so it can exceed or fall short of the legacy count; see [EQUAL_ROWS]: it must equal the `Window` count.
 *   `WorkspaceLabelOverride` and `GlobalTextDisplaySettings` come from settings keys: no legacy count.
 * - BOOKMARKS: all nine tables lower-bound with skips and capped. Children of a hidden parent (untrusted ordinals, unknown
 *   versification) are reported as skips of the child table; duplicates and dangling rows likewise.
 * - READING_PLANS: `ReadingPlan` lower-bound with skips, capped. `ReadingPlanStatus` is derived from days and settings: no
 *   legacy count.
 * - MY_DOCUMENTS: the four tables lower-bound with skips, capped (pages and contents of a dropped parent are reported).
 * - AI_SETTINGS: lower bound only, NOT capped: the preserved Android file adds rows (decision U11). `GlobalAiSettings`
 *   is one fixed-id row, no expectation.
 * - PROGRESS, SETTINGS, REPOSITORIES: derived from JSON settings, keys and files: no legacy row count; only the checks
 *   that hold for every table.
 *
 * Every table also gets the universal checks: the real count equals the report's written count, and the file passes
 * `integrity_check`.
 */
internal val ROW_EXPECTATIONS: List<RowExpectation> = listOf(
    RowExpectation(LegacyCategory.WORKSPACES, "Workspace", listOf("Workspace")),
    RowExpectation(LegacyCategory.WORKSPACES, "Window", listOf("Window")),
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
 * A cross-table rule: [table] must hold exactly as many rows as [other] of the same category ([reason] says why).
 */
internal class EqualRows(val category: LegacyCategory, val table: String, val other: String, val reason: String)

/** The cross-table rules. */
internal val EQUAL_ROWS: List<EqualRows> = listOf(
    EqualRows(LegacyCategory.WORKSPACES, "PageManager", "Window", "the mapper writes one page manager per exported window, synthesizing a default for a window without one"),
)

/**
 * Checks the staged databases before they are adopted. Every failure is a [LegacyMigrationException] of kind
 * [Kind.VALIDATION_FAILED]. For each staged file:
 *
 * 1. it opens through [databases] (the current Room schema, identity hash and migrations);
 * 2. `PRAGMA integrity_check` returns `ok`;
 * 3. for each table the report says was written, the table exists and its real row count equals the written count
 *    (this is what catches a mapper that reports one thing and stores another);
 * 4. the cross-table [EQUAL_ROWS] hold, and the per-table [ROW_EXPECTATIONS] against the reader's counts hold, for every expected table whether or not the
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
        val skips = report.entries.filterIsInstance<ReportEntry.Skipped>()
        /** Skipped rows credited to [table]: attributed to it, or unattributed (credited to the whole category). */
        fun skippedFor(category: LegacyCategory, table: String) = skips.filter { it.category == category && (it.table == null || it.table == table) }.sumOf { it.rows }
        for ((category, name) in STAGED_DATABASES) {
            if (name in present.map { it.second }) continue
            val missing = ROW_EXPECTATIONS.firstOrNull { it.category == category && it.readerEntities.sumOf(readerCount) - skippedFor(category, it.table) > 0 }
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
                    checkDatabase(db, category, name, written, ::skippedFor, readerCount)
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
        skippedFor: (LegacyCategory, String) -> Int,
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
            for (expectation in ROW_EXPECTATIONS.filter { it.category == category }) {
                val reader = expectation.readerEntities.sumOf(readerCount)
                val skipped = skippedFor(category, expectation.table)
                val rows = actual[expectation.table] ?: if (expectation.table in tables) {
                    c.usePrepared("SELECT COUNT(*) FROM `${expectation.table}`") { st -> check(st.step()); st.getLong(0).toInt() }
                } else {
                    if (reader - skipped > 0) fail("$category.${expectation.table}: the table is missing from $name but the legacy store has $reader rows")
                    continue
                }
                if (rows < reader - skipped) fail("$category.${expectation.table}: $rows rows staged, legacy store has $reader and only $skipped were reported as skipped")
                if (expectation.capped && rows > reader) fail("$category.${expectation.table}: $rows rows staged but the legacy store has only $reader")
            }
            for (rule in EQUAL_ROWS.filter { it.category == category }) {
                suspend fun rowsOf(table: String): Int? = if (table !in tables) null else c.usePrepared("SELECT COUNT(*) FROM `$table`") { st -> check(st.step()); st.getLong(0).toInt() }
                val mine = rowsOf(rule.table)
                val theirs = rowsOf(rule.other)
                if (mine != theirs) fail("$category.${rule.table}: $mine rows staged but ${rule.other} has $theirs (${rule.reason})")
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
