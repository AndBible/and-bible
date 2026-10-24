package net.bible.service.legacymigration.read

/** The two stores of the native Swift app: the synced [cloud] store (`AndBible.store`) and the device-[local] one (`LocalStore.store`). */
class LegacySnapshot(val cloud: LegacyStoreReader, val local: LegacyStoreReader) : AutoCloseable {
    /**
     * `ZSETTING` key to value from the [local] store only: `Setting` lives in `LocalStore` and the cloud store's
     * table is always empty. Rows with a NULL key are skipped; a NULL value maps to the empty string.
     */
    val settings: Map<String, String> by lazy {
        buildMap {
            for (row in local.rows("Setting")) {
                val key = (row.values["key"] as? LegacyValue.TextV)?.value ?: continue
                put(key, (row.values["value"] as? LegacyValue.TextV)?.value ?: "")
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
