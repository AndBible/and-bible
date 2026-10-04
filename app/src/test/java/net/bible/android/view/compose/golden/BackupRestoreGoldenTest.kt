package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.backup.BackupFileRow
import net.bible.sharedcore.backup.BackupState
import net.bible.sharedcore.backup.CrashInfo
import net.bible.sharedcore.backup.ResetDbRow
import net.bible.sharedcore.backup.ToggleKind
import net.bible.sharedui.backup.BackupRestoreScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BackupRestoreGoldenTest {

    private val files = listOf(
        BackupFileRow(token = "backup-2026-07-24.zip", displayDate = "2026-07-24 08:00", detail = "App v123, 4 MB"),
        BackupFileRow(token = "backup-2026-07-17.zip", displayDate = "2026-07-17 08:00", detail = "App v122, 3 MB"),
    )

    private val resettableDbs = listOf(
        ResetDbRow(dbFileName = "bookmarks.sqlite3", title = "Bookmarks"),
        ResetDbRow(dbFileName = "workspaces.sqlite3", title = "Workspaces"),
        ResetDbRow(dbFileName = "readingplans.sqlite3", title = "Reading plans"),
    )

    private fun state(
        backupFiles: List<BackupFileRow> = emptyList(),
        resettableDbs: List<ResetDbRow> = emptyList(),
        crash: CrashInfo? = null,
    ) = BackupState(
        toggles = mapOf(
            ToggleKind.BackupDatabase to true,
            ToggleKind.BackupDocuments to false,
            ToggleKind.BackupApp to false,
            ToggleKind.RestoreDatabase to true,
            ToggleKind.RestoreDocuments to false,
        ),
        backupFiles = backupFiles,
        resettableDbs = resettableDbs,
        crash = crash,
    )

    @Composable
    private fun screen(s: BackupState) = BackupRestoreScreen(
        state = s,
        onToggle = { _, _ -> },
        onBackup = {},
        onRestore = {},
        onExportFile = {},
        onRestoreFile = {},
        onResetDb = {},
        onUp = {},
    )

    // Primary state: populated backup-file list + reset-db list, no crash.
    private fun withFilesState() = state(backupFiles = files, resettableDbs = resettableDbs)

    @Test fun backup_withFiles() =
        captureMatrix("BackupRestore", "withFiles", heightDp = 2400) { screen(withFilesState()) }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun backup_withFiles_rtl() =
        captureRtl("BackupRestore", "withFiles", heightDp = 2400) { screen(withFilesState()) }

    // Edge state: no backup files, no resettable dbs, no crash -- only toggles + buttons render
    // (exercises the "hidden when empty" behaviour of both the file-list and reset sections).
    @Test fun backup_empty() =
        captureGolden("BackupRestore", "empty", EDGE_MODE, heightDp = 1000) { screen(state()) }

    // Edge state: last-crash panel visible alongside the primary populated sections.
    @Test fun backup_withCrash() =
        captureGolden("BackupRestore", "withCrash", EDGE_MODE, heightDp = 2400) {
            screen(
                state(
                    backupFiles = files,
                    resettableDbs = resettableDbs,
                    crash = CrashInfo(time = "2026-07-20 09:15:00", text = "java.lang.RuntimeException: boom\n\tat net.bible.Foo.bar(Foo.kt:42)"),
                ),
            )
        }
}
