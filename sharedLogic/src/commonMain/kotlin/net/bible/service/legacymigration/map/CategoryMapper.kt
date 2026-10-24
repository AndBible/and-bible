package net.bible.service.legacymigration.map

import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.stage.StagingDatabases

/** The data groups of the legacy migration, one [CategoryMapper] each. They mirror the Android backup categories. */
enum class LegacyCategory {
    WORKSPACES,
    BOOKMARKS,
    READING_PLANS,
    PROGRESS,
    MY_DOCUMENTS,
    AI_SETTINGS,
    SETTINGS,
    REPOSITORIES,
}

/**
 * Maps one [LegacyCategory] of the legacy stores in a [LegacySnapshot] into the current-schema databases of
 * [StagingDatabases]. A mapper reads only through the snapshot and writes only through Room DAOs; what it wrote or
 * deliberately left out goes into the [MigrationReport].
 */
interface CategoryMapper {
    val category: LegacyCategory

    suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport)
}
