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
package net.bible.sharedui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridOption
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedcore.navigation.gridCellRows
import net.bible.sharedcore.navigation.gridCellRowsForSections
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbTopAppBar
import net.bible.sharedui.components.AbTopBarTitle
import net.bible.sharedui.strings.LocalStrings

/**
 * The long-name autosize floor, as a fraction of the long-name line's own [MaterialTheme]
 * `labelSmall` size rather than a hard-coded sp literal — so the floor cannot drift out of sync if
 * the typography changes. Comparable to classic's smallest `<small>` step (0.512x) and still
 * legible; at today's Material3 default (labelSmall = 11.sp) this works out to 7.15.sp, close to
 * the earlier literal 7.sp so the goldens do not shift for this reason alone.
 */
private const val LONG_NAME_MIN_FONT_SIZE_FRACTION = 0.65f

@Composable
fun GridChoosePassageScreen(
    ui: GridUi,
    options: GridOptions,
    onPick: (Int) -> Unit,
    onToggle: (GridOption) -> Unit,
    onNavigateUp: () -> Unit,
) {
    AbScaffold(
        topBar = {
            AbTopAppBar(
                title = { AbTopBarTitle(ui.title) },
                onNavigateUp = onNavigateUp,
                actions = {
                    // The step guard stays at the CALL SITE, in both hosts, so the quick sheet can
                    // make the same decision independently (round 15b Task 8, amendment D2).
                    if (ui.step == GridStep.BOOK) { GridOptionsOverflow(ui, options, onToggle) }
                },
            )
        },
    ) { padding ->
        GridChoosePassageContent(ui, onPick, Modifier.fillMaxSize().padding(padding))
    }
}

/**
 * The passage grid's six options, as an overflow menu.
 *
 * Public and standalone because round 15b's grid QUICK SHEET puts the same six items in
 * `AbQuickSheet`'s header actions slot (spec §4.6). Dropping them there would be a functional
 * regression, not a cosmetic one — [GridOption.DEUTEROCANONICAL] rebuilds the book list — and a
 * second copy in `:app` would have to duplicate this file's private `CheckItem` as well.
 * `AbOverflowMenu` is a `DropdownMenu`, i.e. a `Popup`, which renders above a sheet without
 * difficulty: the port's ban is on sheet-over-sheet, not popup-over-sheet.
 *
 * The "only at the BOOK step" guard is deliberately NOT here: it belongs to each host's own header,
 * so the sheet and the full screen decide independently.
 */
@Composable
fun GridOptionsOverflow(ui: GridUi, options: GridOptions, onToggle: (GridOption) -> Unit) {
    val strings = LocalStrings.current
    AbOverflowMenu(contentDescription = null) { close ->
        if (ui.showDeutToggle) {
            AbMenuItem(
                text = if (options.showScripture) strings.deuterocanonical else strings.bible,
                onClick = { close(); onToggle(GridOption.DEUTEROCANONICAL) },
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
            )
        }
        CheckItem(strings.menuAlphabetical, options.alphabetical, { Icon(Icons.Filled.SortByAlpha, contentDescription = null) }) { close(); onToggle(GridOption.ALPHABETICAL) }
        CheckItem(strings.menuRowOrder, options.ltr, { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) }) { close(); onToggle(GridOption.LTR) }
        CheckItem(strings.menuGroupByCategory, options.groupByCategory, { Icon(Icons.Filled.Category, contentDescription = null) }) { close(); onToggle(GridOption.GROUP_BY_CATEGORY) }
        CheckItem(strings.menuShowLongName, options.longNames, { Icon(Icons.Filled.TextFields, contentDescription = null) }) { close(); onToggle(GridOption.LONG_NAMES) }
        CheckItem(strings.menuShowProgressBars, options.showProgress, { Icon(Icons.Filled.BarChart, contentDescription = null) }) { close(); onToggle(GridOption.SHOW_PROGRESS) }
    }
}

/**
 * The passage grid's body, without chrome — so it can be hosted by the full-screen
 * [GridChoosePassageScreen] and by a bottom-sheet page alike.
 *
 * [modifier] MUST supply a bounded height when this is hosted in a sheet: the cell size is
 * `maxHeight / rowCount` (with a 40dp floor, below which the grid scrolls), and a bottom sheet's
 * content column is height-unbounded, where a `LazyVerticalGrid` crashes outright.
 *
 * [state] is a parameter, not a private `remember`, for the sheet's sake -- same reason as
 * `KeyListBody`'s `listState`: the quick-sheet shell draws its bottom fade from a
 * `canScrollForward` lambda it is handed, so the host has to read the grid's OWN scroll state or
 * the fade can never appear. The full screen passes nothing, which is behaviour-identical to the
 * `LazyVerticalGrid` creating its own state internally (the previous default).
 */
@Composable
fun GridChoosePassageContent(
    ui: GridUi,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
) {
    val sections = ui.sections
    val cols = ui.columns.coerceAtLeast(1)
    // Row-break spacers between grouped sections (classic group gaps).
    val spacerCount = if (sections != null) (sections.size - 1).coerceAtLeast(0) else 0
    // Content rows: each section starts on a fresh row (a full-span spacer forces the break),
    // so count rows per section, not across the whole list.
    val contentRows = if (sections != null) {
        gridCellRowsForSections(sectionSizes = sections.map { it.size }, columns = cols, minRows = ui.minRows)
    } else {
        gridCellRows(buttonCount = ui.buttons.size, columns = cols, minRows = ui.minRows)
    }
    BoxWithConstraints(modifier = modifier) {
        // Fill the viewport height like the classic grid: divide the available height by the
        // row count. contentPadding is 4dp each side (=8dp) and each spacer is 8dp tall.
        val available = maxHeight - 8.dp - (spacerCount * 8).dp
        val minCell = 40.dp // below this we stop growing and let long (verse) lists scroll instead
        val cellHeight = (available / contentRows).coerceAtLeast(minCell)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(4.dp),
        ) {
            if (sections != null) {
                sections.forEachIndexed { i, section ->
                    items(section.size, key = { "s$i-${section[it].id}" }) { idx ->
                        GridCell(section[idx], ui, cellHeight, onPick)
                    }
                    if (i < sections.lastIndex) {
                        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.height(8.dp)) } // group row-break
                    }
                }
            } else {
                items(ui.buttons.size, key = { ui.buttons[it].id }) { idx ->
                    GridCell(ui.buttons[idx], ui, cellHeight, onPick)
                }
            }
        }
    }
}

