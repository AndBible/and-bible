package net.bible.sharedui.ios.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings

/** Safe, database-free holding screen. Report is supplied by Task 5; it does not resolve a Koin service here. */
@Composable
internal fun CrashRecoveryScreen(onReport: () -> Unit, onResolve: (StartupGateResult) -> Unit) {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .testTag("crash-recovery").verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(strings.errorOccurred, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(strings.crashRecoveryMessage, color = MaterialTheme.colorScheme.onBackground)
        Button(onClick = onReport, modifier = Modifier.testTag("crash-report")) { Text(strings.crashRecoveryReport) }
        Button(onClick = { onResolve(StartupGateResult.OpenBackup) }, modifier = Modifier.testTag("crash-backup")) {
            Text(strings.backupAndRestoreTitle)
        }
        Button(onClick = { onResolve(StartupGateResult.Continue) }, modifier = Modifier.testTag("crash-continue")) {
            Text(strings.crashRecoveryContinue)
        }
    }
}
