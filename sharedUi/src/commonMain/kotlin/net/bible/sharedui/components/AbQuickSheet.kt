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

package net.bible.sharedui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings

/**
 * The reading view's quick-sheet shell: header, optional tabs, a bounded+faded scroll region and an
 * optional fixed footer row. Round 15b, spec §4.2.
 *
 * WHY A FOURTH SHELL. Round 14a's `AbChoiceSheet`/`AbMultiSelectSheet`/`AbActionSheet` are
 * dialog-shaped — a title, a list, a dismiss. None supports a back affordance, header actions, tabs
 * or a footer, and all four of round 15b's surfaces need some of those.
 *
 * THE BACK CONTRACT, which is this shell's reason to exist. Material3 runs `hide()` BEFORE it
 * invokes `onDismissRequest`, so a sheet with an internal page stack cannot distinguish "pop a page"
 * from "close me" at the M3 level. `SpeakSettingsSheet.kt:111` resolves that by re-showing a sheet
 * that M3 has already hidden, which is the most fragile part of that file and has no automated
 * coverage. Here the decision is made BEFORE M3 hides anything: a dismiss request with
 * [canGoBack] == true is routed to [onBack] and the sheet is never hidden. Consequently there is no
 * `LaunchedEffect(sheetState.isVisible)` in this file and none must be added.
 *
 * ROBORAZZI: never capture this composable — an open `ModalBottomSheet` hangs the capture and takes
 * the whole `:app` suite with it. Capture [AbQuickSheetContent] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbQuickSheet(
    open: Boolean,
    title: String,
    onDismiss: () -> Unit,
    canGoBack: () -> Boolean = { false },
    onBack: () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
    tabs: List<String> = emptyList(),
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    canScrollForward: () -> Boolean = { false },
    body: @Composable () -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        // Swipe, scrim tap and back all arrive here, and M3 cannot tell them apart. When the body
        // has somewhere to go back to, that is what a dismiss means; only an empty stack closes.
        onDismissRequest = { if (canGoBack()) onBack() else onDismiss() },
        sheetState = sheetState,
    ) {
        AbQuickSheetContent(
            title = title,
            onClose = onDismiss,
            canGoBack = canGoBack,
            onBack = onBack,
            actions = actions,
            tabs = tabs,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            footer = footer,
            canScrollForward = canScrollForward,
            body = body,
        )
    }
}

/**
 * [AbQuickSheet]'s body. Stateless and free of any sheet container, which is what makes it
 * capturable — and what `SettingsEditorSheetGuardTest` requires of every sheet body.
 */
@Composable
fun AbQuickSheetContent(
    title: String,
    onClose: () -> Unit,
    canGoBack: () -> Boolean = { false },
    onBack: () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
    tabs: List<String> = emptyList(),
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    canScrollForward: () -> Boolean = { false },
    body: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        QuickSheetHeader(title, canGoBack, onBack, actions, onClose)
        if (tabs.isNotEmpty()) {
            // Tap only, deliberately: a HorizontalPager would add a third gesture competing with
            // the sheet's vertical drag and the list's vertical scroll (spec §4.2).
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { onTabSelected(index) },
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        AbSheetScrollBound(canScrollForward = canScrollForward) { body() }
        if (footer != null) {
            HorizontalDivider()
            footer()
        }
    }
}

@Composable
private fun QuickSheetHeader(
    title: String,
    canGoBack: () -> Boolean,
    onBack: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
    onClose: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack()) {
            IconButton(onClick = onBack) {
                // contentDescription = null matches AbScaffold.kt:151-157 and AbTopAppBar's own
                // back arrow; do not invent a string for it here.
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        if (actions != null) actions()
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = strings.settingsEditorClose)
        }
    }
}

/**
 * The fixed footer row: the single affordance by which a quick sheet reaches its full screen.
 * Outside the scroll region on purpose, so it is reachable from every tab and frames the scroll
 * area's bottom (the `SheetConfirmRow` idiom, `SettingsEditorSheet.kt:118`).
 */
@Composable
fun AbQuickSheetFooterRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}
