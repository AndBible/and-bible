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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
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
import net.bible.sharedcore.bookmark.displayStyle
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.components.AbChoiceGroup
import net.bible.sharedui.components.AbExpandableSection
import net.bible.sharedui.components.AbIcons
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
 * deriving one internally. The avatar no longer sits on a disc (round-10a): a filled disc forced
 * the glyph to a black/white contrast tint, so the label's real colour never appeared in the icon
 * itself at all (round-9a I1 only fixed the glyph's invisibility on that disc, not this underlying
 * cause). The glyph is now drawn directly in the label's own colour via [accentArgbFor], which
 * keeps BW/e-ink correct. The style-preview glyph (in [BookmarkStylePreview]/[LabelStyleTag], on a
 * neutral card) uses that same [accentArgbFor] tint unconditionally now (round-12a), including for
 * the default glyph: the reader tints a MARKER with the label colour regardless of icon choice, so
 * a separate neutral-grey default here was a divergence from it, not a convention worth keeping.
 *
 * The whole-verse style axis is gated behind an [AbSwitchRow]: off means the axis inherits
 * [LabelEditState.selectionStyle] (stored as `null`), on reveals a second [AbChoiceGroup] seeded
 * with the current effective style so nothing visibly jumps when it appears. "This bookmark" and
 * "This workspace" collapse into [AbExpandableSection]s whose headers carry marks for whatever is
 * already set inside them, so a collapsed section still tells the user something is there.
 */
