package net.bible.sharedui.readingplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.readingplan.ConfirmKind
import net.bible.sharedcore.readingplan.DailyReadingUi
import net.bible.sharedcore.readingplan.ReadingItem
import net.bible.sharedcore.readingplan.ReadingPlanError
import net.bible.sharedcore.readingplan.SpeakState
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbTopAppBar
import net.bible.sharedui.strings.LocalStrings

@Composable
fun DailyReadingScreen(
    ui: DailyReadingUi,
    speakState: SpeakState,
    error: ReadingPlanError?,
    confirm: ConfirmKind?,
    onToggleRead: (Int) -> Unit,
    onRead: (Int) -> Unit,
    onSpeak: (Int) -> Unit,
    onSpeakAll: () -> Unit,
    onDone: () -> Unit,
    onPauseSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    onChangePlan: () -> Unit,
    onChangeDay: () -> Unit,
    onSetCurrentDay: () -> Unit,
    onSetStartDate: () -> Unit,
    onReset: () -> Unit,
    onImportPlan: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onDismissError: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current

    AbScaffold(
        topBar = {
            AbTopAppBar(
                title = {
                    Column {
                        Text(
                            ui.planName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable(onClick = onChangePlan),
                        )
                        Text(
                            ui.dayDesc,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable(onClick = onChangeDay),
                        )
                    }
                },
                onNavigateUp = onNavigateUp,
                actions = {
                    if (speakState != SpeakState.NONE) {
                        // Pause when speaking, resume (Play) when paused; both delegate to the host.
                        AbActionIcon(
                            icon = if (speakState == SpeakState.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (speakState == SpeakState.PAUSED) strings.speak else strings.pause,
                            onClick = onPauseSpeak,
                        )
                        AbActionIcon(Icons.Filled.Stop, contentDescription = strings.stop, onClick = onStopSpeak)
                    }
                    AbOverflowMenu(contentDescription = null) { close ->
                        if (!ui.isDateBasedPlan) {
                            DropdownMenuItem(text = { Text(strings.setCurrentDay) }, onClick = { close(); onSetCurrentDay() })
                            DropdownMenuItem(text = { Text(strings.setStartDate) }, onClick = { close(); onSetStartDate() })
                        }
                        DropdownMenuItem(text = { Text(strings.resetGeneric) }, onClick = { close(); onReset() })
                        DropdownMenuItem(text = { Text(strings.importReadingPlan) }, onClick = { close(); onImportPlan() })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                ui.dateString,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(ui.readings, key = { it.readingNo }) { item ->
                    ReadingRow(item, onToggleRead, onRead, onSpeak, strings.selectPassage, strings.speak)
                }
                if (ui.showSpeakAll) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(strings.all, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = onSpeakAll) { Text(strings.speak) }
                        }
                    }
                }
            }
            Button(
                onClick = onDone,
                enabled = ui.allRead,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(strings.done) }
        }
    }

    if (confirm != null) {
        val message = if (confirm == ConfirmKind.RESET) strings.resetPlanQuestion else strings.setCurrentDayQuestion
        AbConfirmDialog(
            title = null,
            message = message,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = onConfirm,
            onDismiss = onDismissConfirm,
        )
    }
    if (error != null) {
        AbErrorDialog(message = strings.errorOccurred, confirmText = strings.okay, onDismiss = onDismissError)
    }
}

@Composable
private fun ReadingRow(
    item: ReadingItem,
    onToggleRead: (Int) -> Unit,
    onRead: (Int) -> Unit,
    onSpeak: (Int) -> Unit,
    readLabel: String,
    speakLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = item.isRead, onCheckedChange = { onToggleRead(item.readingNo) })
        Text(item.passage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onRead(item.readingNo) }) { Text(readLabel) }
        TextButton(onClick = { onSpeak(item.readingNo) }) { Text(speakLabel) }
    }
}
