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

package net.bible.sharedui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.progress.PassageRow
import net.bible.sharedcore.progress.ReadingProgressController
import net.bible.sharedcore.progress.ReadingProgressScale
import net.bible.sharedcore.progress.TargetRow
import net.bible.sharedcore.progress.MemorizeModel
import net.bible.sharedui.strings.LocalStrings
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Memorize-tab body (mirrors classic memorization-progress UI). Fully stateless: every value
 * comes from [memorize], every mutation is a callback back to the host. Rendered by
 * `ReadingProgressScreen`'s `memorizeTabContent` seam (the host wires this composable in).
 */
@Composable
fun MemorizeTabBody(
    memorize: MemorizeModel,
    onSetOverview: (Boolean) -> Unit,
    onBookClick: (bookId: String) -> Unit,
    onChapterClick: (chapter: Int) -> Unit,
    onCalendarDayClick: (dayTimestamp: Long) -> Unit,
    onPassageTap: (start: Int, end: Int) -> Unit,
    onPassageUnmark: (row: PassageRow) -> Unit,
    onTargetTap: (start: Int, end: Int) -> Unit,
    onTargetRemove: (row: TargetRow) -> Unit,
    onShowMorePassages: () -> Unit,
    onShowMoreTargets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 1. Summary row.
        Text(
            text = "${memorize.memorizedCount} ${strings.memorizeVersesMemorized}",
            style = MaterialTheme.typography.titleMedium,
        )
        if (memorize.targetTotal > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${memorize.targetTotal} ${strings.memorizeVersesTarget}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { memorize.targetPermille / 1000f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${memorize.targetPercent.roundToInt()}% (${memorize.targetMemorized}/${memorize.targetTotal})",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))

        // 2. Overview/List toggle.
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { onSetOverview(true) }) {
                Text(
                    text = strings.memorizeViewOverview,
                    fontWeight = if (memorize.overviewActive) FontWeight.Bold else FontWeight.Normal,
                )
            }
            TextButton(onClick = { onSetOverview(false) }) {
                Text(
                    text = strings.memorizeViewList,
                    fontWeight = if (!memorize.overviewActive) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (memorize.overviewActive) {
            MemorizeOverview(
                memorize = memorize,
                onBookClick = onBookClick,
                onChapterClick = onChapterClick,
                onCalendarDayClick = onCalendarDayClick,
            )
        } else {
            MemorizeList(
                memorize = memorize,
                onPassageTap = onPassageTap,
                onPassageUnmark = onPassageUnmark,
                onTargetTap = onTargetTap,
                onTargetRemove = onTargetRemove,
                onShowMorePassages = onShowMorePassages,
                onShowMoreTargets = onShowMoreTargets,
            )
        }
    }
}

@Composable
private fun MemorizeOverview(
    memorize: MemorizeModel,
    onBookClick: (bookId: String) -> Unit,
    onChapterClick: (chapter: Int) -> Unit,
    onCalendarDayClick: (dayTimestamp: Long) -> Unit,
) {
    val strings = LocalStrings.current

    BookHeatGrid(
        books = memorize.otBooks,
        color = { memorizationColor(ReadingProgressScale.memorizationLevel(it.readPercent)) },
        onClick = onBookClick,
    )
    Spacer(Modifier.height(12.dp))
    BookHeatGrid(
        books = memorize.ntBooks,
        color = { memorizationColor(ReadingProgressScale.memorizationLevel(it.readPercent)) },
        onClick = onBookClick,
    )
    Spacer(Modifier.height(16.dp))

    val detail = memorize.memChapterDetail
    if (detail != null) {
        Text(text = detail.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        ChapterHeatGrid(
            chapters = detail.chapters,
            color = { memorizationColor(it.level) },
            onClick = onChapterClick,
        )
        Spacer(Modifier.height(16.dp))
    }

    Text(text = strings.memorizeCalendar, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    AbCalendarHeatmap(heatmap = memorize.calendar, onDayClick = onCalendarDayClick)
}

@Composable
private fun MemorizeList(
    memorize: MemorizeModel,
    onPassageTap: (start: Int, end: Int) -> Unit,
    onPassageUnmark: (row: PassageRow) -> Unit,
    onTargetTap: (start: Int, end: Int) -> Unit,
    onTargetRemove: (row: TargetRow) -> Unit,
    onShowMorePassages: () -> Unit,
    onShowMoreTargets: () -> Unit,
) {
    val strings = LocalStrings.current

    // Memorized passages section.
    Text(text = strings.memorizeMemorizedPassages, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    if (memorize.passages.isEmpty()) {
        Text(
            text = strings.memorizeNoMemorizedPassages,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    } else {
        for (row in memorize.passages) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPassageTap(row.startOrdinal, row.endOrdinal) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.rangeName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = row.relativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onPassageUnmark(row) }) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        }
        if (memorize.passagesShown < memorize.passagesTotal) {
            TextButton(onClick = onShowMorePassages) {
                Text(strings.memorizeShowMore(min(memorize.passagesTotal - memorize.passagesShown, ReadingProgressController.PAGE_SIZE)))
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    // Targets section.
    Text(text = strings.memorizeTargets, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    if (memorize.targets.isEmpty()) {
        Text(
            text = strings.memorizeNoTargets,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    } else {
        for (row in memorize.targets) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTargetTap(row.startOrdinal, row.endOrdinal) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${row.rangeName} (${row.memorized}/${row.total})",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = row.relativeTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onTargetRemove(row) }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }
                LinearProgressIndicator(
                    progress = { row.permille / 1000f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (memorize.targetsShown < memorize.targetsTotal) {
            TextButton(onClick = onShowMoreTargets) {
                Text(strings.memorizeShowMore(min(memorize.targetsTotal - memorize.targetsShown, ReadingProgressController.PAGE_SIZE)))
            }
        }
    }
}
