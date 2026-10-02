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

package net.bible.sharedui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import net.bible.sharedui.components.volumeScrollTarget
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedcore.history.HistoryError
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings

/**
 * The History list without chrome, so the full-screen [HistoryScreen] and round 15b's quick sheet
 * render the same rows. [listState] is hoisted so a sheet host can expose `canScrollForward` to
 * `AbQuickSheet`'s bottom fade.
 */
@Composable
fun HistoryListContent(
    entries: List<HistoryEntry>,
    onSelect: (Int) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    // I2 (whole-branch review fix wave): fillMaxWidth, not fillMaxSize -- see WorkspaceQuickContent
    // for the full rationale. The one caller that needs to fill (the full-screen HistoryScreen)
    // passes fillMaxSize() explicitly in its own modifier below.
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth().volumeScrollTarget(listState)) {
        items(entries, key = { it.id }) { entry ->
            TwoLineListItem(
                title = entry.title,
                subtitle = entry.timestamp,
                onClick = { onSelect(entry.id) },
            )
        }
    }
}

@Composable
fun HistoryScreen(
    title: String,
    entries: List<HistoryEntry>,
    error: HistoryError?,
    onSelect: (Int) -> Unit,
    onDismissError: () -> Unit,
) {
    val strings = LocalStrings.current
    // Bound the presentation to a dialog-sized card (~half the available height, full width)
    // instead of filling the whole screen, so it reads as a dialog floating over the reading
    // view (context retention) rather than a full-screen list. The AbScaffold fills whatever
    // constraints it is given, so the bound must be applied here at the root, not on the
    // inner LazyColumn (which then fills the card's content region and scrolls within it).
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f)) {
        AbScaffold(title = title) { padding ->
            HistoryListContent(
                entries = entries,
                onSelect = onSelect,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
    if (error != null) {
        AbErrorDialog(
            message = strings.errorOccurred,
            confirmText = strings.okay,
            onDismiss = onDismissError,
        )
    }
}
