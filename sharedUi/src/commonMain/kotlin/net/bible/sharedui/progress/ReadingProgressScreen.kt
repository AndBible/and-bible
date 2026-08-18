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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.progress.ReadingProgressModel
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * The tabbed reading-progress screen (mirrors classic `ReadingProgressActivity` / `reading_progress.xml`).
 * Fully stateless: every value comes from [model], every mutation is a callback back to the host.
 * The Reading tab ([ReadingTabBody]) is built here; the Memorize tab is rendered by the host-supplied
 * [memorizeTabContent] seam (the host wires in [net.bible.sharedui.progress.MemorizeTabBody]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingProgressScreen(
    model: ReadingProgressModel,
    loading: Boolean,
    onUp: () -> Unit,
    onSelectTab: (ReadingTab) -> Unit,
    onPrevCycle: () -> Unit,
    onNextCycle: () -> Unit,
    onNewCycle: () -> Unit,
    onBookClick: (bookId: String) -> Unit,
    onBookLongClick: (bookId: String) -> Unit,
    onChapterClick: (chapter: Int) -> Unit,
    onChapterLongClick: (chapter: Int) -> Unit,
    onCalendarDayClick: (dayTimestamp: Long) -> Unit,
    onOpenSettings: () -> Unit,
    onShowHelp: () -> Unit,
    memorizeTabContent: @Composable () -> Unit = { /* host supplies MemorizeTabBody */ },
) {
    val strings = LocalStrings.current

    AbScaffold(
        title = strings.readingProgressTitle,
        onNavigateUp = onUp,
        actions = {
            AbOverflowMenu(contentDescription = null) { close ->
                AbMenuItem(
                    text = strings.readingProgressSettings,
                    onClick = { close(); onOpenSettings() },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                )
                AbMenuItem(
                    text = strings.help,
                    onClick = { close(); onShowHelp() },
                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = model.tab.ordinal) {
                Tab(
                    selected = model.tab == ReadingTab.READING,
                    onClick = { onSelectTab(ReadingTab.READING) },
                    text = { Text(strings.memorizeTabReading) },
                )
                Tab(
                    selected = model.tab == ReadingTab.MEMORIZE,
                    onClick = { onSelectTab(ReadingTab.MEMORIZE) },
                    text = { Text(strings.memorizeTabMemorization) },
                )
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when (model.tab) {
                ReadingTab.READING -> ReadingTabBody(
                    model = model,
                    strings = strings,
                    onPrevCycle = onPrevCycle,
                    onNextCycle = onNextCycle,
                    onNewCycle = onNewCycle,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    onChapterClick = onChapterClick,
                    onChapterLongClick = onChapterLongClick,
                    onCalendarDayClick = onCalendarDayClick,
                )
                ReadingTab.MEMORIZE -> memorizeTabContent()
            }
        }
    }
}

@Composable
private fun ReadingTabBody(
    model: ReadingProgressModel,
    strings: Strings,
    onPrevCycle: () -> Unit,
    onNextCycle: () -> Unit,
    onNewCycle: () -> Unit,
    onBookClick: (bookId: String) -> Unit,
    onBookLongClick: (bookId: String) -> Unit,
    onChapterClick: (chapter: Int) -> Unit,
    onChapterLongClick: (chapter: Int) -> Unit,
    onCalendarDayClick: (dayTimestamp: Long) -> Unit,
) {
    var confirmNewCycle by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 1. Summary row.
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryStat(
                modifier = Modifier.weight(1f),
                value = "${model.summary.chaptersRead}",
                label = strings.readingProgressChaptersRead,
            )
            SummaryStat(
                modifier = Modifier.weight(1f),
                value = "${model.summary.activeDays}",
                label = strings.readingProgressActiveDays,
            )
        }
        Spacer(Modifier.height(16.dp))

        // 2. Overall bar.
        LinearProgressIndicator(
            progress = { model.summary.overallPermille / 1000f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = strings.readingProgressOverall(model.summary.overallPercent),
            // Classic: overallProgressLabel is 12sp and gravity=center under the bar
            // (res/layout/reading_progress.xml:86-93). Without an explicit style this inherited
            // bodyLarge (16sp) and TextAlign.Start.
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // 3. Bible overview heading + percent-read scale legend.
        Text(
            text = strings.readingProgressBibleHeatmap,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        AbColorScaleLegend(
            label = strings.readingProgressPercentReadScale,
            steps = model.bookPercentScaleSteps,
            stepColor = { bookProgressColors(it / 100f, model.bookPercentScaleMax).background },
            stepLabel = { strings.readingProgressPercentLabel(it) },
        )
        Spacer(Modifier.height(8.dp))

        // 4. OT / NT book heat grids.
        Text(text = strings.readingProgressOldTestament, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        BookHeatGrid(
            books = model.otBooks,
            colors = { bookProgressColors(it.readPercent, model.bookPercentScaleMax) },
            onClick = onBookClick,
            onLongClick = onBookLongClick,
        )
        Spacer(Modifier.height(12.dp))
        Text(text = strings.readingProgressNewTestament, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        BookHeatGrid(
            books = model.ntBooks,
            colors = { bookProgressColors(it.readPercent, model.bookPercentScaleMax) },
            onClick = onBookClick,
            onLongClick = onBookLongClick,
        )
        Spacer(Modifier.height(16.dp))

        // 5. Chapter detail (only when a book is selected).
        val detail = model.chapterDetail
        if (detail != null) {
            Text(text = detail.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            AbColorScaleLegend(
                label = strings.readingProgressReadCountScale,
                steps = detail.countScaleSteps,
                stepColor = { countHeatColors(it, detail.maxCount).background },
                stepLabel = { "$it" },
            )
            Spacer(Modifier.height(8.dp))
            ChapterHeatGrid(
                chapters = detail.chapters,
                colors = { countHeatColors(it.count, detail.maxCount) },
                onClick = onChapterClick,
                onLongClick = onChapterLongClick,
            )
            Spacer(Modifier.height(16.dp))
        }

        // 6. Calendar heading + heatmap.
        Text(text = strings.readingProgressCalendar, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        AbCalendarHeatmap(heatmap = model.calendar, onDayClick = onCalendarDayClick)
        Spacer(Modifier.height(16.dp))

        // 7. Cycle row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevCycle, enabled = model.canPrevCycle) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = strings.readingProgressPreviousCycle,
                )
            }
            Text(
                text = strings.readingProgressCycle(model.cycle),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNextCycle, enabled = model.canNextCycle) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = strings.readingProgressNextCycle,
                )
            }
            if (model.showNewCycle) {
                Button(onClick = { confirmNewCycle = true }) {
                    Text(strings.readingProgressNewCycle)
                }
            }
        }
    }

    if (confirmNewCycle) {
        AbConfirmDialog(
            title = strings.readingProgressNewCycle,
            message = strings.readingProgressNewCycleConfirm,
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = { confirmNewCycle = false; onNewCycle() },
            onDismiss = { confirmNewCycle = false },
        )
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
