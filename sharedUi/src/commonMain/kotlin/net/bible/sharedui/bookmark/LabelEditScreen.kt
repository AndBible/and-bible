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

package net.bible.sharedui.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedui.components.AbChoiceGroup
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.components.AbColorPickerDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * Stateless port of the classic `LabelEditActivity` / `bookmark_label_edit.xml` editor. Every
 * value comes from [state] (mirrors `LabelEditState`'s derived visibility/enable flags 1:1);
 * every mutation is forwarded to the host via the action lambdas, which the host wires 1:1 to
 * `LabelEditController` (Task 6). [iconSlot] renders the current custom icon (Android drawable
 * resources live host-side, so this screen never touches them); [actions] is the top-bar
 * save/delete/share overflow, also host-built (needs Android resources/dialogs).
 *
 * The colour swatch opens [AbColorPickerDialog], which owns its own working colour and hands it
 * back via [onColor] only when the dialog is confirmed; dismissing it leaves the label's colour
 * unchanged.
 */
@Composable
fun LabelEditScreen(
    state: LabelEditState,
    onName: (String) -> Unit,
    onColor: (Int) -> Unit,
    onEditIcon: () -> Unit,
    onSelectionStyle: (BookmarkDisplayStyle) -> Unit,
    onWholeVerseStyle: (BookmarkDisplayStyle) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleSelected: () -> Unit,
    onTogglePrimary: () -> Unit,
    onToggleAutoAssign: () -> Unit,
    onToggleAutoAssignPrimary: () -> Unit,
    onOverrideMode: (OverrideMode) -> Unit,
    onUp: () -> Unit,
    iconSlot: @Composable (String?) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val strings = LocalStrings.current
    var colorPickerOpen by remember { mutableStateOf(false) }

    AbScaffold(title = strings.editLabelTitle, onNavigateUp = onUp, actions = actions) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(color = state.color, onClick = { colorPickerOpen = true })
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onName,
                    enabled = state.nameEditable,
                    singleLine = true,
                    label = { Text(strings.labelNameHint) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.customIconVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEditIcon)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    iconSlot(state.customIcon)
                    Spacer(Modifier.width(16.dp))
                    Text(strings.selectCustomIconLabel, style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionTitle(strings.bookmarkStyleSectionTitle)
            AbChoiceGroup(
                heading = strings.bookmarkStyleSelectionHeading,
                options = BookmarkDisplayStyle.entries,
                selected = state.selectionStyle,
                optionLabel = { it.label(strings) },
                onSelect = onSelectionStyle,
                preview = {
                    BookmarkStylePreview(
                        style = state.selectionStyle,
                        colorArgb = state.color,
                        sampleText = strings.bookmarkStylePreviewSample,
                        iconSlot = { iconSlot(state.customIcon) },
                    )
                },
            )
            AbChoiceGroup(
                heading = strings.bookmarkStyleWholeVerseHeading,
                options = BookmarkDisplayStyle.entries,
                selected = state.wholeVerseStyle,
                optionLabel = { it.label(strings) },
                onSelect = onWholeVerseStyle,
                preview = {
                    BookmarkStylePreview(
                        style = state.wholeVerseStyle,
                        colorArgb = state.color,
                        sampleText = strings.bookmarkStylePreviewSample,
                        iconSlot = { iconSlot(state.customIcon) },
                    )
                },
            )

            if (state.favouriteVisible) {
                AbSwitchRow(strings.favouriteLabelSwitchLabel, state.favourite, { onToggleFavourite() })
            }

            if (state.thisBookmarkGroupVisible) {
                SectionTitle(strings.thisBookmarkSectionTitle)
                AbSwitchRow(strings.addedToBookmarkLabel, state.thisBookmarkSelected, { onToggleSelected() })
                AbSwitchRow(strings.primaryLabelSwitchLabel, state.thisBookmarkPrimary, { onTogglePrimary() }, enabled = state.thisBookmarkPrimaryEnabled)
            }

            if (state.workspaceGroupVisible) {
                SectionTitle(strings.thisWorkspaceSectionTitle)
                AbSwitchRow(strings.autoAssignLabelSwitchLabel, state.autoAssign, { onToggleAutoAssign() })
                AbSwitchRow(strings.autoAssignPrimaryLabelSwitchLabel, state.autoAssignPrimary, { onToggleAutoAssignPrimary() }, enabled = state.autoAssignPrimaryEnabled)

                AbChoiceGroup(
                    heading = strings.overrideStyleFieldLabel,
                    options = OverrideMode.entries,
                    selected = state.overrideMode,
                    optionLabel = { it.label(strings) },
                    onSelect = onOverrideMode,
                )
            }
        }
    }

    if (colorPickerOpen) {
        AbColorPickerDialog(
            initialColor = state.color,
            onConfirm = { onColor(it); colorPickerOpen = false },
            onDismiss = { colorPickerOpen = false },
        )
    }
}

@Composable
private fun ColorSwatch(color: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick)
            .background(AbColor.toComposeColor(color), CircleShape),
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** Maps [OverrideMode] to its display label — NONE reads "No override", the rest reuse the
 *  workspace-style-override display-mode strings. */
private fun OverrideMode.label(strings: Strings): String = when (this) {
    OverrideMode.NONE -> strings.noOverrideSuffix
    OverrideMode.HIGHLIGHT -> strings.displayModeHighlight
    OverrideMode.UNDERLINE -> strings.displayModeUnderline
    OverrideMode.MARKER -> strings.displayModeMarker
    OverrideMode.HIDDEN -> strings.displayModeHidden
}

/** The four styles reuse the workspace display-mode strings verbatim, so the label's own style and
 *  the workspace override that can replace it speak with one vocabulary. */
private fun BookmarkDisplayStyle.label(strings: Strings): String = when (this) {
    BookmarkDisplayStyle.HIGHLIGHT -> strings.displayModeHighlight
    BookmarkDisplayStyle.UNDERLINE -> strings.displayModeUnderline
    BookmarkDisplayStyle.MARKER -> strings.displayModeMarker
    BookmarkDisplayStyle.HIDDEN -> strings.displayModeHidden
}