@Composable
fun LabelEditScreen(
    state: LabelEditState,
    onName: (String) -> Unit,
    onColor: (Int) -> Unit,
    onCustomIcon: (String?) -> Unit,
    onSelectionStyle: (BookmarkDisplayStyle) -> Unit,
    onWholeVerseStyle: (BookmarkDisplayStyle?) -> Unit,
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
    /** Test seams so a golden can photograph the expanded state — the sections are collapsed by
     *  default in production, and a golden cannot press a header. Same justification as
     *  [ManageLabelsSearchModeMenuRows] being public. */
    initialThisBookmarkExpanded: Boolean = false,
    initialWorkspaceExpanded: Boolean = false,
) {
    val strings = LocalStrings.current
    var identitySheetOpen by remember { mutableStateOf(false) }
    // No disc: a filled circle forced the glyph to a black/white contrast tint, so the label's
    // real colour never appeared in the icon itself (round-9a I1 fixed the invisibility, not the
    // cause). accentArgbFor keeps BW / e-ink correct. A colour close to the surface now draws a
    // faint glyph — accepted, and informative: a colour that cannot be seen here cannot be seen in
    // the reader either. The large swatch still exists where the colour is actually chosen, in
    // LabelIdentitySheet.
    val glyphTint = Color(accentArgbFor(state.color, LocalDisplayColorMode.current))

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
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    iconSlot(state.customIcon, glyphTint)
                }
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
                    // The label's own colour, even for the default glyph: the reader tints a MARKER
                    // with the label colour unconditionally (bookmarks.ts:825), and the grey default
                    // this used to draw was a divergence, not a convention.
                    BookmarkStylePreview(
                        style = state.selectionStyle,
                        colorArgb = state.color,
                        sampleText = strings.bookmarkStylePreviewSample,
                        decoratePartially = true,
                        iconSlot = { iconSlot(state.customIcon, glyphTint) },
                    )
                },
            )

            // An AbSwitchRow, not a Checkbox: this editor already speaks entirely in switch rows,
            // and a lone Checkbox would be a third widget style in one form. What matters is the
            // SEMANTICS -- off means inherit. "Inherit" stops being an odd fifth radio and becomes
            // the absence of a tick. Unchecking stores null; checking seeds the axis with what it
            // already effectively was, so nothing jumps. A new label inherits
            // (BookmarkEntities.kt:720), so this is unchecked and the second group absent in the
            // common case -- which is the decluttering this round is for.
            AbSwitchRow(
                label = strings.bookmarkStyleWholeVerseCustom,
                checked = state.wholeVerseStyle != null,
                onCheckedChange = { on -> onWholeVerseStyle(if (on) state.selectionStyle else null) },
            )
            val wholeVerseStyle = state.wholeVerseStyle
            if (wholeVerseStyle != null) {
                AbChoiceGroup(
                    heading = strings.bookmarkStyleWholeVerseHeading,
                    options = BookmarkDisplayStyle.entries,
                    selected = wholeVerseStyle,
                    optionLabel = { it.label(strings) },
                    onSelect = { onWholeVerseStyle(it) },
                    preview = {
                        // Full decoration on purpose: this axis covers the whole verse, and the contrast
                        // with the half-decorated selection preview above is what tells the two apart.
                        BookmarkStylePreview(
                            style = wholeVerseStyle,
                            colorArgb = state.color,
                            sampleText = strings.bookmarkStylePreviewSample,
                            iconSlot = { iconSlot(state.customIcon, glyphTint) },
                        )
                    },
                )
            }

            if (state.thisBookmarkGroupVisible) {
                var thisBookmarkExpanded by remember { mutableStateOf(initialThisBookmarkExpanded) }
                AbExpandableSection(
                    title = strings.thisBookmarkSectionTitle,
                    expanded = thisBookmarkExpanded,
                    onToggle = { thisBookmarkExpanded = !thisBookmarkExpanded },
                    indicators = {
                        // Marks, not a sentence: a collapsed section still says whether anything
                        // inside it is set, with no new translated string, and reusing exactly the
                        // symbols the list row teaches.
                        if (state.thisBookmarkPrimary) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = strings.primaryLabelSwitchLabel,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                ) {
                    AbSwitchRow(strings.addedToBookmarkLabel, state.thisBookmarkSelected, { onToggleSelected() })
                    AbSwitchRow(
                        strings.primaryLabelSwitchLabel,
                        state.thisBookmarkPrimary,
                        { onTogglePrimary() },
                        enabled = state.thisBookmarkPrimaryEnabled,
                        leadingIcon = {
                            Icon(
                                if (state.thisBookmarkPrimary) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            if (state.workspaceGroupVisible) {
                var workspaceExpanded by remember { mutableStateOf(initialWorkspaceExpanded) }
                AbExpandableSection(
                    title = strings.thisWorkspaceSectionTitle,
                    expanded = workspaceExpanded,
                    onToggle = { workspaceExpanded = !workspaceExpanded },
                    indicators = {
                        // Marks plus one miniature example, not a sentence: a collapsed section says
                        // whether anything inside it is set, and -- for the override -- WHICH style
                        // it imposes, which is strictly more than the ⚙ "something is set" glyph this
                        // replaces. No new translated string, and the marks are exactly the symbols
                        // the list row teaches.
                        //
                        // Set-only, deliberately: a header's job is "there is something inside".
                        // Drawing every off state here would say nothing; the hollow states belong
                        // on the controls themselves and in the list's grid.
                        if (state.autoAssign) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = strings.autoAssignLabelSwitchLabel,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (state.autoAssignPrimary) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = strings.autoAssignPrimaryLabelSwitchLabel,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        state.overrideMode.displayStyle?.let { overrideStyle ->
                            LabelStyleTag(
                                overrideStyle,
                                state.color,
                                modifier = Modifier.widthIn(max = 90.dp),
                                iconSlot = { iconSlot(state.customIcon, glyphTint) },
                            )
                        }
                    },
                ) {
                    AbSwitchRow(
                        strings.autoAssignLabelSwitchLabel,
                        state.autoAssign,
                        { onToggleAutoAssign() },
                        // The same bolt the list row's toggle uses, in the same two states: filled
                        // on, hollow off. Seeing the pair here is what teaches the pair there.
                        leadingIcon = {
                            Icon(
                                if (state.autoAssign) Icons.Filled.Bolt else AbIcons.BoltOutline,
                                contentDescription = null,
                            )
                        },
                    )
                    AbSwitchRow(
                        strings.autoAssignPrimaryLabelSwitchLabel,
                        state.autoAssignPrimary,
                        { onToggleAutoAssignPrimary() },
                        enabled = state.autoAssignPrimaryEnabled,
                        // 🔖 is the list row's primary column; wearing it here is what connects
                        // "add automatically as primary" to the mark the list draws.
                        leadingIcon = {
                            Icon(
                                if (state.autoAssignPrimary) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                            )
                        },
                    )
                    AbChoiceGroup(
                        heading = strings.overrideStyleFieldLabel,
                        options = OverrideMode.entries,
                        selected = state.overrideMode,
                        optionLabel = { it.label(strings) },
                        onSelect = onOverrideMode,
                        // Always present, including for NONE, where it shows the label's own style:
                        // the group then answers "what will this workspace draw" either way, and
                        // nothing appears or disappears as the radio moves. Same convention as the
                        // "Bookmark style" group above -- one preview, plain radio labels.
                        preview = {
                            BookmarkStylePreview(
                                style = state.overrideMode.displayStyle ?: state.selectionStyle,
                                colorArgb = state.color,
                                sampleText = strings.bookmarkStylePreviewSample,
                                iconSlot = { iconSlot(state.customIcon, glyphTint) },
                            )
                        },
                    )
                }
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
