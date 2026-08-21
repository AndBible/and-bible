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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.TextSettingRow
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType

/**
 * The sheet pages for the text display settings whose editor is a slider or a set of sliders:
 * FONTSIZE / TOPMARGIN / LINE_SPACING (one slider) and MARGINSIZE (three). Public and in its own
 * file because two hosts render it — the settings screen and the reading view's in-place editor —
 * and they must show the same editor.
 *
 * [rows] rather than the whole `TextDisplaySettingsScreenState`: neither host needs a title, search
 * state or item list to drive this composable, only the raw row values it edits.
 *
 * Re-resolves the row from [rows] on every recomposition (never a click-time snapshot — see
 * [TextDisplaySettingsScreen]'s own kdoc for why) and self-closes via [onClose] if the row vanishes
 * from underneath it, mirroring [GenericSettingsEditorSheet]'s identical discipline.
 *
 * [onPop] is wired to the sheet's `onDismiss` (back/scrim/swipe-down) and [onClose] to its header
 * ✕ — see [SettingsEditorSheet]'s kdoc for why those are different callbacks. Committing (OK),
 * reverting and cancelling all call [onClose], not [onPop]: these pages are always opened at depth 1
 * from the settings list, and every one of those three actions should return to the list, not to a
 * parent page — true even if a future caller pushes this page deeper, which [onPop] alone would not
 * guarantee.
 */
@Composable
fun TextSettingRowEditorSheet(
    pages: List<SettingsEditorPage>,
    rows: Map<TextSettingType, TextSettingRow>,
    dialogLabels: TextDisplaySettingsScreenLabels,
    onNumericChange: (String, Int) -> Unit,
    onMarginsChange: (String, Int, Int, Int) -> Unit,
    onRevert: (String) -> Unit,
    onPop: () -> Unit,
    onClose: () -> Unit,
) {
    val editorPage = pages.lastOrNull()
    val rowPage = editorPage as? SettingsEditorPage.Row
    val pageType = rowPage?.key?.let { k -> TextSettingType.entries.firstOrNull { it.name == k } }
    val pageRow = pageType?.let { rows[it] }
    LaunchedEffect(rowPage, pageRow) {
        if (rowPage != null && pageRow == null) onClose()
    }
    if (rowPage != null && pageType != null && pageRow != null) {
        val sheetTitle = when (pageType) {
            TextSettingType.FONTSIZE -> dialogLabels.fontSizeDialogTitle
            TextSettingType.TOPMARGIN -> dialogLabels.topMarginDialogTitle
            TextSettingType.LINE_SPACING -> dialogLabels.lineSpacingDialogTitle
            TextSettingType.MARGINSIZE -> dialogLabels.marginSizeDialogTitle
            else -> ""
        }
        SettingsEditorSheet(
            page = editorPage,
            title = sheetTitle,
            showBack = pages.size > 1,
            onDismiss = onPop,
            onClose = onClose,
        ) {
            if (pageType == TextSettingType.MARGINSIZE) {
                val margins = pageRow.value as TextSettingRowValue.Margins
                var left by remember(margins) { mutableIntStateOf(margins.left) }
                var right by remember(margins) { mutableIntStateOf(margins.right) }
                var maxWidth by remember(margins) { mutableIntStateOf(margins.maxWidth) }
                MarginContent(
                    margins = margins,
                    leftLabelFormat = dialogLabels.marginLeftLabelFormat,
                    rightLabelFormat = dialogLabels.marginRightLabelFormat,
                    maxWidthLabelFormat = dialogLabels.marginMaxWidthLabelFormat,
                    onValueChange = { l, r, m -> left = l; right = r; maxWidth = m },
                )
                SheetResetConfirmRow(
                    resetLabel = dialogLabels.resetToInheritedLabel,
                    confirmLabel = dialogLabels.okLabel,
                    cancelLabel = dialogLabels.cancelLabel,
                    onReset = { onRevert(rowPage.key); onClose() },
                    onConfirm = { onMarginsChange(rowPage.key, left, right, maxWidth); onClose() },
                    onCancel = onClose,
                )
            } else {
                val numeric = pageRow.value as TextSettingRowValue.Numeric
                var current by remember(numeric) { mutableIntStateOf(numeric.value) }
                NumericSliderContent(numeric = numeric, onValueChange = { current = it })
                SheetResetConfirmRow(
                    resetLabel = dialogLabels.resetToInheritedLabel,
                    confirmLabel = dialogLabels.okLabel,
                    cancelLabel = dialogLabels.cancelLabel,
                    onReset = { onRevert(rowPage.key); onClose() },
                    onConfirm = { onNumericChange(rowPage.key, current); onClose() },
                    onCancel = onClose,
                )
            }
        }
    }
}
