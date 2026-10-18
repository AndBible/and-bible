package net.bible.service.legacymigration.read

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import net.bible.service.db.openSqlite
import net.bible.service.legacymigration.LegacyMigrationException
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.plist.BinaryPlist
import net.bible.service.legacymigration.plist.KeyedArchive
import net.bible.service.legacymigration.plist.LegacyFormatException
import net.bible.service.legacymigration.plist.PlistData
import net.bible.service.legacymigration.plist.PlistDict
import net.bible.service.legacymigration.schema.EntityMap
import net.bible.service.legacymigration.schema.KnownSchemaMaps
import net.bible.service.legacymigration.schema.SchemaMap
import net.bible.service.legacymigration.schema.ValueEncoding
import net.bible.service.legacymigration.schema.modelFingerprint
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Read-only reader of one SwiftData (Core Data SQLite) store of the legacy native Swift app.
 *
 * Contract:
 * - The store is opened read-only and is never written, so a `-wal` file next to it is honoured (its content is
 *   visible) but not checkpointed; the source files stay intact.
 * - The set of readable entities and columns is closed: it is exactly the [SchemaMap] in [KnownSchemaMaps] that
 *   matches the store's model fingerprint ([modelFingerprint] over `Z_METADATA`'s `NSStoreModelVersionHashes`).
 *   Nothing is guessed from the table layout.
 * - The constructor throws [LegacyMigrationException] with [Kind.MISSING_STORE] (no such file), [Kind.CORRUPT_STORE]
 *   (SQLite cannot open it, `PRAGMA quick_check` is not `ok`, `Z_METADATA` is unreadable, a mapped table or
 *   column is missing) or [Kind.UNKNOWN_MODEL] (fingerprint not in [KnownSchemaMaps]).
 * - [rows] throws [Kind.UNSUPPORTED_ENCODING] when a blob column cannot be decoded and [Kind.CORRUPT_STORE] for
 *   any other malformed cell (for example a UUID that is not 16 bytes) or SQLite failure.
 */
class LegacyStoreReader(path: String) : AutoCloseable {
    private val db: SQLiteConnection
    val fingerprint: String
    val map: SchemaMap

    init {
        if (!FileSystem.SYSTEM.exists(path.toPath())) throw LegacyMigrationException(Kind.MISSING_STORE, "store not found: $path")
        val opened = try {
            openSqlite(path, readOnly = true)
        } catch (e: Exception) {
            throw LegacyMigrationException(Kind.CORRUPT_STORE, "cannot open store $path: ${e.message}", e)
        }
        try {
            val check = firstText(opened, "PRAGMA quick_check")
            if (check != "ok") throw LegacyMigrationException(Kind.CORRUPT_STORE, "quick_check failed for $path: $check")
            fingerprint = modelFingerprint(versionHashes(opened))
            map = KnownSchemaMaps.forFingerprint(fingerprint)
                ?: throw LegacyMigrationException(Kind.UNKNOWN_MODEL, "unknown SwiftData model $fingerprint in $path")
        } catch (e: Throwable) {
            runCatching { opened.close() }
            throw if (e is LegacyMigrationException) e else LegacyMigrationException(Kind.CORRUPT_STORE, "cannot read store $path: ${e.message}", e)
        }
        db = opened
    }

    /** Number of rows of [entity]. */
    fun count(entity: String): Int {
        val e = entity(entity)
        return sqlite("count of $entity") {
            val statement = db.prepare("SELECT COUNT(*) FROM ${e.table}")
            try {
                check(statement.step())
                statement.getLong(0).toInt()
            } finally {
                statement.close()
            }
        }
    }

    /** All rows of [entity] ordered by `Z_PK`, decoded per the schema map. */
    fun rows(entity: String): List<LegacyRow> {
        val e = entity(entity)
        val leaves = e.columns.entries.toList()
        val relations = e.toOne.entries.toList()
        val columnList = (listOf("Z_PK") + leaves.map { it.value.column } + relations.map { it.value }).joinToString(", ")
        return sqlite("rows of $entity") {
            val statement = db.prepare("SELECT $columnList FROM ${e.table} ORDER BY Z_PK")
            try {
                val result = mutableListOf<LegacyRow>()
                while (statement.step()) {
                    val pk = statement.getLong(0)
                    val values = LinkedHashMap<String, LegacyValue?>()
                    leaves.forEachIndexed { i, (property, column) ->
                        values[property] = decode(statement, 1 + i, column.encoding, "$entity.$property (Z_PK $pk)")
                    }
                    val toOne = LinkedHashMap<String, Long?>()
                    relations.forEachIndexed { i, (name, _) ->
                        val index = 1 + leaves.size + i
                        toOne[name] = if (statement.isNull(index)) null else statement.getLong(index)
                    }
                    result += LegacyRow(pk, values, toOne)
                }
                result
            } finally {
                statement.close()
            }
        }
    }

