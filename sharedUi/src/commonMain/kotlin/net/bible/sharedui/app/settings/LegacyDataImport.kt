package net.bible.sharedui.app.settings

/**
 * Platform seam for the settings row "Import data from the previous app version". Bound on iOS only; Android never
 * binds it, so the row stays hidden there.
 */
interface LegacyDataImport {
    /** Whether an import can be offered now (the old app's stores exist and no real migration completed). */
    fun available(): Boolean

    /** Asks for confirmation; on yes records the import request, so the next launch runs the migration, and says so. */
    suspend fun confirmAndRequest()
}
