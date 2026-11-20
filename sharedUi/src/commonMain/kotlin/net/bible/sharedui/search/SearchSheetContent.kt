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

import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.reading.readingShortcut
import net.bible.sharedui.reading.searchFieldUsesCommandKeys
import net.bible.sharedui.reading.isHardwareKeyEvent
import net.bible.sharedui.reading.searchResultKeyCharacter

/**
 * What goes **inside** the reading view's search sheet: a header row (result count on the left, a
 * type-specific [actions] slot and an accessible close button on the right) and then the loading indicator,
 * the empty-list message, or the result list.
 *
 * Type-agnostic on purpose — the Bible and EPUB result lists differ in their rows and their
 * actions, not in this chrome. State comes in as plain parameters rather than as a `ResultsUiState`
 * so `:sharedUi` needs no `:sharedCore` results type.
 *
 * Deliberately **not** a scaffold and deliberately **without a drag handle**:
 * - the sheet's peek height and swipe dismissal belong to the host's
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
 * @param notice a one-line note above the results (a regex search stopped at its time budget); not shown
 *   while [loading].
 * @param onClose hides only the result sheet; it must not leave the toolbar's search mode.
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
    onClose: () -> Unit,
    resultCount: Int = 0,
    notice: String? = null,
    requestResultsFocus: Boolean = true,
    selection: MutableState<Int>? = null,
    onOpenResult: (Int) -> Unit = {},
    onEditQuery: (String?) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    listContent: LazyListScope.(Int) -> Unit,
) {
    val strings = LocalStrings.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val localSelection = remember(listState) { mutableStateOf(listState.firstVisibleItemIndex) }
    var selected by (selection ?: localSelection)
    var listFocused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    fun keyboardUser() = inputModeManager.inputMode == InputMode.Keyboard || FindKeyboardInput.lastWasHardwareKey
    val keyboardMode = keyboardUser()
    val hasResults = requestResultsFocus && !loading && !empty && resultCount > 0
    // Once per shown result set (a new search passes through loading; reopening recomposes this),
    // not on every count change, which would pull focus back from the query while the user types.
    // Touch users keep their focus: only keyboard mode moves it into the list.
    LaunchedEffect(hasResults, listState) {
        if (hasResults && keyboardUser()) {
            selected = selected.coerceIn(0, resultCount - 1)
            // The toolbar may release its field in the same composition as cached results appear.
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (loading) "" else countLabel,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = strings.settingsEditorClose)
            }
        }
        if (notice != null && !loading) {
            Text(
                notice,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
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
                // Results being closed (still sliding out) must neither keep nor take focus.
                modifier = Modifier.focusProperties { canFocus = requestResultsFocus }
                    .focusRequester(focusRequester).onFocusChanged { listFocused = it.isFocused }.onPreviewKeyEvent { event ->
                    // Only the list itself: a focused row (Tab into a card or its expand button) keeps its keys.
                    if (!listFocused) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && isHardwareKeyEvent(event)) FindKeyboardInput.lastWasHardwareKey = true
                    // Space is no query text: it would replace the query with a blank.
                    val character = searchResultKeyCharacter(event)?.takeIf { it.isNotBlank() }
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && resultCount > 0 -> {
                            selected = (selected + 1).coerceAtMost(resultCount - 1)
                            scope.launch { listState.scrollToItem(selected) }
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && resultCount > 0 -> {
                            if (selected == 0) onEditQuery(null) else {
                                selected--
                                scope.launch { listState.scrollToItem(selected) }
                            }
                            true
                        }
                        event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter) && resultCount > 0 -> {
                            onOpenResult(selected)
                            true
                        }
                        readingShortcut(event, iosCommandKeys = searchFieldUsesCommandKeys) == "CtrlKeyF" -> {
                            onEditQuery(null)
                            true
                        }
                        character != null -> {
                            onEditQuery(character)
                            true
                        }
                        else -> false
                    }
                }.focusable(),
                content = { listContent(if (listFocused && keyboardMode) selected else -1) },
            )
        }
    }
    if (error != null) AbErrorDialog(error, strings.okay, onDismissError)
}
