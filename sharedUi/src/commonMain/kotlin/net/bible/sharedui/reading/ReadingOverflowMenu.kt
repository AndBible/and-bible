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

package net.bible.sharedui.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.unit.dp
import net.bible.sharedui.theme.isPureMonochrome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedui.components.AbMenuItem

/**
 * The reading-view toolbar's overflow ("3-dot") options menu: a Material3 [DropdownMenu] listing
 * [items] in order. Ported from classic `MainBibleActivity`'s inflated `R.menu.main_options_menu`
 * (Task 3 builds the real [OptionsMenuItem] list from `OptionsMenuItems` and dispatches [onItemClick]
 * by id). Stateless/controlled: [expanded] and dismissal are owned by the caller (Task 2 wires this
 * into [ReadingToolbar]'s overflow button), matching the classic menu's open-on-tap /
 * dismiss-on-outside-tap-or-item-tap behaviour.
 *
 * A checkable+checked item shows a trailing check mark (e.g. night-mode / show-bookmarks toggles);
 * an [OptionsMenuItem.opensDialog] item is suffixed with " …" (matching the classic strings.xml
 * ellipsis convention for items that open a further dialog/sub-screen, e.g. "Choose translations…").
 *
 * [icon] resolves each row's [OptionsMenuItem.iconKey] to a `Painter` — the same host-lambda seam
 * [ReadingDrawerContent]'s `icon` parameter uses, keeping this file free of Android types.
 * Defaulted to always-`null` so existing call sites and their goldens are unaffected.
 */
@Composable
fun ReadingOverflowMenu(
    items: List<OptionsMenuItem>,
    expanded: Boolean,
    onItemClick: (id: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (iconKey: String) -> Painter? = { null },
) {
    val mono = isPureMonochrome()
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss, modifier = modifier,
        containerColor = if (mono) MaterialTheme.colorScheme.surface else MenuDefaults.containerColor,
        tonalElevation = if (mono) 0.dp else MenuDefaults.TonalElevation,
        shadowElevation = if (mono) 0.dp else MenuDefaults.ShadowElevation,
        border = if (mono) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        ReadingOverflowMenuRows(items, onItemClick, icon)
    }
}

/**
 * The menu's row content, factored out of [ReadingOverflowMenu] so it can be rendered outside a
 * [DropdownMenu]'s [androidx.compose.ui.window.Popup] too (each [androidx.compose.material3.DropdownMenuItem] is a plain
 * composable, not scoped to a menu container). Deliberately public (not `internal`): it's called
 * directly, inside a plain `Column`, by `ReadingOverflowMenuGoldenTest` (in the `:app` module, so
 * `internal` visibility would not reach it) as a golden-capture surrogate for the real popup:
 * force-opening a Compose `DropdownMenu` under Robolectric/Roborazzi has repeatedly, intermittently
 * hung this repo's golden capture (`ShadowPausedLooper.idle()` spin in
 * `captureScreenIfMultipleWindows`) even for a near-identical top-bar overflow menu
 * ([net.bible.sharedui.components.AbOverflowMenu], see `AiPromptsGoldenTest`'s removed
 * `configured_overflowOpen_*` goldens) — so this single implementation is shared by both the real
 * popup and the golden's non-popup surrogate, keeping the rendered rows byte-for-byte the same
 * rather than duplicating the item-building logic.
 *
 * [icon] resolves [OptionsMenuItem.iconKey] to a leading `Painter` (see [ReadingOverflowMenu]'s
 * kdoc). Classic's `MenuPopupHelper.setForceShowIcon(true)` (`MainBibleActivity.kt:1417`, the
 * overflow menu's own call site — `SplitBibleArea.kt:859` is the pane menu's) makes Android's menu
 * row view reserve the icon frame for EVERY row once ANY row in that popup has an icon, so an
 * icon-less row still keeps a blank icon-sized gap and every row's label lands on the same left
 * edge (`ListMenuItemView.setIcon`: a `null` icon gets `mEmptyIcon`/`INVISIBLE`, not `GONE`). This
 * genuinely happens here: [net.bible.android.view.activity.page.OptionsMenuStateBuilder.build]
 * appends the dynamic, icon-less `textOptionItem` rows into the SAME flat list as the nine iconed
 * static entries, so this menu mixes icon and icon-less rows at its one and only level whenever the
 * user has any display-setting history — not just a golden test convenience. [reserveIconSlot]
 * mirrors that: computed from THIS call's own resolved icons (a `null` [OptionsMenuItem.iconKey] or
 * an unresolved key both count as "no icon" for that row, but if ANY row in [items] resolves one,
 * every row - including the icon-less ones - gets a same-size leading slot; a purely icon-less list
 * reserves nothing, keeping today's compact look). The resolve + reservation computation itself is
 * [resolveMenuIconRows], shared with [WindowPaneMenuRows] (previously duplicated byte-for-byte
 * between the two files).
 */
@Composable
fun ReadingOverflowMenuRows(
    items: List<OptionsMenuItem>,
    onItemClick: (id: String) -> Unit,
    icon: @Composable (iconKey: String) -> Painter? = { null },
) {
    val resolved = resolveMenuIconRows(items, OptionsMenuItem::iconKey, icon)
    resolved.rows.forEachIndexed { index, (item, painter) ->
        // Classic's group divider (MenuCompat.setGroupDividerEnabled). Never above the first row.
        if (index > 0 && item.startsNewSection) HorizontalDivider()
        AbMenuItem(
            text = if (item.opensDialog) "${item.label} …" else item.label,
            onClick = { onItemClick(item.id) },
            icon = painter?.let { p -> { Icon(painter = p, contentDescription = null) } },
            reserveIconSlot = resolved.reserveIconSlot && painter == null,
            checkable = item.checkable,
            checked = item.checked,
            enabled = item.enabled,
        )
    }
}
