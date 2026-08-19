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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedcore.theme.isLightColor
import net.bible.sharedui.components.AbChoiceGroup
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.LocalDisplayColorMode

/**
 * Stateless port of the classic `LabelEditActivity` / `bookmark_label_edit.xml` editor. Every
 * value comes from [state] (mirrors `LabelEditState`'s derived visibility/enable flags 1:1);
 * every mutation is forwarded to the host via the action lambdas, which the host wires 1:1 to
 * `LabelEditController` (Task 6). [iconSlot] renders the current custom icon (Android drawable
 * resources live host-side, so this screen never touches them); [actions] is the top-bar
 * save/delete/share overflow, also host-built (needs Android resources/dialogs).
 *
 * The colour circle, name and favourite heart collapse into one identity row that opens
 * [LabelIdentitySheet] (name field, colour presets + custom picker, icon grid) — edits there apply
 * live via [onName]/[onColor]/[onCustomIcon]. The heart's own click toggles favourite directly and
 * does not open the sheet: a single combined target would make "mark as favourite" and "edit the
 * name" the same gesture.
 *
 * [iconSlot] now takes a `tint` alongside the icon key: the glyph's colour depends on WHERE it is
 * drawn, not on the label's own colour, so each call site picks its own tint rather than
 * [net.bible.android.view.activity.bookmark.LabelEditComposeActivity]'s `AndroidLabelIcon`
 * deriving one internally. The avatar sits on a disc filled with the label's own colour, so tinting
 * the glyph with that SAME colour made it disappear (round-9a whole-branch review I1) — the
 * avatar instead picks black or white by [isLightColor]'s luminance threshold on the disc's actual
 * background, the same rule [net.bible.sharedui.components.ColorPickerPresetsPage]'s swatch check
 * mark uses for the identical problem. The style-preview glyph (in [BookmarkStylePreview], on a
 * neutral card) keeps the label-colour tint it always had — that background never collides with it.
 */
@Composable
fun LabelEditScreen(
    state: LabelEditState,
    onName: (String) -> Unit,
    onColor: (Int) -> Unit,
    onCustomIcon: (String?) -> Unit,
    onSelectionStyle: (BookmarkDisplayStyle) -> Unit,
    onWholeVerseStyle: (BookmarkDisplayStyle) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleSelected: () -> Unit,
    onTogglePrimary: () -> Unit,
    onToggleAutoAssign: () -> Unit,
    onToggleAutoAssignPrimary: () -> Unit,
    onOverrideMode: (OverrideMode) -> Unit,
    onUp: () -> Unit,
    iconKeys: List<String?>,
    iconSlot: @Composable (String?, Color) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val strings = LocalStrings.current
    var identitySheetOpen by remember { mutableStateOf(false) }
    val discColorArgb = accentArgbFor(state.color, LocalDisplayColorMode.current)
    // Contrast WITH THE DISC, not the label colour itself — reusing the disc colour as the glyph
    // tint (the pre-fix behaviour) makes the glyph invisible whenever accentArgbFor leaves the
    // colour unchanged (every mode but BW). Same threshold as the colour-picker's check mark.
    val avatarIconTint = if (isLightColor(discColorArgb)) Color.Black else Color.White
    // The style-preview glyph sits on a neutral card, so the label-colour tint it always had is
    // kept as-is; only the "no custom icon" default glyph keeps its neutral grey, matching classic.
    val previewIconTint = if (state.customIcon == null) NoCustomIconTint else Color(state.color)

    AbScaffold(title = strings.editLabelTitle, onNavigateUp = onUp, actions = actions) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = strings.editLabelTitle) { identitySheetOpen = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(Color(discColorArgb), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { iconSlot(state.customIcon, avatarIconTint) }
                Spacer(Modifier.width(16.dp))
                if (state.name.isBlank()) {
                    // No hint/placeholder here read as an empty gap with no affordance at all — a
                    // brand-new label (toolbar "+" with no live search query) opened with a name
                    // the user could not tell was editable, and saving silently discarded it
                    // (round-9a whole-branch review I2). Style identically to the real name so the
                    // row's layout does not shift once a name is typed.
                    Text(
                        strings.labelNameHint,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        state.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.favouriteVisible) {
                    // The heart's own click toggles favourite; only the rest of the row opens the
                    // sheet. A single combined target would make "mark as favourite" and "edit the
                    // name" the same gesture.
                    IconButton(onClick = onToggleFavourite) {
                        Icon(
                            if (state.favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = strings.favouriteLabelSwitchLabel,
                        )
                    }
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
                        iconSlot = { iconSlot(state.customIcon, previewIconTint) },
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
                        iconSlot = { iconSlot(state.customIcon, previewIconTint) },
                    )
                },
            )

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

    if (identitySheetOpen) {
        LabelIdentitySheet(
            name = state.name,
            nameEditable = state.nameEditable,
            colorArgb = state.color,
            customIcon = state.customIcon,
            iconKeys = iconKeys,
            iconVisible = state.customIconVisible,
            onName = onName,
            onColor = onColor,
            onCustomIcon = onCustomIcon,
            onDismiss = { identitySheetOpen = false },
        ) { key, tint -> iconSlot(key, tint) }
    }
}

/** The default-icon tint used only where the glyph sits on a neutral (non-label-coloured)
 *  background — mirrors classic's `grey_500`, the tint the pre-fix `AndroidLabelIcon` used
 *  whenever no custom icon was chosen. */
private val NoCustomIconTint = Color(0xFF9E9E9E)

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
