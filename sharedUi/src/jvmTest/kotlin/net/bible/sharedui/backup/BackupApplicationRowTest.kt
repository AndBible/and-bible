package net.bible.sharedui.backup

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.backup.BackupState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test

/** The Application (.apk) row exists only where an APK can be backed up (#4023). */
@OptIn(ExperimentalTestApi::class)
class BackupApplicationRowTest {
    private val strings = iosStrings("en")

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: BackupState) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                BackupRestoreScreen(state, { _, _ -> }, {}, {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun rowShownWhenApkBackupAvailable() = runComposeUiTest {
        show(BackupState(appBackupAvailable = true))
        onNodeWithText(strings.backupApplicationLabel).assertExists()
    }

    @Test fun rowHiddenWhenNoApk() = runComposeUiTest {
        show(BackupState(appBackupAvailable = false))
        onNodeWithText(strings.backupApplicationLabel).assertDoesNotExist()
        onAllNodesWithText(strings.backupDocumentsLabel).assertCountEquals(2) // backup + restore rows still there
    }
}
