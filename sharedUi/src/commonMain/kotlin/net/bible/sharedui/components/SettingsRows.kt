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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.sharedui.theme.LocalAbColors
import net.bible.service.common.DisplayColorMode
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import net.bible.sharedui.settings.LocalSettingsIcon

/** M3 settings section label: small, coloured with the primary accent.
 *  Promoted from `AbSettingsScreen.kt`'s private `CategoryHeader` in round 13a so surfaces outside
 *  the declarative settings framework (the Speak sheet) render the identical header. */
@Composable
fun AbSettingsCategoryHeader(title: String) = Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
)

/** A settings row: label (+ optional summary) on the left, an M3 [Switch] on the right. The whole
 *  row is clickable and toggles the switch (larger touch target than the thumb alone). When
 *  [enabled] is false the row dims and stops responding to clicks (matches the other interactive
 *  settings rows in [net.bible.sharedui.settings.AbSettingsScreen]).
 *
 *  [onLongClick] defaults to `null` (Batch 12d-A Task 3's long-press-revert seam): when null the row
 *  keeps its original [toggleable] modifier (byte-identical); when non-null it switches to
 *  [combinedClickable] so a long press is available alongside the normal toggle tap.
 *
 *  [iconKey] defaults to `null` (A/B batch 3 F4b): resolved via
 *  [net.bible.sharedui.settings.LocalSettingsIcon], exactly like
 *  [net.bible.sharedui.settings.SettingsRow]'s leading icon (same 24dp [Icon] + 16dp [Spacer],
 *  same position before the label column). The `if (iconPainter != null)` guard means the icon
 *  [Composable] call is only ever EMITTED for a row that actually has one -- there is no
 *  fixed-width icon slot always present, so a null `iconKey` (every non-Text-options caller of this
 *  shared component: `AppSettings`, the AI/backup/speak/bookmark screens, …) keeps its original
 *  layout byte-for-byte; nothing shifts there. This mirrors [net.bible.sharedui.settings.SettingsRow]
 *  and [net.bible.sharedui.settings.AbSettingsScreen]'s `InfoRow` branch, which use the same
 *  conditional-emission pattern rather than a Material3 `leadingContent` slot -- the repo has
 *  already been bitten once by M3 reserving a leading-icon column on a slot LAMBDA's existence
 *  rather than its content, so this deliberately avoids that shape.
 *
 *  [badge] defaults to `null` (A/B batch 4a F2): an optional inheritance badge (e.g. "Workspace"/
 *  "Global", see [net.bible.sharedui.settings.LocalSettingsRowBadge]), rendered via
 *  [SettingsRowBadgeChip] INSIDE the row's text [Column] — never as a `Box` overlay on top of the
 *  row (that previously covered the summary and the switch). Same `if (badge != null)` conditional-
 *  emission shape as [iconKey] above: no badge means nothing is emitted, so every existing caller
 *  (which never passes `badge`) renders byte-identical to before.
 *
 *  [leadingIcon] defaults to `null` (round 10a Task 8): a caller-supplied leading [Composable],
 *  drawn before [iconKey]'s icon in the same 24dp box + 16dp [Spacer] position. It exists because a
 *  Material `ImageVector` living in `:sharedUi` cannot go through [iconKey], which resolves HOST
 *  drawables by key via [net.bible.sharedui.settings.LocalSettingsIcon]. Same `if (leadingIcon !=
 *  null)` conditional-emission shape as [iconKey] and [badge] above, and for the same reason: no
 *  slot is emitted for a row without one, so every existing caller (which never passes
 *  `leadingIcon`) renders byte-identical to before. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    iconKey: String? = null,
    badge: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val mono = LocalDisplayColorMode.current == DisplayColorMode.MONOCHROME
    val disabled = LocalAbColors.current.monoDisabled
    val foreground = if (mono && !enabled) disabled else LocalContentColor.current
    val iconPainter = iconKey?.let { LocalSettingsIcon.current(it) }
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = { if (enabled) onCheckedChange(!checked) },
                            onLongClick = onLongClick,
                            enabled = enabled,
                            role = Role.Switch,
                        )
                    } else {
                        Modifier.toggleable(value = checked, onValueChange = onCheckedChange, enabled = enabled, role = Role.Switch)
                    },
                )
                .alpha(if (enabled || mono) 1f else DISABLED_ALPHA)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same conditional-emission shape as iconKey below (and for the same reason): nothing is
            // emitted for a row without one, so every existing caller renders byte-identical. This slot
            // exists because a Material ImageVector living in :sharedUi cannot go through
            // LocalSettingsIcon, which resolves HOST drawables by key.
            if (leadingIcon != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                Spacer(Modifier.width(16.dp))
            }
            if (iconPainter != null) {
                Icon(painter = iconPainter, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                // A/B batch 4a F2: in the text column, NOT an overlay — the badge participates in
                // measurement, so it can never cover the summary or the switch.
                if (badge != null) {
                    Spacer(Modifier.height(2.dp))
                    SettingsRowBadgeChip(badge)
                }
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = if (mono && !enabled) disabled else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled,
                colors = if (mono) SwitchDefaults.colors(
                    disabledCheckedThumbColor = disabled,
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.surface,
                    disabledCheckedBorderColor = disabled,
                    disabledUncheckedThumbColor = disabled,
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surface,
                    disabledUncheckedBorderColor = disabled,
                ) else SwitchDefaults.colors(),
            )
        }
    }
}

/**
 * A generic clickable settings row (title + optional summary + optional leading icon + optional
 * trailing content), used for the list-choice, text-input and navigation item types. Disabled rows
 * dim and stop responding to clicks. [TwoLineListItem] isn't reused here because these rows may have
 * a single line (no summary) and an optional trailing slot.
 *
 * [iconKey] defaults to `null` (no icon): `SettingsItem.NavigationRow`, `SettingsItem.ListChoiceRow`
 * and `SettingsItem.TextInputRow` all carry an optional `iconKey`, resolved here via
 * [LocalSettingsIcon]. (Those three are named in prose, not as kdoc links: `SettingsItem` lives in
 * `:sharedCore` and is not imported here, so a `[…]` link would not resolve.)
 * `SettingsItem.SwitchRow` also has an `iconKey` field; it is now passed straight through to
 * [net.bible.sharedui.components.AbSwitchRow]'s own (A/B batch 3 F4b) `iconKey` parameter, which
 * resolves it via the same [LocalSettingsIcon] seam and only ever emits the icon `Composable` when
 * non-null — so every OTHER caller of that shared component (`AppSettings`, the AI/backup/speak/
 * bookmark screens, …), none of which passes `iconKey`, keeps its original icon-less layout.
 *
 * [onLongClick] defaults to `null` (Batch 12d-A Task 3's long-press-revert seam): when null the row
 * keeps its original plain [clickable] modifier (byte-identical); when non-null it switches to
 * [combinedClickable] to add the long-press gesture alongside the existing click.
 *
 * [badge] defaults to `null` (A/B batch 4a F2): an optional inheritance badge (e.g. "Workspace"/
 * "Global", see [net.bible.sharedui.settings.LocalSettingsRowBadge]), rendered via
 * [net.bible.sharedui.components.SettingsRowBadgeChip] INSIDE the title/summary [Column] — never as
 * a `Box` overlay on top of the row (that previously covered the summary and any trailing content).
 * Same `if (badge != null)` conditional-emission shape as [iconKey] above.
 *
 * [leadingIcon] defaults to `null` (round 13a): a caller-supplied leading [Composable], drawn before
 * [iconKey]'s icon in the same 24dp box + 16dp [Spacer] position. It exists because a Material
 * `ImageVector` living in `:sharedUi` cannot go through [iconKey], which resolves HOST drawables by
 * key via [LocalSettingsIcon] — and round 13a's Speak sheet, whose every row carries such an
 * `ImageVector`, is why this component was promoted out of `AbSettingsScreen` in the first place.
 * Same `if (leadingIcon != null)` conditional-emission shape as [iconKey] and [badge] above, and for
 * the same reason: no slot is emitted for a row without one, so every pre-13a caller renders
 * byte-identical to before.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbSettingsRow(
    title: String,
    summary: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    iconKey: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    badge: String? = null,
) {
    val mono = LocalDisplayColorMode.current == DisplayColorMode.MONOCHROME
    val disabled = LocalAbColors.current.monoDisabled
    val foreground = if (mono && !enabled) disabled else LocalContentColor.current
    val iconPainter = iconKey?.let { LocalSettingsIcon.current(it) }
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier.clickable(enabled = enabled, onClick = onClick)
                    },
                )
                .rowEnabled(enabled || mono)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same conditional-emission shape as iconKey below, and for the same reason as
            // AbSwitchRow.leadingIcon: nothing is emitted for a row without one, so every existing
            // caller renders byte-identically. A Material ImageVector living in :sharedUi cannot go
            // through LocalSettingsIcon, which resolves HOST drawables by key.
            if (leadingIcon != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                Spacer(Modifier.width(16.dp))
            }
            if (iconPainter != null) {
                Icon(painter = iconPainter, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                // A/B batch 4a F2: in the text column, NOT an overlay — the badge participates in
                // measurement, so it can never cover the summary or any trailing content.
                if (badge != null) {
                    Spacer(Modifier.height(2.dp))
                    SettingsRowBadgeChip(badge)
                }
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mono && !enabled) disabled else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

/** Dim a row when disabled (matches the classic preference-screen greyed-out affordance). */
private fun Modifier.rowEnabled(enabled: Boolean): Modifier =
    if (enabled) this else this.then(Modifier.alpha(DISABLED_ALPHA))

