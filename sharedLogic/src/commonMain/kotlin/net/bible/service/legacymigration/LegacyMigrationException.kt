package net.bible.service.legacymigration

/** Failure of a legacy (native Swift app) migration step; [kind] tells the caller how to react. */
class LegacyMigrationException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind {
        /** The store's model fingerprint matches no known schema map. */
        UNKNOWN_MODEL,

        /** The file is not a readable, intact SQLite/Core Data store. */
        CORRUPT_STORE,

        /** A column holds a blob in an encoding the decoders do not understand. */
        UNSUPPORTED_ENCODING,

        /** An expected store file is missing. */
        MISSING_STORE,

        /** The mapped data failed validation. */
        VALIDATION_FAILED,

        /** Adopting the mapped data into the new databases failed. */
        ADOPTION_FAILED,
    }
}
