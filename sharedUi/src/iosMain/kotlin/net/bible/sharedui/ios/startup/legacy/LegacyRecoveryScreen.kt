package net.bible.sharedui.ios.startup.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.bible.service.legacymigration.control.LegacyRecoveryModel
import net.bible.service.legacymigration.control.RecoveryReason
import net.bible.sharedui.strings.LocalStrings

/**
 * Shown when the legacy migration is blocked. Database-free; the caller wraps it in `AbTheme`. Buttons the [model]
 * forbids are absent, not disabled. "Start without old data" asks for confirmation first. "Try again" does not re-run
 * anything here: [onTryAgain] (which clears the attempt counter when [LegacyRecoveryModel.tryAgainResetsAttempts]) is
 * followed by a hint to close and reopen the app.
 */
@Composable
internal fun LegacyRecoveryScreen(
    model: LegacyRecoveryModel,
    onSaveOldData: () -> Unit,
    onReport: () -> Unit,
    onTryAgain: () -> Unit,
    onRestoreBackup: () -> Unit,
    onStartFresh: () -> Unit,
) {
    val strings = LocalStrings.current
    var confirmFresh by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    val message = when (model.reason) {
        RecoveryReason.KEYCHAIN_UNAVAILABLE -> strings.legacyMigrationFailedKeychain
        RecoveryReason.ROLLBACK_PENDING -> strings.legacyMigrationRollbackPending
        else -> strings.legacyMigrationFailedMessage
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .testTag("legacy-recovery").verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(strings.legacyMigrationFailedTitle, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(message, color = MaterialTheme.colorScheme.onBackground)
        Button(onClick = onSaveOldData, modifier = Modifier.testTag("legacy-save")) { Text(strings.legacyMigrationSaveOldData) }
        Button(onClick = onReport, modifier = Modifier.testTag("legacy-report")) { Text(strings.legacyMigrationSendReport) }
        Button(
            onClick = {
                if (model.tryAgainResetsAttempts) onTryAgain()
                showHint = true
            },
            modifier = Modifier.testTag("legacy-try-again"),
        ) { Text(strings.legacyMigrationTryAgain) }
        if (showHint) {
            Text(strings.legacyMigrationTryAgainHint, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.testTag("legacy-try-again-hint"))
        }
        if (model.canRestoreBackup) {
            OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.testTag("legacy-restore")) { Text(strings.legacyMigrationRestoreBackup) }
        }
        if (model.canStartFresh) {
            OutlinedButton(onClick = { confirmFresh = true }, modifier = Modifier.testTag("legacy-start-fresh")) {
                Text(strings.legacyMigrationStartFresh)
            }
        }
    }
    if (confirmFresh) {
        AlertDialog(
            onDismissRequest = { confirmFresh = false },
            text = { Text(strings.legacyMigrationStartFreshConfirm) },
            confirmButton = {
                TextButton(onClick = { confirmFresh = false; onStartFresh() }, modifier = Modifier.testTag("legacy-start-fresh-confirm")) {
                    Text(strings.legacyMigrationStartFresh)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFresh = false }, modifier = Modifier.testTag("legacy-start-fresh-cancel")) {
                    Text(strings.cancel)
                }
            },
        )
    }
}