    override fun close() = db.close()

    private fun entity(name: String): EntityMap =
        map.entities[name] ?: throw IllegalArgumentException("unknown entity $name in model $fingerprint")

    private inline fun <T> sqlite(what: String, block: () -> T): T = try {
        block()
    } catch (e: LegacyMigrationException) {
        throw e
    } catch (e: Exception) {
        throw LegacyMigrationException(Kind.CORRUPT_STORE, "cannot read $what: ${e.message}", e)
    }

    private fun decode(s: SQLiteStatement, i: Int, encoding: ValueEncoding, where: String): LegacyValue? {
        if (s.isNull(i)) return null
        return when (encoding) {
            ValueEncoding.INTEGER -> LegacyValue.IntV(s.getLong(i))
            ValueEncoding.REAL -> LegacyValue.RealV(s.getDouble(i))
            ValueEncoding.TEXT -> LegacyValue.TextV(s.getText(i))
            ValueEncoding.BOOL -> LegacyValue.BoolV(s.getLong(i) != 0L)
            ValueEncoding.DATE -> LegacyValue.DateV(coreDataToEpochMillis(s.getDouble(i)))
            ValueEncoding.UUID -> LegacyValue.UuidV(uuidString(s.getBlob(i), where))
            ValueEncoding.KEYED_ARCHIVE -> try {
                LegacyValue.ArchiveV(KeyedArchive.decode(s.getBlob(i)))
            } catch (e: LegacyFormatException) {
                throw LegacyMigrationException(Kind.UNSUPPORTED_ENCODING, "undecodable keyed archive in $where: ${e.message}", e)
            }
            ValueEncoding.JSON -> LegacyValue.JsonV(
                try {
                    s.getBlob(i).decodeToString(throwOnInvalidSequence = true)
                } catch (e: CharacterCodingException) {
                    throw LegacyMigrationException(Kind.UNSUPPORTED_ENCODING, "JSON blob in $where is not UTF-8", e)
                },
            )
            ValueEncoding.BYTES -> LegacyValue.BytesV(s.getBlob(i))
        }
    }

    private companion object {
        /** Seconds between 1970-01-01 and the Core Data reference date 2001-01-01. */
        const val REFERENCE_DATE_OFFSET_SECONDS = 978_307_200L

        fun coreDataToEpochMillis(seconds: Double): Long =
            kotlin.math.round((seconds + REFERENCE_DATE_OFFSET_SECONDS) * 1000.0).toLong()

        fun uuidString(bytes: ByteArray, where: String): String {
            if (bytes.size != 16) throw LegacyMigrationException(Kind.CORRUPT_STORE, "UUID in $where has ${bytes.size} bytes, expected 16")
            val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
        }

        fun firstText(db: SQLiteConnection, sql: String): String? {
            val statement = db.prepare(sql)
            try {
                return if (statement.step()) statement.getText(0) else null
            } finally {
                statement.close()
            }
        }

        fun versionHashes(db: SQLiteConnection): Map<String, ByteArray> {
            val statement = db.prepare("SELECT Z_PLIST FROM Z_METADATA")
            val plist = try {
                if (!statement.step()) throw LegacyMigrationException(Kind.CORRUPT_STORE, "Z_METADATA is empty")
                statement.getBlob(0)
            } finally {
                statement.close()
            }
            try {
                val root = BinaryPlist.parse(plist) as? PlistDict ?: throw LegacyFormatException("Z_METADATA plist is not a dictionary")
                val hashes = root.entries["NSStoreModelVersionHashes"] as? PlistDict
                    ?: throw LegacyFormatException("Z_METADATA has no NSStoreModelVersionHashes")
                return hashes.entries.mapValues { (name, v) ->
                    (v as? PlistData)?.bytes ?: throw LegacyFormatException("version hash of $name is not data")
                }
            } catch (e: LegacyFormatException) {
                throw LegacyMigrationException(Kind.CORRUPT_STORE, "unreadable Z_METADATA: ${e.message}", e)
            }
        }
    }
}
