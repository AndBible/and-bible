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

package net.bible.sharedui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import net.bible.sharedcore.settings.AppSettingsDialog
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbLinkRouting
import net.bible.sharedui.components.AbMessageDialog
import net.bible.sharedui.strings.LocalStrings

/**
 * Thin wrapper around [AbSettingsScreen] for the main app Settings screen: passes every callback
 * straight through and supplies a single top-bar action — a "reset to defaults" button — via
 * [AbSettingsScreen]'s `actions` slot. Kept as its own composable (rather than callers using
 * [AbSettingsScreen] directly) so the reset action lives in one place and matches the classic
 * preference-screen behaviour (an app-bar reset icon on the main Settings screen only).
 *
 * Platform-dialog removal Task 15: [dialog] is [AppSettingsController]'s own confirm/help state
 * (`AlertDialog.Builder`s in classic and, until this task, in `NavHostComposeActivity` too) — [onReset]
 * now only REQUESTS the confirmation (the controller decides whether/what to show), and the discrete
 * -help row (routed to here via `onNavigate`) does the same. [onOpenLink] feeds [AbLinkRouting] for
 * the help body's inline wiki link, since this destination's ambient `LocalUriHandler` is the bare
 * platform one, not `AppDialogHost`'s `CommonUtils.openLink`-backed override.
 */
@Composable
fun AppSettingsScreen(
    state: SettingsScreenState,
    onUp: (() -> Unit)?,
    onSwitch: (String, Boolean) -> Unit,
    onListChoice: (String, String) -> Unit,
    onTextInput: (String, String) -> Unit,
    onSliderChange: (String, Int) -> Unit,
    onMultiSelectChange: (String, Set<String>) -> Unit,
    onNavigate: (String) -> Unit,
    onReset: () -> Unit,
    resetContentDescription: String,
    searchQuery: String = "",
    searchModeActive: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    dialog: AppSettingsDialog = AppSettingsDialog.None,
    onConfirmDialog: () -> Unit = {},
    onDismissDialog: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    val strings = LocalStrings.current
    val searchHint = strings.searchSettings
    AbSettingsScreen(
        state = state,
        onUp = onUp,
        onSwitch = onSwitch,
        onListChoice = onListChoice,
        onTextInput = onTextInput,
        onNavigate = onNavigate,
        onSliderChange = onSliderChange,
        onMultiSelectChange = onMultiSelectChange,
        searchable = true,
        searchHint = searchHint,
        searchQuery = searchQuery,
        searchModeActive = searchModeActive,
        onSearchQueryChange = onSearchQueryChange,
        onOpenSearch = onOpenSearch,
        onCloseSearch = onCloseSearch,
        actions = {
            IconButton(onClick = onReset) {
                Icon(Icons.Filled.RestartAlt, contentDescription = resetContentDescription)
            }
        },
    )

    when (dialog) {
        is AppSettingsDialog.ConfirmReset -> AbConfirmDialog(
            title = null,
            message = dialog.message,
            confirmText = strings.yes,
            dismissText = strings.cancel,
            onConfirm = onConfirmDialog,
            onDismiss = onDismissDialog,
        )
        is AppSettingsDialog.DiscreteHelp -> AbLinkRouting(onOpenLink = onOpenLink) {
            AbMessageDialog(
                title = dialog.title,
                html = dialog.html,
                confirmText = strings.okay,
                onConfirm = onDismissDialog,
                onDismissRequest = onDismissDialog,
            )
        }
        AppSettingsDialog.None -> {}
    }
}
