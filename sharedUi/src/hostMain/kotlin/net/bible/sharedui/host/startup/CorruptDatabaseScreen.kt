@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.host.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.service.db.CorruptDatabase
import net.bible.service.db.CorruptDatabaseResetException
import net.bible.service.db.CorruptDatabasesException
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.log.Log
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.AbTheme

/**
 * #4112: what desktop and iOS show when startup failed with [CorruptDatabasesException] (Android asks the same in a
 * dialog, `ErrorReportControl.showCorruptDatabaseDialog`). The databases are closed and nothing has been moved or
 * emptied. The user can send a report ([onReport], one at a time), open Backup & Restore ([backupContent], shown in
 * place of this screen with the databases closed; its back call retries the startup, which opens normally when a
 * backup was restored), or reset the damaged data ([reset], default [DatabaseContainer.resetCorruptDatabases], which
 * keeps the damaged files as `<name>.corrupt-<timestamp>`), after which [onRetry] runs the startup again. A failed reset
 * shows its message on the screen and changes nothing.
 */
@Composable
@kotlin.native.HiddenFromObjC
fun CorruptDatabaseRecovery(
    error: CorruptDatabasesException,
    dark: Boolean,
    strings: Strings,
    onReport: suspend () -> Unit,
    onRetry: () -> Unit,
    reset: suspend (List<CorruptDatabase>) -> Unit = { DatabaseContainer.resetCorruptDatabases(it) },
    backupContent: @Composable (onBack: () -> Unit) -> Unit,
) {
    var showBackup by remember(error) { mutableStateOf(false) }
    if (showBackup) {
        backupContent {
            showBackup = false
            onRetry()
        }
        return
    }
    val scope = rememberCoroutineScope()
    var busy by remember(error) { mutableStateOf(false) }
    var resetFailure by remember(error) { mutableStateOf<String?>(null) }
    fun runAction(action: suspend () -> Unit, failed: (Exception) -> Unit = {}, after: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = try {
                action()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CorruptDatabaseRecovery", "Recovery action failed", e)
                failed(e)
                false
            } finally {
                busy = false
            }
            if (ok) after()
        }
    }
    CompositionLocalProvider(LocalStrings provides strings) {
        AbTheme(darkTheme = dark) {
            CorruptDatabaseScreen(
                databases = error.databases,
                busy = busy,
                onReport = { runAction(onReport) },
                onBackup = { if (!busy) showBackup = true },
                resetFailure = resetFailure,
                onReset = {
                    runAction(
                        action = { resetFailure = null; withContext(Dispatchers.IO) { reset(error.databases) } },
                        failed = { e ->
                            val name = (e as? CorruptDatabaseResetException)?.database?.fileName
                                ?: error.databases.joinToString { it.fileName }
                            resetFailure = strings.corruptDatabaseResetFailed(name)
                        },
                        after = onRetry,
                    )
                },
            )
        }
    }
}

/**
 * The screen of [CorruptDatabaseRecovery]: the damaged [databases] by file name and the three choices. Closing the
 * app needs no button here: the desktop window closes as usual and iOS apps do not quit themselves.
 */
@Composable
internal fun CorruptDatabaseScreen(
    databases: List<CorruptDatabase>,
    busy: Boolean,
    onReport: () -> Unit,
    onBackup: () -> Unit,
    onReset: () -> Unit,
    resetFailure: String? = null,
) {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .testTag("corrupt-database").verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(strings.corruptDatabaseHeadline, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            strings.corruptDatabaseFilesDetail(databases.joinToString { it.fileName }),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (resetFailure != null) {
            Text(resetFailure, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("corrupt-database-reset-failed"))
        }
        Button(onClick = onReport, enabled = !busy, modifier = Modifier.testTag("corrupt-database-report")) {
            Text(strings.crashRecoveryReport)
        }
        Button(onClick = onBackup, enabled = !busy, modifier = Modifier.testTag("corrupt-database-backup")) {
            Text(strings.backupAndRestoreTitle)
        }
        OutlinedButton(onClick = onReset, enabled = !busy, modifier = Modifier.testTag("corrupt-database-reset")) {
            Text(strings.corruptDatabaseReset)
        }
    }
}