@Composable
private fun GridCell(b: GridButton, ui: GridUi, cellHeight: Dp, onPick: (Int) -> Unit) {
    // M3 neutral + accent, theme-aware throughout (no hardcoded dark): an inactive cell is the
    // neutral tonal surface tinted a quarter-step towards its book's category colour — classic's
    // category signal, see categoryChipColor — and the current cell is a filled primary container.
    // The current cell deliberately keeps M3's accent rather than classic's category fill: now that
    // every OTHER cell carries the category hue, reusing it for "current" would weaken the very
    // signal the tint adds.
    val container = when {
        b.isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> categoryChipColor(b.colorGroup, MaterialTheme.colorScheme.surfaceVariant)
    }
    val textColor = when {
        b.isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier = Modifier.padding(2.dp)) {
        Surface(
            onClick = { onPick(b.id) },
            color = container,
            shape = RoundedCornerShape(6.dp),
            border = if (b.isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.fillMaxWidth().height(cellHeight),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (ui.showLongNames) {
                    // Abbreviation (bold) on top, full name below.
                    val density = LocalDensity.current
                    val abbrStyle = MaterialTheme.typography.labelMedium
                    val longStyle = MaterialTheme.typography.labelSmall
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(2.dp),
                    ) {
                        Text(
                            text = b.label,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            style = abbrStyle,
                        )
                        b.longLabel?.let {
                            // Classic wraps a long name over multiple lines and shrinks it with nested
                            // <small> tags picked from the longest word (ButtonGrid.kt:220-267) — a
                            // heuristic that measures nothing. Compose Multiplatform measures for real:
                            // StepBased tries maxFontSize and steps down to minFontSize until the text
                            // fits. But StepBased only sees an "overflow" against constraints it is
                            // actually given — an unconstrained Column lets any font size "fit" by using
                            // more lines, so it never shrinks (verified: with no maxLines/height bound,
                            // the golden was pixel-identical to the unfixed original). So this needs its
                            // own real constraints: maxLines=2 (classic's practical wrap depth — enough
                            // to show most long names in full without letting a pathological one grow
                            // forever) and heightIn(max=...) sized from what's actually left in the cell
                            // after the abbreviation line and the Column's own padding, so the shrink
                            // search has a genuine vertical bound to solve against. minFontSize is
                            // derived from the label size (below) rather than a bare literal, comparable
                            // to classic's smallest step (0.512x) and still legible; overflow=Ellipsis is
                            // the last-resort floor for a name that still doesn't fit at minFontSize over
                            // 2 lines.
                            val abbrHeight = with(density) { abbrStyle.lineHeight.toDp() }
                            // A fraction of maxFontSize, not a bare literal, so the two cannot drift
                            // apart if the typography changes; ~0.65x keeps today's ~7sp value (11sp
                            // labelSmall * 0.65 = 7.15sp).
                            val minFontSize = longStyle.fontSize * LONG_NAME_MIN_FONT_SIZE_FRACTION
                            // abbrHeight scales with the user's font-scale setting while cellHeight does
                            // not, so at the 40dp minCell floor with a large font scale the bound above
                            // can collapse to a few dp — and since maxLines/width (not height) drive the
                            // ellipsis, a tiny height bound clips the name away entirely instead of
                            // ellipsizing it. Floor at one line rendered at minFontSize, computed from
                            // the style's own lineHeight:fontSize ratio so it scales the same way.
                            val lineHeightRatio = longStyle.lineHeight.value / longStyle.fontSize.value
                            val oneLineAtMinFontSize = with(density) { (minFontSize.value * lineHeightRatio).sp.toDp() }
                            val longNameMaxHeight = (cellHeight - 4.dp - abbrHeight).coerceAtLeast(oneLineAtMinFontSize)
                            BasicText(
                                text = it,
                                style = longStyle.copy(
                                    color = textColor,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.heightIn(max = longNameMaxHeight),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = minFontSize,
                                    maxFontSize = longStyle.fontSize,
                                    stepSize = 0.5.sp,
                                ),
                            )
                        }
                    }
                } else {
                    Text(
                        text = b.label,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        fontWeight = if (b.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(2.dp),
                    )
                }
                if (ui.showProgress && (b.readProgress > 0f || b.memProgress > 0f)) {
                    ProgressBars(b.readProgress, b.memProgress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ProgressBars(reading: Float, memorization: Float, modifier: Modifier) {
    Column(modifier) {
        if (reading > 0f) Bar(reading, readingColor())
        if (memorization > 0f) Bar(memorization, memorizationColor())
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(3.dp)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

@Composable
private fun CheckItem(text: String, checked: Boolean, icon: @Composable () -> Unit, onClick: () -> Unit) {
    AbMenuItem(text = text, onClick = onClick, icon = icon, checkable = true, checked = checked)
}
