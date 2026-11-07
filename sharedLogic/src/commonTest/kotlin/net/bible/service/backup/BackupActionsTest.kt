package net.bible.service.backup

import kotlin.test.Test
import kotlin.test.assertEquals

/** A stored "back up the application" toggle must be inert where there is no APK (#4023). */
class BackupActionsTest {
    @Test fun appActionDroppedWhenNoApk() {
        assertEquals(
            listOf(BackupAction.Database),
            backupActionsFor(backupApp = true, backupDatabase = true, backupDocuments = false, appBackupAvailable = false),
        )
    }

    @Test fun appActionKeptWhenApkAvailable() {
        assertEquals(
            listOf(BackupAction.App, BackupAction.Database, BackupAction.Documents),
            backupActionsFor(backupApp = true, backupDatabase = true, backupDocuments = true, appBackupAvailable = true),
        )
    }

    @Test fun onlyAppCheckedWithoutApkDoesNothing() {
        assertEquals(
            emptyList(),
            backupActionsFor(backupApp = true, backupDatabase = false, backupDocuments = false, appBackupAvailable = false),
        )
    }
}