private const val DISABLED_ALPHA = 0.38f

/**
 * Small trailing chip showing a row's inheritance badge (e.g. "Workspace"/"Global") — see
 * [net.bible.sharedui.settings.LocalSettingsRowBadge]. Uses [MaterialTheme.colorScheme] only (no
 * hard-coded hues), so it stays legible and hue-free in the black-and-white / e-ink display modes.
 *
 * `internal` (A/B batch 4a F2, moved from `AbSettingsScreen.kt` where it was `private`): both
 * [AbSwitchRow] above and `SettingsRow` in `AbSettingsScreen.kt` render it inside their own text
 * `Column`, so it needs to be visible to both files without being a public API.
 */
@Composable
internal fun SettingsRowBadgeChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** A settings row: label + a right-aligned value readout on the top line, an M3 [Slider] below.
 *  [value]/[onValueChange] are Int; the slider rounds. [valueLabel] is the pre-formatted readout
 *  for the current [value] (e.g. "150 %").
 *
 *  The slider tracks the finger via LOCAL drag state, so [onValueChange] fires only ONCE per drag
 *  gesture — on release — mirroring classic `SeekBarPreference` (a per-tick callback here would run
 *  a Room write + a full settings rebuild on every increment). [onValueChangeFinished] fires after
 *  the release persist, for any extra host-side finish handling.
 *
 *  While dragging, the readout updates live from the local value using [valueLabelFor] if provided;
 *  callers without a formatter keep showing the static [valueLabel] (which refreshes once the
 *  persisted [value] comes back).
 *
 *  [iconKey] resolves a host drawable through [LocalSettingsIcon] exactly as `AbSwitchRow`/
 *  `AbSettingsRow` do, and [leadingIcon] remains for `ImageVector`s that cannot go through the seam. */
@Composable
fun AbSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChangeFinished: (() -> Unit)? = null,
    valueLabelFor: ((Int) -> String)? = null,
    iconKey: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val iconPainter = iconKey?.let { LocalSettingsIcon.current(it) }
    // Re-seed the local drag position whenever the persisted [value] changes (e.g. after a release
    // persist round-trips a fresh snapshot, or an external reset).
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val displayLabel = valueLabelFor?.invoke(dragValue.roundToInt()) ?: valueLabel
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Conditional emission (see AbSwitchRow.leadingIcon): callers without an icon render
            // byte-identically to before this parameter existed.
            if (leadingIcon != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                Spacer(Modifier.width(16.dp))
            }
            if (iconPainter != null) {
                Icon(painter = iconPainter, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(displayLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            valueRange = valueRange,
            onValueChangeFinished = {
                onValueChange(dragValue.roundToInt())
                onValueChangeFinished?.invoke()
            },
        )
    }
}
