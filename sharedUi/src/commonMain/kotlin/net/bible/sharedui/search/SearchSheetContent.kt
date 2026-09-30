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

package net.bible.sharedui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.strings.LocalStrings

/**
 * What goes **inside** the reading view's search sheet: a header row (result count on the left, a
 * type-specific [actions] slot on the right) and then, depending on state, the loading indicator,
 * the empty-list message, or the result list.
 *
 * Type-agnostic on purpose — the Bible and EPUB result lists differ in their rows and their
 * actions, not in this chrome. State comes in as plain parameters rather than as a `ResultsUiState`
 * so `:sharedUi` needs no `:sharedCore` results type.
 *
 * Deliberately **not** a scaffold and deliberately **without a drag handle**:
 * - the sheet's own chrome (the handle, the peek height, the drag) belongs to the host's
 *   `BottomSheetScaffold`;
 * - `AbTopAppBar` would be wrong for the header, because it calls
 *   `SyncSystemBars(container, fillWindowBackground = true)` and applies Material's real
 *   system-bar insets — both assumptions hold only for its usual `ActivityBase` host, and neither
 *   holds inside the reading view: `SyncSystemBars` would repaint the reading view's own system
 *   bars in the header's colour, and the real inset padding would double-inset a header that
 *   already sits inside the host scaffold's own inset-aware layout.
 *
 * @param countLabel the already-formatted result count (the caller owns the wording). Not shown
 *   while [loading]: a count next to the spinner reads as a finished result (F91).
 * @param empty passed in rather than derived from the list, because "no rows" and "nothing searched
 *   yet" are different states to the caller.
 * @param listState hoisted so the host can restore the scroll position across a search (F25).
 * @param error non-null shows a dialog **over a sheet that stays open**: unlike the old
 *   `SearchResultsComposeActivity`, which toasted and then `finish()`ed, there is nothing to finish.
 */
@Composable
fun SearchSheetContent(
    countLabel: String,
    loading: Boolean,
    error: String?,
    empty: Boolean,
    listState: LazyListState,
    onDismissError: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    listContent: LazyListScope.() -> Unit,
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (loading) "" else countLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            actions()
        }
        when {
            loading -> AbLoadingIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            empty -> Text(
                strings.emptyList,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                textAlign = TextAlign.Center,
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = listContent,
            )
        }
    }
    if (error != null) AbErrorDialog(error, strings.okay, onDismissError)
}
