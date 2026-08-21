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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.bible.sharedcore.settings.KEY_OPEN_GLOBAL_SETTINGS
import net.bible.sharedcore.settings.KEY_OPEN_WORKSPACE_SETTINGS
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedcore.settings.TextDisplaySettingsScreenState
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.textSettingEditorPageFor
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.strings.LocalStrings

/**
 * Host-resolved strings for [TextDisplaySettingsScreen]'s own chrome: the top-bar reset action +
 * its confirmation, the per-row long-press revert confirmation, the four value-editor dialogs'
 * titles/margin-label formats, and the neutral "reset to inherited"/OK/Cancel button labels.
 * [badgeWorkspace]/[badgeGlobal] are not consumed by the screen itself — they're for the HOST to
 * build the [TextDisplaySettingsScreen.badgeFor] lambda from a row's `InheritedFrom` (see the
 * golden test's `badgeLabel` helper), kept here so every string this screen's UI needs lives in
 * one host-resolved bundle. Category headers / row titles / drill-up link titles are NOT here —
 * those are resolved by [net.bible.sharedcore.settings.TextDisplaySettingsLabels] and already baked
 * into [TextDisplaySettingsScreenState.items] by the controller.
 */
data class TextDisplaySettingsScreenLabels(
    val resetContentDescription: String,
    val resetConfirmMessage: String,
    val revertMessage: String,
    val fontSizeDialogTitle: String,
    val topMarginDialogTitle: String,
    val lineSpacingDialogTitle: String,
    val marginSizeDialogTitle: String,
    /** Contains "%d", replaced with the current left-margin value (mm). */
    val marginLeftLabelFormat: String,
    /** Contains "%d", replaced with the current right-margin value (mm). */
    val marginRightLabelFormat: String,
    /** Contains "%d", replaced with the current max-text-width value (mm). */
    val marginMaxWidthLabelFormat: String,
    val resetToInheritedLabel: String,
    val badgeWorkspace: String,
    val badgeGlobal: String,
    val okLabel: String,
    val cancelLabel: String,
)

/**
 * The text-display-settings screen (font size, margins, colours, Strong's/morphology, footnotes,
 * bookmarks display, etc.) — 9 categories + the drill-up parent-link rows, rendered via
 * [AbSettingsScreen] from the (host-built, via `TextDisplaySettingsController`) [state]. Every row
 * is editable, and every edit has a working revert-to-inherited path:
 *
 * - Switch/list-choice rows edit inline (through [onSwitch]/[onListChoice]); their revert is the
 *   uniform long-press path below (any interactive row can be long-pressed).
 * - FONTSIZE/TOPMARGIN/LINE_SPACING open a numeric slider dialog (own OK / neutral "reset to
 *   inherited" / Cancel).
 * - MARGINSIZE opens a 3-slider margin dialog (same 3-button shape).
 * - COLORS/BOOKMARKS_HIDELABELS and the two drill-up parent-link rows ([KEY_OPEN_WORKSPACE_SETTINGS]/
 *   [KEY_OPEN_GLOBAL_SETTINGS]) forward to [onNavigate] — the host decides where they lead (a
 *   colours screen, a label picker, or the enclosing workspace's / the global text-display-settings
 *   screen).
 * - Long-pressing any interactive row EXCEPT the two drill-up parent-link rows opens a
 *   revert-confirm dialog → [onRevert] (this is the ONLY revert path for the 4 list-choice rows
 *   STRONGS/PAGE_SCROLL_AMOUNT/SCROLL_HELPER_LINE_STYLE/FONTFAMILY, which [AbSettingsContent]
 *   renders natively as list-choice rows — a real, working revert, not a placeholder). The two
 *   parent-link rows are excluded: their keys ([KEY_OPEN_WORKSPACE_SETTINGS]/[KEY_OPEN_GLOBAL_SETTINGS])
 *   aren't [TextSettingType] names, so routing them into [onRevert] would throw when the host's
 *   `TextDisplaySettingsController.onRevert` calls `TextSettingType.valueOf(key)` — see
 *   [isRevertableSettingsKey], the guard used below (mirrors this screen's own `handleNavigate`
 *   special-casing of the same two keys).
 * - The top-bar reset action opens a reset-confirm dialog → [onReset] (resets every row in this
 *   scope back to inherited).
 *
 * [badgeFor] supplies [LocalSettingsRowBadge] for the whole screen, so rows inheriting their value
 * from a wider scope show a small "Workspace"/"Global" chip (see [TextDisplaySettingsScreenLabels]).
 *
 * Numeric/margin slider ranges are NEVER hard-coded here — they're read fresh from `state.rows`
 * (a [TextSettingRowValue.Numeric] or [TextSettingRowValue.Margins]) on every recomposition,
 * mirroring [AbSettingsContent]'s dialog discipline: the dialog always renders the CURRENT row
 * (which may have changed underneath — e.g. a concurrent sync — while it was open), never a
 * click-time snapshot, and closes itself if the row disappears.
 */
