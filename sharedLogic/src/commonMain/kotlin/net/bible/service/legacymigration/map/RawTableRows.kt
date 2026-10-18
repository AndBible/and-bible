package net.bible.service.legacymigration.map

import androidx.sqlite.SQLiteStatement
import net.bible.android.database.IdType
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacyValue

// Row plumbing shared by MyDocumentsMapper and AiSettingsMapper: legacy rows to typed cells, bound as SQL parameters.

/** How a column is bound; [DATE] is a legacy `Date` written as epoch milliseconds. */
internal enum class Kind { ID, TEXT, INT32, INT64, DATE, REAL, BOOL }

/**
 * One column of a staged table: the Android column [name], the legacy property it comes from ([source], null when the
 * caller supplies the value), whether NULL is allowed and the value used when the legacy value is absent.
 */
internal class Col(val name: String, val kind: Kind, val source: String? = name, val nullable: Boolean = false, val default: Any? = null)

/** A text cell. Bound as bytes under `CAST(? AS TEXT)` so that an embedded NUL character is not a terminator. */
internal class Txt(val bytes: ByteArray) {
    constructor(s: String) : this(s.encodeToByteArray())
}

/** Rows and values rejected, counted by reason during one build. */
internal class Drops {
    val counts = LinkedHashMap<String, Int>()
    fun add(reason: String, rows: Int = 1) { counts[reason] = (counts[reason] ?: 0) + rows }
    /** Adds the counts of [other] (used to keep a failed attempt's partial counts out of the report). */
    fun merge(other: Drops) { for ((reason, rows) in other.counts) add(reason, rows) }

    fun flushTo(report: MigrationReport, category: LegacyCategory) {
        for ((what, rows) in counts) if (rows > 0) report.skipped(category, what, rows)
    }
}

internal fun ByteArray.hexKey(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/**
 * A staged table: its Android [table] name, the [label] used in report lines and the [columns] in INSERT order. The
 * first column is the primary key. Rows are plain cell lists (`null`, `Long`, `Double`, [Txt] or a blob `ByteArray`).
 */
internal class TableDef(val table: String, val label: String, val columns: List<Col>) {
    val insertSql: String = "INSERT INTO $table (${columns.joinToString(", ") { it.name }}) VALUES " +
        "(${columns.joinToString(", ") { if (it.kind == Kind.TEXT) "CAST(? AS TEXT)" else "?" }})"

    /**
     * Converts a legacy [row] to cells, with [overrides] replacing the columns the caller derives itself. Returns null
     * (counted in [drops]) when the row cannot be written: a missing required id, an integer that does not fit Android's
     * 32-bit column (the Swift exporter would trap or fail there) or a non-finite number. A present value of an
     * unexpected type is counted and treated as absent.
     */
    fun fromLegacy(row: LegacyRow, drops: Drops, overrides: Map<String, Any?> = emptyMap()): List<Any?>? {
        val cells = ArrayList<Any?>(columns.size)
        for (col in columns) {
            if (col.name in overrides) { cells += overrides.getValue(col.name); continue }
            val raw = col.source?.let { row.values[it] }
            val present = raw?.takeIf { matches(col.kind, it).also { ok -> if (!ok) drops.add("unexpected value type ${raw::class.simpleName} for ${col.source}") } }
            val cell: Any? = when (col.kind) {
                Kind.ID -> (present as LegacyValue.UuidV?)?.let { IdType.fromString(it.value).toByteArray() }
                    ?: if (col.nullable) null else { drops.add("$label row without ${col.name}"); return null }
                Kind.TEXT -> (present as LegacyValue.TextV?)?.let { Txt(it.value) } ?: if (col.nullable) null else Txt(col.default as String? ?: "")
                Kind.INT32 -> (present as LegacyValue.IntV?)?.value?.also {
                    if (it !in Int.MIN_VALUE..Int.MAX_VALUE) { drops.add("$label value out of 32-bit range"); return null }
                } ?: if (col.nullable) null else col.default as Long? ?: 0L
                Kind.INT64 -> (present as LegacyValue.IntV?)?.value ?: if (col.nullable) null else col.default as Long? ?: 0L
                Kind.DATE -> (present as LegacyValue.DateV?)?.epochMillis ?: if (col.nullable) null else 0L
                Kind.REAL -> (present as LegacyValue.RealV?)?.value?.also {
                    if (!it.isFinite()) { drops.add("$label value is not a finite number"); return null }
                } ?: if (col.nullable) null else col.default as Double? ?: 0.0
                Kind.BOOL -> (present as LegacyValue.BoolV?)?.value ?: if (col.nullable) null else col.default as Boolean? ?: false
            }
            cells += if (col.kind == Kind.BOOL && cell is Boolean) (if (cell) 1L else 0L) else cell
        }
        return cells
    }

    private fun matches(kind: Kind, v: LegacyValue) = when (kind) {
        Kind.ID -> v is LegacyValue.UuidV
        Kind.TEXT -> v is LegacyValue.TextV
        Kind.INT32, Kind.INT64 -> v is LegacyValue.IntV
        Kind.DATE -> v is LegacyValue.DateV
        Kind.REAL -> v is LegacyValue.RealV
        Kind.BOOL -> v is LegacyValue.BoolV
    }
}

internal fun SQLiteStatement.bindCells(cells: List<Any?>) {
    cells.forEachIndexed { index, cell ->
        val i = index + 1
        when (cell) {
            null -> bindNull(i)
            is Long -> bindLong(i, cell)
            is Double -> bindDouble(i, cell)
            is Txt -> bindBlob(i, cell.bytes)
            is ByteArray -> bindBlob(i, cell)
            else -> error("unsupported cell ${cell::class}")
        }
    }
}

/** The raw legacy values a sort needs (Swift sorts the model objects before projecting them). */
internal fun LegacyRow.long(key: String): Long = (values[key] as? LegacyValue.IntV)?.value ?: 0L
internal fun LegacyRow.text(key: String): String = (values[key] as? LegacyValue.TextV)?.value ?: ""

/** Swift's `uuidString` (upper case) of a UUID property; empty when absent. */
internal fun LegacyRow.uuidString(key: String): String = (values[key] as? LegacyValue.UuidV)?.value?.uppercase() ?: ""

