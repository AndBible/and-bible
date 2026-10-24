package net.bible.service.legacymigration.schema

import okio.ByteString.Companion.encodeUtf8
import okio.Buffer

/** How the value of a SwiftData (Core Data SQLite) column is encoded. */
enum class ValueEncoding {
    /** Int16/32/64 column (SQLite INTEGER). */
    INTEGER,

    /** Float/Double column (SQLite REAL). */
    REAL,

    /** String column. */
    TEXT,

    /** Bool column, stored as INTEGER 0/1. */
    BOOL,

    /** Date column: REAL seconds since 2001-01-01 (Core Data reference date). */
    DATE,

    /** UUID column: 16 raw bytes. */
    UUID,

    /** `NSKeyedArchiver` binary plist blob (`NSSet`/`NSArray` of transformable attributes). */
    KEYED_ARCHIVE,

    /** UTF-8 JSON blob (Swift `Codable` collections stored as binary data). */
    JSON,

    /** Opaque bytes with no known structure. */
    BYTES,
}

/** One leaf attribute of an entity: the table column that holds it and how to decode it. */
data class ColumnMap(val column: String, val encoding: ValueEncoding)

/**
 * One SwiftData entity mapped to its Core Data table.
 *
 * @property columns property path (`"textDisplaySettings.fontSize"`; composite attributes are flattened with `.`) to its column.
 *   Column names are `Z` + the upper-cased leaf name; a leaf name that occurs more than once in the entity gets
 *   the numeric suffix `1`, `2`, ... in depth-first model order (`ZDECODEDMALFORMEDKNOWNFIELD`, `...FIELD1`).
 * @property toOne to-one relationship name to the foreign key column holding the destination's `Z_PK`.
 */
data class EntityMap(
    val name: String,
    val table: String,
    val entityNumber: Int,
    val columns: Map<String, ColumnMap>,
    val toOne: Map<String, String>,
)

/** The complete mapping of one compiled SwiftData model, identified by [fingerprint] ([modelFingerprint]). */
data class SchemaMap(val fingerprint: String, val entities: Map<String, EntityMap>)

/**
 * Fingerprint of a compiled Core Data model: lower-case hex SHA-256 over the entity names in sorted order, each
 * contributing its UTF-8 name followed by its raw version-hash bytes (`NSStoreModelVersionHashes` of `Z_METADATA`).
 * The generator and the reader must use this same function.
 */
fun modelFingerprint(versionHashes: Map<String, ByteArray>): String {
    val buffer = Buffer()
    for (name in versionHashes.keys.sorted()) {
        buffer.write(name.encodeUtf8())
        buffer.write(versionHashes.getValue(name))
    }
    return buffer.readByteString().sha256().hex()
}
