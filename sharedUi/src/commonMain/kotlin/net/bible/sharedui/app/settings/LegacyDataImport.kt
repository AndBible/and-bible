@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.settings

/**
 * Platform seam for the settings row "Import data from the previous app version". Bound on iOS only; Android never
 * binds it, so the row stays hidden there.
 */
@kotlin.native.HiddenFromObjC
interface LegacyDataImport {
    /**
     * Whether an import can be offered now (the old app's stores exist and no real migration completed). Called from the
     * settings snapshot build, so it must not do I/O: implementations answer from a cache.
     */
    fun available(): Boolean

    /** Asks for confirmation; on yes records the import request, so the next launch runs the migration, and says so. */
    suspend fun confirmAndRequest()
}
