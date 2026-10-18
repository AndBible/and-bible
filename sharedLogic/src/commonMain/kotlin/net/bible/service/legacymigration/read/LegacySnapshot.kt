package net.bible.service.legacymigration.read

/** The two stores of the native Swift app: the synced [cloud] store (`AndBible.store`) and the device-[local] one (`LocalStore.store`). */
class LegacySnapshot(val cloud: LegacyStoreReader, val local: LegacyStoreReader) : AutoCloseable {
    /**
     * `ZSETTING` key to value over both stores; where a key exists in both, the device-local store wins.
     * Rows with a NULL key are skipped; a NULL value maps to the empty string.
     */
    val settings: Map<String, String> by lazy {
        buildMap {
            for (reader in listOf(cloud, local)) {
                for (row in reader.rows("Setting")) {
                    val key = (row.values["key"] as? LegacyValue.TextV)?.value ?: continue
                    put(key, (row.values["value"] as? LegacyValue.TextV)?.value ?: "")
                }
            }
        }
    }

    override fun close() {
        try {
            cloud.close()
        } finally {
            local.close()
        }
    }
}
