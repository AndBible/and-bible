package net.bible.service.legacymigration.read

/** A decoded leaf value of a legacy SwiftData row. A SQL NULL is represented by a missing/`null` map value, never by a [LegacyValue]. */
sealed interface LegacyValue {
    data class IntV(val value: Long) : LegacyValue
    data class RealV(val value: Double) : LegacyValue
    data class TextV(val value: String) : LegacyValue
    data class BoolV(val value: Boolean) : LegacyValue

    /** A date as milliseconds since the Unix epoch. */
    data class DateV(val epochMillis: Long) : LegacyValue

    /** A UUID in lowercase canonical form. */
    data class UuidV(val value: String) : LegacyValue

    /** The resolved root of an `NSKeyedArchiver` blob (see `KeyedArchive.decode`). */
    data class ArchiveV(val value: Any?) : LegacyValue

    /** A UTF-8 JSON document, undecoded. */
    data class JsonV(val value: String) : LegacyValue

    class BytesV(val value: ByteArray) : LegacyValue {
        override fun equals(other: Any?) = other is BytesV && value.contentEquals(other.value)
        override fun hashCode() = value.contentHashCode()
    }
}

/**
 * One row of an entity table.
 *
 * @property pk the Core Data primary key (`Z_PK`)
 * @property values property path to decoded value (`null` for SQL NULL)
 * @property toOne to-one relationship name to the destination's `Z_PK` (`null` when unset)
 */
data class LegacyRow(val pk: Long, val values: Map<String, LegacyValue?>, val toOne: Map<String, Long?>)