/**
 * True when [key] is safe to route through [TextDisplaySettingsScreen]'s `onRevert` — i.e. NOT one
 * of the two drill-up parent-link keys ([KEY_OPEN_WORKSPACE_SETTINGS]/[KEY_OPEN_GLOBAL_SETTINGS]).
 * Those two are navigation rows with keys that aren't [TextSettingType] names, so `onRevert` — the
 * host's `TextDisplaySettingsController.onRevert` — calling `TextSettingType.valueOf(key)` throws
 * `IllegalArgumentException` for them (review Finding 1, Batch 12d-A T4 fix — long-pressing a
 * parent-link row used to crash). A top-level, named function (not inlined into the `onLongPress`
 * lambda) so this guard is directly unit-testable without any Compose test infrastructure.
 */
fun isRevertableSettingsKey(key: String): Boolean =
    key != KEY_OPEN_WORKSPACE_SETTINGS && key != KEY_OPEN_GLOBAL_SETTINGS

/** The three text settings whose editor is a single numeric slider. MARGINSIZE has its own
 *  three-slider editor and is routed separately. */
private val NUMERIC_TEXT_SETTING_KEYS = setOf(
    TextSettingType.FONTSIZE.name,
    TextSettingType.TOPMARGIN.name,
    TextSettingType.LINE_SPACING.name,
)

