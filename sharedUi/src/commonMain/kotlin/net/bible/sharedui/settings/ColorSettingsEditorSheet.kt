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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorSettingsUiState
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.colorForPage
import net.bible.sharedui.components.AbColorPickerContent
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.strings.LocalStrings

/**
 * The whole colours editor as sheet pages: `Colors` → `ColorPick` → `BackgroundImage`, all pushed
 * onto ONE sheet rather than a dialog stacked on a screen — the point of this task (T8) is that
 * Task 10 can open this in place over the reading view instead of launching the settings activity.
 *
 * Renders nothing when [pages] is empty, the same self-hiding contract [SettingsEditorSheet] itself
 * has, so a host can render this unconditionally.
 *
 * A swatch (or the background-image "Change" button) PUSHES a page onto this same sheet — never a
 * dialog on top of it, which is the sheet-over-sheet / dialog-over-sheet nesting this design exists
 * to avoid. [ColorField.WORKSPACE] lets the workspace swatch's `ColorPick` page be pushed and
 * resolved the same way as the four `Colors`-field swatches — see that enum value's kdoc for why it
 * exists and how [net.bible.sharedcore.settings.ColorSettingsController] routes it.
 *
 * The one deliberate EXCEPTION to "push a page, never a dialog": the background-image chooser's
 * delete-confirm ([ColorSettingsUiState.deleteConfirm]) still renders as an [AbConfirmDialog] here,
 * exactly as [BackgroundImageChooserScreen] renders it over its own scaffold. A destructive
 * confirmation is chrome layered on top of whichever page is showing, not a page of its own — an
 * `AlertDialog`/`AbConfirmDialog` already opens in its own window and stacks over a `ModalBottomSheet`
 * (or anything else) without issue, so "dialog over sheet" is fine here; only "SHEET over sheet" is
 * what this design bans. [onConfirmDeleteBackgroundImage]/[onDismissDeleteConfirm] are this sheet's
 * own addition beyond the brief's sketch, which named no callback for resolving that dialog — without
 * them the confirm/cancel buttons would have nothing to call and the flow could never complete.
 *
 * Both scrolling page bodies ([ColorSettingsContent] on `Colors`, [BackgroundImageChooserContent] on
 * `BackgroundImage`) are wrapped in their own ancestor `Box(Modifier.heightIn(max = …))` here, by
 * CONVENTION, matching [GenericSettingsEditorSheet]'s own list/grid pages — not because either
 * content's particular modifier-chain order requires it. Whether a size constraint passed through a
 * composable's own `modifier` parameter can bound a scroll/lazy viewport depends entirely on where
 * that parameter lands in the composable's internal chain: outside the scroll node (as in
 * [ColorSettingsContent]'s own chain today) it would work, inside it (as in
 * [net.bible.sharedui.components.AbListChoiceContent]'s `verticalScroll(state).then(modifier)`, the
 * bug [GenericSettingsEditorSheet] had to work around) it silently would not. The ancestor `Box` form
 * used here is unconditionally correct regardless of that internal order, so it is the one that never
 * has to be re-derived per composable — that is the actual reason for using it here, not a claim
 * that either content's own `modifier` parameter is unusable for this. The `ColorPick` page needs no
 * such wrapper: [AbColorPickerContent] already self-bounds to 420.dp internally, leaving room below
 * for this page's own confirm row, mirroring [net.bible.sharedui.components.AbColorPickerDialog]'s dialog contract.
 */
