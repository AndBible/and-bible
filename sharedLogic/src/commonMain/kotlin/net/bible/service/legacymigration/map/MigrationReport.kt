package net.bible.service.legacymigration.map

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One line of the [MigrationReport]. */
sealed interface ReportEntry {
    /** [rows] rows were written to [table] of the category's staging database. */
    data class Written(val category: LegacyCategory, val table: String, val rows: Int) : ReportEntry

    /**
     * [rows] legacy rows (or values) were not migrated, for [reason]. [table] names the staged table the missing rows
     * would have belonged to when the mapper knows it (the validator then credits the skip to that table only); null
     * means unattributed (a value, or a row whose table the mapper does not name), credited to every table of the category.
     */
    data class Skipped(val category: LegacyCategory, val reason: String, val rows: Int, val table: String? = null) : ReportEntry

    /** A `Setting`/UserDefaults key that the key ledger does not know. Reported, never an error. */
    data class UnknownKey(val key: String) : ReportEntry
}

/**
 * What a migration run did: tables written with row counts, rows skipped with the reason, and unknown setting keys.
 * It is diagnostic only (the migration's outcome never depends on it) and is serialised by [toJson] for the user's
 * support request. Not thread-safe; a run uses it from one coroutine at a time.
 */
class MigrationReport {
    private val list = mutableListOf<ReportEntry>()

    val entries: List<ReportEntry> get() = list

    fun written(category: LegacyCategory, table: String, rows: Int) {
        list += ReportEntry.Written(category, table, rows)
    }

    fun skipped(category: LegacyCategory, reason: String, rows: Int, table: String? = null) {
        list += ReportEntry.Skipped(category, reason, rows, table)
    }

    fun unknownKey(key: String) {
        list += ReportEntry.UnknownKey(key)
    }

    fun toJson(): String = JsonObject(
        mapOf(
            "entries" to JsonArray(
                list.map { entry ->
                    when (entry) {
                        is ReportEntry.Written -> buildJsonObject {
                            put("type", "written")
                            put("category", entry.category.name)
                            put("table", entry.table)
                            put("rows", entry.rows)
                        }
                        is ReportEntry.Skipped -> buildJsonObject {
                            put("type", "skipped")
                            put("category", entry.category.name)
                            put("reason", entry.reason)
                            put("rows", entry.rows)
                            entry.table?.let { put("table", it) }
                        }
                        is ReportEntry.UnknownKey -> buildJsonObject {
                            put("type", "unknownKey")
                            put("key", entry.key)
                        }
                    }
                },
            ),
        ),
    ).toString()
}