@Composable
fun TextDisplaySettingsScreen(
    state: TextDisplaySettingsScreenState,
    dialogLabels: TextDisplaySettingsScreenLabels,
    badgeFor: (String) -> String?,
    onUp: () -> Unit,
    onSwitch: (String, Boolean) -> Unit,
    onListChoice: (String, String) -> Unit,
    onNumericChange: (String, Int) -> Unit,
    onMarginsChange: (String, Int, Int, Int) -> Unit,
    onRevert: (String) -> Unit,
    onReset: () -> Unit,
    onNavigate: (String) -> Unit,
    searchQuery: String = "",
    searchModeActive: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
) {
    // Screen-local dialog state: the STABLE KEY of the open editor (if any), never a captured item
    // snapshot — see the kdoc above and AbSettingsContent's identical discipline for its own dialogs.
    var numericDialogKey by remember { mutableStateOf<String?>(null) }
    var marginDialogKey by remember { mutableStateOf<String?>(null) }
    var revertKey by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    fun handleNavigate(key: String) {
        val page = textSettingEditorPageFor(key)
        when {
            page == null || page is SettingsEditorPage.Colors -> onNavigate(key)
            page is SettingsEditorPage.Row && page.key == TextSettingType.MARGINSIZE.name ->
                marginDialogKey = page.key
            page is SettingsEditorPage.Row && page.key in NUMERIC_TEXT_SETTING_KEYS ->
                numericDialogKey = page.key
            // The four ListChoiceRow types never reach here: AbSettingsContent opens their editor
            // itself, so onNavigate is not called for them — same as before this extraction.
            else -> Unit
        }
    }

    CompositionLocalProvider(LocalSettingsRowBadge provides badgeFor) {
        AbSettingsScreen(
            state = SettingsScreenState(state.title, state.items),
            onUp = onUp,
            onSwitch = onSwitch,
            // The 4 native ListChoiceRow types (STRONGS/PAGE_SCROLL_AMOUNT/SCROLL_HELPER_LINE_STYLE/
            // FONTFAMILY) are handled entirely by AbSettingsContent's own AbListChoiceDialog; their
            // revert is the uniform long-press path below, not a dialog-level neutral reset here.
            onListChoice = onListChoice,
            onTextInput = { _, _ -> },
            onNavigate = ::handleNavigate,
            onLongPress = { key -> if (isRevertableSettingsKey(key)) revertKey = key },
            searchable = true,
            searchHint = LocalStrings.current.searchSettings,
            searchQuery = searchQuery,
            searchModeActive = searchModeActive,
            onSearchQueryChange = onSearchQueryChange,
            onOpenSearch = onOpenSearch,
            onCloseSearch = onCloseSearch,
            actions = {
                IconButton(onClick = { showResetConfirm = true }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = dialogLabels.resetContentDescription)
                }
            },
        )
    }

    val numericKey = numericDialogKey
    val numericType = numericKey?.let { runCatching { TextSettingType.valueOf(it) }.getOrNull() }
    val numericRow = numericType?.let { state.rows[it] }
    LaunchedEffect(numericDialogKey, numericRow) {
        if (numericDialogKey != null && numericRow == null) numericDialogKey = null
    }
    if (numericKey != null && numericType != null && numericRow != null) {
        val numericTitle = when (numericType) {
            TextSettingType.FONTSIZE -> dialogLabels.fontSizeDialogTitle
            TextSettingType.TOPMARGIN -> dialogLabels.topMarginDialogTitle
            TextSettingType.LINE_SPACING -> dialogLabels.lineSpacingDialogTitle
            else -> ""
        }
        NumericSliderDialog(
            title = numericTitle,
            numeric = numericRow.value as TextSettingRowValue.Numeric,
            okLabel = dialogLabels.okLabel,
            cancelLabel = dialogLabels.cancelLabel,
            resetLabel = dialogLabels.resetToInheritedLabel,
            onConfirm = { chosen -> onNumericChange(numericKey, chosen); numericDialogKey = null },
            onReset = { onRevert(numericKey); numericDialogKey = null },
            onDismiss = { numericDialogKey = null },
        )
    }

    val marginKey = marginDialogKey
    val marginRow = marginKey?.let { state.rows[TextSettingType.MARGINSIZE] }
    LaunchedEffect(marginDialogKey, marginRow) {
        if (marginDialogKey != null && marginRow == null) marginDialogKey = null
    }
    if (marginKey != null && marginRow != null) {
        MarginDialog(
            title = dialogLabels.marginSizeDialogTitle,
            margins = marginRow.value as TextSettingRowValue.Margins,
            leftLabelFormat = dialogLabels.marginLeftLabelFormat,
            rightLabelFormat = dialogLabels.marginRightLabelFormat,
            maxWidthLabelFormat = dialogLabels.marginMaxWidthLabelFormat,
            okLabel = dialogLabels.okLabel,
            cancelLabel = dialogLabels.cancelLabel,
            resetLabel = dialogLabels.resetToInheritedLabel,
            onConfirm = { left, right, maxWidth -> onMarginsChange(marginKey, left, right, maxWidth); marginDialogKey = null },
            onReset = { onRevert(marginKey); marginDialogKey = null },
            onDismiss = { marginDialogKey = null },
        )
    }

    val pendingRevertKey = revertKey
    if (pendingRevertKey != null) {
        AbConfirmDialog(
            title = null,
            message = dialogLabels.revertMessage,
            confirmText = dialogLabels.okLabel,
            dismissText = dialogLabels.cancelLabel,
            onConfirm = { onRevert(pendingRevertKey); revertKey = null },
            onDismiss = { revertKey = null },
        )
    }

    if (showResetConfirm) {
        AbConfirmDialog(
            title = null,
            message = dialogLabels.resetConfirmMessage,
            confirmText = dialogLabels.okLabel,
            dismissText = dialogLabels.cancelLabel,
            onConfirm = { onReset(); showResetConfirm = false },
            onDismiss = { showResetConfirm = false },
        )
    }
}