@Composable
fun ColorSettingsEditorSheet(
    pages: List<SettingsEditorPage>,
    state: ColorSettingsUiState,
    labels: ColorSettingsLabels,
    chooserLabels: BackgroundImageChooserLabels,
    thumbnailFor: (String) -> ImageBitmap?,
    importVisible: Boolean,
    onColorChange: (ColorField, Int) -> Unit,
    onWorkspaceColorChange: (Int) -> Unit,
    onNoiseChange: (night: Boolean, value: Int) -> Unit,
    onOpacityChange: (night: Boolean, value: Int) -> Unit,
    onSelectBackgroundImage: (night: Boolean, initials: String?) -> Unit,
    onImportBackgroundImage: () -> Unit,
    onRequestDeleteBackgroundImage: (BackgroundImageOption) -> Unit,
    onConfirmDeleteBackgroundImage: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onPush: (SettingsEditorPage) -> Unit,
    onPop: () -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalStrings.current
    val page = pages.lastOrNull() ?: return
    val title = when (page) {
        is SettingsEditorPage.Colors -> state.colors.title
        is SettingsEditorPage.ColorPick -> strings.colorPickerTitle
        is SettingsEditorPage.BackgroundImage ->
            if (page.night) labels.backgroundImageNight else labels.backgroundImageDay
        is SettingsEditorPage.Row -> return   // not this sheet's page kind
    }
    SettingsEditorSheet(
        page = page,
        title = title,
        showBack = pages.size > 1,
        onDismiss = onPop,
        onClose = onClose,
    ) {
        when (page) {
            is SettingsEditorPage.Colors ->
                // 480.dp: taller than the 400.dp list-choice bound (GenericSettingsEditorSheet /
                // SettingsEditorSheetGoldenTest) on purpose -- this form is two full day/night
                // sections plus an optional workspace row, not one flat list, and there is no
                // confirm row below it to leave room for.
                Box(Modifier.heightIn(max = 480.dp)) {
                    ColorSettingsContent(
                        state = state,
                        labels = labels,
                        // A swatch pushes a page onto THIS sheet -- see the class kdoc.
                        onColorFieldClick = { onPush(SettingsEditorPage.ColorPick(it)) },
                        onWorkspaceColorClick = { onPush(SettingsEditorPage.ColorPick(ColorField.WORKSPACE)) },
                        onNoiseChange = onNoiseChange,
                        onOpacityChange = onOpacityChange,
                        onChangeBackgroundImage = { onPush(SettingsEditorPage.BackgroundImage(it)) },
                    )
                }
            is SettingsEditorPage.ColorPick -> {
                val initial = colorForPage(state, page.field)
                var working by remember(initial) { mutableStateOf(initial or (0xFF shl 24)) }
                var presetsPage by remember { mutableStateOf(true) } // classic opens on presets
                AbColorPickerContent(
                    initialColor = initial or (0xFF shl 24),
                    working = working,
                    onWorkingChange = { working = it },
                    presetsPage = presetsPage,
                )
                // A purpose-named row, not SheetResetConfirmRow: onSwitchPage toggles presets/custom,
                // it does not reset anything, and reusing that row's onReset slot for this would make
                // the parameter lie about what it does. See SheetPageSwitchConfirmRow's kdoc.
                SheetPageSwitchConfirmRow(
                    switchLabel = if (presetsPage) strings.colorPickerCustom else strings.colorPickerPresets,
                    confirmLabel = strings.okay,
                    cancelLabel = strings.cancel,
                    onSwitchPage = { presetsPage = !presetsPage },
                    onConfirm = {
                        if (page.field == ColorField.WORKSPACE) onWorkspaceColorChange(working)
                        else onColorChange(page.field, working)
                        onPop()
                    },
                    onCancel = onPop,
                )
            }
            is SettingsEditorPage.BackgroundImage ->
                // 400.dp: same bound GenericSettingsEditorSheet's own list/grid pages use -- no
                // reason for this grid to clip at a different height than the sheet's other lists.
                Box(Modifier.heightIn(max = 400.dp)) {
                    BackgroundImageChooserContent(
                        options = state.backgroundOptions,
                        labels = chooserLabels,
                        thumbnailFor = thumbnailFor,
                        importVisible = importVisible,
                        onSelect = { onSelectBackgroundImage(page.night, it) },
                        onImport = onImportBackgroundImage,
                        onRequestDelete = onRequestDeleteBackgroundImage,
                    )
                }
            is SettingsEditorPage.Row -> Unit
        }
    }

    val deleteConfirm = state.deleteConfirm
    if (deleteConfirm != null) {
        AbConfirmDialog(
            title = chooserLabels.deleteTitle,
            message = chooserLabels.deleteConfirm,
            confirmText = chooserLabels.delete,
            dismissText = chooserLabels.cancel,
            onConfirm = onConfirmDeleteBackgroundImage,
            onDismiss = onDismissDeleteConfirm,
        )
    }
}
