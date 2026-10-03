/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.backup.BackupFileRow
import net.bible.sharedcore.backup.BackupState
import net.bible.sharedcore.backup.ResetDbRow
import net.bible.sharedcore.backup.ToggleKind
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.components.volumeVerticalScroll

/**
 * Stateless port of classic `BackupActivity` (`backup_view.xml` + `backup_file_list_item.xml`):
 * backup/restore toggles + action buttons, the auto-backup file list, the per-database reset
 * buttons, and the last-crash panel. Driven purely by [state]; every action is a host seam.
 *
 * **Toggles are independent switches, not mutually-exclusive radio groups.** Classic groups
 * `BackupApp`/`BackupDatabase`/`BackupDocuments` (and separately `RestoreDatabase`/`RestoreDocuments`)
 * in an Android `RadioGroup` so only one backup/restore kind is selected at a time. The ported
 * [BackupState.toggles] is a flat `Map<ToggleKind, Boolean>` and `BackupController.setToggle`
 * (`:sharedCore`, Task 4) flips exactly the given [ToggleKind] with no sibling bookkeeping -- so
 * this screen renders all five as independent M3 [AbSwitchRow]s. This is a deliberate model
 * simplification decided at the controller layer, not an oversight here.
 *
 * The backup-file and reset-db sections are each hidden when their list is empty (no dangling
 * section heading with zero rows). The crash panel appears only when [BackupState.crash] is set.
 */
@Composable
fun BackupRestoreScreen(
    state: BackupState,
    onToggle: (ToggleKind, Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExportFile: (String) -> Unit,
    onRestoreFile: (String) -> Unit,
    onResetDb: (String) -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    AbScaffold(title = strings.backupAndRestoreTitle, onNavigateUp = onUp) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .volumeVerticalScroll(rememberScrollState()),
        ) {
            SectionHeading(strings.backupAndRestoreTitle)
            AbSwitchRow(
                label = strings.backupDatabaseLabel,
                summary = strings.backupDatabaseInfo,
                checked = state.toggles[ToggleKind.BackupDatabase] ?: false,
                onCheckedChange = { onToggle(ToggleKind.BackupDatabase, it) },
            )
            AbSwitchRow(
                label = strings.backupDocumentsLabel,
                summary = strings.backupDocumentsInfo,
                checked = state.toggles[ToggleKind.BackupDocuments] ?: false,
                onCheckedChange = { onToggle(ToggleKind.BackupDocuments, it) },
            )
            AbSwitchRow(
                label = strings.backupApplicationLabel,
                summary = strings.backupApplicationInfo,
                checked = state.toggles[ToggleKind.BackupApp] ?: false,
                onCheckedChange = { onToggle(ToggleKind.BackupApp, it) },
            )
            Button(
                onClick = onBackup,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(strings.backupToButton) }

            SectionHeading(strings.restoreOrImportTitle)
            AbSwitchRow(
                label = strings.backupDatabaseLabel,
                summary = strings.backupDatabaseInfo,
                checked = state.toggles[ToggleKind.RestoreDatabase] ?: false,
                onCheckedChange = { onToggle(ToggleKind.RestoreDatabase, it) },
            )
            AbSwitchRow(
                label = strings.backupDocumentsLabel,
                summary = strings.backupDocumentsInfo,
                checked = state.toggles[ToggleKind.RestoreDocuments] ?: false,
                onCheckedChange = { onToggle(ToggleKind.RestoreDocuments, it) },
            )
            Button(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(strings.restoreOrImportFromButton) }

            if (state.backupFiles.isNotEmpty()) {
                SectionHeading(strings.autoBackupsTitle)
                state.backupFiles.forEach { file ->
                    BackupFileListRow(
                        file = file,
                        strings = strings,
                        onExport = { onExportFile(file.token) },
                        onRestore = { onRestoreFile(file.token) },
                    )
                    HorizontalDivider()
                }
            }

            if (state.resettableDbs.isNotEmpty()) {
                SectionHeading(strings.resetDatabasesTitle)
                Text(
                    strings.resetDatabasesDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                state.resettableDbs.forEach { db ->
                    OutlinedButton(
                        onClick = { onResetDb(db.dbFileName) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { Text(strings.resetSomething(db.title)) }
                }
            }

            val crash = state.crash
            if (crash != null) {
                SectionHeading(strings.lastCrashInfoTitle)
                // The whole screen is already vertically scrolling (verticalScroll above), so this
                // inner scroll is bounded via heightIn(max=...) -- a second unbounded verticalScroll
                // nested in the same axis would be measured with infinite height and crash.
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "${crash.time}\n\n${crash.text}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

/** M3 section label, mirrors classic's bold 20sp section heading (`backup_view.xml`). */
@Composable
private fun SectionHeading(title: String) = Text(
    text = title,
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
)

/** One row of the auto-backup file list (classic `backup_file_list_item.xml`): date + detail on
 *  the left, export + restore-from-this-file icon buttons on the right. */
@Composable
private fun BackupFileListRow(
    file: BackupFileRow,
    strings: Strings,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(file.displayDate, style = MaterialTheme.typography.bodyLarge)
            Text(file.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onExport) {
            Icon(Icons.Filled.Save, contentDescription = strings.export)
        }
        IconButton(onClick = onRestore) {
            Icon(Icons.Filled.SettingsBackupRestore, contentDescription = strings.restoreLabel)
        }
    }
}
