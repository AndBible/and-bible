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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The reading view's quick-sheet shell: header, optional tabs, a bounded+faded scroll region and an
 * optional fixed footer row. Round 15b, spec §4.2.
 *
 * WHY A FOURTH SHELL. Round 14a's `AbChoiceSheet`/`AbMultiSelectSheet`/`AbActionSheet` are
 * dialog-shaped — a title, a list, a dismiss. None supports a back affordance, header actions, tabs
 * or a footer, and all four of round 15b's surfaces need some of those.
 *
 * THE BACK CONTRACT, and why the hide-then-reshow is unavoidable here (fix round 2 correction: an
 * earlier version of this kdoc claimed the shell could decide "before M3 hides anything" — that is
 * false, and the claim below replaces it). Disassembling this project's own `material3` jar shows
 * every M3 dismiss path — `animateToDismiss` for swipe/scrim/back, `settleToDismiss` for a flung
 * swipe — runs `sheetState.hide()` **to completion** and only THEN calls [onDismiss]'s
 * `onDismissRequest`. M3 does carry its own internal effect that re-shows a sheet after such a hide,
 * but it is keyed on the `sheetState` OBJECT IDENTITY, so it never re-fires for a state this
 * composable keeps alive across recompositions — which is exactly why `SpeakSettingsSheet.kt:111`
 * has to re-show by hand, and why this shell must too. A `BackHandler` intercepting back BEFORE M3
 * ever saw it would avoid the blip, but it cannot live in commonMain — this port already records
 * that twice, at `SettingsEditorSheet.kt:64` and `SpeakSettingsSheet.kt:57` — and closing the whole
 * sheet on any gesture dismiss (the alternative considered and rejected) would make a swipe or
 * system back on a back-able page skip past the header's own back arrow and exit the sheet entirely,
 * which is worse.
 *
 * So: a dismiss request with [canGoBack] == true calls [onBack] **and** re-shows the sheet via
 * `sheetState.show()`, both from this ONE explicit place, keyed on the caller's own [canGoBack] —
 * not a per-sheet `LaunchedEffect(sheetState.isVisible)` keyed on visibility, which would also fire
 * on every REAL close and have nothing to distinguish the two. That is this shell's actual
 * contribution: one hide/show blip, decided in one place, instead of the visibility-keyed workaround
 * duplicated per sheet.
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
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        // Swipe, scrim tap and back all arrive here, and M3 cannot tell them apart. When the body
        // has somewhere to go back to, that is what a dismiss means; only an empty stack closes.
        onDismissRequest = {
            if (canGoBack()) {
                onBack()
                // M3 has ALREADY completed hide() by the time it calls us (verified against this
                // project's material3 bytecode), and its own re-show effect is keyed on the
                // sheetState identity, so it will not re-fire. Re-showing here is therefore not a
                // workaround we could design away — it is the only way a page-stack pop can keep the
                // sheet on screen. A BackHandler that intercepted back BEFORE M3 saw it would avoid
                // the hide/show blip, but it cannot live in commonMain (SettingsEditorSheet.kt:64,
                // SpeakSettingsSheet.kt:57).
                scope.launch { sheetState.show() }
            } else {
                onDismiss()
            }
        },
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
        AbSheetHeader(title = title, onClose = onClose, canGoBack = canGoBack, onBack = onBack, actions = actions)
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