/**
 * Slider dialog for [TextSettingType.FONTSIZE]/[TextSettingType.TOPMARGIN]/[TextSettingType.LINE_SPACING]:
 * one [AbSliderRow] ranging over [TextSettingRowValue.Numeric.min]`..`[TextSettingRowValue.Numeric.max]
 * (never hard-coded — carried by the service via [numeric]), an OK button committing the dragged value,
 * a neutral "reset to inherited" button, and Cancel.
 *
 * Public (not `private`) so golden tests can render it directly with explicit params, the same
 * way `AbColorPickerGoldenTest` calls `AbColorPicker(...)` — [TextDisplaySettingsScreen] itself
 * still only ever opens it internally (via its own screen-local dialog-key state); this does not
 * change that screen's own public behavior.
 */
@Composable
fun NumericSliderDialog(
    title: String,
    numeric: TextSettingRowValue.Numeric,
    okLabel: String,
    cancelLabel: String,
    resetLabel: String,
    onConfirm: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember(numeric) { mutableIntStateOf(numeric.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AbSliderRow(
                label = "",
                value = current,
                onValueChange = { current = it },
                valueRange = numeric.min.toFloat()..numeric.max.toFloat(),
                valueLabel = numeric.displayText,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text(okLabel) } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(resetLabel) }
                TextButton(onClick = onDismiss) { Text(cancelLabel) }
            }
        },
    )
}

/**
 * Dialog for [TextSettingType.MARGINSIZE]: three [AbSliderRow]s (left/right/max-text-width), each
 * ranging `0..`[TextSettingRowValue.Margins.leftMax]/[rightMax][TextSettingRowValue.Margins.rightMax]/
 * [maxWidthMax][TextSettingRowValue.Margins.maxWidthMax] (never hard-coded — carried by [margins]).
 * Each slider's own row label carries its current value (the classic `SeekBarPreference` style — no
 * separate readout), formatted from the matching `dialogLabels.margin*LabelFormat` "%d" template.
 *
 * Public (not `private`) for the same golden-testing reason as [NumericSliderDialog] — see its kdoc.
 */
@Composable
fun MarginDialog(
    title: String,
    margins: TextSettingRowValue.Margins,
    leftLabelFormat: String,
    rightLabelFormat: String,
    maxWidthLabelFormat: String,
    okLabel: String,
    cancelLabel: String,
    resetLabel: String,
    onConfirm: (left: Int, right: Int, maxWidth: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var left by remember(margins) { mutableIntStateOf(margins.left) }
    var right by remember(margins) { mutableIntStateOf(margins.right) }
    var maxWidth by remember(margins) { mutableIntStateOf(margins.maxWidth) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AbSliderRow(
                    label = leftLabelFormat.replace("%d", left.toString()),
                    value = left,
                    onValueChange = { left = it },
                    valueRange = 0f..margins.leftMax.toFloat(),
                    valueLabel = "",
                )
                AbSliderRow(
                    label = rightLabelFormat.replace("%d", right.toString()),
                    value = right,
                    onValueChange = { right = it },
                    valueRange = 0f..margins.rightMax.toFloat(),
                    valueLabel = "",
                )
                AbSliderRow(
                    label = maxWidthLabelFormat.replace("%d", maxWidth.toString()),
                    value = maxWidth,
                    onValueChange = { maxWidth = it },
                    valueRange = 0f..margins.maxWidthMax.toFloat(),
                    valueLabel = "",
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(left, right, maxWidth) }) { Text(okLabel) } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(resetLabel) }
                TextButton(onClick = onDismiss) { Text(cancelLabel) }
            }
        },
    )
}
