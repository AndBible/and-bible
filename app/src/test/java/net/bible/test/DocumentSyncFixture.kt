package net.bible.test

import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.sharedcore.platform.OrderedLauncher

internal fun DocumentSyncSettings.fixtureLauncher(): OrderedLauncher =
    javaClass.getDeclaredField("launcher").apply { isAccessible = true }.get(this) as OrderedLauncher

/** Await persistence submitted by this settings instance, not a later fixture binding. */
suspend fun DocumentSyncSettings.awaitFixtureWrites() {
    fixtureLauncher().join("document-sync-settings")
}
